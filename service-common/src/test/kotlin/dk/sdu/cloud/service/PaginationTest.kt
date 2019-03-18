package dk.sdu.cloud.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaginationTest {

    @Test
    fun `Test pagination more items than itemsPerPage`() {
        val p = Page(11, 10, 0, listOf("items"))
        assertEquals(11, p.itemsInTotal)
        assertEquals(10, p.itemsPerPage)
        assertEquals(0, p.pageNumber)
        assertEquals(2, p.pagesInTotal)
        assertEquals("items", p.items.first())
    }

    @Test
    fun `Test pagination exact number of items as itemsPerPage`() {
        val p = Page(10, 10, 0, listOf(null))
        assertEquals(10, p.itemsInTotal)
        assertEquals(1, p.pagesInTotal)

    }

    @Test
    fun `Test pagination 0 content`() {
        val p = Page(0, 10, 0, listOf(null))
        assertEquals(0, p.itemsInTotal)
        assertEquals(0, p.pagesInTotal)
    }

    @Test
    fun `Test pagination less items than itemPerPage `() {
        val p = Page(1, 10, 0, listOf("item"))
        assertEquals(1, p.itemsInTotal)
        assertEquals(1, p.pagesInTotal)
        val p2 = Page(5, 10, 0, listOf("item"))
        assertEquals(5, p2.itemsInTotal)
        assertEquals(1, p2.pagesInTotal)
    }

    @Test
    fun `Test normalized pagination Request`() {
        val p = PaginationRequest(10, 0).normalize()
        assertEquals(10, p.itemsPerPage)
        assertEquals(0, p.page)

    }

    @Test
    fun `Test normalized pagination Request - Not listed number of itemsPerPage`() {
        val p = PaginationRequest(12, 0).normalize()
        assertEquals(50, p.itemsPerPage)
        assertEquals(0, p.page)
    }

    @Test
    fun `Test normalized pagination Request - Page not given`() {
        val p = PaginationRequest(10).normalize()
        assertEquals(10, p.itemsPerPage)
        assertEquals(0, p.page)
    }

    @Test
    fun `Test paginate list`() {
        val p = listOf("hello", "new", "list").paginate(PaginationRequest(10, 0).normalize())
        assertEquals(10, p.itemsPerPage)
        assertEquals("hello", p.items.first())
        assertEquals("list", p.items.last())
        assertEquals(3, p.itemsInTotal)
        assertEquals(1, p.pagesInTotal)
    }

    @Test
    fun `Test paginate list - requested non available page`() {
        val p = listOf("hello", "new", "list").paginate(PaginationRequest(10, 2).normalize())
        assertEquals(10, p.itemsPerPage)
        assertTrue(p.items.isEmpty())
        assertEquals(3, p.itemsInTotal)
        assertEquals(1, p.pagesInTotal)
    }

    @Test
    fun `Test mapping Page`() {
        val p = Page(3, 10, 0, listOf(2, 4, 9))
        val z = p.mapItems { i: Int -> i * 2 }
        assertEquals(18, z.items.last())
        assertEquals(4, z.items.first())
        assertEquals(3, z.itemsInTotal)
        assertEquals(0, z.pageNumber)
        assertEquals(10, z.itemsPerPage)
    }
}
