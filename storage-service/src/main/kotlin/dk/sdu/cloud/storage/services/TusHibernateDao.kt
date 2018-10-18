package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.service.db.CriteriaBuilderContext
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import java.util.Date
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.persistence.criteria.Predicate

@Entity
@Table(name = "tus_upload_entity")
class TusUploadEntity(
    @Id
    @GeneratedValue
    var id: Long = 0L,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    var size: Long,

    var uploadPath: String,

    var owner: String,

    @Enumerated(EnumType.ORDINAL)
    var sensitivity: SensitivityLevel,

    var progress: Long
) {
    companion object : HibernateEntity<TusUploadEntity>, WithId<Long>
}

fun TusUploadEntity.toModel(): TusUpload =
    TusUpload(
        id,
        size,
        owner,
        uploadPath,
        sensitivity,
        progress,
        createdAt.time,
        modifiedAt.time
    )

class TusHibernateDAO : TusDAO<HibernateSession> {
    private fun CriteriaBuilderContext<*, TusUploadEntity>.isAuthorized(user: String): Predicate {
        return entity[TusUploadEntity::owner] equal literal(user)
    }

    override fun findUpload(
        session: HibernateSession,
        user: String,
        id: Long
    ): TusUpload {
        return session.criteria<TusUploadEntity> {
            allOf(
                isAuthorized(user),
                entity[TusUploadEntity::id] equal id
            )
        }.uniqueResult()?.toModel() ?: throw TusException.NotFound()
    }

    override fun create(
        session: HibernateSession,
        user: String,
        uploadCreationCommand: TusUploadCreationCommand
    ): Long {
        val entity = with(uploadCreationCommand) {
            TusUploadEntity(
                0,
                Date(),
                Date(),
                sizeInBytes,
                uploadPath,
                user,
                sensitivity,
                0
            )
        }

        return session.save(entity) as Long
    }

    override fun updateProgress(
        session: HibernateSession,
        user: String,
        id: Long,
        progress: Long
    ) {
        val entity = session.criteria<TusUploadEntity> {
            allOf(isAuthorized(user), entity[TusUploadEntity::id] equal id)
        }.uniqueResult() ?: throw TusException.NotFound()

        entity.progress = progress
        entity.modifiedAt = Date()
        session.save(entity)
    }
}
