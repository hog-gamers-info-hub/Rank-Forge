package com.hoggamers.rankforge.presentation.screen

import android.content.Context
import android.net.Uri
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
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
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    fun delete(file: File): Boolean = fileOperations.delete(file)

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
    ): LocalImagePreservationResult = preserveToDirectory(
        directory = matchDirectory(tournamentId, matchId),
        selectedUri = selectedUri,
    )

    suspend fun preserveRosterScreenshot(
        tournamentId: String,
        rosterScreenshotIndex: Int,
        selectedUri: String,
    ): LocalImagePreservationResult {
        if (rosterScreenshotIndex !in 1..3) {
            return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        return preserveToDirectory(
            directory = rosterScreenshotDirectory(tournamentId, rosterScreenshotIndex),
            selectedUri = selectedUri,
        )
    }

    suspend fun preserveMatchResultScreenshot(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
        selectedUri: String,
    ): LocalImagePreservationResult = preserveToDirectory(
        directory = matchResultScreenshotDirectory(tournamentId, matchId, role),
        selectedUri = selectedUri,
    )

    suspend fun preserveLobbyScreenshot(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        selectedUri: String,
    ): LocalImagePreservationResult {
        if (lobbyScreenshotIndex !in 1..3) {
            return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        return preserveToDirectory(
            directory = lobbyScreenshotDirectory(tournamentId, matchId, lobbyScreenshotIndex),
            selectedUri = selectedUri,
        )
    }

    suspend fun restoreMatchLobbyScreenshot(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        extension: String,
        bytes: ByteArray,
    ): LocalImagePreservationResult {
        if (lobbyScreenshotIndex !in 1..3) {
            return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        return restoreBytesToDirectory(
            directory = lobbyScreenshotDirectory(tournamentId, matchId, lobbyScreenshotIndex),
            extension = extension,
            bytes = bytes,
        )
    }

    suspend fun restoreMatchResultScreenshot(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
        extension: String,
        bytes: ByteArray,
    ): LocalImagePreservationResult = restoreBytesToDirectory(
        directory = matchResultScreenshotDirectory(tournamentId, matchId, role),
        extension = extension,
        bytes = bytes,
    )

    suspend fun restoreCustomDesignImage(
        ownerUserId: String,
        customDesignId: String,
        extension: String,
        bytes: ByteArray,
    ): LocalImagePreservationResult {
        if (!isCanonicalUuid(ownerUserId) || !isCanonicalUuid(customDesignId) ||
            extension !in CUSTOM_DESIGN_EXTENSIONS
        ) {
            return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        val directory = customDesignDirectory(ownerUserId, customDesignId)
        val targetFile = File(directory, "original.$extension")
        return try {
            restoreBytesToDirectory(
                directory = directory,
                extension = extension,
                bytes = bytes,
            )
        } catch (cancellation: CancellationException) {
            delete(targetFile)
            throw cancellation
        }
    }

    suspend fun cleanupCustomDesign(
        ownerUserId: String,
        customDesignId: String,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        if (!isCanonicalUuid(ownerUserId) || !isCanonicalUuid(customDesignId)) {
            return@withContext LocalImageCleanupResult.Failed
        }
        val directory = customDesignDirectory(ownerUserId, customDesignId)
        if (!directory.exists()) return@withContext LocalImageCleanupResult.Cleaned
        if (!directory.isDirectory) return@withContext LocalImageCleanupResult.Failed

        val files = listFilesOrNull(directory)
            ?: return@withContext LocalImageCleanupResult.Failed
        val ownedFiles = files.filter { file ->
            file.name in CUSTOM_DESIGN_ORIGINAL_FILES || file.name.endsWith(TEMPORARY_SUFFIX)
        }
        if (!deleteFiles(ownedFiles)) return@withContext LocalImageCleanupResult.Failed

        val remaining = listFilesOrNull(directory)
            ?: return@withContext LocalImageCleanupResult.Failed
        if (remaining.isNotEmpty()) return@withContext LocalImageCleanupResult.Failed
        if (directory.exists() && !deleteFile(directory)) return@withContext LocalImageCleanupResult.Failed
        LocalImageCleanupResult.Cleaned
    }

    fun customDesignPreservedFile(
        ownerUserId: String,
        customDesignId: String,
        extension: String,
    ): File {
        require(isCanonicalUuid(ownerUserId)) { "Custom Design owner must be a canonical UUID." }
        require(isCanonicalUuid(customDesignId)) { "Custom Design ID must be a canonical UUID." }
        require(extension in CUSTOM_DESIGN_EXTENSIONS) { "Unsupported Custom Design extension." }
        return File(customDesignDirectory(ownerUserId, customDesignId), "original.$extension")
    }

    suspend fun snapshotLobbyTemplate(
        tournamentId: String,
        generation: String,
        lobbyScreenshotIndex: Int,
        sourceFile: File,
        extension: String,
    ): LocalImagePreservationResult {
        if (lobbyScreenshotIndex !in 1..3) {
            return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        return copyFileToDirectory(
            directory = lobbyTemplateDirectory(tournamentId, generation, lobbyScreenshotIndex),
            sourceFile = sourceFile,
            extension = extension,
        )
    }

    suspend fun copyLobbyTemplateToMatch(
        tournamentId: String,
        lobbyScreenshotIndex: Int,
        matchId: String,
        templateRelativePath: String,
        extension: String,
    ): LocalImagePreservationResult {
        if (lobbyScreenshotIndex !in 1..3) {
            return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        val sourceFile = resolveRelativePath(templateRelativePath)
            ?: return LocalImagePreservationResult.Failed(LocalImagePreservationFailure.SOURCE_READ_FAILED)
        return copyFileToDirectory(
            directory = lobbyScreenshotDirectory(tournamentId, matchId, lobbyScreenshotIndex),
            sourceFile = sourceFile,
            extension = extension,
        )
    }

    private suspend fun preserveToDirectory(
        directory: File,
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
        if (!runCatching { fileOperations.ensureDirectory(directory) }.getOrDefault(false)) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        }
        val temporaryFile = try {
            fileOperations.createTempFile(directory)
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

        val targetFile = File(directory, "original.$extension")
        if (!runCatching { fileOperations.atomicMove(temporaryFile, targetFile) }.getOrDefault(false)) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.ATOMIC_MOVE_FAILED,
            )
        }

        if (!cleanupStaleFiles(directory, targetFile)) {
            LocalImagePreservationResult.PreservedWithCleanupFailure(targetFile)
        } else {
            LocalImagePreservationResult.Preserved(targetFile)
        }
    }

    private suspend fun copyFileToDirectory(
        directory: File,
        sourceFile: File,
        extension: String,
    ): LocalImagePreservationResult = withContext(ioDispatcher) {
        if (!runCatching { sourceFile.isFile && sourceFile.canRead() && sourceFile.length() > 0L }
                .getOrDefault(false)
        ) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.SOURCE_READ_FAILED,
            )
        }
        if (extension.isBlank() || !runCatching { fileOperations.ensureDirectory(directory) }.getOrDefault(false)) {
            return@withContext LocalImagePreservationResult.Failed(
                LocalImagePreservationFailure.COPY_FAILED,
            )
        }
        val temporaryFile = try {
            fileOperations.createTempFile(directory)
        } catch (_: IOException) {
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        } catch (_: RuntimeException) {
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        try {
            sourceFile.inputStream().use { source ->
                fileOperations.openOutput(temporaryFile).use { output ->
                    source.copyTo(output)
                    output.flush()
                    if (output is FileOutputStream) output.fd.sync()
                }
            }
        } catch (exception: CancellationException) {
            safeDelete(temporaryFile)
            throw exception
        } catch (_: IOException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        } catch (_: RuntimeException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        val targetFile = File(directory, "original.$extension")
        if (!runCatching { fileOperations.atomicMove(temporaryFile, targetFile) }.getOrDefault(false)) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.ATOMIC_MOVE_FAILED)
        }
        if (cleanupStaleFiles(directory, targetFile)) {
            LocalImagePreservationResult.Preserved(targetFile)
        } else {
            LocalImagePreservationResult.PreservedWithCleanupFailure(targetFile)
        }
    }

    private suspend fun restoreBytesToDirectory(
        directory: File,
        extension: String,
        bytes: ByteArray,
    ): LocalImagePreservationResult = withContext(ioDispatcher) {
        if (extension.isBlank() || bytes.isEmpty() ||
            !runCatching { fileOperations.ensureDirectory(directory) }.getOrDefault(false)
        ) {
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        val temporaryFile = try {
            fileOperations.createTempFile(directory)
        } catch (_: IOException) {
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        } catch (_: RuntimeException) {
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        try {
            fileOperations.openOutput(temporaryFile).use { output ->
                output.write(bytes)
                output.flush()
                if (output is FileOutputStream) output.fd.sync()
            }
        } catch (cancellation: CancellationException) {
            safeDelete(temporaryFile)
            throw cancellation
        } catch (_: IOException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        } catch (_: RuntimeException) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.COPY_FAILED)
        }
        val targetFile = File(directory, "original.$extension")
        if (!runCatching { fileOperations.atomicMove(temporaryFile, targetFile) }.getOrDefault(false)) {
            safeDelete(temporaryFile)
            return@withContext LocalImagePreservationResult.Failed(LocalImagePreservationFailure.ATOMIC_MOVE_FAILED)
        }
        if (cleanupStaleFiles(directory, targetFile)) {
            LocalImagePreservationResult.Preserved(targetFile)
        } else {
            LocalImagePreservationResult.PreservedWithCleanupFailure(targetFile)
        }
    }

    suspend fun cleanup(
        tournamentId: String,
        matchId: String,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        val directory = matchDirectory(tournamentId, matchId)
        val files = listFilesOrNull(directory)
            ?: return@withContext LocalImageCleanupResult.Failed
        val ownedFiles = files.filter { file ->
            file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX)
        }
        if (deleteFiles(ownedFiles)) {
            LocalImageCleanupResult.Cleaned
        } else {
            LocalImageCleanupResult.Failed
        }
    }

    suspend fun cleanupMatchAssets(
        tournamentId: String,
        matchId: String,
        referencedRelativePaths: Collection<String>,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        if (!deleteReferencedFiles(referencedRelativePaths, matchDirectory(tournamentId, matchId))) {
            return@withContext LocalImageCleanupResult.Failed
        }
        val cleanups = buildList {
            add(cleanupIfPresent(matchDirectory(tournamentId, matchId)) { cleanup(tournamentId, matchId) })
            add(
                cleanupIfPresent(
                    matchResultScreenshotDirectory(
                        tournamentId,
                        matchId,
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    ),
                ) {
                    cleanupMatchResultScreenshot(
                        tournamentId,
                        matchId,
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    )
                },
            )
            add(
                cleanupIfPresent(
                    matchResultScreenshotDirectory(
                        tournamentId,
                        matchId,
                        MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    ),
                ) {
                    cleanupMatchResultScreenshot(
                        tournamentId,
                        matchId,
                        MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    )
                },
            )
            (1..3).forEach { index ->
                add(
                    cleanupIfPresent(lobbyScreenshotDirectory(tournamentId, matchId, index)) {
                        cleanupLobbyScreenshot(tournamentId, matchId, index)
                    },
                )
            }
        }
        if (cleanups.all { it == LocalImageCleanupResult.Cleaned }) {
            LocalImageCleanupResult.Cleaned
        } else {
            LocalImageCleanupResult.Failed
        }
    }

    suspend fun cleanupTournamentAssets(
        tournamentId: String,
        matchIds: Collection<String>,
        templateGenerations: Collection<String>,
        referencedRelativePaths: Collection<String>,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        if (!deleteReferencedFiles(referencedRelativePaths, tournamentDirectory(tournamentId))) {
            return@withContext LocalImageCleanupResult.Failed
        }
        val cleanups = buildList {
            (1..3).forEach { index ->
                add(
                    cleanupIfPresent(rosterScreenshotDirectory(tournamentId, index)) {
                        cleanupRosterScreenshot(tournamentId, index)
                    },
                )
            }
            matchIds.forEach { matchId -> add(cleanupMatchAssets(tournamentId, matchId, emptyList())) }
            templateGenerations.forEach { generation ->
                add(
                    cleanupIfPresent(lobbyTemplateGenerationDirectory(tournamentId, generation)) {
                        cleanupLobbyTemplateGeneration(tournamentId, generation)
                    },
                )
            }
        }
        if (cleanups.all { it == LocalImageCleanupResult.Cleaned }) {
            LocalImageCleanupResult.Cleaned
        } else {
            LocalImageCleanupResult.Failed
        }
    }

    private suspend fun cleanupIfPresent(
        directory: File,
        cleanup: suspend () -> LocalImageCleanupResult,
    ): LocalImageCleanupResult = if (directory.exists()) cleanup() else LocalImageCleanupResult.Cleaned

    suspend fun cleanupRosterScreenshot(
        tournamentId: String,
        rosterScreenshotIndex: Int,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        if (rosterScreenshotIndex !in 1..3) return@withContext LocalImageCleanupResult.Failed
        val directory = rosterScreenshotDirectory(tournamentId, rosterScreenshotIndex)
        val files = listFilesOrNull(directory)
            ?: return@withContext LocalImageCleanupResult.Failed
        val ownedFiles = files.filter { file ->
            file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX)
        }
        if (deleteFiles(ownedFiles)) {
            LocalImageCleanupResult.Cleaned
        } else {
            LocalImageCleanupResult.Failed
        }
    }

    suspend fun cleanupMatchResultScreenshot(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        val directory = matchResultScreenshotDirectory(tournamentId, matchId, role)
        val files = listFilesOrNull(directory)
            ?: return@withContext LocalImageCleanupResult.Failed
        val ownedFiles = files.filter { file ->
            file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX)
        }
        if (deleteFiles(ownedFiles)) {
            LocalImageCleanupResult.Cleaned
        } else {
            LocalImageCleanupResult.Failed
        }
    }

    suspend fun cleanupLobbyScreenshot(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        if (lobbyScreenshotIndex !in 1..3) return@withContext LocalImageCleanupResult.Failed
        val directory = lobbyScreenshotDirectory(tournamentId, matchId, lobbyScreenshotIndex)
        val files = listFilesOrNull(directory)
            ?: return@withContext LocalImageCleanupResult.Failed
        val ownedFiles = files.filter { file ->
            file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX)
        }
        if (deleteFiles(ownedFiles)) {
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

    fun rosterRelativePath(
        tournamentId: String,
        rosterScreenshotIndex: Int,
        extension: String,
    ): String = "$SCREENSHOTS_DIRECTORY/${encodeSegment(tournamentId)}/roster/$rosterScreenshotIndex/original.$extension"

    fun matchResultRelativePath(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
        extension: String,
    ): String =
        "$SCREENSHOTS_DIRECTORY/${encodeSegment(tournamentId)}/${encodeSegment(matchId)}/result/" +
            "${roleDirectoryName(role)}/original.$extension"

    fun matchResultPreservedFile(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
        extension: String,
    ): File = File(matchResultScreenshotDirectory(tournamentId, matchId, role), "original.$extension")

    fun lobbyRelativePath(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        extension: String,
    ): String =
        "$SCREENSHOTS_DIRECTORY/${encodeSegment(tournamentId)}/${encodeSegment(matchId)}/lobby/" +
            "$lobbyScreenshotIndex/original.$extension"

    fun lobbyPreservedFile(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
        extension: String,
    ): File = File(
        lobbyScreenshotDirectory(tournamentId, matchId, lobbyScreenshotIndex),
        "original.$extension",
    )

    fun lobbyTemplateRelativePath(
        tournamentId: String,
        generation: String,
        lobbyScreenshotIndex: Int,
        extension: String,
    ): String =
        "$SCREENSHOTS_DIRECTORY/${encodeSegment(tournamentId)}/lobby-template/${encodeSegment(generation)}/" +
            "$lobbyScreenshotIndex/original.$extension"

    fun lobbyTemplatePreservedFile(
        tournamentId: String,
        generation: String,
        lobbyScreenshotIndex: Int,
        extension: String,
    ): File = File(
        lobbyTemplateDirectory(tournamentId, generation, lobbyScreenshotIndex),
        "original.$extension",
    )

    suspend fun cleanupLobbyTemplateGeneration(
        tournamentId: String,
        generation: String,
    ): LocalImageCleanupResult = withContext(ioDispatcher) {
        if (tournamentId.isBlank() || generation.isBlank() || generation.contains('/') || generation.contains('\\')) {
            return@withContext LocalImageCleanupResult.Failed
        }
        val generationDirectory = lobbyTemplateGenerationDirectory(tournamentId, generation)
        val slotDirectories = (1..3).map { index ->
            File(generationDirectory, index.toString())
        }
        var success = true
        slotDirectories.forEach { directory ->
            val files = listFilesOrNull(directory)
            if (files == null) {
                success = false
            } else {
                files.filter { file ->
                    file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX)
                }.forEach { file ->
                    if (!deleteFile(file)) success = false
                }
            }
        }
        if (success) {
            slotDirectories.asReversed().forEach { directory ->
                if (directory.exists() && !deleteFile(directory)) {
                    success = false
                }
            }
            if (generationDirectory.exists() && !deleteFile(generationDirectory)) {
                success = false
            }
        }
        if (success) LocalImageCleanupResult.Cleaned else LocalImageCleanupResult.Failed
    }

    fun lobbyTemplateGenerationFromRelativePath(
        tournamentId: String,
        relativePath: String,
    ): String? {
        val prefix = "$SCREENSHOTS_DIRECTORY/${encodeSegment(tournamentId)}/lobby-template/"
        if (!relativePath.startsWith(prefix)) return null
        val remainder = relativePath.removePrefix(prefix).split('/')
        if (remainder.size != 3 || remainder[1] !in setOf("1", "2", "3")) return null
        if (!remainder[2].startsWith("original.")) return null
        return decodeSegment(remainder[0])
            ?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') }
    }

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

    private fun tournamentDirectory(tournamentId: String): File =
        File(File(appPrivateRoot, SCREENSHOTS_DIRECTORY), encodeSegment(tournamentId))

    private fun rosterScreenshotDirectory(tournamentId: String, rosterScreenshotIndex: Int): File =
        File(File(appPrivateRoot, SCREENSHOTS_DIRECTORY), "${encodeSegment(tournamentId)}/roster/$rosterScreenshotIndex")

    private fun matchResultScreenshotDirectory(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
    ): File = File(
        File(appPrivateRoot, SCREENSHOTS_DIRECTORY),
        "${encodeSegment(tournamentId)}/${encodeSegment(matchId)}/result/${roleDirectoryName(role)}",
    )

    private fun lobbyScreenshotDirectory(
        tournamentId: String,
        matchId: String,
        lobbyScreenshotIndex: Int,
    ): File = File(
        File(appPrivateRoot, SCREENSHOTS_DIRECTORY),
        "${encodeSegment(tournamentId)}/${encodeSegment(matchId)}/lobby/$lobbyScreenshotIndex",
    )

    private fun lobbyTemplateDirectory(
        tournamentId: String,
        generation: String,
        lobbyScreenshotIndex: Int,
    ): File = File(
        File(appPrivateRoot, SCREENSHOTS_DIRECTORY),
        "${encodeSegment(tournamentId)}/lobby-template/${encodeSegment(generation)}/$lobbyScreenshotIndex",
    )

    private fun lobbyTemplateGenerationDirectory(
        tournamentId: String,
        generation: String,
    ): File = File(
        File(appPrivateRoot, SCREENSHOTS_DIRECTORY),
        "${encodeSegment(tournamentId)}/lobby-template/${encodeSegment(generation)}",
    )

    private fun customDesignDirectory(ownerUserId: String, customDesignId: String): File =
        File(
            File(File(File(appPrivateRoot, CUSTOM_DESIGNS_DIRECTORY), "users"), ownerUserId),
            customDesignId,
        )

    private fun roleDirectoryName(role: MatchResultScreenshotRole): String = when (role) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER -> "upper"
        MatchResultScreenshotRole.MATCH_RESULT_LOWER -> "lower"
    }

    private suspend fun cleanupStaleFiles(directory: File, targetFile: File): Boolean {
        val files = listFilesOrNull(directory) ?: return false
        return deleteFiles(files.filter { file ->
                file.name != targetFile.name &&
                    (file.name.startsWith("original.") || file.name.endsWith(TEMPORARY_SUFFIX))
            })
    }

    private suspend fun deleteReferencedFiles(
        relativePaths: Collection<String>,
        ownerDirectory: File,
    ): Boolean {
        val ownerPath = runCatching { ownerDirectory.canonicalFile.toPath() }.getOrNull() ?: return false
        for (relativePath in relativePaths) {
            val file = resolveRelativePath(relativePath) ?: return false
            val filePath = runCatching { file.canonicalFile.toPath() }.getOrNull() ?: return false
            if (!filePath.startsWith(ownerPath) || !deleteFile(file)) return false
        }
        return true
    }

    private suspend fun safeDelete(file: File) {
        deleteFile(file)
    }

    private suspend fun listFilesOrNull(directory: File): List<File>? = try {
        currentCoroutineContext().ensureActive()
        fileOperations.listFiles(directory)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun deleteFile(file: File): Boolean = try {
        currentCoroutineContext().ensureActive()
        fileOperations.delete(file)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

    private suspend fun deleteFiles(files: Iterable<File>): Boolean {
        for (file in files) {
            if (!deleteFile(file)) return false
        }
        return true
    }

    private companion object {
        const val SCREENSHOTS_DIRECTORY = "screenshots"
        const val CUSTOM_DESIGNS_DIRECTORY = "custom-designs"
        const val TEMPORARY_SUFFIX = ".tmp"
        val CUSTOM_DESIGN_EXTENSIONS = setOf("png", "jpg", "webp")
        val CUSTOM_DESIGN_ORIGINAL_FILES = setOf("original.png", "original.jpg", "original.webp")

        fun isCanonicalUuid(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

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

        fun decodeSegment(value: String): String? {
            if (value.isBlank() || value.length % 2 != 0 || value.any { it !in "0123456789abcdefABCDEF" }) {
                return null
            }
            return runCatching {
                value.chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                    .toString(Charsets.UTF_8)
            }.getOrNull()
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
