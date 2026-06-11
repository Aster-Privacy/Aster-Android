-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keep class org.astermail.android.crypto.** { *; }

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
