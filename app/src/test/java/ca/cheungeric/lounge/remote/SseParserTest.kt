package ca.cheungeric.lounge.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    private fun parseAll(vararg chunks: String): List<SseEvent> {
        val p = SseParser()
        return chunks.flatMap { p.feed(it) }
    }

    @Test
    fun put_wholeNode() {
        val ev = parseAll("event: put\ndata: {\"path\":\"/\",\"data\":{\"a\":{\"type\":\"home\"}}}\n\n")
        assertEquals(listOf(SseEvent.Put("/", "{\"a\":{\"type\":\"home\"}}")), ev)
    }

    @Test
    fun put_nullData() {
        val ev = parseAll("event: put\ndata: {\"path\":\"/x\",\"data\":null}\n\n")
        assertEquals(listOf(SseEvent.Put("/x", "null")), ev)
    }

    @Test
    fun patch() {
        val ev = parseAll("event: patch\ndata: {\"path\":\"/\",\"data\":{\"k\":1}}\n\n")
        assertEquals(listOf(SseEvent.Patch("/", "{\"k\":1}")), ev)
    }

    @Test
    fun keepAlive_cancel_authRevoked() {
        val ev = parseAll(
            "event: keep-alive\ndata: null\n\n",
            "event: cancel\ndata: null\n\n",
            "event: auth_revoked\ndata: credential is no longer valid\n\n",
        )
        assertEquals(listOf(SseEvent.KeepAlive, SseEvent.Cancel, SseEvent.AuthRevoked), ev)
    }

    @Test
    fun splitAcrossChunks_everyCharacter() {
        val text = "event: put\ndata: {\"path\":\"/a\",\"data\":{\"type\":\"back\"}}\n\nevent: keep-alive\ndata: null\n\n"
        val p = SseParser()
        val out = text.map { p.feed(it.toString()) }.flatten()
        assertEquals(listOf(SseEvent.Put("/a", "{\"type\":\"back\"}"), SseEvent.KeepAlive), out)
    }

    @Test
    fun noEventUntilBlankLine() {
        val p = SseParser()
        assertTrue(p.feed("event: put\ndata: {\"path\":\"/\",\"data\":1}\n").isEmpty())
        assertEquals(listOf(SseEvent.Put("/", "1")), p.feed("\n"))
    }

    @Test
    fun crlf_includingSplitBetweenCrAndLf() {
        val p = SseParser()
        val out = p.feed("event: put\r\ndata: {\"path\":\"/\",\"data\":2}\r") +
            p.feed("\n\r\nevent: keep-alive\r\ndata: null\r\n\r\n")
        assertEquals(listOf(SseEvent.Put("/", "2"), SseEvent.KeepAlive), out)
    }

    @Test
    fun multiLineData_joinedWithNewline() {
        val ev = parseAll("event: put\ndata: {\"path\":\"/\",\ndata: \"data\":3}\n\n")
        assertEquals(listOf(SseEvent.Put("/", "3")), ev)
    }

    @Test
    fun commentsAndUnknownIgnored_malformedDropped() {
        val ev = parseAll(
            ": hello\n\n",
            "event: weird\ndata: x\n\n",
            "event: put\ndata: not json\n\n",
            "event: put\ndata: {\"data\":1}\n\n",
            "data: orphan\n\n",
            "event: keep-alive\ndata: null\n\n",
        )
        assertEquals(listOf(SseEvent.KeepAlive), ev)
    }
}
