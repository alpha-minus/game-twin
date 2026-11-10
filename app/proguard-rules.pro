# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Security Crypto (Tink) – errorprone annotations are compile-only, not on classpath at runtime
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase$Callback
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class kotlin.coroutines.** { *; }

# Keep data classes
-keepclassmembers class * {
    <fields>;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep PdfBox-Android classes for PDF text search
-keep class com.tom.roush.pdfbox.** { *; }
-keepclassmembers class com.tom.roush.pdfbox.** { *; }
-dontwarn com.tom.roush.pdfbox.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.bouncycastle.**
-dontwarn javax.crypto.**
# Keep all PdfBox resources
# Note: R8 doesn't support -keepresources, resources are kept by default
# -keepresources assets/com/tom_roush/pdfbox/**
# Keep BouncyCastle classes (PDFBox dependency)
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** {
    *;
}
# Keep PDFBoxResourceLoader initialization class
-keep class com.tom.roush.pdfbox.android.PDFBoxResourceLoader { *; }
-keepclassmembers class com.tom.roush.pdfbox.android.PDFBoxResourceLoader {
    *;
}

# Keep Gson classes and annotations
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gson TypeToken - critical for generic type reflection
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <fields>;
    <methods>;
}

# Generic Gson DTO protection — any class with @SerializedName-annotated fields
# needs its constructors preserved or Gson's ConstructorConstructor runs out of
# allocation strategies and throws `JsonIOException: Abstract classes can't be
# instantiated`. Diagnosed on AYN Thor release build, 2026-06-09, against
# RetroArchHistoryParser$HistoryJson — debug skips R8 so the bug is release-only.
# This rule supersedes the manual whack-a-mole pattern below (license response
# classes); leaving the per-class rules in place as belt-and-suspenders. Belt is
# also `= null` defaults on each DTO field so Kotlin synthesizes a no-arg ctor.
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    <init>(...);
}

# AI assistant Gson DTOs — no @SerializedName, so the generic rule above doesn't
# cover them. Keep names + members so Gson can instantiate without the Unsafe path
# that R8 strips (see RetroArchHistoryParser$HistoryJson precedent). Release-only bug.
-keep class com.twinlore.app.data.ai.AiMessage { *; }
-keep class com.twinlore.app.data.ai.AiSession { *; }
-keep class com.twinlore.app.data.ai.AiRole { *; }

# Keep Retrofit interfaces and classes
-keep interface retrofit2.Call
-keep class retrofit2.Response { *; }
-keep class retrofit2.HttpException { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Lemon Squeezy API response models (Gson deserialization)
# Prevent obfuscation - Gson uses reflection and needs original class names
-keepnames class com.twinlore.app.data.license.ActivationResponse { *; }
-keepnames class com.twinlore.app.data.license.ValidationResponse { *; }
-keepnames class com.twinlore.app.data.license.DeactivationResponse { *; }
-keepnames class com.twinlore.app.data.license.LicenseKeyResponse { *; }
-keepnames class com.twinlore.app.data.license.LicenseInstanceResponse { *; }
-keepnames class com.twinlore.app.data.license.LicenseMetaResponse { *; }
-keepnames class com.twinlore.app.data.license.LicenseStatus { *; }

# Keep all fields and constructors in license API response classes (needed for Gson)
-keepclassmembers class com.twinlore.app.data.license.*Response {
    <fields>;
    <init>();
    <init>(...);
}

# Explicitly keep constructors - prevent ProGuard from removing them
-keepclassmembers class com.twinlore.app.data.license.ActivationResponse {
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.license.ValidationResponse {
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.license.DeactivationResponse {
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.license.LicenseKeyResponse {
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.license.LicenseInstanceResponse {
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.license.LicenseMetaResponse {
    <init>(...);
}

# Prevent these classes from being made abstract
-keep,allowobfuscation class com.twinlore.app.data.license.*Response

# Keep license data classes
-keep class com.twinlore.app.data.license.StoredLicenseData { *; }
-keep class com.twinlore.app.data.license.LicenseValidationResult { *; }

# Keep sealed classes (LicenseState, Result) - prevent ProGuard from making them abstract
-keep class com.twinlore.app.data.license.LicenseState { *; }
-keep class com.twinlore.app.data.license.LicenseState$* { *; }
-keep class com.twinlore.app.domain.Result { *; }
-keep class com.twinlore.app.domain.Result$* { *; }

# Keep all data classes used in API responses (prevent obfuscation)
-keep class com.twinlore.app.data.license.** { *; }

# Keep SteamGridDB API response models (Gson deserialization)
# Prevent obfuscation - Gson uses reflection and needs original class names and field names
-keepnames class com.twinlore.app.data.remote.SteamGridSearchResult { *; }
-keepnames class com.twinlore.app.data.remote.SteamGridAsset { *; }
-keepnames class com.twinlore.app.data.remote.SteamGridResponse { *; }

# Keep all fields and constructors in SteamGridDB API response classes (needed for Gson)
-keepclassmembers class com.twinlore.app.data.remote.SteamGridSearchResult {
    <fields>;
    <init>();
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.remote.SteamGridAsset {
    <fields>;
    <init>();
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.remote.SteamGridResponse {
    <fields>;
    <init>();
    <init>(...);
}

# Keep Retrofit interface for SteamGridDB API
-keep interface com.twinlore.app.data.remote.SteamGridApi { *; }
-keepclassmembers interface com.twinlore.app.data.remote.SteamGridApi {
    <methods>;
}

# Keep Steam Web API DTOs (Gson deserialization)
-keep class com.twinlore.app.data.steam.Steam* { *; }
-keepclassmembers class com.twinlore.app.data.steam.Steam* {
    <fields>;
    <init>();
    <init>(...);
}
-keep interface com.twinlore.app.data.steam.SteamApi { *; }
-keep class com.twinlore.app.data.steam.CachedSteamSnapshot { *; }

# Keep RetroAchievements API and DTOs (Gson)
-keepnames class com.twinlore.app.data.retroachievements.Ra* { *; }
-keepclassmembers class com.twinlore.app.data.retroachievements.Ra* {
    <fields>;
    <init>();
    <init>(...);
}

# Keep GitHub Releases API and DTOs (Gson) - update checker
-keep interface com.twinlore.app.data.update.GitHubReleasesApi { *; }
-keepclassmembers interface com.twinlore.app.data.update.GitHubReleasesApi {
    <methods>;
}
-keep class com.twinlore.app.data.update.GitHubRelease { *; }
-keep class com.twinlore.app.data.update.GitHubAsset { *; }
-keepclassmembers class com.twinlore.app.data.update.GitHubRelease {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.update.GitHubAsset {
    <fields>;
    <init>(...);
}

# Keep scraping coordinator and sealed classes
-keep class com.twinlore.app.data.scraping.VaultScrapeCoordinator { *; }
-keep class com.twinlore.app.data.scraping.VaultScrapeCoordinator$* { *; }
-keep class com.twinlore.app.data.scraping.SteamGridClient { *; }
-keep class com.twinlore.app.data.scraping.GameNameSanitizer { *; }

# Keep sealed classes (ScrapingProgress, AssetScrapeResult) - prevent ProGuard from making them abstract
-keep class com.twinlore.app.data.scraping.VaultScrapeCoordinator$ScrapingProgress { *; }
-keep class com.twinlore.app.data.scraping.VaultScrapeCoordinator$ScrapingProgress$* { *; }
-keep class com.twinlore.app.data.scraping.VaultScrapeCoordinator$AssetScrapeResult { *; }
-keep class com.twinlore.app.data.scraping.VaultScrapeCoordinator$AssetScrapeResult$* { *; }

# Keep all scraping-related classes
-keep class com.twinlore.app.data.scraping.** { *; }

# Keep RetroArch history data classes (Gson deserialization)
# Prevent obfuscation - Gson uses reflection and needs original class names and field names
-keepnames class com.twinlore.app.data.RetroArchHistoryData { *; }
-keepnames class com.twinlore.app.data.RetroArchHistoryItem { *; }
-keepnames class com.twinlore.app.data.RetroArchHistoryEntry { *; }

# Keep all fields and constructors in RetroArch history classes (needed for Gson)
-keepclassmembers class com.twinlore.app.data.RetroArchHistoryData {
    <fields>;
    <init>();
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.RetroArchHistoryItem {
    <fields>;
    <init>();
    <init>(...);
}
-keepclassmembers class com.twinlore.app.data.RetroArchHistoryEntry {
    <fields>;
    <init>();
    <init>(...);
}

# Prevent these classes from being made abstract
-keep,allowobfuscation class com.twinlore.app.data.RetroArchHistoryData
-keep,allowobfuscation class com.twinlore.app.data.RetroArchHistoryItem
-keep,allowobfuscation class com.twinlore.app.data.RetroArchHistoryEntry

# Keep domain data classes used with Gson TypeToken reflection
-keep class com.twinlore.app.domain.ScrollMarker { *; }
-keep class com.twinlore.app.domain.MapMarker { *; }
-keep class com.twinlore.app.domain.ScrollRestoreData { *; }
-keep class com.twinlore.app.domain.MarkerStyle { *; }
-keepclassmembers class com.twinlore.app.domain.ScrollMarker {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.twinlore.app.domain.MapMarker {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.twinlore.app.domain.ScrollRestoreData {
    <fields>;
    <init>(...);
}

# Keep PDF WebView JavaScript bridge — R8 must not strip @JavascriptInterface methods
-keep class com.twinlore.app.ui.screens.pdf.PdfBridge { *; }
-keepclassmembers class com.twinlore.app.ui.screens.pdf.PdfBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Release builds ship no Log.v / Log.d. Warn and error stay; anything that
# must not appear there is removed at the call site instead.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
