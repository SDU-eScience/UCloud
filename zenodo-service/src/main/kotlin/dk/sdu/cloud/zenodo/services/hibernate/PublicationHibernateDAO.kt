package dk.sdu.cloud.zenodo.services.hibernate

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.zenodo.api.ZenodoPublication
import dk.sdu.cloud.zenodo.api.ZenodoPublicationStatus
import dk.sdu.cloud.zenodo.api.ZenodoPublicationWithFiles
import dk.sdu.cloud.zenodo.services.PublicationException
import dk.sdu.cloud.zenodo.services.PublicationService

class PublicationHibernateDAO :
    PublicationService<HibernateSession> {
    override fun findById(
        session: HibernateSession,
        user: String,
        id: Long
    ): ZenodoPublicationWithFiles {
        return (PublicationEntity[session, id]?.takeIf { it.owner == user }
                ?: throw PublicationException.NotFound()).toModel()
    }

    override fun findForUser(
        session: HibernateSession,
        user: String,
        pagination: NormalizedPaginationRequest
    ): Page<ZenodoPublication> {
        return session.paginatedCriteria<PublicationEntity>(pagination) {
            entity[PublicationEntity::owner] equal user
        }.mapItems { it.toModel() }
    }

    override fun createUploadForFiles(
        session: HibernateSession,
        user: String,
        name: String,
        filePaths: Set<String>
    ): Long {
        val filteredFiles = mutableListOf<String>()
        val fileNames = mutableSetOf<String>()
        filePaths.forEach {
            val fileName = it.removeSuffix("/").substringAfterLast('/')
            if (fileName !in fileNames) {
                fileNames += fileName
                filteredFiles += it
            }
        }

        val entity = PublicationEntity(
            name,
            user,
            ZenodoPublicationStatus.PENDING
        )

        val id = session.save(entity) as Long

        filteredFiles.forEach {
            session.save(
                PublicationDataObjectEntity(
                    PublicationDataObjectEntity.Key(
                        it,
                        entity
                    )
                )
            )
        }
        return id
    }

    override fun markUploadAsCompleteInPublication(session: HibernateSession, publicationId: Long, path: String) {
        val entity = session.criteria<PublicationDataObjectEntity> {
            val key = entity[PublicationDataObjectEntity::id]

            allOf(
                key[PublicationDataObjectEntity.Key::publication][PublicationEntity::id] equal publicationId,
                key[PublicationDataObjectEntity.Key::dataObjectPath] equal path
            )
        }.uniqueResult() ?: throw PublicationException.NotFound()

        entity.uploaded = true
        session.update(entity)
    }

    override fun attachZenodoId(session: HibernateSession, publicationId: Long, zenodoId: String) {
        val entity = PublicationEntity[session, publicationId] ?: throw PublicationException.NotFound()
        entity.zenodoId = zenodoId
        session.update(entity)
    }

    override fun updateStatusOf(session: HibernateSession, publicationId: Long, status: ZenodoPublicationStatus) {
        val entity = PublicationEntity[session, publicationId] ?: throw PublicationException.NotFound()
        entity.status = status
        session.update(entity)
    }
}