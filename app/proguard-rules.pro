# Vosk + JNA používají nativní kód a reflexi
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keepclassmembers class * extends com.sun.jna.** { *; }
