package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.typedQuery
import java.util.Date
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "projects")
class ProjectEntity(
    @Column(length = 1024, unique = true)
    var fsRoot: String, // TODO Should this be unique?

    @Column(unique = true, nullable = false)
    var fsRootId: String,

    var owner: String,

    var description: String,

    override var createdAt: Date = Date(),

    override var modifiedAt: Date = Date(),

    @Id
    @GeneratedValue
    var id: Long = 0
) : WithTimestamps {
    companion object : HibernateEntity<ProjectEntity>, WithId<Long>
}

private fun ProjectEntity.toModel(): Project = Project(
    id,
    fsRoot,
    fsRootId,
    owner,
    description
)

class ProjectHibernateDAO : ProjectDAO<HibernateSession> {
    override fun findByFSRoot(session: HibernateSession, path: String): Project {
        return internalByRoot(session, path)?.toModel() ?: throw ProjectException.NotFound()
    }

    override fun findById(session: HibernateSession, id: Long): Project? {
        return ProjectEntity[session, id]?.toModel()
    }

    override fun createProject(session: HibernateSession, project: Project): Long {
        val exists = internalByRoot(session, project.fsRoot) != null
        if (exists) throw ProjectException.Duplicate()

        val entity = ProjectEntity(
            project.fsRoot,
            project.fsRootId,
            project.owner,
            project.description
        )

        return session.save(entity) as Long
    }

    override fun findBestMatchingProjectByPath(session: HibernateSession, path: String): Project {
        return session
            .typedQuery<ProjectEntity>(
                //language=HQL
                """
                from ProjectEntity
                where :path like concat(fsRoot, '%')
                order by char_length(fsRoot) desc
                """.trimIndent()
            )
            .setParameter("path", path)
            .setMaxResults(1)
            .uniqueResult()?.toModel() ?: throw ProjectException.NotFound()
    }

    override fun updateProjectRoot(session: HibernateSession, id: Long, newRoot: String) {
        val existing = ProjectEntity[session, id] ?: throw ProjectException.NotFound()
        existing.fsRoot = newRoot
        session.update(existing)
    }

    override fun deleteProjectByRoot(session: HibernateSession, root: String) {
        internalByRoot(session, root)?.let {
            session.delete(it)
        }
    }

    override fun deleteProjectById(session: HibernateSession, id: Long) {
        ProjectEntity[session, id]?.let {
            session.delete(it)
        }
    }

    private fun internalByRoot(session: HibernateSession, path: String): ProjectEntity? {
        return session
            .criteria<ProjectEntity> { entity[ProjectEntity::fsRoot] equal path }
            .uniqueResult()
    }
}
