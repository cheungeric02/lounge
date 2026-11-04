package ca.cheungeric.lounge

import ca.cheungeric.lounge.ui.patchnotes.APP_VERSION
import ca.cheungeric.lounge.ui.patchnotes.PATCH_NOTES
import org.junit.Assert.assertEquals
import org.junit.Test

class PatchNotesTest {
    @Test
    fun latestPatchNoteMatchesAppVersion() {
        assertEquals(APP_VERSION, PATCH_NOTES.first().v)
    }
}
