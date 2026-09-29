package com.hoggamers.rankforge.data.local

import android.content.Context
import android.graphics.BitmapFactory
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface PointTableLogoSourceStreamOpener {
    suspend fun open(uri: String): InputStream?
}

fun interface PointTableLogoMimeTypeReader {
    fun read(uri: String): String?
}

fun interface PointTableLogoFileDecoder {
    fun canDecode(file: File): Boolean
}

data class PointTableLogoCandidate(
    val tournamentId: String,
    val localRelativePath: String,
    val displayUri: String,
)

sealed interface PointTableLogoImageStoreResult {
    data class Preserved(
        val localRelativePath: String,
        val displayUri: String,
    ) : PointTableLogoImageStoreResult

    data object Failed : PointTableLogoImageStoreResult
}

interface PointTableLogoImageStore {
    suspend fun preserve(
        tournamentId: String,
        selectedUri: String,
    ): PointTableLogoImageStoreResult

    suspend fun cleanup(tournamentId: String): Boolean

    fun displayUriOrNull(localRelativePath: String): String?

    suspend fun prepareCandidate(
        tournamentId: String,
        selectedUri: String,
    ): PointTableLogoImageStoreResult = PointTableLogoImageStoreResult.Failed

    suspend fun commitCandidate(candidate: PointTableLogoCandidate): PointTableLogoImageStoreResult =
        PointTableLogoImageStoreResult.Failed

    suspend fun discardCandidate(candidate: PointTableLogoCandidate) = Unit

    suspend fun deleteLogo(localRelativePath: String): Boolean = false
}

@Singleton
class AndroidPointTableLogoImageStore(
    private val appPrivateRoot: File,
    private val sourceStreamOpener: PointTableLogoSourceStreamOpener,
    private val mimeTypeReader: PointTableLogoMimeTypeReader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logoFileDecoder: PointTableLogoFileDecoder = PointTableLogoFileDecoder { true },
) : PointTableLogoImageStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        appPrivateRoot = context.filesDir,
        sourceStreamOpener = PointTableLogoSourceStreamOpener { uri ->
            context.contentResolver.openInputStream(Uri.parse(uri))
        },
        mimeTypeReader = PointTableLogoMimeTypeReader { uri ->
            context.contentResolver.getType(Uri.parse(uri))
        },
        logoFileDecoder = PointTableLogoFileDecoder(::canDecodeBitmap),
    )

    override suspend fun preserve(
        tournamentId: String,
        selectedUri: String,
    ): PointTableLogoImageStoreResult = withContext(ioDispatcher) {
        if (tournamentId.isBlank() || selectedUri.isBlank()) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        val extension = runCatching { extensionFor(mimeTypeReader.read(selectedUri)) }
            .getOrNull()
            ?: return@withContext PointTableLogoImageStoreResult.Failed
        val directory = logoDirectory(tournamentId)
        if (!ensureDirectory(directory)) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        val temporaryFile = try {
            File.createTempFile("original-", TEMPORARY_SUFFIX, directory)
        } catch (_: IOException) {
            return@withContext PointTableLogoImageStoreResult.Failed
        } catch (_: RuntimeException) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }

        try {
            val input = sourceStreamOpener.open(selectedUri)
                ?: return@withContext failedAfterDeleting(temporaryFile)
            input.use { source ->
                FileOutputStream(temporaryFile).use { output ->
                    copyWithCancellationChecks(source, output)
                    output.flush()
                    output.fd.sync()
                }
            }
        } catch (cancellation: CancellationException) {
            deleteQuietly(temporaryFile)
            throw cancellation
        } catch (_: FileNotFoundException) {
            return@withContext failedAfterDeleting(temporaryFile)
        } catch (_: IOException) {
            return@withContext failedAfterDeleting(temporaryFile)
        } catch (_: SecurityException) {
            return@withContext failedAfterDeleting(temporaryFile)
        } catch (_: RuntimeException) {
            return@withContext failedAfterDeleting(temporaryFile)
        }

        if (!logoFileDecoder.canDecode(temporaryFile)) {
            return@withContext failedAfterDeleting(temporaryFile)
        }

        val targetFile = File(directory, "original.$extension")
        if (!atomicishReplace(temporaryFile, targetFile)) {
            deleteQuietly(temporaryFile)
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        cleanupStaleFiles(directory, targetFile)
        PointTableLogoImageStoreResult.Preserved(
            localRelativePath = relativePath(tournamentId, extension),
            displayUri = targetFile.toURI().toString(),
        )
    }

    override suspend fun cleanup(tournamentId: String): Boolean = withContext(ioDispatcher) {
        if (tournamentId.isBlank()) return@withContext false
        val directory = logoDirectory(tournamentId)
        if (!directory.exists()) return@withContext true
        if (!directory.isDirectory) return@withContext false
        val files = directory.listFiles()?.toList() ?: return@withContext false
        for (file in files.filter(::isOwnedFile)) {
            currentCoroutineContext().ensureActive()
            if (!deleteQuietly(file)) return@withContext false
        }
        val remaining = directory.listFiles()?.toList() ?: return@withContext false
        if (remaining.isNotEmpty() || !directory.delete()) return@withContext false
        val tournamentDirectory = directory.parentFile
        if (tournamentDirectory != null && tournamentDirectory.listFiles()?.isEmpty() == true) {
            tournamentDirectory.delete()
        }
        true
    }

    override suspend fun prepareCandidate(
        tournamentId: String,
        selectedUri: String,
    ): PointTableLogoImageStoreResult = withContext(ioDispatcher) {
        if (tournamentId.isBlank() || selectedUri.isBlank()) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        val extension = runCatching { extensionFor(mimeTypeReader.read(selectedUri)) }
            .getOrNull()
            ?: return@withContext PointTableLogoImageStoreResult.Failed
        val directory = logoDirectory(tournamentId)
        if (!ensureDirectory(directory)) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        val candidateFile = try {
            File.createTempFile("candidate-", ".${extension}", directory)
        } catch (_: IOException) {
            return@withContext PointTableLogoImageStoreResult.Failed
        } catch (_: RuntimeException) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }

        try {
            val input = sourceStreamOpener.open(selectedUri)
                ?: return@withContext failedAfterDeleting(candidateFile)
            input.use { source ->
                FileOutputStream(candidateFile).use { output ->
                    copyWithCancellationChecks(source, output)
                    output.flush()
                    output.fd.sync()
                }
            }
        } catch (cancellation: CancellationException) {
            deleteQuietly(candidateFile)
            throw cancellation
        } catch (_: FileNotFoundException) {
            return@withContext failedAfterDeleting(candidateFile)
        } catch (_: IOException) {
            return@withContext failedAfterDeleting(candidateFile)
        } catch (_: SecurityException) {
            return@withContext failedAfterDeleting(candidateFile)
        } catch (_: RuntimeException) {
            return@withContext failedAfterDeleting(candidateFile)
        }

        if (!logoFileDecoder.canDecode(candidateFile)) {
            return@withContext failedAfterDeleting(candidateFile)
        }

        val relativePath = relativePathForFileName(tournamentId, candidateFile.name)
        PointTableLogoImageStoreResult.Preserved(relativePath, candidateFile.toURI().toString())
    }

    override suspend fun commitCandidate(
        candidate: PointTableLogoCandidate,
    ): PointTableLogoImageStoreResult = withContext(ioDispatcher) {
        val candidateFile = resolveLogoFile(candidate.localRelativePath)
            ?.takeIf { it.name.startsWith("candidate-") }
            ?: return@withContext PointTableLogoImageStoreResult.Failed
        if (candidate.tournamentId.isBlank() || !candidateFile.isFile) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        val extension = candidateFile.extension.lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_EXTENSIONS) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        val committedFile = File(
            checkNotNull(candidateFile.parentFile),
            "logo-${UUID.randomUUID()}.$extension",
        )
        if (!atomicishReplace(candidateFile, committedFile)) {
            return@withContext PointTableLogoImageStoreResult.Failed
        }
        PointTableLogoImageStoreResult.Preserved(
            localRelativePath = relativePathForFileName(candidate.tournamentId, committedFile.name),
            displayUri = committedFile.toURI().toString(),
        )
    }

    override suspend fun discardCandidate(candidate: PointTableLogoCandidate) {
        withContext(ioDispatcher) {
            resolveLogoFile(candidate.localRelativePath)
                ?.takeIf { it.name.startsWith("candidate-") }
                ?.let(::deleteQuietly)
        }
    }

    override suspend fun deleteLogo(localRelativePath: String): Boolean = withContext(ioDispatcher) {
        resolveLogoFile(localRelativePath)?.let(::deleteQuietly) ?: false
    }

    override fun displayUriOrNull(localRelativePath: String): String? {
        val target = resolveLogoFile(localRelativePath) ?: return null
        if (!target.isFile || !target.canRead() || !logoFileDecoder.canDecode(target)) return null
        return target.toURI().toString()
    }

    fun relativePath(tournamentId: String, extension: String): String {
        require(tournamentId.isNotBlank()) { "Tournament ID must not be blank." }
        require(extension in SUPPORTED_EXTENSIONS) { "Unsupported logo extension." }
        return "$POINT_TABLE_DETAILS_DIRECTORY/${encodeSegment(tournamentId)}/logo/original.$extension"
    }

    private fun relativePathForFileName(tournamentId: String, fileName: String): String =
        "$POINT_TABLE_DETAILS_DIRECTORY/${encodeSegment(tournamentId)}/logo/$fileName"

    private fun logoDirectory(tournamentId: String): File = File(
        File(File(appPrivateRoot, POINT_TABLE_DETAILS_DIRECTORY), encodeSegment(tournamentId)),
        "logo",
    )

    private fun ensureDirectory(directory: File): Boolean =
        directory.isDirectory || (directory.mkdirs() && directory.isDirectory)

    private fun atomicishReplace(source: File, target: File): Boolean = try {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private suspend fun copyWithCancellationChecks(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) return
            if (read > 0) output.write(buffer, 0, read)
        }
    }

    private fun cleanupStaleFiles(directory: File, targetFile: File) {
        directory.listFiles()?.forEach { file ->
            if (file != targetFile && isOwnedFile(file)) deleteQuietly(file)
        }
    }

    private fun isOwnedFile(file: File): Boolean =
        isOwnedLogoFileName(file.name)

    private fun failedAfterDeleting(file: File): PointTableLogoImageStoreResult {
        deleteQuietly(file)
        return PointTableLogoImageStoreResult.Failed
    }

    private fun deleteQuietly(file: File): Boolean =
        runCatching { !file.exists() || file.delete() || !file.exists() }.getOrDefault(false)

    private fun normalizedLogoPath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val parts = normalized.split('/')
        if (parts.size != 4 || parts[0] != POINT_TABLE_DETAILS_DIRECTORY || parts[2] != "logo") {
            return null
        }
        if (parts[1].isBlank() || parts[1].any { it !in "0123456789abcdefABCDEF" }) return null
        if (!isOwnedLogoFileName(parts[3])) return null
        return normalized.takeIf(::isSafeOrganizationLogoPath)
    }

    private fun resolveLogoFile(path: String): File? {
        val normalized = normalizedLogoPath(path) ?: return null
        val root = runCatching { appPrivateRoot.canonicalFile.toPath() }.getOrNull() ?: return null
        val target = runCatching { File(appPrivateRoot, normalized).canonicalFile }.getOrNull() ?: return null
        return target.takeIf { it.toPath().startsWith(root) }
    }

    private fun isOwnedLogoFileName(name: String): Boolean {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        if (extension !in SUPPORTED_EXTENSIONS) return name.endsWith(TEMPORARY_SUFFIX)
        return name in SUPPORTED_EXTENSIONS.map { "original.$it" } ||
            name.startsWith("candidate-") ||
            name.startsWith("logo-")
    }

    private companion object {
        const val POINT_TABLE_DETAILS_DIRECTORY = "point-table-details"
        const val TEMPORARY_SUFFIX = ".tmp"
        const val BUFFER_SIZE = 8_192
        val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "webp")

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

private fun canDecodeBitmap(file: File): Boolean = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    options.outWidth > 0 && options.outHeight > 0
}.getOrDefault(false)
