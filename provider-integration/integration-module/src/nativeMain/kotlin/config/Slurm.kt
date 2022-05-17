package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("Slurm")
data class SlurmConfig(
    override val matches: String,
    val partition: String,
    val useFakeMemoryAllocations: Boolean = false,
    val accountMapper: AccountMapper = AccountMapper.None(),
    val modifySlurmConf: String? = "/etc/slurm/slurm.conf",
) : ConfigSchema.Plugins.Jobs() {
    @Serializable
    sealed class AccountMapper {
        // This mapper will always return no preferred accounts, which will cause slurm to always use the default.
        // This implicitly turns off accounting of all jobs which are not known to UCloud/IM.
        @Serializable
        @SerialName("None")
        class None : AccountMapper()

        @Serializable
        @SerialName("Extension")
        data class Extension(val extension: String) : AccountMapper()
    }
}
