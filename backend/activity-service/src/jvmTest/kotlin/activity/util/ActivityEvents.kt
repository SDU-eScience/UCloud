package dk.sdu.cloud.activity.util

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.service.test.TestUsers

internal val downloadEvent = ActivityEvent.Download(
    TestUsers.user.username,
    123456789,
    "1"
)

internal val favoriteEvent = ActivityEvent.Favorite(
    TestUsers.user.username,
    true,
    123456987,
    "3"
)

internal val movedEvent = ActivityEvent.Moved(
    TestUsers.user.username,
    "newName",
    123456798,
    "5"
)

internal val deletedEvent = ActivityEvent.Deleted(
    TestUsers.user.username,
    123456789,
    "6"
)
