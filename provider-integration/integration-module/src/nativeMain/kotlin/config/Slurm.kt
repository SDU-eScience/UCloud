package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("Slurm")
data class SlurmConfig(
    override val matches: String,
    val partition: String,
) : ConfigSchema.Plugins.Jobs()

