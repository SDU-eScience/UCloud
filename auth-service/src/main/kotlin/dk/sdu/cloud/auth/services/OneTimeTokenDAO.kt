package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.db.HibernateSession
import org.hibernate.annotations.NaturalId
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

interface OneTimeTokenDAO<Session> {
    fun claim(session: Session, jti: String, claimedBy: String): Boolean
}

/**
 * Updated in:
 *
 * - V1__Initial.sql
 */
@Entity
@Table(name = "ott_black_list")
data class OTTBlackListEntity(
    @Id
    @NaturalId
    var jti: String,
    var claimedBy: String
)

class OneTimeTokenHibernateDAO : OneTimeTokenDAO<HibernateSession> {
    override fun claim(session: HibernateSession, jti: String, claimedBy: String): Boolean {
        val value = try {
            (session.save(OTTBlackListEntity(jti, claimedBy)) as String)
                .also {
                    // No exception is thrown if we don't flush immediately
                    session.flush()
                }
        } catch (ex: Exception) {
            null
        }

        return value != null
    }
}