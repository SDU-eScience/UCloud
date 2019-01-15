package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.service.db.updateCriteria
import org.hibernate.annotations.NaturalId
import org.hibernate.query.Query
import java.sql.ResultSet
import java.util.*
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.persistence.UniqueConstraint
import kotlin.collections.ArrayList

@Entity
@Table(name = "projects")
data class ProjectEntity(
    @Id
    @NaturalId
    var id: String,

    @Column(length = 4096)
    var title: String,

    @OneToMany(
        mappedBy = "project",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    val members: MutableList<ProjectMemberEntity> = ArrayList(),

    override var createdAt: Date = Date(),

    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<ProjectEntity>, WithId<String>
}

@Entity
@Table(
    name = "project_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["username", "project_id"])]
)
data class ProjectMemberEntity(
    @Column(name = "username")
    var username: String,

    @Enumerated(EnumType.STRING)
    var role: ProjectRole,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: ProjectEntity,

    @Id
    @GeneratedValue
    var id: Long? = null,

    override var createdAt: Date = Date(),

    override var modifiedAt: Date = Date()
) : WithTimestamps {

    companion object : HibernateEntity<ProjectMemberEntity>, WithId<Long>

    override fun toString(): String {
        return "ProjectMemberEntity(username='$username', role=$role, id=$id, createdAt=$createdAt, modifiedAt=$modifiedAt)"
    }
}

class ProjectHibernateDao : ProjectDao<HibernateSession> {
    override fun create(session: HibernateSession, id: String, title: String, principalInvestigator: String) {
        val entity = ProjectEntity(id, title)
        session.save(entity)

        val memberEntity = ProjectMemberEntity(principalInvestigator, ProjectRole.PI, entity)
        entity.members.add(memberEntity)
        session.save(entity)
    }

    override fun delete(session: HibernateSession, id: String) {
        session.deleteCriteria<ProjectMemberEntity> {
            entity[ProjectMemberEntity::project][ProjectEntity::id] equal id
        }.executeUpdate()

        session.deleteCriteria<ProjectEntity> { entity[ProjectEntity::id] equal id }.executeUpdate().takeIf { it == 1 }
            ?: throw ProjectException.NotFound()
    }

    override fun findById(session: HibernateSession, projectId: String): Project {
        val entity = ProjectEntity[session, projectId] ?: throw ProjectException.NotFound()
        return Project(
            entity.id,
            entity.title,
            entity.members.map { ProjectMember(it.username, it.role) }
        )
    }

    override fun addMember(session: HibernateSession, projectId: String, member: ProjectMember) {
        val project = ProjectEntity[session, projectId] ?: throw ProjectException.NotFound()
        val memberEntity = ProjectMemberEntity(member.username, member.role, project)
        project.members.add(memberEntity)
        session.save(project)
    }

    override fun deleteMember(session: HibernateSession, projectId: String, member: String) {
        session.deleteCriteria<ProjectMemberEntity> {
            (entity[ProjectMemberEntity::project][ProjectEntity::id] equal projectId) and
                    (entity[ProjectMemberEntity::username] equal member)
        }.executeUpdate().takeIf { it == 1 } ?: throw ProjectException.UserDoesNotExist()
    }

    override fun changeMemberRole(session: HibernateSession, projectId: String, member: String, newRole: ProjectRole) {
        session.updateCriteria<ProjectMemberEntity>(
            setProperties = { criteria.set(entity[ProjectMemberEntity::role], newRole) },
            where = {
                (entity[ProjectMemberEntity::project][ProjectEntity::id] equal projectId) and
                        (entity[ProjectMemberEntity::username] equal member)
            }
        ).executeUpdate().takeIf { it == 1 } ?: throw ProjectException.NotFound()
    }

    override fun findRoleOfMember(session: HibernateSession, projectId: String, member: String): ProjectRole? {
        return session.criteria<ProjectMemberEntity> {
            (entity[ProjectMemberEntity::username] equal member) and
                    (entity[ProjectMemberEntity::project][ProjectEntity::id] equal projectId)
        }.uniqueResult()?.role
    }

    override fun listProjectsForUser(
        session: HibernateSession,
        user: String,
        pagination: NormalizedPaginationRequest
    ): Page<UserProjectSummary> {
        val itemsInTotal = session.createCriteriaBuilder<Long, ProjectMemberEntity>().run {
            criteria.where(entity[ProjectMemberEntity::username] equal user)
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()

        @Suppress("UNCHECKED_CAST")
        val items = (session
            .createQuery(
                //language=HQL
                """
                select mem.role, project.id, project.title
                from ProjectMemberEntity as mem
                    inner join mem.project as project
                where
                    mem.username = :username
                order by project.id
                """.trimIndent()
            ) as Query<Array<Any>>)
            .setParameter("username", user)
            .paginatedList(pagination)
            .map {
                val role = it[0] as ProjectRole
                val id = it[1] as String
                val title = it[2] as String


                UserProjectSummary(id, title, ProjectMember(user, role))
            }

        return Page(
            itemsInTotal.toInt(),
            pagination.itemsPerPage,
            pagination.page,
            items
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
