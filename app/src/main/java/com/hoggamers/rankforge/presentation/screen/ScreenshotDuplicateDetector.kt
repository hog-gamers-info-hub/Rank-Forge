package com.hoggamers.rankforge.presentation.screen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImageSourceFingerprintResult {
    data class Success(
        val value: String,
    ) : ImageSourceFingerprintResult

    data object Failure : ImageSourceFingerprintResult
}

fun interface ImageSourceStreamOpener {
    suspend fun open(uri: String): InputStream?
}

class ImageSourceFingerprintGenerator(
    private val streamOpener: ImageSourceStreamOpener,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(AndroidImageSourceStreamOpener(context.contentResolver))

    suspend fun fingerprint(uri: String?): ImageSourceFingerprintResult {
        if (uri.isNullOrBlank()) {
            return ImageSourceFingerprintResult.Failure
        }

        return withContext(coroutineDispatcher) {
            try {
                val stream = streamOpener.open(uri) ?: return@withContext ImageSourceFingerprintResult.Failure
                stream.use {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(FINGERPRINT_BUFFER_SIZE)
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        if (read > 0) digest.update(buffer, 0, read)
                    }
                    ImageSourceFingerprintResult.Success(
                        digest.digest().joinToString(separator = "") { byte ->
                            "%02x".format(byte.toInt() and 0xff)
                        },
                    )
                }
            } catch (_: SecurityException) {
                ImageSourceFingerprintResult.Failure
            } catch (_: FileNotFoundException) {
                ImageSourceFingerprintResult.Failure
            } catch (_: IOException) {
                ImageSourceFingerprintResult.Failure
            } catch (exception: RuntimeException) {
                if (exception is CancellationException) {
                    throw exception
                }
                ImageSourceFingerprintResult.Failure
            }
        }
    }

    private companion object {
        const val FINGERPRINT_BUFFER_SIZE = 8_192
    }
}

private class AndroidImageSourceStreamOpener(
    private val contentResolver: ContentResolver,
) : ImageSourceStreamOpener {
    override suspend fun open(uri: String): InputStream? = contentResolver.openInputStream(Uri.parse(uri))
}

sealed interface ScreenshotDuplicateLinkResult {
    data class Linked(
        val fingerprint: String,
    ) : ScreenshotDuplicateLinkResult

    data object SameMatch : ScreenshotDuplicateLinkResult

    data class LinkedToOtherMatch(
        val matchId: String,
    ) : ScreenshotDuplicateLinkResult

    data object FingerprintFailure : ScreenshotDuplicateLinkResult
    data object StateConflict : ScreenshotDuplicateLinkResult
}

sealed interface ScreenshotDuplicateUnlinkResult {
    data object Unlinked : ScreenshotDuplicateUnlinkResult
    data object StateConflict : ScreenshotDuplicateUnlinkResult
}

@ActivityRetainedScoped
class ScreenshotDuplicateDetector @Inject constructor(
    private val fingerprintGenerator: ImageSourceFingerprintGenerator,
) {
    private val lock = Any()
    private val fingerprintOwnersByTournament = mutableMapOf<String, MutableMap<String, String>>()

    suspend fun link(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
        currentFingerprint: String?,
    ): ScreenshotDuplicateLinkResult {
        val fingerprint = when (val result = fingerprintGenerator.fingerprint(selectedUri)) {
            is ImageSourceFingerprintResult.Success -> result.value
            ImageSourceFingerprintResult.Failure -> return ScreenshotDuplicateLinkResult.FingerprintFailure
        }

        return synchronized(lock) {
            val owners = fingerprintOwnersByTournament.getOrPut(tournamentId) { mutableMapOf() }
            when (val owner = owners[fingerprint]) {
                null -> {
                    if (currentFingerprint == fingerprint) {
                        return@synchronized ScreenshotDuplicateLinkResult.SameMatch
                    }
                    if (currentFingerprint != null) {
                        val currentOwner = owners[currentFingerprint]
                        if (currentOwner != null && currentOwner != matchId) {
                            return@synchronized ScreenshotDuplicateLinkResult.StateConflict
                        }
                        if (currentOwner == matchId) {
                            owners.remove(currentFingerprint)
                        }
                    }
                    owners[fingerprint] = matchId
                    ScreenshotDuplicateLinkResult.Linked(fingerprint)
                }
                matchId -> ScreenshotDuplicateLinkResult.SameMatch
                else -> ScreenshotDuplicateLinkResult.LinkedToOtherMatch(owner)
            }
        }
    }

    fun unlink(
        tournamentId: String,
        matchId: String,
        fingerprint: String?,
    ): ScreenshotDuplicateUnlinkResult = synchronized(lock) {
        if (fingerprint == null) {
            return@synchronized ScreenshotDuplicateUnlinkResult.Unlinked
        }
        val owners = fingerprintOwnersByTournament[tournamentId]
            ?: return@synchronized ScreenshotDuplicateUnlinkResult.StateConflict
        when (owners[fingerprint]) {
            matchId -> {
                owners.remove(fingerprint)
                if (owners.isEmpty()) {
                    fingerprintOwnersByTournament.remove(tournamentId)
                }
                ScreenshotDuplicateUnlinkResult.Unlinked
            }
            else -> ScreenshotDuplicateUnlinkResult.StateConflict
        }
    }

    fun rollback(
        tournamentId: String,
        matchId: String,
        newFingerprint: String,
        previousFingerprint: String?,
    ): Boolean = synchronized(lock) {
        val owners = fingerprintOwnersByTournament[tournamentId] ?: return@synchronized false
        if (owners[newFingerprint] != matchId) return@synchronized false
        if (previousFingerprint != null) {
            val previousOwner = owners[previousFingerprint]
            if (previousOwner != null && previousOwner != matchId) return@synchronized false
        }
        owners.remove(newFingerprint)
        if (previousFingerprint != null) {
            owners[previousFingerprint] = matchId
        }
        if (owners.isEmpty()) {
            fingerprintOwnersByTournament.remove(tournamentId)
        }
        true
    }
}
