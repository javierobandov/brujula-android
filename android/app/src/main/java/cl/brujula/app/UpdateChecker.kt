package cl.brujula.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdate(val version: String, val downloadUrl: String)

class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    fun check(onResult: (Result<AppUpdate?>) -> Unit) {
        Thread {
            try {
                val request = Request.Builder()
                    .url(RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("GitHub respondió ${response.code}")
                    val json = JSONObject(response.body?.string().orEmpty())
                    val version = json.optString("tag_name").removePrefix("v")
                    val apk = json.optJSONArray("assets")?.let { assets ->
                        (0 until assets.length()).asSequence()
                            .map { assets.getJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk") }
                    }
                    val result = if (version.isBlank() || apk == null) null else AppUpdate(version, apk.optString("browser_download_url"))
                    onMain { onResult(Result.success(result)) }
                }
            } catch (error: Throwable) {
                onMain { onResult(Result.failure(error)) }
            }
        }.start()
    }

    fun downloadAndInstall(update: AppUpdate, onStatus: (String) -> Unit) {
        val folder = File(context.getExternalFilesDir("Download"), "updates").apply { mkdirs() }
        val apk = File(folder, "brujula-${update.version}.apk")
        if (apk.exists()) apk.delete()
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Brújula ${update.version}")
            .setDescription("Descargando actualización")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(apk))
        val manager = context.getSystemService(DownloadManager::class.java)
        val downloadId = manager.enqueue(request)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                receiverContext.unregisterReceiver(this)
                val uri = FileProvider.getUriForFile(receiverContext, "${receiverContext.packageName}.fileprovider", apk)
                receiverContext.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                onMain { onStatus("Descarga lista. Confirma la instalación de Android.") }
            }
        }
        val flags = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_NOT_EXPORTED else 0
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flags)
        onStatus("Descargando actualización…")
    }

    private fun onMain(action: () -> Unit) = android.os.Handler(context.mainLooper).post(action)

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/javierobandov/brujula-android/releases/latest"
    }
}
