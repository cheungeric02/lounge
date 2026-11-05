package ca.cheungeric.lounge.remote

import ca.cheungeric.lounge.remote.LinkRouter.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRouterTest {

    private fun pkg(url: String) = LinkRouter.route(url)?.pkg

    @Test
    fun youtube_hosts() {
        for (u in listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://youtu.be/dQw4w9WgXcQ",
        )) {
            assertEquals(u, LinkRouter.YOUTUBE, pkg(u))
        }
        // Watch URLs pass through unchanged.
        assertEquals(
            Route("https://m.youtube.com/watch?v=dQw4w9WgXcQ", LinkRouter.YOUTUBE),
            LinkRouter.route("https://m.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun youtuBe_normalisedToWatch() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            LinkRouter.route("https://youtu.be/dQw4w9WgXcQ")!!.url,
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            LinkRouter.route("https://youtu.be/dQw4w9WgXcQ?si=abcDEF123")!!.url,
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42",
            LinkRouter.route("https://youtu.be/dQw4w9WgXcQ?si=x&t=42")!!.url,
        )
    }

    @Test
    fun shorts_normalisedToWatch() {
        assertEquals(
            Route("https://www.youtube.com/watch?v=abcdEFGH123", LinkRouter.YOUTUBE),
            LinkRouter.route("https://www.youtube.com/shorts/abcdEFGH123?feature=share"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=abcdEFGH123",
            LinkRouter.route("https://m.youtube.com/shorts/abcdEFGH123")!!.url,
        )
    }

    @Test
    fun netflix_titleAndWatchOnly() {
        assertEquals(LinkRouter.NETFLIX, pkg("https://www.netflix.com/title/80057281"))
        assertEquals(LinkRouter.NETFLIX, pkg("https://www.netflix.com/watch/80057281?trackId=1"))
        assertEquals(LinkRouter.NETFLIX, pkg("https://netflix.com/ca/title/80057281"))
        // Other Netflix pages → plain VIEW.
        assertNull(pkg("https://www.netflix.com/browse"))
        assertTrue(LinkRouter.route("https://www.netflix.com/browse") != null)
    }

    @Test
    fun spotify_prime_disney() {
        assertEquals(LinkRouter.SPOTIFY, pkg("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertNull(pkg("https://www.spotify.com/ca-en/premium/"))
        assertEquals(LinkRouter.PRIME, pkg("https://www.primevideo.com/detail/0ABC"))
        assertEquals(LinkRouter.PRIME, pkg("https://primevideo.com/"))
        assertEquals(LinkRouter.PRIME, pkg("https://www.amazon.com/gp/video/detail/B0ABC"))
        assertEquals(LinkRouter.PRIME, pkg("https://www.amazon.ca/gp/video/detail/B0ABC"))
        assertEquals(LinkRouter.PRIME, pkg("https://www.amazon.co.uk/gp/video/storefront"))
        assertNull(pkg("https://www.amazon.ca/dp/B0ABC"))
        assertEquals(LinkRouter.DISNEY, pkg("https://www.disneyplus.com/video/abc"))
        assertEquals(LinkRouter.DISNEY, pkg("https://disneyplus.com/"))
    }

    @Test
    fun otherHosts_plainView() {
        val r = LinkRouter.route("https://example.com/a?b=c")!!
        assertEquals(Route("https://example.com/a?b=c", null), r)
        assertEquals(listOf<String?>(null), r.targets)
        // Look-alike hosts don't map.
        assertNull(pkg("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(pkg("https://youtube.com.evil.example/watch?v=x"))
    }

    @Test
    fun mappedTarget_fallsBackToPlainView() {
        assertEquals(listOf(LinkRouter.YOUTUBE, null), LinkRouter.route("https://youtu.be/dQw4w9WgXcQ")!!.targets)
    }

    @Test
    fun rejects_nonHttp_tooLong_garbage() {
        assertNull(LinkRouter.route("intent://foo#Intent;scheme=x;end"))
        assertNull(LinkRouter.route("javascript:alert(1)"))
        assertNull(LinkRouter.route("file:///sdcard/x.mp4"))
        assertNull(LinkRouter.route("content://x/y"))
        assertNull(LinkRouter.route("market://details?id=x"))
        assertNull(LinkRouter.route("ftp://example.com/"))
        assertNull(LinkRouter.route("example.com"))
        assertNull(LinkRouter.route("https://"))
        assertNull(LinkRouter.route("https://exa mple.com/"))
        assertNull(LinkRouter.route(""))
        assertNull(LinkRouter.route(null))
        val long = "https://example.com/" + "a".repeat(LinkRouter.MAX_LEN)
        assertNull(LinkRouter.route(long))
        val ok = "https://example.com/" + "a".repeat(LinkRouter.MAX_LEN - "https://example.com/".length)
        assertEquals(LinkRouter.MAX_LEN, ok.length)
        assertTrue(LinkRouter.isAcceptable(ok))
        assertFalse(LinkRouter.isAcceptable("HTTPS:/x"))
        assertTrue(LinkRouter.isAcceptable("HTTPS://EXAMPLE.COM/"))
    }

    @Test
    fun targetPackagesCoverAllMappings() {
        assertEquals(
            setOf(LinkRouter.YOUTUBE, LinkRouter.NETFLIX, LinkRouter.SPOTIFY, LinkRouter.PRIME, LinkRouter.DISNEY),
            LinkRouter.TARGET_PACKAGES.toSet(),
        )
    }
}
