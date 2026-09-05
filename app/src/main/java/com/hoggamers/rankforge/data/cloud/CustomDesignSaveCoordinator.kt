package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import io.github.jan.supabase.auth.auth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CustomDesignSaveRequest(
    val imageReference: String,
    val draftSourceWidth: Int,
    val draftSourceHeight: Int,
    val currentSourceWidth: Int,
    val currentSourceHeight: Int,
    val labels: CustomDesignOcrLabels,
    val effectiveGridGeometry: CustomDesignEffectiveGridGeometry?,
    val textColors: CustomDesignColumnTextColors = CustomDesignColumnTextColors.allBlack(),
)

enum class CustomDesignSaveFailure {
    VALIDATION,
    MISSING_AUTH_SESSION,
    AUTHORIZATION,
    IMAGE_PREPARATION,
    STORAGE_UPLOAD,
    DATABASE_INSERT,
}

sealed interface CustomDesignSaveResult {
    data class Success(val customDesignId: String, val objectPath: String) : CustomDesignSaveResult
    data class Failed(val failure: CustomDesignSaveFailure) : CustomDesignSaveResult
}

fun interface CustomDesignSaveAction {
    suspend fun save(request: CustomDesignSaveRequest): CustomDesignSaveResult
}

@Singleton
class CustomDesignSaveCoordinator internal constructor(
    private val currentUserId: suspend () -> String?,
    private val imagePreparer: CustomDesignImagePreparer,
    private val storageUploader: CustomDesignStorageUploader,
    private val cloudDataSource: CustomDesignTemplateCloudDataSource,
) : CustomDesignSaveAction {
    @Inject
    constructor(
        clientProvider: SupabaseClientProvider,
        imagePreparer: CustomDesignImagePreparer,
        storageUploader: CustomDesignStorageUploader,
        cloudDataSource: CustomDesignTemplateCloudDataSource,
    ) : this(
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        imagePreparer = imagePreparer,
        storageUploader = storageUploader,
        cloudDataSource = cloudDataSource,
    )

    override suspend fun save(request: CustomDesignSaveRequest): CustomDesignSaveResult {
        val ownerId = currentUserId()
            ?: return CustomDesignSaveResult.Failed(CustomDesignSaveFailure.MISSING_AUTH_SESSION)
        validate(request)?.let { return CustomDesignSaveResult.Failed(it) }

        val customDesignId = UUID.randomUUID().toString()
        val preparedImage = try {
            imagePreparer.prepare(request.imageReference)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return CustomDesignSaveResult.Failed(CustomDesignSaveFailure.IMAGE_PREPARATION)
        }
        var uploaded = false
        return try {
            val uploadResult = storageUploader.upload(
                expectedOwnerUserId = ownerId,
                customDesignId = customDesignId,
                preparedFile = preparedImage.file,
                extension = preparedImage.extension,
                mimeType = preparedImage.mimeType,
            )
            val objectPath = when (uploadResult) {
                is CustomDesignStorageUploadResult.Uploaded -> {
                    uploaded = true
                    uploadResult.objectPath
                }
                is CustomDesignStorageUploadResult.Failed -> {
                    return CustomDesignSaveResult.Failed(CustomDesignSaveFailure.STORAGE_UPLOAD)
                }
            }

            if (currentUserId() != ownerId) {
                bestEffortDelete(ownerId, customDesignId, preparedImage.extension)
                return CustomDesignSaveResult.Failed(CustomDesignSaveFailure.AUTHORIZATION)
            }

            val payload = request.toPayload(
                customDesignId = customDesignId,
                ownerId = ownerId,
                objectPath = objectPath,
                preparedImage = preparedImage,
            )
            when (cloudDataSource.insert(payload, ownerId)) {
                CustomDesignTemplateCloudInsertResult.Inserted ->
                    CustomDesignSaveResult.Success(customDesignId, objectPath)
                is CustomDesignTemplateCloudInsertResult.Failed -> {
                    bestEffortDelete(ownerId, customDesignId, preparedImage.extension)
                    CustomDesignSaveResult.Failed(CustomDesignSaveFailure.DATABASE_INSERT)
                }
            }
        } catch (cancellation: CancellationException) {
            if (uploaded) {
                bestEffortDelete(
                    ownerId,
                    customDesignId,
                    preparedImage.extension,
                )
            }
            throw cancellation
        } catch (_: Throwable) {
            if (uploaded) bestEffortDelete(ownerId, customDesignId, preparedImage.extension)
            CustomDesignSaveResult.Failed(CustomDesignSaveFailure.DATABASE_INSERT)
        } finally {
            preparedImage.cleanup()
        }
    }

    private suspend fun bestEffortDelete(ownerId: String, customDesignId: String, extension: String) {
        runCatching { storageUploader.delete(ownerId, customDesignId, extension) }
    }

    private fun validate(request: CustomDesignSaveRequest): CustomDesignSaveFailure? {
        if (request.imageReference.isBlank() ||
            request.draftSourceWidth <= 0 ||
            request.draftSourceHeight <= 0 ||
            request.currentSourceWidth <= 0 ||
            request.currentSourceHeight <= 0 ||
            request.draftSourceWidth != request.currentSourceWidth ||
            request.draftSourceHeight != request.currentSourceHeight
        ) return CustomDesignSaveFailure.VALIDATION
        if (request.labels.teamName.isBlank() ||
            request.labels.win.isBlank() ||
            request.labels.totalKills.isBlank() ||
            request.labels.positionPoints.isBlank() ||
            request.labels.totalPoints.isBlank()
        ) return CustomDesignSaveFailure.VALIDATION
        val geometry = request.effectiveGridGeometry ?: return CustomDesignSaveFailure.VALIDATION
        if (geometry.sourceWidth != request.currentSourceWidth ||
            geometry.sourceHeight != request.currentSourceHeight ||
            geometry.columnX.keys != CustomDesignAnchorField.entries.toSet() ||
            geometry.rowY.keys != (1..12).toSet()
        ) return CustomDesignSaveFailure.VALIDATION
        if (geometry.columnX.values.any { !it.isFinite() || it !in 0f..request.currentSourceWidth.toFloat() }) {
            return CustomDesignSaveFailure.VALIDATION
        }
        if (geometry.rowY.values.any { !it.isFinite() || it !in 0f..request.currentSourceHeight.toFloat() }) {
            return CustomDesignSaveFailure.VALIDATION
        }
        if ((1..11).any { geometry.rowY.getValue(it) >= geometry.rowY.getValue(it + 1) }) {
            return CustomDesignSaveFailure.VALIDATION
        }
        return null
    }

    private fun CustomDesignSaveRequest.toPayload(
        customDesignId: String,
        ownerId: String,
        objectPath: String,
        preparedImage: PreparedCustomDesignImage,
    ): CustomDesignTemplateCloudPayload {
        val geometry = checkNotNull(effectiveGridGeometry)
        return CustomDesignTemplateCloudPayload(
            id = customDesignId,
            userId = ownerId,
            imagePath = objectPath,
            imageSha256 = preparedImage.sha256,
            imageByteSize = preparedImage.byteSize,
            imageExtension = preparedImage.extension,
            imageMimeType = preparedImage.mimeType,
            sourceWidth = currentSourceWidth,
            sourceHeight = currentSourceHeight,
            labelsJson = buildJsonObject {
                put("teamName", labels.teamName)
                put("win", labels.win)
                put("totalKills", labels.totalKills)
                put("positionPoints", labels.positionPoints)
                put("totalPoints", labels.totalPoints)
            },
            columnsJson = buildJsonObject {
                CustomDesignAnchorField.entries.forEach { field ->
                    put(field.name, geometry.columnX.getValue(field).toDouble())
                }
            },
            rowsJson = buildJsonObject {
                (1..12).forEach { rank -> put(rank.toString(), geometry.rowY.getValue(rank).toDouble()) }
            },
            textColorsJson = buildJsonObject {
                CustomDesignAnchorField.entries.forEach { field ->
                    put(field.name, textColors.colorFor(field))
                }
            },
        )
    }
}
