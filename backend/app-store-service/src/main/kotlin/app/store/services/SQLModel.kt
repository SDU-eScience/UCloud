package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.byteArray
import dk.sdu.cloud.service.db.async.jsonb
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import java.io.Serializable

/**
 * Updated in:
 *
 * - V4__Tools.sql
 */

object ToolTable : SQLTable("tools") {
    val owner = text("owner", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val tool = jsonb("tool", notNull = true)
    val originalDocument = text("original_document", notNull = true)
    val idName = text("name", notNull = true)
    val idVersion = text("version", notNull = true)
}

object FavoriteApplicationTable : SQLTable("favorited_by") {
    val applicationName = text("application_name", notNull = true)
    val applicationVersion = text("application_version", notNull = true)
    val user = text("the_user", notNull = true)
    val id = long("id")
}

object TagTable : SQLTable("application_tags") {
    val applicationName = text("application_name", notNull = true)
    val tag = text("tag", notNull = true)
    val id = long("id")
}

/**
 * Updated in:
 *
 * - V3__Applications.sql
 * - V4__Tools.sql
 */
object ApplicationTable : SQLTable("applications") {
    val owner = text("owner", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val authors = jsonb("authors", notNull = true)
    val title = text("title", notNull = true)
    val description = text("description", notNull = true)
    val website = text("website")
    val application = jsonb("application", notNull = true)
    val toolName = text("tool_name", notNull = true)
    val toolVersion  = text("tool_version")
    val isPublic = bool("is_public", notNull = true)
    val idName = text("name", notNull = true)
    val idVersion = text("version", notNull = true)
}

object ApplicationLogosTable : SQLTable("application_logos") {
    val application = text("application", notNull = true)
    val data = byteArray("data", notNull = true)
}

object ToolLogoTable : SQLTable("tool_logos") {
    val application = text("application", notNull = true)
    val data = byteArray("data", notNull = true)
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

const val LOGO_MAX_SIZE = 1024 * 512
