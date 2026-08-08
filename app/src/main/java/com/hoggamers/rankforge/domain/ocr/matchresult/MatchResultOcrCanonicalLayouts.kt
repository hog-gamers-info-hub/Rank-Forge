package com.hoggamers.rankforge.domain.ocr.matchresult

data class MatchResultOcrCanonicalLayout(
    val width: Double,
    val height: Double,
    val fields: List<MatchResultOcrCanonicalField>,
)

data class MatchResultOcrCanonicalField(
    val id: String,
    val type: MatchResultOcrFieldType,
    val position: Int?,
    val visualRow: MatchResultOcrVisualRow?,
    val slot: Int?,
    val rect: MatchResultOcrRect,
)

object MatchResultOcrCanonicalLayouts {
    val upper: MatchResultOcrCanonicalLayout = buildUpperLayout()
    val lower: MatchResultOcrCanonicalLayout = buildLowerLayout()

    private fun buildUpperLayout(): MatchResultOcrCanonicalLayout {
        val playerColumns = mapOf(
            "L1" to HorizontalRange(96.0, 233.0),
            "L2" to HorizontalRange(380.0, 520.0),
            "R1" to HorizontalRange(720.0, 865.0),
            "R2" to HorizontalRange(956.0, 1076.0),
        )
        val killColumns = mapOf(
            "L1" to HorizontalRange(233.0, 252.0),
            "L2" to HorizontalRange(520.0, 541.0),
            "R1" to HorizontalRange(865.0, 885.0),
            "R2" to HorizontalRange(1076.0, 1096.0),
        )
        val rows = mapOf(
            1 to RowBands(VerticalRange(8.0, 38.0), VerticalRange(45.0, 76.0)),
            2 to RowBands(VerticalRange(99.0, 129.0), VerticalRange(140.0, 170.0)),
            3 to RowBands(VerticalRange(191.0, 221.0), VerticalRange(233.0, 263.0)),
            4 to RowBands(VerticalRange(290.0, 321.0), VerticalRange(331.0, 362.0)),
            5 to RowBands(VerticalRange(382.0, 412.0), VerticalRange(423.0, 453.0)),
            6 to RowBands(VerticalRange(7.0, 35.0), VerticalRange(42.0, 70.0)),
            7 to RowBands(VerticalRange(88.0, 116.0), VerticalRange(121.0, 149.0)),
            8 to RowBands(VerticalRange(167.0, 195.0), VerticalRange(201.0, 229.0)),
            9 to RowBands(VerticalRange(248.0, 276.0), VerticalRange(282.0, 310.0)),
            10 to RowBands(VerticalRange(329.0, 357.0), VerticalRange(363.0, 391.0)),
        )
        val placements = mapOf(
            1 to MatchResultOcrRect(10.0, 8.0, 61.0, 73.0),
            2 to MatchResultOcrRect(13.0, 99.0, 63.0, 164.0),
            3 to MatchResultOcrRect(12.0, 191.0, 63.0, 258.0),
            4 to MatchResultOcrRect(23.0, 293.0, 50.0, 349.0),
            5 to MatchResultOcrRect(23.0, 383.0, 51.0, 442.0),
            6 to MatchResultOcrRect(672.0, 21.0, 701.0, 57.0),
            7 to MatchResultOcrRect(673.0, 99.0, 701.0, 137.0),
            8 to MatchResultOcrRect(673.0, 178.0, 701.0, 216.0),
            9 to MatchResultOcrRect(673.0, 258.0, 701.0, 296.0),
            10 to MatchResultOcrRect(668.0, 338.0, 708.0, 376.0),
        )

        val fields = buildList {
            (1..10).forEach { position ->
                add(
                    MatchResultOcrCanonicalField(
                        id = "PLACEMENT_$position",
                        type = MatchResultOcrFieldType.PLACEMENT,
                        position = position,
                        visualRow = null,
                        slot = null,
                        rect = placements.getValue(position),
                    ),
                )

                val columnKeys = if (position <= 5) {
                    listOf("L1", "L1", "L2", "L2")
                } else {
                    listOf("R1", "R1", "R2", "R2")
                }
                val row = rows.getValue(position)
                val verticalRanges = listOf(row.upper, row.lower, row.upper, row.lower)
                (1..4).forEach { slot ->
                    val vertical = verticalRanges[slot - 1]
                    val player = playerColumns.getValue(columnKeys[slot - 1])
                    val kill = killColumns.getValue(columnKeys[slot - 1])
                    add(
                        MatchResultOcrCanonicalField(
                            id = "PLAYER_${position}_$slot",
                            type = MatchResultOcrFieldType.PLAYER,
                            position = position,
                            visualRow = null,
                            slot = slot,
                            rect = MatchResultOcrRect(
                                player.left,
                                vertical.top,
                                player.right,
                                vertical.bottom,
                            ),
                        ),
                    )
                    add(
                        MatchResultOcrCanonicalField(
                            id = "KILL_${position}_$slot",
                            type = MatchResultOcrFieldType.KILL,
                            position = position,
                            visualRow = null,
                            slot = slot,
                            rect = MatchResultOcrRect(
                                kill.left,
                                vertical.top,
                                kill.right,
                                vertical.bottom,
                            ),
                        ),
                    )
                }
            }
        }
        check(fields.size == 90)
        return MatchResultOcrCanonicalLayout(1156.0, 456.0, fields)
    }

    private fun buildLowerLayout(): MatchResultOcrCanonicalLayout {
        val rows = mapOf(
            MatchResultOcrVisualRow.A to listOf(
                VerticalRange(297.0, 326.0),
                VerticalRange(331.0, 360.0),
                VerticalRange(297.0, 326.0),
                VerticalRange(331.0, 360.0),
            ),
            MatchResultOcrVisualRow.B to listOf(
                VerticalRange(378.0, 407.0),
                VerticalRange(412.0, 441.0),
                VerticalRange(378.0, 407.0),
                VerticalRange(412.0, 441.0),
            ),
        )
        val playerColumns = listOf(
            HorizontalRange(725.0, 869.0),
            HorizontalRange(725.0, 869.0),
            HorizontalRange(959.0, 1074.0),
            HorizontalRange(959.0, 1074.0),
        )
        val killColumns = listOf(
            HorizontalRange(865.0, 884.0),
            HorizontalRange(865.0, 884.0),
            HorizontalRange(1074.0, 1090.0),
            HorizontalRange(1074.0, 1090.0),
        )
        val placements = mapOf(
            MatchResultOcrVisualRow.A to MatchResultOcrRect(675.0, 297.0, 710.0, 363.0),
            MatchResultOcrVisualRow.B to MatchResultOcrRect(675.0, 377.0, 710.0, 445.0),
        )

        val fields = buildList {
            MatchResultOcrVisualRow.entries.forEach { visualRow ->
                add(
                    MatchResultOcrCanonicalField(
                        id = "LOWER_ROW_${visualRow.name}_PLACEMENT",
                        type = MatchResultOcrFieldType.PLACEMENT,
                        position = null,
                        visualRow = visualRow,
                        slot = null,
                        rect = placements.getValue(visualRow),
                    ),
                )
                (1..4).forEach { slot ->
                    val vertical = rows.getValue(visualRow)[slot - 1]
                    val player = playerColumns[slot - 1]
                    val kill = killColumns[slot - 1]
                    add(
                        MatchResultOcrCanonicalField(
                            id = "LOWER_ROW_${visualRow.name}_PLAYER_$slot",
                            type = MatchResultOcrFieldType.PLAYER,
                            position = null,
                            visualRow = visualRow,
                            slot = slot,
                            rect = MatchResultOcrRect(
                                player.left,
                                vertical.top,
                                player.right,
                                vertical.bottom,
                            ),
                        ),
                    )
                    add(
                        MatchResultOcrCanonicalField(
                            id = "LOWER_ROW_${visualRow.name}_KILL_$slot",
                            type = MatchResultOcrFieldType.KILL,
                            position = null,
                            visualRow = visualRow,
                            slot = slot,
                            rect = MatchResultOcrRect(
                                kill.left,
                                vertical.top,
                                kill.right,
                                vertical.bottom,
                            ),
                        ),
                    )
                }
            }
        }
        check(fields.size == 18)
        return MatchResultOcrCanonicalLayout(1156.0, 452.0, fields)
    }

    private data class HorizontalRange(val left: Double, val right: Double)

    private data class VerticalRange(val top: Double, val bottom: Double)

    private data class RowBands(val upper: VerticalRange, val lower: VerticalRange)
}
