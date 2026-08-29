package com.hoggamers.rankforge.data.ocr.matchlobby

import com.paddle.ocr.model.OCRRunResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LobbyPanelPpEngineSessionTest {
    @Test
    fun prewarmCreatesTheEngineOnce() = runTest {
        val factory = RecordingEngineFactory()
        val session = factory.session()

        session.prewarm()

        assertEquals(1, factory.createCount)
        assertEquals(0, factory.engine.recognizeCount)
    }

    @Test
    fun repeatedPrewarmReusesTheSameEngine() = runTest {
        val factory = RecordingEngineFactory()
        val session = factory.session()

        session.prewarm()
        session.prewarm()

        assertEquals(1, factory.createCount)
    }

    @Test
    fun recognizeAfterPrewarmDoesNotCreateOrInferDuringPrewarm() = runTest {
        val factory = RecordingEngineFactory()
        val session = factory.session()

        session.prewarm()
        assertEquals(0, factory.engine.recognizeCount)

        session.recognize("panel")

        assertEquals(1, factory.createCount)
        assertEquals(1, factory.engine.recognizeCount)
    }

    @Test
    fun recognizeWithoutPrewarmRetainsLazyCreation() = runTest {
        val factory = RecordingEngineFactory()
        factory.session().recognize("panel")

        assertEquals(1, factory.createCount)
        assertEquals(1, factory.engine.recognizeCount)
    }

    @Test
    fun concurrentPrewarmAndRecognizeCreateOneEngine() = runTest {
        val createStarted = CompletableDeferred<Unit>()
        val allowCreate = CompletableDeferred<Unit>()
        val factory = RecordingEngineFactory {
            createStarted.complete(Unit)
            allowCreate.await()
        }
        val session = factory.session()

        val prewarm = async { session.prewarm() }
        createStarted.await()
        val recognize = async { session.recognize("panel") }
        assertFalse(recognize.isCompleted)

        allowCreate.complete(Unit)
        prewarm.await()
        recognize.await()

        assertEquals(1, factory.createCount)
        assertEquals(1, factory.engine.recognizeCount)
    }

    @Test
    fun failedPrewarmDoesNotPoisonLaterLazyRetry() = runTest {
        var shouldFail = true
        val factory = RecordingEngineFactory {
            if (shouldFail) {
                shouldFail = false
                throw IllegalStateException("create failed")
            }
        }
        val session = factory.session()

        try {
            session.prewarm()
        } catch (_: IllegalStateException) {
            // The Android runtime catches this optimization failure; the session
            // intentionally leaves its engine slot empty so retry remains possible.
        }
        session.recognize("panel")

        assertEquals(2, factory.createCount)
        assertEquals(1, factory.engine.recognizeCount)
    }

    private class RecordingEngineFactory(
        private val beforeCreate: suspend () -> Unit = {},
    ) {
        val engine = RecordingEngine()
        var createCount = 0

        fun session() = LobbyPanelPpEngineSession<String> {
            createCount++
            beforeCreate()
            engine
        }
    }

    private class RecordingEngine : LobbyPanelPpEngine<String> {
        var recognizeCount = 0

        override suspend fun recognize(input: String): OCRRunResult {
            recognizeCount++
            return OCRRunResult(
                results = emptyList(),
                detectionTimeMs = 0L,
                recognitionTimeMs = 0L,
                totalTimeMs = 0L,
                lineCount = 0,
            )
        }
    }
}
