package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.Test

class UserDAOTest{

    private fun withDatabase(closure: () -> Unit) {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(Principals)
        }

        try {
            closure()
        } finally {
            transaction {
                SchemaUtils.drop(Principals)
            }
        }
    }

    @Test
    fun `Testing this`() {
        withDatabase {
            val userDao = UserDAO
            transaction {
                Principals.insert {
                    it[id] = "idOfPrincipal"
                    it[loginType] = "PASSWORD"
                    it[role] = "USER"

                }
            }
        }
    }
}