package com.hoggamers.rankforge.data.tournament

import androidx.room.withTransaction
import com.hoggamers.rankforge.data.local.MatchOcrCorrectionSnapshotEntity
import com.hoggamers.rankforge.data.local.MatchOcrEvidenceEntity
import com.hoggamers.rankforge.data.local.MatchOcrRowEvidenceEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.local.RankForgeStateEntity
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionFailure
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrCorrectionSnapshot
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrRowEvidence
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.ConfirmedRosterReplacementCandidate
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterRepositoryResult
import com.hoggamers.rankforge.domain.tournament.RestoredRosterPlayer
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionRepositoryResult
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull
import com.hoggamers.rankforge.domain.tournament.isValidCorrectionSnapshot
import com.hoggamers.rankforge.domain.tournament.correctedMatchPlacements
import com.hoggamers.rankforge.domain.tournament.correctedMatchKills
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentRestorationLocalRepository
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchRestorationLocalRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TournamentSummary
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.LocalRevisionState
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.detectDivergence
import java.time.LocalDate
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.hoggamers.rankforge.presentation.screen.LocalImageCleanupResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import java.io.File

@Singleton
class RoomTournamentRepository @Inject constructor(
    private val database: RankForgeDatabase,
    private val localImagePreserver: LocalImagePreserver,
    private val deletionIntentRepository: DeletionIntentRepository,
    private val clock: Clock,
) : TournamentRepository, TournamentRestorationLocalRepository, MatchRestorationLocalRepository,
    LocalDeletionRepository {
    constructor(database: RankForgeDatabase) : this(
        database = database,
        localImagePreserver = LocalImagePreserver(
            appPrivateRoot = File(System.getProperty("java.io.tmpdir"), "rank-forge-repository-default"),
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { null },
        ),
        deletionIntentRepository = NoOpDeletionIntentRepository,
        clock = Clock.systemUTC(),
    )

    constructor(
        database: RankForgeDatabase,
        localImagePreserver: LocalImagePreserver,
    ) : this(
        database = database,
        localImagePreserver = localImagePreserver,
        deletionIntentRepository = NoOpDeletionIntentRepository,
        clock = Clock.systemUTC(),
    )

    constructor(
        database: RankForgeDatabase,
        localImagePreserver: LocalImagePreserver,
        deletionIntentRepository: DeletionIntentRepository,
    ) : this(
        database = database,
        localImagePreserver = localImagePreserver,
        deletionIntentRepository = deletionIntentRepository,
        clock = Clock.systemUTC(),
    )

    constructor(database: RankForgeDatabase, clock: Clock) : this(
        database = database,
        localImagePreserver = LocalImagePreserver(
            appPrivateRoot = File(System.getProperty("java.io.tmpdir"), "rank-forge-repository-default"),
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { null },
        ),
        deletionIntentRepository = NoOpDeletionIntentRepository,
        clock = clock,
    )

    private val state = MutableStateFlow(RepositoryState())
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch {
            try {
                val deletionIntents = runCatching { deletionIntentRepository.readAll() }
                    .getOrDefault(emptyList())
                val restored = runCatching { database.stateDao().readPayload() }
                    .getOrNull()
                    ?.let { payload ->
                        runCatching { json.decodeFromString<PersistedState>(payload).toRepositoryState() }
                            .getOrNull()
                    }
                    ?: RepositoryState()
                val restoredWithoutActiveDeletionTargets =
                    restored.withoutActiveDeletionTargets(deletionIntents)
                val synchronizedState = database.withTransaction {
                    val existingIds = database.tournamentDao().observeAll().first().map { it.id }.toSet()
                    restoredWithoutActiveDeletionTargets.tournaments
                        .filter { it.id !in existingIds }
                        .forEach { tournament ->
                            database.tournamentDao().upsert(
                                tournament.toEntity(
                                    creationOrder = database.tournamentDao().nextCreationOrder(),
                                ),
                            )
                        }

                    val normalizedTournaments = database.tournamentDao().observeAll().first().map { it.toDomain() }
                    normalizedTournaments.forEach { tournament ->
                        backfillSlots(tournament.id, restoredWithoutActiveDeletionTargets)
                        backfillRoster(tournament.id, restoredWithoutActiveDeletionTargets)
                    }

                    val normalizedTournamentIds = normalizedTournaments.map { it.id }.toSet()
                    val existingMatches = database.matchDao().observeAll().first()
                        .filter { it.tournamentId in normalizedTournamentIds }
                    val existingMatchIds = existingMatches
                        .map { it.id }
                        .toSet()
                    restoredWithoutActiveDeletionTargets.matches.values.flatten()
                        .filter { it.id !in existingMatchIds }
                        .filter { match -> normalizedTournaments.any { it.id == match.tournamentId } }
                        .forEach { database.matchDao().upsert(it.toEntity()) }

                    val normalizedMatchEntities = database.matchDao().observeAll().first()
                        .filter { it.tournamentId in normalizedTournamentIds }
                    normalizedMatchEntities.forEach { match ->
                        val legacyMatch = restoredWithoutActiveDeletionTargets.matches.values.flatten()
                            .firstOrNull { it.id == match.id }
                        backfillMatchPlacements(match.id, legacyMatch)
                        backfillMatchKills(match.id, legacyMatch)
                        backfillMatchParticipantResults(match.id, legacyMatch)
                        backfillMatchCorrections(match.id, legacyMatch)
                        backfillMatchDraftValues(match.id, restoredWithoutActiveDeletionTargets)
                    }

                    val normalizedSlots = normalizedTournaments.associate { tournament ->
                        tournament.id to database.teamSlotDao()
                            .observeByTournamentId(tournament.id)
                            .first()
                            .map { it.toDomain() }
                    }
                    val normalizedRosters = normalizedTournaments
                        .flatMap { tournament ->
                            TeamSlot.SLOT_NUMBERS.mapNotNull { slotNumber ->
                                val players = database.rosterPlayerDao()
                                    .observeByTournamentAndSlot(tournament.id, slotNumber)
                                    .first()
                                    .map { it.toDomain() }
                                players.takeIf { it.isNotEmpty() }
                                    ?.let { RosterKey(tournament.id, slotNumber) to it }
                            }
                        }
                        .toMap()
                    val normalizedMatches = normalizedMatchEntities.map { match ->
                        match.toDomain(
                            placements = database.matchPlacementDao().observeByMatchId(match.id)
                                .first()
                                .map { it.toDomain() },
                            kills = database.matchKillDao().observeByMatchId(match.id)
                                .first()
                                .map { it.toDomain() },
                            participantResults = database.matchParticipantResultDao()
                                .observeByMatchId(match.id)
                                .first()
                                .map { it.toDomain() },
                            correctionHistory = database.matchCorrectionDao().observeByMatchId(match.id)
                                .first()
                                .map { it.toDomain(json) },
                        )
                    }
                    val normalizedDraftValues = normalizedMatchEntities.flatMap { match ->
                        val key = DraftKey(match.tournamentId, match.id)
                        database.matchDraftValueDao().observeByMatchId(match.id)
                            .first()
                            .map { value -> key to (value.teamSlotNumber to value.toDomain()) }
                    }.groupBy(
                        keySelector = { it.first },
                        valueTransform = { it.second },
                    ).mapValues { (_, values) -> values.toMap() }
                    val synchronized = restoredWithoutActiveDeletionTargets.copy(
                        tournaments = normalizedTournaments,
                        slots = normalizedSlots,
                        rosters = normalizedRosters,
                        matches = normalizedMatches.groupBy { it.tournamentId },
                        draftValues = normalizedDraftValues,
                    )
                    val synchronizedWithoutActiveDeletionTargets =
                        synchronized.withoutActiveDeletionTargets(deletionIntents)
                    if (synchronizedWithoutActiveDeletionTargets != restored) {
                        database.stateDao().save(
                            RankForgeStateEntity(
                                payload = json.encodeToString(
                                    synchronizedWithoutActiveDeletionTargets.toPersistedState(),
                                ),
                            ),
                        )
                    }
                    synchronizedWithoutActiveDeletionTargets
                }
                state.value = synchronizedState
            } finally {
                ready.complete(Unit)
            }
        }
        scope.launch {
            ready.await()
            recoverPendingLocalCleanup()
        }
    }

    override fun observeAll(): Flow<List<Tournament>> = flow {
        ready.await()
        emitAll(database.tournamentDao().observeAll().map { tournaments ->
            tournaments.map { it.toDomain() }
        })
    }

    override fun observeSummaries(): Flow<List<TournamentSummary>> = flow {
        ready.await()
        emitAll(database.tournamentDao().observeSummaries().map { summaries ->
            summaries.map { it.toDomain() }
        })
    }

    override fun observeById(tournamentId: String): Flow<Tournament?> = flow {
        ready.await()
        emitAll(database.tournamentDao().observeById(tournamentId).map { it?.toDomain() })
    }

    override suspend fun readLocalRevisionState(tournamentId: String): LocalRevisionState {
        awaitState()
        return database.syncRevisionDao().readByTournamentId(tournamentId)?.toDomain()
            ?: LocalRevisionState.Missing
    }

    override suspend fun confirmCloudRevision(tournamentId: String, cloudRevision: Int) {
        require(cloudRevision > 0)
        database.syncRevisionDao().upsert(
            com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                tournamentId = tournamentId,
                localRevision = cloudRevision,
                baseCloudRevision = cloudRevision,
            ),
        )
    }

    override suspend fun establishCloudBaseline(tournamentId: String, cloudRevision: Int) {
        require(cloudRevision > 0)
        awaitState()
        val existing = database.syncRevisionDao().readByTournamentId(tournamentId)
        database.syncRevisionDao().upsert(
            com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                tournamentId = tournamentId,
                localRevision = existing?.localRevision ?: 1,
                baseCloudRevision = cloudRevision,
            ),
        )
    }

    override suspend fun rebaseCloudRevisionForConflictResolution(
        tournamentId: String,
        cloudRevision: Int,
    ) {
        require(cloudRevision > 0)
        awaitState()
        val existing = database.syncRevisionDao().readByTournamentId(tournamentId)
        database.syncRevisionDao().upsert(
            com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                tournamentId = tournamentId,
                localRevision = existing?.localRevision ?: 1,
                baseCloudRevision = cloudRevision,
            ),
        )
    }

    override suspend fun detectTournamentDivergence(
        tournamentId: String,
        cloudRevision: CloudRevision,
    ): RevisionConflict? = readLocalRevisionState(tournamentId).detectDivergence(cloudRevision)

    override suspend fun detectMatchDivergence(
        tournamentId: String,
        cloudRevision: CloudRevision,
    ): RevisionConflict? = readLocalRevisionState(tournamentId).detectDivergence(cloudRevision)

    override suspend fun create(tournament: Tournament) {
        awaitState()
        writeMutex.withLock {
            val current = state.value
            database.withTransaction {
                val existing = database.tournamentDao().observeById(tournament.id).first()
                if (existing != null) {
                    val existingSlots = database.teamSlotDao().observeByTournamentId(tournament.id).first()
                    val missingSlots = TeamSlot.SLOT_NUMBERS
                        .filter { slotNumber -> existingSlots.none { it.slotNumber == slotNumber } }
                        .map { slotNumber -> TeamSlot.create(tournament.id, slotNumber).toEntity() }
                    if (missingSlots.isNotEmpty()) {
                        database.teamSlotDao().upsertAll(missingSlots)
                    }
                    val normalizedSlots = database.teamSlotDao()
                        .observeByTournamentId(tournament.id)
                        .first()
                        .map { it.toDomain() }
                    val next = current.withTournamentMirror(existing.toDomain(), normalizedSlots)
                    if (next != current) {
                        saveLegacyState(next)
                        state.value = next
                    }
                    return@withTransaction
                }

                database.tournamentDao().upsert(
                    tournament.toEntity(
                        creationOrder = database.tournamentDao().nextCreationOrder(),
                        lastUpdatedEpochMillis = clock.millis(),
                    ),
                )
                val normalizedSlots = TeamSlot.fixedSlotsForTournament(tournament.id)
                database.teamSlotDao().upsertAll(normalizedSlots.map { it.toEntity() })
                database.syncRevisionDao().upsert(
                    com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                        tournamentId = tournament.id,
                        localRevision = 1,
                        baseCloudRevision = null,
                    ),
                )
                val next = current.withTournamentMirror(tournament, normalizedSlots)
                saveLegacyState(next)
                state.value = next
            }
        }
    }

    override suspend fun deleteMatchLocally(matchId: String): LocalDeletionResult {
        awaitState()
        return writeMutex.withLock {
            val match = database.matchDao().observeById(matchId).first()
                ?: return@withLock LocalDeletionResult.NotFound
            val referencedPaths = buildList {
                database.screenshotMetadataDao().readByMatchId(matchId)?.localRelativePath?.let(::add)
                database.matchResultScreenshotAssetDao().observeByMatchId(matchId).first()
                    .forEach { add(it.localRelativePath) }
                database.matchLobbyScreenshotAssetDao().observeByMatchId(matchId).first()
                    .forEach { add(it.localRelativePath) }
            }
            if (localImagePreserver.cleanupMatchAssets(
                    tournamentId = match.tournamentId,
                    matchId = matchId,
                    referencedRelativePaths = referencedPaths,
                ) != LocalImageCleanupResult.Cleaned
            ) {
                return@withLock LocalDeletionResult.FileCleanupFailed
            }

            val next = state.value.copy(
                matches = state.value.matches.mapValues { (_, matches) ->
                    matches.filterNot { it.id == matchId }
                }.filterValues { it.isNotEmpty() },
                draftValues = state.value.draftValues.filterKeys { it.matchId != matchId },
            )
            database.withTransaction {
                database.matchDao().deleteById(matchId)
                // The queue stores tournamentId only, so purge the full tournament scope.
                database.syncQueueDao().deleteByTournamentId(match.tournamentId)
                touchTournament(match.tournamentId)
                saveLegacyState(next)
            }
            state.value = next
            LocalDeletionResult.Deleted
        }
    }

    private suspend fun recoverPendingLocalCleanup() {
        deletionIntentRepository.readPendingLocalCleanup().forEach { intent ->
            val result = when (intent.targetType) {
                DeletionTargetType.MATCH -> deleteMatchLocally(intent.targetId)
                DeletionTargetType.TOURNAMENT -> deleteTournamentLocally(intent.targetId)
            }
            if (result == LocalDeletionResult.Deleted || result == LocalDeletionResult.NotFound) {
                runCatching {
                    deletionIntentRepository.clear(intent.targetType, intent.targetId)
                }
            }
        }
    }

    override suspend fun deleteTournamentLocally(tournamentId: String): LocalDeletionResult {
        awaitState()
        return writeMutex.withLock {
            database.tournamentDao().observeById(tournamentId).first()
                ?: return@withLock LocalDeletionResult.NotFound
            val matches = database.matchDao().observeByTournamentId(tournamentId).first()
            val templates = database.tournamentLobbyTemplateAssetDao().readByTournamentId(tournamentId)
            val referencedPaths = buildList {
                database.rosterScreenshotMetadataDao().readByTournamentId(tournamentId)
                    .forEach { add(it.localRelativePath) }
                database.screenshotMetadataDao().observeByTournamentId(tournamentId).first()
                    .forEach { add(it.localRelativePath) }
                database.matchResultScreenshotAssetDao().readByTournamentId(tournamentId)
                    .forEach { add(it.localRelativePath) }
                database.matchLobbyScreenshotAssetDao().readByTournamentId(tournamentId)
                    .forEach { add(it.localRelativePath) }
                templates.forEach { add(it.localRelativePath) }
            }
            val templateGenerations = templates.mapNotNull { asset ->
                localImagePreserver.lobbyTemplateGenerationFromRelativePath(
                    tournamentId = tournamentId,
                    relativePath = asset.localRelativePath,
                )
            }.toSet()
            if (localImagePreserver.cleanupTournamentAssets(
                    tournamentId = tournamentId,
                    matchIds = matches.map { it.id },
                    templateGenerations = templateGenerations,
                    referencedRelativePaths = referencedPaths,
                ) != LocalImageCleanupResult.Cleaned
            ) {
                return@withLock LocalDeletionResult.FileCleanupFailed
            }

            val next = state.value.copy(
                tournaments = state.value.tournaments.filterNot { it.id == tournamentId },
                slots = state.value.slots - tournamentId,
                rosters = state.value.rosters.filterKeys { it.tournamentId != tournamentId },
                matches = state.value.matches - tournamentId,
                draftValues = state.value.draftValues.filterKeys { it.tournamentId != tournamentId },
            )
            database.withTransaction {
                database.syncRevisionDao().deleteByTournamentId(tournamentId)
                database.syncQueueDao().deleteByTournamentId(tournamentId)
                database.tournamentDao().deleteById(tournamentId)
                saveLegacyState(next)
            }
            state.value = next
            LocalDeletionResult.Deleted
        }
    }

    override suspend fun restore(snapshot: TournamentCloudRestorationSnapshot) {
        require(snapshot.slots.map { it.slotNumber } == TeamSlot.SLOT_NUMBERS.toList())
        require(snapshot.slots.all { it.tournamentId == snapshot.tournament.id })
        require(snapshot.players.all {
            it.tournamentId == snapshot.tournament.id &&
                it.slotNumber in TeamSlot.SLOT_NUMBERS &&
                it.rosterPosition in 1..RosterPlayer.MAX_PLAYERS
        })
        require(
            snapshot.players
                .groupBy { it.slotNumber }
                .values
                .all { players -> players.map { it.rosterPosition }.distinct().size == players.size },
        )
        awaitState()
        writeMutex.withLock {
            val current = state.value
            val next = current.copy(
                tournaments = current.tournaments.map { tournament ->
                    if (tournament.id == snapshot.tournament.id) snapshot.tournament else tournament
                }.let { tournaments ->
                    if (tournaments.any { it.id == snapshot.tournament.id }) {
                        tournaments
                    } else {
                        tournaments + snapshot.tournament
                    }
                },
                slots = current.slots + (snapshot.tournament.id to snapshot.slots),
                rosters = current.rosters
                    .filterKeys { it.tournamentId != snapshot.tournament.id }
                    .plus(
                        snapshot.players
                            .groupBy { RosterKey(it.tournamentId, it.slotNumber) }
                            .mapValues { (_, players) ->
                                players.sortedBy { it.rosterPosition }.map { player ->
                                    RosterPlayer(
                                        tournamentId = player.tournamentId,
                                        slotNumber = player.slotNumber,
                                        displayName = player.displayName,
                                    )
                                }
                            },
                    ),
            )
            database.withTransaction {
                val existingTournament = database.tournamentDao()
                    .observeById(snapshot.tournament.id)
                    .first()
                database.tournamentDao().upsert(
                    snapshot.tournament.toEntity(
                        creationOrder = existingTournament?.creationOrder
                            ?: database.tournamentDao().nextCreationOrder(),
                        lastUpdatedEpochMillis = existingTournament?.lastUpdatedEpochMillis,
                    ),
                )
                database.teamSlotDao().deleteByTournamentId(snapshot.tournament.id)
                database.teamSlotDao().upsertAll(snapshot.slots.map { it.toEntity() })
                database.rosterPlayerDao().upsertAll(snapshot.players.map { it.toEntity() })
                snapshot.cloudRevision?.let { revision ->
                    database.syncRevisionDao().upsert(
                        com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                            snapshot.tournament.id,
                            revision.value,
                            revision.value,
                        ),
                    )
                }
                saveLegacyState(next)
            }
            state.value = next
        }
    }

    override suspend fun replaceMatches(snapshot: MatchCloudRestorationSnapshot) {
        require(snapshot.matches.all { it.tournamentId == snapshot.tournamentId })
        require(snapshot.matches.map { it.matchNumber }.distinct().size == snapshot.matches.size)
        awaitState()
        writeMutex.withLock {
            val next = state.value.copy(matches = state.value.matches + (snapshot.tournamentId to snapshot.matches))
            database.withTransaction {
                database.matchDao().deleteByTournamentId(snapshot.tournamentId)
                snapshot.matches.forEach { match ->
                    database.matchDao().upsert(match.toEntity())
                    database.matchPlacementDao().upsertAll(match.placements.map { it.toEntity(match.id) })
                    database.matchKillDao().upsertAll(match.kills.map { it.toEntity(match.id) })
                    database.matchParticipantResultDao().upsertAll(
                        match.participantResults.map { it.toEntity(match.id) },
                    )
                }
                snapshot.cloudRevision?.let { revision ->
                    database.syncRevisionDao().upsert(
                        com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                            snapshot.tournamentId,
                            revision.value,
                            revision.value,
                        ),
                    )
                }
                saveLegacyState(next)
            }
            state.value = next
        }
    }

    override suspend fun replaceDraftMatches(snapshot: MatchCloudRestorationSnapshot) {
        require(snapshot.matches.all { it.tournamentId == snapshot.tournamentId && it.status == MatchStatus.DRAFT })
        require(snapshot.matches.map { it.matchNumber }.distinct().size == snapshot.matches.size)
        awaitState()
        writeMutex.withLock {
            val finalized = state.value.matches[snapshot.tournamentId].orEmpty()
                .filter { it.status == MatchStatus.FINALIZED }
            val next = state.value.copy(matches = state.value.matches + (snapshot.tournamentId to (finalized + snapshot.matches)))
            database.withTransaction {
                database.matchDao().deleteDraftByTournamentId(snapshot.tournamentId)
                snapshot.matches.forEach { match ->
                    database.matchDao().upsert(match.toEntity())
                    database.matchPlacementDao().upsertAll(match.placements.map { it.toEntity(match.id) })
                    database.matchKillDao().upsertAll(match.kills.map { it.toEntity(match.id) })
                    database.matchParticipantResultDao().upsertAll(
                        match.participantResults.map { it.toEntity(match.id) },
                    )
                }
                snapshot.cloudRevision?.let { revision ->
                    database.syncRevisionDao().upsert(
                        com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                            snapshot.tournamentId,
                            revision.value,
                            revision.value,
                        ),
                    )
                }
                saveLegacyState(next)
            }
            state.value = next
        }
    }

    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flow {
        ready.await()
        emitAll(database.teamSlotDao().observeByTournamentId(tournamentId).map { slots ->
            slots.map { it.toDomain() }
        })
    }

    override suspend fun saveTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    ) {
        teamNamesBySlotNumber.keys.forEach { require(it in TeamSlot.SLOT_NUMBERS) }
        awaitState()
        writeMutex.withLock {
            val current = state.value
            if (current.tournaments.none { it.id == tournamentId }) return@withLock
            val normalizedSlots = database.teamSlotDao()
                .observeByTournamentId(tournamentId)
                .first()
                .map { it.toDomain() }
            val legacySlots = current.slots[tournamentId].orEmpty()
            val slots = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                normalizedSlots.firstOrNull { it.slotNumber == slotNumber }
                    ?: legacySlots.firstOrNull { it.slotNumber == slotNumber }
                    ?: TeamSlot.create(tournamentId, slotNumber)
            }
            val updatedSlots = slots.map { slot ->
                slot.copy(teamName = teamNamesBySlotNumber[slot.slotNumber] ?: slot.teamName)
            }
            val next = current.copy(
                tournaments = current.tournaments.map { tournament ->
                    if (tournament.id == tournamentId && tournament.status == TournamentStatus.CONFIRMED) {
                        tournament.copy(status = TournamentStatus.DRAFT)
                    } else tournament
                },
                slots = current.slots + (tournamentId to updatedSlots),
            )
            val teamNamesChanged = updatedSlots != slots
            val tournamentStatusChanged = current.tournaments
                .firstOrNull { it.id == tournamentId }
                ?.status != next.tournaments.firstOrNull { it.id == tournamentId }?.status
            if (!teamNamesChanged && !tournamentStatusChanged) return@withLock
            database.withTransaction {
                database.teamSlotDao().upsertAll(updatedSlots.map { it.toEntity() })
                persistTournamentStatusChanges(current, next)
                touchTournament(tournamentId)
                saveLegacyState(next)
                markLocalRevisionChanged(tournamentId)
            }
            state.value = next
        }
    }

    override fun observeRosterByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>> = flow {
        ready.await()
        emitAll(
            database.rosterPlayerDao()
                .observeByTournamentAndSlot(tournamentId, slotNumber)
                .map { players -> players.map { it.toDomain() } },
        )
    }

    override fun observeRosterByTournamentId(
        tournamentId: String,
    ): Flow<Map<Int, List<RosterPlayer>>> = flow {
        ready.await()
        emitAll(
            database.rosterPlayerDao()
                .observeByTournamentId(tournamentId)
                .map { players ->
                    players.groupBy { it.slotNumber }
                        .mapValues { (_, roster) -> roster.map { it.toDomain() } }
                },
        )
    }

    override suspend fun saveRoster(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    ) {
        require(slotNumber in TeamSlot.SLOT_NUMBERS)
        require(players.size <= RosterPlayer.MAX_PLAYERS)
        require(players.all { it.tournamentId == tournamentId && it.slotNumber == slotNumber })
        awaitState()
        writeMutex.withLock {
            val current = state.value
            if (current.tournaments.none { it.id == tournamentId }) return@withLock
            val next = current.copy(
                tournaments = current.tournaments.map { tournament ->
                    if (tournament.id == tournamentId && tournament.status == TournamentStatus.CONFIRMED) {
                        tournament.copy(status = TournamentStatus.DRAFT)
                    } else tournament
                },
                rosters = current.rosters + (RosterKey(tournamentId, slotNumber) to players.toList()),
            )
            val tournamentStatusChanged = current.tournaments
                .firstOrNull { it.id == tournamentId }
                ?.status != next.tournaments.firstOrNull { it.id == tournamentId }?.status
            database.withTransaction {
                database.rosterPlayerDao().deleteByTournamentAndSlot(tournamentId, slotNumber)
                database.rosterPlayerDao().upsertAll(players.toEntities())
                persistTournamentStatusChanges(current, next)
                if (tournamentStatusChanged) {
                    touchTournament(tournamentId)
                }
                saveLegacyState(next)
                markLocalRevisionChanged(tournamentId)
            }
            state.value = next
        }
    }

    override suspend fun replaceConfirmedTournamentRoster(
        candidate: ConfirmedRosterReplacementCandidate,
    ): ReplaceConfirmedTournamentRosterRepositoryResult {
        val expectedSlots = TeamSlot.SLOT_NUMBERS.toSet()
        if (
            candidate.tournamentId.isBlank() ||
            candidate.teamNamesBySlotNumber.keys != expectedSlots ||
            candidate.rosterPlayersBySlotNumber.keys != expectedSlots ||
            candidate.rosterPlayersBySlotNumber.any { (slotNumber, players) ->
                players.any { player ->
                    player.tournamentId != candidate.tournamentId || player.slotNumber != slotNumber
                }
            }
        ) {
            return ReplaceConfirmedTournamentRosterRepositoryResult.InvalidCandidate
        }

        awaitState()
        return writeMutex.withLock {
            var updatedState: RepositoryState? = null
            val result = database.withTransaction {
                val tournamentEntity = database.tournamentDao()
                    .observeById(candidate.tournamentId)
                    .first()
                    ?: return@withTransaction ReplaceConfirmedTournamentRosterRepositoryResult.TournamentNotFound
                if (database.matchDao().observeByTournamentId(candidate.tournamentId).first().isNotEmpty()) {
                    return@withTransaction ReplaceConfirmedTournamentRosterRepositoryResult.BlockedByExistingMatches
                }

                val existingSlots = database.teamSlotDao()
                    .observeByTournamentId(candidate.tournamentId)
                    .first()
                val confirmedTournament = tournamentEntity.toDomain().copy(status = TournamentStatus.CONFIRMED)
                val replacementSlots = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                    TeamSlot(
                        tournamentId = candidate.tournamentId,
                        slotNumber = slotNumber,
                        teamName = candidate.teamNamesBySlotNumber.getValue(slotNumber),
                    )
                }
                val replacementRosters = TeamSlot.SLOT_NUMBERS.associate { slotNumber ->
                    RosterKey(candidate.tournamentId, slotNumber) to
                        candidate.rosterPlayersBySlotNumber.getValue(slotNumber)
                }
                val next = state.value.copy(
                    tournaments = state.value.tournaments.map { tournament ->
                        if (tournament.id == candidate.tournamentId) confirmedTournament else tournament
                    }.let { tournaments ->
                        if (tournaments.any { it.id == candidate.tournamentId }) {
                            tournaments
                        } else {
                            tournaments + confirmedTournament
                        }
                    },
                    slots = state.value.slots + (candidate.tournamentId to replacementSlots),
                    rosters = state.value.rosters
                        .filterKeys { it.tournamentId != candidate.tournamentId }
                        .plus(replacementRosters),
                )
                val teamNamesChanged = TeamSlot.SLOT_NUMBERS.any { slotNumber ->
                    existingSlots.firstOrNull { it.slotNumber == slotNumber }?.teamName !=
                        candidate.teamNamesBySlotNumber.getValue(slotNumber)
                }
                val tournamentStatusChanged = tournamentEntity.toDomain().status != TournamentStatus.CONFIRMED

                database.teamSlotDao().upsertAll(replacementSlots.map { it.toEntity() })
                TeamSlot.SLOT_NUMBERS.forEach { slotNumber ->
                    database.rosterPlayerDao()
                        .deleteByTournamentAndSlot(candidate.tournamentId, slotNumber)
                }
                database.rosterPlayerDao().upsertAll(
                    TeamSlot.SLOT_NUMBERS.flatMap { slotNumber ->
                        candidate.rosterPlayersBySlotNumber.getValue(slotNumber).toEntities()
                    },
                )
                persistTournamentStatusChanges(state.value, next)
                if (teamNamesChanged || tournamentStatusChanged) {
                    touchTournament(candidate.tournamentId)
                }
                saveLegacyState(next)
                markLocalRevisionChanged(candidate.tournamentId)
                updatedState = next
                ReplaceConfirmedTournamentRosterRepositoryResult.Replaced
            }
            if (result is ReplaceConfirmedTournamentRosterRepositoryResult.Replaced) {
                state.value = checkNotNull(updatedState)
            }
            result
        }
    }

    override suspend fun confirmTournament(tournamentId: String): Boolean {
        var confirmed = false
        updateState { current ->
            if (current.tournaments.none { it.id == tournamentId }) return@updateState current
            current.copy(tournaments = current.tournaments.map { tournament ->
                if (tournament.id == tournamentId && tournament.status == TournamentStatus.DRAFT) {
                    confirmed = true
                    tournament.copy(status = TournamentStatus.CONFIRMED)
                } else tournament
            })
        }
        return confirmed
    }

    override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> = flow {
        ready.await()
        emitAll(
            combine(
                database.matchDao().observeByTournamentId(tournamentId),
                database.matchPlacementDao().observeByTournamentId(tournamentId),
                database.matchKillDao().observeByTournamentId(tournamentId),
                database.matchParticipantResultDao().observeByTournamentId(tournamentId),
                database.matchCorrectionDao().observeByTournamentId(tournamentId),
            ) { matches, placements, kills, participantResults, corrections ->
                val placementsByMatch = placements.groupBy { it.matchId }
                val killsByMatch = kills.groupBy { it.matchId }
                val participantResultsByMatch = participantResults.groupBy { it.matchId }
                val correctionsByMatch = corrections.groupBy { it.matchId }
                matches.map { match ->
                    match.toDomain(
                        placements = placementsByMatch[match.id].orEmpty().map { it.toDomain() },
                        kills = killsByMatch[match.id].orEmpty().map { it.toDomain() },
                        participantResults = participantResultsByMatch[match.id].orEmpty()
                            .map { it.toDomain() },
                        correctionHistory = correctionsByMatch[match.id].orEmpty().map { it.toDomain(json) },
                    )
                }
            },
        )
    }

    override fun observeMatchById(matchId: String): Flow<Match?> = flow {
        ready.await()
        emitAll(
            combine(
                database.matchDao().observeById(matchId),
                database.matchPlacementDao().observeByMatchId(matchId),
                database.matchKillDao().observeByMatchId(matchId),
                database.matchParticipantResultDao().observeByMatchId(matchId),
                database.matchCorrectionDao().observeByMatchId(matchId),
            ) { match, placements, kills, participantResults, corrections ->
                match?.toDomain(
                    placements = placements.map { it.toDomain() },
                    kills = kills.map { it.toDomain() },
                    participantResults = participantResults.map { it.toDomain() },
                    correctionHistory = corrections.map { it.toDomain(json) },
                )
            },
        )
    }

    override suspend fun readPreservedMatchOcrEvidence(
        tournamentId: String,
        matchId: String,
    ): PreservedMatchOcrEvidence? {
        awaitState()
        val evidence = database.matchOcrEvidenceDao().readMatchEvidence(matchId)
            ?.takeIf { it.tournamentId == tournamentId }
            ?: return null
        return PreservedMatchOcrEvidence(
            tournamentId = evidence.tournamentId,
            matchId = evidence.matchId,
            sourceScreenshotId = evidence.sourceScreenshotId,
            preservedAt = evidence.preservedAt,
            provenance = evidence.provenance,
            rows = database.matchOcrEvidenceDao().readRowEvidence(matchId).map { row ->
                PreservedMatchOcrRowEvidence(
                    rowIndex = row.rowIndex,
                    originalOcrText = row.originalOcrText,
                    originalPlacement = row.originalPlacement,
                    originalKills = row.originalKills,
                    originalSuggestedTeamSlot = row.originalSuggestedTeamSlot,
                    confidenceSummary = row.confidenceSummary,
                    safetySummary = row.safetySummary,
                    manualReviewRequired = row.manualReviewRequired,
                )
            },
            correctionSnapshots = database.matchOcrEvidenceDao().readCorrectionSnapshots(matchId).map { snapshot ->
                PreservedMatchOcrCorrectionSnapshot(
                    rowIndex = snapshot.rowIndex,
                    correctedPlacement = snapshot.correctedPlacement,
                    correctedKills = snapshot.correctedKills,
                    correctedTeamSlot = snapshot.correctedTeamSlot,
                    placementChanged = snapshot.placementChanged,
                    killsChanged = snapshot.killsChanged,
                    teamSlotChanged = snapshot.teamSlotChanged,
                )
            },
        )
    }

    override suspend fun createDraftMatch(match: Match): CreateMatchRepositoryResult {
        awaitState()
        return writeMutex.withLock {
            val current = state.value
            val tournament = current.tournaments.firstOrNull { it.id == match.tournamentId }
                ?: return@withLock CreateMatchRepositoryResult.Rejected(MatchCreationFailure.TOURNAMENT_NOT_FOUND)
            val participation = current.slots[match.tournamentId]
                .orEmpty()
                .analyzeTeamSlotParticipation()
            if (participation.activeCount == 0) {
                return@withLock CreateMatchRepositoryResult.Rejected(MatchCreationFailure.NO_PARTICIPATING_TEAMS)
            }
            val matches = current.matches[match.tournamentId].orEmpty()
            if (matches.any { it.id == match.id }) {
                return@withLock CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_ID)
            }
            if (matches.any { it.matchNumber == match.matchNumber }) {
                return@withLock CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_MATCH_NUMBER)
            }
            if (matches.size >= MAX_MATCHES_PER_TOURNAMENT) {
                return@withLock CreateMatchRepositoryResult.Rejected(MatchCreationFailure.LIMIT_REACHED)
            }
            val next = current.copy(
                matches = current.matches + (match.tournamentId to (matches + match)),
            )
            database.withTransaction {
                database.matchDao().upsert(match.toEntity())
                replaceMatchPlacements(match.id, match.placements)
                replaceMatchKills(match.id, match.kills)
                replaceMatchCorrections(match.id, match.correctionHistory)
                touchTournament(match.tournamentId)
                saveLegacyState(next)
                markLocalRevisionChanged(match.tournamentId)
            }
            state.value = next
            CreateMatchRepositoryResult.Created
        }
    }

    override suspend fun saveDraftMatchPlacements(
        matchId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult {
        awaitState()
        return writeMutex.withLock {
            val current = state.value
            val match = current.matches.values.flatten().firstOrNull { it.id == matchId }
                ?: return@withLock SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_FOUND)
            if (match.status != MatchStatus.DRAFT) {
                return@withLock SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_DRAFT)
            }
            if (placements.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) {
                return@withLock SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.INVALID_TEAM_SLOT)
            }
            if (placements.any { it.position !in TeamSlot.SLOT_NUMBERS }) {
                return@withLock SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.INVALID_POSITION)
            }
            if (placements.map { it.teamSlotNumber }.distinct().size != placements.size) {
                return@withLock SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_TEAM_SLOT)
            }
            if (placements.map { it.position }.distinct().size != placements.size) {
                return@withLock SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_POSITION)
            }
            val updatedMatch = match.copy(placements = placements.toList())
            if (updatedMatch.placements == match.placements) {
                return@withLock SaveMatchPlacementsRepositoryResult.Saved
            }
            val next = current.replaceMatch(match.id) { updatedMatch }
            database.withTransaction {
                database.matchDao().upsert(updatedMatch.toEntity())
                replaceMatchPlacements(matchId, placements)
                touchTournament(match.tournamentId)
                saveLegacyState(next)
                markLocalRevisionChanged(match.tournamentId)
            }
            state.value = next
            SaveMatchPlacementsRepositoryResult.Saved
        }
    }

    override suspend fun saveDraftMatchKills(
        matchId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult {
        awaitState()
        return writeMutex.withLock {
            val current = state.value
            val match = current.matches.values.flatten().firstOrNull { it.id == matchId }
                ?: return@withLock SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_FOUND)
            if (match.status != MatchStatus.DRAFT) {
                return@withLock SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_DRAFT)
            }
            if (kills.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) {
                return@withLock SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_TEAM_SLOT)
            }
            if (kills.any { it.kills < 0 }) {
                return@withLock SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_KILLS)
            }
            if (kills.map { it.teamSlotNumber }.distinct().size != kills.size) {
                return@withLock SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.DUPLICATE_TEAM_SLOT)
            }
            val updatedMatch = match.copy(kills = kills.toList())
            if (updatedMatch.kills == match.kills) {
                return@withLock SaveMatchKillsRepositoryResult.Saved
            }
            val next = current.replaceMatch(match.id) { updatedMatch }
            database.withTransaction {
                database.matchDao().upsert(updatedMatch.toEntity())
                replaceMatchKills(matchId, kills)
                touchTournament(match.tournamentId)
                saveLegacyState(next)
                markLocalRevisionChanged(match.tournamentId)
            }
            state.value = next
            SaveMatchKillsRepositoryResult.Saved
        }
    }

    override suspend fun finalizeDraftMatch(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
    ): FinalizeMatchRepositoryResult =
        finalizeDraftMatchInternal(
            matchId = matchId,
            placements = placements,
            kills = kills,
            participantResults = participantResults,
            evidence = null,
        )

    override suspend fun finalizeDraftMatchWithOcrEvidence(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
        evidence: PreservedMatchOcrEvidence,
    ): FinalizeMatchRepositoryResult =
        finalizeDraftMatchInternal(
            matchId = matchId,
            placements = placements,
            kills = kills,
            participantResults = participantResults,
            evidence = evidence,
        )

    private suspend fun finalizeDraftMatchInternal(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
        evidence: PreservedMatchOcrEvidence?,
    ): FinalizeMatchRepositoryResult {
        awaitState()
        return writeMutex.withLock {
            val current = state.value
            val match = current.matches.values.flatten().firstOrNull { it.id == matchId }
                ?: return@withLock FinalizeMatchRepositoryResult.Rejected(
                    FinalizeMatchFailure.MATCH_NOT_FOUND,
                )
            if (match.status != MatchStatus.DRAFT) {
                return@withLock FinalizeMatchRepositoryResult.Rejected(
                    FinalizeMatchFailure.MATCH_NOT_DRAFT,
                )
            }
            val participation = database.teamSlotDao()
                .observeByTournamentId(match.tournamentId)
                .first()
                .map { it.toDomain() }
                .analyzeTeamSlotParticipation()
            val expectedTeamSlots = participation.activeSlotNumbers.toSet()
            val positionedTeamSlots = placements.map { it.teamSlotNumber }.toSet()
            val expectedPlacements = (1..placements.size).toSet()
            val finalizedParticipantResults = participantResults
                ?: buildLegacyParticipantResults(
                    registeredTeamSlots = expectedTeamSlots,
                    placements = placements,
                    kills = kills,
                )
            if (
                !participation.isReadyForMatchCreation ||
                placements.isEmpty() ||
                placements.any { it.teamSlotNumber !in expectedTeamSlots || it.position !in expectedPlacements } ||
                kills.any { it.teamSlotNumber !in positionedTeamSlots || it.kills < 0 } ||
                kills.map { it.teamSlotNumber }.toSet() != positionedTeamSlots ||
                placements.map { it.position }.toSet() != expectedPlacements ||
                placements.map { it.teamSlotNumber }.distinct().size != placements.size ||
                kills.map { it.teamSlotNumber }.distinct().size != kills.size ||
                placements.map { it.position }.distinct().size != placements.size ||
                !finalizedParticipantResults.isValidSnapshotFor(
                    registeredTeamSlots = expectedTeamSlots,
                    positionedTeamSlots = positionedTeamSlots,
                )
            ) {
                return@withLock FinalizeMatchRepositoryResult.Rejected(
                    FinalizeMatchFailure.INVALID_DATA,
                )
            }
            if (
                evidence != null &&
                !evidence.isValidFor(
                    match = match,
                    placements = placements,
                    kills = kills,
                    expectedTeamSlots = positionedTeamSlots,
                    expectedPlacements = expectedPlacements,
                )
            ) {
                return@withLock FinalizeMatchRepositoryResult.Rejected(
                    FinalizeMatchFailure.INVALID_DATA,
                )
            }
            val finalizedMatch = match.copy(
                status = MatchStatus.FINALIZED,
                placements = placements.toList(),
                kills = kills.toList(),
                participantResults = finalizedParticipantResults.sortedBy { it.teamSlotNumber },
            )
            val next = current.copy(
                matches = current.matches.replaceMatch(match.tournamentId, matchId) { finalizedMatch },
                draftValues = current.draftValues - DraftKey(match.tournamentId, matchId),
            )
            try {
                database.withTransaction {
                    database.matchDao().upsert(finalizedMatch.toEntity())
                    replaceMatchPlacements(matchId, placements)
                    replaceMatchKills(matchId, kills)
                    replaceMatchParticipantResults(matchId, finalizedParticipantResults)
                    database.matchDraftValueDao().deleteByMatchId(matchId)
                    touchTournament(match.tournamentId)
                    saveLegacyState(next)
                    markLocalRevisionChanged(match.tournamentId)
                    evidence?.let { preservedEvidence ->
                        database.matchOcrEvidenceDao().insertSnapshot(
                            matchEvidence = preservedEvidence.toEntity(),
                            rowEvidence = preservedEvidence.rows.map { it.toEntity(preservedEvidence) },
                            correctionSnapshots = preservedEvidence.correctionSnapshots.map {
                                it.toEntity(preservedEvidence)
                            },
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (evidence != null) {
                    return@withLock FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.INVALID_DATA)
                }
                throw throwable
            }
            state.value = next
            FinalizeMatchRepositoryResult.Finalized(finalizedMatch)
        }
    }

    override suspend fun submitMatchCorrection(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>?,
    ): SubmitMatchCorrectionRepositoryResult {
        awaitState()
        return writeMutex.withLock {
            val current = state.value
            val match = current.matches.values.flatten().firstOrNull { it.id == matchId }
                ?: return@withLock SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.MATCH_NOT_FOUND)
            if (match.status != MatchStatus.FINALIZED) {
                return@withLock SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.MATCH_NOT_FINALIZED)
            }
            val previousParticipantResults = match.finalizedParticipantResultsOrNull()
            val correctedParticipantResults = participantResults ?: placements.map { placement ->
                val kill = kills.singleOrNull { it.teamSlotNumber == placement.teamSlotNumber }
                    ?: return@withLock SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.INVALID_DATA)
                MatchParticipantResult(
                    teamSlotNumber = placement.teamSlotNumber,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = placement.position,
                    kills = kill.kills,
                )
            }
            if (!isValidCorrectionSnapshot(previousParticipantResults, correctedParticipantResults)) {
                return@withLock SubmitMatchCorrectionRepositoryResult.Rejected(MatchCorrectionFailure.INVALID_DATA)
            }
            val correctedMatch = match.copy(
                placements = correctedParticipantResults.mapNotNull { result ->
                    result.placement?.let { MatchPlacement(result.teamSlotNumber, it) }
                },
                kills = correctedMatchKills(correctedParticipantResults),
                participantResults = correctedParticipantResults.sortedBy { it.teamSlotNumber },
                correctionHistory = match.correctionHistory + MatchCorrectionRecord(
                    previousPlacements = match.placements.toList(),
                    previousKills = match.kills.toList(),
                    correctedPlacements = correctedMatchPlacements(correctedParticipantResults),
                    correctedKills = correctedMatchKills(correctedParticipantResults),
                    previousParticipantResults = previousParticipantResults.orEmpty(),
                    correctedParticipantResults = correctedParticipantResults,
                ),
            )
            val next = current.copy(
                matches = current.matches.replaceMatch(match.tournamentId, matchId) { correctedMatch },
                draftValues = current.draftValues - DraftKey(match.tournamentId, matchId),
            )
            database.withTransaction {
                database.matchDao().upsert(correctedMatch.toEntity())
                replaceMatchPlacements(matchId, correctedMatch.placements)
                replaceMatchKills(matchId, correctedMatch.kills)
                replaceMatchParticipantResults(matchId, correctedMatch.participantResults)
                replaceMatchCorrections(matchId, correctedMatch.correctionHistory)
                database.matchDraftValueDao().deleteByMatchId(matchId)
                touchTournament(match.tournamentId)
                saveLegacyState(next)
                markLocalRevisionChanged(match.tournamentId)
            }
            state.value = next
            SubmitMatchCorrectionRepositoryResult.Submitted(correctedMatch)
        }
    }

    override fun observeDraftMatchValues(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = flow {
        ready.await()
        emitAll(
            database.matchDraftValueDao().observeByMatchId(matchId).map { values ->
                values.associate { it.teamSlotNumber to it.toDomain() }
            },
        )
    }

    override suspend fun saveDraftMatchValue(
        tournamentId: String,
        matchId: String,
        teamSlotNumber: Int,
        placementInput: String?,
        killsInput: String?,
    ) {
        require(teamSlotNumber in TeamSlot.SLOT_NUMBERS)
        awaitState()
        writeMutex.withLock {
            val current = state.value
            val match = current.matches[tournamentId].orEmpty().firstOrNull { it.id == matchId } ?: return@withLock
            val key = DraftKey(tournamentId, matchId)
            val old = current.draftValues[key]?.get(teamSlotNumber) ?: MatchDraftFieldValues()
            val updated = old.copy(
                placementInput = placementInput ?: old.placementInput,
                killsInput = killsInput ?: old.killsInput,
            )
            val next = current.copy(
                draftValues = current.draftValues + (key to (
                    current.draftValues[key].orEmpty() + (teamSlotNumber to updated)
                    )),
            )
            database.withTransaction {
                database.matchDraftValueDao().upsert(updated.toEntity(match.id, teamSlotNumber))
                if (updated != old) touchTournament(tournamentId)
                saveLegacyState(next)
            }
            state.value = next
        }
    }

    override suspend fun clearDraftMatch(tournamentId: String, matchId: String) {
        awaitState()
        writeMutex.withLock {
            val current = state.value
            val match = current.matches[tournamentId].orEmpty().firstOrNull { it.id == matchId } ?: return@withLock
            val clearedMatch = match.copy(placements = emptyList(), kills = emptyList())
            val hadDraftValues = current.draftValues[DraftKey(tournamentId, matchId)].orEmpty().isNotEmpty()
            if (clearedMatch == match && !hadDraftValues) return@withLock
            val next = current.copy(
                matches = current.matches.replaceMatch(tournamentId, matchId) { clearedMatch },
                draftValues = current.draftValues - DraftKey(tournamentId, matchId),
            )
            database.withTransaction {
                database.matchDao().upsert(clearedMatch.toEntity())
                database.matchPlacementDao().deleteByMatchId(matchId)
                database.matchKillDao().deleteByMatchId(matchId)
                database.matchDraftValueDao().deleteByMatchId(matchId)
                touchTournament(tournamentId)
                saveLegacyState(next)
                markLocalRevisionChanged(tournamentId)
            }
            state.value = next
        }
    }

    override suspend fun clearMatchCorrectionDraft(tournamentId: String, matchId: String) {
        awaitState()
        writeMutex.withLock {
            val current = state.value
            if (current.matches[tournamentId].orEmpty().none { it.id == matchId }) return@withLock
            if (current.draftValues[DraftKey(tournamentId, matchId)].orEmpty().isEmpty()) return@withLock
            val next = current.copy(draftValues = current.draftValues - DraftKey(tournamentId, matchId))
            database.withTransaction {
                database.matchDraftValueDao().deleteByMatchId(matchId)
                touchTournament(tournamentId)
                saveLegacyState(next)
            }
            state.value = next
        }
    }

    private suspend fun awaitState(): RepositoryState {
        ready.await()
        return state.value
    }

    private suspend fun updateState(transform: (RepositoryState) -> RepositoryState) {
        ready.await()
        writeMutex.withLock {
            val current = state.value
            val next = transform(current)
            database.withTransaction {
                persistTournamentStatusChanges(current, next)
                current.tournaments.zip(next.tournaments)
                    .filter { (before, after) -> before.status != after.status }
                    .forEach { (_, tournament) -> touchTournament(tournament.id) }
                saveLegacyState(next)
            }
            state.value = next
        }
    }

    private suspend fun persistTournamentStatusChanges(
        current: RepositoryState,
        next: RepositoryState,
    ) {
        next.tournaments
            .filter { nextTournament ->
                current.tournaments.firstOrNull { it.id == nextTournament.id }?.status != nextTournament.status
            }
            .forEach { tournament ->
                val existingTournament = database.tournamentDao()
                    .observeById(tournament.id)
                    .first()
                val creationOrder = existingTournament?.creationOrder
                    ?: database.tournamentDao().nextCreationOrder()
                database.tournamentDao().upsert(
                    tournament.toEntity(
                        creationOrder = creationOrder,
                        lastUpdatedEpochMillis = existingTournament?.lastUpdatedEpochMillis,
                    ),
                )
            }
    }

    private suspend fun backfillSlots(
        tournamentId: String,
        restored: RepositoryState,
    ) {
        val existingSlots = database.teamSlotDao().observeByTournamentId(tournamentId).first()
        val legacySlots = restored.slots[tournamentId].orEmpty()
        val missingSlots = TeamSlot.SLOT_NUMBERS.mapNotNull { slotNumber ->
            if (existingSlots.any { it.slotNumber == slotNumber }) {
                null
            } else {
                (legacySlots.firstOrNull { it.slotNumber == slotNumber }
                    ?: TeamSlot.create(tournamentId, slotNumber)).toEntity()
            }
        }
        if (missingSlots.isNotEmpty()) {
            database.teamSlotDao().upsertAll(missingSlots)
        }
    }

    private suspend fun backfillRoster(
        tournamentId: String,
        restored: RepositoryState,
    ) {
        TeamSlot.SLOT_NUMBERS.forEach { slotNumber ->
            val existingPlayers = database.rosterPlayerDao()
                .observeByTournamentAndSlot(tournamentId, slotNumber)
                .first()
            val existingPositions = existingPlayers.map { it.rosterPosition }.toSet()
            val legacyPlayers = restored.rosters[RosterKey(tournamentId, slotNumber)].orEmpty()
            val missingPlayers = legacyPlayers.mapIndexedNotNull { index, player ->
                val rosterPosition = index + 1
                if (rosterPosition in existingPositions) null else player.toEntity(rosterPosition)
            }
            if (missingPlayers.isNotEmpty()) {
                database.rosterPlayerDao().upsertAll(missingPlayers)
            }
        }
    }

    private suspend fun replaceMatchPlacements(
        matchId: String,
        placements: List<MatchPlacement>,
    ) {
        database.matchPlacementDao().deleteByMatchId(matchId)
        if (placements.isNotEmpty()) {
            database.matchPlacementDao().upsertAll(placements.map { it.toEntity(matchId) })
        }
    }

    private suspend fun replaceMatchKills(
        matchId: String,
        kills: List<MatchKill>,
    ) {
        database.matchKillDao().deleteByMatchId(matchId)
        if (kills.isNotEmpty()) {
            database.matchKillDao().upsertAll(kills.map { it.toEntity(matchId) })
        }
    }

    private suspend fun replaceMatchParticipantResults(
        matchId: String,
        participantResults: List<MatchParticipantResult>,
    ) {
        database.matchParticipantResultDao().deleteByMatchId(matchId)
        if (participantResults.isNotEmpty()) {
            database.matchParticipantResultDao().upsertAll(
                participantResults.map { it.toEntity(matchId) },
            )
        }
    }

    private suspend fun replaceMatchCorrections(
        matchId: String,
        corrections: List<MatchCorrectionRecord>,
    ) {
        database.matchCorrectionDao().upsertAll(
            corrections.mapIndexed { index, correction -> correction.toEntity(matchId, index, json) },
        )
    }

    private suspend fun backfillMatchPlacements(
        matchId: String,
        legacyMatch: Match?,
    ) {
        val existing = database.matchPlacementDao().observeByMatchId(matchId).first()
        val existingSlots = existing.map { it.teamSlotNumber }.toSet()
        val missing = legacyMatch?.placements.orEmpty()
            .filter { it.teamSlotNumber !in existingSlots }
            .map { it.toEntity(matchId) }
        if (missing.isNotEmpty()) database.matchPlacementDao().upsertAll(missing)
    }

    private suspend fun backfillMatchKills(
        matchId: String,
        legacyMatch: Match?,
    ) {
        val existing = database.matchKillDao().observeByMatchId(matchId).first()
        val existingSlots = existing.map { it.teamSlotNumber }.toSet()
        val missing = legacyMatch?.kills.orEmpty()
            .filter { it.teamSlotNumber !in existingSlots }
            .map { it.toEntity(matchId) }
        if (missing.isNotEmpty()) database.matchKillDao().upsertAll(missing)
    }

    private suspend fun backfillMatchParticipantResults(
        matchId: String,
        legacyMatch: Match?,
    ) {
        val existing = database.matchParticipantResultDao().observeByMatchId(matchId).first()
        if (existing.isNotEmpty()) return

        val source = legacyMatch?.participantResults.orEmpty().ifEmpty {
            if (legacyMatch?.status == MatchStatus.FINALIZED) {
                val placements = database.matchPlacementDao().observeByMatchId(matchId)
                    .first()
                    .map { it.toDomain() }
                val killsBySlot = database.matchKillDao().observeByMatchId(matchId)
                    .first()
                    .map { it.toDomain() }
                    .associateBy { it.teamSlotNumber }
                placements.mapNotNull { placement ->
                    killsBySlot[placement.teamSlotNumber]?.let { kill ->
                        MatchParticipantResult(
                            teamSlotNumber = placement.teamSlotNumber,
                            participationStatus = MatchParticipationStatus.PARTICIPATED,
                            placement = placement.position,
                            kills = kill.kills,
                        )
                    }
                }
            } else {
                emptyList()
            }
        }
        if (source.isNotEmpty()) {
            database.matchParticipantResultDao().upsertAll(source.map { it.toEntity(matchId) })
        }
    }

    private suspend fun backfillMatchCorrections(
        matchId: String,
        legacyMatch: Match?,
    ) {
        val existing = database.matchCorrectionDao().observeByMatchId(matchId).first()
        val existingIndexes = existing.map { it.correctionIndex }.toSet()
        val missing = legacyMatch?.correctionHistory.orEmpty()
            .mapIndexedNotNull { index, correction ->
                if (index in existingIndexes) null else correction.toEntity(matchId, index, json)
            }
        if (missing.isNotEmpty()) database.matchCorrectionDao().upsertAll(missing)
    }

    private suspend fun backfillMatchDraftValues(
        matchId: String,
        restored: RepositoryState,
    ) {
        val match = database.matchDao().observeById(matchId).first() ?: return
        val key = DraftKey(match.tournamentId, match.id)
        val existing = database.matchDraftValueDao().observeByMatchId(matchId).first()
        val existingSlots = existing.map { it.teamSlotNumber }.toSet()
        val missing = restored.draftValues[key].orEmpty()
            .filterKeys { it !in existingSlots }
            .map { (slotNumber, value) -> value.toEntity(matchId, slotNumber) }
        if (missing.isNotEmpty()) database.matchDraftValueDao().upsertAll(missing)
    }

    private suspend fun saveLegacyState(state: RepositoryState) {
        database.stateDao().save(
            RankForgeStateEntity(payload = json.encodeToString(state.toPersistedState())),
        )
    }

    private suspend fun markLocalRevisionChanged(tournamentId: String) {
        val revisions = database.syncRevisionDao()
        val existing = revisions.readByTournamentId(tournamentId)
        if (existing == null) {
            revisions.upsert(
                com.hoggamers.rankforge.data.local.SyncRevisionEntity(
                    tournamentId = tournamentId,
                    localRevision = 1,
                    baseCloudRevision = null,
                ),
            )
        } else {
            revisions.incrementLocalRevision(tournamentId)
        }
    }

    private suspend fun touchTournament(tournamentId: String) {
        database.tournamentDao().updateLastUpdatedEpochMillis(tournamentId, clock.millis())
    }

    private fun PreservedMatchOcrEvidence.isValidFor(
        match: Match,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        expectedTeamSlots: Set<Int>,
        expectedPlacements: Set<Int>,
    ): Boolean {
        val expectedRowIndexes = (0 until TeamSlot.MAX_SLOT_NUMBER).toSet()
        val placementsBySlot = placements.associateBy { it.teamSlotNumber }
        val killsBySlot = kills.associateBy { it.teamSlotNumber }

        return matchId == match.id &&
            tournamentId == match.tournamentId &&
            provenance.isNotBlank() &&
            rows.size == TeamSlot.MAX_SLOT_NUMBER &&
            correctionSnapshots.size == expectedTeamSlots.size &&
            rows.map { it.rowIndex }.toSet() == expectedRowIndexes &&
            correctionSnapshots.map { it.rowIndex }.distinct().size == correctionSnapshots.size &&
            correctionSnapshots.all { it.rowIndex in expectedRowIndexes } &&
            rows.all { row ->
                row.originalPlacement == null || row.originalPlacement in TeamSlot.SLOT_NUMBERS
            } &&
            rows.all { row ->
                row.originalKills == null || row.originalKills >= 0
            } &&
            rows.all { row ->
                row.originalSuggestedTeamSlot == null || row.originalSuggestedTeamSlot in TeamSlot.SLOT_NUMBERS
            } &&
            placements.map { it.teamSlotNumber }.toSet() == expectedTeamSlots &&
            kills.map { it.teamSlotNumber }.toSet() == expectedTeamSlots &&
            placements.map { it.position }.toSet() == expectedPlacements &&
            correctionSnapshots.map { it.correctedPlacement }.toSet().size == expectedTeamSlots.size &&
            correctionSnapshots.map { it.correctedTeamSlot }.toSet().size == expectedTeamSlots.size &&
            correctionSnapshots.all { snapshot ->
                snapshot.correctedPlacement in expectedPlacements &&
                    snapshot.correctedKills >= 0 &&
                    snapshot.correctedTeamSlot in expectedTeamSlots &&
                    placementsBySlot[snapshot.correctedTeamSlot]?.position == snapshot.correctedPlacement &&
                    killsBySlot[snapshot.correctedTeamSlot]?.kills == snapshot.correctedKills
            }
    }

    private fun PreservedMatchOcrEvidence.toEntity() = MatchOcrEvidenceEntity(
        matchId = matchId,
        tournamentId = tournamentId,
        sourceScreenshotId = sourceScreenshotId,
        preservedAt = preservedAt,
        provenance = provenance,
    )

    private fun PreservedMatchOcrRowEvidence.toEntity(
        evidence: PreservedMatchOcrEvidence,
    ) = MatchOcrRowEvidenceEntity(
        matchId = evidence.matchId,
        tournamentId = evidence.tournamentId,
        rowIndex = rowIndex,
        originalOcrText = originalOcrText,
        originalPlacement = originalPlacement,
        originalKills = originalKills,
        originalSuggestedTeamSlot = originalSuggestedTeamSlot,
        confidenceSummary = confidenceSummary,
        safetySummary = safetySummary,
        manualReviewRequired = manualReviewRequired,
    )

    private fun PreservedMatchOcrCorrectionSnapshot.toEntity(
        evidence: PreservedMatchOcrEvidence,
    ) = MatchOcrCorrectionSnapshotEntity(
        matchId = evidence.matchId,
        tournamentId = evidence.tournamentId,
        rowIndex = rowIndex,
        correctedPlacement = correctedPlacement,
        correctedKills = correctedKills,
        correctedTeamSlot = correctedTeamSlot,
        placementChanged = placementChanged,
        killsChanged = killsChanged,
        teamSlotChanged = teamSlotChanged,
        preservedAt = evidence.preservedAt,
        provenance = evidence.provenance,
    )

    private fun List<MatchParticipantResult>.isValidSnapshotFor(
        registeredTeamSlots: Set<Int>,
        positionedTeamSlots: Set<Int>,
    ): Boolean {
        val resultsBySlot = associateBy { it.teamSlotNumber }
        val participated = filter {
            it.participationStatus == MatchParticipationStatus.PARTICIPATED
        }
        val noShows = filter {
            it.participationStatus == MatchParticipationStatus.NO_SHOW
        }
        return size == registeredTeamSlots.size &&
            resultsBySlot.size == size &&
            resultsBySlot.keys == registeredTeamSlots &&
            participated.map { it.teamSlotNumber }.toSet() == positionedTeamSlots &&
            noShows.map { it.teamSlotNumber }.toSet() == registeredTeamSlots - positionedTeamSlots &&
            participated.mapNotNull { it.placement }.toSet() == (1..positionedTeamSlots.size).toSet() &&
            participated.mapNotNull { it.placement }.distinct().size == participated.size
    }

    private fun RepositoryState.replaceMatch(matchId: String, transform: (Match) -> Match): RepositoryState = copy(
        matches = matches.mapValues { (_, matches) -> matches.map { if (it.id == matchId) transform(it) else it } },
    )

    private fun Map<String, List<Match>>.replaceMatch(
        tournamentId: String,
        matchId: String,
        transform: (Match) -> Match,
    ): Map<String, List<Match>> = mapValues { (key, matches) ->
        if (key == tournamentId) matches.map { if (it.id == matchId) transform(it) else it } else matches
    }
}

private fun buildLegacyParticipantResults(
    registeredTeamSlots: Set<Int>,
    placements: List<MatchPlacement>,
    kills: List<MatchKill>,
): List<MatchParticipantResult> {
    val placementsBySlot = placements.associateBy { it.teamSlotNumber }
    val killsBySlot = kills.associateBy { it.teamSlotNumber }
    return registeredTeamSlots.sorted().map { teamSlotNumber ->
        val placement = placementsBySlot[teamSlotNumber]
        val kill = killsBySlot[teamSlotNumber]
        if (placement != null && kill != null) {
            MatchParticipantResult(
                teamSlotNumber = teamSlotNumber,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = placement.position,
                kills = kill.kills,
            )
        } else {
            MatchParticipantResult(
                teamSlotNumber = teamSlotNumber,
                participationStatus = MatchParticipationStatus.NO_SHOW,
                placement = null,
                kills = 0,
            )
        }
    }
}

@Serializable
private data class PersistedState(
    val tournaments: List<PersistedTournament> = emptyList(),
    val slots: List<PersistedSlot> = emptyList(),
    val rosters: List<PersistedRoster> = emptyList(),
    val matches: List<PersistedMatch> = emptyList(),
    val draftValues: List<PersistedDraftMatch> = emptyList(),
)

@Serializable
private data class PersistedTournament(val id: String, val name: String, val date: String, val organizerName: String, val organizerContactNumber: String, val status: String, val ownerUserId: String? = null)
@Serializable
private data class PersistedSlot(val tournamentId: String, val slotNumber: Int, val teamName: String)
@Serializable
private data class PersistedRoster(val tournamentId: String, val slotNumber: Int, val displayName: String)
@Serializable
private data class PersistedMatch(val id: String, val tournamentId: String, val matchNumber: Int, val date: String, val mapName: String, val status: String, val placements: List<PersistedPlacement> = emptyList(), val kills: List<PersistedKill> = emptyList(), val correctionHistory: List<PersistedCorrection> = emptyList(), val participantResults: List<PersistedParticipantResult> = emptyList())
@Serializable
private data class PersistedPlacement(val teamSlotNumber: Int, val position: Int)
@Serializable
private data class PersistedKill(val teamSlotNumber: Int, val kills: Int)
@Serializable
private data class PersistedCorrection(val previousPlacements: List<PersistedPlacement> = emptyList(), val previousKills: List<PersistedKill> = emptyList(), val correctedPlacements: List<PersistedPlacement> = emptyList(), val correctedKills: List<PersistedKill> = emptyList())
@Serializable
private data class PersistedParticipantResult(val teamSlotNumber: Int, val participationStatus: String, val placement: Int? = null, val kills: Int)
@Serializable
private data class PersistedDraftMatch(val tournamentId: String, val matchId: String, val values: List<PersistedDraftValue>)
@Serializable
private data class PersistedDraftValue(val teamSlotNumber: Int, val placementInput: String, val killsInput: String)

private fun RepositoryState.toPersistedState() = PersistedState(
    tournaments = tournaments.map { PersistedTournament(it.id, it.name, it.date.toString(), it.organizerName, it.organizerContactNumber, it.status.name, it.ownerUserId) },
    slots = slots.values.flatten().map { PersistedSlot(it.tournamentId, it.slotNumber, it.teamName) },
    rosters = rosters.map { (key, players) -> players.map { PersistedRoster(key.tournamentId, key.slotNumber, it.displayName) } }.flatten(),
    matches = matches.values.flatten().map { match -> PersistedMatch(match.id, match.tournamentId, match.matchNumber, match.date.toString(), match.mapName, match.status.name, match.placements.map { PersistedPlacement(it.teamSlotNumber, it.position) }, match.kills.map { PersistedKill(it.teamSlotNumber, it.kills) }, match.correctionHistory.map { correction -> PersistedCorrection(correction.previousPlacements.map { PersistedPlacement(it.teamSlotNumber, it.position) }, correction.previousKills.map { PersistedKill(it.teamSlotNumber, it.kills) }, correction.correctedPlacements.map { PersistedPlacement(it.teamSlotNumber, it.position) }, correction.correctedKills.map { PersistedKill(it.teamSlotNumber, it.kills) }) }, match.participantResults.map { result -> PersistedParticipantResult(result.teamSlotNumber, result.participationStatus.name, result.placement, result.kills) }) },
    draftValues = draftValues.map { (key, values) -> PersistedDraftMatch(key.tournamentId, key.matchId, values.map { (slot, value) -> PersistedDraftValue(slot, value.placementInput, value.killsInput) }) },
)

private fun PersistedState.toRepositoryState() = RepositoryState(
    tournaments = tournaments.map { Tournament(it.id, it.name, LocalDate.parse(it.date), it.organizerName, it.organizerContactNumber, TournamentStatus.valueOf(it.status), it.ownerUserId) },
    slots = slots.groupBy { it.tournamentId }.mapValues { (_, values) -> values.map { TeamSlot(it.tournamentId, it.slotNumber, it.teamName) } },
    rosters = rosters.groupBy { RosterKey(it.tournamentId, it.slotNumber) }.mapValues { (_, values) -> values.map { RosterPlayer(it.tournamentId, it.slotNumber, it.displayName) } },
    matches = matches.groupBy { it.tournamentId }.mapValues { (_, values) -> values.map { match -> Match(match.id, match.tournamentId, match.matchNumber, LocalDate.parse(match.date), match.mapName, MatchStatus.valueOf(match.status), match.placements.map { MatchPlacement(it.teamSlotNumber, it.position) }, match.kills.map { MatchKill(it.teamSlotNumber, it.kills) }, match.correctionHistory.map { correction -> MatchCorrectionRecord(correction.previousPlacements.map { MatchPlacement(it.teamSlotNumber, it.position) }, correction.previousKills.map { MatchKill(it.teamSlotNumber, it.kills) }, correction.correctedPlacements.map { MatchPlacement(it.teamSlotNumber, it.position) }, correction.correctedKills.map { MatchKill(it.teamSlotNumber, it.kills) }) }, match.participantResults.map { result -> MatchParticipantResult(result.teamSlotNumber, MatchParticipationStatus.valueOf(result.participationStatus), result.placement, result.kills) }) } },
    draftValues = draftValues.associate { draft ->
        DraftKey(draft.tournamentId, draft.matchId) to draft.values.associate { value ->
            value.teamSlotNumber to MatchDraftFieldValues(value.placementInput, value.killsInput)
        }
    },
)

private data class RepositoryState(
    val tournaments: List<Tournament> = emptyList(),
    val slots: Map<String, List<TeamSlot>> = emptyMap(),
    val rosters: Map<RosterKey, List<RosterPlayer>> = emptyMap(),
    val matches: Map<String, List<Match>> = emptyMap(),
    val draftValues: Map<DraftKey, Map<Int, MatchDraftFieldValues>> = emptyMap(),
)

private fun RepositoryState.withoutActiveDeletionTargets(
    intents: List<DeletionIntent>,
): RepositoryState {
    val activeTournamentIds = intents
        .filter { it.targetType == DeletionTargetType.TOURNAMENT }
        .map { it.targetId }
        .toSet()
    val activeMatchIds = intents
        .filter { it.targetType == DeletionTargetType.MATCH }
        .map { it.targetId }
        .toSet()
    return copy(
        tournaments = tournaments.filterNot { it.id in activeTournamentIds },
        slots = slots.filterKeys { it !in activeTournamentIds },
        rosters = rosters.filterKeys { it.tournamentId !in activeTournamentIds },
        matches = matches
            .filterKeys { it !in activeTournamentIds }
            .mapValues { (_, values) -> values.filterNot { it.id in activeMatchIds } }
            .filterValues { it.isNotEmpty() },
        draftValues = draftValues.filterKeys {
            it.tournamentId !in activeTournamentIds && it.matchId !in activeMatchIds
        },
    )
}

private fun RepositoryState.withTournamentMirror(
    tournament: Tournament,
    slots: List<TeamSlot>,
): RepositoryState {
    return copy(
        tournaments = if (tournaments.any { it.id == tournament.id }) tournaments else tournaments + tournament,
        slots = this.slots + (tournament.id to slots),
    )
}

private data class RosterKey(val tournamentId: String, val slotNumber: Int)
private data class DraftKey(val tournamentId: String, val matchId: String)

private fun com.hoggamers.rankforge.data.local.SyncRevisionEntity.toDomain() = LocalRevisionState(
    localRevision = localRevision,
    baseCloudRevision = baseCloudRevision?.let(::CloudRevision),
)
