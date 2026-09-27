# ============================================================
# 影视YUAN 混淆规则（R8）
# ============================================================

# ---------- Gson 反射模型类（防止 JSON 字段被裁剪/重命名） ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# 本项目参与 JSON 解析的数据模型/网络解析层整体保留
-keep class com.videobox.movie.data.** { *; }
-keep class com.videobox.movie.net.** { *; }

# 自定义 HLS 广告过滤数据源（被 ExoPlayer 以工厂方式引用）
-keep class com.videobox.movie.player.FilteringDataSource { *; }

# ---------- Glide（自带 consumer 规则，补充保活注解/模块） ----------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }

# ---------- 清单引用的入口类（保险起见显式保留，避免混淆后类名变化） ----------
-keep public class com.videobox.movie.SplashActivity
-keep public class com.videobox.movie.MainActivity
-keep public class com.videobox.movie.App
