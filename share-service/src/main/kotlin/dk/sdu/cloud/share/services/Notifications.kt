package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.share.api.CreateShareRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun aSendCreatedNotification(serviceClient: AuthenticatedClient, id: Long, owner: String, share: CreateShareRequest): Job =
    coroutineScope {
        launch {
            NotificationDescriptions.create.call(
                CreateNotification(
                    user = share.sharedWith,
                    notification = Notification(
                        type = "SHARE_REQUEST",
                        message = "$owner has shared a file with you",

                        meta = mapOf(
                            "shareId" to id,
                            "path" to share.path,
                            "rights" to share.rights
                        )
                    )
                ),
                serviceClient
            )
        }
    }
