package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Index
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
@Table(
    name = "principals",
    indexes = [Index(columnList = "uid")]
)
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

    abstract fun toModel(totpStatus: Boolean): Principal

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
    override fun toModel(totpStatus: Boolean): Principal {
        return ServicePrincipal(id, role, uid)
    }
}

@Entity
sealed class PersonEntity : PrincipalEntity() {
    abstract var title: String?
    abstract var firstNames: String
    abstract var lastName: String
    abstract var phoneNumber: String?
    abstract var orcId: String?
    abstract var email: String?
    abstract var serviceLicenseAgreement: Int
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
    override var email: String? = null,
    override var serviceLicenseAgreement: Int,

    var hashedPassword: ByteArray,
    var salt: ByteArray
) : PersonEntity() {
    override fun toModel(totpStatus: Boolean): Principal {
        return Person.ByPassword(
            id,
            role,
            title,
            firstNames,
            lastName,
            phoneNumber,
            orcId,
            email,
            uid,
            totpStatus,
            serviceLicenseAgreement,
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
        if (serviceLicenseAgreement != other.serviceLicenseAgreement) return false
        if (orcId != other.orcId) return false
        if (email != other.email) return false

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
        result = 31 * result + (email?.hashCode() ?: 0)
        result = 31 * result + serviceLicenseAgreement.hashCode()
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
    override var email: String? = null,
    override var serviceLicenseAgreement: Int,
    var orgId: String,
    var wayfId: String
) : PersonEntity() {
    override fun toModel(totpStatus: Boolean): Principal {
        return Person.ByWAYF(
            id,
            role,
            title,
            firstNames,
            lastName,
            phoneNumber,
            orcId,
            email,
            uid,
            serviceLicenseAgreement,
            orgId,
            wayfId
        )
    }
}

data class UserInformation(
    val email: String?,
    val firstNames: String?,
    val lastName: String?
)

class UserHibernateDAO(
    private val passwordHashingService: PasswordHashingService,
    private val twoFactorDAO: TwoFactorDAO<HibernateSession>
) : UserDAO<HibernateSession> {

    override fun getUserInfo(session: HibernateSession, username: String): UserInformation {
        val user = session
            .criteria<PersonEntity> { entity[PersonEntity::id] equal username }
            .singleResult

        return UserInformation(user.email, user.firstNames, user.lastName)
    }

    override fun updateUserInfo(
        session: HibernateSession,
        username: String,
        firstNames: String?,
        lastName: String?,
        email: String?
    ) {
        val entity = PrincipalEntity[session, username] as? PersonEntityByPassword ?: throw UserException.NotFound()

        if (!firstNames.isNullOrBlank()) {
            entity.firstNames = firstNames
        }
        if (!lastName.isNullOrBlank()) {
            entity.lastName = lastName
        }
        if (!email.isNullOrBlank()) {
            entity.email = email
        }
        session.update(entity)
    }

    override fun findById(session: HibernateSession, id: String): Principal {
        val status = twoFactorDAO.findStatusBatched(session, listOf(id))
        return PrincipalEntity[session, id]?.toModel(status.getValue(id)) ?: throw UserException.NotFound()
    }

    override fun findByIdOrNull(session: HibernateSession, id: String): Principal? {
        return PrincipalEntity[session, id]?.toModel(twoFactorDAO.findStatusBatched(session, listOf(id)).getValue(id))
    }

    override fun findAllByIds(session: HibernateSession, ids: List<String>): Map<String, Principal?> {
        val status = twoFactorDAO.findStatusBatched(session, ids)
        val usersWeFound = session
            .criteria<PrincipalEntity> { entity[PrincipalEntity::id] isInCollection ids }
            .list()
            .map { it.toModel(status.getValue(it.id)) }
            .associateBy { it.id }

        val usersWeDidntFind = ids.filter { it !in usersWeFound }
        val nullEntries = usersWeDidntFind.map { it to null as Principal? }.toMap()

        return usersWeFound + nullEntries
    }

    override fun findEmail(session: HibernateSession, id: String): String? {
        val user = session
            .criteria<PersonEntity> { entity[PersonEntity::id] equal id }
            .singleResult

        return user.email
    }

    override fun findByEmail(session: HibernateSession, email: String): UserIdAndName {
        val user = session
            .criteria<PersonEntity> { entity[PersonEntity::email] equal email }
            .singleResult

        return UserIdAndName(user.id, user.firstNames)
    }

    override fun findAllByUIDs(session: HibernateSession, uids: List<Long>): Map<Long, Principal?> {
        val users = session
            .criteria<PrincipalEntity> { entity[PrincipalEntity::uid] isInCollection uids }
            .list()

        val twoFactorStatus = twoFactorDAO.findStatusBatched(session, users.map { it.id })

        val usersWeFound = users
            .map { it.toModel(twoFactorStatus.getValue(it.id)) }
            .associateBy { it.uid }

        val usersWeDidntFind = uids.filter { it !in usersWeFound }
        val nullEntires = usersWeDidntFind.map { it to null as Principal? }.toMap()

        return usersWeFound + nullEntires
    }

    override fun findByUsernamePrefix(session: HibernateSession, prefix: String): List<Principal> {
        return session.criteria<PrincipalEntity> {
            entity[PrincipalEntity::id] like (builder.concat(prefix, literal("%")))
        }.list().map { it.toModel(false) }
    }

    override fun findByWayfId(session: HibernateSession, wayfId: String): Person.ByWAYF {
        return (session
            .createQuery("from PrincipalEntity where wayfId = :wayfId")
            .setParameter("wayfId", wayfId).list().firstOrNull() as? PrincipalEntity)
            ?.toModel(false) as? Person.ByWAYF ?: throw UserException.NotFound()
    }

    override fun findByWayfIdAndUpdateEmail(session: HibernateSession, wayfId: String, email: String?): Person.ByWAYF {
        val entity = (session
            .createQuery("from PrincipalEntity where wayfId = :wayfId")
            .setParameter("wayfId", wayfId).list().firstOrNull() as? PersonEntityByWAYF)

        if (entity != null && email != null) {
            entity.email = email
            session.save(entity)
        }

        return entity?.toModel(false) as? Person.ByWAYF ?: throw UserException.NotFound()
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

    override fun unconditionalUpdatePassword(session: HibernateSession, id: String, newPassword: String) {
        val entity = PrincipalEntity[session, id] as? PersonEntityByPassword ?: throw UserException.NotFound()

        val (hashedPassword, salt) = passwordHashingService.hashPassword(newPassword)
        entity.hashedPassword = hashedPassword
        entity.salt = salt
        session.update(entity)
    }

    override fun setAcceptedSlaVersion(session: HibernateSession, user: String, version: Int) {
        val entity = PrincipalEntity[session, user] as? PersonEntity ?: throw UserException.NotFound()
        entity.serviceLicenseAgreement = version
        session.update(entity)
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
            email,
            serviceLicenseAgreement,
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
            email,
            serviceLicenseAgreement,
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
    }
}

