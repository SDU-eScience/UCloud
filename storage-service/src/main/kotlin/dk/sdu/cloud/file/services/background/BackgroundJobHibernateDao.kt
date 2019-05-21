package dk.sdu.cloud.file.services.background

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import io.ktor.http.HttpStatusCode
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "background_jobs")
internal class BackgroundJobEntity(
    @get:Id
    @get:NaturalId
    var jobId: String,

    var requestType: String,
    var requestMessage: String,

    var owner: String,

    var responseCode: Int = -1,
    var response: String? = null,

    override var createdAt: Date = Date(),
    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<BackgroundJobEntity>, WithId<String>
}

internal class BackgroundJobHibernateDao : BackgroundJobDao<HibernateSession> {
    override fun findOrNull(session: HibernateSession, jobId: String, user: String): BackgroundJob? {
        val entity = session.criteria<BackgroundJobEntity> {
            entity[BackgroundJobEntity::jobId] equal jobId
        }.uniqueResult() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (entity.owner != user) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        val request = BackgroundRequest(
            jobId = entity.jobId,
            requestType = entity.requestType,
            requestMessage = entity.requestMessage,
            owner = entity.owner
        )

        val response = run {
            val responseCode = entity.responseCode
            val response = entity.response

            if (responseCode >= 0 && response != null) {
                BackgroundResponse(responseCode, response)
            } else {
                null
            }
        }

        return BackgroundJob(request, response)
    }

    override fun create(session: HibernateSession, request: BackgroundRequest) {
        val entity = with (request) {
            BackgroundJobEntity(jobId, requestType, requestMessage, owner)
        }

        session.save(entity)
    }

    override fun setResponse(session: HibernateSession, jobId: String, response: BackgroundResponse) {
        val entity = session.criteria<BackgroundJobEntity> {
            entity[BackgroundJobEntity::jobId] equal jobId
        }.uniqueResult() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        entity.responseCode = response.responseCode
        entity.response = response.response

        session.update(entity)
    }
}
