[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# Example: Registering a Provider

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
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
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
            "filterName": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
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
    "permissions": null
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
#     "permissions": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/providers_registration.png)

</details>


