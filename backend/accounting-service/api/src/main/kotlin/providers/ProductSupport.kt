package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.calls.UCloudApiStable
import dk.sdu.cloud.calls.checkMinimumValue
import dk.sdu.cloud.calls.checkNotBlank
import dk.sdu.cloud.calls.checkTextLength
import dk.sdu.cloud.provider.api.Resources
import kotlinx.serialization.Serializable

interface ProductSupport {
    val product: ProductReference
    val maintenance: Maintenance?
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class Maintenance(
    @UCloudApiDoc("""
        A description of the scheduled/ongoing maintenance.
        
        The text may contain any type of character, but the operator should keep in mind that this will be displayed
        in a web-application. This text should be kept to only a single paragraph, but it may contain line-breaks as
        needed. This text must not be blank. The Core will require that this text contains at most 4000 characters.
    """)
    val description: String,

    @UCloudApiDoc("""
        Describes the availability of the affected service.
    """)
    val availability: Availability,

    @UCloudApiDoc("""
        Describes when the maintenance is expected to start.
        
        This is an ordinary UCloud timestamp (millis since unix epoch). The timestamp can be in the future (or past).
        But, the Core will enforce that the maintenance is in the "recent" past to ensure that the timestamp is not
        incorrect.
    """)
    val startsAt: Long,

    @UCloudApiDoc("""
        Describes when the maintenance is expected to end.
        
        This property is optional and can be left blank. In this case, users will not be notified about when the
        maintenance is expected to end. This can be useful if a product is reaching EOL. In either case, the description
        should be used to clarify the meaning of this property.
        
        This is an ordinary UCloud timestamp (millis since unix epoch). The timestamp can be in the future (or past).
        But, the Core will enforce that the maintenance is in the "recent" past to ensure that the timestamp is not
        incorrect.
    """)
    val estimatedEndsAt: Long? = null,
) {
    init {
        checkNotBlank(::description, description)
        checkTextLength(::description, description, maximumSize = 4000)

        // Sanity check that the timestamp is properly formatted (in millis). This requires that the timestamp are at
        // least in the current millennium.
        checkMinimumValue(::startsAt, startsAt, 1_000_000_000_000L)
        if (estimatedEndsAt != null) checkMinimumValue(::estimatedEndsAt, estimatedEndsAt, 1_000_000_000_000L)
    }

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    enum class Availability {
        @UCloudApiDoc("""
            You might encounter some disruption to the service, but the end-user might not notice this disruption.
            
            This will display a weak warning on the affected resources and products. Users will still be able to use the
            resources.
        """)
        MINOR_DISRUPTION,

        @UCloudApiDoc("""
            You should expect some disruption of the service.
            
            This will display a prominent warning on the affected resources and products. Users will still be able to
            use the resources.
        """)
        MAJOR_DISRUPTION,

        @UCloudApiDoc("""
            The service is unavailable.
            
            This will display a prominent warning on the affected resources and products. Users will _not_ be able to
            use the resources. This check is only enforced my the frontend, this means that any backend services will
            still have to reject the request. The frontend will allow normal operation if one of the following is true:
            
            - The current user is a UCloud administrator
            - The current user has a `localStorage` property with key `NO_MAINTENANCE_BLOCK`
            
            These users should still receive the normal warning. But, the user-interface will not block the 
            operations. Instead, these users will receive the normal responses. If the service is down, then this 
            will result in an error message.

            This is used intend in combination with a feature in the IM. This feature will allow an operator to 
            define an allow list of users who can always access the system. The operator should use this when they 
            wish to test the system following maintenance. During this period, only users on the allow list can use 
            the system. All other users will receive a generic error message indicating that the system is down for 
            maintenance.
        """)
        NO_SERVICE,
    }
}

@Serializable
@UCloudApiOwnedBy(Resources::class)
@UCloudApiStable
data class ResolvedSupport<P : Product, Support : ProductSupport>(
    val product: P,
    val support: Support
)
