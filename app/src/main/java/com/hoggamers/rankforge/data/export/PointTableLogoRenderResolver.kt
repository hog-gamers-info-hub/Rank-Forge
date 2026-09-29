package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hoggamers.rankforge.data.local.PointTableDetailsRepository
import com.hoggamers.rankforge.data.local.PointTableLogoImageStore
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PointTableLogoRenderResolver @Inject constructor(
    private val pointTableDetailsRepository: PointTableDetailsRepository,
    private val pointTableLogoImageStore: PointTableLogoImageStore,
) {
    suspend fun resolve(
        tournamentId: String,
        designKey: String,
        organizationLogoPath: String?,
    ): PointTableLogoRenderData? = withContext(Dispatchers.IO) {
        if (tournamentId.isBlank() || designKey.isBlank() || organizationLogoPath == null) {
            return@withContext null
        }
        val placement = pointTableDetailsRepository.getPointTableLogoPlacement(
            tournamentId = tournamentId,
            designKey = designKey,
        )?.takeIf {
            it.tournamentId == tournamentId &&
                it.designKey == designKey &&
                it.isValid()
        } ?: return@withContext null
        val displayUri = pointTableLogoImageStore.displayUriOrNull(organizationLogoPath)
            ?: return@withContext null
        val filePath = Uri.parse(displayUri).path ?: return@withContext null
        val bitmap = decodeBitmap(filePath) ?: return@withContext null
        PointTableLogoRenderData(bitmap = bitmap, placement = placement)
    }

    private fun decodeBitmap(filePath: String): Bitmap? {
        val bitmap = try {
            BitmapFactory.decodeFile(filePath)
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
            return null
        }
        return bitmap
    }
}
