package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.inject.Inject

internal const val WRITE_GROUP_ROTATION_UUID_IDENTITY_SNAPSHOT_V4_RPC =
    "write_group_rotation_uuid_identity_snapshot_v4"

interface GroupRotationTeamIdentityCloudV4RpcInvoker {
    suspend fun write(
        parameters: GroupRotationTeamIdentityV4WriteParameters,
    ): GroupRotationTeamIdentityV4RpcResponse
}

/** The V4 identity invoker is intentionally not wired into active sync yet. */
class SupabaseGroupRotationTeamIdentityCloudV4RpcInvoker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : GroupRotationTeamIdentityCloudV4RpcInvoker {
    override suspend fun write(
        parameters: GroupRotationTeamIdentityV4WriteParameters,
    ): GroupRotationTeamIdentityV4RpcResponse = clientProvider.client.postgrest.rpc(
        WRITE_GROUP_ROTATION_UUID_IDENTITY_SNAPSHOT_V4_RPC,
        parameters,
    ).decodeSingle()
}

interface GroupRotationTeamIdentityCloudV4RemoteDataSource {
    suspend fun write(
        request: GroupRotationTeamIdentityV4WriteRequest,
    ): GroupRotationTeamIdentityV4ExecutionResult
}

/**
 * Internal upload seam for a request that has already been mapped and frozen.
 * The coordinator uses this so retries never rebuild a payload from mutable
 * Room identity state.
 */
interface GroupRotationTeamIdentityCloudV4PreparedRemoteDataSource {
    suspend fun writePrepared(
        parameters: GroupRotationTeamIdentityV4WriteParameters,
    ): GroupRotationTeamIdentityV4ExecutionResult
}

/**
 * Isolated V4 identity transport. It does not update Room revisions, enqueue
 * retries, or participate in active application synchronization.
 */
class SupabaseGroupRotationTeamIdentityCloudV4RemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val sessionProbe: SupabaseAuthSessionProbe,
    private val identityRepository: GroupRotationTeamIdentityLocalRepository,
    private val rpcInvoker: GroupRotationTeamIdentityCloudV4RpcInvoker,
) : GroupRotationTeamIdentityCloudV4RemoteDataSource,
    GroupRotationTeamIdentityCloudV4PreparedRemoteDataSource {
    private val mapper = GroupRotationTeamIdentityCloudV4Mapper(identityRepository)

    override suspend fun write(
        request: GroupRotationTeamIdentityV4WriteRequest,
    ): GroupRotationTeamIdentityV4ExecutionResult {
        val mapped = try {
            mapper.map(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return GroupRotationTeamIdentityV4ExecutionResult.Failure(
                category = throwable.toGroupRotationTeamIdentityV4FailureCategory(),
            )
        }

        if (mapped is GroupRotationTeamIdentityV4MappingResult.Invalid) {
            return GroupRotationTeamIdentityV4ExecutionResult.Failure(
                category = GroupRotationTeamIdentityV4FailureCategory.VALIDATION,
                outcome = "validation_failure",
            )
        }
        return writePrepared((mapped as GroupRotationTeamIdentityV4MappingResult.Success).parameters)
    }

    override suspend fun writePrepared(
        parameters: GroupRotationTeamIdentityV4WriteParameters,
    ): GroupRotationTeamIdentityV4ExecutionResult {
        if (!config.isConfigured) {
            return GroupRotationTeamIdentityV4ExecutionResult.Failure(
                category = GroupRotationTeamIdentityV4FailureCategory.VALIDATION,
                outcome = "validation_failure",
            )
        }

        return try {
            sessionProbe.awaitInitialization()
            if (!sessionProbe.currentReadiness().allowsAuthenticatedState()) {
                GroupRotationTeamIdentityV4ExecutionResult.Failure(
                    category = GroupRotationTeamIdentityV4FailureCategory.AUTHENTICATION,
                    outcome = "authentication_required",
                )
            } else {
                mapResponse(
                    response = rpcInvoker.write(parameters),
                    expectedRevision = parameters.expectedRevision,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            GroupRotationTeamIdentityV4ExecutionResult.Failure(
                category = throwable.toGroupRotationTeamIdentityV4FailureCategory(),
            )
        }
    }

    private fun mapResponse(
        response: GroupRotationTeamIdentityV4RpcResponse,
        expectedRevision: Int,
    ): GroupRotationTeamIdentityV4ExecutionResult = when (response.outcome) {
        "success" -> response.revision
            ?.takeIf { it > 0 }
            ?.let { GroupRotationTeamIdentityV4ExecutionResult.Success(it) }
            ?: GroupRotationTeamIdentityV4ExecutionResult.Failure(
                category = GroupRotationTeamIdentityV4FailureCategory.VALIDATION,
                outcome = "success",
                cloudRevision = response.revision,
            )
        "authentication_required" -> failure(
            response,
            GroupRotationTeamIdentityV4FailureCategory.AUTHENTICATION,
        )
        "unauthorized" -> failure(response, GroupRotationTeamIdentityV4FailureCategory.AUTHORIZATION)
        "stale_write" -> failure(
            response = response,
            category = GroupRotationTeamIdentityV4FailureCategory.CONFLICT,
            expectedRevision = expectedRevision,
            staleConflict = true,
        )
        "missing_revision" -> failure(response, GroupRotationTeamIdentityV4FailureCategory.CONFLICT)
        "validation_failure", "missing_data" -> failure(
            response,
            GroupRotationTeamIdentityV4FailureCategory.VALIDATION,
        )
        else -> failure(response, GroupRotationTeamIdentityV4FailureCategory.UNKNOWN)
    }

    private fun failure(
        response: GroupRotationTeamIdentityV4RpcResponse,
        category: GroupRotationTeamIdentityV4FailureCategory,
        expectedRevision: Int? = null,
        staleConflict: Boolean = false,
    ) = GroupRotationTeamIdentityV4ExecutionResult.Failure(
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

internal fun Throwable.toGroupRotationTeamIdentityV4FailureCategory(): GroupRotationTeamIdentityV4FailureCategory {
    val description = generateSequence(this) { it.cause }
        .joinToString(" ") { it.message.orEmpty() }
        .lowercase()
    return when {
        "42501" in description ||
            "row-level security" in description ||
            "permission denied" in description ||
            "forbidden" in description ||
            "403" in description -> GroupRotationTeamIdentityV4FailureCategory.AUTHORIZATION
        "401" in description || "unauthorized" in description || "jwt" in description ->
            GroupRotationTeamIdentityV4FailureCategory.AUTHENTICATION
        "violates" in description || "invalid" in description || "235" in description ||
            "bad request" in description -> GroupRotationTeamIdentityV4FailureCategory.VALIDATION
        this is IOException ||
            this is ConnectException ||
            this is UnknownHostException ||
            "timeout" in description ||
            "network" in description ||
            "connection" in description -> GroupRotationTeamIdentityV4FailureCategory.NETWORK
        else -> GroupRotationTeamIdentityV4FailureCategory.UNKNOWN
    }
}
