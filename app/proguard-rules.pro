# Keep Streammania's own code as written (it's small); R8 still optimises the libraries.
-keep class app.embertv.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Optional pieces some libraries mention but don't need on Android.
-ignorewarnings
-dontwarn **
