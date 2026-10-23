# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Serialized profile handover DTOs use Gson reflection; the audio pipeline does not.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.example.voxa.data.P2PSharingDto { *; }
-keep class com.example.voxa.data.IntentWithTemplates { *; }
-keep class com.example.voxa.data.license.ProgressReview { *; }
-keep class com.example.voxa.data.license.Report** { *; }
-keepclassmembers class com.example.voxa.data.** { <fields>; }
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
