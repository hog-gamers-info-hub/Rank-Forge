package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.sync.ForegroundScreenshotRecoveryAction
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Singleton
class RecoverScreenshotAssetsOnForegroundConnectivityUseCase @Inject constructor(
    private val observeTournaments: ObserveTournamentsUseCase,
    private val observeMatches: ObserveMatchesUseCase,
    private val ownerProvider: ScreenshotOwnerProvider,
    private val lobbyAssets: MatchLobbyScreenshotAssetRepository,
    private val resultAssets: MatchResultScreenshotAssetRepository,
    private val lobbyCheckpoint: MatchLobbyScreenshotUploadCheckpointAction,
    private val resultCheckpoint: MatchResultScreenshotUploadCheckpointAction,
) : ForegroundScreenshotRecoveryAction {
    override suspend fun recoverAfterParentQueue() {
        val ownerId = try {
            ownerProvider.currentOwnerUserId()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return
        } ?: return
        recoverAfterParentQueue(ownerId)
    }

    override suspend fun recoverAfterParentQueue(expectedOwnerUserId: String) {
        if (expectedOwnerUserId.isBlank() || !isCurrentOwner(expectedOwnerUserId)) return

        val tournaments = try {
            observeTournaments().first().filter { it.ownerUserId == expectedOwnerUserId }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return
        }

        tournaments.forEach { tournament ->
            val matchIds = try {
                observeMatches(tournament.id).first().map { it.id }.toSet()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                emptySet()
            }
            if (matchIds.isEmpty()) return@forEach

            val lobby = try {
                lobbyAssets.observeByTournamentIdAndOwner(tournament.id, expectedOwnerUserId).first()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                emptyList()
            }
            for (asset in lobby.filter { asset ->
                asset.ownerUserId == expectedOwnerUserId && asset.matchId in matchIds && asset.hasConfirmedLobbyCrop()
            }) {
                if (!isCurrentOwner(expectedOwnerUserId)) return
                retryLobby(asset, expectedOwnerUserId)
            }
            if (!isCurrentOwner(expectedOwnerUserId)) return

            val result = try {
                resultAssets.observeByTournamentIdAndOwner(tournament.id, expectedOwnerUserId).first()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                emptyList()
            }
            for (asset in result.filter { asset ->
                asset.ownerUserId == expectedOwnerUserId && asset.matchId in matchIds && asset.hasConfirmedResultCrop()
            }) {
                if (!isCurrentOwner(expectedOwnerUserId)) return
                retryResult(asset, expectedOwnerUserId)
            }
        }
    }

    private suspend fun retryLobby(asset: MatchLobbyScreenshotAssetEntity, expectedOwnerUserId: String) {
        val identity = asset.identityOrNull() ?: return
        try {
            lobbyCheckpoint.run(identity, expectedOwnerUserId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Keep the local asset retryable without affecting foreground navigation.
        }
    }

    private suspend fun retryResult(asset: MatchResultScreenshotAssetEntity, expectedOwnerUserId: String) {
        val identity = asset.identityOrNull() ?: return
        try {
            resultCheckpoint.run(identity, expectedOwnerUserId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Keep the local asset retryable without affecting foreground navigation.
        }
    }

    private suspend fun isCurrentOwner(expectedOwnerUserId: String): Boolean =
        try {
            ownerProvider.currentOwnerUserId() == expectedOwnerUserId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
}

private fun MatchLobbyScreenshotAssetEntity.hasConfirmedLobbyCrop(): Boolean =
    cropProfileId == OcrCropValidationProfiles.Lobby.id &&
        validCrop(originalWidth, originalHeight, cropLeft, cropTop, cropRight, cropBottom, OcrCropValidationProfiles.Lobby)

private fun MatchResultScreenshotAssetEntity.hasConfirmedResultCrop(): Boolean =
    cropProfileId == OcrCropValidationProfiles.MatchResult.id &&
        validCrop(originalWidth, originalHeight, cropLeft, cropTop, cropRight, cropBottom, OcrCropValidationProfiles.MatchResult)

private fun validCrop(
    width: Int,
    height: Int,
    left: Double?,
    top: Double?,
    right: Double?,
    bottom: Double?,
    profile: com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfile,
): Boolean {
    val dimensions = OcrImageDimensions.from(width, height) ?: return false
    val crop = OcrNormalizedCropRect(
        left ?: return false,
        top ?: return false,
        right ?: return false,
        bottom ?: return false,
    )
    return OcrCropValidator.validate(crop, dimensions, profile) is OcrCropValidationResult.Valid
}
