package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.api.Product

@Deprecated("Renamed to MachineTemplate", replaceWith = ReplaceWith("MachineTemplate"))
typealias MachineReservation = MachineTemplate
typealias MachineTemplate = Product.Compute
