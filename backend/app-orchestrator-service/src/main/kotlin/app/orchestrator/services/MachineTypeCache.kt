package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.compute.DefaultMachineRequest
import dk.sdu.cloud.accounting.compute.ListMachinesRequest
import dk.sdu.cloud.accounting.compute.MachineReservation
import dk.sdu.cloud.accounting.compute.MachineTypes
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.HttpStatusCode

class MachineTypeCache(private val serviceClient: AuthenticatedClient) {
    val machines = SimpleCache<Unit, List<MachineReservation>> {
        MachineTypes.listMachines.call(ListMachinesRequest(false), serviceClient).orThrow()
    }

    val defaultMachine = SimpleCache<Unit, String> {
        MachineTypes.defaultMachine.call(DefaultMachineRequest, serviceClient).orThrow().name
    }

    suspend fun find(name: String): MachineReservation? {
        val machines = machines.get(Unit) ?: return null
        return machines.find { it.name == name }
    }

    suspend fun findDefault(): MachineReservation {
        val name = defaultMachine.get(Unit)
            ?: throw RPCException("No default machine", HttpStatusCode.InternalServerError)
        return find(name) ?: throw RPCException("No default machine", HttpStatusCode.InternalServerError)
    }
}