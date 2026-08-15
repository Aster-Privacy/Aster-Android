-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations
-keep,includedescriptorclasses class org.astermail.android.api.**$$serializer { *; }
-keepclassmembers class org.astermail.android.api.** {
    *** Companion;
}
-keepclasseswithmembers class org.astermail.android.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Ktor
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.**

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# SQLCipher (native libs loaded via JNI)
-keep class net.zetetic.** { *; }
-keep interface net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Google Tink - must keep for EncryptedSharedPreferences (loaded via reflection)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# TurnstileBridge - methods called from JavaScript via reflection
-keepclassmembers class org.astermail.android.ui.auth.TurnstileBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Error Prone / javax annotations (compile-only, safe to ignore)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
