package ca.cheungeric.lounge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MoveFavoriteToTest {

    private val base = LayoutPrefsState(favorites = listOf("a.a", "b.b", "c.c", "d.d"), initialized = true)

    private fun move(p: LayoutPrefsState, pkg: String, to: Int, installed: Collection<String>? = null) =
        HomeLayout.moveFavoriteTo(p, pkg, to, installed).favorites

    @Test
    fun movesForwardAndBack() {
        assertEquals(listOf("b.b", "c.c", "a.a", "d.d"), move(base, "a.a", 2))
        assertEquals(listOf("d.d", "a.a", "b.b", "c.c"), move(base, "d.d", 0))
        assertEquals(listOf("a.a", "c.c", "b.b", "d.d"), move(base, "c.c", 1))
    }

    @Test
    fun clampsIndex() {
        assertEquals(listOf("b.b", "c.c", "d.d", "a.a"), move(base, "a.a", 99))
        assertEquals(listOf("c.c", "a.a", "b.b", "d.d"), move(base, "c.c", -5))
        assertEquals(listOf("b.b", "c.c", "d.d", "a.a"), move(base, "a.a", Int.MAX_VALUE))
    }

    @Test
    fun edges_noopSameInstance() {
        assertSame(base, HomeLayout.moveFavoriteTo(base, "a.a", 0))
        assertSame(base, HomeLayout.moveFavoriteTo(base, "d.d", 3))
        assertSame(base, HomeLayout.moveFavoriteTo(base, "d.d", 50))
        val single = base.copy(favorites = listOf("a.a"))
        assertSame(single, HomeLayout.moveFavoriteTo(single, "a.a", 3))
    }

    @Test
    fun nonFavoriteOrHidden_noop() {
        assertSame(base, HomeLayout.moveFavoriteTo(base, "z.z", 1))
        val hidden = base.copy(hidden = setOf("b.b"))
        assertSame(hidden, HomeLayout.moveFavoriteTo(hidden, "b.b", 0))
        val empty = LayoutPrefsState()
        assertSame(empty, HomeLayout.moveFavoriteTo(empty, "a.a", 0))
    }

    @Test
    fun indexIsInVisibleRow_invisibleKeepSlots() {
        // b.b hidden, c.c uninstalled: visible row = [a, d]; move d to 0.
        val p = base.copy(hidden = setOf("b.b"))
        assertEquals(
            listOf("d.d", "b.b", "c.c", "a.a"),
            move(p, "d.d", 0, installed = listOf("a.a", "b.b", "d.d")),
        )
        // Without installed info, c.c is treated as visible: row = [a, c, d].
        assertEquals(listOf("a.a", "b.b", "d.d", "c.c"), move(p, "d.d", 1))
        // A favorite that isn't installed can't be moved.
        assertSame(p, HomeLayout.moveFavoriteTo(p, "c.c", 0, listOf("a.a", "d.d")))
    }
}
