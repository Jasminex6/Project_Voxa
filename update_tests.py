import re

file_path = 'D:/programming/Ai-Nexus/Basma/Project_Voxa/Voxa/app/src/test/java/com/example/voxa/AiBridgeTest.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace 2048 with 1024
text = text.replace('2048', '1024')

# Replace the YamnetEncoder test methods entirely
yamnet_tests_pattern = re.compile(
    r'@Test\s+fun testYamnetEncoder_prepareAudioWindow_tilesShortSignal\(\) \{.*?(?=@Test\s+fun testPrototypicalMatcher_qcOutlierRejection)',
    re.DOTALL
)

new_yamnet_tests = """@Test
    fun testYamnetEncoder_prepareAudioWindow_padsShortSignal() {
        // 1000 samples is short (< 15360)
        val pcm = ShortArray(1000) { it.toShort() }
        val prepared = YamnetEncoder.prepareAudioWindow(pcm)
        assertEquals(15360, prepared.size)
        
        // Element at index 1000 should be equal to index 0 due to repeat padding
        assertEquals(prepared[0], prepared[1000], 0.0001f)
    }

    @Test
    fun testYamnetEncoder_prepareAudioWindow_leavesLongSignal() {
        // 30000 samples is long (>= 15360)
        val pcm = ShortArray(30000) { it.toShort() }
        val prepared = YamnetEncoder.prepareAudioWindow(pcm)
        assertEquals(30000, prepared.size) // No cropping!
    }

    """

text = yamnet_tests_pattern.sub(new_yamnet_tests, text)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)

print("Tests updated.")
