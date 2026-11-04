package ca.cheungeric.lounge.update

/** What the self-updater is doing; observed by the Home banner and Settings. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: VersionInfo) : UpdateState
    /** [progress] in 0..1, or -1 when the server sent no length. */
    data class Downloading(val info: VersionInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    /**
     * Android's "Install unknown apps" screen for Lounge was opened; the user must allow it and then
     * press Install again.
     */
    data class PermissionRequired(val info: VersionInfo) : UpdateState
    data class Failed(val message: String, val info: VersionInfo? = null) : UpdateState
    data object UpToDate : UpdateState
}
