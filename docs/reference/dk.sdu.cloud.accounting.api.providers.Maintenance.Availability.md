[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# `Maintenance.Availability`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Availability {
    MINOR_DISRUPTION,
    MAJOR_DISRUPTION,
    NO_SERVICE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>MINOR_DISRUPTION</code> You might encounter some disruption to the service, but the end-user might not notice this disruption.
</summary>



This will display a weak warning on the affected resources and products. Users will still be able to use the
resources.


</details>

<details>
<summary>
<code>MAJOR_DISRUPTION</code> You should expect some disruption of the service.
</summary>



This will display a prominent warning on the affected resources and products. Users will still be able to
use the resources.


</details>

<details>
<summary>
<code>NO_SERVICE</code> The service is unavailable.
</summary>



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


</details>



</details>


