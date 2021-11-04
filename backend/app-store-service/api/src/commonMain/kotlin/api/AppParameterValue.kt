package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.WithStringId
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc(
    """An `AppParameterValue` is value which is supplied to a parameter of an `Application`.
    
Each value type can is type-compatible with one or more `ApplicationParameter`s. The effect of a specific value depends
on its use-site, and the type of its associated parameter.

`ApplicationParameter`s have the following usage sites:

- Invocation: This affects the command line arguments passed to the software.
- Environment variables: This affects the environment variables passed to the software.
- Resources: This only affects the resources which are imported into the software environment. Not all values can be
  used as a resource.
""", importance = 925)
@Serializable
@UCloudApiOwnedBy(AppStore::class)
sealed class AppParameterValue {
    @UCloudApiDoc("""
        A reference to a UCloud file
        
        - __Compatible with:__ `ApplicationParameter.InputFile` and `ApplicationParameter.InputDirectory`
        - __Mountable as a resource:__ ✅ Yes
        - __Expands to:__ The absolute path to the file or directory in the software's environment
        - __Side effects:__ Includes the file or directory in the `Job`'s temporary work directory
            
        The path of the file must be absolute and refers to either a UCloud directory or file.
    """, importance = 924)
    @Serializable
    @SerialName("file")
    data class File(
        @UCloudApiDoc("The absolute path to the file or directory in UCloud")
        val path: String,
        @UCloudApiDoc(
            """Indicates if this file or directory should be mounted as read-only

A provider must reject the request if it does not support read-only mounts when `readOnly = true`.
"""
        )
        var readOnly: Boolean = false,
    ) : AppParameterValue()

    @UCloudApiDoc("""
        A boolean value (true or false)
    
        - __Compatible with:__ `ApplicationParameter.Bool`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ `trueValue` of `ApplicationParameter.Bool` if value is `true` otherwise `falseValue`
        - __Side effects:__ None
    """, importance = 924)
    @Serializable
    @SerialName("boolean")
    data class Bool(val value: Boolean) : AppParameterValue()

    @UCloudApiDoc(
        """A textual value
    
- __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.
"""
    )
    @Serializable
    @SerialName("textarea")
    data class TextArea(val value: String) : AppParameterValue()

    @UCloudApiDoc("""
        A textual value
    
        - __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
        - __Side effects:__ None

        When this is used with an `Enumeration` it must match the value of one of the associated `options`.
    """, importance = 924)
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : AppParameterValue()

    @UCloudApiDoc("""
        An integral value

        - __Compatible with:__ `ApplicationParameter.Integer`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ The number
        - __Side effects:__ None

        Internally this uses a big integer type and there are no defined limits.
    """, importance = 924)
    @Serializable
    @SerialName("integer")
    data class Integer(val value: Long) : AppParameterValue()

    @UCloudApiDoc("""
        A floating point value
    
        - __Compatible with:__ `ApplicationParameter.FloatingPoint`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ The number
        - __Side effects:__ None

        Internally this uses a big decimal type and there are no defined limits.
    """, importance = 924)
    @Serializable
    @SerialName("floating_point")
    data class FloatingPoint(val value: Double) : AppParameterValue()

    @UCloudApiDoc("""
        A reference to a separate UCloud `Job`
    
        - __Compatible with:__ `ApplicationParameter.Peer`
        - __Mountable as a resource:__ ✅ Yes
        - __Expands to:__ The `hostname`
        - __Side effects:__ Configures the firewall to allow bidirectional communication between this `Job` and the peering 
          `Job`
    """, importance = 924)
    @Serializable
    @SerialName("peer")
    data class Peer(val hostname: String, val jobId: String) : AppParameterValue() {
        init {
            if (!hostname.matches(hostNameRegex)) {
                throw RPCException("Invalid hostname: $hostname", HttpStatusCode.BadRequest)
            }

            if (hostname.length > 250) {
                throw RPCException("Hostname is too long: ${hostname.take(250)}...", HttpStatusCode.BadRequest)
            }
        }

    }

    @UCloudApiDoc("""
        A reference to a software license, registered locally at the provider
    
        - __Compatible with:__ `ApplicationParameter.LicenseServer`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ `${"$"}{license.address}:${"$"}{license.port}/${"$"}{license.key}` or 
          `${"$"}{license.address}:${"$"}{license.port}` if no key is provided
        - __Side effects:__ None
    """, importance = 924)
    @Serializable
    @SerialName("license_server")
    data class License(override val id: String) : AppParameterValue(), WithStringId

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("A reference to block storage (Not yet implemented)", importance = 924)
    @Serializable
    @SerialName("block_storage")
    data class BlockStorage(override val id: String) : AppParameterValue(), WithStringId

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("A reference to block storage (Not yet implemented)", importance = 924)
    @Serializable
    @SerialName("network")
    data class Network(override val id: String) : AppParameterValue(), WithStringId

    @UCloudApiDoc("""
        A reference to an HTTP ingress, registered locally at the provider
    
        - __Compatible with:__ `ApplicationParameter.Ingress`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ `${"$"}{id}`
        - __Side effects:__ Configures an HTTP ingress for the application's interactive web interface. This interface should
          not perform any validation, that is, the application should be publicly accessible.
    """, importance = 924)
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @Serializable
    @SerialName("ingress")
    data class Ingress(override val id: String) : AppParameterValue(), WithStringId
}

private val hostNameRegex =
    Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
        "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$")
