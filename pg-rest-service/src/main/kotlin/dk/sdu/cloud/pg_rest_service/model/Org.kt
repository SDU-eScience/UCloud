package dk.sdu.cloud.pg_rest_service.model

import org.jetbrains.exposed.dao.IntIdTable




object Org : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val orgfullname = text("orgfullname").nullable()
	val orgshortname = text("orgshortname").nullable()
	val active = integer("active").nullable()
}

