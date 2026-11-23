package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.Locale

enum class StorageTargetMode(val label: String) {
    AUTO_SD_PREFERRED("Auto (SD)"),
    INTERNAL_ONLY("Internal"),
    CUSTOM_FOLDER("Folder")
}

data class DetectedStorageVolume(
    val label: String,
    val path: String,
    val isRemovable: Boolean,
    val freeBytes: Long,
    val totalBytes: Long
)

data class StorageStatus(
    val removableSdCard: DetectedStorageVolume? = null,
    val internalStorage: DetectedStorageVolume = DetectedStorageVolume("Internal Storage", "", false, 0L, 0L),
    val selectedMode: StorageTargetMode = StorageTargetMode.AUTO_SD_PREFERRED,
    val customTreeUri: String? = null,
    val customFolderLabel: String? = null,
    val activeDestinationLabel: String = "Internal Storage"
)

class StorageLocationManager(private val context: Context) {

    fun detectVolumes(
        selectedMode: StorageTargetMode,
        customTreeUri: String?,
        customFolderLabel: String?
    ): StorageStatus {
        val externalDirs = context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC).filterNotNull()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        var sdVolume: DetectedStorageVolume? = null
        var internalVol: DetectedStorageVolume? = null

        for (dir in externalDirs) {
            val state = runCatching { Environment.getExternalStorageState(dir) }.getOrNull()
            if (state != Environment.MEDIA_MOUNTED) continue

            val removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
            val stats = readStatFs(dir)
            val volLabel = resolveVolumeDescription(storageManager, dir, removable)

            val detected = DetectedStorageVolume(
                label = volLabel,
                path = dir.absolutePath,
                isRemovable = removable,
                freeBytes = stats.first,
                totalBytes = stats.second
            )

            if (removable && sdVolume == null) {
                sdVolume = detected
            } else if (!removable && internalVol == null) {
                internalVol = detected
            }
        }

        if (internalVol == null) {
            val fallback = context.filesDir
            val stats = readStatFs(fallback)
            internalVol = DetectedStorageVolume(
                label = "Internal App Storage",
                path = fallback.absolutePath,
                isRemovable = false,
                freeBytes = stats.first,
                totalBytes = stats.second
            )
        }

        val activeLabel = when (selectedMode) {
            StorageTargetMode.AUTO_SD_PREFERRED -> {
                if (sdVolume != null) "SD Card (${sdVolume.label})" else internalVol.label
            }
            StorageTargetMode.INTERNAL_ONLY -> internalVol.label
            StorageTargetMode.CUSTOM_FOLDER -> {
                customFolderLabel?.takeIf { it.isNotBlank() } ?: "Choose Transfer Folder"
            }
        }

        return StorageStatus(
            removableSdCard = sdVolume,
            internalStorage = internalVol,
            selectedMode = selectedMode,
            customTreeUri = customTreeUri,
            customFolderLabel = customFolderLabel,
            activeDestinationLabel = activeLabel
        )
    }

    fun resolveRecordingDirectory(status: StorageStatus): File {
        val baseDir = when (status.selectedMode) {
            StorageTargetMode.AUTO_SD_PREFERRED -> {
                status.removableSdCard?.let { File(it.path) } ?: File(status.internalStorage.path)
            }
            StorageTargetMode.INTERNAL_ONLY,
            StorageTargetMode.CUSTOM_FOLDER -> {
                // Local capture writes to fastest local dir first; CUSTOM_FOLDER mirrors immediately after stop
                File(status.internalStorage.path)
            }
        }
        val recDir = File(baseDir, "recordings")
        if (!recDir.exists()) recDir.mkdirs()
        return recDir
    }

    fun resolveSegmentsDirectory(status: StorageStatus): File {
        val segDir = File(resolveRecordingDirectory(status), "segments")
        if (!segDir.exists()) segDir.mkdirs()
        return segDir
    }

    fun resolveTransferDirectoryOnSdOrInternal(status: StorageStatus): File {
        val base = status.removableSdCard?.let { File(it.path) } ?: File(status.internalStorage.path)
        val transferDir = File(base, "GeoportTransfers")
        if (!transferDir.exists()) transferDir.mkdirs()
        return transferDir
    }

    fun persistTreePermission(uri: Uri): String {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: uri.lastPathSegment ?: "Selected Folder"
        return docId.substringAfterLast(':').ifBlank { docId }
    }

    suspend fun copyFileToSafFolder(
        treeUri: Uri,
        sourceFile: File,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext false
        writeStreamToSafFolder(treeUri, sourceFile.name, mimeType) { os ->
            sourceFile.inputStream().use { input -> input.copyTo(os) }
        }
    }

    suspend fun writeTextToSafFolder(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        writeStreamToSafFolder(treeUri, fileName, mimeType) { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    private fun writeStreamToSafFolder(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            // Create document inside the chosen folder
            val createdUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                mimeType,
                fileName
            ) ?: return@runCatching false

            resolver.openOutputStream(createdUri)?.use { os ->
                writer(os)
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    suspend fun copyFileToDir(sourceFile: File, destDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext false
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, sourceFile.name)
        if (destFile.absolutePath == sourceFile.absolutePath) return@withContext true
        runCatching {
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun resolveVolumeDescription(
        storageManager: StorageManager,
        dir: File,
        removable: Boolean
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val vol = runCatching { storageManager.getStorageVolume(dir) }.getOrNull()
            if (vol != null) {
                val desc = runCatching { vol.getDescription(context) }.getOrNull()
                if (!desc.isNullOrBlank()) return desc
            }
        }
        return if (removable) "Removable SD Card" else "Internal Storage"
    }

    private fun readStatFs(dir: File): Pair<Long, Long> {
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault(0L to 0L)
    }
}

fun formatBytesHuman(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        "${"%.1f".format(Locale.US, gb)} GB"
    } else {
        val mb = bytes / (1024.0 * 1024.0)
        "${"%.0f".format(Locale.US, mb)} MB"
    }
}
