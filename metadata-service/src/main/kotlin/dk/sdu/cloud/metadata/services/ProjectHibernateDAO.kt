package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.util.normalize
import dk.sdu.cloud.service.db.*
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "projects")
class ProjectEntity2(
    @Column(length = 1024, unique = true)
    var fsRoot: String,

    var owner: String,

    var description: String,

    override var createdAt: Date = Date(),

    override var modifiedAt: Date = Date(),

    @Id
    @GeneratedValue
    var id: Long = 0
) : WithTimestamps {
    companion object : HibernateEntity<ProjectEntity2>, WithId<Long>
}

private fun ProjectEntity2.toModel(): Project = Project(
    id,
    fsRoot,
    owner,
    description
)

class ProjectHibernateDAO : ProjectDAO<HibernateSession> {
    override fun findByFSRoot(session: HibernateSession, path: String): Project {
        return internalByRoot(session, path)?.toModel() ?: throw ProjectException.NotFound()
    }

    override fun findById(session: HibernateSession, id: Long): Project? {
        return ProjectEntity2[session, id]?.toModel()
    }

    override fun createProject(session: HibernateSession, project: Project): Long {
        val exists = internalByRoot(session, project.fsRoot) != null
        if (exists) throw ProjectException.Duplicate()

        val entity = ProjectEntity2(
            project.fsRoot,
            project.owner,
            project.description
        )

        return session.save(entity) as Long
    }

    override fun findBestMatchingProjectByPath(session: HibernateSession, path: String): Project {
        return session
            .typedQuery<ProjectEntity2>(
                //language=HQL
                """
                from ProjectEntity2
                where :path like concat(fsRoot, '%')
                order by char_length(fsRoot) desc
                """.trimIndent()
            )
            .setParameter("path", path)
            .setMaxResults(1)
            .uniqueResult()?.toModel() ?: throw ProjectException.NotFound()
    }

    override fun updateProjectRoot(session: HibernateSession, id: Long, newRoot: String) {
        val existing = ProjectEntity2[session, id] ?: throw ProjectException.NotFound()
        existing.fsRoot = newRoot
        session.update(existing)
    }

    override fun deleteProjectByRoot(session: HibernateSession, root: String) {
        internalByRoot(session, root)?.let {
            session.delete(it)
        }
    }

    override fun deleteProjectById(session: HibernateSession, id: Long) {
        ProjectEntity2[session, id]?.let {
            session.delete(it)
        }
    }

    private fun internalByRoot(session: HibernateSession, path: String): ProjectEntity2? {
        return session
            .criteria<ProjectEntity2> { entity[ProjectEntity2::fsRoot] equal path }
            .uniqueResult()
    }
}