package dk.sdu.cloud.pg_rest_service.dao
import dk.sdu.cloud.pg_rest_service.model.*
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Data_transfer_headerEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Data_transfer_headerEntity>(Data_transfer_header)
	var modified_ts by Data_transfer_header.modified_ts
	var created_ts  by Data_transfer_header.created_ts
	var marked_for_delete  by Data_transfer_header.marked_for_delete
	var total_bytes  by Data_transfer_header.total_bytes
	var total_progress  by Data_transfer_header.total_progress
	var data_transfer_type_ref_id by Data_transfer_type optionalReferencedOn Data_transfer_header.data_transfer_type_ref_id
	var data_transfer_state_ref_id by Data_transfer_state optionalReferencedOn Data_transfer_header.data_transfer_state_ref_id
	var principal_ref_id by Principal referencedOn Data_transfer_header.principal_ref_id
	var active  by Data_transfer_detail.active
}


