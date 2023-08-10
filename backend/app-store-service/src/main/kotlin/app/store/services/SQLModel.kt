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

object ApplicationLogosTable : SQLTable("application_logos") {
    val application = text("application", notNull = true)
    val data = byteArray("data", notNull = true)
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

const val LOGO_MAX_SIZE = 1024 * 1024 * 5
