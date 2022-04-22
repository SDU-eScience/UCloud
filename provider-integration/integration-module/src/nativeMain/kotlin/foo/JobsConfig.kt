package dk.sdu.cloud

import dk.sdu.cloud.Config
import kotlinx.serialization.*

@Serializable
@SerialName("MyJob")
data class MyJobConfig(
    override val matches: String,

    val foobar: String,
) : Config.Plugins.Jobs()

