package com.hoggamers.rankforge.domain.ocr.matchresult

import java.util.concurrent.CancellationException

data class MatchResultPositionKillFallbackTarget(
    val position: Int,
    val missingSlots: Set<Int>,
)

/** Coordinates best-effort Position PP recovery and merges only strong missing-kill evidence. */
class MatchResultPositionKillFallbackMerger {
    suspend fun recover(
        baseSemantics: List<MatchResultPositionSemanticResult>,
        availablePositions: Set<Int>,
        recoverPosition: suspend (MatchResultPositionKillFallbackTarget) -> MatchResultPositionSemanticResult?,
    ): List<MatchResultPositionSemanticResult> {
        val targets = baseSemantics
            .mapNotNull { semantic ->
                val missingSlots = semantic.row?.playerSlots.orEmpty()
                    .filter { slot ->
                        slot.player.resolvedText.isNotBlank() && slot.kill.resolvedText.isBlank()
                    }
                    .map { it.slot }
                    .toSet()
                if (missingSlots.isEmpty() || semantic.position !in availablePositions) {
                    null
                } else {
                    MatchResultPositionKillFallbackTarget(semantic.position, missingSlots)
                }
            }
            .sortedBy { it.position }

        if (targets.isEmpty()) return baseSemantics

        val recoveredSemantics = baseSemantics.toMutableList()
        targets.forEach { target ->
            val fallback = try {
                recoverPosition(target)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            } ?: return@forEach

            val index = recoveredSemantics.indexOfFirst { it.position == target.position }
            if (index >= 0) {
                recoveredSemantics[index] = merge(
                    base = recoveredSemantics[index],
                    fallback = fallback,
                    allowedTargetSlots = target.missingSlots,
                )
            }
        }
        return recoveredSemantics
    }

    fun merge(
        base: MatchResultPositionSemanticResult,
        fallback: MatchResultPositionSemanticResult,
        allowedTargetSlots: Set<Int>,
    ): MatchResultPositionSemanticResult {
        if (
            allowedTargetSlots.isEmpty() ||
            base.role != fallback.role ||
            base.position != fallback.position
        ) {
            return base
        }

        val basePlayers = base.fields
            .filter { it.type == MatchResultOcrFieldType.PLAYER }
            .associateBy { it.slot }
        val baseKills = base.fields
            .filter { it.type == MatchResultOcrFieldType.KILL }
            .associateBy { it.slot }
        val fallbackKills = fallback.fields
            .filter { it.type == MatchResultOcrFieldType.KILL }
            .associateBy { it.slot }
        val recoveredKills = allowedTargetSlots.mapNotNull { slot ->
            val basePlayer = basePlayers[slot]
            val baseKill = baseKills[slot]
            val evidence = fallback.basicKillEvidence[slot]
            val fallbackKill = fallbackKills[slot]
            val strongEvidence = evidence?.takeIf { it.isStrongNumericKill() }
            if (
                basePlayer == null || basePlayer.resolvedText.isBlank() ||
                baseKill == null || baseKill.resolvedText.isNotBlank() ||
                fallbackKill == null || strongEvidence == null ||
                fallback.row?.playerSlots?.any { it.slot == slot } != true
            ) {
                return@mapNotNull null
            }
            val kill = strongEvidence.kill ?: return@mapNotNull null
            slot to baseKill.copy(
                ocrText = fallbackKill.ocrText.ifBlank { strongEvidence.rawText },
                resolvedText = kill.toString(),
                status = strongEvidence.toPositionPpKillStatus(),
            )
        }.toMap()

        if (recoveredKills.isEmpty()) return base

        val fields = base.fields.map { field ->
            recoveredKills[field.slot]?.takeIf { field.type == MatchResultOcrFieldType.KILL } ?: field
        }
        val baseRow = base.row ?: return base
        val row = runCatching {
            MatchResultOcrRowAssembler.assemble(
                position = base.position,
                source = baseRow.source,
                fields = fields,
                visualRow = baseRow.placement.visualRow,
            )
        }.getOrNull() ?: return base
        val presentPlayers = fields.filter {
            it.type == MatchResultOcrFieldType.PLAYER && it.resolvedText.isNotBlank()
        }
        val allPresentPlayersHaveKills = presentPlayers.all { player ->
            fields.firstOrNull {
                it.type == MatchResultOcrFieldType.KILL && it.slot == player.slot
            }?.resolvedText?.isNotBlank() == true
        }
        val noKillConflict = base.killVerifications.values.none {
            it is MatchResultNumericVerification.Conflict
        }
        val placementNotConflict = base.placementVerification !is MatchResultNumericVerification.Conflict
        return base.copy(
            fields = fields,
            row = row,
            isAutoAcceptable = base.structuralIdentityValid && placementNotConflict &&
                allPresentPlayersHaveKills && noKillConflict,
        )
    }
}

private fun ParsedEliminationText?.isStrongNumericKill(): Boolean =
    this?.markerMatched == true && (
        prefixType == MatchResultEliminationPrefixType.EXPLICIT_NUMERIC && kill != null ||
            prefixType == MatchResultEliminationPrefixType.O_NORMALIZED && kill == 0
        )

private fun ParsedEliminationText.toPositionPpKillStatus(): MatchResultOcrFieldStatus = when (prefixType) {
    MatchResultEliminationPrefixType.O_NORMALIZED -> MatchResultOcrFieldStatus.O_NORMALIZED_TO_0
    MatchResultEliminationPrefixType.EXPLICIT_NUMERIC -> MatchResultOcrFieldStatus.DIRECT_NUMERIC
    MatchResultEliminationPrefixType.EMPTY_PREFIX -> error("Marker-only evidence is not recoverable.")
}
