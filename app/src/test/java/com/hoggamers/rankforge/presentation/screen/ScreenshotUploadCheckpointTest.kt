package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScreenshotUploadCheckpointTest {
    private val roots = mutableListOf<File>()
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC)

    @After
    fun tearDown() {
        roots.forEach { it.deleteRecursively() }
    }

    @Test
    fun lobbyUploadedStorageObjectIsNotUploadedAgainButMetadataIsRetried() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val asset = lobbyAsset(identity, preserver, uploadStatus = ScreenshotUploadStatus.UPLOADED.name)
            .copy(storageBucket = "ocr-screenshots", storageObjectPath = "existing/path.png")
        val repository = CheckpointLobbyRepository(asset)
        var storageCalls = 0
        var cloudCalls = 0
        var cloudAsset: MatchLobbyScreenshotAssetEntity? = null
        val result = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = object : MatchLobbyScreenshotStorageUploader {
                override suspend fun upload(tournamentId: String?, matchId: String?, lobbyScreenshotIndex: Int?, localFile: File?) : MatchLobbyScreenshotStorageUploadResult {
                    storageCalls += 1
                    return error("Storage must not be called for an uploaded object")
                }
            },
            cloudDataSource = object : MatchLobbyScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
                    cloudCalls += 1
                    cloudAsset = asset
                    return MatchLobbyScreenshotAssetCloudResult.Success
                }
                override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
                    MatchLobbyScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Completed, result)
        assertEquals(0, storageCalls)
        assertEquals(1, cloudCalls)
        assertEquals(asset.sha256, cloudAsset?.sha256)
        assertEquals(asset.cropProfileId, cloudAsset?.cropProfileId)
        assertEquals(asset.cropLeft, cloudAsset?.cropLeft)
        assertEquals(asset.localRelativePath, cloudAsset?.localRelativePath)
    }

    @Test
    fun lobbyMetadataRetryPromotesRetainedStorageBeforeCloudUpsert() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val repository = CheckpointLobbyRepository(
            lobbyAsset(identity, preserver).copy(
                uploadStatus = ScreenshotUploadStatus.FAILED.name,
                uploadFailureCode = "WRITE_FAILED",
                storageBucket = "ocr-screenshots",
                storageObjectPath = "existing/path.png",
            ),
        )
        var storageCalls = 0
        var cloudCalls = 0
        var retryPayload: MatchLobbyScreenshotAssetEntity? = null
        val checkpoint = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = lobbyStorage {
                storageCalls += 1
                error("Storage must not be called when the path is retained")
            },
            cloudDataSource = object : MatchLobbyScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
                    cloudCalls += 1
                    if (cloudCalls == 1) {
                        return MatchLobbyScreenshotAssetCloudResult.Failed(
                            com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED,
                        )
                    }
                    retryPayload = asset
                    return MatchLobbyScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
                    MatchLobbyScreenshotAssetCloudResult.Success
            },
        )

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Failed, checkpoint.run(identity))
        assertEquals(ScreenshotUploadStatus.FAILED.name, repository.asset.uploadStatus)
        assertEquals(0, storageCalls)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Completed, checkpoint.run(identity))
        assertEquals(2, cloudCalls)
        assertEquals(0, storageCalls)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, repository.asset.uploadStatus)
        assertEquals(null, repository.asset.uploadFailureCode)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, retryPayload?.uploadStatus)
        assertEquals(null, retryPayload?.uploadFailureCode)
        assertEquals("ocr-screenshots", retryPayload?.storageBucket)
        assertEquals("existing/path.png", retryPayload?.storageObjectPath)
    }

    @Test
    fun resultCloudFailureIsRetainedLocallyForLaterRetry() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val repository = CheckpointResultRepository(resultAsset(identity, preserver))
        var cloudAsset: MatchResultScreenshotAssetEntity? = null
        val result = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage(MatchResultScreenshotStorageUploadResult.Uploaded("new/path.png")),
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    cloudAsset = asset
                    return MatchResultScreenshotAssetCloudResult.Failed(
                        com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure.WRITE_FAILED,
                    )
                }
                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) =
                    MatchResultScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Failed, result)
        assertEquals(ScreenshotUploadStatus.FAILED.name, repository.asset.uploadStatus)
        assertEquals(3L, repository.asset.revision)
        assertEquals(identity.tournamentId, cloudAsset?.tournamentId)
        assertEquals(identity.matchId, cloudAsset?.matchId)
        assertEquals(identity.role.name, cloudAsset?.screenshotRole)
        assertEquals(repository.asset.sha256, cloudAsset?.sha256)
        assertEquals(repository.asset.cropProfileId, cloudAsset?.cropProfileId)
        assertEquals(repository.asset.cropLeft, cloudAsset?.cropLeft)
        assertEquals(repository.asset.cropTop, cloudAsset?.cropTop)
        assertEquals(repository.asset.cropRight, cloudAsset?.cropRight)
        assertEquals(repository.asset.cropBottom, cloudAsset?.cropBottom)
        assertEquals(repository.asset.localRelativePath, cloudAsset?.localRelativePath)
        assertEquals(2L, cloudAsset?.revision)
        assertEquals(10_000L, cloudAsset?.uploadedAt)
    }

    @Test
    fun lobbyCloudFailureIsRetainedLocallyForLaterRetry() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val repository = CheckpointLobbyRepository(lobbyAsset(identity, preserver))
        var cloudAsset: MatchLobbyScreenshotAssetEntity? = null
        val result = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = lobbyStorage(MatchLobbyScreenshotStorageUploadResult.Uploaded("new/path.png")),
            cloudDataSource = object : MatchLobbyScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
                    cloudAsset = asset
                    return MatchLobbyScreenshotAssetCloudResult.Failed(
                        com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudFailure.WRITE_FAILED,
                    )
                }
                override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
                    MatchLobbyScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Failed, result)
        assertEquals(ScreenshotUploadStatus.FAILED.name, repository.asset.uploadStatus)
        assertEquals(3L, repository.asset.revision)
        assertEquals(identity.tournamentId, cloudAsset?.tournamentId)
        assertEquals(identity.matchId, cloudAsset?.matchId)
        assertEquals(identity.lobbyScreenshotIndex, cloudAsset?.lobbyScreenshotIndex)
        assertEquals(repository.asset.sha256, cloudAsset?.sha256)
        assertEquals(repository.asset.cropProfileId, cloudAsset?.cropProfileId)
        assertEquals(repository.asset.cropLeft, cloudAsset?.cropLeft)
        assertEquals(repository.asset.cropTop, cloudAsset?.cropTop)
        assertEquals(repository.asset.cropRight, cloudAsset?.cropRight)
        assertEquals(repository.asset.cropBottom, cloudAsset?.cropBottom)
        assertEquals(repository.asset.localRelativePath, cloudAsset?.localRelativePath)
        assertEquals(2L, cloudAsset?.revision)
        assertEquals(10_000L, cloudAsset?.uploadedAt)
    }

    @Test
    fun resultUploadedStorageObjectIsNotUploadedAgainButMetadataIsRetried() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val asset = resultAsset(identity, preserver).copy(
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            storageBucket = "ocr-screenshots",
            storageObjectPath = "existing/path.png",
        )
        val repository = CheckpointResultRepository(asset)
        var storageCalls = 0
        var cloudAsset: MatchResultScreenshotAssetEntity? = null
        val result = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage {
                storageCalls += 1
                error("Storage must not be called for an uploaded object")
            },
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    cloudAsset = asset
                    return MatchResultScreenshotAssetCloudResult.Success
                }
                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) =
                    MatchResultScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Completed, result)
        assertEquals(0, storageCalls)
        assertEquals(asset.sha256, cloudAsset?.sha256)
        assertEquals(asset.cropProfileId, cloudAsset?.cropProfileId)
        assertEquals(asset.cropRight, cloudAsset?.cropRight)
        assertEquals(asset.localRelativePath, cloudAsset?.localRelativePath)
    }

    @Test
    fun resultMetadataRetryPromotesRetainedStorageBeforeCloudUpsert() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val repository = CheckpointResultRepository(
            resultAsset(identity, preserver).copy(
                uploadStatus = ScreenshotUploadStatus.FAILED.name,
                uploadFailureCode = "WRITE_FAILED",
                storageBucket = "ocr-screenshots",
                storageObjectPath = "existing/path.png",
            ),
        )
        var storageCalls = 0
        var cloudCalls = 0
        var retryPayload: MatchResultScreenshotAssetEntity? = null
        val checkpoint = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage {
                storageCalls += 1
                error("Storage must not be called when the path is retained")
            },
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    cloudCalls += 1
                    if (cloudCalls == 1) {
                        return MatchResultScreenshotAssetCloudResult.Failed(
                            com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure.WRITE_FAILED,
                        )
                    }
                    retryPayload = asset
                    return MatchResultScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) =
                    MatchResultScreenshotAssetCloudResult.Success
            },
        )

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Failed, checkpoint.run(identity))
        assertEquals(ScreenshotUploadStatus.FAILED.name, repository.asset.uploadStatus)
        assertEquals(0, storageCalls)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Completed, checkpoint.run(identity))
        assertEquals(2, cloudCalls)
        assertEquals(0, storageCalls)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, repository.asset.uploadStatus)
        assertEquals(null, repository.asset.uploadFailureCode)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, retryPayload?.uploadStatus)
        assertEquals(null, retryPayload?.uploadFailureCode)
        assertEquals("ocr-screenshots", retryPayload?.storageBucket)
        assertEquals("existing/path.png", retryPayload?.storageObjectPath)
    }

    @Test
    fun lobbyStorageUploadCannotOverwriteAReplacedFingerprint() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = lobbyAsset(identity, preserver, sha = "old-sha")
        val replacement = original.copy(
            sha256 = "new-sha",
            revision = 5,
            cropProfileId = null,
            cropLeft = null,
            cropTop = null,
            cropRight = null,
            cropBottom = null,
        )
        val repository = CheckpointLobbyRepository(original)
        val result = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = object : MatchLobbyScreenshotStorageUploader {
                override suspend fun upload(tournamentId: String?, matchId: String?, lobbyScreenshotIndex: Int?, localFile: File?) : MatchLobbyScreenshotStorageUploadResult {
                    repository.asset = replacement
                    return MatchLobbyScreenshotStorageUploadResult.Uploaded("stale/path.png")
                }
            },
        ).run(identity)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Skipped, result)
        assertEquals(replacement, repository.asset)
    }

    @Test
    fun resultStorageUploadCannotOverwriteAReplacedFingerprint() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = resultAsset(identity, preserver)
        val replacement = original.copy(
            sha256 = "new-sha",
            revision = 5,
            cropProfileId = null,
            cropLeft = null,
            cropTop = null,
            cropRight = null,
            cropBottom = null,
        )
        val repository = CheckpointResultRepository(original)
        val result = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage {
                repository.asset = replacement
                MatchResultScreenshotStorageUploadResult.Uploaded("stale/path.png")
            },
        ).run(identity)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Skipped, result)
        assertEquals(replacement, repository.asset)
    }

    @Test
    fun lobbyMissingLocalFileNeverStartsStorageOrCloudUpload() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val path = preserver.lobbyRelativePath(identity.tournamentId, identity.matchId, 1, "png")
        val repository = CheckpointLobbyRepository(lobbyAsset(identity, preserver).copy(localRelativePath = path))
        preserver.resolveRelativePath(path)!!.delete()
        var storageCalls = 0
        var cloudCalls = 0
        val result = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = lobbyStorage {
                storageCalls++
                MatchLobbyScreenshotStorageUploadResult.Uploaded("unused.png")
            },
            cloudDataSource = object : MatchLobbyScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
                    cloudCalls++
                    return MatchLobbyScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
                    MatchLobbyScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Failed, result)
        assertEquals(0, storageCalls)
        assertEquals(0, cloudCalls)
        assertEquals(ScreenshotUploadStatus.FAILED.name, repository.asset.uploadStatus)
    }

    @Test
    fun resultMissingLocalFileNeverStartsStorageOrCloudUpload() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val path = preserver.matchResultRelativePath(
            identity.tournamentId,
            identity.matchId,
            identity.role,
            "png",
        )
        val repository = CheckpointResultRepository(resultAsset(identity, preserver).copy(localRelativePath = path))
        preserver.resolveRelativePath(path)!!.delete()
        var storageCalls = 0
        var cloudCalls = 0
        val result = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage {
                storageCalls++
                MatchResultScreenshotStorageUploadResult.Uploaded("unused.png")
            },
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    cloudCalls++
                    return MatchResultScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) =
                    MatchResultScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Failed, result)
        assertEquals(0, storageCalls)
        assertEquals(0, cloudCalls)
        assertEquals(ScreenshotUploadStatus.FAILED.name, repository.asset.uploadStatus)
    }

    @Test
    fun lobbySameShaNewerRevisionWinsTheStorageCas() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = lobbyAsset(identity, preserver)
        val repository = CheckpointLobbyRepository(original)
        var storageCalls = 0
        var cloudAsset: MatchLobbyScreenshotAssetEntity? = null
        val result = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = object : MatchLobbyScreenshotStorageUploader {
                override suspend fun upload(
                    tournamentId: String?,
                    matchId: String?,
                    lobbyScreenshotIndex: Int?,
                    localFile: File?,
                ): MatchLobbyScreenshotStorageUploadResult {
                    storageCalls++
                    if (storageCalls == 1) {
                        repository.asset = original.copy(
                            cropLeft = 0.2,
                            cropRight = 0.8,
                            revision = original.revision + 1,
                        )
                    }
                    return MatchLobbyScreenshotStorageUploadResult.Uploaded("fresh-$storageCalls.png")
                }
            },
            cloudDataSource = object : MatchLobbyScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
                    cloudAsset = asset
                    return MatchLobbyScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
                    MatchLobbyScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Completed, result)
        assertEquals(2, storageCalls)
        assertEquals(0.2, cloudAsset?.cropLeft)
        assertEquals("fresh-2.png", repository.asset.storageObjectPath)
    }

    @Test
    fun resultSameShaNewerRevisionWinsTheStorageCas() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = resultAsset(identity, preserver)
        val repository = CheckpointResultRepository(original)
        var storageCalls = 0
        var cloudAsset: MatchResultScreenshotAssetEntity? = null
        val result = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage {
                storageCalls++
                if (storageCalls == 1) {
                    repository.asset = original.copy(
                        cropLeft = 0.2,
                        cropRight = 0.8,
                        revision = original.revision + 1,
                    )
                }
                MatchResultScreenshotStorageUploadResult.Uploaded("fresh-$storageCalls.png")
            },
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    cloudAsset = asset
                    return MatchResultScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) =
                    MatchResultScreenshotAssetCloudResult.Success
            },
        ).run(identity)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Completed, result)
        assertEquals(2, storageCalls)
        assertEquals(0.2, cloudAsset?.cropLeft)
        assertEquals("fresh-2.png", repository.asset.storageObjectPath)
    }

    @Test
    fun lobbyCloudWindowPublishesTheNewestConfirmedGenerationWithoutDeleting() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = lobbyAsset(identity, preserver)
        val repository = CheckpointLobbyRepository(original)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cloudAssets = mutableListOf<MatchLobbyScreenshotAssetEntity>()
        var deletes = 0
        val result = MatchLobbyScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = lobbyStorage(MatchLobbyScreenshotStorageUploadResult.Uploaded("path.png")),
            cloudDataSource = object : MatchLobbyScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
                    cloudAssets += asset
                    if (cloudAssets.size == 1) {
                        started.complete(Unit)
                        release.await()
                    }
                    return MatchLobbyScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetCloudResult {
                    deletes++
                    return MatchLobbyScreenshotAssetCloudResult.Success
                }
            },
        )
        val job = async { result.run(identity) }
        started.await()
        repository.asset = original.copy(
            cropLeft = 0.2,
            cropRight = 0.8,
            revision = original.revision + 2,
            storageBucket = "ocr-screenshots",
            storageObjectPath = "path.png",
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        )
        release.complete(Unit)

        assertEquals(MatchLobbyScreenshotUploadCheckpointResult.Completed, job.await())
        assertEquals(2, cloudAssets.size)
        assertEquals(0.2, cloudAssets.last().cropLeft)
        assertEquals(0, deletes)
    }

    @Test
    fun resultCloudWindowStopsWhenGenerationBecomesUnconfirmedWithoutDeleting() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = resultAsset(identity, preserver)
        val repository = CheckpointResultRepository(original)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var deletes = 0
        val result = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage(MatchResultScreenshotStorageUploadResult.Uploaded("path.png")),
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    started.complete(Unit)
                    release.await()
                    return MatchResultScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetCloudResult {
                    deletes++
                    return MatchResultScreenshotAssetCloudResult.Success
                }
            },
        )
        val job = async { result.run(identity) }
        started.await()
        repository.asset = original.copy(
            cropProfileId = null,
            cropLeft = null,
            cropTop = null,
            cropRight = null,
            cropBottom = null,
            revision = original.revision + 2,
        )
        release.complete(Unit)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Skipped, job.await())
        assertEquals(0, deletes)
        assertEquals(null, repository.asset.cropProfileId)
    }

    @Test
    fun resultCloudWindowPublishesTheNewestConfirmedGenerationWithoutDeleting() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        val original = resultAsset(identity, preserver)
        val repository = CheckpointResultRepository(original)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cloudAssets = mutableListOf<MatchResultScreenshotAssetEntity>()
        var deletes = 0
        val checkpoint = MatchResultScreenshotUploadCheckpoint(
            repository,
            preserver,
            clock,
            storageUploader = resultStorage(MatchResultScreenshotStorageUploadResult.Uploaded("path.png")),
            cloudDataSource = object : MatchResultScreenshotAssetCloudDataSource {
                override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
                    cloudAssets += asset
                    if (cloudAssets.size == 1) {
                        started.complete(Unit)
                        release.await()
                    }
                    return MatchResultScreenshotAssetCloudResult.Success
                }

                override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetCloudResult {
                    deletes++
                    return MatchResultScreenshotAssetCloudResult.Success
                }
            },
        )
        val job = async { checkpoint.run(identity) }
        started.await()
        repository.asset = original.copy(
            cropLeft = 0.2,
            cropRight = 0.8,
            revision = original.revision + 2,
            storageBucket = "ocr-screenshots",
            storageObjectPath = "path.png",
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        )
        release.complete(Unit)

        assertEquals(MatchResultScreenshotUploadCheckpointResult.Completed, job.await())
        assertEquals(2, cloudAssets.size)
        assertEquals(0.2, cloudAssets.last().cropLeft)
        assertEquals(0, deletes)
    }

    @Test(expected = CancellationException::class)
    fun lobbyStorageCancellationPropagates() = runTest {
        val identity = MatchLobbyScreenshotIdentity("tournament-1", "match-1", 1)
        val root = newRoot()
        val preserver = localPreserver(root)
        MatchLobbyScreenshotUploadCheckpoint(
            CheckpointLobbyRepository(lobbyAsset(identity, preserver)),
            preserver,
            clock,
            storageUploader = lobbyStorage(failure = CancellationException("cancelled")),
        ).run(identity)
    }

    @Test(expected = CancellationException::class)
    fun resultStorageCancellationPropagates() = runTest {
        val identity = MatchResultScreenshotIdentity(
            "tournament-1",
            "match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val root = newRoot()
        val preserver = localPreserver(root)
        MatchResultScreenshotUploadCheckpoint(
            CheckpointResultRepository(resultAsset(identity, preserver)),
            preserver,
            clock,
            storageUploader = resultStorage(failure = CancellationException("cancelled")),
        ).run(identity)
    }

    private fun resultStorage(
        result: MatchResultScreenshotStorageUploadResult = MatchResultScreenshotStorageUploadResult.Uploaded("path.png"),
        failure: Throwable? = null,
        action: (() -> MatchResultScreenshotStorageUploadResult)? = null,
    ) = object : MatchResultScreenshotStorageUploader {
        override suspend fun upload(tournamentId: String?, matchId: String?, role: MatchResultScreenshotRole?, localFile: File?): MatchResultScreenshotStorageUploadResult {
            failure?.let { throw it }
            return action?.invoke() ?: result
        }
    }

    private fun lobbyStorage(
        result: MatchLobbyScreenshotStorageUploadResult = MatchLobbyScreenshotStorageUploadResult.Uploaded("path.png"),
        failure: Throwable? = null,
        action: (() -> MatchLobbyScreenshotStorageUploadResult)? = null,
    ) = object : MatchLobbyScreenshotStorageUploader {
        override suspend fun upload(tournamentId: String?, matchId: String?, lobbyScreenshotIndex: Int?, localFile: File?): MatchLobbyScreenshotStorageUploadResult {
            failure?.let { throw it }
            return action?.invoke() ?: result
        }
    }

    private fun newRoot(): File = Files.createTempDirectory("rank-forge-screenshot-checkpoint").toFile().also(roots::add)

    private fun localPreserver(root: File) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { null },
        mimeTypeReader = ImageSourceMimeTypeReader { null },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun lobbyAsset(
        identity: MatchLobbyScreenshotIdentity,
        preserver: LocalImagePreserver,
        uploadStatus: String = ScreenshotUploadStatus.FAILED.name,
        sha: String = "sha-a",
    ): MatchLobbyScreenshotAssetEntity {
        val path = preserver.lobbyRelativePath(identity.tournamentId, identity.matchId, identity.lobbyScreenshotIndex, "png")
        writeLocalFile(preserver, path)
        return MatchLobbyScreenshotAssetEntity(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
            ownerUserId = "owner-1",
            localRelativePath = path,
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 100,
            originalHeight = 100,
            byteSize = 1,
            sha256 = sha,
            localStatus = ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = uploadStatus,
            uploadFailureCode = null,
            storageBucket = null,
            storageObjectPath = null,
            cropProfileId = "lobby",
            cropLeft = 0.0,
            cropTop = 0.0,
            cropRight = 1.0,
            cropBottom = 1.0,
            createdAt = 1,
            updatedAt = 1,
            preservedAt = 1,
            uploadedAt = null,
            revision = 1,
        )
    }

    private fun resultAsset(
        identity: MatchResultScreenshotIdentity,
        preserver: LocalImagePreserver,
    ): MatchResultScreenshotAssetEntity {
        val path = preserver.matchResultRelativePath(identity.tournamentId, identity.matchId, identity.role, "png")
        writeLocalFile(preserver, path)
        return MatchResultScreenshotAssetEntity(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            screenshotKind = identity.kind.name,
            screenshotRole = identity.role.name,
            ownerUserId = "owner-1",
            localRelativePath = path,
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 100,
            originalHeight = 100,
            byteSize = 1,
            sha256 = "sha-result",
            localStatus = ScreenshotLocalStatus.PRESERVED.name,
            uploadStatus = ScreenshotUploadStatus.FAILED.name,
            uploadFailureCode = null,
            storageBucket = null,
            storageObjectPath = null,
            cropProfileId = "match-result",
            cropLeft = 0.0,
            cropTop = 0.0,
            cropRight = 1.0,
            cropBottom = 1.0,
            createdAt = 1,
            updatedAt = 1,
            preservedAt = 1,
            uploadedAt = null,
            revision = 1,
        )
    }

    private fun writeLocalFile(preserver: LocalImagePreserver, path: String) {
        val file = preserver.resolveRelativePath(path)!!
        file.parentFile!!.mkdirs()
        file.writeText("image")
    }
}

private class CheckpointLobbyRepository(
    var asset: MatchLobbyScreenshotAssetEntity,
) : MatchLobbyScreenshotAssetRepository {
    override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(listOf(asset))
    override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(asset)
    override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity? = asset
    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(listOf(asset))
    override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = null
    override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult {
        this.asset = asset
        return MatchLobbyScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadSuccessIfFingerprintMatches(identity: MatchLobbyScreenshotIdentity, sha256: String, storageBucket: String, storageObjectPath: String, uploadedAt: Long, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256) return false
        return saveOrReplace(asset.copy(storageBucket = storageBucket, storageObjectPath = storageObjectPath, uploadStatus = ScreenshotUploadStatus.UPLOADED.name, uploadFailureCode = null, uploadedAt = uploadedAt, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchLobbyScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadFailureIfFingerprintMatches(identity: MatchLobbyScreenshotIdentity, sha256: String, failureCode: String, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256) return false
        return saveOrReplace(asset.copy(uploadStatus = ScreenshotUploadStatus.FAILED.name, uploadFailureCode = failureCode, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchLobbyScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadSuccessIfGenerationMatches(identity: MatchLobbyScreenshotIdentity, sha256: String, expectedRevision: Long, storageBucket: String, storageObjectPath: String, uploadedAt: Long, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256 || asset.revision != expectedRevision) return false
        return saveOrReplace(asset.copy(storageBucket = storageBucket, storageObjectPath = storageObjectPath, uploadStatus = ScreenshotUploadStatus.UPLOADED.name, uploadFailureCode = null, uploadedAt = uploadedAt, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchLobbyScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadFailureIfGenerationMatches(identity: MatchLobbyScreenshotIdentity, sha256: String, expectedRevision: Long, failureCode: String, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256 || asset.revision != expectedRevision) return false
        return saveOrReplace(asset.copy(uploadStatus = ScreenshotUploadStatus.FAILED.name, uploadFailureCode = failureCode, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchLobbyScreenshotAssetSaveResult.Saved
    }
    override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
    override suspend fun deleteByMatchId(matchId: String) = Unit
    override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
    override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
}

private class CheckpointResultRepository(
    var asset: MatchResultScreenshotAssetEntity,
) : MatchResultScreenshotAssetRepository {
    override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(listOf(asset))
    override fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?> = flowOf(asset)
    override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? = asset
    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(listOf(asset))
    override suspend fun findDuplicateFingerprint(identity: MatchResultScreenshotIdentity, sha256: String) = null
    override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult {
        this.asset = asset
        return MatchResultScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadSuccessIfFingerprintMatches(identity: MatchResultScreenshotIdentity, sha256: String, storageBucket: String, storageObjectPath: String, uploadedAt: Long, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256) return false
        return saveOrReplace(asset.copy(storageBucket = storageBucket, storageObjectPath = storageObjectPath, uploadStatus = ScreenshotUploadStatus.UPLOADED.name, uploadFailureCode = null, uploadedAt = uploadedAt, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchResultScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadFailureIfFingerprintMatches(identity: MatchResultScreenshotIdentity, sha256: String, failureCode: String, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256) return false
        return saveOrReplace(asset.copy(uploadStatus = ScreenshotUploadStatus.FAILED.name, uploadFailureCode = failureCode, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchResultScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadSuccessIfGenerationMatches(identity: MatchResultScreenshotIdentity, sha256: String, expectedRevision: Long, storageBucket: String, storageObjectPath: String, uploadedAt: Long, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256 || asset.revision != expectedRevision) return false
        return saveOrReplace(asset.copy(storageBucket = storageBucket, storageObjectPath = storageObjectPath, uploadStatus = ScreenshotUploadStatus.UPLOADED.name, uploadFailureCode = null, uploadedAt = uploadedAt, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchResultScreenshotAssetSaveResult.Saved
    }
    override suspend fun updateUploadFailureIfGenerationMatches(identity: MatchResultScreenshotIdentity, sha256: String, expectedRevision: Long, failureCode: String, updatedAt: Long): Boolean {
        if (asset.identityOrNull() != identity || asset.sha256 != sha256 || asset.revision != expectedRevision) return false
        return saveOrReplace(asset.copy(uploadStatus = ScreenshotUploadStatus.FAILED.name, uploadFailureCode = failureCode, updatedAt = updatedAt, revision = asset.revision + 1L)) is MatchResultScreenshotAssetSaveResult.Saved
    }
    override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun persistConfirmedCrop(identity: MatchResultScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchResultScreenshotCropSaveResult.Saved
    override suspend fun clearConfirmedCrop(identity: MatchResultScreenshotIdentity, updatedAt: Long) = MatchResultScreenshotCropSaveResult.Saved
    override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit
    override suspend fun deleteByMatchId(matchId: String) = Unit
}
