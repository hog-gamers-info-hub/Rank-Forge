package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

enum class LobbySlotAnchorSource {
    ML_KIT_SLOT,
    PP_OCR_SLOT,
    TEAM_CROP_CENTER_FALLBACK,
}

/** Evidence from one structural slot-number OCR pass. */
data class LobbySlotAnchorEvidence(
    val rawText: String? = null,
    val detectedSlotNumber: Int? = null,
    val boundingBox: RawOcrBoundingBox? = null,
    val belongsToSlotArea: Boolean = true,
    /** Origin and dimensions of a smaller OCR request, when one was used. */
    val requestOriginX: Int = 0,
    val requestOriginY: Int = 0,
    val requestWidth: Int? = null,
    val requestHeight: Int? = null,
)

/**
 * Resolved row-mapping anchor. The authoritative team slot identity is kept
 * separately from any slot value read by this structural pass.
 */
data class LobbySlotAnchor(
    val authoritativeTeamSlotNumber: Int,
    val detectedSlotNumber: Int?,
    val source: LobbySlotAnchorSource,
    val anchorX: Double?,
    val anchorY: Double,
    val selectedEvidence: LobbySlotAnchorEvidence?,
)

class LobbySlotAnchorResolver {
    fun resolve(
        authoritativeTeamSlotNumber: Int,
        teamCropWidth: Int,
        teamCropHeight: Int,
        mlKitEvidence: LobbySlotAnchorEvidence?,
        ppOcrEvidence: LobbySlotAnchorEvidence?,
    ): LobbySlotAnchor? {
        if (authoritativeTeamSlotNumber !in 1..12 || teamCropWidth <= 0 || teamCropHeight <= 0) {
            return null
        }

        usableCandidate(
            evidence = mlKitEvidence,
            teamCropWidth = teamCropWidth,
            teamCropHeight = teamCropHeight,
        )?.let { candidate ->
            return candidate.toAnchor(
                authoritativeTeamSlotNumber = authoritativeTeamSlotNumber,
                source = LobbySlotAnchorSource.ML_KIT_SLOT,
            )
        }

        usableCandidate(
            evidence = ppOcrEvidence,
            teamCropWidth = teamCropWidth,
            teamCropHeight = teamCropHeight,
        )?.let { candidate ->
            return candidate.toAnchor(
                authoritativeTeamSlotNumber = authoritativeTeamSlotNumber,
                source = LobbySlotAnchorSource.PP_OCR_SLOT,
            )
        }

        return LobbySlotAnchor(
            authoritativeTeamSlotNumber = authoritativeTeamSlotNumber,
            detectedSlotNumber = null,
            source = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
            anchorX = null,
            anchorY = teamCropHeight / 2.0,
            selectedEvidence = null,
        )
    }

    private fun usableCandidate(
        evidence: LobbySlotAnchorEvidence?,
        teamCropWidth: Int,
        teamCropHeight: Int,
    ): Candidate? {
        if (evidence == null || !evidence.belongsToSlotArea) return null
        val boundingBox = evidence.boundingBox ?: return null
        if (!boundingBox.isUsableWithin(teamCropWidth, teamCropHeight)) return null

        val textSlotNumber = evidence.rawText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toIntOrNull()
        if (textSlotNumber != null && textSlotNumber !in 1..12) return null
        if (evidence.detectedSlotNumber != null && evidence.detectedSlotNumber !in 1..12) return null

        val detectedSlotNumber = evidence.detectedSlotNumber ?: textSlotNumber ?: return null
        return Candidate(evidence = evidence, detectedSlotNumber = detectedSlotNumber)
    }

    private data class Candidate(
        val evidence: LobbySlotAnchorEvidence,
        val detectedSlotNumber: Int,
    ) {
        fun toAnchor(
            authoritativeTeamSlotNumber: Int,
            source: LobbySlotAnchorSource,
        ): LobbySlotAnchor {
            val box = requireNotNull(evidence.boundingBox)
            return LobbySlotAnchor(
                authoritativeTeamSlotNumber = authoritativeTeamSlotNumber,
                detectedSlotNumber = detectedSlotNumber,
                source = source,
                anchorX = (box.left + box.right) / 2.0,
                anchorY = (box.top + box.bottom) / 2.0,
                selectedEvidence = evidence,
            )
        }
    }

    private companion object {
        fun RawOcrBoundingBox.isUsableWithin(width: Int, height: Int): Boolean =
            left >= 0 &&
                top >= 0 &&
                right <= width &&
                bottom <= height &&
                right > left &&
                bottom > top
    }
}
