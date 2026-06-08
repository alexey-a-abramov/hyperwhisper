package com.hyperwhisper.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The JSONC reader must tolerate everything an LLM or a hand-editing user
 * throws at it: comments, trailing commas, prose around the JSON object.
 */
class JsoncParserTest {

    @Test
    fun parsesPlainJson() {
        val obj = JsoncParser.parseObject("""{"a": 1, "b": "x"}""")
        assertNotNull(obj)
        assertEquals(1, obj!!.get("a").asInt)
    }

    @Test
    fun stripsLineComments() {
        val obj = JsoncParser.parseObject(
            """
            {
              // the answer
              "a": 42
            }
            """.trimIndent()
        )
        assertEquals(42, obj!!.get("a").asInt)
    }

    @Test
    fun stripsBlockComments() {
        val obj = JsoncParser.parseObject("""{ /* hidden */ "a": /* mid */ 7 }""")
        assertEquals(7, obj!!.get("a").asInt)
    }

    @Test
    fun preservesCommentLikeContentInsideStrings() {
        val obj = JsoncParser.parseObject("""{"url": "http://example.com//path", "note": "a /* b */ c"}""")
        assertEquals("http://example.com//path", obj!!.get("url").asString)
        assertEquals("a /* b */ c", obj.get("note").asString)
    }

    @Test
    fun preservesEscapedQuotesInsideStrings() {
        val obj = JsoncParser.parseObject("""{"a": "say \"hi\" // not a comment"}""")
        assertEquals("say \"hi\" // not a comment", obj!!.get("a").asString)
    }

    @Test
    fun removesTrailingCommasInObjectsAndArrays() {
        val obj = JsoncParser.parseObject(
            """
            {
              "list": ["x", "y",],
              "nested": {"a": 1,},
            }
            """.trimIndent()
        )
        assertNotNull(obj)
        assertEquals(2, obj!!.getAsJsonArray("list").size())
        assertEquals(1, obj.getAsJsonObject("nested").get("a").asInt)
    }

    @Test
    fun extractsJsonFromSurroundingProse() {
        val obj = JsoncParser.parseObject(
            "Sure! Here are your changes:\n{\"changes\": []}\nLet me know if you need more."
        )
        assertNotNull(obj)
        assertEquals(0, obj!!.getAsJsonArray("changes").size())
    }

    @Test
    fun returnsNullWhenNoJsonPresent() {
        assertNull(JsoncParser.parseObject("no json here at all"))
        assertNull(JsoncParser.parseObject(""))
    }
}
