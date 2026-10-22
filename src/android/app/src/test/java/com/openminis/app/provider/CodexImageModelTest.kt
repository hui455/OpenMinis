package com.openminis.app.provider

import com.openminis.app.data.model.LLMMessage
import com.openminis.app.data.model.LLMModel
import com.openminis.app.provider.openai.OpenAIProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [T-codex-gpt-image25-android] Pins what actually goes on the wire for the
 * Codex image models.
 *
 * The distinction these cover is invisible in a diff: gpt-image-2 sends a bare
 * `{type:image_generation}` and lets the backend pick the model, while the 2.5
 * variants name themselves in that same object. Getting it backwards would
 * either pin gpt-image-2's behaviour to something the backend currently
 * chooses, or silently route sunburst/flare to the default model — and both
 * failures look like a correct request in the logs.
 *
 * `buildCodexImageBody` is private and the Codex constructor hardcodes
 * chatgpt.com, so this reaches it reflectively rather than through
 * MockWebServer. That is the honest trade: it tests body construction, not the
 * transport.
 */
class CodexImageModelTest {

    private fun imageModel(id: String) = LLMModel(
        id = id,
        displayName = id,
        provider = "OpenAI",
        inputModalities = listOf("text", "image"),
        outputModalities = listOf("image"),
    )

    private fun bodyFor(modelId: String, responseModel: String? = null): JSONObject {
        val provider = OpenAIProvider(
            oauthTokenProvider = { "test-token" },
            model = imageModel(modelId),
        )
        provider.modelOverrides = com.openminis.app.data.model.ModelOverrides(codexImageResponseModel = responseModel)
        val m = OpenAIProvider::class.java
            .getDeclaredMethod("buildCodexImageBody", List::class.java)
            .apply { isAccessible = true }
        val messages = listOf(LLMMessage(role = LLMMessage.Role.USER, content = "a red bicycle"))
        return m.invoke(provider, messages) as JSONObject
    }

    @Test
    fun `calling model override preserves the selected image tool and prompt`() {
        val body = bodyFor("gpt-image-2.5-sunburst", "  account-text-model  ")
        assertEquals("account-text-model", body.getString("model"))
        assertEquals("gpt-image-2.5-sunburst", imageTool(body).getString("model"))
        assertTrue(body.getJSONArray("input").getJSONObject(0).getString("content").contains("a red bicycle"))
        assertTrue(body.getBoolean("stream"))
        assertFalse(body.getBoolean("store"))
    }

    @Test
    fun `blank calling model retains the legacy default`() {
        assertEquals("gpt-5.5", bodyFor("gpt-image-2", "  ").getString("model"))
    }

    @Test
    fun `image and malformed calling model ids fail before a request is sent`() {
        for (id in listOf("gpt-image-2.5-sunburst", "bad model", "bad\nmodel")) {
            try {
                bodyFor("gpt-image-2", id)
                org.junit.Assert.fail("Expected rejection for $id")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                assertTrue(e.cause is IllegalArgumentException)
            }
        }
    }

    @Test
    fun `calling model setting survives serialization and is not empty`() {
        val settings = com.openminis.app.data.model.ModelOverrides(codexImageResponseModel = "account-text-model")
        assertFalse(settings.isEmpty)
        val serializer = com.openminis.app.data.model.ModelOverrides.serializer()
        val encoded = kotlinx.serialization.json.Json.encodeToString(serializer, settings)
        assertEquals(settings, kotlinx.serialization.json.Json.decodeFromString(serializer, encoded))
        assertEquals(null, kotlinx.serialization.json.Json.decodeFromString(serializer, "{}").codexImageResponseModel)
    }

    private fun imageTool(body: JSONObject): JSONObject =
        body.getJSONArray("tools").getJSONObject(0)

    @Test
    fun `gpt-image-2 sends a bare image_generation tool`() {
        val tool = imageTool(bodyFor("gpt-image-2"))
        assertEquals("image_generation", tool.getString("type"))
        assertFalse(
            "gpt-image-2 must not pin a model — the backend picks it",
            tool.has("model"),
        )
    }

    @Test
    fun `the 2 point 5 variants name themselves in the tool`() {
        for (id in listOf("gpt-image-2.5-sunburst", "gpt-image-2.5-flare")) {
            val tool = imageTool(bodyFor(id))
            assertEquals("image_generation", tool.getString("type"))
            assertEquals("tool must name $id", id, tool.getString("model"))
        }
    }

    /**
     * The envelope is the same for all three: the wire model stays gpt-5.5 and
     * only the tool object varies. A change here would mean the 2.5 variants
     * took a different request shape, not just a different image model.
     */
    @Test
    fun `every codex image model keeps the same wire envelope`() {
        for (id in listOf("gpt-image-2", "gpt-image-2.5-sunburst", "gpt-image-2.5-flare")) {
            val body = bodyFor(id)
            assertEquals("wire model for $id", "gpt-5.5", body.getString("model"))
            assertTrue("$id must still stream", body.getBoolean("stream"))
            assertEquals("$id must send exactly one tool", 1, body.getJSONArray("tools").length())
        }
    }

    /** The prompt is carried through, so a body that builds is not an empty one. */
    @Test
    fun `the user prompt reaches the request`() {
        val input = bodyFor("gpt-image-2.5-flare").getJSONArray("input").getJSONObject(0)
        assertTrue(input.getString("content").contains("a red bicycle"))
    }
}
