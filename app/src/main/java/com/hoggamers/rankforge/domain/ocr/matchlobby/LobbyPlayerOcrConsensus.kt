package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.matching.PlayerNameSimilarityMatcher
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import java.text.Normalizer
import java.util.Locale

enum class LobbyPlayerOcrEngine {
    ML_KIT,
    PP_OCRV6,
}

enum class LobbyPlayerNameOcrSource {
    PP_PRIMARY,
    ML_FALLBACK,
    MISSING,
}

data class LobbyPlayerOcrTextFragment(
    val text: String,
    val boundingBox: RawOcrBoundingBox? = null,
    val confidence: Float? = null,
)

data class LobbyPlayerOcrEngineEvidence(
    val engine: LobbyPlayerOcrEngine,
    val rawText: String,
    val candidateText: String?,
    val blocks: List<RawOcrBlock> = emptyList(),
    val fragments: List<LobbyPlayerOcrTextFragment> = emptyList(),
    val failureType: String? = null,
    val failureMessage: String? = null,
)

enum class LobbyPlayerOcrConsensusStatus {
    BOTH_EMPTY,
    AGREED,
    ML_ONLY,
    PP_ONLY,
    SIMILAR_PP_SELECTED,
    DISAGREEMENT_PP_SELECTED,
}

data class LobbyPlayerOcrConsensus(
    val resolvedText: String?,
    val status: LobbyPlayerOcrConsensusStatus,
    val similarityScore: Int?,
)

data class LobbyPlayerDualOcrResult(
    val teamSlotNumber: Int,
    val row: LobbyPlayerRow,
    val rowBounds: LobbyPlayerRowCropBounds,
    val slotAnchorSource: LobbySlotAnchorSource,
    val slotAnchorY: Double,
    /** Null means ML row OCR was not run; a non-null empty/failed evidence records fallback execution. */
    val mlEvidence: LobbyPlayerOcrEngineEvidence? = null,
    val ppEvidence: LobbyPlayerOcrEngineEvidence,
    val resolvedText: String? = null,
    val consensusStatus: LobbyPlayerOcrConsensusStatus = LobbyPlayerOcrConsensusStatus.BOTH_EMPTY,
    val similarityScore: Int? = null,
    val selectedSource: LobbyPlayerNameOcrSource = when {
        !ppEvidence.candidateText.isNullOrBlank() -> LobbyPlayerNameOcrSource.PP_PRIMARY
        !mlEvidence?.candidateText.isNullOrBlank() -> LobbyPlayerNameOcrSource.ML_FALLBACK
        else -> LobbyPlayerNameOcrSource.MISSING
    },
    val finalText: String? = resolvedText,
)

object LobbyPlayerOcrComparisonNormalizer {
    fun normalize(value: String?): String? = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFC) }
        ?.trim()
        ?.replace(WHITESPACE_REGEX, " ")
        ?.takeIf { it.isNotEmpty() }

    fun normalizeForSimilarity(value: String?): String? = normalize(value)?.lowercase(Locale.ROOT)

    private val WHITESPACE_REGEX = Regex("\\s+")
}

object LobbyPlayerOcrConsensusResolver {
    const val SIMILARITY_THRESHOLD = 85

    fun resolve(
        mlCandidate: String?,
        ppCandidate: String?,
    ): LobbyPlayerOcrConsensus {
        val ml = mlCandidate?.takeIf { it.isNotBlank() }
        val pp = ppCandidate?.takeIf { it.isNotBlank() }
        if (ml == null && pp == null) {
            return LobbyPlayerOcrConsensus(
                resolvedText = null,
                status = LobbyPlayerOcrConsensusStatus.BOTH_EMPTY,
                similarityScore = null,
            )
        }
        if (ml == null) {
            return LobbyPlayerOcrConsensus(
                resolvedText = pp,
                status = LobbyPlayerOcrConsensusStatus.PP_ONLY,
                similarityScore = null,
            )
        }
        if (pp == null) {
            return LobbyPlayerOcrConsensus(
                resolvedText = ml,
                status = LobbyPlayerOcrConsensusStatus.ML_ONLY,
                similarityScore = null,
            )
        }

        val normalizedMl = requireNotNull(LobbyPlayerOcrComparisonNormalizer.normalize(ml))
        val normalizedPp = requireNotNull(LobbyPlayerOcrComparisonNormalizer.normalize(pp))
        if (normalizedMl == normalizedPp) {
            return LobbyPlayerOcrConsensus(
                resolvedText = ml,
                status = LobbyPlayerOcrConsensusStatus.AGREED,
                similarityScore = 100,
            )
        }

        val similarity = PlayerNameSimilarityMatcher.similarityScoreForComparison(
            requireNotNull(LobbyPlayerOcrComparisonNormalizer.normalizeForSimilarity(ml)),
            requireNotNull(LobbyPlayerOcrComparisonNormalizer.normalizeForSimilarity(pp)),
        )
        return LobbyPlayerOcrConsensus(
            resolvedText = pp,
            status = if (similarity > SIMILARITY_THRESHOLD) {
                LobbyPlayerOcrConsensusStatus.SIMILAR_PP_SELECTED
            } else {
                LobbyPlayerOcrConsensusStatus.DISAGREEMENT_PP_SELECTED
            },
            similarityScore = similarity,
        )
    }
}
