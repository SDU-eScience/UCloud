package dk.sdu.cloud.app.orchestrator.api

data class MachineReservation(
    val name: String,
    val cpu: Int?,
    val memoryInGigs: Int?,
    val gpu: Int? = null
) {
    init {
        if (gpu != null) require(gpu >= 0) { "gpu is negative ($this)" }
        if (cpu != null) require(cpu >= 0) { "cpu is negative ($this)" }
        if (memoryInGigs != null) require(memoryInGigs >= 0) { "memoryInGigs is negative ($this)" }
    }

    companion object {
        val BURST = MachineReservation("Burst", null, null, null)
    }
}
