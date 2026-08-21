package com.hoggamers.rankforge.domain.tournament

enum class MatchParticipationStatus {
    PARTICIPATED,
    NO_SHOW,
}

data class MatchParticipantResult(
    val teamSlotNumber: Int,
    val participationStatus: MatchParticipationStatus,
    val placement: Int?,
    val kills: Int,
) {
    init {
        require(teamSlotNumber in TeamSlot.SLOT_NUMBERS) {
            "Team slot number must be between 1 and 12."
        }
        require(kills >= 0) { "Kills cannot be negative." }
        when (participationStatus) {
            MatchParticipationStatus.PARTICIPATED -> {
                require(placement != null && placement >= 1) {
                    "Participated results require a positive placement."
                }
            }
            MatchParticipationStatus.NO_SHOW -> {
                require(placement == null) { "No-show results cannot have a placement." }
                require(kills == 0) { "No-show results must have zero kills." }
            }
        }
    }

    val isNoShow: Boolean
        get() = participationStatus == MatchParticipationStatus.NO_SHOW

    val placementPoints: Int
        get() = if (isNoShow) 0 else PositionPointsEngine()(requireNotNull(placement))

    val killPoints: Int
        get() = if (isNoShow) 0 else KillPointsEngine()(kills)

    val totalPoints: Int
        get() = if (isNoShow) 0 else MatchTotalEngine()(requireNotNull(placement), kills)
}
