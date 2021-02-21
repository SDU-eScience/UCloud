package dk.sdu.cloud.file.api

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizationTest {
    @Test
    fun `test root normalization`() {
        assertEquals("/", "/".normalize())
    }

    @Test
    fun `test current directory`() {
        assertEquals("/", "/././././././././././././././././././././././././././../././".normalize())
        assertEquals("/", "/./".normalize())
        assertEquals("/", "/.".normalize())
    }

    @Test
    fun `test parent directory`() {
        assertEquals("/", "/foo/..".normalize())
        assertEquals("/", "/foo/..".normalize())
        assertEquals("/", "/..".normalize())
        assertEquals("/", "/../../../../../../../../../../".normalize())
        assertEquals("/", "/../../../../../../../../../..".normalize())
    }

    @Test
    fun `test normalized path`() {
        assertEquals("/normal/path/nothing/special", "/normal/path/nothing/special".normalize())
        assertEquals("/normal/path/nothing/special", "/normal/path/nothing/special/".normalize())
    }

    @Test
    fun `test case from existing test`() {
        assertEquals("/home/user1/folder/./../folder/./", "/home/user1/folder/./../folder/./Weirdpath".parent())
        assertEquals("/home/user1/folder", "/home/user1/folder/./../folder/./Weirdpath".parent().normalize())
    }
}
