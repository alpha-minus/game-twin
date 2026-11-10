package com.twinlore.app.data.license

import android.content.Context
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest

/**
 * Generates stable device fingerprint for license activation
 */
@Singleton
class DeviceFingerprintGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceFingerprint"
    }
    
    /**
     * Generate stable device identifier
     * Uses Android ID as primary identifier, with fallback to package signature hash
     */
    fun generateFingerprint(): String {
        return try {
            // Primary: Android ID (stable across app installs, changes on factory reset)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            Log.d(TAG, "Package name: ${context.packageName}")
            
            // Fallback: Use package name + build info if Android ID is unavailable
            val identifier = androidId?.takeIf { it.isNotEmpty() && it != "9774d56d682e549c" }
                ?: generateFallbackFingerprint()

            // Create a simple hash for consistent format
            val fingerprint = hashString(identifier)
            fingerprint
        } catch (e: Exception) {
            Log.e(TAG, "Error generating device fingerprint", e)
            // Last resort: use package name hash
            val fallbackFingerprint = hashString(context.packageName)
            fallbackFingerprint
        }
    }
    
    /**
     * Generate fallback fingerprint using package name and build info
     */
    private fun generateFallbackFingerprint(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val packageName = context.packageName
            val versionCode = packageInfo.longVersionCode.toString()
            "${packageName}_${versionCode}"
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fallback fingerprint", e)
            context.packageName
        }
    }
    
    /**
     * Create simple hash of string
     */
    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }.take(16) // Use first 16 chars
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing string", e)
            input
        }
    }
    
    /**
     * Generate human-readable device name for instance_name
     */
    fun generateDeviceName(): String {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
            "Android Device ($deviceName)"
        } catch (e: Exception) {
            Log.e(TAG, "Error generating device name", e)
            "Android Device"
        }
    }
}
