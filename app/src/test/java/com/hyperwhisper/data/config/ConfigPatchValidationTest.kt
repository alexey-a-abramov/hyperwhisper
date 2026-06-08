package com.hyperwhisper.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation/normalization of LLM patch output: fuzzy enum matching, spoken
 * language names → ISO codes, bool synonyms, range checks, unknown paths,
 * and no-op dropping.
 */
class ConfigPatchValidationTest {

    private val snapshot = ConfigTestFixtures.defaultSnapshot()

    private fun parse(changesJson: String) =
        ConfigPatchParser.parseLlmOutput("""{"changes": $changesJson}""", snapshot)!!

    @Test
    fun fuzzyEnumValueNormalizes() {
        val patch = parse("""[{"path": "appearance.colorScheme", "value": "ruby red"}]""")
        assertEquals(0, patch.errors.size)
        assertEquals(1, patch.valid.size)
        assertEquals("RUBY_RED", patch.valid[0].newValue)
        assertEquals("Ruby Red", patch.valid[0].newDisplay)
    }

    @Test
    fun languageNameResolvesToIsoCode() {
        val patch = parse("""[{"path": "transcription.inputLanguage", "value": "Spanish"}]""")
        assertEquals("es", patch.valid.single().newValue)
    }

    @Test
    fun autoDetectWordsResolveToEmptyLanguage() {
        // default inputLanguage is "" → auto resolves to "" → no-op dropped
        val patch = parse("""[{"path": "transcription.inputLanguage", "value": "auto"}]""")
        assertTrue(patch.isEmpty)
    }

    @Test
    fun boolSynonymsResolve() {
        val patch = parse("""[{"path": "appearance.techieMode", "value": "on"}]""")
        assertEquals(true, patch.valid.single().newValue)
    }

    @Test
    fun nativeBooleanAccepted() {
        val patch = parse("""[{"path": "appearance.techieMode", "value": true}]""")
        assertEquals(true, patch.valid.single().newValue)
    }

    @Test
    fun outOfRangeIntRejected() {
        val patch = parse("""[{"path": "localModels.threads", "value": 99}]""")
        assertTrue(patch.valid.isEmpty())
        assertEquals("localModels.threads", patch.errors.single().path)
    }

    @Test
    fun unknownPathRejected() {
        val patch = parse("""[{"path": "foo.bar", "value": "x"}]""")
        assertEquals("Unknown setting", patch.errors.single().reason)
    }

    @Test
    fun noOpChangesDropped() {
        // OCEAN_DEEP is the default color scheme
        val patch = parse("""[{"path": "appearance.colorScheme", "value": "OCEAN_DEEP"}]""")
        assertTrue(patch.isEmpty)
    }

    @Test
    fun keyboardModeSynonymResolves() {
        val patch = parse("""[{"path": "appearance.keyboardMode", "value": "code"}]""")
        assertEquals("CODE", patch.valid.single().newValue)
    }

    @Test
    fun enumSetReplacesWholeSet() {
        val patch = parse(
            """[{"path": "appearance.enabledKeyboardLayouts", "value": ["english", "RUSSIAN"]}]"""
        )
        assertEquals(setOf("ENGLISH", "RUSSIAN"), patch.valid.single().newValue)
    }

    @Test
    fun mixedValidAndInvalidChangesKeepBoth() {
        val patch = parse(
            """[
                {"path": "appearance.darkMode", "value": "dark"},
                {"path": "nope.nope", "value": 1}
            ]"""
        )
        assertEquals(1, patch.valid.size)
        assertEquals(1, patch.errors.size)
        assertEquals("DARK", patch.valid[0].newValue)
    }

    @Test
    fun bareArrayOutputAccepted() {
        val patch = ConfigPatchParser.parseLlmOutput(
            """[{"path": "appearance.darkMode", "value": "dark"}]""",
            snapshot,
        )!!
        assertEquals("DARK", patch.valid.single().newValue)
    }

    @Test
    fun proseAroundJsonAccepted() {
        val patch = ConfigPatchParser.parseLlmOutput(
            "Here you go:\n{\"changes\": [{\"path\": \"appearance.darkMode\", \"value\": \"dark\"}]}\nDone!",
            snapshot,
        )!!
        assertEquals("DARK", patch.valid.single().newValue)
    }

    @Test
    fun unparseableOutputReturnsNull() {
        assertNull(ConfigPatchParser.parseLlmOutput("I could not understand the request.", snapshot))
    }

    @Test
    fun voiceModeSelectionResolvesByName() {
        val patch = parse("""[{"path": "voiceModes.selected", "value": "Configuration"}]""")
        assertEquals("configuration", patch.valid.single().newValue)
    }
}
