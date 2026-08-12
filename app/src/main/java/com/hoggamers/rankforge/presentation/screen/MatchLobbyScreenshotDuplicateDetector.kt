package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.NoOpMatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface MatchLobbyScreenshotDuplicateLinkResult {
    data class Linked(val fingerprint: String) : MatchLobbyScreenshotDuplicateLinkResult
    data object SameIdentity : MatchLobbyScreenshotDuplicateLinkResult
    data class LinkedToOtherIdentity(
        val identity: MatchLobbyScreenshotIdentity,
    ) : MatchLobbyScreenshotDuplicateLinkResult
    data object FingerprintFailure : MatchLobbyScreenshotDuplicateLinkResult
    data object StateConflict : MatchLobbyScreenshotDuplicateLinkResult
}

sealed interface MatchLobbyScreenshotDuplicateUnlinkResult {
    data object Unlinked : MatchLobbyScreenshotDuplicateUnlinkResult
    data object StateConflict : MatchLobbyScreenshotDuplicateUnlinkResult
}

class MatchLobbyScreenshotDuplicateDetector @Inject constructor(
    private val fingerprintGenerator: ImageSourceFingerprintGenerator,
    private val assetRepository: MatchLobbyScreenshotAssetRepository =
        NoOpMatchLobbyScreenshotAssetRepository(),
) {
    private val lock = Any()
    private val fingerprintOwnersByTournament =
        mutableMapOf<String, MutableMap<String, MatchLobbyScreenshotIdentity>>()

    suspend fun link(
        identity: MatchLobbyScreenshotIdentity,
        selectedUri: String,
        currentFingerprint: String?,
    ): MatchLobbyScreenshotDuplicateLinkResult {
        val fingerprint = when (val result = fingerprintGenerator.fingerprint(selectedUri)) {
            is ImageSourceFingerprintResult.Success -> result.value
            ImageSourceFingerprintResult.Failure ->
                return MatchLobbyScreenshotDuplicateLinkResult.FingerprintFailure
        }
        val persistedDuplicate = try {
            assetRepository.findDuplicateFingerprint(identity, fingerprint)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchLobbyScreenshotDuplicateLinkResult.StateConflict
        }
        if (persistedDuplicate != null) {
            val duplicateIdentity = persistedDuplicate.identityOrNull()
                ?: return MatchLobbyScreenshotDuplicateLinkResult.StateConflict
            return MatchLobbyScreenshotDuplicateLinkResult.LinkedToOtherIdentity(duplicateIdentity)
        }
        val persistedSameIdentity = try {
            assetRepository.getByIdentity(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchLobbyScreenshotDuplicateLinkResult.StateConflict
        }
        if (persistedSameIdentity?.sha256 == fingerprint) {
            return MatchLobbyScreenshotDuplicateLinkResult.SameIdentity
        }
        return synchronized(lock) {
            val owners = fingerprintOwnersByTournament.getOrPut(identity.tournamentId) { mutableMapOf() }
            when (val owner = owners[fingerprint]) {
                null -> {
                    if (currentFingerprint == fingerprint) {
                        return@synchronized MatchLobbyScreenshotDuplicateLinkResult.SameIdentity
                    }
                    if (currentFingerprint != null) {
                        val currentOwner = owners[currentFingerprint]
                        if (currentOwner != null && currentOwner != identity) {
                            return@synchronized MatchLobbyScreenshotDuplicateLinkResult.StateConflict
                        }
                        if (currentOwner == identity) owners.remove(currentFingerprint)
                    }
                    owners[fingerprint] = identity
                    MatchLobbyScreenshotDuplicateLinkResult.Linked(fingerprint)
                }
                identity -> MatchLobbyScreenshotDuplicateLinkResult.SameIdentity
                else -> MatchLobbyScreenshotDuplicateLinkResult.LinkedToOtherIdentity(owner)
            }
        }
    }

    fun unlink(
        identity: MatchLobbyScreenshotIdentity,
        fingerprint: String?,
    ): MatchLobbyScreenshotDuplicateUnlinkResult = synchronized(lock) {
        if (fingerprint == null) return@synchronized MatchLobbyScreenshotDuplicateUnlinkResult.Unlinked
        val owners = fingerprintOwnersByTournament[identity.tournamentId]
            ?: return@synchronized MatchLobbyScreenshotDuplicateUnlinkResult.Unlinked
        return@synchronized when (owners[fingerprint]) {
            null -> MatchLobbyScreenshotDuplicateUnlinkResult.Unlinked
            identity -> {
                owners.remove(fingerprint)
                if (owners.isEmpty()) fingerprintOwnersByTournament.remove(identity.tournamentId)
                MatchLobbyScreenshotDuplicateUnlinkResult.Unlinked
            }
            else -> MatchLobbyScreenshotDuplicateUnlinkResult.StateConflict
        }
    }

    fun rollback(
        identity: MatchLobbyScreenshotIdentity,
        newFingerprint: String,
        previousFingerprint: String?,
    ): Boolean = synchronized(lock) {
        val owners = fingerprintOwnersByTournament[identity.tournamentId] ?: return@synchronized false
        if (owners[newFingerprint] != identity) return@synchronized false
        if (previousFingerprint != null && owners[previousFingerprint]?.let { it != identity } == true) {
            return@synchronized false
        }
        owners.remove(newFingerprint)
        if (previousFingerprint != null) owners[previousFingerprint] = identity
        if (owners.isEmpty()) fingerprintOwnersByTournament.remove(identity.tournamentId)
        true
    }
}
