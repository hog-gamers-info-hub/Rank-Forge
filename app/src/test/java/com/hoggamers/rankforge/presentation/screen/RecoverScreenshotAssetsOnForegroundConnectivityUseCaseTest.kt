package com.hoggamers.rankforge.presentation.screen

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
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverScreenshotAssetsOnForegroundConnectivityUseCaseTest {
    @Test
    fun recoversConfirmedLobbyBeforeConfirmedResultForExistingLocalMatches() = runTest {
        val tournament = tournament()
        val match = match()
        val lobbyIdentity = MatchLobbyScreenshotIdentity(tournament.id, match.id, 1)
        val resultIdentity = MatchResultScreenshotIdentity(
            tournamentId = tournament.id,
            matchId = match.id,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        )
        val calls = mutableListOf<String>()
        val useCase = useCase(
            tournaments = listOf(tournament),
            matches = listOf(match),
            lobby = listOf(lobbyAsset(lobbyIdentity)),
            result = listOf(resultAsset(resultIdentity)),
            lobbyAction = recordingLobbyAction { calls += "lobby:${it.tournamentId}:${it.matchId}:${it.lobbyScreenshotIndex}" },
            resultAction = recordingResultAction { calls += "result:${it.tournamentId}:${it.matchId}:${it.role}" },
        )

        useCase.recoverAfterParentQueue()

        assertEquals(
            listOf("lobby:tournament-1:match-1:1", "result:tournament-1:match-1:MATCH_RESULT_UPPER"),
            calls,
        )
    }

    @Test
    fun skipsUnconfirmedWrongOwnerAndUnknownMatchAssets() = runTest {
        val tournament = tournament()
        val match = match()
        val validLobby = lobbyAsset(MatchLobbyScreenshotIdentity(tournament.id, match.id, 1))
        val validResult = resultAsset(
            MatchResultScreenshotIdentity(
                tournament.id,
                match.id,
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            ),
        )
        val calls = mutableListOf<String>()
        val useCase = useCase(
            tournaments = listOf(tournament),
            matches = listOf(match),
            lobby = listOf(
                validLobby,
                validLobby.copy(cropProfileId = null),
                validLobby.copy(ownerUserId = "other-user"),
                validLobby.copy(matchId = "missing-match"),
            ),
            result = listOf(validResult),
            lobbyAction = recordingLobbyAction { calls += "lobby" },
            resultAction = recordingResultAction { calls += "result" },
        )

        useCase.recoverAfterParentQueue()

        assertEquals(listOf("lobby", "result"), calls)
    }

    @Test
    fun ordinaryAssetRetryFailureDoesNotBlockLaterAssets() = runTest {
        val tournament = tournament()
        val match = match()
        val lobby = listOf(
            lobbyAsset(MatchLobbyScreenshotIdentity(tournament.id, match.id, 1)),
            lobbyAsset(MatchLobbyScreenshotIdentity(tournament.id, match.id, 2)),
        )
        var calls = 0
        val useCase = useCase(
            tournaments = listOf(tournament),
            matches = listOf(match),
            lobby = lobby,
            result = emptyList(),
            lobbyAction = object : MatchLobbyScreenshotUploadCheckpointAction {
                override suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult {
                    calls += 1
                    if (calls == 1) error("metadata unavailable")
                    return MatchLobbyScreenshotUploadCheckpointResult.Completed
                }
                override suspend fun run(identity: MatchLobbyScreenshotIdentity, expectedOwnerUserId: String): MatchLobbyScreenshotUploadCheckpointResult {
                    calls += 1
                    if (calls == 1) error("metadata unavailable")
                    return MatchLobbyScreenshotUploadCheckpointResult.Completed
                }
            },
        )

        useCase.recoverAfterParentQueue()

        assertEquals(2, calls)
    }

    @Test(expected = CancellationException::class)
    fun checkpointCancellationPropagates() = runTest {
        val tournament = tournament()
        val match = match()
        useCase(
            tournaments = listOf(tournament),
            matches = listOf(match),
            lobby = listOf(lobbyAsset(MatchLobbyScreenshotIdentity(tournament.id, match.id, 1))),
            result = emptyList(),
            lobbyAction = object : MatchLobbyScreenshotUploadCheckpointAction {
                override suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult = throw CancellationException("cancelled")
                override suspend fun run(identity: MatchLobbyScreenshotIdentity, expectedOwnerUserId: String): MatchLobbyScreenshotUploadCheckpointResult = throw CancellationException("cancelled")
            },
        ).recoverAfterParentQueue()
    }

    @Test
    fun ownerSwitchStopsRemainingForegroundRecoveryItems() = runTest {
        val tournament = tournament()
        val match = match()
        val owner = MutableRecoveryOwnerProvider("owner-1")
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val calls = mutableListOf<MatchLobbyScreenshotIdentity>()
        val useCase = useCase(
            tournaments = listOf(tournament),
            matches = listOf(match),
            lobby = listOf(
                lobbyAsset(MatchLobbyScreenshotIdentity(tournament.id, match.id, 1)),
                lobbyAsset(MatchLobbyScreenshotIdentity(tournament.id, match.id, 2)),
            ),
            result = emptyList(),
            ownerProvider = owner,
            lobbyAction = object : MatchLobbyScreenshotUploadCheckpointAction {
                override suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult =
                    MatchLobbyScreenshotUploadCheckpointResult.Skipped

                override suspend fun run(identity: MatchLobbyScreenshotIdentity, expectedOwnerUserId: String): MatchLobbyScreenshotUploadCheckpointResult {
                    calls += identity
                    started.complete(Unit)
                    finish.await()
                    return MatchLobbyScreenshotUploadCheckpointResult.Completed
                }
            },
        )

        val pending = async { useCase.recoverAfterParentQueue("owner-1") }
        started.await()
        owner.ownerId = "owner-2"
        finish.complete(Unit)
        pending.await()

        assertEquals(1, calls.size)
        assertEquals(1, calls.single().lobbyScreenshotIndex)
    }

    private fun useCase(
        tournaments: List<Tournament>,
        matches: List<Match>,
        lobby: List<MatchLobbyScreenshotAssetEntity>,
        result: List<MatchResultScreenshotAssetEntity>,
        ownerProvider: ScreenshotOwnerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String = "owner-1"
        },
        lobbyAction: MatchLobbyScreenshotUploadCheckpointAction = MatchLobbyScreenshotUploadCheckpointAction {
            MatchLobbyScreenshotUploadCheckpointResult.Skipped
        },
        resultAction: MatchResultScreenshotUploadCheckpointAction = MatchResultScreenshotUploadCheckpointAction {
            MatchResultScreenshotUploadCheckpointResult.Skipped
        },
    ) = RecoverScreenshotAssetsOnForegroundConnectivityUseCase(
        observeTournaments = ObserveTournamentsUseCase(FakeTournamentRepository(tournaments, matches)),
        observeMatches = ObserveMatchesUseCase(FakeTournamentRepository(tournaments, matches)),
        ownerProvider = ownerProvider,
        lobbyAssets = FakeLobbyRepository(lobby),
        resultAssets = FakeResultRepository(result),
        lobbyCheckpoint = lobbyAction,
        resultCheckpoint = resultAction,
    )

    private fun recordingLobbyAction(onRun: (MatchLobbyScreenshotIdentity) -> Unit) =
        object : MatchLobbyScreenshotUploadCheckpointAction {
            override suspend fun run(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotUploadCheckpointResult {
                onRun(identity)
                return MatchLobbyScreenshotUploadCheckpointResult.Completed
            }
            override suspend fun run(identity: MatchLobbyScreenshotIdentity, expectedOwnerUserId: String): MatchLobbyScreenshotUploadCheckpointResult {
                onRun(identity)
                return MatchLobbyScreenshotUploadCheckpointResult.Completed
            }
        }

    private fun recordingResultAction(onRun: (MatchResultScreenshotIdentity) -> Unit) =
        object : MatchResultScreenshotUploadCheckpointAction {
            override suspend fun run(identity: MatchResultScreenshotIdentity): MatchResultScreenshotUploadCheckpointResult {
                onRun(identity)
                return MatchResultScreenshotUploadCheckpointResult.Completed
            }
            override suspend fun run(identity: MatchResultScreenshotIdentity, expectedOwnerUserId: String): MatchResultScreenshotUploadCheckpointResult {
                onRun(identity)
                return MatchResultScreenshotUploadCheckpointResult.Completed
            }
        }

    private fun tournament() = Tournament(
        id = "tournament-1",
        name = "Tournament",
        date = LocalDate.of(2026, 8, 14),
        organizerName = "Organizer",
        organizerContactNumber = "contact",
        status = TournamentStatus.DRAFT,
        ownerUserId = "owner-1",
    )

    private fun match() = Match(
        id = "match-1",
        tournamentId = "tournament-1",
        matchNumber = 1,
        date = LocalDate.of(2026, 8, 14),
        mapName = "Map",
        status = MatchStatus.DRAFT,
    )

    private fun lobbyAsset(identity: MatchLobbyScreenshotIdentity) = MatchLobbyScreenshotAssetEntity(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        lobbyScreenshotIndex = identity.lobbyScreenshotIndex,
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/${identity.matchId}/lobby/${identity.lobbyScreenshotIndex}/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 1,
        sha256 = "sha-${identity.lobbyScreenshotIndex}",
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.FAILED.name,
        uploadFailureCode = "NETWORK",
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

    private fun resultAsset(identity: MatchResultScreenshotIdentity) = MatchResultScreenshotAssetEntity(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotKind = identity.kind.name,
        screenshotRole = identity.role.name,
        ownerUserId = "owner-1",
        localRelativePath = "screenshots/${identity.matchId}/result/${identity.role.name}/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 1,
        sha256 = "sha-${identity.role.name}",
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.FAILED.name,
        uploadFailureCode = "NETWORK",
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

private class MutableRecoveryOwnerProvider(var ownerId: String?) : ScreenshotOwnerProvider {
    override suspend fun currentOwnerUserId(): String? = ownerId
}

private class FakeTournamentRepository(
    private val tournaments: List<Tournament>,
    private val matches: List<Match>,
) : TournamentRepository {
    override suspend fun create(tournament: Tournament) = Unit
    override fun observeAll(): Flow<List<Tournament>> = flowOf(tournaments)
    override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(tournaments.find { it.id == tournamentId })
    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())
    override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
    override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
    override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
    override suspend fun confirmTournament(tournamentId: String): Boolean = true
    override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> = flowOf(matches.filter { it.tournamentId == tournamentId })
}

private class FakeLobbyRepository(
    private val assets: List<MatchLobbyScreenshotAssetEntity>,
) : MatchLobbyScreenshotAssetRepository {
    override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(assets.filter { it.matchId == matchId })
    override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(assets.find { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex })
    override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity? = assets.find { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(assets.filter { it.tournamentId == tournamentId })
    override fun observeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
        flowOf(assets.filter { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId })
    override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String): MatchLobbyScreenshotAssetEntity? = null
    override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult = MatchLobbyScreenshotAssetSaveResult.Saved
    override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
    override suspend fun deleteByMatchId(matchId: String) = Unit
    override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.Saved
    override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.Saved
}

private class FakeResultRepository(
    private val assets: List<MatchResultScreenshotAssetEntity>,
) : MatchResultScreenshotAssetRepository {
    override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(assets.filter { it.matchId == matchId })
    override fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?> = flowOf(assets.find { it.matchId == identity.matchId && it.screenshotRole == identity.role.name })
    override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? = assets.find { it.matchId == identity.matchId && it.screenshotRole == identity.role.name }
    override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(assets.filter { it.tournamentId == tournamentId })
    override fun observeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
        flowOf(assets.filter { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId })
    override suspend fun findDuplicateFingerprint(identity: MatchResultScreenshotIdentity, sha256: String): MatchResultScreenshotAssetEntity? = null
    override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult = MatchResultScreenshotAssetSaveResult.Saved
    override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
    override suspend fun persistConfirmedCrop(identity: MatchResultScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved
    override suspend fun clearConfirmedCrop(identity: MatchResultScreenshotIdentity, updatedAt: Long): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved
    override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit
    override suspend fun deleteByMatchId(matchId: String) = Unit
}
