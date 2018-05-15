package dk.sdu.cloud.pg_rest_service.dao
import dk.sdu.cloud.pg_rest_service.model.Data_transfer_detail
import dk.sdu.cloud.pg_rest_service.model.Data_transfer_header
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class Data_transfer_detailEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<Data_transfer_detailEntity>(Data_transfer_detail)
	var modified_ts by Data_transfer_detail.modified_ts
	var created_ts  by Data_transfer_detail.created_ts
	var marked_for_delete  by Data_transfer_detail.marked_for_delete
	var data_object_ref_id  by Data_transfer_detail.data_object_ref_id
	var part_bytes  by Data_transfer_detail.part_bytes
	var part_progress  by Data_transfer_detail.part_progress
	var active  by Data_transfer_detail.active
	var data_transfer_header_ref_id by Data_transfer_header referrersOn Data_transfer_detail.data_transfer_header_ref_id
}