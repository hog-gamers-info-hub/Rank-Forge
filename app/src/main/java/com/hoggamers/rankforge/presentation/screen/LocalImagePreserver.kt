package com.hoggamers.rankforge.presentation.screen

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ImageSourceMimeTypeReader {
    fun read(uri: String): String?
}

sealed interface LocalImagePreservationResult {
    data class Preserved(
        val file: File,
    ) : LocalImagePreservationResult

    data class PreservedWithCleanupFailure(
        val file: File,
    ) : LocalImagePreservationResult

    data class Failed(
        val error: LocalImagePreservationFailure,
    ) : LocalImagePreservationResult
}

enum class LocalImagePreservationFailure {
    SOURCE_READ_FAILED,
    COPY_FAILED,
    ATOMIC_MOVE_FAILED,
}

sealed interface LocalImageCleanupResult {
    data object Cleaned : LocalImageCleanupResult
    data object Failed : LocalImageCleanupResult
}

interface LocalImageFileOperations {
    fun ensureDirectory(directory: File): Boolean

    fun createTempFile(directory: File): File

    fun openOutput(file: File): OutputStream

    fun atomicMove(source: File, target: File): Boolean

    fun listFiles(directory: File): List<File>?

    fun delete(file: File): Boolean
}

class LocalImagePreserver(
    private val appPrivateRoot: File,
    private val sourceStreamOpener: ImageSourceStreamOpener,
    private val mimeTypeReader: ImageSourceMimeTypeReader,
    private val fileOperations: LocalImageFileOperations = DefaultLocalImageFileOperations,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        appPrivateRoot = context.filesDir,
        sourceStreamOpener = ImageSourceStreamOpener { uri ->
            context.contentResolver.openInputStream(Uri.parse(uri))
        },
        mimeTypeReader = ImageSourceMimeTypeReader { uri ->
            context.contentResolver.getType(Uri.parse(uri))
        },
    )

    suspend fun preserve(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
    ): LocalImagePreservationResult = withContext(ioDispatcher) {
        if (selectedUri.isBlank()) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        }
        val extension = runCatching { extensionFor(mimeTypeReader.read(selectedUri)) }
            .getOrNull()
            ?: return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        val matchDirectory = matchDirectory(tournamentId, matchId)
        if (!runCatching { fileOperations.ensureDirectory(matchDirectory) }.getOrDefault(false)) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        }
        val temporaryFile = try {
            fileOperations.createTempFile(matchDirectory)
        } catch (_: IOException) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        } catch (_: RuntimeException) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        }

        val input = try {
            sourceStreamOpener.open(selectedUri)
        } catch (_: SecurityException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        } catch (_: FileNotFoundException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        } catch (_: IOException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        } catch (exception: CancellationException) {
            safeDelete(temporaryFile)
            throw exception
        } catch (_: RuntimeException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        }
        if (input == null) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        }

        try {
            input.use { source ->
                fileOperations.openOutput(temporaryFile).use { output ->
                    source.copyTo(output)
                    output.flush()
                    if (output is FileOutputStream) {
                        output.fd.sync()
                    }
                }
            }
        } catch (exception: CancellationException) {
            safeDelete(temporaryFile)
            throw exception
        } catch (_: IOException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        } catch (_: RuntimeException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        }

        val targetFile = File(matchDirectory, "original.$extension")
        if (!runCatching { fileOperations.atomicMove(temporaryFile, targetFile) }.getOrDefault(false)) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.ATOMIC_MOVE_FAILED,
            )
        }

        if (!cleanupStaleFiles(matchDirectory, targetFile)) {
            LocalImagePreservationResult.PreservedWithCleanupFailure(targetFile)
        } else {
            LocalImagePreservationResult.Preserved(targetFile)
        }
    }

    suspend fun cleanup(
        tournamentId: String,
        matchId: String,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        val directory = matchDirectory(tournamentId, matchId)
        val files = runCatching { fileOperations.listFiles(directory) }.getOrNull()
            ?: return@withContext LocalImageCleanupResult.Failed
        val ownedFiles = files.filter { file ->
            file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX)
        }
        if (ownedFiles.all { file -> runCatching { fileOperations.delete(file) }.getOrDefault(false) }) {
            LocalImageCleanupResult.Cleaned
        } else {
            LocalImageCleanupResult.Failed
        }
    }

    fun preservedFile(
        tournamentId: String,
        matchId: String,
        extension: String,
    ): File = File(matchDirectory(tournamentId, matchId), "original.$extension")

    fun relativePath(
        tournamentId: String,
        matchId: String,
        extension: String,
    ): String = "$SCREENSHOTS_DIRECTORY/${encodeSegment(tournamentId)}/${encodeSegment(matchId)}/original.$extension"

    fun relativePathFor(file: File): String? {
        val rootPath = runCatching { File(appPrivateRoot, SCREENSHOTS_DIRECTORY).canonicalFile.toPath() }
            .getOrNull()
            ?: return null
        val filePath = runCatching { file.canonicalFile.toPath() }
            .getOrNull()
            ?: return null
        if (!filePath.startsWith(rootPath)) return null
        val relative = rootPath.relativize(filePath).joinToString("/")
        return "$SCREENSHOTS_DIRECTORY/$relative"
    }

    fun resolveRelativePath(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val normalized = relativePath.replace('\\', '/')
        if (!normalized.startsWith("$SCREENSHOTS_DIRECTORY/")) return null
        if (normalized.contains("../") || normalized.contains("/..")) return null
        val root = runCatching { appPrivateRoot.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(root, normalized).canonicalFile }.getOrNull() ?: return null
        return if (target.toPath().startsWith(root.toPath())) target else null
    }

    private fun matchDirectory(tournamentId: String, matchId: String): File =
        File(File(appPrivateRoot, SCREENSHOTS_DIRECTORY), "${encodeSegment(tournamentId)}/${encodeSegment(matchId)}")

    private fun cleanupStaleFiles(directory: File, targetFile: File): Boolean {
        val files = runCatching { fileOperations.listFiles(directory) }.getOrNull() ?: return false
        return files
            .filter { file ->
                file.name != targetFile.name &&
                    (file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX))
            }
            .all { file -> runCatching { fileOperations.delete(file) }.getOrDefault(false) }
    }

    private fun safeDelete(file: File) {
        runCatching { fileOperations.delete(file) }
    }

    private companion object {
        const val SCREENSHOTS_DIRECTORY = "screenshots"
        const val TEMPORARY_SUFFIX = ".tmp"

        fun extensionFor(mimeType: String?): String? = when (mimeType?.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> null
        }

        fun encodeSegment(value: String): String = value.encodeToByteArray()
            .joinToString(separator = "") { byte ->
                "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
            }
    }
}

private object DefaultLocalImageFileOperations : LocalImageFileOperations {
    override fun ensureDirectory(directory: File): Boolean =
        directory.isDirectory || (directory.mkdirs() && directory.isDirectory)

    override fun createTempFile(directory: File): File =
        File.createTempFile("original-", ".tmp", directory)

    override fun openOutput(file: File): OutputStream = file.outputStream()

    override fun atomicMove(source: File, target: File): Boolean = try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        true
    } catch (_: AtomicMoveNotSupportedException) {
        false
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    override fun listFiles(directory: File): List<File>? =
        if (!directory.exists()) emptyList() else directory.listFiles()?.toList()

    override fun delete(file: File): Boolean = !file.exists() || file.delete() || !file.exists()
}
