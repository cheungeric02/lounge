package ca.cheungeric.lounge.remote

import ca.cheungeric.lounge.remote.ProfilePicker.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePickerTest {

    private fun picker(vararg names: String, title: String = "Who’s watching?"): List<Node> {
        val nodes = mutableListOf(Node(null, null, false, -1), Node(title, null, false, 0))
        for (n in names) {
            val tile = nodes.size
            nodes += Node(null, null, true, 0)
            nodes += Node(n, null, false, tile)
        }
        nodes += Node("Add account", null, true, 0)
        return nodes
    }

    @Test fun picksClickableTileForProfile() {
        val nodes = picker("Eric", "Vanessa")
        val i = ProfilePicker.target(nodes, "Vanessa")!!
        assertTrue(nodes[i].clickable)
        assertEquals("Vanessa", nodes[i + 1].text)
    }

    @Test fun matchesFullNameAndCase() {
        val nodes = picker("Eric Cheung", "Vanessa Smith")
        val i = ProfilePicker.target(nodes, "eric")!!
        assertEquals("Eric Cheung", nodes[i + 1].text)
    }

    @Test fun noPrefixFalseMatch() {
        assertFalse(ProfilePicker.matches("Erica", "Eric"))
        assertTrue(ProfilePicker.matches("Eric, eric@gmail.com", "Eric"))
        assertTrue(ProfilePicker.matches("  eric  ", "Eric"))
    }

    @Test fun neverClicksOutsidePicker() {
        // A video titled "Eric" on the YouTube home screen must not be clicked.
        val home = listOf(Node(null, null, false, -1), Node(null, null, true, 0), Node("Eric", null, false, 1))
        assertNull(ProfilePicker.target(home, "Eric"))
    }

    @Test fun missingProfileIsNull() = assertNull(ProfilePicker.target(picker("Eric"), "Vanessa"))

    @Test fun pickerDetectedByStraightApostropheOrAddAccount() {
        assertTrue(ProfilePicker.isPickerScreen(picker("Eric", title = "Who's watching?")))
        assertTrue(ProfilePicker.isPickerScreen(listOf(Node("Add account", null, true, -1))))
    }

    @Test fun matchByContentDescription() {
        val nodes = picker() + Node(null, "Vanessa", true, 0)
        assertEquals(nodes.size - 1, ProfilePicker.target(nodes, "Vanessa"))
    }

    @Test fun normalizeProfile() {
        assertEquals("Eric", ProfilePicker.normalizeProfile("  Eric "))
        assertNull(ProfilePicker.normalizeProfile("   "))
        assertNull(ProfilePicker.normalizeProfile("x".repeat(41)))
    }
}
