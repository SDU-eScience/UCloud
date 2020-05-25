package dk.sdu.cloud.accounting.compute.rpc

import dk.sdu.cloud.accounting.compute.MachineTypes
import dk.sdu.cloud.accounting.compute.services.MachineService
import dk.sdu.cloud.accounting.compute.services.toActor
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext

class MachineController(
    private val db: DBContext,
    private val machines: MachineService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(MachineTypes.findMachine) {
            ok(machines.findMachine(db, request.name))
        }

        implement(MachineTypes.defaultMachine) {
            ok(machines.findDefaultMachine(db))
        }

        implement(MachineTypes.listMachines) {
            ok(machines.listMachines(db))
        }

        implement(MachineTypes.createMachine) {
            ok(machines.addMachine(db, ctx.securityToken.toActor(), request))
        }

        implement(MachineTypes.markAsInactive) {
            ok(machines.markAsInactive(db, ctx.securityToken.toActor(), request.name))
        }

        implement(MachineTypes.setAsDefault) {
            ok(machines.markAsDefault(db, ctx.securityToken.toActor(), request.name))
        }

        implement(MachineTypes.updateMachine) {
            ok(machines.updateMachine(db, ctx.securityToken.toActor(), request))
        }

        return@with
    }
}