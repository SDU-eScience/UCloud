package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.Date
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import kotlin.collections.ArrayList

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
) {
    companion object : HibernateEntity<ToolEntity>, WithId<EmbeddedNameAndVersion>
}

@Entity
@Table(name = "favorited_by")
class FavoriteApplicationEntity(
    @ManyToOne
    var application: ApplicationEntity,

    @Column(name = "the_user")
    var user: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<FavoriteApplicationEntity>, WithId<Long>
}

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

    // New start
    @Type(type = JSONB_TYPE)
    var authors: List<String>,

    var title: String,

    var description: String,

    var website: String?,

    @Type(type = JSONB_TYPE)
    var tags: List<String>,
    // New end

    @Type(type = JSONB_TYPE)
    var application: ApplicationInvocationDescription,

    @Column(name = "tool_name")
    var toolName: String,

    @Column(name = "tool_version")
    var toolVersion: String,

    @EmbeddedId
    var id: EmbeddedNameAndVersion
) {
    companion object : HibernateEntity<ApplicationEntity>, WithId<EmbeddedNameAndVersion>
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

