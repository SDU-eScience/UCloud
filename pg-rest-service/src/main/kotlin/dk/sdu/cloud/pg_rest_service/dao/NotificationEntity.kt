package dk.sdu.cloud.pg_rest_service.controller.dao
import dk.sdu.cloud.pg_rest_service.model.App
import dk.sdu.cloud.pg_rest_service.model.Notification
import dk.sdu.cloud.pg_rest_service.model.Principal
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass


class NotificationEntity(id: EntityID<Int>) : IntEntity(id) {
	companion object: IntEntityClass<NotificationEntity>(Notification)

	var modified_ts by Notification.modified_ts
	var notificationtext by Notification.notification_text
	var marked_for_delete by Notification.marked_for_delete
	var created_ts by Notification.created_ts
	//var meta by Notification.meta
	var viewed by Notification.viewed
	var principal_ref_id by Principal referrersOn Notification.principal_ref_id
}