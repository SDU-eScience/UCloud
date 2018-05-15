package dk.sdu.cloud.pg_rest_service.model

import dk.sdu.cloud.controller.model.Principal
import org.jetbrains.exposed.dao.IntIdTable


object Principal_notification_subscriptiontype_relation : IntIdTable() {
	val modified_ts = datetime("modified_ts")
	val principalnotificationsubscriptiontyperefid = Principal_notification_subscriptiontype_relation.reference("principalnotificationsubscriptiontype", Principal_notification_subscription_type)
	val marked_for_delete = integer("marked_for_delete").nullable()
	val created_ts = datetime("created_ts")
	val active = integer("active").nullable()
	val principalrefid = Principal_notification_subscriptiontype_relation.reference("principal", Principal)
}