package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.NoOpMatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface MatchResultScreenshotDuplicateLinkResult {
    data class Linked(
        val fingerprint: String,
    ) : MatchResultScreenshotDuplicateLinkResult

    data object SameIdentity : MatchResultScreenshotDuplicateLinkResult

    data class LinkedToOtherIdentity(
        val identity: MatchResultScreenshotIdentity,
    ) : MatchResultScreenshotDuplicateLinkResult

    data object FingerprintFailure : MatchResultScreenshotDuplicateLinkResult
    data object StateConflict : MatchResultScreenshotDuplicateLinkResult
}

sealed interface MatchResultScreenshotDuplicateUnlinkResult {
    data object Unlinked : MatchResultScreenshotDuplicateUnlinkResult
    data object StateConflict : MatchResultScreenshotDuplicateUnlinkResult
}

class MatchResultScreenshotDuplicateDetector @Inject constructor(
    private val fingerprintGenerator: ImageSourceFingerprintGenerator,
    private val assetRepository: MatchResultScreenshotAssetRepository =
        NoOpMatchResultScreenshotAssetRepository(),
) {
    private val lock = Any()
    private val fingerprintOwnersByMatch =
        mutableMapOf<MatchScope, MutableMap<String, MatchResultScreenshotIdentity>>()

    suspend fun link(
        identity: MatchResultScreenshotIdentity,
        selectedUri: String,
        currentFingerprint: String?,
    ): MatchResultScreenshotDuplicateLinkResult {
        val fingerprint = when (val result = fingerprintGenerator.fingerprint(selectedUri)) {
            is ImageSourceFingerprintResult.Success -> result.value
            ImageSourceFingerprintResult.Failure ->
                return MatchResultScreenshotDuplicateLinkResult.FingerprintFailure
        }

        val persistedDuplicate = try {
            assetRepository.findDuplicateFingerprint(identity, fingerprint)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchResultScreenshotDuplicateLinkResult.StateConflict
        }
        if (persistedDuplicate != null) {
            val duplicateIdentity = persistedDuplicate.identityOrNull()
                ?: return MatchResultScreenshotDuplicateLinkResult.StateConflict
            return MatchResultScreenshotDuplicateLinkResult.LinkedToOtherIdentity(duplicateIdentity)
        }

        val persistedSameIdentity = try {
            assetRepository.getByIdentity(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchResultScreenshotDuplicateLinkResult.StateConflict
        }
        if (persistedSameIdentity?.sha256 == fingerprint) {
            return MatchResultScreenshotDuplicateLinkResult.SameIdentity
        }

        return synchronized(lock) {
            val owners = fingerprintOwnersByMatch.getOrPut(identity.scope()) { mutableMapOf() }
            when (val owner = owners[fingerprint]) {
                null -> {
                    if (currentFingerprint == fingerprint) {
                        return@synchronized MatchResultScreenshotDuplicateLinkResult.SameIdentity
                    }
                    if (currentFingerprint != null) {
                        val currentOwner = owners[currentFingerprint]
                        if (currentOwner != null && currentOwner != identity) {
                            return@synchronized MatchResultScreenshotDuplicateLinkResult.StateConflict
                        }
                        if (currentOwner == identity) {
                            owners.remove(currentFingerprint)
                        }
                    }
                    owners[fingerprint] = identity
                    MatchResultScreenshotDuplicateLinkResult.Linked(fingerprint)
                }

                identity -> MatchResultScreenshotDuplicateLinkResult.SameIdentity
                else -> MatchResultScreenshotDuplicateLinkResult.LinkedToOtherIdentity(owner)
            }
        }
    }

    fun unlink(
        identity: MatchResultScreenshotIdentity,
        fingerprint: String?,
    ): MatchResultScreenshotDuplicateUnlinkResult = synchronized(lock) {
        if (fingerprint == null) {
            return@synchronized MatchResultScreenshotDuplicateUnlinkResult.Unlinked
        }
        val owners = fingerprintOwnersByMatch[identity.scope()]
            ?: return@synchronized MatchResultScreenshotDuplicateUnlinkResult.Unlinked
        when (val owner = owners[fingerprint]) {
            null -> MatchResultScreenshotDuplicateUnlinkResult.Unlinked
            identity -> {
                owners.remove(fingerprint)
                if (owners.isEmpty()) {
                    fingerprintOwnersByMatch.remove(identity.scope())
                }
                MatchResultScreenshotDuplicateUnlinkResult.Unlinked
            }

            else -> MatchResultScreenshotDuplicateUnlinkResult.StateConflict
        }
    }

    fun rollback(
        identity: MatchResultScreenshotIdentity,
        newFingerprint: String,
        previousFingerprint: String?,
    ): Boolean = synchronized(lock) {
        val owners = fingerprintOwnersByMatch[identity.scope()] ?: return@synchronized false
        if (owners[newFingerprint] != identity) return@synchronized false
        if (previousFingerprint != null) {
            val previousOwner = owners[previousFingerprint]
            if (previousOwner != null && previousOwner != identity) return@synchronized false
        }
        owners.remove(newFingerprint)
        if (previousFingerprint != null) {
            owners[previousFingerprint] = identity
        }
        if (owners.isEmpty()) {
            fingerprintOwnersByMatch.remove(identity.scope())
        }
        true
    }

    private fun MatchResultScreenshotIdentity.scope() = MatchScope(tournamentId, matchId)

    private data class MatchScope(
        val tournamentId: String,
        val matchId: String,
    )
}
