package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeDesignBitmapComposerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val template = FreeDesignTemplateRegistry.default()
    private val template2 = requireNotNull(
        FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID),
    )
    private val template3 = requireNotNull(
        FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V3_TEMPLATE_ID),
    )
    private val template4 = requireNotNull(
        FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V4_TEMPLATE_ID),
    )
    private val composer = FreeDesignBitmapComposer(context.assets)

    @Test
    fun bundledAssetComposesToExpectedDistinctBitmap() {
        val result = composer.compose(tournamentModel(), template)
        val composed = (result as FreeDesignBitmapComposeResult.Success).bitmap
        try {
            assertEquals(1254, composed.width)
            assertEquals(1254, composed.height)
            assertEquals(Bitmap.Config.ARGB_8888, composed.config)
        } finally {
            composed.recycle()
        }
    }

    @Test
    fun outputPreservesUnrenderedSourcePixelsAndChangesHeaderAndRows() {
        val source = decodeSource()
        val result = composer.compose(tournamentModel(), template)
        val composed = (result as FreeDesignBitmapComposeResult.Success).bitmap
        try {
            assertNotSame(source, composed)
            assertEquals(source.getPixel(0, 0), composed.getPixel(0, 0))
            assertEquals(source.getPixel(1253, 1253), composed.getPixel(1253, 1253))
            assertTrue(changedPixelCount(source, composed, 80, 80, 1174, 270) > 0)
            assertTrue(changedPixelCount(source, composed, 80, 350, 1174, 430) > 0)
        } finally {
            source.recycle()
            composed.recycle()
        }
    }

    @Test
    fun template2TournamentAndMatchComposeAtExactSourceDimensions() {
        val tournamentResult = composer.compose(tournamentModel(), template2)
        val tournamentBitmap = (tournamentResult as FreeDesignBitmapComposeResult.Success).bitmap
        val matchResult = composer.compose(matchModel(), template2)
        val matchBitmap = (matchResult as FreeDesignBitmapComposeResult.Success).bitmap
        try {
            assertEquals(1254, tournamentBitmap.width)
            assertEquals(1254, tournamentBitmap.height)
            assertEquals(1254, matchBitmap.width)
            assertEquals(1254, matchBitmap.height)
        } finally {
            tournamentBitmap.recycle()
            matchBitmap.recycle()
        }
    }

    @Test
    fun template3TournamentAndMatchComposeAtExactSourceDimensions() {
        val tournamentResult = composer.compose(tournamentModel(), template3)
        val tournamentBitmap = (tournamentResult as FreeDesignBitmapComposeResult.Success).bitmap
        val matchResult = composer.compose(matchModel(), template3)
        val matchBitmap = (matchResult as FreeDesignBitmapComposeResult.Success).bitmap
        try {
            assertEquals(1072, tournamentBitmap.width)
            assertEquals(1467, tournamentBitmap.height)
            assertEquals(1072, matchBitmap.width)
            assertEquals(1467, matchBitmap.height)
        } finally {
            tournamentBitmap.recycle()
            matchBitmap.recycle()
        }
    }

    @Test
    fun template4TournamentAndMatchComposeAtExactSourceDimensions() {
        val tournamentResult = composer.compose(tournamentModel(), template4)
        val tournamentBitmap = (tournamentResult as FreeDesignBitmapComposeResult.Success).bitmap
        val matchResult = composer.compose(matchModel(), template4)
        val matchBitmap = (matchResult as FreeDesignBitmapComposeResult.Success).bitmap
        try {
            assertEquals(1254, tournamentBitmap.width)
            assertEquals(1254, tournamentBitmap.height)
            assertEquals(1254, matchBitmap.width)
            assertEquals(1254, matchBitmap.height)
        } finally {
            tournamentBitmap.recycle()
            matchBitmap.recycle()
        }
    }

    @Test
    fun template2SourcePixelsRemainUnchangedWhileHeaderAndTableAreComposed() {
        val source = decodeSource(template2)
        val result = composer.compose(tournamentModel(), template2)
        val composed = (result as FreeDesignBitmapComposeResult.Success).bitmap
        try {
            assertNotSame(source, composed)
            assertEquals(source.width, 1254)
            assertEquals(source.height, 1254)
            assertEquals(source.getPixel(0, 0), composed.getPixel(0, 0))
            assertEquals(source.getPixel(1253, 1253), composed.getPixel(1253, 1253))
            assertTrue(changedPixelCount(source, composed, 40, 40, 1214, 290) > 0)
            assertTrue(changedPixelCount(source, composed, 40, 350, 1214, 1120) > 0)
        } finally {
            source.recycle()
            composed.recycle()
        }
    }

    @Test
    fun incorrectExpectedDimensionsFailWithoutResizing() {
        val result = composer.compose(
            tournamentModel(),
            template.copy(sourceWidth = 1253),
        )

        assertEquals(
            FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.DIMENSION_MISMATCH,
            ),
            result,
        )
    }

    @Test
    fun missingAssetFailsWithAssetNotFound() {
        val result = composer.compose(
            tournamentModel(),
            template.copy(assetPath = "result_templates/missing.webp"),
        )

        assertEquals(
            FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.ASSET_NOT_FOUND,
            ),
            result,
        )
    }

    @Test
    fun rendererFailureDoesNotReturnPartialBitmap() {
        val result = composer.compose(
            tournamentModel(rows = rows(13)),
            template,
        )

        assertEquals(
            FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.RENDER_FAILED,
            ),
            result,
        )
    }

    private fun decodeSource(selectedTemplate: FreeDesignTemplate = template): Bitmap =
        context.assets.open(selectedTemplate.assetPath).use { input ->
        BitmapFactory.decodeStream(
            input,
            null,
            BitmapFactory.Options().apply { inScaled = false },
        )!!
        }

    private fun matchModel() = com.hoggamers.rankforge.domain.export.MatchResultExportModel(
        tournamentName = "Champions Cup 2026",
        organizerName = "HOG Gamers",
        tournamentDate = LocalDate.of(2026, 9, 3),
        matchNumber = 4,
        matchDate = LocalDate.of(2026, 8, 31),
        mapName = "Bermuda",
        rows = rows(1),
    )

    private fun tournamentModel(
        rows: List<ResultExportRow> = rows(1),
    ) = TournamentResultExportModel(
        tournamentName = "Champions Cup 2026",
        organizerName = "HOG Gamers",
        tournamentDate = LocalDate.of(2026, 9, 3),
        finalizedMatchCount = 2,
        rows = rows,
    )

    private fun rows(count: Int) = (1..count).map { rank ->
        ResultExportRow(
            rank = rank,
            teamName = "Team $rank",
            win = if (rank == 1) 1 else 0,
            totalKills = rank,
            positionPoints = 13 - rank,
            totalPoints = rank * 2,
        )
    }

    private fun changedPixelCount(
        source: Bitmap,
        composed: Bitmap,
        left: Int,
        top: Int,
        rightExclusive: Int,
        bottomExclusive: Int,
    ): Int {
        var changed = 0
        for (x in left until rightExclusive) {
            for (y in top until bottomExclusive) {
                if (source.getPixel(x, y) != composed.getPixel(x, y)) changed++
            }
        }
        return changed
    }
}
