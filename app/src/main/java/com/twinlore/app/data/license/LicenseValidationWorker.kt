package com.twinlore.app.data.license

import android.util.Log
import com.twinlore.app.data.NetworkConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles background license validation
 * Runs validation checks periodically when conditions are met
 */
@Singleton
class LicenseValidationWorker @Inject constructor(
    private val licenseManager: LicenseManager,
    private val networkConnectivityManager: NetworkConnectivityManager
) {
    companion object {
        private const val TAG = "LicenseValidationWorker"
    }
    
    private val supervisorJob = SupervisorJob()
    private val workerScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    
    fun cancel() {
        supervisorJob.cancel()
    }
    
    /**
     * Trigger background validation if conditions are met
     * Non-blocking, runs asynchronously
     */
    fun triggerValidation() {
        workerScope.launch {
            try {
                // Check if network is available
                if (!networkConnectivityManager.isNetworkAvailable()) {
                    Log.d(TAG, "Network not available, skipping validation")
                    return@launch
                }
                
                // Check if validation should be performed
                if (!licenseManager.shouldPerformValidation()) {
                    Log.d(TAG, "Validation not needed yet")
                    return@launch
                }
                
                // Perform validation
                Log.d(TAG, "Starting background license validation")
                val success = licenseManager.startBackgroundValidation()
                
                if (success) {
                    Log.d(TAG, "Background validation completed successfully")
                } else {
                    Log.d(TAG, "Background validation failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in background validation worker", e)
            }
        }
    }
}
