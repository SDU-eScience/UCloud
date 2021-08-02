package dk.sdu.cloud.sync.mounter.services

import dk.sdu.cloud.sync.mounter.api.*

class MountService {
    fun mount(request: MountRequest): MountResponse {
        return MountResponse
    }

    fun unmount(request: UnmountRequest): UnmountResponse {
        return UnmountResponse
    }

    fun state(request: StateRequest): StateResponse {
        return StateResponse
    }
}