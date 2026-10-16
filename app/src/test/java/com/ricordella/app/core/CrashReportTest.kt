package com.ricordella.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {

    private fun frame(cls: String, method: String, file: String, line: Int) = StackTraceElement(cls, method, file, line)

    private fun error(message: String, vararg frames: StackTraceElement, cause: Throwable? = null): Throwable =
        IllegalStateException(message, cause).apply { stackTrace = arrayOf(*frames) }

    @Test
    fun whereLineNamesTheFirstAppFrameOfTheRootCause() {
        val root = error(
            "indice fuori dai limiti",
            frame("java.util.ArrayList", "get", "ArrayList.java", 427),
            frame("com.ricordella.app.feature.home.HomeViewModel", "buildState", "HomeViewModel.kt", 214),
            frame("com.ricordella.app.feature.home.HomeScreenKt", "HomeScreen", "HomeScreen.kt", 143),
        )
        val wrapper = error("wrapper", frame("androidx.compose.runtime.Recomposer", "run", "Recomposer.kt", 9), cause = root)

        val report = CrashReport.format("intestazione", wrapper)

        assertTrue(report.lines().any { it == "DOVE: HomeViewModel.kt, riga 214 (com.ricordella.app.feature.home.HomeViewModel.buildState)" })
        assertTrue(report.contains("ERRORE: java.lang.IllegalStateException: wrapper"))
        assertTrue(report.contains("CAUSA ORIGINALE: java.lang.IllegalStateException: indice fuori dai limiti"))
        // Le tappe nel codice dell'app: dalla più vicina all'errore, senza le righe delle librerie.
        val steps = report.substringAfter("TAPPE NEL CODICE DELL'APP").substringBefore("STACK COMPLETO").lines().filter { it.matches(Regex("\\d+\\..*")) }
        assertEquals(listOf("1. HomeViewModel.kt, riga 214", "2. HomeScreen.kt, riga 143"), steps.map { it.substringBefore(" (") })
    }

    @Test
    fun withoutCauseThereIsNoOriginalCauseLine() {
        val report = CrashReport.format("h", error("solo", frame("com.ricordella.app.core.X", "y", "X.kt", 7)))
        assertFalse(report.contains("CAUSA ORIGINALE"))
        assertTrue(report.contains("DOVE: X.kt, riga 7"))
    }

    @Test
    fun withoutAppFramesWhereFallsBackToTheTopFrame() {
        val report = CrashReport.format("h", error("libreria", frame("com.google.mlkit.Foo", "bar", "Foo.java", 10)))
        assertTrue(report.contains("DOVE: Foo.java, riga 10 (com.google.mlkit.Foo.bar)"))
        assertFalse(report.contains("TAPPE NEL CODICE DELL'APP"))
    }

    @Test
    fun emptyStackAndMissingLineAreReadable() {
        assertTrue(CrashReport.format("h", error("vuoto")).contains("DOVE: sconosciuto (stack vuoto)"))
        val noLine = CrashReport.format("h", error("x", frame("com.ricordella.app.A", "b", "A.kt", -1)))
        assertTrue(noLine.contains("DOVE: A.kt, riga ? (com.ricordella.app.A.b)"))
    }

    @Test
    fun deepCauseCycleDoesNotLoopForever() {
        val a = IllegalStateException("a")
        val b = IllegalStateException("b", a)
        a.initCause(b)
        assertTrue(CrashReport.format("h", a).contains("DOVE:"))
    }

    @Test
    fun longReportIsCutButKeepsTheLocationOnTop() {
        val frames = Array(2_000) { frame("com.ricordella.app.Deep", "m$it", "Deep.kt", it) }
        val report = CrashReport.format("h", error("enorme", *frames))
        assertEquals(CrashReport.MAX_CHARS, report.length)
        assertTrue(report.contains("DOVE: Deep.kt, riga 0"))
    }

    @Test
    fun manualReportHasOnlyTheHeader() {
        assertEquals("Segnalazione", CrashReport.format("Segnalazione", null).trim())
    }
}
