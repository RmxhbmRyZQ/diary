# 隐秘日记 ProGuard 规则

# ── 保留加密类（javax.crypto） ──
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-dontwarn javax.crypto.**

# ── 保留 CryptoManager ──
-keep class com.secretdiary.app.security.CryptoManager { *; }
-keep class com.secretdiary.app.security.SessionManager { *; }

# ── 保留 Room 实体 ──
-keep class com.secretdiary.app.data.local.entity.** { *; }

# ── 保留 Gson 序列化的 DTO ──
-keep class com.secretdiary.app.data.remote.dto.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.google.gson.** { *; }
-keepattributes com.google.gson.annotations.SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── 保留 Hilt ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── 保留 Retrofit/OkHttp ──
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# ── 保留 Coil ──
-keep class coil.** { *; }

# ── 保留 Android Keystore ──
-keep class android.security.keystore.** { *; }

# ── 通用保留 ──
-keepattributes Exceptions,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
