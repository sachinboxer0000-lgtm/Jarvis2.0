package com.example.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MobileControlAgent(private val context: Context) {

    suspend fun launchAppByName(appName: String): Result<String> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val cleanName = appName.trim().lowercase()

        // Well-known packages mapping for high accuracy
        val knownApps = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "camera" to null, // Can launch via MediaStore.ACTION_IMAGE_CAPTURE
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "calculator" to "com.google.android.calculator",
            "play store" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "clock" to "com.google.android.deskclock"
        )

        val targetPkg = knownApps[cleanName]
        if (targetPkg != null) {
            val intent = pm.getLaunchIntentForPackage(targetPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return@withContext Result.success("Launched $appName ($targetPkg)")
            }
        }

        // Search installed applications
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(cleanName) || cleanName.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return@withContext Result.success("Launched ${pm.getApplicationLabel(app)}")
                }
            }
        }

        // If not installed, offer Play Store link
        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$appName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(playStoreIntent)
            Result.success("App '$appName' not found locally. Opened Google Play Store search.")
        } catch (_: Exception) {
            Result.failure(Exception("Could not find or launch application '$appName'"))
        }
    }

    suspend fun searchYouTube(query: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            val encodedQuery = Uri.encode(query)
            val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                return@withContext Result.success("Searching YouTube for '$query'")
            }

            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            Result.success("Opened YouTube search for '$query'")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openUrl(url: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Result.success("Opened $fullUrl")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openSettings(settingType: String = ""): Result<String> = withContext(Dispatchers.Default) {
        try {
            val action = when (settingType.lowercase()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Result.success("Opened Android Settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
