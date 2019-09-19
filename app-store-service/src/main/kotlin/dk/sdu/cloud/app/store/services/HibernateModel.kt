package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType

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
    var applicationName: String,

    var applicationVersion: String,

    @Column(name = "the_user")
    var user: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<FavoriteApplicationEntity>, WithId<Long>
}

@Entity
@Table(name = "application_tags")
class TagEntity(
    var applicationName: String,

    @Column(name = "tag")
    var tag: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<TagEntity>, WithId<Long>
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

    @Type(type = JSONB_TYPE)
    var authors: List<String>,

    var title: String,

    @Column(length = 1024 * 64)
    var description: String,

    var website: String?,

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

@Entity
@Table(name = "application_logos")
class ApplicationLogoEntity(
    @Id
    var application: String,

    @Column(length = LOGO_MAX_SIZE)
    var data: ByteArray
) {
    companion object : HibernateEntity<ApplicationLogoEntity>, WithId<String>
}

@Entity
@Table(name = "tool_logos")
class ToolLogoEntity(
    @Id
    var application: String,

    @Column(length = LOGO_MAX_SIZE)
    var data: ByteArray
) {
    companion object : HibernateEntity<ToolLogoEntity>, WithId<String>
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

const val LOGO_MAX_SIZE = 1024 * 512
