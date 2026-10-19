-keep class com.josh.notes.** { *; }
# Vosk + JNA (reflection / native callbacks)
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
