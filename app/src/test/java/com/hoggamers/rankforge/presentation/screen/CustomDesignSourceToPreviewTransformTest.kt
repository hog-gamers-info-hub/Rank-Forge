package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDesignSourceToPreviewTransformTest {
    @Test
    fun squareSourceIntoSquareContainerHasNoLetterbox() {
        val transform = transform(1080, 1080, 540f, 540f)

        assertEquals(0.5f, transform.scale, 0f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(0f, transform.offsetY, 0f)
        assertEquals(540f, transform.displayedWidth, 0f)
        assertEquals(540f, transform.displayedHeight, 0f)
    }

    @Test
    fun squareSourceIntoTallerContainerCentersVertically() {
        val transform = transform(1080, 1080, 400f, 600f)

        assertEquals(400f / 1080f, transform.scale, 0.00001f)
        assertEquals(0f, transform.offsetX, 0f)
        assertEquals(100f, transform.offsetY, 0.00001f)
        assertEquals(400f, transform.displayedWidth, 0.00001f)
        assertEquals(400f, transform.displayedHeight, 0.00001f)
    }

    @Test
    fun squareSourceIntoWiderContainerCentersHorizontally() {
        val transform = transform(1080, 1080, 600f, 400f)

        assertEquals(400f / 1080f, transform.scale, 0.00001f)
        assertEquals(100f, transform.offsetX, 0.00001f)
        assertEquals(0f, transform.offsetY, 0f)
    }

    @Test
    fun rectangularSourceIntoSquareContainerIncludesLetterbox() {
        val transform = transform(1080, 540, 400f, 400f)

        assertEquals(400f / 1080f, transform.scale, 0.00001f)
        assertEquals(400f, transform.displayedWidth, 0.00001f)
        assertEquals(200f, transform.displayedHeight, 0.00001f)
        assertEquals(100f, transform.offsetY, 0.00001f)
    }

    @Test
    fun mapsCenterToCenterOfDisplayedImage() {
        val transform = transform(1080, 1080, 400f, 600f)
        val point = transform.mapPoint(540f, 540f)

        assertEquals(200f, point.x, 0.00001f)
        assertEquals(300f, point.y, 0.00001f)
    }

    @Test
    fun mapsSourceBoundariesToDisplayedImageBoundaries() {
        val transform = transform(1080, 1080, 400f, 600f)

        assertEquals(transform.offsetX, transform.mapX(0f), 0f)
        assertEquals(transform.offsetY, transform.mapY(0f), 0f)
        assertEquals(
            transform.offsetX + transform.displayedWidth,
            transform.mapX(1080f),
            0.00001f,
        )
        assertEquals(
            transform.offsetY + transform.displayedHeight,
            transform.mapY(1080f),
            0.00001f,
        )
    }

    @Test
    fun preservesSourceCoordinateMappingForVerifiedColumnAndRow() {
        val transform = transform(1080, 1080, 540f, 540f)

        assertEquals(472.75f, transform.mapX(945.5f), 0.00001f)
        assertEquals(172.5f, transform.mapY(345f), 0.00001f)
        assertEquals(945.5f, transform.unmapX(472.75f), 0.00001f)
        assertEquals(345f, transform.unmapY(172.5f), 0.00001f)
    }

    @Test
    fun removesVerticalLetterboxDuringInverseMapping() {
        val transform = transform(1080, 1080, 400f, 600f)

        assertEquals(0f, transform.unmapX(transform.offsetX), 0.00001f)
        assertEquals(1080f, transform.unmapX(transform.offsetX + transform.displayedWidth), 0.00001f)
        assertEquals(0f, transform.unmapY(transform.offsetY), 0.00001f)
        assertEquals(1080f, transform.unmapY(transform.offsetY + transform.displayedHeight), 0.00001f)
        assertEquals(540f, transform.unmapY(transform.mapY(540f)), 0.00001f)
    }

    @Test
    fun removesHorizontalLetterboxDuringInverseMapping() {
        val transform = transform(1080, 1080, 600f, 400f)

        assertEquals(0f, transform.unmapX(transform.offsetX), 0.00001f)
        assertEquals(1080f, transform.unmapX(transform.offsetX + transform.displayedWidth), 0.00001f)
        assertEquals(540f, transform.unmapX(transform.mapX(540f)), 0.00001f)
    }

    private fun transform(
        sourceWidth: Int,
        sourceHeight: Int,
        containerWidth: Float,
        containerHeight: Float,
    ) = SourceToPreviewTransform.fit(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
    ) ?: error("Expected a valid transform")
}
