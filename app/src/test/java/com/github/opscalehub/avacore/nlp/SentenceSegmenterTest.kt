package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmenterTest {

    @Test fun splits_onSentenceBoundaries() {
        val units = SentenceSegmenter.split("سلام. خوبی؟")
        assertEquals(2, units.size)
        assertEquals("سلام.", units[0].text)
        assertEquals("خوبی؟", units[1].text)
        assertTrue(units[0].trailingPauseMs > 0)
    }

    @Test fun clausePauseShorterThanSentence() {
        val clause = SentenceSegmenter.split("اول؛")[0]
        val sentence = SentenceSegmenter.split("اول.")[0]
        assertTrue(clause.trailingPauseMs < sentence.trailingPauseMs)
    }

    @Test fun singleUnit_whenNoPunctuation() {
        val units = SentenceSegmenter.split("یک دو سه")
        assertEquals(1, units.size)
        assertEquals("یک دو سه", units[0].text)
    }

    @Test fun absorbsRepeatedTerminators() {
        val units = SentenceSegmenter.split("واقعا؟!")
        assertEquals(1, units.size)
        assertEquals("واقعا؟!", units[0].text)
    }

    @Test fun longRunIsCapped() {
        val long = "کلمه ".repeat(80).trim() // ~ 400 chars, no punctuation
        val units = SentenceSegmenter.split(long)
        assertTrue("expected multiple capped units", units.size > 1)
    }
}
