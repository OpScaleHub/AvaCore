package com.github.opscalehub.avacore.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizerTest {

    private val n = Normalizer()

    @Test fun folds_arabicLetters() {
        // Arabic kaf/yeh -> Persian keheh/yeh
        assertEquals("کتاب یک", n.normalize("كتاب يك"))
    }

    @Test fun strips_kashidaAndTanvin() {
        assertEquals("به", n.normalize("بـــه"))
        assertEquals("واقعا", n.normalize("واقعاً"))
    }

    @Test fun normalizes_punctuationSpacing() {
        assertEquals("سلام، خوبی؟", n.normalize("سلام،خوبی?"))
    }

    @Test fun collapses_whitespace() {
        assertEquals("متن دو", n.normalize("متن     دو"))
    }

    @Test fun preserves_zwnj() {
        assertTrue(n.normalize("می‌روم").contains('‌'))
    }
}
