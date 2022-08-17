package dk.sdu.cloud.controllers

import dk.sdu.cloud.ipc.IpcServer

interface IpcController {
    fun configureIpc(server: IpcServer)
}
