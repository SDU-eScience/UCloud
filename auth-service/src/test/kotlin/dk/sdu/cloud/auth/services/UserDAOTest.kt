package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Role
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDAOTest{

    @Test
    fun `create a user byPassword and check correct and wrong password`() {
        val person = PersonUtils.createUserByPassword(
            "FirstName Middle",
            "Lastname",
            "testmail.com",
            Role.ADMIN,
            "ThisIsMyPassword"
        )
        assertEquals("FirstName Middle", person.firstNames)
        assertEquals("Lastname", person.lastName)
        assertEquals("testmail.com", person.emailAddresses.first())
        assertEquals(Role.ADMIN, person.role)

        assertTrue(person.checkPassword("ThisIsMyPassword"))

        assertFalse(person.checkPassword("ThisIsMyPasword"))
    }


    //Sanitation of mail validity
}