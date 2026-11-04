package ca.cheungeric.lounge.update

import android.content.Context

/** Downloads, verifies (sha256) and installs an APK via a PackageInstaller session. TODO(T6). */
class UpdateInstaller(private val context: Context) {
    suspend fun install(info: VersionInfo) {
        // TODO(T6)
    }
}
