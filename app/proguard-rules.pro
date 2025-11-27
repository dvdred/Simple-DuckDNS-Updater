# Add project specific ProGuard rules here.
# You can control the set of used libraries using the libraryjars option with the full path of the JAR file.
# For example:
# libraryjars /libs/mylib.jar

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface class:
# -keepclassmembers class fqcn.of.javascript.interface.for.webview {
#    public *;
# }

# For Android 8.0 and above, if your app uses WorkManager, you might need:
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.impl.utils.** { *; }

# Keep all classes that extend BroadcastReceiver
-keep public class com.duckdns.UpdateReceiver

# Keep all classes that extend Activity
-keep public class com.duckdns.MainActivity

# Keep all classes that are used in the manifest
-keep public class com.duckdns.** { *; }

# Keep the application class
-keep public class com.duckdns.DuckDNSApplication

# For logging purposes
-keep class java.util.logging.** { *; }
