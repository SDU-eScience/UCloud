package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.service.TYPE_PROPERTY
import java.math.BigDecimal
import java.math.BigInteger

@UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
@UCloudApiDoc("A parameter supplied to a compute job")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AppParameter.File::class, name = "file"),
    JsonSubTypes.Type(value = AppParameter.Bool::class, name = "boolean"),
    JsonSubTypes.Type(value = AppParameter.Text::class, name = "text"),
    JsonSubTypes.Type(value = AppParameter.Integer::class, name = "integer"),
    JsonSubTypes.Type(value = AppParameter.FloatingPoint::class, name = "floating_point"),
    JsonSubTypes.Type(value = AppParameter.Peer::class, name = "peer"),
    JsonSubTypes.Type(value = AppParameter.License::class, name = "license_server"),
    JsonSubTypes.Type(value = AppParameter.Network::class, name = "network"),
    JsonSubTypes.Type(value = AppParameter.BlockStorage::class, name = "block_storage"),
    JsonSubTypes.Type(value = AppParameter.Ingress::class, name = "ingress"),
)
sealed class AppParameter {
    @UCloudApiDoc(
        "A reference to a UCloud file\n\n" +
            "The path to the file most always be absolute an refers to either a UCloud directory or file."
    )
    data class File(val path: String) : AppParameter()

    @UCloudApiDoc("A boolean value")
    data class Bool(val value: Boolean) : AppParameter()

    @UCloudApiDoc("A textual value")
    data class Text(val value: String) : AppParameter()

    @UCloudApiDoc(
        "An integral value\n\n" +
            "Internally this uses a big integer type and there are no defined limits."
    )
    data class Integer(val value: BigInteger) : AppParameter()

    @UCloudApiDoc(
        "A floating point value\n\n" +
            "Internally this uses a big decimal type and there are no defined limits."
    )
    data class FloatingPoint(val value: BigDecimal) : AppParameter()

    @UCloudApiDoc(
        "A reference to a separate UCloud job\n\n" +
            "The compute provider should use this information to make sure that the two jobs can communicate with " +
            "each other."
    )
    data class Peer(val peerJobId: String) : AppParameter()

    @UCloudApiDoc("A reference to a license")
    data class License(val id: String, val address: String, val port: Int, val license: String?) : AppParameter()

    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    @UCloudApiDoc("A reference to block storage")
    data class BlockStorage(val id: String)

    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    @UCloudApiDoc("A reference to block storage")
    data class Network(val id: String)

    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    @UCloudApiDoc("HTTP Ingress")
    data class Ingress(val id: String)
}
