package com.hoggamers.rankforge.domain.sync

/** A positive, server-issued revision. Zero is only an explicit new-record expectation. */
@JvmInline
value class CloudRevision(val value: Int) {
    init {
        require(value > 0) { "Cloud revisions must be positive." }
    }
}

data class LocalRevisionState(
    val localRevision: Int?,
    val baseCloudRevision: CloudRevision?,
) {
    val expectedCloudRevision: Int? get() = baseCloudRevision?.value

    companion object {
        val Missing = LocalRevisionState(localRevision = null, baseCloudRevision = null)
        val New = LocalRevisionState(localRevision = 1, baseCloudRevision = null)
    }
}

sealed interface RevisionConflict {
    data object MissingRevision : RevisionConflict
    data class StaleWrite(
        val expectedRevision: CloudRevision,
        val currentCloudRevision: CloudRevision,
    ) : RevisionConflict
    data class LocalCloudDivergence(
        val baseRevision: CloudRevision,
        val localRevision: Int,
        val cloudRevision: CloudRevision,
    ) : RevisionConflict
}

fun LocalRevisionState.detectDivergence(cloudRevision: CloudRevision): RevisionConflict? {
    val base = baseCloudRevision ?: return null
    val local = localRevision ?: return RevisionConflict.MissingRevision
    return if (local > base.value && cloudRevision.value != base.value) {
        RevisionConflict.LocalCloudDivergence(base, local, cloudRevision)
    } else {
        null
    }
}

/** Zero is an explicit create expectation, never a persisted cloud revision. */
fun LocalRevisionState.expectedRevisionForWrite(): Int? = when {
    baseCloudRevision != null -> baseCloudRevision.value
    localRevision == 1 -> 0
    else -> null
}

fun RevisionConflict.queueFailureCategory(): String = when (this) {
    RevisionConflict.MissingRevision -> "MISSING_REVISION"
    is RevisionConflict.StaleWrite -> "STALE_WRITE_CONFLICT"
    is RevisionConflict.LocalCloudDivergence -> "LOCAL_CLOUD_DIVERGENCE"
}
