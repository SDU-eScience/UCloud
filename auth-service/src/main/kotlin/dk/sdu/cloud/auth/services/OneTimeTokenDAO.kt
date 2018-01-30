package dk.sdu.cloud.auth.services

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException

object OTTBlackListTable : Table() {
    val jti = varchar("jti", 36).primaryKey()
    val claimedBy = varchar("claimed_by", 256)
}


object OneTimeTokenDAO {
    private val log = LoggerFactory.getLogger(OneTimeTokenDAO::class.java)

    fun claim(jti: String, claimedBy: String): Boolean {
        val result = try {
            transaction {
                OTTBlackListTable.insert {
                    it[OTTBlackListTable.jti] = jti
                    it[OTTBlackListTable.claimedBy] = claimedBy
                }
            }
        } catch (ex: SQLException) {
            null
        }

        return result != null
    }
}