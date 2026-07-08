package com.example.voxa.utils

object TashkeelHelper {
    /**
     * Preprocesses Arabic text to apply standard vowelization (tashkeel) diacritics,
     * allowing TTS engines to parse rules and speak properly.
     */
    fun apply(text: String): String {
        val clean = text.trim()
        val tashkeelMap = mapOf(
            "مية" to "مَيَّة",
            "ميه" to "مَيَّة",
            "ميّه" to "مَيَّة",
            "ماء" to "مَاء",
            "ساعدني" to "سَاعِدْنِي",
            "مساعدة" to "مُسَاعَدَة",
            "عايز اكل" to "عَايِز أَكْل",
            "عايزة اكل" to "عَايِزَة أَكْل",
            "عايزه اكل" to "عَايِزَة أَكْل",
            "عايز" to "عايِز",
            "عايزة" to "عايْزَة",
            "عايزه" to "عايْزَة",
            "اكل" to "أَكْل",
            "اشرب" to "أَشْرَب",
            "حمام" to "حَمَّام",
            "الحمام" to "الْحَمَّام",
            "حليب" to "حَلِيب",
            "نعم" to "نَعَمْ",
            "لا" to "لَا",
            "تعبان" to "تَعْبَان",
            "تعبانة" to "تَعْبَانَة",
            "مريض" to "مَرِيض",
            "مريضة" to "مَرِيضَة",
            "انام" to "أَنَام",
            "نام" to "نَام",
            "لعبة" to "لُعْبَة",
            "برا" to "بَرَّا"
        )
        
        var result = clean
        for ((key, value) in tashkeelMap) {
            result = result.replace(Regex("(?<![\\p{L}\\p{N}])$key(?![\\p{L}\\p{N}])"), value)
        }
        return result
    }

    fun applyTashkeel(text: String): String = apply(text)
}
