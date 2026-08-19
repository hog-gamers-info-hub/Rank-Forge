package com.hoggamers.rankforge.domain.ocr.matchlobby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbySlotGridReconstructorTest {
    private val reconstructor = LobbySlotGridReconstructor()

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
            val grid = reconstructed(idealAnchors(screenshotIndex))

            assertEquals(screenshotIndex, grid.screenshotIndex)
            assertEquals(
                (1..4).map { localIndex -> (screenshotIndex - 1) * 4 + localIndex },
                grid.points.map { it.slotNumber },
            )
            assertTrue(grid.points.all { it.source == LobbyGridPointSource.OBSERVED })
        }
    }

    @Test
    fun threeAnchorsInferEachPossibleMissingRole() {
        val cases = listOf(
            LobbySlotGridRole.TOP_LEFT,
            LobbySlotGridRole.TOP_RIGHT,
            LobbySlotGridRole.BOTTOM_LEFT,
            LobbySlotGridRole.BOTTOM_RIGHT,
        )

        cases.forEach { missingRole ->
            val grid = reconstructed(
                idealAnchors(1).filter { anchor ->
                    LobbySlotGridRole.fromSlotNumber(anchor.slotNumber) != missingRole
                },
            )
            val expected = idealPoint(1, missingRole)
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

    @Test
    fun realScreenshotOneEvidenceInfersSlotThree() {
        val grid = reconstructed(
            listOf(
                anchor(1, 584.5, 231.0),
                anchor(2, 1_075.5, 231.5),
                anchor(4, 1_075.5, 436.5),
            ),
        )

        val slotThree = grid.pointFor(LobbySlotGridRole.BOTTOM_LEFT)
        assertEquals(3, slotThree.slotNumber)
        assertEquals(LobbyGridPointSource.INFERRED, slotThree.source)
        assertEquals(584.5, slotThree.centerX, 0.0)
        assertEquals(436.5, slotThree.centerY, 0.0)
    }

    @Test
    fun currentScreenshotTwoAndThreeCasesRemainFullyObserved() {
        val screenshotTwo = reconstructed(idealAnchors(2))
        val screenshotThree = reconstructed(idealAnchors(3))

        assertTrue(screenshotTwo.points.all { it.source == LobbyGridPointSource.OBSERVED })
        assertTrue(screenshotThree.points.all { it.source == LobbyGridPointSource.OBSERVED })
    }

    @Test
    fun twoAnchorsAreInsufficientRegardlessOfArrangement() {
        val cases = listOf(
            listOf(anchor(1, 100.0, 100.0), anchor(3, 100.0, 200.0)),
            listOf(anchor(1, 100.0, 100.0), anchor(2, 300.0, 100.0)),
            listOf(anchor(1, 100.0, 100.0), anchor(4, 300.0, 200.0)),
        )

        cases.forEach { observed ->
            assertEquals(
                LobbyGridReconstructionResult.InsufficientAnchors,
                reconstructor.reconstruct(1, observed),
            )
        }
    }

    @Test
    fun zeroAndOneAnchorAreInsufficient() {
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
    fun invalidSlotGroupsAreRejectedForEachDirection() {
        assertEquals(
            LobbyGridReconstructionResult.InvalidSlotGroup,
            reconstructor.reconstruct(1, listOf(anchor(5, 100.0, 100.0))),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidSlotGroup,
            reconstructor.reconstruct(2, listOf(anchor(1, 100.0, 100.0))),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidSlotGroup,
            reconstructor.reconstruct(3, listOf(anchor(8, 100.0, 100.0))),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidSlotGroup,
            reconstructor.reconstruct(4, emptyList()),
        )
    }

    @Test
    fun duplicateSlotIsRejected() {
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
    }

    @Test
    fun invalidOrderingAndNonFiniteCoordinatesReturnInvalidGeometry() {
        assertEquals(
            LobbyGridReconstructionResult.InvalidGeometry,
            reconstructor.reconstruct(
                1,
                listOf(
                    anchor(1, 300.0, 100.0),
                    anchor(2, 100.0, 100.0),
                    anchor(3, 300.0, 200.0),
                    anchor(4, 100.0, 200.0),
                ),
            ),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidGeometry,
            reconstructor.reconstruct(
                1,
                listOf(
                    anchor(1, 100.0, 200.0),
                    anchor(2, 300.0, 100.0),
                    anchor(3, 100.0, 100.0),
                    anchor(4, 300.0, 200.0),
                ),
            ),
        )
        assertEquals(
            LobbyGridReconstructionResult.InvalidGeometry,
            reconstructor.reconstruct(
                1,
                listOf(anchor(1, Double.NaN, 100.0)),
            ),
        )
    }

    @Test
    fun completeGridCalculatesPitchesAndAlignmentMetrics() {
        val grid = reconstructed(
            listOf(
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
        val third = reconstructor.reconstruct(1, listOf(observed[2], observed[0], observed[3], observed[1]))

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

    private fun reconstructed(observed: List<LobbyObservedSlotAnchor>): LobbySlotGrid {
        val screenshotIndex = ((observed.minOf { it.slotNumber } - 1) / 4) + 1
        return (reconstructor.reconstruct(screenshotIndex, observed) as LobbyGridReconstructionResult.Reconstructed).grid
    }

    private fun idealAnchors(screenshotIndex: Int): List<LobbyObservedSlotAnchor> =
        listOf(
            idealPoint(screenshotIndex, LobbySlotGridRole.TOP_LEFT),
            idealPoint(screenshotIndex, LobbySlotGridRole.TOP_RIGHT),
            idealPoint(screenshotIndex, LobbySlotGridRole.BOTTOM_LEFT),
            idealPoint(screenshotIndex, LobbySlotGridRole.BOTTOM_RIGHT),
        ).map { point ->
            anchor(point.slotNumber, point.centerX, point.centerY)
        }

    private fun idealPoint(
        screenshotIndex: Int,
        role: LobbySlotGridRole,
    ) = LobbyGridPoint(
        slotNumber = (screenshotIndex - 1) * 4 + role.ordinal + 1,
        role = role,
        centerX = if (role == LobbySlotGridRole.TOP_LEFT || role == LobbySlotGridRole.BOTTOM_LEFT) 100.0 else 300.0,
        centerY = if (role == LobbySlotGridRole.TOP_LEFT || role == LobbySlotGridRole.TOP_RIGHT) 100.0 else 200.0,
        source = LobbyGridPointSource.OBSERVED,
    )

    private fun anchor(slotNumber: Int, centerX: Double, centerY: Double) =
        LobbyObservedSlotAnchor(slotNumber, centerX, centerY)
}
