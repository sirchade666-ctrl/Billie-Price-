package com.example.data.model

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    NEEDS_SOURCE_CONFIG,
    ERROR
}

data class AppReleaseInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadPageUrl: String,
    val publishedAt: String = ""
)

data class AppUpdateUiState(
    val currentVersionName: String = "2.0",
    val currentVersionCode: Int = 2,
    val updateSource: String = "",
    val autoCheckOnLaunch: Boolean = true,
    val skippedVersion: String = "",
    val status: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val availableRelease: AppReleaseInfo? = null,
    val showUpdatePopup: Boolean = false,
    val showUpdateSettingsDialog: Boolean = false,
    val statusMessage: String = "",
    val lastCheckedMs: Long = 0L
)
