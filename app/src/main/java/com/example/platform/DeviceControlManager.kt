package com.example.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.data.model.BatteryStatusInfo
import com.example.data.model.LocationSnapshot
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

data class DeviceActionResult(
    val success: Boolean,
    val message: String
)

data class InstalledAppSummary(
    val label: String,
    val packageName: String
)

class DeviceControlManager(private val context: Context) {

    fun readBatteryStatus(): BatteryStatusInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryStatusInfo()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargeSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (charging) "Charging" else "On Battery"
        }

        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        return BatteryStatusInfo(
            percentage = pct,
            charging = charging,
            chargeSource = chargeSource,
            healthLabel = health,
            temperatureCelsius = tempTenths / 10f,
            voltageMv = voltage
        )
    }

    fun hasPermission(permission: String): Boolean {
        if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings(): DeviceActionResult {
        return launchIntent(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            },
            successMsg = "Opened Android App Settings for Shubham AI.",
            unsupportedMsg = "Unable to open App Settings on this device."
        )
    }

    fun openYouTube(query: String = ""): DeviceActionResult {
        val uri = if (query.isBlank()) {
            Uri.parse("https://www.youtube.com")
        } else {
            Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query.trim(), "UTF-8")}")
        }
        val ytIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
        }
        return if (ytIntent.resolveActivity(context.packageManager) != null) {
            launchIntent(ytIntent, "Opened YouTube.", "YouTube app could not be opened.")
        } else {
            launchIntent(
                Intent(Intent.ACTION_VIEW, uri),
                "YouTube app not installed; opened YouTube in browser.",
                "No browser or YouTube app available to handle this action."
            )
        }
    }

    fun openBrowserSearch(query: String): DeviceActionResult {
        val clean = query.trim()
        val url = if (clean.startsWith("https://") || clean.startsWith("http://")) {
            clean
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(clean.ifBlank { "latest news" }, "UTF-8")}"
        }
        return launchIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            successMsg = "Opened browser search for \"${clean.ifBlank { "latest news" }}\".",
            unsupportedMsg = "No web browser is installed to open this link."
        )
    }

    fun openUrl(url: String): DeviceActionResult {
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("https://") && !safeUrl.startsWith("http://")) {
            return DeviceActionResult(false, "Blocked invalid URL scheme: only http/https links are permitted.")
        }
        return launchIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)),
            successMsg = "Opened link in browser.",
            unsupportedMsg = "No browser installed to open external URLs."
        )
    }

    fun openDialerWithNumber(rawNumber: String): DeviceActionResult {
        val sanitized = sanitizePhoneNumber(rawNumber)
        if (sanitized.isEmpty()) {
            return DeviceActionResult(false, "Enter a valid phone number before opening the dialer.")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized"))
        return launchIntent(
            intent,
            successMsg = "Opened Phone Dialer with $sanitized.",
            unsupportedMsg = "Phone dialer is not supported on this device."
        )
    }

    /**
     * Must ONLY be called AFTER an explicit in-app confirmation dialog is approved by the user.
     * Uses ACTION_CALL only if CALL_PHONE permission is granted; otherwise safely falls back to ACTION_DIAL.
     */
    @SuppressLint("MissingPermission")
    fun initiateConfirmedCall(rawNumber: String): DeviceActionResult {
        val sanitized = sanitizePhoneNumber(rawNumber)
        if (sanitized.isEmpty()) {
            return DeviceActionResult(false, "Invalid phone number.")
        }
        return if (hasPermission(Manifest.permission.CALL_PHONE)) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$sanitized"))
            launchIntent(
                callIntent,
                successMsg = "Initiating confirmed call to $sanitized.",
                unsupportedMsg = "Direct phone calling is unsupported on this device."
            )
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized"))
            launchIntent(
                dialIntent,
                successMsg = "CALL_PHONE permission not granted; opened Phone Dialer with $sanitized for manual tap.",
                unsupportedMsg = "Phone dialer is unsupported on this device."
            )
        }
    }

    fun openMusicApp(): DeviceActionResult {
        val candidates = listOf(
            "com.google.android.apps.youtube.music",
            "com.spotify.music"
        )
        for (pkg in candidates) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                return launchIntent(launch, "Opened music app ($pkg).", "Failed to open music app.")
            }
        }
        val categoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
        }
        return launchIntent(
            categoryIntent,
            successMsg = "Opened default Music application.",
            unsupportedMsg = "No compatible music application found on this device."
        )
    }

    fun toggleMediaPlayPause(): DeviceActionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return DeviceActionResult(false, "Android AudioManager is unavailable.")
            val eventTime = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
            )
            DeviceActionResult(
                true,
                "Dispatched Play/Pause media key to active media session (works when a media player is active)."
            )
        } catch (e: Exception) {
            DeviceActionResult(false, "Media key dispatch unsupported: ${e.localizedMessage}")
        }
    }

    fun listLaunchableApps(): List<InstalledAppSummary> {
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = context.packageManager.queryIntentActivities(mainIntent, 0)
        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
            if (label.isBlank()) null else InstalledAppSummary(label = label, packageName = pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.US) }
    }

    fun launchInstalledApp(packageName: String, label: String): DeviceActionResult {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return DeviceActionResult(false, "$label ($packageName) is not installed or cannot be launched.")
        return launchIntent(launch, "Launched $label.", "Could not launch $label.")
    }

    fun launchAppByName(nameQuery: String): DeviceActionResult {
        val clean = nameQuery.trim()
        if (clean.isEmpty()) return DeviceActionResult(false, "Please specify an app name to open.")
        val apps = listLaunchableApps()
        val exact = apps.firstOrNull { it.label.equals(clean, ignoreCase = true) }
        val partial = exact ?: apps.firstOrNull {
            it.label.contains(clean, ignoreCase = true) || it.packageName.contains(clean, ignoreCase = true)
        }
        return if (partial != null) {
            launchInstalledApp(partial.packageName, partial.label)
        } else {
            DeviceActionResult(
                false,
                "Could not find an installed launchable app matching \"$clean\" on this device."
            )
        }
    }

    fun hasFlashlightHardware(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    fun setFlashlight(enable: Boolean): DeviceActionResult {
        if (!hasFlashlightHardware()) {
            return DeviceActionResult(
                false,
                "Flashlight hardware is not available on this device or emulator."
            )
        }
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return DeviceActionResult(false, "Android CameraManager service is unavailable.")
            val torchCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (torchCameraId == null) {
                DeviceActionResult(false, "No camera with an addressable LED flash unit was found.")
            } else {
                cameraManager.setTorchMode(torchCameraId, enable)
                DeviceActionResult(
                    true,
                    if (enable) "Flashlight turned ON." else "Flashlight turned OFF."
                )
            }
        } catch (e: SecurityException) {
            DeviceActionResult(false, "Flashlight access was denied by Android security policy: ${e.localizedMessage}")
        } catch (e: Exception) {
            DeviceActionResult(false, "Unable to toggle flashlight right now: ${e.localizedMessage ?: "Camera busy or unsupported"}")
        }
    }

    fun openMapsNavigation(destination: String): DeviceActionResult {
        val clean = destination.trim()
        val uri = if (clean.isEmpty()) {
            Uri.parse("geo:0,0")
        } else {
            Uri.parse("geo:0,0?q=${URLEncoder.encode(clean, "UTF-8")}")
        }
        return launchIntent(
            Intent(Intent.ACTION_VIEW, uri),
            successMsg = "Opened Maps navigation for \"${clean.ifBlank { "current area" }}\".",
            unsupportedMsg = "No Maps or navigation app is installed on this device."
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(usePrecise: Boolean): Result<LocationSnapshot> {
        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) {
            return Result.failure(SecurityException("Location permission has not been granted."))
        }
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val priority = if (usePrecise && hasFine) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
            val cts = CancellationTokenSource()
            val location = client.getCurrentLocation(priority, cts.token).await()
                ?: client.lastLocation.await()
                ?: return Result.failure(
                    IllegalStateException("Location unavailable right now. Ensure device GPS/Location is enabled.")
                )

            val areaLabel = withContext(Dispatchers.IO) {
                resolveAreaName(location.latitude, location.longitude)
            }
            Result.success(
                LocationSnapshot(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    precisionLabel = if (usePrecise && hasFine) "Precise GPS" else "Approximate",
                    areaLabel = areaLabel
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveAreaName(lat: Double, lng: Double): String {
        return try {
            if (!Geocoder.isPresent()) return "Coordinates only (Geocoder unavailable)"
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            listOfNotNull(addr?.locality, addr?.adminArea, addr?.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Unknown area" }
        } catch (_: Exception) {
            "Coordinates only"
        }
    }

    fun sanitizePhoneNumber(input: String): String =
        input.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(20)

    private fun launchIntent(
        intent: Intent,
        successMsg: String,
        unsupportedMsg: String
    ): DeviceActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                DeviceActionResult(false, unsupportedMsg)
            } else {
                context.startActivity(intent)
                DeviceActionResult(true, successMsg)
            }
        } catch (e: Exception) {
            DeviceActionResult(false, "$unsupportedMsg (${e.localizedMessage})")
        }
    }
}
