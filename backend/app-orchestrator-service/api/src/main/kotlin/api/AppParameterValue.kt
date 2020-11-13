package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.ktor.http.*
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
    JsonSubTypes.Type(value = AppParameterValue.File::class, name = "file"),
    JsonSubTypes.Type(value = AppParameterValue.Bool::class, name = "boolean"),
    JsonSubTypes.Type(value = AppParameterValue.Text::class, name = "text"),
    JsonSubTypes.Type(value = AppParameterValue.Integer::class, name = "integer"),
    JsonSubTypes.Type(value = AppParameterValue.FloatingPoint::class, name = "floating_point"),
    JsonSubTypes.Type(value = AppParameterValue.Peer::class, name = "peer"),
    JsonSubTypes.Type(value = AppParameterValue.License::class, name = "license_server"),
    JsonSubTypes.Type(value = AppParameterValue.Network::class, name = "network"),
    JsonSubTypes.Type(value = AppParameterValue.BlockStorage::class, name = "block_storage"),
    JsonSubTypes.Type(value = AppParameterValue.Ingress::class, name = "ingress"),
)
sealed class AppParameterValue {
    @UCloudApiDoc(
        "A reference to a UCloud file\n\n" +
            "The path to the file most always be absolute an refers to either a UCloud directory or file."
    )
    data class File(val path: String, var readOnly: Boolean = false) : AppParameterValue()

    @UCloudApiDoc("A boolean value")
    data class Bool(val value: Boolean) : AppParameterValue()

    @UCloudApiDoc("A textual value")
    data class Text(val value: String) : AppParameterValue()

    @UCloudApiDoc(
        "An integral value\n\n" +
            "Internally this uses a big integer type and there are no defined limits."
    )
    data class Integer(val value: BigInteger) : AppParameterValue()

    @UCloudApiDoc(
        "A floating point value\n\n" +
            "Internally this uses a big decimal type and there are no defined limits."
    )
    data class FloatingPoint(val value: BigDecimal) : AppParameterValue()

    @UCloudApiDoc(
        "A reference to a separate UCloud job\n\n" +
            "The compute provider should use this information to make sure that the two jobs can communicate with " +
            "each other."
    )
    data class Peer(val hostname: String, val jobId: String) : AppParameterValue() {
        init {
            if (!hostname.matches(hostNameRegex)) {
                throw RPCException("Invalid hostname: $hostname", HttpStatusCode.BadRequest)
            }
        }
    }

    @UCloudApiDoc("A reference to a license")
    data class License(
        val id: String,

        @UCloudApiDoc("Ignored in user requests - Filled in by UCloud")
        val address: String = "",

        @UCloudApiDoc("Ignored in user requests - Filled in by UCloud")
        val port: Int = -1,

        @UCloudApiDoc("Ignored in user requests - Filled in by UCloud")
        val license: String? = null,
    ) : AppParameterValue()

    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    @UCloudApiDoc("A reference to block storage")
    data class BlockStorage(val id: String)

    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    @UCloudApiDoc("A reference to block storage")
    data class Network(val id: String)

    @UCloudApiExperimental(UCloudApiExperimental.Level.ALPHA)
    @UCloudApiDoc("HTTP Ingress")
    data class Ingress(val domain: String) {
        init {
            if (!domain.matches(hostNameRegex)) {
                throw RPCException("Invalid domain: $domain", HttpStatusCode.BadRequest)
            }
        }
    }
}

private val hostNameRegex =
    Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
        "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$")
