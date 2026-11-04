package ca.cheungeric.lounge.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Lower-case hex SHA-256 of everything [input] yields (does not close it). */
fun sha256Hex(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    return md.digest().toHex()
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** Case-insensitive, whitespace-tolerant hex comparison; blank expected never matches. */
fun sha256Matches(expected: String, actual: String): Boolean {
    val e = expected.trim()
    return e.isNotEmpty() && e.equals(actual.trim(), ignoreCase = true)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Downloads, verifies (sha256) and installs an APK via a PackageInstaller session. */
class UpdateInstaller(private val context: Context) {

    /**
     * Runs the whole install flow, reporting progress through [onState]. Returns the last state:
     * [UpdateState.Installing] once the session is committed (the result then arrives via
     * [InstallStatusReceiver]), [UpdateState.PermissionRequired] or [UpdateState.Failed].
     */
    suspend fun install(info: VersionInfo, onState: (UpdateState) -> Unit = {}): UpdateState {
        val result = runCatching { doInstall(info, onState) }
            .getOrElse { UpdateState.Failed("Update failed: ${it.message ?: it.javaClass.simpleName}", info) }
        onState(result)
        return result
    }

    private suspend fun doInstall(info: VersionInfo, onState: (UpdateState) -> Unit): UpdateState {
        val app = context.applicationContext
        val pm = app.packageManager
        if (!pm.canRequestPackageInstalls()) {
            openUnknownSourcesSettings(app)
            return UpdateState.PermissionRequired(info)
        }

        val apk = File(app.cacheDir, APK_NAME)
        try {
            onState(UpdateState.Downloading(info, 0f))
            val hash = download(info.apkUrl, apk) { p -> onState(UpdateState.Downloading(info, p)) }
            if (!sha256Matches(info.sha256, hash)) {
                return UpdateState.Failed("Download was corrupted (checksum mismatch). Try again.", info)
            }
            onState(UpdateState.Installing)
            commitSession(app, apk)
            return UpdateState.Installing
        } finally {
            apk.delete()
        }
    }

    private fun openUnknownSourcesSettings(app: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (_: Exception) {
            // Some TV builds lack this screen; the PermissionRequired message still tells the user what to do.
        }
    }

    /** Streams [url] into [dest], returning its sha256 hex. */
    private suspend fun download(url: String, dest: File, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
                conn.setRequestProperty("Cache-Control", "no-cache")
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
                val total = conn.contentLengthLong
                val md = MessageDigest.getInstance("SHA-256")
                var read = 0L
                var lastReported = -1
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            md.update(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                if (total <= 0) onProgress(1f)
                md.digest().toHex()
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun commitSession(app: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(app, InstallStatusReceiver::class.java).setPackage(app.packageName)
            // PackageInstaller fills in status extras, so the PendingIntent must be mutable on API 31+.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(app, sessionId, intent, flags)
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private companion object {
        const val APK_NAME = "update.apk"
        const val TIMEOUT_MS = 10_000
    }
}
