package dk.sdu.cloud.bare.http

import dk.sdu.cloud.bare.api.Entity
import dk.sdu.cloud.bare.api.EntityDescriptions
import dk.sdu.cloud.bare.services.SampleEntity
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.list
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.routing.Route

class EntityController(private val db: HibernateSessionFactory) : Controller, Loggable {
    override val baseContext: String = EntityDescriptions.baseContext
    override val log = logger()

    override fun configure(routing: Route): Unit = with(routing) {
        implement(EntityDescriptions.create) { req ->
            logEntry(log, req)
            db.withTransaction {
                val sampleEntity = SampleEntity(req.text)
                it.save(sampleEntity)
                ok(sampleEntity.toEntity())
            }
        }

        implement(EntityDescriptions.list) { req ->
            logEntry(log, req)
            val paginationRequest = req.normalize()
            val page = db.withTransaction {
                SampleEntity.list(it, paginationRequest)
            }

            ok(Page(0, paginationRequest.itemsPerPage, paginationRequest.page, page.map { it.toEntity() }))
        }
    }

    fun SampleEntity.toEntity(): Entity = Entity(contents, id!!)
}