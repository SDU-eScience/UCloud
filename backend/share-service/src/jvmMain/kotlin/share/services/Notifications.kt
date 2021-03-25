package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.share.api.Shares
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

suspend fun aSendCreatedNotification(
    serviceClient: AuthenticatedClient,
    owner: String,
    share: Shares.Create.Request
): Job =
    coroutineScope {
        launch {
            try {
                NotificationDescriptions.create.call(
                    CreateNotification(
                        user = share.sharedWith,
                        notification = Notification(
                            type = "SHARE_REQUEST",
                            message = "$owner has shared a file with you",

                            meta = JsonObject(mapOf(
                                "path" to JsonPrimitive(share.path),
                                "rights" to defaultMapper.encodeToJsonElement(share.rights)
                            ))
                        )
                    ),
                    serviceClient
                )
            } catch (ignored: Throwable) {
                // Ignored
            }
        }
    }
