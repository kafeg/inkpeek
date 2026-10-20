package io.github.kafeg.inkpeek.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class AiTest {
    /** One-connection-at-a-time HTTP server: [answer] gets the JSON body, returns (status, body). */
    private fun serve(answer: (JSONObject) -> Pair<Int, String>): ServerSocket = ServerSocket(0).also { ss ->
        thread(isDaemon = true) {
            while (!ss.isClosed) runCatching {
                ss.accept().use { s ->
                    val inp = s.getInputStream()
                    // Headers are ASCII; the body length is in bytes (the prompt has non-ASCII quotes).
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) head.append(inp.read().toChar())
                    val len = Regex("(?i)content-length: *(\\d+)").find(head)!!.groupValues[1].toInt()
                    val (code, out) = answer(JSONObject(String(inp.readNBytes(len), Charsets.UTF_8)))
                    val b = out.toByteArray()
                    s.getOutputStream().apply { write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(b); flush() }
                }
            }
        }
    }

    /** A server that rejects `reasoning_effort` gets the request again without it; the choice is remembered. */
    @Test fun reasoningFieldFallsBack() {
        val seen = ArrayList<Boolean>()
        val ss = serve { b -> seen += b.has("reasoning_effort")
            if (b.has("reasoning_effort")) 400 to """{"error":{"message":"unknown field"}}""" else 200 to """{"choices":[{"message":{"content":"дом"}}]}""" }
        val memo = HashMap<String, Int>()
        Ai.recall = { memo[it] }; Ai.remember = { k, v -> memo[k] = v }
        try {
            val url = "http://127.0.0.1:${ss.localPort}/v1"
            assertEquals("дом", Ai.ask(Provider.CUSTOM, "", "m", Action.ALL[0], "Haus", "Russian", url))
            assertEquals(listOf(true, false), seen)
            seen.clear()
            assertEquals("дом", Ai.ask(Provider.CUSTOM, "", "m", Action.ALL[1], "Haus", "Russian", url))
            assertEquals(listOf(false), seen) // remembered: one call
        } finally { ss.close() }
    }
}
