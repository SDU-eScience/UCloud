package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.*

/**
 * Updated in:
 *
 * - V4__Tools.sql
 */
@Entity
@Table(name = "tools")
data class ToolEntity(
    var owner: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Type(type = JSONB_TYPE)
    var tool: NormalizedToolDescription,

    @Column(length = 1024 * 64)
    var originalDocument: String,

    @EmbeddedId
    var id: EmbeddedNameAndVersion
)

/**
 * Updated in:
 *
 * - V3__Applications.sql
 * - V4__Tools.sql
 */
@Entity
@Table(name = "applications")
class ApplicationEntity(
    var owner: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Type(type = JSONB_TYPE)
    var application: NormalizedApplicationDescription,

    // Note: This is just the original document. We _do not_ attempt to keep this synchronized with changes
    // to description etc.
    //
    // In case this is used for migration we should apply these updates on top of it!
    @Column(length = 1024 * 64)
    var originalDocument: String,

    @ManyToOne
    var tool: ToolEntity,

    @EmbeddedId
    var id: EmbeddedNameAndVersion
) {
    companion object : HibernateEntity<ApplicationEntity>, WithId<EmbeddedNameAndVersion>
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

/**
 * Updated in:
 *
 * - V1__Initial.sql
 * - V5__JobReferences.sql
 * - V6__JWTs.sql
 */
@Entity
@Table(name = "jobs")
data class JobEntity(
    @Id
    @NaturalId
    var systemId: UUID,

    var owner: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Enumerated(EnumType.STRING)
    var state: AppState,

    var slurmId: Long?,

    var status: String?,

    var sshUser: String?,

    var jobDirectory: String?,

    var workingDirectory: String?,

    @ManyToOne
    var application: ApplicationEntity,

    var jwt: String
) {
    companion object : HibernateEntity<JobEntity>, WithId<UUID>
}
