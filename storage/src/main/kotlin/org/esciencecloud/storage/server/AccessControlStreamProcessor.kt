package org.esciencecloud.storage.server

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Result

class AccessControlStreamProcessor(private val storageService: StorageService) {
    fun init(builder: KStreamBuilder) {
        AccessControlProcessor.UpdateACL.mapResult(builder) { updateACL(it) }
    }

    // TODO I'm not sure about the Entity type this will not serialize well.
    private fun updateACL(request: UpdateACLRequest): Result<Unit> {
        val connection = storageService.validateRequest(request).capture() ?: return Result.lastError()
        val path = connection.paths.parseAbsolute(request.path, addHost = true)
        return connection.accessControl.updateACL(path, listOf(request.updatedEntries))
    }
}