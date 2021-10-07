<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/projects/favorites.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/products.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / Providers
# Providers

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Providers, the backbone of UCloud, expose compute and storage resources to end-users._

## Rationale

UCloud/Core is an orchestrator of [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)  s. This means, that the core doesn't actually know how 
to serve files or run computational workloads. Instead, the core must ask one or more [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  s 
to fulfil requests from the user.

![](/backend/accounting-service/wiki/overview.png)

__Figure:__ UCloud/Core receives a request from the user and forwards it to a provider.

The core isn't a simple proxy. Before passing the request, UCloud performs the following tasks:

- __Authentication:__ UCloud ensures that users have authenticated.
- __Authorization:__ The [`Project`](/docs/reference/dk.sdu.cloud.project.api.Project.md)  system of UCloud brings role-based 
  authorization to all [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)  s. The core verifies all actions before forwarding the request.
- __Resolving references:__ UCloud maintains a catalog of all [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)  s in the system. All user 
  requests only contain a reference to these [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)  s. UCloud verifies and resolves all 
  references before proxying the request.

The communication between UCloud/Core and the provider happens through the __provider APIs__. Throughout the 
developer guide, you will find various sections describing these APIs. These APIs contain both an ingoing 
(from the provider's perspective) and outgoing APIs. This allows for bidirectional communication between 
both parties. In almost all cases, the communication from the user goes through UCloud/Core. The only 
exception to this rule is when the data involved is either sensitive or large. In these cases, UCloud will 
only be responsible for facilitating direct communication. A common example of this is 
[file uploads](/docs/reference/files.createUpload.md).

## Table of Contents
<details>
<summary>
<a href='#example-definition-of-a-provider-(retrieval)'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-definition-of-a-provider-(retrieval)'>Definition of a Provider (Retrieval)</a></td></tr>
<tr><td><a href='#example-registering-a-provider'>Registering a Provider</a></td></tr>
<tr><td><a href='#example-a-provider-authenticating-with-ucloud/core'>A Provider authenticating with UCloud/Core</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#browse'><code>browse</code></a></td>
<td>Browses the catalog of available resources</td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieve a single resource</td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for all accessible providers</td>
</tr>
<tr>
<td><a href='#retrievespecification'><code>retrieveSpecification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td>Searches the catalog of available resources</td>
</tr>
<tr>
<td><a href='#approve'><code>approve</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates one or more resources</td>
</tr>
<tr>
<td><a href='#renewtoken'><code>renewToken</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#requestapproval'><code>requestApproval</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL attached to a resource</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#provider'><code>Provider</code></a></td>
<td>A `Resource` is the core data model used to synchronize tasks between UCloud and a</td>
</tr>
<tr>
<td><a href='#providerincludeflags'><code>ProviderIncludeFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providerspecification'><code>ProviderSpecification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providerstatus'><code>ProviderStatus</code></a></td>
<td>Describes the current state of the `Resource`</td>
</tr>
<tr>
<td><a href='#providersupport'><code>ProviderSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourcebilling.free'><code>ResourceBilling.Free</code></a></td>
<td>Contains information related to the accounting/billing of a `Resource`</td>
</tr>
<tr>
<td><a href='#resourceowner'><code>ResourceOwner</code></a></td>
<td>The owner of a `Resource`</td>
</tr>
<tr>
<td><a href='#resourcepermissions'><code>ResourcePermissions</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updatedacl'><code>UpdatedAcl</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersapproverequest'><code>ProvidersApproveRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrenewrefreshtokenrequestitem'><code>ProvidersRenewRefreshTokenRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrequestapprovalrequest'><code>ProvidersRequestApprovalRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrequestapprovalrequest.information'><code>ProvidersRequestApprovalRequest.Information</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrequestapprovalrequest.sign'><code>ProvidersRequestApprovalRequest.Sign</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrequestapprovalresponse'><code>ProvidersRequestApprovalResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrequestapprovalresponse.awaitingadministratorapproval'><code>ProvidersRequestApprovalResponse.AwaitingAdministratorApproval</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providersrequestapprovalresponse.requiressignature'><code>ProvidersRequestApprovalResponse.RequiresSignature</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Definition of a Provider (Retrieval)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>A UCloud administrator (<code>admin</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* This example shows an example provider. The provider's specification contains basic contact
information. This information is used by UCloud when it needs to communicate with a provider. */

Providers.retrieveSpecification.call(
    FindByStringId(
        id = "51231", 
    ),
    admin
).orThrow()

/*
ProviderSpecification(
    domain = "provider.example.com", 
    https = true, 
    id = "example", 
    port = 443, 
    product = ProductReference(
        category = "", 
        id = "", 
        provider = "ucloud_core", 
    ), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example shows an example provider. The provider's specification contains basic contact
information. This information is used by UCloud when it needs to communicate with a provider. */

// Authenticated as admin
await callAPI(ProvidersApi.retrieveSpecification(
    {
        "id": "51231"
    }
);

/*
{
    "id": "example",
    "domain": "provider.example.com",
    "https": true,
    "port": 443,
    "product": {
        "id": "",
        "category": "",
        "provider": "ucloud_core"
    }
}
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

# This example shows an example provider. The provider's specification contains basic contact
# information. This information is used by UCloud when it needs to communicate with a provider.

# Authenticated as admin
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/providers/retrieveSpecification?id=51231" 

# {
#     "id": "example",
#     "domain": "provider.example.com",
#     "https": true,
#     "port": 443,
#     "product": {
#         "id": "",
#         "category": "",
#         "provider": "ucloud_core"
#     }
# }

```


</details>


## Example: Registering a Provider
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The integration module (unauthenticated) (<code>integrationModule</code>)</li>
<li>The admin of the provider (authenticated as a normal UCloud user) (<code>systemAdministrator</code>)</li>
<li>A UCloud administrator (<code>admin</code>)</li>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* This example shows how a Provider registers with UCloud/Core. In this example, the Provider will 
be using the Integration Module. The system administrator, of the Provider, has just installed the 
Integration Module. Before starting the module, the system administrator has configured the module 
to contact UCloud at a known address. */


/* When the system administrator launches the Integration Module, it will automatically contact 
UCloud. This request contains the contact information back to the Provider. */

Providers.requestApproval.call(
    ProvidersRequestApprovalRequest.Information(
        specification = ProviderSpecification(
            domain = "provider.example.com", 
            https = true, 
            id = "example", 
            port = null, 
            product = ProductReference(
                category = "", 
                id = "", 
                provider = "ucloud_core", 
            ), 
        ), 
    ),
    integrationModule
).orThrow()

/*
ProvidersRequestApprovalResponse.RequiresSignature(
    token = "9eb96d0a27b1330cdc727ef4316bd48265f71414", 
)
*/

/* UCloud/Core responds with a token and the IM displays a link to the sysadmin. The sysadmin follows 
this link, and authenticates with their own UCloud user. This triggers the following request: */

Providers.requestApproval.call(
    ProvidersRequestApprovalRequest.Sign(
        token = "9eb96d0a27b1330cdc727ef4316bd48265f71414", 
    ),
    integrationModule
).orThrow()

/*
ProvidersRequestApprovalResponse.RequiresSignature(
    token = "9eb96d0a27b1330cdc727ef4316bd48265f71414", 
)
*/

/* The sysadmin now sends his token to a UCloud administrator. This communication always happen 
out-of-band. For a production system, we expect to have been in a dialogue with you about this 
process already.

The UCloud administrator approves the request. */

Providers.approve.call(
    ProvidersApproveRequest(
        token = "9eb96d0a27b1330cdc727ef4316bd48265f71414", 
    ),
    admin
).orThrow()

/*
FindByStringId(
    id = "51231", 
)
*/

/* UCloud/Core sends a welcome message to the Integration Module. The core uses the original token to 
authenticate the request. The request also contains the refreshToken and publicKey required by the 
IM. Under normal circumstances, the IM will auto-configure itself to use these tokens. */

IntegrationProvider.welcome.call(
    IntegrationProviderWelcomeRequest(
        createdProvider = ProviderWelcomeTokens(
            publicKey = "~~ public key ~~", 
            refreshToken = "8accc446c2e3ac924ff07c77d93e1679378a5dad", 
        ), 
        token = "9eb96d0a27b1330cdc727ef4316bd48265f71414", 
    ),
    ucloud
).orThrow()

/*
Unit
*/

/* Alternatively, the sysadmin can read the tokens and perform manual configuration. */

Providers.retrieve.call(
    ResourceRetrieveRequest(
        flags = ProviderIncludeFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterName = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            includeOthers = false, 
            includeProduct = false, 
            includeSupport = false, 
            includeUpdates = false, 
        ), 
        id = "51231", 
    ),
    systemAdministrator
).orThrow()

/*
Provider(
    acl = null, 
    billing = ResourceBilling.Free, 
    createdAt = 1633329776235, 
    id = "51231", 
    owner = ResourceOwner(
        createdBy = "sysadmin", 
        project = null, 
    ), 
    permissions = null, 
    publicKey = "~~ public key ~~", 
    refreshToken = "8accc446c2e3ac924ff07c77d93e1679378a5dad", 
    specification = ProviderSpecification(
        domain = "provider.example.com", 
        https = true, 
        id = "example", 
        port = null, 
        product = ProductReference(
            category = "", 
            id = "", 
            provider = "ucloud_core", 
        ), 
    ), 
    status = ProviderStatus(
        resolvedProduct = null, 
        resolvedSupport = null, 
    ), 
    updates = emptyList(), 
    providerGeneratedId = "51231", 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example shows how a Provider registers with UCloud/Core. In this example, the Provider will 
be using the Integration Module. The system administrator, of the Provider, has just installed the 
Integration Module. Before starting the module, the system administrator has configured the module 
to contact UCloud at a known address. */


/* When the system administrator launches the Integration Module, it will automatically contact 
UCloud. This request contains the contact information back to the Provider. */

// Authenticated as integrationModule
await callAPI(ProvidersApi.requestApproval(
    {
        "type": "information",
        "specification": {
            "id": "example",
            "domain": "provider.example.com",
            "https": true,
            "port": null,
            "product": {
                "id": "",
                "category": "",
                "provider": "ucloud_core"
            }
        }
    }
);

/*
{
    "type": "requires_signature",
    "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
}
*/

/* UCloud/Core responds with a token and the IM displays a link to the sysadmin. The sysadmin follows 
this link, and authenticates with their own UCloud user. This triggers the following request: */

await callAPI(ProvidersApi.requestApproval(
    {
        "type": "sign",
        "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
    }
);

/*
{
    "type": "requires_signature",
    "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
}
*/

/* The sysadmin now sends his token to a UCloud administrator. This communication always happen 
out-of-band. For a production system, we expect to have been in a dialogue with you about this 
process already.

The UCloud administrator approves the request. */

// Authenticated as admin
await callAPI(ProvidersApi.approve(
    {
        "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
    }
);

/*
{
    "id": "51231"
}
*/

/* UCloud/Core sends a welcome message to the Integration Module. The core uses the original token to 
authenticate the request. The request also contains the refreshToken and publicKey required by the 
IM. Under normal circumstances, the IM will auto-configure itself to use these tokens. */

// Authenticated as ucloud
await callAPI(ExampleImApi.welcome(
    {
        "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414",
        "createdProvider": {
            "refreshToken": "8accc446c2e3ac924ff07c77d93e1679378a5dad",
            "publicKey": "~~ public key ~~"
        }
    }
);

/*
{
}
*/

/* Alternatively, the sysadmin can read the tokens and perform manual configuration. */

// Authenticated as systemAdministrator
await callAPI(ProvidersApi.retrieve(
    {
        "flags": {
            "includeOthers": false,
            "includeUpdates": false,
            "includeSupport": false,
            "includeProduct": false,
            "filterCreatedBy": null,
            "filterCreatedAfter": null,
            "filterCreatedBefore": null,
            "filterProvider": null,
            "filterProductId": null,
            "filterProductCategory": null,
            "filterProviderIds": null,
            "filterIds": null,
            "filterName": null
        },
        "id": "51231"
    }
);

/*
{
    "id": "51231",
    "specification": {
        "id": "example",
        "domain": "provider.example.com",
        "https": true,
        "port": null,
        "product": {
            "id": "",
            "category": "",
            "provider": "ucloud_core"
        }
    },
    "refreshToken": "8accc446c2e3ac924ff07c77d93e1679378a5dad",
    "publicKey": "~~ public key ~~",
    "createdAt": 1633329776235,
    "status": {
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "updates": [
    ],
    "owner": {
        "createdBy": "sysadmin",
        "project": null
    },
    "permissions": null,
    "billing": {
    },
    "acl": null
}
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

# This example shows how a Provider registers with UCloud/Core. In this example, the Provider will 
# be using the Integration Module. The system administrator, of the Provider, has just installed the 
# Integration Module. Before starting the module, the system administrator has configured the module 
# to contact UCloud at a known address.

# When the system administrator launches the Integration Module, it will automatically contact 
# UCloud. This request contains the contact information back to the Provider.

# Authenticated as integrationModule
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/providers/requestApproval" -d '{
    "type": "information",
    "specification": {
        "id": "example",
        "domain": "provider.example.com",
        "https": true,
        "port": null,
        "product": {
            "id": "",
            "category": "",
            "provider": "ucloud_core"
        }
    }
}'


# {
#     "type": "requires_signature",
#     "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
# }

# UCloud/Core responds with a token and the IM displays a link to the sysadmin. The sysadmin follows 
# this link, and authenticates with their own UCloud user. This triggers the following request:

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/providers/requestApproval" -d '{
    "type": "sign",
    "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
}'


# {
#     "type": "requires_signature",
#     "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
# }

# The sysadmin now sends his token to a UCloud administrator. This communication always happen 
# out-of-band. For a production system, we expect to have been in a dialogue with you about this 
# process already.
# 
# The UCloud administrator approves the request.

# Authenticated as admin
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/providers/approve" -d '{
    "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414"
}'


# {
#     "id": "51231"
# }

# UCloud/Core sends a welcome message to the Integration Module. The core uses the original token to 
# authenticate the request. The request also contains the refreshToken and publicKey required by the 
# IM. Under normal circumstances, the IM will auto-configure itself to use these tokens.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/example/integration/welcome" -d '{
    "token": "9eb96d0a27b1330cdc727ef4316bd48265f71414",
    "createdProvider": {
        "refreshToken": "8accc446c2e3ac924ff07c77d93e1679378a5dad",
        "publicKey": "~~ public key ~~"
    }
}'


# {
# }

# Alternatively, the sysadmin can read the tokens and perform manual configuration.

# Authenticated as systemAdministrator
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/providers/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=51231" 

# {
#     "id": "51231",
#     "specification": {
#         "id": "example",
#         "domain": "provider.example.com",
#         "https": true,
#         "port": null,
#         "product": {
#             "id": "",
#             "category": "",
#             "provider": "ucloud_core"
#         }
#     },
#     "refreshToken": "8accc446c2e3ac924ff07c77d93e1679378a5dad",
#     "publicKey": "~~ public key ~~",
#     "createdAt": 1633329776235,
#     "status": {
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "updates": [
#     ],
#     "owner": {
#         "createdBy": "sysadmin",
#         "project": null
#     },
#     "permissions": null,
#     "billing": {
#     },
#     "acl": null
# }

```


</details>


## Example: A Provider authenticating with UCloud/Core
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>The provider has already been registered with UCloud/Core</li>
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

/* 📝 Note: The tokens shown here are not representative of tokens you will see in practice */

AuthProviders.refresh.call(
    bulkRequestOf(RefreshToken(
        refreshToken = "fb69e4367ee0fe4c76a4a926394aee547a41d998", 
    )),
    provider
).orThrow()

/*
BulkResponse(
    responses = listOf(AccessToken(
        accessToken = "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo", 
    )), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* 📝 Note: The tokens shown here are not representative of tokens you will see in practice */

// Authenticated as provider
await callAPI(AuthProvidersApi.refresh(
    {
        "items": [
            {
                "refreshToken": "fb69e4367ee0fe4c76a4a926394aee547a41d998"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "accessToken": "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
        }
    ]
}
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

# 📝 Note: The tokens shown here are not representative of tokens you will see in practice

# Authenticated as provider
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/auth/providers/refresh" -d '{
    "items": [
        {
            "refreshToken": "fb69e4367ee0fe4c76a4a926394aee547a41d998"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "accessToken": "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
#         }
#     ]
# }

```


</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#providerincludeflags'>ProviderIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#provider'>Provider</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#providerincludeflags'>ProviderIncludeFlags</a>&gt;</code>|<code><a href='#provider'>Provider</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#providersupport'>ProviderSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `retrieveSpecification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#providerspecification'>ProviderSpecification</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `search`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#providerincludeflags'>ProviderIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#provider'>Provider</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `approve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#providersapproverequest'>ProvidersApproveRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#providerspecification'>ProviderSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `renewToken`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#providersrenewrefreshtokenrequestitem'>ProvidersRenewRefreshTokenRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `requestApproval`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#providersrequestapprovalrequest'>ProvidersRequestApprovalRequest</a></code>|<code><a href='#providersrequestapprovalresponse'>ProvidersRequestApprovalResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#updatedacl'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Provider`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Resource` is the core data model used to synchronize tasks between UCloud and a_

```kotlin
data class Provider(
    val id: String,
    val specification: ProviderSpecification,
    val refreshToken: String,
    val publicKey: String,
    val createdAt: Long,
    val status: ProviderStatus,
    val updates: List<ProviderUpdate>,
    val owner: ResourceOwner,
    val permissions: ResourcePermissions?,
    val acl: List<ResourceAclEntry>?,
    val billing: ResourceBilling.Free,
    val providerGeneratedId: String?,
)
```
[provider](/backend/provider-service/README.md).

`Resource`s provide instructions to providers on how they should complete a given task. Examples of a `Resource`
include: [Compute jobs](/backend/app-orchestrator-service/README.md), HTTP ingress points and license servers. For
example, a (compute) `Job` provides instructions to the provider on how to start a software computation. It also gives
the provider APIs for communicating the status of the `Job`.

All `Resource` share a common interface and data model. The data model contains a specification of the `Resource`, along
with metadata, such as: ownership, billing and status.

`Resource`s are created in UCloud when a user requests it. This request is verified by UCloud and forwarded to the
provider. It is then up to the provider to implement the functionality of the `Resource`.

![](/backend/provider-service/wiki/resource_create.svg)

__Figure:__ UCloud orchestrates with the provider to create a `Resource`

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique identifier referencing the `Resource`
</summary>



The ID is unique across a provider for a single resource type.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#providerspecification'>ProviderSpecification</a></code></code>
</summary>





</details>

<details>
<summary>
<code>refreshToken</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>publicKey</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#providerstatus'>ProviderStatus</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ProviderUpdate.md'>ProviderUpdate</a>&gt;</code></code> Contains a list of updates from the provider as well as UCloud
</summary>



Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='#resourceowner'>ResourceOwner</a></code></code> Contains information about the original creator of the `Resource` along with project association
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='#resourcepermissions'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>acl</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>billing</code>: <code><code><a href='#resourcebilling.free'>ResourceBilling.Free</a></code></code>
</summary>





</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ProviderIncludeFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProviderIncludeFlags(
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val includeProduct: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterIds: String?,
    val filterName: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
</summary>





</details>

<details>
<summary>
<code>filterCreatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProviderIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the provider ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>filterIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the resource ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>filterName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ProviderSpecification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProviderSpecification(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int?,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>domain</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>https</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>port</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>



</details>



---

### `ProviderStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class ProviderStatus(
    val resolvedSupport: ResolvedSupport<Product, ProviderSupport>?,
    val resolvedProduct: Product?,
)
```
The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#providersupport'>ProviderSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `ProviderSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProviderSupport(
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>



</details>



---

### `ResourceBilling.Free`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Contains information related to the accounting/billing of a `Resource`_

```kotlin
data class Free(
    val creditsCharged: Long,
    val pricePerUnit: Long,
)
```
Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
to be charged a different price than newly launched products.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>creditsCharged</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `ResourceOwner`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The owner of a `Resource`_

```kotlin
data class ResourceOwner(
    val createdBy: String,
    val project: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>project</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ResourcePermissions`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourcePermissions(
    val myself: List<Permission>,
    val others: List<ResourceAclEntry>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>myself</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.Permission.md'>Permission</a>&gt;</code></code> The permissions that the requesting user has access to
</summary>





</details>

<details>
<summary>
<code>others</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;?</code></code> The permissions that other users might have access to
</summary>



This value typically needs to be included through the `includeFullPermissions` flag


</details>



</details>



---

### `UpdatedAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdatedAcl(
    val id: String,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>added</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>deleted</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.AclEntity.md'>AclEntity</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ProvidersApproveRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProvidersApproveRequest(
    val token: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ProvidersRenewRefreshTokenRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProvidersRenewRefreshTokenRequestItem(
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ProvidersRequestApprovalRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ProvidersRequestApprovalRequest {
    class Information : ProvidersRequestApprovalRequest()
    class Sign : ProvidersRequestApprovalRequest()
}
```



---

### `ProvidersRequestApprovalRequest.Information`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Information(
    val specification: ProviderSpecification,
    val type: String /* "information" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>specification</code>: <code><code><a href='#providerspecification'>ProviderSpecification</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "information" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ProvidersRequestApprovalRequest.Sign`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Sign(
    val token: String,
    val type: String /* "sign" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "sign" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ProvidersRequestApprovalResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ProvidersRequestApprovalResponse {
    class RequiresSignature : ProvidersRequestApprovalResponse()
    class AwaitingAdministratorApproval : ProvidersRequestApprovalResponse()
}
```



---

### `ProvidersRequestApprovalResponse.AwaitingAdministratorApproval`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AwaitingAdministratorApproval(
    val token: String,
    val type: String /* "awaiting_admin_approval" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "awaiting_admin_approval" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ProvidersRequestApprovalResponse.RequiresSignature`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RequiresSignature(
    val token: String,
    val type: String /* "requires_signature" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "requires_signature" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

