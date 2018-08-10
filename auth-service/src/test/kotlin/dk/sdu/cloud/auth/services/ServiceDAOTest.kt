package dk.sdu.cloud.auth.services

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceDAOTest {
    private val servicename = "nameOfMyService"
    private val endpoint = "endpoint"
    private val service = Service(servicename, endpoint)

    @Test
    fun `insert and find (duplicates and non find case also) test`() {
        val serviceDAO = ServiceDAO
        assertTrue(serviceDAO.insert(service))
        //Cannot insert a service with the same name twice
        assertFalse(serviceDAO.insert(service))

        val resultService = serviceDAO.findByName(servicename)
        assertEquals(servicename, resultService?.name)
        assertEquals(endpoint, resultService?.endpoint)

        assertNull(serviceDAO.findByName("notHere"))
    }
}