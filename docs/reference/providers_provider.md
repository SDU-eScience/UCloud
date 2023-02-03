[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# Example: Definition of a Provider (Retrieval)

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

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/providers_provider.png)

</details>


