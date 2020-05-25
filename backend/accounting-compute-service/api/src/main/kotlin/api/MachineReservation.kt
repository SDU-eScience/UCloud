package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod

enum class MachineType {
    STANDARD,
    HIGH_MEMORY,
    GPU
}

data class MachineReservation(
    val name: String,
    val cpu: Int? = null,
    val memoryInGigs: Int? = null,
    val gpu: Int? = null,
    val pricePerHour: Long? = null,
    val type: MachineType = MachineType.STANDARD
) {
    init {
        if (gpu != null) require(gpu >= 0) { "gpu is negative ($this)" }
        if (cpu != null) require(cpu >= 0) { "cpu is negative ($this)" }
        if (memoryInGigs != null) require(memoryInGigs >= 0) { "memoryInGigs is negative ($this)" }
    }
}

typealias CreateMachineRequest = MachineReservation
typealias CreateMachineResponse = Unit

data class ListMachinesRequest(val viewInactive: Boolean?)
typealias ListMachinesResponse = List<MachineReservation>

data class MarkAsInactiveRequest(val name: String)
typealias MarkAsInactiveResponse = Unit

data class FindMachineRequest(val name: String)
typealias FindMachineResponse = MachineReservation

typealias DefaultMachineRequest = Unit
typealias DefaultMachineResponse = MachineReservation

data class SetAsDefaultRequest(val name: String)
typealias SetAsDefaultResponse = Unit

typealias UpdateMachineRequest = MachineReservation
typealias UpdateMachineResponse = Unit

object MachineTypes : CallDescriptionContainer("accounting.compute.machines") {
    val baseContext = "/api/accounting/compute/machines"

    val createMachine = call<CreateMachineRequest, CreateMachineResponse, CommonErrorMessage>("createMachine") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val defaultMachine = call<DefaultMachineRequest, DefaultMachineResponse, CommonErrorMessage>("defaultMachine") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"default-machine"
            }
        }
    }

    val listMachines = call<ListMachinesRequest, ListMachinesResponse, CommonErrorMessage>("listMachines") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListMachinesRequest::viewInactive)
            }
        }
    }

    val findMachine = call<FindMachineRequest, FindMachineResponse, CommonErrorMessage>("findMachine") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +boundTo(FindMachineRequest::name)
            }
        }
    }

    val markAsInactive = call<MarkAsInactiveRequest, MarkAsInactiveResponse, CommonErrorMessage>("markAsInactive") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"mark-as-inactive"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val setAsDefault = call<SetAsDefaultRequest, SetAsDefaultResponse, CommonErrorMessage>("setAsDefault") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"mark-as-default"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val updateMachine = call<UpdateMachineRequest, UpdateMachineResponse, CommonErrorMessage>("updateMachine") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}