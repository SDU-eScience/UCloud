package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import io.mockk.mockk
import org.junit.Test
import sun.security.util.Password
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDAOTest {
    private val passwordHashingService = PasswordHashingService()
    private val personService = PersonService(passwordHashingService, mockk(relaxed = true))

    @Test
    fun `create a user byPassword and check correct and wrong password`() {
        val email = "test@testmail.com"
        val person = personService.createUserByPassword(
            "FirstName Middle",
            "Lastname",
            email,
            Role.ADMIN,
            "ThisIsMyPassword"
        )
        assertEquals("FirstName Middle", person.firstNames)
        assertEquals("Lastname", person.lastName)
        assertEquals(email, person.emailAddresses.first())
        assertEquals(Role.ADMIN, person.role)

        assertTrue(passwordHashingService.checkPassword(person.password, person.salt, "ThisIsMyPassword"))

        assertFalse(passwordHashingService.checkPassword(person.password, person.salt, "ThisIsMyPasword"))
    }

}
