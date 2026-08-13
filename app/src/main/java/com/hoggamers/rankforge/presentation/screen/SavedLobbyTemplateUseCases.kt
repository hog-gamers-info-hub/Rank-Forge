package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetEntity
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface SaveLobbyTemplateResult {
    data object Saved : SaveLobbyTemplateResult
    data object NotReady : SaveLobbyTemplateResult
    data object Failed : SaveLobbyTemplateResult
}

sealed interface ApplyLobbyTemplateResult {
    data object Applied : ApplyLobbyTemplateResult
    data object Unavailable : ApplyLobbyTemplateResult
    data object Failed : ApplyLobbyTemplateResult
}

fun interface ApplyLobbyTemplateAction {
    suspend operator fun invoke(tournamentId: String, newMatchId: String): ApplyLobbyTemplateResult
}

fun interface MatchLobbyScreenshotUploadCheckpointAction {
    suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult
}

class SaveLobbyTemplateUseCase @Inject constructor(
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val templateRepository: TournamentLobbyTemplateAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        tournamentId: String,
        sourceMatchId: String,
    ): SaveLobbyTemplateResult {
        val previousTemplates = try {
            templateRepository.getByTournamentId(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return SaveLobbyTemplateResult.Failed
        }
        val assets = (1..3).map { index ->
            assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, sourceMatchId, index))
        }
        val prepared = assets.mapIndexed { offset, asset ->
            asset?.let { prepareAsset(tournamentId, sourceMatchId, offset + 1, it) }
        }
        if (prepared.any { it == null }) return SaveLobbyTemplateResult.NotReady
        val validAssets = prepared.filterNotNull()
        if (validAssets.map { it.asset.sha256 }.toSet().size != 3) return SaveLobbyTemplateResult.NotReady

        val generation = UUID.randomUUID().toString()
        val now = clock.millis()
        val snapshots = mutableListOf<TournamentLobbyTemplateAssetEntity>()
        try {
            validAssets.forEach { preparedAsset ->
                val copy = localImagePreserver.snapshotLobbyTemplate(
                    tournamentId = tournamentId,
                    generation = generation,
                    lobbyScreenshotIndex = preparedAsset.index,
                    sourceFile = preparedAsset.file,
                    extension = preparedAsset.asset.fileExtension,
                )
                val copiedFile = when (copy) {
                    is LocalImagePreservationResult.Preserved -> copy.file
                    is LocalImagePreservationResult.PreservedWithCleanupFailure -> copy.file
                    is LocalImagePreservationResult.Failed -> {
                        cleanupTemplateGenerationBestEffort(tournamentId, generation)
                        return SaveLobbyTemplateResult.Failed
                    }
                }
                val relativePath = localImagePreserver.relativePathFor(copiedFile)
                    ?: run {
                        cleanupTemplateGenerationBestEffort(tournamentId, generation)
                        return SaveLobbyTemplateResult.Failed
                    }
                snapshots += TournamentLobbyTemplateAssetEntity(
                    tournamentId = tournamentId,
                    lobbyScreenshotIndex = preparedAsset.index,
                    ownerUserId = preparedAsset.asset.ownerUserId,
                    localRelativePath = relativePath,
                    fileExtension = preparedAsset.asset.fileExtension,
                    mimeType = preparedAsset.asset.mimeType,
                    originalWidth = preparedAsset.asset.originalWidth,
                    originalHeight = preparedAsset.asset.originalHeight,
                    byteSize = preparedAsset.asset.byteSize,
                    sha256 = preparedAsset.asset.sha256,
                    cropProfileId = preparedAsset.crop.cropProfileId,
                    cropLeft = preparedAsset.crop.crop.left,
                    cropTop = preparedAsset.crop.crop.top,
                    cropRight = preparedAsset.crop.crop.right,
                    cropBottom = preparedAsset.crop.crop.bottom,
                    sourceMatchId = sourceMatchId,
                    savedAt = now,
                    updatedAt = now,
                    revision = 1L,
                )
            }
        } catch (cancellation: CancellationException) {
            cleanupTemplateGenerationBestEffort(tournamentId, generation)
            throw cancellation
        } catch (_: Throwable) {
            cleanupTemplateGenerationBestEffort(tournamentId, generation)
            return SaveLobbyTemplateResult.Failed
        }
        val replacementResult = try {
            templateRepository.replaceForTournament(tournamentId, snapshots)
            SaveLobbyTemplateResult.Saved
        } catch (cancellation: CancellationException) {
            cleanupTemplateGenerationBestEffort(tournamentId, generation)
            throw cancellation
        } catch (_: Throwable) {
            cleanupTemplateGenerationBestEffort(tournamentId, generation)
            return SaveLobbyTemplateResult.Failed
        }
        if (replacementResult == SaveLobbyTemplateResult.Saved) {
            previousTemplates
                .mapNotNull { template ->
                    localImagePreserver.lobbyTemplateGenerationFromRelativePath(
                        tournamentId,
                        template.localRelativePath,
                    )
                }
                .distinct()
                .filter { it != generation }
                .forEach { previousGeneration ->
                    cleanupTemplateGenerationBestEffort(tournamentId, previousGeneration)
                }
        }
        return replacementResult
    }

    private suspend fun cleanupTemplateGenerationBestEffort(
        tournamentId: String,
        generation: String,
    ) {
        withContext(NonCancellable) {
            try {
                localImagePreserver.cleanupLobbyTemplateGeneration(tournamentId, generation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Cleanup must not change the save result or remove the active generation.
            }
        }
    }

    private fun prepareAsset(
        tournamentId: String,
        sourceMatchId: String,
        index: Int,
        asset: MatchLobbyScreenshotAssetEntity,
    ): PreparedAsset? {
        val identity = MatchLobbyScreenshotIdentity(tournamentId, sourceMatchId, index)
        if (asset.identityOrNull() != identity || asset.ownerUserId.isBlank() || asset.sha256.isBlank()) return null
        val file = localImagePreserver.resolveRelativePath(asset.localRelativePath)
            ?: return null
        if (!runCatching { file.isFile && file.canRead() && file.length() > 0L }.getOrDefault(false)) return null
        val crop = confirmedCrop(asset) ?: return null
        if (asset.mimeType.isBlank() || asset.fileExtension.isBlank() || asset.byteSize <= 0L) return null
        if (OcrImageDimensions.from(asset.originalWidth, asset.originalHeight) == null) return null
        return PreparedAsset(index, asset, file, crop)
    }

    private fun confirmedCrop(asset: MatchLobbyScreenshotAssetEntity): ConfirmedCrop? {
        if (asset.cropProfileId != OcrCropValidationProfiles.Lobby.id) return null
        val crop = OcrNormalizedCropRect(
            asset.cropLeft ?: return null,
            asset.cropTop ?: return null,
            asset.cropRight ?: return null,
            asset.cropBottom ?: return null,
        )
        return if (OcrCropValidator.validate(
                crop,
                OcrImageDimensions.from(asset.originalWidth, asset.originalHeight),
                OcrCropValidationProfiles.Lobby,
            ) is OcrCropValidationResult.Valid
        ) {
            ConfirmedCrop(asset.cropProfileId, crop)
        } else {
            null
        }
    }

    private data class PreparedAsset(
        val index: Int,
        val asset: MatchLobbyScreenshotAssetEntity,
        val file: File,
        val crop: ConfirmedCrop,
    )

    private data class ConfirmedCrop(
        val cropProfileId: String,
        val crop: OcrNormalizedCropRect,
    )
}

class ApplyLobbyTemplateToMatchUseCase @Inject constructor(
    private val templateRepository: TournamentLobbyTemplateAssetRepository,
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val clock: Clock,
) : ApplyLobbyTemplateAction {
    override suspend operator fun invoke(
        tournamentId: String,
        newMatchId: String,
    ): ApplyLobbyTemplateResult {
        val templates = templateRepository.getByTournamentId(tournamentId)
        if (!isCompleteTemplate(tournamentId, templates)) return ApplyLobbyTemplateResult.Unavailable
        val copied = mutableListOf<Pair<TournamentLobbyTemplateAssetEntity, File>>()
        templates.forEach { template ->
            val result = localImagePreserver.copyLobbyTemplateToMatch(
                tournamentId = tournamentId,
                lobbyScreenshotIndex = template.lobbyScreenshotIndex,
                matchId = newMatchId,
                templateRelativePath = template.localRelativePath,
                extension = template.fileExtension,
            )
            val file = when (result) {
                is LocalImagePreservationResult.Preserved -> result.file
                is LocalImagePreservationResult.PreservedWithCleanupFailure -> result.file
                is LocalImagePreservationResult.Failed -> {
                    copied.forEach { (_, copiedFile) ->
                        localImagePreserver.cleanupLobbyScreenshot(
                            tournamentId,
                            newMatchId,
                            copiedFile.parentFile?.name?.toIntOrNull() ?: return@forEach,
                        )
                    }
                    return ApplyLobbyTemplateResult.Failed
                }
            }
            copied += template to file
        }
        val now = clock.millis()
        val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            ?: templates.first().ownerUserId
        val savedIndices = mutableListOf<Int>()
        copied.forEach { (template, file) ->
            val identity = MatchLobbyScreenshotIdentity(tournamentId, newMatchId, template.lobbyScreenshotIndex)
            val asset = MatchLobbyScreenshotAssetEntity(
                tournamentId = tournamentId,
                matchId = newMatchId,
                lobbyScreenshotIndex = template.lobbyScreenshotIndex,
                ownerUserId = ownerUserId,
                localRelativePath = localImagePreserver.relativePathFor(file)
                    ?: localImagePreserver.lobbyRelativePath(
                        tournamentId,
                        newMatchId,
                        template.lobbyScreenshotIndex,
                        template.fileExtension,
                    ),
                fileExtension = template.fileExtension,
                mimeType = template.mimeType,
                originalWidth = template.originalWidth,
                originalHeight = template.originalHeight,
                byteSize = template.byteSize,
                sha256 = template.sha256,
                localStatus = ScreenshotLocalStatus.PRESERVED.name,
                uploadStatus = ScreenshotUploadStatus.PENDING.name,
                uploadFailureCode = null,
                storageBucket = null,
                storageObjectPath = null,
                cropProfileId = template.cropProfileId,
                cropLeft = template.cropLeft,
                cropTop = template.cropTop,
                cropRight = template.cropRight,
                cropBottom = template.cropBottom,
                createdAt = now,
                updatedAt = now,
                preservedAt = now,
                uploadedAt = null,
                revision = 1L,
            )
            if (assetRepository.saveOrReplace(asset) !is MatchLobbyScreenshotAssetSaveResult.Saved) {
                savedIndices.forEach { savedIndex ->
                    assetRepository.deleteByIdentity(
                        MatchLobbyScreenshotIdentity(tournamentId, newMatchId, savedIndex),
                    )
                }
                copied.forEach { (_, copiedFile) ->
                    localImagePreserver.cleanupLobbyScreenshot(
                        tournamentId,
                        newMatchId,
                        copiedFile.parentFile?.name?.toIntOrNull() ?: return@forEach,
                    )
                }
                return ApplyLobbyTemplateResult.Failed
            }
            savedIndices += template.lobbyScreenshotIndex
            if (asset.identityOrNull() != identity) return ApplyLobbyTemplateResult.Failed
        }
        return ApplyLobbyTemplateResult.Applied
    }

    private fun isCompleteTemplate(
        tournamentId: String,
        templates: List<TournamentLobbyTemplateAssetEntity>,
    ): Boolean {
        if (templates.map { it.lobbyScreenshotIndex } != listOf(1, 2, 3)) return false
        if (templates.any { it.tournamentId != tournamentId || it.ownerUserId.isBlank() || it.sha256.isBlank() }) return false
        if (templates.map { it.sha256 }.toSet().size != 3) return false
        return templates.all { template ->
            val file = localImagePreserver.resolveRelativePath(template.localRelativePath)
            val crop = OcrNormalizedCropRect(
                template.cropLeft,
                template.cropTop,
                template.cropRight,
                template.cropBottom,
            )
            file != null && runCatching { file.isFile && file.canRead() && file.length() > 0L }.getOrDefault(false) &&
                template.cropProfileId == OcrCropValidationProfiles.Lobby.id &&
                OcrCropValidator.validate(
                    crop,
                    OcrImageDimensions.from(template.originalWidth, template.originalHeight),
                    OcrCropValidationProfiles.Lobby,
                ) is OcrCropValidationResult.Valid
        }
    }
}
