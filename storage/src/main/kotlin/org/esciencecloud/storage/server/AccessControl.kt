package org.esciencecloud.storage.server

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Result

class AccessControl(private val storageService: StorageService) {
    fun initStream(builder: KStreamBuilder) {
        AccessControlProcessor.UpdateACL.process(builder) { _, request -> updateACL(request) }
    }

    // TODO I'm not sure about the Entity type this will not serialize well.
    private fun updateACL(request: Request<UpdateACLRequest>): Result<Unit> {
        val connection = storageService.validateRequest(request.header).capture() ?: return Result.lastError()
        val path = connection.paths.parseAbsolute(request.event.path, addHost = true)
        return connection.accessControl.updateACL(path, listOf(request.event.updatedEntries))
    }
}
