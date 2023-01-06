[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# Example: Accounting

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>One or more active Jobs running at the Provider</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we show how a Provider can implement accounting. Accounting is done, periodically,
by the provider in a background process. We recommend that Providers combine this with the same
background processing required for state changes. */


/* You should read understand how Products work in UCloud. UCloud supports multiple ways of accounting
for usage. The most normal one, which we show here, is the `CREDITS_PER_MINUTE` policy. This policy
requires that a Provider charges credits (1 credit = 1/1_000_000 DKK) for every minute of usage. */


/* We assume that the Provider has just determined that Jobs "51231" (single replica) and "63489"
(23 replicas) each have used 15 minutes of compute time since last accounting iteration. */

JobsControl.chargeCredits.call(
    bulkRequestOf(ResourceChargeCredits(
        chargeId = "51231-charge-04-oct-2021-12:30", 
        description = null, 
        id = "51231", 
        performedBy = null, 
        periods = 1, 
        units = 15, 
    ), ResourceChargeCredits(
        chargeId = "63489-charge-04-oct-2021-12:30", 
        description = null, 
        id = "63489", 
        performedBy = null, 
        periods = 23, 
        units = 15, 
    )),
    provider
).orThrow()

/*
ResourceChargeCreditsResponse(
    duplicateCharges = emptyList(), 
    insufficientFunds = emptyList(), 
)
*/

/* 📝 Note: Because the ProductPriceUnit, of the Product associated with the Job, is
`CREDITS_PER_MINUTE` each unit corresponds to minutes of usage. A different ProductPriceUnit, for
example `CREDITS_PER_HOUR` would alter the definition of this unit. */


/* 📝 Note: The chargeId is an identifier which must be unique for any charge made by the Provider.
If the Provider makes a different charge request with this ID then the request will be ignored. We
recommend that Providers use this to their advantage and include, for example, a timestamp from
the last iteration. This means that you, as a Provider, cannot accidentally charge twice for the
same usage. */


/* In the next iteration, the Provider also determines that 15 minutes has passed for these Jobs. */

JobsControl.chargeCredits.call(
    bulkRequestOf(ResourceChargeCredits(
        chargeId = "51231-charge-04-oct-2021-12:45", 
        description = null, 
        id = "51231", 
        performedBy = null, 
        periods = 1, 
        units = 15, 
    ), ResourceChargeCredits(
        chargeId = "63489-charge-04-oct-2021-12:45", 
        description = null, 
        id = "63489", 
        performedBy = null, 
        periods = 23, 
        units = 15, 
    )),
    provider
).orThrow()

/*
ResourceChargeCreditsResponse(
    duplicateCharges = emptyList(), 
    insufficientFunds = listOf(FindByStringId(
        id = "63489", 
    )), 
)
*/

/* However, this time UCloud has told us that 63489 no longer has enough credits to pay for this.
The Provider should respond to this by immediately cancelling the Job, UCloud/Core does not perform
this step for you! */


/* 📝 Note: This request should be triggered by the normal life-cycle handler. */

JobsControl.update.call(
    bulkRequestOf(ResourceUpdateAndId(
        id = "63489", 
        update = JobUpdate(
            allowRestart = null, 
            expectedDifferentState = null, 
            expectedState = null, 
            newMounts = null, 
            newTimeAllocation = null, 
            outputFolder = null, 
            state = JobState.SUCCESS, 
            status = "The job was terminated (No credits)", 
            timestamp = 0, 
        ), 
    )),
    provider
).orThrow()

/*
Unit
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# In this example, we show how a Provider can implement accounting. Accounting is done, periodically,
# by the provider in a background process. We recommend that Providers combine this with the same
# background processing required for state changes.

# You should read understand how Products work in UCloud. UCloud supports multiple ways of accounting
# for usage. The most normal one, which we show here, is the `CREDITS_PER_MINUTE` policy. This policy
# requires that a Provider charges credits (1 credit = 1/1_000_000 DKK) for every minute of usage.

# We assume that the Provider has just determined that Jobs "51231" (single replica) and "63489"
# (23 replicas) each have used 15 minutes of compute time since last accounting iteration.

# Authenticated as provider
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/control/chargeCredits" -d '{
    "items": [
        {
            "id": "51231",
            "chargeId": "51231-charge-04-oct-2021-12:30",
            "units": 15,
            "periods": 1,
            "performedBy": null,
            "description": null
        },
        {
            "id": "63489",
            "chargeId": "63489-charge-04-oct-2021-12:30",
            "units": 15,
            "periods": 23,
            "performedBy": null,
            "description": null
        }
    ]
}'


# {
#     "insufficientFunds": [
#     ],
#     "duplicateCharges": [
#     ]
# }

# 📝 Note: Because the ProductPriceUnit, of the Product associated with the Job, is
# `CREDITS_PER_MINUTE` each unit corresponds to minutes of usage. A different ProductPriceUnit, for
# example `CREDITS_PER_HOUR` would alter the definition of this unit.

# 📝 Note: The chargeId is an identifier which must be unique for any charge made by the Provider.
# If the Provider makes a different charge request with this ID then the request will be ignored. We
# recommend that Providers use this to their advantage and include, for example, a timestamp from
# the last iteration. This means that you, as a Provider, cannot accidentally charge twice for the
# same usage.

# In the next iteration, the Provider also determines that 15 minutes has passed for these Jobs.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/control/chargeCredits" -d '{
    "items": [
        {
            "id": "51231",
            "chargeId": "51231-charge-04-oct-2021-12:45",
            "units": 15,
            "periods": 1,
            "performedBy": null,
            "description": null
        },
        {
            "id": "63489",
            "chargeId": "63489-charge-04-oct-2021-12:45",
            "units": 15,
            "periods": 23,
            "performedBy": null,
            "description": null
        }
    ]
}'


# {
#     "insufficientFunds": [
#         {
#             "id": "63489"
#         }
#     ],
#     "duplicateCharges": [
#     ]
# }

# However, this time UCloud has told us that 63489 no longer has enough credits to pay for this.
# The Provider should respond to this by immediately cancelling the Job, UCloud/Core does not perform
# this step for you!

# 📝 Note: This request should be triggered by the normal life-cycle handler.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/control/update" -d '{
    "items": [
        {
            "id": "63489",
            "update": {
                "state": "SUCCESS",
                "outputFolder": null,
                "status": "The job was terminated (No credits)",
                "expectedState": null,
                "expectedDifferentState": null,
                "newTimeAllocation": null,
                "allowRestart": null,
                "newMounts": null,
                "timestamp": 0
            }
        }
    ]
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs.provider.PROVIDERID_accounting.png)

</details>


