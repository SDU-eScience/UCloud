package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ProjectProxy
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.list
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Inheritance
import javax.persistence.InheritanceType
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType

/**
 * Updated in:
 *
 * - V1__Initial.sql
 * - V7__WayfId.sql
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "principals")
sealed class PrincipalEntity {
    @get:Id
    @get:NaturalId
    abstract var id: String

    @get:Enumerated(EnumType.STRING)
    abstract var role: Role

    @get:Temporal(TemporalType.TIMESTAMP)
    abstract var createdAt: Date

    @get:Temporal(TemporalType.TIMESTAMP)
    abstract var modifiedAt: Date

    @get:Column(
        unique = true,
        updatable = false,
        insertable = false,
        columnDefinition = "bigint not null auto_increment" // This is only true for testing
    )
    abstract var uid: Long

    abstract fun toModel(): Principal

    companion object : HibernateEntity<PrincipalEntity>, WithId<String>
}

@Entity
data class ServiceEntity(
    override var id: String,
    override var role: Role,
    override var createdAt: Date,
    override var modifiedAt: Date,
    override var uid: Long = 0
) : PrincipalEntity() {
    override fun toModel(): Principal {
        return ServicePrincipal(id, role, uid)
    }
}

@Entity
data class ProjectProxyEntity(
    override var id: String,
    override var role: Role,
    override var createdAt: Date,
    override var modifiedAt: Date,
    override var uid: Long = 0
) : PrincipalEntity() {
    override fun toModel(): Principal {
        return ProjectProxy(id, role, uid)
    }
}

@Entity
sealed class PersonEntity : PrincipalEntity() {
    abstract var title: String?
    abstract var firstNames: String
    abstract var lastName: String
    abstract var phoneNumber: String?
    abstract var orcId: String?
}

@Entity
data class PersonEntityByPassword(
    override var id: String,
    override var role: Role,
    override var createdAt: Date,
    override var modifiedAt: Date,
    override var title: String?,
    override var firstNames: String,
    override var lastName: String,
    override var phoneNumber: String?,
    override var orcId: String?,
    override var uid: Long = 0,

    var hashedPassword: ByteArray,
    var salt: ByteArray
) : PersonEntity() {
    override fun toModel(): Principal {
        return Person.ByPassword(
            id,
            role,
            title,
            firstNames,
            lastName,
            phoneNumber,
            orcId,
            emptyList(),
            null,
            uid,
            hashedPassword,
            salt
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonEntityByPassword

        if (id != other.id) return false
        if (role != other.role) return false
        if (createdAt != other.createdAt) return false
        if (modifiedAt != other.modifiedAt) return false
        if (title != other.title) return false
        if (firstNames != other.firstNames) return false
        if (lastName != other.lastName) return false
        if (phoneNumber != other.phoneNumber) return false
        if (orcId != other.orcId) return false
        if (!Arrays.equals(hashedPassword, other.hashedPassword)) return false
        if (!Arrays.equals(salt, other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + modifiedAt.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + firstNames.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + (phoneNumber?.hashCode() ?: 0)
        result = 31 * result + (orcId?.hashCode() ?: 0)
        result = 31 * result + Arrays.hashCode(hashedPassword)
        result = 31 * result + Arrays.hashCode(salt)
        return result
    }
}

@Entity
data class PersonEntityByWAYF(
    override var id: String,
    override var role: Role,
    override var createdAt: Date,
    override var modifiedAt: Date,
    override var title: String?,
    override var firstNames: String,
    override var lastName: String,
    override var phoneNumber: String?,
    override var orcId: String?,
    override var uid: Long = 0,
    var orgId: String,
    var wayfId: String
) : PersonEntity() {
    override fun toModel(): Principal {
        return Person.ByWAYF(
            id,
            role,
            title,
            firstNames,
            lastName,
            phoneNumber,
            orcId,
            emptyList(),
            null,
            uid,
            orgId,
            wayfId
        )
    }
}

class UserHibernateDAO(
    private val passwordHashingService: PasswordHashingService
) : UserDAO<HibernateSession> {
    override fun findById(session: HibernateSession, id: String): Principal {
        return PrincipalEntity[session, id]?.toModel() ?: throw UserException.NotFound()
    }

    override fun findByIdOrNull(session: HibernateSession, id: String): Principal? {
        return PrincipalEntity[session, id]?.toModel()
    }

    override fun findAllByIds(session: HibernateSession, ids: List<String>): Map<String, Principal?> {
        val usersWeFound = session
            .criteria<PrincipalEntity> { entity[PrincipalEntity::id] isInCollection ids }
            .list()
            .map { it.toModel() }
            .associateBy { it.id }

        val usersWeDidntFind = ids.filter { it !in usersWeFound }
        val nullEntries = usersWeDidntFind.map { it to null as Principal? }.toMap()

        return usersWeFound + nullEntries
    }

    override fun findByUsernamePrefix(session: HibernateSession, prefix: String): List<Principal> {
        return session.criteria<PrincipalEntity> {
            entity[PrincipalEntity::id] like (builder.concat(prefix, literal("%")))
        }.list().map { it.toModel() }
    }

    override fun findByWayfId(session: HibernateSession, wayfId: String): Person.ByWAYF {
        return (session.criteria<PersonEntityByWAYF> {
            entity[PersonEntityByWAYF::wayfId] equal wayfId
        } as? PrincipalEntity)?.toModel() as? Person.ByWAYF ?: throw UserException.NotFound()
    }

    override fun insert(session: HibernateSession, principal: Principal) {
        session.save(principal.toEntity())
    }

    override fun delete(session: HibernateSession, id: String) {
        session.delete(PrincipalEntity[session, id] ?: throw UserException.NotFound())
    }

    override fun updatePassword(
        session: HibernateSession,
        id: String,
        newPassword: String,
        currentPasswordForVerification: String?
    ) {
        val entity = PrincipalEntity[session, id] as? PersonEntityByPassword ?: throw UserException.NotFound()
        if (currentPasswordForVerification != null) {
            if (!passwordHashingService.checkPassword(
                    entity.hashedPassword,
                    entity.salt,
                    currentPasswordForVerification
                )
            ) {
                throw UserException.InvalidAuthentication()
            }
        }

        val (hashedPassword, salt) = passwordHashingService.hashPassword(newPassword)
        entity.hashedPassword = hashedPassword
        entity.salt = salt
        session.update(entity)
    }

    override fun listAll(session: HibernateSession): List<Principal> {
        return PrincipalEntity.list(session).map { it.toModel() }
    }
}

fun Principal.toEntity(): PrincipalEntity {
    return when (this) {
        is Person.ByWAYF -> PersonEntityByWAYF(
            id,
            role,
            Date(),
            Date(),
            title,
            firstNames,
            lastName,
            phoneNumber,
            orcId,
            uid,
            organizationId,
            wayfId
        )

        is Person.ByPassword -> PersonEntityByPassword(
            id,
            role,
            Date(),
            Date(),
            title,
            firstNames,
            lastName,
            phoneNumber,
            orcId,
            uid,
            password,
            salt
        )

        is ServicePrincipal -> ServiceEntity(
            id,
            role,
            Date(),
            Date(),
            uid
        )

        is ProjectProxy -> ProjectProxyEntity(
            id,
            role,
            Date(),
            Date(),
            uid
        )
    }
}

