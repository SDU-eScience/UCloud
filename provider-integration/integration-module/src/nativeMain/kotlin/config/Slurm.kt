package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("Slurm")
data class SlurmConfig(
    override val matches: String,
    val partition: String,
    val mountpoint: String,
    val useFakeMemoryAllocations: Boolean = false,
) : ConfigSchema.Plugins.Jobs()

