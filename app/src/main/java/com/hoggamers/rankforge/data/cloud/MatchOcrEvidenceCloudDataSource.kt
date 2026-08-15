package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MatchOcrEvidenceCloudFailure {
    MISSING_AUTH_SESSION,
    NETWORK,
    AUTHORIZATION,
    READ_FAILED,
    WRITE_FAILED,
}

sealed interface MatchOcrEvidenceCloudResult {
    data object Success : MatchOcrEvidenceCloudResult
    data class Failed(val failure: MatchOcrEvidenceCloudFailure) : MatchOcrEvidenceCloudResult
}

sealed interface MatchOcrEvidenceCloudReadResult {
    data class Success(val snapshots: List<MatchOcrEvidenceCloudSnapshot>) : MatchOcrEvidenceCloudReadResult
    data class Failed(val failure: MatchOcrEvidenceCloudFailure) : MatchOcrEvidenceCloudReadResult
}

@Serializable
data class MatchOcrEvidenceCloudPayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("source_screenshot_id") val sourceScreenshotId: String?,
    @SerialName("preserved_at") val preservedAt: String,
    val provenance: String,
)

data class MatchOcrEvidenceCloudSnapshot(
    val evidence: MatchOcrEvidenceCloudPayload,
    val rows: List<FinalizedMatchOcrEvidenceRowUploadPayload>,
    val correctionSnapshots: List<FinalizedMatchOcrCorrectionSnapshotUploadPayload>,
)

interface MatchOcrEvidenceCloudDataSource {
    suspend fun upsert(
        snapshots: List<FinalizedMatchOcrEvidenceUploadPayload>,
    ): MatchOcrEvidenceCloudResult

    suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchOcrEvidenceCloudReadResult
}

@Singleton
class SupabaseMatchOcrEvidenceCloudDataSource internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val upsertEvidence: suspend (List<MatchOcrEvidenceCloudPayload>) -> Unit,
    private val deleteRows: suspend (String) -> Unit,
    private val deleteCorrectionSnapshots: suspend (String) -> Unit,
    private val insertRows: suspend (List<FinalizedMatchOcrEvidenceRowUploadPayload>) -> Unit,
    private val insertCorrectionSnapshots: suspend (List<FinalizedMatchOcrCorrectionSnapshotUploadPayload>) -> Unit,
    private val readEvidence: suspend (String) -> List<MatchOcrEvidenceCloudPayload>,
    private val readRows: suspend (String) -> List<FinalizedMatchOcrEvidenceRowUploadPayload>,
    private val readCorrectionSnapshots: suspend (String) -> List<FinalizedMatchOcrCorrectionSnapshotUploadPayload>,
) : MatchOcrEvidenceCloudDataSource {
    @Inject
    constructor(
        config: SupabaseAuthConfig,
        clientProvider: SupabaseClientProvider,
    ) : this(
        isConfigured = { config.isConfigured },
        currentUserId = { clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() } },
        upsertEvidence = { payloads -> clientProvider.client.from(EVIDENCE_TABLE).upsert(payloads) },
        deleteRows = { matchId ->
            clientProvider.client.from(ROW_TABLE).delete {
                filter { eq("match_id", matchId) }
            }
        },
        deleteCorrectionSnapshots = { matchId ->
            clientProvider.client.from(CORRECTION_TABLE).delete {
                filter { eq("match_id", matchId) }
            }
        },
        insertRows = { payloads ->
            if (payloads.isNotEmpty()) clientProvider.client.from(ROW_TABLE).insert(payloads)
        },
        insertCorrectionSnapshots = { payloads ->
            if (payloads.isNotEmpty()) clientProvider.client.from(CORRECTION_TABLE).insert(payloads)
        },
        readEvidence = { tournamentId ->
            clientProvider.client.from(EVIDENCE_TABLE).select {
                filter { eq("tournament_id", tournamentId) }
            }.decodeList()
        },
        readRows = { matchId ->
            clientProvider.client.from(ROW_TABLE).select {
                filter { eq("match_id", matchId) }
            }.decodeList()
        },
        readCorrectionSnapshots = { matchId ->
            clientProvider.client.from(CORRECTION_TABLE).select {
                filter { eq("match_id", matchId) }
            }.decodeList()
        },
    )

    override suspend fun upsert(
        snapshots: List<FinalizedMatchOcrEvidenceUploadPayload>,
    ): MatchOcrEvidenceCloudResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext MatchOcrEvidenceCloudResult.Failed(
            MatchOcrEvidenceCloudFailure.WRITE_FAILED,
        )
        if (currentUserId() == null) return@withContext MatchOcrEvidenceCloudResult.Failed(
            MatchOcrEvidenceCloudFailure.MISSING_AUTH_SESSION,
        )
        try {
            snapshots.forEach { snapshot ->
                upsertEvidence(
                    listOf(
                        MatchOcrEvidenceCloudPayload(
                            matchId = snapshot.matchId,
                            tournamentId = snapshot.tournamentId,
                            sourceScreenshotId = snapshot.sourceScreenshotId,
                            preservedAt = snapshot.preservedAt,
                            provenance = snapshot.provenance,
                        ),
                    ),
                )
                deleteRows(snapshot.matchId)
                deleteCorrectionSnapshots(snapshot.matchId)
                insertRows(snapshot.rows)
                insertCorrectionSnapshots(snapshot.correctionSnapshots)
            }
            MatchOcrEvidenceCloudResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchOcrEvidenceCloudResult.Failed(throwable.toCloudFailure(MatchOcrEvidenceCloudFailure.WRITE_FAILED))
        }
    }

    override suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchOcrEvidenceCloudReadResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext MatchOcrEvidenceCloudReadResult.Failed(
            MatchOcrEvidenceCloudFailure.READ_FAILED,
        )
        if (currentUserId() == null) return@withContext MatchOcrEvidenceCloudReadResult.Failed(
            MatchOcrEvidenceCloudFailure.MISSING_AUTH_SESSION,
        )
        if (matchIds.isEmpty()) return@withContext MatchOcrEvidenceCloudReadResult.Success(emptyList())
        try {
            readEvidence(tournamentId)
                .filter { it.matchId in matchIds }
                .map { evidence ->
                    MatchOcrEvidenceCloudSnapshot(
                        evidence = evidence,
                        rows = readRows(evidence.matchId),
                        correctionSnapshots = readCorrectionSnapshots(evidence.matchId),
                    )
                }
                .let(MatchOcrEvidenceCloudReadResult::Success)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchOcrEvidenceCloudReadResult.Failed(throwable.toCloudFailure(MatchOcrEvidenceCloudFailure.READ_FAILED))
        }
    }

    companion object {
        const val EVIDENCE_TABLE = "match_ocr_evidence"
        const val ROW_TABLE = "match_ocr_row_evidence"
        const val CORRECTION_TABLE = "match_ocr_correction_snapshots"
    }
}

class NoOpMatchOcrEvidenceCloudDataSource : MatchOcrEvidenceCloudDataSource {
    override suspend fun upsert(
        snapshots: List<FinalizedMatchOcrEvidenceUploadPayload>,
    ): MatchOcrEvidenceCloudResult = MatchOcrEvidenceCloudResult.Success

    override suspend fun readByTournamentAndMatchIds(
        tournamentId: String,
        matchIds: Set<String>,
    ): MatchOcrEvidenceCloudReadResult = MatchOcrEvidenceCloudReadResult.Success(emptyList())
}

private fun Throwable.toCloudFailure(
    fallback: MatchOcrEvidenceCloudFailure,
): MatchOcrEvidenceCloudFailure {
    val message = message.orEmpty().lowercase()
    return when {
        this is IOException || message.contains("network") || message.contains("timeout") || message.contains("connection") ->
            MatchOcrEvidenceCloudFailure.NETWORK
        message.contains("401") || message.contains("session") || message.contains("jwt") || message.contains("unauthorized") ->
            MatchOcrEvidenceCloudFailure.MISSING_AUTH_SESSION
        message.contains("403") || message.contains("42501") || message.contains("forbidden") || message.contains("row-level security") ->
            MatchOcrEvidenceCloudFailure.AUTHORIZATION
        else -> fallback
    }
}
