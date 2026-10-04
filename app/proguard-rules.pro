# Keep JNI bridge and internal callback interface
-keep class com.lfmlocal.app.inference.LlamaBridge { *; }
-keep interface com.lfmlocal.app.inference.LlamaBridge$TokenCallback { *; }
-keepclasseswithmembers class * implements com.lfmlocal.app.inference.LlamaBridge$TokenCallback {
    public void onToken(java.lang.String);
    public boolean isCancelled();
}
-keep class com.lfmlocal.app.data.** { *; }
