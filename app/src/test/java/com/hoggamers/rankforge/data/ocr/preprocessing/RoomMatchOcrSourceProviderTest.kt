package com.hoggamers.rankforge.data.ocr.preprocessing

import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.domain.ocr.review.MatchOcrSourceProviderResult
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoomMatchOcrSourceProviderTest {

    @Test
    fun blankContextStopsBeforeMetadataLookup() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(),
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = " ",
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.InvalidContext,
            result,
        )
        assertEquals(0, repository.getCalls)
    }

    @Test
    fun missingMetadataReturnsTypedFailure() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = null,
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.MetadataNotFound,
            result,
        )
        assertEquals(1, repository.getCalls)
        assertEquals(MATCH_ID, repository.lastMatchId)
    }

    @Test
    fun metadataRepositoryFailureRemainsControlled() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(),
            throwUnexpected = true,
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.LoadingFailure,
            result,
        )
        assertEquals(1, repository.getCalls)
    }

    @Test
    fun metadataRepositoryCancellationPropagates() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(),
            throwCancellation = true,
        )

        val provider = provider(
            repository = repository,
        )

        assertCancellation {
            provider.load(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            )
        }

        assertEquals(1, repository.getCalls)
    }

    @Test
    fun tournamentMismatchStopsBeforeLocalFileResolution() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(
                tournamentId = "foreign-tournament",
            ),
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.TournamentMismatch,
            result,
        )
        assertEquals(1, repository.getCalls)
    }

    @Test
    fun unsupportedMimeTypeIsRejectedBeforeFileAccess() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(
                mimeType = "image/gif",
            ),
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.UnsafeImage,
            result,
        )
    }

    @Test
    fun oversizedDimensionIsRejectedBeforeFileAccess() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(
                width = 8_193,
                height = 720,
            ),
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.UnsafeImage,
            result,
        )
    }

    @Test
    fun excessivePixelCountIsRejectedBeforeFileAccess() = runTest {
        val repository = FakeScreenshotMetadataRepository(
            metadata = metadata(
                width = 8_000,
                height = 8_000,
            ),
        )

        val provider = provider(
            repository = repository,
        )

        val result = provider.load(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            MatchOcrSourceProviderResult.UnsafeImage,
            result,
        )
    }

    @Test
    fun nonPositiveDimensionsAreRejectedBeforeFileAccess() = runTest {
        val invalidDimensions = listOf(
            0 to 720,
            -1 to 720,
            1600 to 0,
            1600 to -1,
        )

        invalidDimensions.forEach { (width, height) ->
            val provider = provider(
                repository = FakeScreenshotMetadataRepository(
                    metadata = metadata(
                        width = width,
                        height = height,
                    ),
                ),
            )

            val result = provider.load(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            )

            assertEquals(
                MatchOcrSourceProviderResult.UnsafeImage,
                result,
            )
        }
    }

    @Test
    fun traversalRelativePathIsRejectedAsMissingLocalFile() = runTest {
        val root = createTempRoot()

        try {
            val repository = FakeScreenshotMetadataRepository(
                metadata = metadata(
                    localRelativePath =
                        "screenshots/../outside/original.jpg",
                ),
            )

            val provider = provider(
                repository = repository,
                root = root,
            )

            val result = provider.load(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            )

            assertEquals(
                MatchOcrSourceProviderResult.LocalFileMissing,
                result,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun pathOutsideScreenshotNamespaceIsRejectedAsMissingLocalFile() = runTest {
        val root = createTempRoot()

        try {
            val repository = FakeScreenshotMetadataRepository(
                metadata = metadata(
                    localRelativePath =
                        "other-directory/original.jpg",
                ),
            )

            val provider = provider(
                repository = repository,
                root = root,
            )

            val result = provider.load(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            )

            assertEquals(
                MatchOcrSourceProviderResult.LocalFileMissing,
                result,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun supportedMetadataWithMissingPrivateFileReturnsTypedFailure() = runTest {
        val root = createTempRoot()

        try {
            val repository = FakeScreenshotMetadataRepository(
                metadata = metadata(
                    mimeType = "IMAGE/JPEG",
                    localRelativePath =
                        "screenshots/test-tournament/test-match/original.jpg",
                ),
            )

            val provider = provider(
                repository = repository,
                root = root,
            )

            val result = provider.load(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            )

            assertEquals(
                MatchOcrSourceProviderResult.LocalFileMissing,
                result,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun provider(
        repository: ScreenshotMetadataRepository,
        root: File = createTempRoot(),
    ): RoomMatchOcrSourceProvider =
        RoomMatchOcrSourceProvider(
            metadataRepository = repository,
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = root,
                sourceStreamOpener =
                    ImageSourceStreamOpener { null },
                mimeTypeReader =
                    ImageSourceMimeTypeReader { null },
            ),
        )

    private fun metadata(
        matchId: String = MATCH_ID,
        tournamentId: String = TOURNAMENT_ID,
        localRelativePath: String =
            "screenshots/test-tournament/test-match/original.jpg",
        mimeType: String = "image/jpeg",
        width: Int = 1600,
        height: Int = 720,
    ): ScreenshotMetadataEntity =
        ScreenshotMetadataEntity(
            matchId = matchId,
            tournamentId = tournamentId,
            ownerUserId = "synthetic-owner",
            localRelativePath = localRelativePath,
            fileExtension = "jpg",
            mimeType = mimeType,
            width = width,
            height = height,
            byteSize = 1024L,
            sha256 = "synthetic-sha256",
            storageBucket = null,
            storageObjectPath = null,
            localStatus = "PRESERVED",
            uploadStatus = "PENDING",
            uploadFailureCode = null,
            createdAt = 1L,
            updatedAt = 1L,
            preservedAt = 1L,
            uploadedAt = null,
            revision = 1L,
        )

    private fun createTempRoot(): File =
        kotlin.io.path.createTempDirectory(
            prefix = "rank-forge-match-ocr-source-",
        ).toFile()

    private suspend fun assertCancellation(
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            Unit
        }
    }

    private class FakeScreenshotMetadataRepository(
        private val metadata: ScreenshotMetadataEntity?,
        private val throwUnexpected: Boolean = false,
        private val throwCancellation: Boolean = false,
    ) : ScreenshotMetadataRepository {

        var getCalls = 0
        var lastMatchId: String? = null

        override fun observeByMatchId(
            matchId: String,
        ): Flow<ScreenshotMetadataEntity?> =
            flowOf(metadata)

        override suspend fun getByMatchId(
            matchId: String,
        ): ScreenshotMetadataEntity? {
            getCalls += 1
            lastMatchId = matchId

            if (throwCancellation) {
                throw CancellationException(
                    "test cancellation",
                )
            }

            if (throwUnexpected) {
                error("test repository failure")
            }

            return metadata
        }

        override fun observeByTournamentId(
            tournamentId: String,
        ): Flow<List<ScreenshotMetadataEntity>> =
            flowOf(
                metadata
                    ?.takeIf {
                        it.tournamentId == tournamentId
                    }
                    ?.let(::listOf)
                    .orEmpty(),
            )

        override suspend fun createOrReplace(
            metadata: ScreenshotMetadataEntity,
        ) = Unit

        override suspend fun updateUploadSuccess(
            matchId: String,
            storageBucket: String,
            storageObjectPath: String,
            uploadedAt: Long,
            updatedAt: Long,
        ) = Unit

        override suspend fun updateUploadFailure(
            matchId: String,
            failureCode: String,
            updatedAt: Long,
        ) = Unit

        override suspend fun markLocalMissing(
            matchId: String,
            updatedAt: Long,
        ) = Unit

        override suspend fun markCleanupFailure(
            matchId: String,
            updatedAt: Long,
        ) = Unit

        override suspend fun deleteByMatchId(
            matchId: String,
        ) = Unit

        override suspend fun deleteByTournamentId(
            tournamentId: String,
        ) = Unit
    }

    private companion object {
        const val TOURNAMENT_ID =
            "test-tournament"

        const val MATCH_ID =
            "test-match"
    }
}