package dk.sdu.cloud.app.orchestrator.api

data class MachineReservation(
    val name: String,
    val cpu: Int?,
    val memoryInGigs: Int?
) {
    companion object {
        val BURST = MachineReservation("Burst", null, null)
    }
}
