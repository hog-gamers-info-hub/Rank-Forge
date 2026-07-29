package com.hoggamers.rankforge.presentation.screen

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RosterScreenshotLocalImageStoreResult {
    data class Preserved(
        val localRelativePath: String,
        val displayUri: String,
    ) : RosterScreenshotLocalImageStoreResult

    data object Failed : RosterScreenshotLocalImageStoreResult
}

interface RosterScreenshotLocalImageStore {
    suspend fun preserve(
        tournamentId: String,
        rosterScreenshotIndex: Int,
        selectedUri: String,
    ): RosterScreenshotLocalImageStoreResult

    suspend fun cleanup(
        tournamentId: String,
        rosterScreenshotIndex: Int,
    )

    fun displayUriOrNull(localRelativePath: String): String?
}

@Singleton
class LocalRosterScreenshotImageStore @Inject constructor(
    private val localImagePreserver: LocalImagePreserver,
) : RosterScreenshotLocalImageStore {
    override suspend fun preserve(
        tournamentId: String,
        rosterScreenshotIndex: Int,
        selectedUri: String,
    ): RosterScreenshotLocalImageStoreResult = when (
        val result = localImagePreserver.preserveRosterScreenshot(
            tournamentId = tournamentId,
            rosterScreenshotIndex = rosterScreenshotIndex,
            selectedUri = selectedUri,
        )
    ) {
        is LocalImagePreservationResult.Preserved -> result.toStoreResult()
        is LocalImagePreservationResult.PreservedWithCleanupFailure -> result.toStoreResult()
        is LocalImagePreservationResult.Failed -> RosterScreenshotLocalImageStoreResult.Failed
    }

    override suspend fun cleanup(tournamentId: String, rosterScreenshotIndex: Int) {
        localImagePreserver.cleanupRosterScreenshot(tournamentId, rosterScreenshotIndex)
    }

    override fun displayUriOrNull(localRelativePath: String): String? {
        val file = localImagePreserver.resolveRelativePath(localRelativePath) ?: return null
        return file.takeIf(File::isFile)?.takeIf(File::canRead)?.toURI()?.toString()
    }

    private fun LocalImagePreservationResult.Preserved.toStoreResult(): RosterScreenshotLocalImageStoreResult {
        val relativePath = localImagePreserver.relativePathFor(file)
            ?: return RosterScreenshotLocalImageStoreResult.Failed
        return RosterScreenshotLocalImageStoreResult.Preserved(relativePath, file.toURI().toString())
    }

    private fun LocalImagePreservationResult.PreservedWithCleanupFailure.toStoreResult(): RosterScreenshotLocalImageStoreResult {
        val relativePath = localImagePreserver.relativePathFor(file)
            ?: return RosterScreenshotLocalImageStoreResult.Failed
        return RosterScreenshotLocalImageStoreResult.Preserved(relativePath, file.toURI().toString())
    }
}

class NoOpRosterScreenshotLocalImageStore : RosterScreenshotLocalImageStore {
    override suspend fun preserve(
        tournamentId: String,
        rosterScreenshotIndex: Int,
        selectedUri: String,
    ): RosterScreenshotLocalImageStoreResult = RosterScreenshotLocalImageStoreResult.Preserved(
        localRelativePath = "",
        displayUri = selectedUri,
    )

    override suspend fun cleanup(tournamentId: String, rosterScreenshotIndex: Int) = Unit

    override fun displayUriOrNull(localRelativePath: String): String? = null
}
