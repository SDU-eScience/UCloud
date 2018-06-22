package dk.sdu.cloud.auth.services

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneTimeTokenDAOTest{

    @Test
    fun `claim one time token test`() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(OTTBlackListTable)
        }

        val oneTimeToken = OneTimeTokenDAO
        assertTrue(oneTimeToken.claim("jti", "me"))

        transaction {
            for (ott in OTTBlackListTable.selectAll()) {
                assertEquals("jti",ott[OTTBlackListTable.jti])
                assertEquals("me", ott[OTTBlackListTable.claimedBy])
            }
        }

        transaction {
            SchemaUtils.drop(OTTBlackListTable)
        }

    }

    @Test
    fun `claim one time token - transaction failure - test`() {

        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(OTTBlackListTable)
        }

        val oneTimeToken = OneTimeTokenDAO
        assertFalse(oneTimeToken.claim("ThisIsAStringThatIsLongerThan36CharactersSoThatItWillFail", "me"))

        transaction {
            SchemaUtils.drop(OTTBlackListTable)
        }
    }
}
