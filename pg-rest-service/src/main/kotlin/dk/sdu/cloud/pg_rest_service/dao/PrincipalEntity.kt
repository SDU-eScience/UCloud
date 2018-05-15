package dk.sdu.cloud.pg_rest_service.controller.dao
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class PrincipalEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<PrincipalEntity>(Principal)

	var modified_ts by Principal.modified_ts
	var marked_for_delete by Principal.marked_for_delete
	var created_ts by Principal.created_ts
	var principaltitle = Principal.principaltitle
	var principalfirstnames = Principal.principalfirstnames
	var principallastname = Principal.principallastname
	var principalphoneno = Principal.principalphoneno
	var ext_wayf_id = Principal.ext_wayf_id
	var username = Principal.username
	var orcid = Principal.orcid
	//var latitude = Principal.latitude
	//var longitude= Principal.latitude
	var active by Principal.active
	var record_state = Principal.record_state
	var hashed_password = Principal.hashed_password
	var salt = Principal.salt
}