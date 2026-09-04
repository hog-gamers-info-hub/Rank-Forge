package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import com.hoggamers.rankforge.presentation.screen.LocalImagePreservationResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import io.github.jan.supabase.auth.auth
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive

data class VerifiedCustomDesignTemplate(
    val customDesignId: String,
    val ownerUserId: String,
    val imagePath: String,
    val imageSha256: String,
    val imageByteSize: Long,
    val imageExtension: String,
    val imageMimeType: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val labels: CustomDesignOcrLabels,
    val geometry: CustomDesignEffectiveGridGeometry,
)

enum class CustomDesignRestoreFailure {
    VALIDATION,
    MISSING_AUTH_SESSION,
    NOT_FOUND,
    AUTHORIZATION,
    READ_FAILED,
    DOWNLOAD_FAILED,
    INTEGRITY_MISMATCH,
    LOCAL_PERSISTENCE,
}

sealed interface CustomDesignRestoreResult {
    data class Success(val design: RestoredCustomDesign) : CustomDesignRestoreResult
    data class Failed(val failure: CustomDesignRestoreFailure) : CustomDesignRestoreResult
}

data class RestoredCustomDesign(
    val customDesignId: String,
    val ownerUserId: String,
    val localImageReference: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val labels: CustomDesignOcrLabels,
    val geometry: CustomDesignEffectiveGridGeometry,
)

fun interface CustomDesignRestoreAction {
    suspend fun restore(customDesignId: String): CustomDesignRestoreResult
}

@Singleton
class CustomDesignRestoreCoordinator internal constructor(
    private val currentUserId: suspend () -> String?,
    private val cloudDataSource: CustomDesignTemplateCloudDataSource,
    private val storageDownloader: AuthenticatedScreenshotStorageDownloader,
    private val localImagePreserver: LocalImagePreserver,
) : CustomDesignRestoreAction {
    @Inject
    constructor(
        clientProvider: SupabaseClientProvider,
        cloudDataSource: CustomDesignTemplateCloudDataSource,
        storageDownloader: AuthenticatedScreenshotStorageDownloader,
        localImagePreserver: LocalImagePreserver,
    ) : this(
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        cloudDataSource = cloudDataSource,
        storageDownloader = storageDownloader,
        localImagePreserver = localImagePreserver,
    )

    override suspend fun restore(customDesignId: String): CustomDesignRestoreResult {
        if (!isCanonicalCustomDesignUuid(customDesignId)) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.VALIDATION)
        }
        val ownerId = currentUserId()
            ?.takeIf { isCanonicalCustomDesignUuid(it) }
            ?: return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.MISSING_AUTH_SESSION)
        val payload = when (val read = cloudDataSource.readById(customDesignId, ownerId)) {
            is CustomDesignTemplateCloudReadResult.Success -> read.payload
            CustomDesignTemplateCloudReadResult.NotFound ->
                return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.NOT_FOUND)
            is CustomDesignTemplateCloudReadResult.Failed -> return read.failure.toRestoreFailure()
        }
        val verified = CustomDesignTemplateValidator.validate(payload, customDesignId, ownerId)
            ?: return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.VALIDATION)
        if (currentUserId() != ownerId) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION)
        }

        val bytes = try {
            storageDownloader.download(
                expectedOwnerUserId = ownerId,
                bucket = CUSTOM_DESIGNS_BUCKET,
                objectPath = verified.imagePath,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION)
        } catch (_: IOException) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.DOWNLOAD_FAILED)
        } catch (_: Throwable) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.DOWNLOAD_FAILED)
        }
        if (bytes.size.toLong() != verified.imageByteSize || bytes.sha256() != verified.imageSha256) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.INTEGRITY_MISMATCH)
        }
        if (currentUserId() != ownerId) {
            return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION)
        }

        var restoredFile: File? = null
        return try {
            val preserved = localImagePreserver.restoreCustomDesignImage(
                ownerUserId = ownerId,
                customDesignId = customDesignId,
                extension = verified.imageExtension,
                bytes = bytes,
            )
            val file = when (preserved) {
                is LocalImagePreservationResult.Preserved -> preserved.file
                is LocalImagePreservationResult.PreservedWithCleanupFailure -> preserved.file
                is LocalImagePreservationResult.Failed ->
                    return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.LOCAL_PERSISTENCE)
            }
            restoredFile = file
            val expectedFile = localImagePreserver.customDesignPreservedFile(
                ownerUserId = ownerId,
                customDesignId = customDesignId,
                extension = verified.imageExtension,
            )
            if (file != expectedFile) {
                localImagePreserver.delete(file)
                return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.LOCAL_PERSISTENCE)
            }
            if (currentUserId() != ownerId) {
                localImagePreserver.delete(file)
                restoredFile = null
                return CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.AUTHORIZATION)
            }
            CustomDesignRestoreResult.Success(
                RestoredCustomDesign(
                    customDesignId = verified.customDesignId,
                    ownerUserId = verified.ownerUserId,
                    localImageReference = file.toURI().toString(),
                    sourceWidth = verified.sourceWidth,
                    sourceHeight = verified.sourceHeight,
                    labels = verified.labels,
                    geometry = verified.geometry,
                ),
            )
        } catch (cancellation: CancellationException) {
            restoredFile?.let(localImagePreserver::delete)
            throw cancellation
        }
    }
}

internal object CustomDesignTemplateValidator {
    private val labelKeys = setOf("teamName", "win", "totalKills", "positionPoints", "totalPoints")
    private val columnKeys = CustomDesignAnchorField.entries.mapTo(linkedSetOf()) { it.name }
    private val rowKeys = (1..12).mapTo(linkedSetOf(), Int::toString)

    fun validate(
        payload: CustomDesignTemplateCloudPayload,
        requestedId: String,
        expectedOwnerUserId: String,
    ): VerifiedCustomDesignTemplate? {
        if (!isCanonicalCustomDesignUuid(requestedId) ||
            !isCanonicalCustomDesignUuid(expectedOwnerUserId) ||
            !isCanonicalCustomDesignUuid(payload.id) ||
            !isCanonicalCustomDesignUuid(payload.userId) ||
            payload.id != requestedId ||
            payload.userId != expectedOwnerUserId ||
            payload.imageByteSize <= 0L ||
            payload.sourceWidth <= 0 ||
            payload.sourceHeight <= 0 ||
            !payload.imageSha256.matches(Regex("[0-9a-f]{64}"))
        ) return null
        val expectedFormat = when (payload.imageExtension) {
            "png" -> "image/png"
            "jpg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> return null
        }
        if (payload.imageMimeType != expectedFormat ||
            payload.imagePath != SupabaseCustomDesignStorageUploader.objectPath(
                expectedOwnerUserId,
                requestedId,
                payload.imageExtension,
            )
        ) return null

        val labels = labelKeys.associateWith { key ->
            (payload.labelsJson[key] as? JsonPrimitive)
                ?.takeIf { it.isString && it.content.isNotBlank() }
                ?.content
        }
        if (labels.values.any { it == null }) return null

        val columns = linkedMapOf<CustomDesignAnchorField, Float>()
        for (field in CustomDesignAnchorField.entries) {
            val x = payload.columnsJson[field.name]?.finiteNumber() ?: return null
            if (x !in 0.0..payload.sourceWidth.toDouble()) return null
            columns[field] = x.toFloat()
        }
        val rows = linkedMapOf<Int, Float>()
        for (rank in 1..12) {
            val y = payload.rowsJson[rank.toString()]?.finiteNumber() ?: return null
            if (y !in 0.0..payload.sourceHeight.toDouble()) return null
            rows[rank] = y.toFloat()
        }
        if ((1..11).any { rows.getValue(it) >= rows.getValue(it + 1) }) return null
        if (payload.labelsJson.keys != labelKeys ||
            payload.columnsJson.keys != columnKeys ||
            payload.rowsJson.keys != rowKeys
        ) return null
        return VerifiedCustomDesignTemplate(
            customDesignId = payload.id,
            ownerUserId = payload.userId,
            imagePath = payload.imagePath,
            imageSha256 = payload.imageSha256,
            imageByteSize = payload.imageByteSize,
            imageExtension = payload.imageExtension,
            imageMimeType = payload.imageMimeType,
            sourceWidth = payload.sourceWidth,
            sourceHeight = payload.sourceHeight,
            labels = CustomDesignOcrLabels(
                teamName = labels.getValue("teamName")!!,
                win = labels.getValue("win")!!,
                totalKills = labels.getValue("totalKills")!!,
                positionPoints = labels.getValue("positionPoints")!!,
                totalPoints = labels.getValue("totalPoints")!!,
            ),
            geometry = CustomDesignEffectiveGridGeometry(
                sourceWidth = payload.sourceWidth,
                sourceHeight = payload.sourceHeight,
                columnX = columns,
                rowY = rows,
            ),
        )
    }

    private fun kotlinx.serialization.json.JsonElement.finiteNumber(): Double? =
        (this as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.content
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }
}

private fun CustomDesignTemplateCloudFailure.toRestoreFailure() = when (this) {
    CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION -> CustomDesignRestoreResult.Failed(
        CustomDesignRestoreFailure.MISSING_AUTH_SESSION,
    )
    CustomDesignTemplateCloudFailure.AUTHORIZATION -> CustomDesignRestoreResult.Failed(
        CustomDesignRestoreFailure.AUTHORIZATION,
    )
    CustomDesignTemplateCloudFailure.VALIDATION -> CustomDesignRestoreResult.Failed(
        CustomDesignRestoreFailure.VALIDATION,
    )
    CustomDesignTemplateCloudFailure.READ_FAILED,
    CustomDesignTemplateCloudFailure.WRITE_FAILED,
    CustomDesignTemplateCloudFailure.DELETE_FAILED,
    -> CustomDesignRestoreResult.Failed(CustomDesignRestoreFailure.READ_FAILED)
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
