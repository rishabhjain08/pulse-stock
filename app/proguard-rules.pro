# Keep Ktor / OkHttp internals
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data models
-keep class com.pulsestock.app.data.** { *; }

# SQLCipher — native libsqlcipher.so looks up Java fields (e.g. mNativeHandle) by name via JNI.
# Without this, R8 renames them and JNI_OnLoad crashes with NoSuchFieldError.
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
