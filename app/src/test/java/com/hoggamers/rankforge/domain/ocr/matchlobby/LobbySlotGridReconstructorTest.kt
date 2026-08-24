package com.hoggamers.rankforge.domain.ocr.matchlobby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbySlotGridReconstructorTest {
    private val reconstructor = LobbySlotGridReconstructor()
    private val exactTwoToOneReconstructor = LobbySlotGridReconstructor(
        LobbyGridGeometryCalibration(columnToRowPitchRatio = 2.0),
    )

    @Test
    fun roleMappingUsesOneGenericFourRoleCycleForAllTwelveSlots() {
        val expectedRoles = listOf(
            LobbySlotGridRole.TOP_LEFT,
            LobbySlotGridRole.TOP_RIGHT,
            LobbySlotGridRole.BOTTOM_LEFT,
            LobbySlotGridRole.BOTTOM_RIGHT,
        )

        (1..12).forEach { slotNumber ->
            assertEquals(
                expectedRoles[(slotNumber - 1) % 4],
                LobbySlotGridRole.fromSlotNumber(slotNumber),
            )
        }
    }

    @Test
    fun fourAnchorsReconstructAllScreenshotGroupsWithObservedPoints() {
        (1..3).forEach { screenshotIndex ->
            val grid = reconstructed(
                reconstructor = reconstructor,
                screenshotIndex = screenshotIndex,
                observed = idealAnchors(screenshotIndex),
            )

            assertEquals(screenshotIndex, grid.screenshotIndex)
            assertEquals(
                (1..4).map { localIndex -> (screenshotIndex - 1) * 4 + localIndex },
                grid.points.map { it.slotNumber },
            )
            assertTrue(grid.points.all { it.source == LobbyGridPointSource.OBSERVED })
            assertEquals(200.0, grid.columnPitch, 0.0)
            assertEquals(100.0, grid.rowPitch, 0.0)
        }
    }

    @Test
    fun threeAnchorsInferEachPossibleMissingRoleForAllScreenshotGroups() {
        (1..3).forEach { screenshotIndex ->
            LobbySlotGridRole.entries.forEach { missingRole ->
                val grid = reconstructed(
                    reconstructor = reconstructor,
                    screenshotIndex = screenshotIndex,
                    observed = idealAnchors(screenshotIndex).filter { anchor ->
                        LobbySlotGridRole.fromSlotNumber(anchor.slotNumber) != missingRole
                    },
                )
                val expected = idealPoint(screenshotIndex, missingRole)
                val actual = grid.pointFor(missingRole)

                assertEquals(expected.slotNumber, actual.slotNumber)
                assertEquals(missingRole, actual.role)
                assertEquals(expected.centerX, actual.centerX, 0.0)
                assertEquals(expected.centerY, actual.centerY, 0.0)
                assertEquals(LobbyGridPointSource.INFERRED, actual.source)
                assertTrue(
                    grid.points.filter { it.role != missingRole }
                        .all { it.source == LobbyGridPointSource.OBSERVED },
                )
            }
        }
    }

    @Test
    fun everyTwoAnchorRelationshipReconstructsAllFourRolesForAllScreenshotGroups() {
        val rolePairs = listOf(
            setOf(LobbySlotGridRole.TOP_LEFT, LobbySlotGridRole.TOP_RIGHT),
            setOf(LobbySlotGridRole.BOTTOM_LEFT, LobbySlotGridRole.BOTTOM_RIGHT),
            setOf(LobbySlotGridRole.TOP_LEFT, LobbySlotGridRole.BOTTOM_LEFT),
            setOf(LobbySlotGridRole.TOP_RIGHT, LobbySlotGridRole.BOTTOM_RIGHT),
            setOf(LobbySlotGridRole.TOP_LEFT, LobbySlotGridRole.BOTTOM_RIGHT),
            setOf(LobbySlotGridRole.TOP_RIGHT, LobbySlotGridRole.BOTTOM_LEFT),
        )

        (1..3).forEach { screenshotIndex ->
            rolePairs.forEach { observedRoles ->
                val observed = idealAnchors(screenshotIndex).filter { anchor ->
                    LobbySlotGridRole.fromSlotNumber(anchor.slotNumber) in observedRoles
                }
                val grid = reconstructed(
                    reconstructor = exactTwoToOneReconstructor,
                    screenshotIndex = screenshotIndex,
                    observed = observed,
                )

                LobbySlotGridRole.entries.forEach { role ->
                    val expected = idealPoint(screenshotIndex, role)
                    val actual = grid.pointFor(role)

                    assertEquals(expected.slotNumber, actual.slotNumber)
                    assertEquals(expected.centerX, actual.centerX, 0.0)
                    assertEquals(expected.centerY, actual.centerY, 0.0)
                    assertEquals(
                        if (role in observedRoles) {
                            LobbyGridPointSource.OBSERVED
                        } else {
                            LobbyGridPointSource.INFERRED
                        },
                        actual.source,
                    )
                }

                assertEquals(200.0, grid.columnPitch, 0.0)
                assertEquals(100.0, grid.rowPitch, 0.0)
            }
        }
    }

    @Test
    fun realScreenshotTopRowOneAndTwoUsesObservedHorizontalPitchAndRatioForBottomRow() {
        val grid = reconstructed(
            reconstructor = reconstructor,
            screenshotIndex = 1,
            observed = listOf(
                anchor(1, 584.5, 236.0),
                anchor(2, 1_075.5, 236.5),
            ),
        )

        val expectedRowPitch = 491.0 / (491.0 / 204.5)
        val expectedTopY = (236.0 + 236.5) / 2.0
        val expectedBottomY = expectedTopY + expectedRowPitch

        assertEquals(584.5, grid.leftColumnCenterX, 0.0)
        assertEquals(1_075.5, grid.rightColumnCenterX, 0.0)
        assertEquals(expectedTopY, grid.topRowCenterY, 0.0)
        assertEquals(expectedBottomY, grid.bottomRowCenterY, 0.000001)
        assertEquals(491.0, grid.columnPitch, 0.0)
        assertEquals(204.5, grid.rowPitch, 0.000001)

        val slotThree = grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT)
        val slotFour = grid.pointFor(LobbySlotGridRole.BOTTOM_RIGHT)
        assertEquals(584.5, slotThree.centerX, 0.0)
        assertEquals(expectedBottomY, slotThree.centerY, 0.000001)
        assertEquals(LobbyGridPointSource.INFERRED, slotThree.source)
        assertEquals(1_075.5, slotFour.centerX, 0.0)
        assertEquals(expectedBottomY, slotFour.centerY, 0.000001)
        assertEquals(LobbyGridPointSource.INFERRED, slotFour.source)
    }

    @Test
    fun realScreenshotRightColumnTwoAndFourUsesObservedVerticalPitchAndRatioForLeftColumn() {
        val grid = reconstructed(
            reconstructor = reconstructor,
            screenshotIndex = 1,
            observed = listOf(
                anchor(2, 1_075.5, 236.5),
                anchor(4, 1_075.5, 441.0),
            ),
        )

        val expectedColumnPitch = 204.5 * (491.0 / 204.5)
        val expectedLeftX = 1_075.5 - expectedColumnPitch

        assertEquals(expectedLeftX, grid.leftColumnCenterX, 0.000001)
        assertEquals(1_075.5, grid.rightColumnCenterX, 0.0)
        assertEquals(236.5, grid.topRowCenterY, 0.0)
        assertEquals(441.0, grid.bottomRowCenterY, 0.0)
        assertEquals(491.0, grid.columnPitch, 0.000001)
        assertEquals(204.5, grid.rowPitch, 0.0)

        val slotOne = grid.pointFor(LobbySlotGridRole.TOP_LEFT)
        val slotThree = grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT)
        assertEquals(584.5, slotOne.centerX, 0.000001)
        assertEquals(236.5, slotOne.centerY, 0.0)
        assertEquals(LobbyGridPointSource.INFERRED, slotOne.source)
        assertEquals(584.5, slotThree.centerX, 0.000001)
        assertEquals(441.0, slotThree.centerY, 0.0)
        assertEquals(LobbyGridPointSource.INFERRED, slotThree.source)
    }

    @Test
    fun realScreenshotDiagonalOneAndFourUsesBothObservedPitchesWithoutRatio() {
        val deliberatelyDifferentRatio = LobbySlotGridReconstructor(
            LobbyGridGeometryCalibration(columnToRowPitchRatio = 99.0),
        )
        val grid = reconstructed(
            reconstructor = deliberatelyDifferentRatio,
            screenshotIndex = 1,
            observed = listOf(
                anchor(1, 584.5, 236.0),
                anchor(4, 1_075.5, 441.0),
            ),
        )

        assertEquals(584.5, grid.leftColumnCenterX, 0.0)
        assertEquals(1_075.5, grid.rightColumnCenterX, 0.0)
        assertEquals(236.0, grid.topRowCenterY, 0.0)
        assertEquals(441.0, grid.bottomRowCenterY, 0.0)
        assertEquals(491.0, grid.columnPitch, 0.0)
        assertEquals(205.0, grid.rowPitch, 0.0)

        val slotTwo = grid.pointFor(LobbySlotGridRole.TOP_RIGHT)
        val slotThree = grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT)
        assertEquals(1_075.5, slotTwo.centerX, 0.0)
        assertEquals(236.0, slotTwo.centerY, 0.0)
        assertEquals(LobbyGridPointSource.INFERRED, slotTwo.source)
        assertEquals(584.5, slotThree.centerX, 0.0)
        assertEquals(441.0, slotThree.centerY, 0.0)
        assertEquals(LobbyGridPointSource.INFERRED, slotThree.source)
    }

    @Test
    fun threeAnchorsAlwaysUseDirectGeometryAndIgnorePitchRatioFallback() {
        val deliberatelyWrongRatio = LobbySlotGridReconstructor(
            LobbyGridGeometryCalibration(columnToRowPitchRatio = 10.0),
        )
        val grid = reconstructed(
            reconstructor = deliberatelyWrongRatio,
            screenshotIndex = 1,
            observed = listOf(
                anchor(1, 100.0, 100.0),
                anchor(2, 305.0, 110.0),
                anchor(4, 305.0, 210.0),
            ),
        )

        val slotThree = grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT)

        assertEquals(100.0, slotThree.centerX, 0.0)
        assertEquals(210.0, slotThree.centerY, 0.0)
        assertEquals(105.0, grid.topRowCenterY, 0.0)
        assertEquals(210.0, grid.bottomRowCenterY, 0.0)
        assertEquals(100.0, grid.leftColumnCenterX, 0.0)
        assertEquals(305.0, grid.rightColumnCenterX, 0.0)
        assertEquals(105.0, grid.rowPitch, 0.0)
        assertEquals(205.0, grid.columnPitch, 0.0)
    }

    @Test
    fun zeroAndOneAnchorRemainInsufficient() {
        assertEquals(
            LobbyGridReconstructionResult.InsufficientAnchors,
            reconstructor.reconstruct(1, emptyList()),
        )
        assertEquals(
            LobbyGridReconstructionResult.InsufficientAnchors,
            reconstructor.reconstruct(1, listOf(anchor(1, 100.0, 100.0))),
        )
    }

    @Test
    fun invalidTwoAnchorOrderingReturnsInvalidGeometryForRowColumnAndDiagonals() {
        val cases = listOf(
            listOf(anchor(1, 300.0, 100.0), anchor(2, 100.0, 100.0)),
            listOf(anchor(3, 300.0, 200.0), anchor(4, 100.0, 200.0)),
            listOf(anchor(1, 100.0, 300.0), anchor(3, 100.0, 100.0)),
            listOf(anchor(2, 300.0, 300.0), anchor(4, 300.0, 100.0)),
            listOf(anchor(1, 300.0, 100.0), anchor(4, 100.0, 200.0)),
            listOf(anchor(2, 100.0, 100.0), anchor(3, 300.0, 200.0)),
        )

        cases.forEach { observed ->
            assertEquals(
                LobbyGridReconstructionResult.InvalidGeometry,
                reconstructor.reconstruct(1, observed),
            )
        }
    }

    @Test
    fun invalidSlotGroupsDuplicateSlotsAndNonFiniteCentersAreRejected() {
        assertEquals(
            LobbyGridReconstructionResult.InvalidSlotGroup,
            reconstructor.reconstruct(1, listOf(anchor(5, 100.0, 100.0))),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidSlotGroup,
            reconstructor.reconstruct(4, emptyList()),
        )
        assertEquals(
            LobbyGridReconstructionResult.DuplicateSlot,
            reconstructor.reconstruct(
                1,
                listOf(
                    anchor(1, 100.0, 100.0),
                    anchor(1, 101.0, 101.0),
                    anchor(2, 300.0, 100.0),
                ),
            ),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidGeometry,
            reconstructor.reconstruct(
                1,
                listOf(
                    anchor(1, Double.NaN, 100.0),
                    anchor(2, 300.0, 100.0),
                ),
            ),
        )
    }

    @Test
    fun completeGridCalculatesPitchesAndAlignmentMetrics() {
        val grid = reconstructed(
            reconstructor = reconstructor,
            screenshotIndex = 1,
            observed = listOf(
                anchor(1, 100.0, 100.0),
                anchor(2, 300.0, 110.0),
                anchor(3, 105.0, 200.0),
                anchor(4, 300.0, 210.0),
            ),
        )

        assertEquals(100.0, grid.rowPitch, 0.0)
        assertEquals(197.5, grid.columnPitch, 0.0)
        assertEquals(10.0, grid.topRowAlignmentError, 0.0)
        assertEquals(10.0, grid.bottomRowAlignmentError, 0.0)
        assertEquals(5.0, grid.leftColumnAlignmentError, 0.0)
        assertEquals(0.0, grid.rightColumnAlignmentError, 0.0)
    }

    @Test
    fun sameAnchorsProduceSameRoleOrderedResultRegardlessOfInputOrder() {
        val observed = idealAnchors(1)
        val first = reconstructor.reconstruct(1, observed)
        val second = reconstructor.reconstruct(1, observed.reversed())
        val third = reconstructor.reconstruct(
            1,
            listOf(observed[2], observed[0], observed[3], observed[1]),
        )

        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(
            listOf(
                LobbySlotGridRole.TOP_LEFT,
                LobbySlotGridRole.TOP_RIGHT,
                LobbySlotGridRole.BOTTOM_LEFT,
                LobbySlotGridRole.BOTTOM_RIGHT,
            ),
            (first as LobbyGridReconstructionResult.Reconstructed).grid.points.map { it.role },
        )
    }

    private fun reconstructed(
        reconstructor: LobbySlotGridReconstructor,
        screenshotIndex: Int,
        observed: List<LobbyObservedSlotAnchor>,
    ): LobbySlotGrid =
        (reconstructor.reconstruct(
            screenshotIndex = screenshotIndex,
            observedAnchors = observed,
        ) as LobbyGridReconstructionResult.Reconstructed).grid

    private fun idealAnchors(screenshotIndex: Int): List<LobbyObservedSlotAnchor> =
        LobbySlotGridRole.entries.map { role ->
            val point = idealPoint(screenshotIndex, role)
            anchor(point.slotNumber, point.centerX, point.centerY)
        }

    private fun idealPoint(
        screenshotIndex: Int,
        role: LobbySlotGridRole,
    ) = LobbyGridPoint(
        slotNumber = (screenshotIndex - 1) * 4 + role.ordinal + 1,
        role = role,
        centerX = if (
            role == LobbySlotGridRole.TOP_LEFT ||
            role == LobbySlotGridRole.BOTTOM_LEFT
        ) {
            100.0
        } else {
            300.0
        },
        centerY = if (
            role == LobbySlotGridRole.TOP_LEFT ||
            role == LobbySlotGridRole.TOP_RIGHT
        ) {
            100.0
        } else {
            200.0
        },
        source = LobbyGridPointSource.OBSERVED,
    )

    private fun anchor(
        slotNumber: Int,
        centerX: Double,
        centerY: Double,
    ) = LobbyObservedSlotAnchor(slotNumber, centerX, centerY)
}
