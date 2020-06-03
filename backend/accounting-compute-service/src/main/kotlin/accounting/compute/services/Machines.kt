package dk.sdu.cloud.accounting.compute.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.compute.MachineTemplate
import dk.sdu.cloud.accounting.compute.MachineType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode

object MachineTable : SQLTable("machines") {
    val name = text("name", notNull = true)
    val type = text("type", notNull = true)
    val pricePerHour = long("price_per_hour", notNull = true)
    val active = bool("active", notNull = false)
    val defaultMachine = bool("default_machine", notNull = false)

    val cpu = int("cpu", notNull = false)
    val gpu = int("gpu", notNull = false)
    val memoryInGigs = int("memory_in_gigs", notNull = false)
}

class MachineService {
    suspend fun listMachines(ctx: DBContext): List<MachineTemplate> {
        return ctx.withSession { session ->
            session
                .sendQuery("select * from machines where active = true")
                .rows
                .map { it.toMachine() }
        }
    }

    suspend fun findMachine(ctx: DBContext, name: String): MachineTemplate {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("name", name) },
                    "select * from machines where name = ?name"
                )
                .rows
                .singleOrNull()
                ?.toMachine()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun findDefaultMachine(ctx: DBContext): MachineTemplate {
        return ctx.withSession { session ->
            session
                .sendQuery("select * from machines where default_machine = true")
                .rows
                .singleOrNull()
                ?.toMachine()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun addMachine(
        ctx: DBContext,
        initiatedBy: Actor,
        machine: MachineTemplate
    ) {
        verifyWriteAccess(initiatedBy)

        ctx.withSession { session ->
            session.insert(MachineTable) {
                set(MachineTable.name, machine.name)
                set(MachineTable.cpu, machine.cpu)
                set(MachineTable.gpu, machine.gpu)
                set(MachineTable.memoryInGigs, machine.memoryInGigs)
                set(MachineTable.type, machine.type.name)
                set(MachineTable.pricePerHour, machine.pricePerHour)
                set(MachineTable.active, true)
                set(MachineTable.defaultMachine, false)
            }
        }
    }

    suspend fun markAsInactive(
        ctx: DBContext,
        initiatedBy: Actor,
        name: String
    ) {
        verifyWriteAccess(initiatedBy)

        ctx.withSession { session ->
            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("name", name)
                    },

                    """
                        update machines  
                        set active = false
                        where name = ?name
                    """
                )
                .rowsAffected > 0

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun markAsDefault(
        ctx: DBContext,
        initiatedBy: Actor,
        name: String
    ) {
        verifyWriteAccess(initiatedBy)

        ctx.withSession { session ->
            val success = session
                .sendPreparedStatement(
                    { setParameter("name", name) },
                    "update machines set default_machine = true where name = ?name"
                )
                .rowsAffected > 0

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            session.sendPreparedStatement(
                { setParameter("name", name) },
                "update machines set default_machine = false where name != ?name"
            )
        }
    }

    suspend fun updateMachine(
        ctx: DBContext,
        initiatedBy: Actor,
        updatedMachine: MachineTemplate
    ) {
        verifyWriteAccess(initiatedBy)
        ctx.withSession { session ->
            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("cpu", updatedMachine.cpu)
                        setParameter("memoryInGigs", updatedMachine.memoryInGigs)
                        setParameter("gpu", updatedMachine.gpu)
                        setParameter("pricePerHour", updatedMachine.pricePerHour)
                        setParameter("type", updatedMachine.type.name)
                    },

                    """
                        update machines
                        set 
                            cpu = ?cpu,
                            memory_in_gigs = ?memoryInGigs,
                            gpu = ?gpu,
                            price_per_hour = ?pricePerHour,
                            type = ?type
                        where name = ?name
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    private fun verifyWriteAccess(actor: Actor) {
        if (actor is Actor.User && actor.principal.role in Roles.PRIVILEDGED) return
        if (actor == Actor.System) return
        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    private fun RowData.toMachine(): MachineTemplate {
        val row = this
        return MachineTemplate(
            name = row.getField(MachineTable.name),
            type = row.getField(MachineTable.type).let { MachineType.valueOf(it) },
            pricePerHour = row.getField(MachineTable.pricePerHour),
            cpu = row.getFieldNullable(MachineTable.cpu),
            gpu = row.getFieldNullable(MachineTable.gpu),
            memoryInGigs = row.getFieldNullable(MachineTable.memoryInGigs)
        )
    }
}