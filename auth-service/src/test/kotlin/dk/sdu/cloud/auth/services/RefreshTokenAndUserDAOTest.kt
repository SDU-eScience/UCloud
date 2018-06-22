package dk.sdu.cloud.auth.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals

class RefreshTokenAndUserDAOTest{

    private val associatedUser = "associatedUser"

    private fun withDatabase(closure: () -> Unit) {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(Principals)
            SchemaUtils.create(RefreshTokens)
        }

        try {
            closure()
        } finally {
            transaction {
                SchemaUtils.drop(RefreshTokens)
                SchemaUtils.drop(Principals)
            }
        }
    }

    private val refresh = RefreshTokenAndUser(
        associatedUser,
        "ThisIsAToken"
    )

    @Test
    fun `Test this`() {
        withDatabase {
            val refreshTokenAndUser = RefreshTokenAndUserDAO
            //refreshTokenAndUser.insert(refresh)

            transaction {
                for (token in RefreshTokens.selectAll()) {
                    assertEquals("ThisIsAToken",token[RefreshTokens.token])
                    assertEquals("associatedUser", token[RefreshTokens.associatedUser])
                }
            }
        }
    }
}