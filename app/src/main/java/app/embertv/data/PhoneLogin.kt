package app.embertv.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * A tiny web page served by the TV on the home network, so the login can be typed on a phone.
 * Nothing leaves the network: the phone's browser posts straight to the TV, and the server only
 * runs while the sign-in screen is showing. The random [token] in the address keeps stray
 * requests out.
 */
class PhoneLoginServer(
    /** Signs in with what the phone sent; returns an error message, or null on success. */
    private val onLogin: suspend (server: String, user: String, pass: String) -> String?,
) {
    val token: String = (1..4).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")

    /**
     * A one-off key carried in the QR code after the '#', which browsers never send over the
     * network. The page uses it to scramble the password before sending, so nothing on the Wi-Fi
     * can read it. Typing the address by hand (no key) still works, unscrambled.
     */
    private val key: ByteArray = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
    private val keyText: String = android.util.Base64.encodeToString(key, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

    /** Undoes the page's scrambling: the password XOR SHA-256(key, nonce, block number). */
    private fun unscramble(hex: String, nonceHex: String): String? = runCatching {
        fun bytes(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val ct = bytes(hex)
        val nonce = bytes(nonceHex)
        val out = ByteArray(ct.size)
        var block = 0
        while (block * 32 < ct.size) {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.update(key)
            md.update(nonce)
            md.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            val ks = md.digest()
            for (j in 0 until 32) {
                val i = block * 32 + j
                if (i >= ct.size) break
                out[i] = (ct[i].toInt() xor ks[j].toInt()).toByte()
            }
            block++
        }
        String(out, Charsets.UTF_8)
    }.getOrNull()
    @Volatile private var socket: ServerSocket? = null

    /** Serves until [stop]. Calls [onReady] with the address to show once listening. */
    suspend fun run(onReady: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        val ip = localAddress() ?: return@withContext
        val server = listOf(8765, 8766, 8767, 0).firstNotNullOfOrNull { port ->
            try {
                ServerSocket(port)
            } catch (e: Exception) {
                null
            }
        } ?: return@withContext
        socket = server
        val port = server.localPort
        onReady((if (port == 80) "http://$ip/$token" else "http://$ip:$port/$token") + "#k=$keyText")
        try {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                try {
                    client.soTimeout = 15_000
                    handle(client)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A broken request from the phone; carry on listening.
                } finally {
                    runCatching { client.close() }
                }
            }
        } finally {
            runCatching { server.close() }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun handle(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val path = parts[1].substringBefore('?').trimEnd('/')
        var length = 0
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon > 0 && header.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                length = header.substring(colon + 1).trim().toIntOrNull() ?: 0
            }
        }
        val out = client.getOutputStream()
        fun respond(status: String, html: String) {
            val body = html.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.US_ASCII))
            out.write(body)
            out.flush()
        }

        if (path != "/$token") {
            respond("404 Not Found", page("Not found", "<p class=\"msg\">Open the exact address shown on your TV.</p>"))
            return
        }
        if (method != "POST") {
            respond("200 OK", page("Sign in", form("", "", null)))
            return
        }
        val body = ByteArray(length.coerceIn(0, 16_384))
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) break
            read += n
        }
        val fields = String(body, 0, read, Charsets.UTF_8).split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else pair.substring(0, i) to runCatching { URLDecoder.decode(pair.substring(i + 1), "UTF-8") }.getOrDefault("")
        }.toMap()
        var server = fields["server"].orEmpty().trim()
        var user = fields["username"].orEmpty().trim()
        val scrambled = fields["pw_enc"]
        var pass = if (scrambled != null) unscramble(scrambled, fields["nonce"].orEmpty()).orEmpty() else fields["password"].orEmpty()
        // A pasted playlist link carries the username and password too.
        credentialsFromLink(server)?.let { (u, p) ->
            if (user.isEmpty()) user = u
            if (pass.isEmpty()) pass = p
        }
        if (server.isEmpty() || user.isEmpty()) {
            respond("200 OK", page("Sign in", form(server, user, "Enter the server address and username.")))
            return
        }
        val error = onLogin(server, user, pass)
        if (error == null) {
            respond(
                "200 OK",
                page("Signed in", "<div class=\"done\">✓</div><h1>You're signed in</h1><p class=\"msg\">Your TV is getting your channels ready. You can close this page.</p>"),
            )
        } else {
            server = server.substringBefore('?')
            respond("200 OK", page("Sign in", form(server, user, error)))
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8_192) break
        }
        return buf.toString("UTF-8")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun form(server: String, user: String, error: String?): String = """
        <h1>Sign in on your TV</h1>
        <p class="msg">Type the details from your IPTV provider and they'll go straight to your TV.</p>
        <p class="lock" id="lock">Sent straight to your TV over your Wi-Fi</p>
        ${if (error != null) "<div class=\"err\"><b>Couldn't sign in</b>${esc(error)}</div>" else ""}
        <form method="post" action="/$token" autocomplete="off">
          <label>Server address<input name="server" type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="http://example.com:8080" value="${esc(server)}"></label>
          <label>Username<input name="username" autocapitalize="off" autocorrect="off" spellcheck="false" value="${esc(user)}"></label>
          <label>Password<span class="pw"><input name="password" type="password" autocapitalize="off" autocorrect="off"><a href="#" id="show">Show</a></span></label>
          <button type="submit">Sign in on TV</button>
        </form>
        <p class="hint">Tip: you can paste the whole playlist link (…get.php?username=…&amp;password=…) into the server box.</p>
    """.trimIndent()

    private fun page(title: String, content: String): String = """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Streammania · $title</title>
        <style>
          :root { color-scheme: dark; }
          body { margin: 0; min-height: 100vh; background: radial-gradient(circle at 20% 0%, rgba(255,106,43,.16), transparent 55%), #0b0b0f; color: #f5f5f7;
                 font: 16px/1.45 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
          main { max-width: 420px; margin: 0 auto; padding: 36px 20px 48px; }
          .brand { font-weight: 800; font-size: 24px; letter-spacing: -0.6px; margin-bottom: 30px; }
          .brand span { background: linear-gradient(90deg, #FFB547, #FF6A2B, #E8362A); -webkit-background-clip: text; background-clip: text; color: transparent; }
          h1 { font-size: 28px; margin: 0 0 6px; letter-spacing: -0.4px; }
          .msg { color: #a8a8b3; margin: 0 0 10px; }
          .lock { color: #a8a8b3; font-size: 13px; font-weight: 600; margin: 0 0 24px; }
          .err { background: rgba(255,69,58,.14); border: 1px solid rgba(255,69,58,.22); color: #a8a8b3; padding: 12px 14px; border-radius: 16px; margin: 0 0 18px; font-size: 14px; }
          .err b { display: block; color: #f5f5f7; font-size: 15px; margin-bottom: 2px; }
          label { display: block; font-size: 11px; font-weight: 600; letter-spacing: 1.1px; text-transform: uppercase; color: #a8a8b3; margin-bottom: 16px; padding-left: 4px; }
          input { display: block; box-sizing: border-box; width: 100%; margin: 7px 0 0 -4px; width: calc(100% + 4px); padding: 15px 16px; font-size: 17px; color: #f5f5f7;
                  letter-spacing: 0; text-transform: none; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.08); border-radius: 14px; outline: none; }
          input:focus { border-color: rgba(255,255,255,.6); background: rgba(255,255,255,.12); }
          .pw { position: relative; display: block; }
          .pw input { padding-right: 70px; }
          .pw a { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); margin-top: 3px; color: #a8a8b3; text-decoration: none; font-size: 13px; font-weight: 600;
                  letter-spacing: 0; text-transform: none; padding: 8px 10px; }
          button { width: 100%; margin-top: 8px; padding: 16px; font-size: 17px; font-weight: 600; color: #000; background: #fff; border: 0; border-radius: 26px; }
          button:disabled { opacity: .6; }
          .hint { color: #6e6e78; font-size: 13px; margin-top: 18px; }
          .done { width: 64px; height: 64px; border-radius: 32px; background: #fff; color: #000; font-size: 32px; font-weight: 700;
                  display: flex; align-items: center; justify-content: center; margin-bottom: 18px; }
        </style></head>
        <body><main><div class="brand">stream<span>mania</span></div>$content</main>
        <script>
        function sha256(m){var K=[],H=[1779033703,3144134277,1013904242,2773480762,1359893119,2600822924,528734635,1541459225],n=2,c=0;
        while(c<64){var p=1;for(var d=2;d*d<=n;d++)if(n%d==0){p=0;break}if(p){K[c++]=(Math.pow(n,1/3)%1)*4294967296|0}n++}
        var l=m.length,b=m.concat([128]);while(b.length%64!=56)b.push(0);var bl=l*8;for(var i=7;i>=0;i--)b.push(i>3?0:(bl>>>(i*8))&255);
        var w=[];for(var o=0;o<b.length;o+=64){for(i=0;i<16;i++)w[i]=b[o+i*4]<<24|b[o+i*4+1]<<16|b[o+i*4+2]<<8|b[o+i*4+3];
        for(i=16;i<64;i++){var x=w[i-15],y=w[i-2];w[i]=(w[i-16]+((x>>>7|x<<25)^(x>>>18|x<<14)^(x>>>3))+w[i-7]+((y>>>17|y<<15)^(y>>>19|y<<13)^(y>>>10)))|0}
        var a=H[0],B=H[1],C=H[2],D=H[3],e=H[4],f=H[5],g=H[6],h=H[7];
        for(i=0;i<64;i++){var t1=h+((e>>>6|e<<26)^(e>>>11|e<<21)^(e>>>25|e<<7))+(e&f^~e&g)+K[i]+w[i]|0,t2=((a>>>2|a<<30)^(a>>>13|a<<19)^(a>>>22|a<<10))+(a&B^a&C^B&C)|0;h=g;g=f;f=e;e=D+t1|0;D=C;C=B;B=a;a=t1+t2|0}
        H[0]=H[0]+a|0;H[1]=H[1]+B|0;H[2]=H[2]+C|0;H[3]=H[3]+D|0;H[4]=H[4]+e|0;H[5]=H[5]+f|0;H[6]=H[6]+g|0;H[7]=H[7]+h|0}
        var r=[];for(i=0;i<8;i++)for(var j=3;j>=0;j--)r.push(H[i]>>>(j*8)&255);return r}
        var k = (location.hash.match(/k=([A-Za-z0-9_-]+)/) || [])[1];
        function b64(t) { t = t.replace(/-/g, "+").replace(/_/g, "/"); while (t.length % 4) t += "="; var d = atob(t), o = []; for (var i = 0; i < d.length; i++) o.push(d.charCodeAt(i)); return o; }
        function hex(a) { return a.map(function (x) { return ("0" + x.toString(16)).slice(-2); }).join(""); }
        function ready() {
          var l = document.getElementById("lock");
          if (l && k) l.textContent = "Scrambled and sent only to your TV, over your Wi-Fi";
          var s = document.getElementById("show");
          if (s) s.onclick = function (e) { e.preventDefault(); var p = document.querySelector("input[name=password]"); var shown = p.type == "text"; p.type = shown ? "password" : "text"; s.textContent = shown ? "Show" : "Hide"; };
        }
        ready();
        // Sent from script rather than as a plain form, so the password goes scrambled.
        document.addEventListener("submit", function (ev) {
          var f = ev.target; ev.preventDefault();
          var btn = f.querySelector("button"); btn.disabled = true; btn.textContent = "Signing in on your TV…";
          var p = new URLSearchParams();
          p.set("server", f.server.value); p.set("username", f.username.value);
          var pw = f.password.value;
          if (k) {
            var key = b64(k), nonce = Array.from(crypto.getRandomValues(new Uint8Array(12)));
            var pt = Array.from(new TextEncoder().encode(pw)), ct = [];
            for (var n = 0; n * 32 < pt.length; n++) {
              var ks = sha256(key.concat(nonce, [n >>> 24 & 255, n >>> 16 & 255, n >>> 8 & 255, n & 255]));
              for (var j = 0; j < 32 && n * 32 + j < pt.length; j++) ct.push(pt[n * 32 + j] ^ ks[j]);
            }
            p.set("pw_enc", hex(ct)); p.set("nonce", hex(nonce));
          } else p.set("password", pw);
          fetch(location.pathname, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: p.toString() })
            .then(function (r) { return r.text(); })
            .then(function (t) { var d = new DOMParser().parseFromString(t, "text/html"); document.querySelector("main").innerHTML = d.querySelector("main").innerHTML; ready(); })
            .catch(function () { btn.disabled = false; btn.textContent = "Sign in on TV"; var m = document.querySelector(".msg"); if (m) m.textContent = "Couldn't reach your TV. Is Streammania still on the sign-in screen?"; });
        });
        </script></body></html>
    """.trimIndent()

    companion object {
        /** This device's address on the home network (Wi-Fi or Ethernet). */
        fun localAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .let { addresses -> addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull { !it.isLinkLocalAddress } }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
