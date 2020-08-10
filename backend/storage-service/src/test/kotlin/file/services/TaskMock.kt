package dk.sdu.cloud.file.services

import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.task.api.CreateResponse
import dk.sdu.cloud.task.api.MarkAsCompleteResponse
import dk.sdu.cloud.task.api.PostStatusResponse
import dk.sdu.cloud.task.api.Tasks

fun successfulTaskMock() {
    ClientMock.mockCallSuccess(
        Tasks.create,
        CreateResponse("ID", "Owner", "_storage", null, null, false, Time.now(), Time.now())
    )

    ClientMock.mockCallSuccess(
        Tasks.postStatus,
        PostStatusResponse
    )

    ClientMock.mockCallSuccess(
        Tasks.markAsComplete,
        MarkAsCompleteResponse
    )


}
