package com.example.voxa

import com.example.voxa.utils.TashkeelHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class TashkeelTest {

    @Test
    fun testTashkeelMapping() {
        // Assertions for exact matches in the preprocessor map
        assertEquals("مَيَّة", TashkeelHelper.applyTashkeel("مية"))
        assertEquals("سَاعِدْنِي", TashkeelHelper.applyTashkeel("ساعدني"))
        assertEquals("أَكْل", TashkeelHelper.applyTashkeel("اكل"))
    }

    @Test
    fun testTashkeelSentenceReplacement() {
        // Assertions for replacements inside a sentence
        assertEquals("سَاعِدْنِي بسرعة", TashkeelHelper.applyTashkeel("ساعدني بسرعة"))
        assertEquals("عايز أَشْرَب مَيَّة", TashkeelHelper.applyTashkeel("عايز اشرب مية"))
    }

    @Test
    fun testUntouchedWords() {
        // Words not in the map should remain unchanged
        assertEquals("تفاح", TashkeelHelper.applyTashkeel("تفاح"))
        assertEquals("كتاب جديد", TashkeelHelper.applyTashkeel("كتاب جديد"))
    }
}
