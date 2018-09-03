package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.StorageEvent
import kotlinx.coroutines.experimental.channels.ReceiveChannel

interface FileSystemListener {
    suspend fun attachToFSChannel(channel: ReceiveChannel<StorageEvent>)
}