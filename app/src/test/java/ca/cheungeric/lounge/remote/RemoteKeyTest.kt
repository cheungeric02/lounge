package ca.cheungeric.lounge.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

class RemoteKeyTest {

    @Test
    fun generate_isValid_fourWords() {
        val rng = Random(42)
        repeat(500) {
            val k = RemoteKey.generate(rng)
            assertTrue(k, RemoteKey.isValid(k))
            assertEquals(4, k.split('-').size)
        }
        assertTrue(RemoteKey.isValid(RemoteKey.generate()))
    }

    @Test
    fun normalize_acceptsUrlCaseWhitespace() {
        val k = "maple-otter-river-cloud"
        assertEquals(k, RemoteKey.normalize(k))
        assertEquals(k, RemoteKey.normalize("  MAPLE-Otter-river-CLOUD \n"))
        assertEquals(k, RemoteKey.normalize(RemoteKey.pairingUrl(k)))
        assertEquals(k, RemoteKey.normalize(" https://cheungeric02.github.io/lounge/remote/#k=Maple-Otter-River-Cloud "))
        assertEquals(k, RemoteKey.normalize("https://cheungeric02.github.io/lounge/remote/#x=1&k=$k"))
    }

    @Test
    fun rejectsBad() {
        for (bad in listOf(
            null, "", "maple-otter-river", "maple-otter-river-cloud-tiger", "maple otter river cloud",
            "maple_otter_river_cloud", "maple-otter-river-zzzz", "maple--otter-river", "maple-otter-river-cloud-",
            "https://cheungeric02.github.io/lounge/remote/", "https://cheungeric02.github.io/lounge/remote/#k=",
            "https://x/#k=maple-otter-river-nope",
        )) {
            assertNull(bad, RemoteKey.normalize(bad))
        }
        assertFalse(RemoteKey.isValid("Maple-otter-river-cloud")) // validation is strict; normalize lowercases
    }

    @Test
    fun pairingUrl_format() {
        assertEquals(
            "https://cheungeric02.github.io/lounge/remote/#k=maple-otter-river-cloud",
            RemoteKey.pairingUrl("maple-otter-river-cloud"),
        )
    }

    @Test
    fun firebasePathGuard() {
        val base = "https://db.example"
        assertEquals(
            "$base/lounge/maple-otter-river-cloud/tv/icons/com%2Cnetflix%2Cninja.json",
            FirebaseRest.urlFor(base, "lounge/maple-otter-river-cloud/tv/icons/com,netflix,ninja"),
        )
        assertEquals("$base/lounge/maple-otter-river-cloud.json", FirebaseRest.urlFor(base, "lounge/maple-otter-river-cloud"))
        for (bad in listOf("", "lounge", "lounge/", "atlas/main", "lounge/not-a-valid-key-x/tv",
            "lounge/maple-otter-river-cloud/../x", "lounge/maple-otter-river-cloud//x", "/lounge/maple-otter-river-cloud/tv")) {
            try {
                FirebaseRest.urlFor(base, bad)
                throw AssertionError("accepted $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}

class WordListTest {
    @Test
    fun exactly256_distinct_shape() {
        val w = WordList.words
        assertEquals(256, w.size)
        assertEquals(256, w.toSet().size)
        assertTrue(w.all { Regex("[a-z]{3,6}").matches(it) })
    }

    @Test
    fun matchesWebWordsFile() {
        // Gradle unit tests run with working dir = app/.
        val f = listOf(File("../remote/words.txt"), File("remote/words.txt")).first { it.exists() }
        val fileWords = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(WordList.words, fileWords)
    }
}
