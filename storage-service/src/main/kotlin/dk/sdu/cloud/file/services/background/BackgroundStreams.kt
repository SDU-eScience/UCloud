package dk.sdu.cloud.file.services.background

import dk.sdu.cloud.events.EventStreamContainer

class BackgroundStreams(val namespace: String) : EventStreamContainer() {
    val stream = stream<BackgroundRequest>("$namespace-background", { it.jobId })
}

