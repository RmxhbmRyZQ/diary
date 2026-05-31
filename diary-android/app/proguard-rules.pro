# ============================================================
# 隐秘日记 ProGuard / R8 规则
# 重点关注：Retrofit 泛型反射、Gson 序列化、Room、Hilt DI
# ============================================================

# R8 full mode 的优化器会重写泛型签名，导致 Retrofit 运行时崩溃。
# 关闭全局优化——仍保留代码压缩（shrink）和混淆（obfuscate）。
-dontoptimize

# ── 字节码属性（Retrofit/Gson 反射必须）──
-keepattributes Signature
-keepattributes InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes LineNumberTable

# ============================================================
# Retrofit 服务接口
# ============================================================
# 关键：Kotlin suspend 函数的 JVM 返回值是 Object，
# Retrofit 必须从 Signature 属性读取 Response<ApiResponse<...>> 的泛型。
# 任何 allowshrinking/allowoptimization 都可能导致 R8 移除泛型类型参数。

-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ApiService 显式全保留（不使用 allowshrinking——泛型类型参数引用的
# DTO 可能被 R8 误判为"未使用"而删除）
-keep interface com.secretdiary.app.data.remote.api.ApiService {
    <methods>;
}

-dontwarn retrofit2.**
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions*

# ============================================================
# DTO（Gson 序列化 + Retrofit 泛型参数）
# ============================================================
# 使用严格 -keep：R8 只能通过 ApiService 的 Signature 属性间接
# 发现 DTO 的引用（如 ConfigResponse），allowshrinking 可能误删。
-keep class com.secretdiary.app.data.remote.dto.** {
    <fields>;
    <init>(...);
}

# @SerializedName 的字段名必须保留（JSON key 映射）
-keepclassmembers class com.secretdiary.app.data.remote.dto.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson 类型适配器工厂
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================================
# Kotlin（suspend / coroutines / metadata）
# ============================================================
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================================
# Room（实体、DAO、Database）
# ============================================================
-keep class com.secretdiary.app.data.local.entity.** {
    <fields>;
    <init>(...);
}
-keep @androidx.room.Entity class * { <fields>; <init>(...); }
-keep @androidx.room.Dao interface * { <methods>; }
-keep class * extends androidx.room.RoomDatabase {
    <methods>;
    <fields>;
}

# ============================================================
# Hilt / Dagger DI
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Hilt 生成的类（@HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel）
-keep class com.secretdiary.app.Hilt_* { *; }
-keep class com.secretdiary.app.*_Factory { *; }
-keep class com.secretdiary.app.*_MembersInjector { *; }
-keep class com.secretdiary.app.*_HiltModules { *; }
-keep class com.secretdiary.app.*_HiltModules_* { *; }
-dontwarn com.secretdiary.app.SecretDiaryApplication_GeneratedInjector

# ============================================================
# 安全模块
# ============================================================
-keep class com.secretdiary.app.security.** {
    public <methods>;
    <init>(...);
}
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }
-dontwarn javax.crypto.**
-dontwarn com.google.errorprone.annotations.**

# Android Keystore & EncryptedSharedPreferences
-keep class android.security.keystore.** { *; }
-keep class androidx.security.crypto.** { *; }

# ============================================================
# Coil + 自定义 AttachmentFetcher
# ============================================================
-keep class com.secretdiary.app.ui.components.AttachmentFetcher { *; }
-keep class com.secretdiary.app.ui.components.AttachmentFetcher$Factory { *; }
-keep class coil.** { *; }

# ============================================================
# Biometric
# ============================================================
-keep class androidx.biometric.** { *; }

# ============================================================
# Markwon
# ============================================================
-keep class io.noties.markwon.** { *; }

# ============================================================
# 通用
# ============================================================
# 保留数据类的无参构造（Gson 需要）
-keepclassmembers class * {
    <init>(...);
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
