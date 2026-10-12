package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.inject.Inject

internal const val WRITE_MATCH_SNAPSHOT_V4_RPC = "write_match_snapshot_v4"
internal const val FINALIZE_MATCH_SNAPSHOT_V4_RPC = "finalize_match_snapshot_v4"

interface GroupRotationMatchCloudV4RpcInvoker {
    suspend fun write(parameters: GroupRotationV4DraftWriteParameters): GroupRotationV4RpcResponse
    suspend fun finalize(parameters: GroupRotationV4FinalizationParameters): GroupRotationV4RpcResponse
}

/** The isolated V4 invoker. It is intentionally not bound into the active sync graph yet. */
class SupabaseGroupRotationMatchCloudV4RpcInvoker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : GroupRotationMatchCloudV4RpcInvoker {
    override suspend fun write(parameters: GroupRotationV4DraftWriteParameters): GroupRotationV4RpcResponse =
        clientProvider.client.postgrest.rpc(
            WRITE_MATCH_SNAPSHOT_V4_RPC,
            parameters,
        ).decodeSingle()

    override suspend fun finalize(parameters: GroupRotationV4FinalizationParameters): GroupRotationV4RpcResponse =
        clientProvider.client.postgrest.rpc(
            FINALIZE_MATCH_SNAPSHOT_V4_RPC,
            parameters,
        ).decodeSingle()
}

interface GroupRotationMatchCloudV4RemoteDataSource {
    suspend fun writeDraft(request: GroupRotationV4DraftWriteRequest): GroupRotationMatchCloudV4ExecutionResult
    suspend fun finalize(request: GroupRotationV4FinalizationRequest): GroupRotationMatchCloudV4ExecutionResult
}

/** Internal seam for dispatching an already-frozen V4 request payload. */
interface GroupRotationMatchCloudV4PreparedRemoteDataSource {
    suspend fun writeDraftPrepared(
        parameters: GroupRotationV4DraftWriteParameters,
    ): GroupRotationMatchCloudV4ExecutionResult

    suspend fun finalizePrepared(
        parameters: GroupRotationV4FinalizationParameters,
    ): GroupRotationMatchCloudV4ExecutionResult
}

/**
 * Isolated, inactive V4 transport. It never updates Room revision state or the
 * persistent retry queue; callers must provide the same requestId when retrying.
 */
class SupabaseGroupRotationMatchCloudV4RemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val sessionProbe: SupabaseAuthSessionProbe,
    private val rpcInvoker: GroupRotationMatchCloudV4RpcInvoker,
) : GroupRotationMatchCloudV4RemoteDataSource,
    GroupRotationMatchCloudV4PreparedRemoteDataSource {
    override suspend fun writeDraft(
        request: GroupRotationV4DraftWriteRequest,
    ): GroupRotationMatchCloudV4ExecutionResult {
        val mapped = GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament = request.tournament,
            request = request,
        )
        return execute(mapped, request.expectedRevision) { rpcInvoker.write(it) }
    }

    override suspend fun finalize(
        request: GroupRotationV4FinalizationRequest,
    ): GroupRotationMatchCloudV4ExecutionResult {
        val mapped = GroupRotationMatchCloudV4Mapper.mapFinalization(
            tournament = request.tournament,
            request = request,
        )
        return execute(mapped, request.expectedRevision) { rpcInvoker.finalize(it) }
    }

    override suspend fun writeDraftPrepared(
        parameters: GroupRotationV4DraftWriteParameters,
    ): GroupRotationMatchCloudV4ExecutionResult = executePrepared(
        parameters = parameters,
        expectedRevision = parameters.expectedRevision,
        invoke = { rpcInvoker.write(it) },
    )

    override suspend fun finalizePrepared(
        parameters: GroupRotationV4FinalizationParameters,
    ): GroupRotationMatchCloudV4ExecutionResult = executePrepared(
        parameters = parameters,
        expectedRevision = parameters.expectedRevision,
        invoke = { rpcInvoker.finalize(it) },
    )

    private suspend fun <P> execute(
        mapped: GroupRotationV4MappingResult<P>,
        expectedRevision: Int,
        invoke: suspend (P) -> GroupRotationV4RpcResponse,
    ): GroupRotationMatchCloudV4ExecutionResult {
        if (mapped is GroupRotationV4MappingResult.Invalid) {
            return GroupRotationMatchCloudV4ExecutionResult.Failure(
                category = GroupRotationMatchCloudV4FailureCategory.VALIDATION,
                outcome = "validation_failure",
            )
        }
        return executePrepared(
            parameters = (mapped as GroupRotationV4MappingResult.Success<P>).parameters,
            expectedRevision = expectedRevision,
            invoke = invoke,
        )
    }

    private suspend fun <P> executePrepared(
        parameters: P,
        expectedRevision: Int,
        invoke: suspend (P) -> GroupRotationV4RpcResponse,
    ): GroupRotationMatchCloudV4ExecutionResult {
        if (!config.isConfigured) {
            return GroupRotationMatchCloudV4ExecutionResult.Failure(
                category = GroupRotationMatchCloudV4FailureCategory.VALIDATION,
            )
        }
        return try {
            sessionProbe.awaitInitialization()
            if (!sessionProbe.currentReadiness().allowsAuthenticatedState()) {
                GroupRotationMatchCloudV4ExecutionResult.Failure(
                    category = GroupRotationMatchCloudV4FailureCategory.AUTHENTICATION,
                    outcome = "authentication_required",
                )
            } else {
                mapResponse(
                    response = invoke(parameters),
                    expectedRevision = expectedRevision,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            GroupRotationMatchCloudV4ExecutionResult.Failure(
                category = throwable.toGroupRotationMatchCloudV4FailureCategory(),
            )
        }
    }

    private fun mapResponse(
        response: GroupRotationV4RpcResponse,
        expectedRevision: Int,
    ): GroupRotationMatchCloudV4ExecutionResult =
        when (response.outcome) {
            "success" -> response.revision
                ?.takeIf { it > 0 }
                ?.let { GroupRotationMatchCloudV4ExecutionResult.Success(it) }
                ?: GroupRotationMatchCloudV4ExecutionResult.Failure(
                    category = GroupRotationMatchCloudV4FailureCategory.VALIDATION,
                    outcome = "success",
                    cloudRevision = response.revision,
                )
            "authentication_required" -> failure(response, GroupRotationMatchCloudV4FailureCategory.AUTHENTICATION)
            "unauthorized" -> failure(response, GroupRotationMatchCloudV4FailureCategory.AUTHORIZATION)
            "stale_write" -> failure(
                response,
                GroupRotationMatchCloudV4FailureCategory.CONFLICT,
                expectedRevision = expectedRevision,
                staleConflict = true,
            )
            "missing_revision" -> failure(response, GroupRotationMatchCloudV4FailureCategory.CONFLICT)
            "already_finalized" -> failure(response, GroupRotationMatchCloudV4FailureCategory.CONFLICT)
            "validation_failure", "missing_data" -> failure(response, GroupRotationMatchCloudV4FailureCategory.VALIDATION)
            else -> failure(response, GroupRotationMatchCloudV4FailureCategory.UNKNOWN)
        }

    private fun failure(
        response: GroupRotationV4RpcResponse,
        category: GroupRotationMatchCloudV4FailureCategory,
        expectedRevision: Int? = null,
        staleConflict: Boolean = false,
    ) = GroupRotationMatchCloudV4ExecutionResult.Failure(
        category = category,
        outcome = response.outcome,
        conflict = when {
            response.outcome == "missing_revision" -> RevisionConflict.MissingRevision
            staleConflict && expectedRevision != null && expectedRevision > 0 &&
                response.revision != null && response.revision > 0 ->
                RevisionConflict.StaleWrite(
                    expectedRevision = CloudRevision(expectedRevision),
                    currentCloudRevision = CloudRevision(response.revision),
                )
            else -> null
        },
        cloudRevision = response.revision,
    )
}

internal fun Throwable.toGroupRotationMatchCloudV4FailureCategory(): GroupRotationMatchCloudV4FailureCategory {
    val description = generateSequence(this) { it.cause }
        .joinToString(" ") { it.message.orEmpty() }
        .lowercase()
    return when {
        "42501" in description ||
            "row-level security" in description ||
            "permission denied" in description ||
            "forbidden" in description ||
            "403" in description -> GroupRotationMatchCloudV4FailureCategory.AUTHORIZATION
        "401" in description || "unauthorized" in description || "jwt" in description ->
            GroupRotationMatchCloudV4FailureCategory.AUTHENTICATION
        "violates" in description || "invalid" in description || "235" in description ||
            "bad request" in description -> GroupRotationMatchCloudV4FailureCategory.VALIDATION
        this is IOException ||
            this is ConnectException ||
            this is UnknownHostException ||
            "timeout" in description ||
            "network" in description ||
            "connection" in description -> GroupRotationMatchCloudV4FailureCategory.NETWORK
        else -> GroupRotationMatchCloudV4FailureCategory.UNKNOWN
    }
}
