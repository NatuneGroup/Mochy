package com.metrolist.shared.api.lyrics

import com.metrolist.shared.model.LyricsTimingKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MlxParserTest {
    @Test
    fun jsonStructuralBudgetsRejectExcessLinesWordsAndLineText() {
        fun document(lines: String) = """{"version":2,"lines":[$lines]}"""
        val line = """{"text":"line"}"""
        assertEquals(5000, MlxParser.parse(document(List(5000) { line }.joinToString(","))).lines.size)
        assertTrue(MlxParser.parse(document(List(5001) { line }.joinToString(","))).lines.isEmpty())
        val word = """{"text":"x","startMs":0,"endMs":1}"""
        val words = List(10000) { word }.joinToString(",")
        for (field in listOf("words", "backgroundWords", "romanizedWords")) {
            val wordLine = """{"$field":[$words]}"""
            assertEquals(2, MlxParser.parse(document("$wordLine,$wordLine")).lines.size)
            val extraWord = """{"$field":[$word]}"""
            assertTrue(MlxParser.parse(document("$wordLine,$wordLine,$extraWord")).lines.isEmpty())
        }
        val mixedWords = List(7000) { word }.joinToString(",")
        val mixedLines = listOf("words", "backgroundWords", "romanizedWords").joinToString(",") { """{"$it":[$mixedWords]}""" }
        assertTrue(MlxParser.parse(document(mixedLines)).lines.isEmpty())
        val backgroundFallback = """{"backgroundText":"background","words":[$words]}"""
        assertTrue(MlxParser.parse(document("$backgroundFallback,$backgroundFallback")).lines.isEmpty())
        for (field in listOf("text", "backgroundText", "romanizedText", "translatedText")) {
            assertEquals(1, MlxParser.parse(document("""{"$field":"${"x".repeat(16384)}"}""")).lines.size)
            assertTrue(MlxParser.parse(document("""{"$field":"${"x".repeat(16385)}"}""")).lines.isEmpty())
        }
        val combined = """{"text":"${"x".repeat(10000)}","backgroundText":"${"x".repeat(10000)}"}"""
        assertTrue(MlxParser.parse(document(combined)).lines.isEmpty())
    }

    @Test
    fun duplicateTimestampsMatchAuxiliaryLinesByOccurrence() {
        val document =
            MlxParser.parse(
                """
                [meta]
                [lyrics]
                [00:01.000]First
                [00:01.000]Second
                [romanlyrics]
                [00:01.000]Roman first
                [00:01.000]Roman second
                [translatedlyrics]
                [00:01.000]Translation first
                [00:01.000]Translation second
                """.trimIndent(),
            )
        assertEquals(listOf("Roman first", "Roman second"), document.lines.map { it.romanizedText })
        assertEquals(listOf("Translation first", "Translation second"), document.lines.map { it.translatedText })
    }

    @Test
    fun jsonDocumentLimitMatchesDownloadLimit() {
        val mlx = """{"version":2,"lines":[{"startMs":1000,"endMs":2000,"text":"Line"}]}""".padEnd(8 * 1024 * 1024)

        assertTrue(MlxParser.isMlxFormat(mlx))
        assertEquals(
            "Line",
            MlxParser
                .parse(mlx)
                .lines
                .single()
                .text,
        )
        assertFalse(MlxParser.isMlxFormat(mlx + " "))
        assertTrue(MlxParser.parse(mlx + " ").lines.isEmpty())
    }

    @Test
    fun parsesTextDocumentsAboveOldLimit() {
        val mlx = "[meta]\n[lyrics]\n" + ("[00:01.000]" + "x".repeat(1_000) + "\n").repeat(600)

        assertTrue(mlx.length > 512 * 1024)
        assertTrue(MlxParser.isMlxFormat(mlx))
        assertEquals(600, MlxParser.parse(mlx).lines.size)
    }

    @Test
    fun formatValidationRejectsDocumentsWithoutUsableParsedLyrics() {
        val documents =
            listOf(
                """{"version":2,"lines":[{}]}""",
                """{"version":1,"lines":[{"text":"Line"}]}""",
                "[meta]\n[lyrics]\n",
                "[meta]\n[lyrics]\n" + "x".repeat(16 * 1024 + 1),
            )

        for (mlx in documents) {
            assertFalse(MlxParser.isMlxFormat(mlx))
        }
    }

    @Test
    fun derivesVisibleTextFromBackgroundContentWhenForegroundIsAbsent() {
        val lines =
            """
            [
              {"background":true,"backgroundText":"Echo"},
              {"background":true,"backgroundWords":[{"text":"Echo ","startMs":1000,"endMs":1500},{"text":"again","startMs":1500,"endMs":2000}]},
              {"text":"Lead","backgroundText":"Echo"},
              {"words":[{"text":"Lead","startMs":1000,"endMs":2000}],"backgroundText":"Echo"},
              {"backgroundText":"Unmarked echo"}
            ]
            """.trimIndent()
        val mlx = """{"version":2,"lines":$lines}"""

        assertTrue(MlxParser.isMlxFormat(mlx))
        assertEquals(listOf("Echo", "Echo again", "Lead", "Lead", "Unmarked echo"), MlxParser.parse(mlx).lines.map { it.text })
        assertTrue(
            MlxParser
                .parse(mlx)
                .lines
                .last()
                .isBackground,
        )
    }

    @Test
    fun untimedBackgroundTextDoesNotCountAsWordTiming() {
        for (foreground in listOf("", "Lead")) {
            val mlx =
                """{"version":2,"lines":[{"startMs":1000,"endMs":2000,"text":"$foreground","backgroundText":"Echo"}]}"""
            val lines = MlxParser.parse(mlx).lines
            val backgroundWord =
                lines
                    .single()
                    .words
                    .orEmpty()
                    .single()

            assertEquals("Echo", backgroundWord.text)
            assertEquals(1.0, backgroundWord.startTime)
            assertEquals(2.0, backgroundWord.endTime)
            assertTrue(backgroundWord.isBackground)
            assertTrue(backgroundWord.isLineTimed)
            assertEquals(LyricsTimingKind.LINE_SYNCED, lyricsTimingKind(lines))
        }

        val timedBackground =
            """{"version":2,"lines":[{"startMs":1000,"endMs":2000,"backgroundWords":[{"text":"Echo","startMs":1000,"endMs":2000}]}]}"""
        assertEquals(LyricsTimingKind.WORD_SYNCED, lyricsTimingKind(MlxParser.parse(timedBackground).lines))
    }

    @Test
    fun backgroundRowsKeepPrimaryAndNestedWordGroupsSeparate() {
        val document =
            MlxParser.parse(
                """
                [meta]
                [lyrics]
                [00:01.000~00:02.000]{bg}<00:01.000>你好<00:02.000> [bg:<00:01.000>Oh<00:02.000>]
                [romanlyrics]
                [00:01.000~00:02.000]<00:01.000>ni hao<00:02.000>
                """.trimIndent(),
            )
        val entry = document.lines.single()
        assertTrue(entry.isBackground)
        assertEquals(listOf(false, true), entry.words.orEmpty().map { it.isBackground })
        assertEquals(
            "Oh",
            entry.words
                .orEmpty()
                .last()
                .text,
        )
        assertTrue(entry.romanizedWords.orEmpty().none { it.isBackground })
    }
}
