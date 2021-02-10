# Compute Providers

In this document we describe the details of a UCloud compute provider. Any entity which provide one or more compute
features to UCloud is considered a compute provider. We will define exactly which features a provider can support in the
sections to come.

---

__📝 NOTE:__ This document describes the integration between UCloud and compute providers using the low-level protocols.
For a more high-level view we refer to this document (TODO).

---

![](/backend/app-orchestrator-service/wiki/provider.png)

__Figure:__ A simplified view of a common compute provider integrating with UCloud

## Integration Module

The integration module is a software module running locally at the provider. It is responsible for implementing the
low-level provider API. You can read more about the communication later in this document.

The module acts as a translator between UCloud and the job scheduler:

1. The module translates requests from UCloud into a commands understood by the job scheduler
2. The module translates information from the job scheduler into a format understood by UCloud

The low-level API is typically implemented by the UCloud integration module (TODO Link).

As an example, UCloud might send a request to the provider, asking to start a job. The job will contain information
about the user, along with the software and input data. The job of the integration module is to transform this request
into a format understood natively by the job scheduler. For example, if the job scheduler is implemented using Slurm, it
might result in `sbatch` being invoked by the integration module.

The integration module will track progress of the jobs and communicate this back to UCloud. Continuing with our Slurm
example, the integration module will track the progress of the jobs, for example by polling `squeue`, and pushing
updates to UCloud as they occur.

UCloud typically displays the `stdin`/`stderr` output of a job. This request is send by the user to UCloud. UCloud will
then verify that the user has permissions to read this information. The request is then forwarded to the provider, at
this point the provider might read the logs using `sattach` or a similar tool.

## Provider Manifest

Every provider expose a document called a _provider manifest_. This manifest is exposed via the integration module, and
contains information about the features supported by the provider.

### Example

TODO

### Reference

---

#### `ProviderManifest`

<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.ProviderManifest:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
UNKNOWN TYPE: dk.sdu.cloud.app.orchestrator.api.ProviderManifest
<!--</editor-fold>-->
<!-- /typedoc -->

---

#### `ManifestFeatureSupport`

<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
UNKNOWN TYPE: dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport
<!--</editor-fold>-->
<!-- /typedoc -->

---

#### `Compute`

<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport.Compute:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
UNKNOWN TYPE: dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport.Compute
<!--</editor-fold>-->
<!-- /typedoc -->

---

#### `Docker`

<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport.Compute.Docker:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
UNKNOWN TYPE: dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport.Compute.Docker
<!--</editor-fold>-->
<!-- /typedoc -->

---

#### `VirtualMachine`

<!-- typedoc:dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport.Compute.VirtualMachine:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
UNKNOWN TYPE: dk.sdu.cloud.app.orchestrator.api.ManifestFeatureSupport.Compute.VirtualMachine
<!--</editor-fold>-->
<!-- /typedoc -->

---

## Job Scheduler

The job scheduler is responsible for running `Job`s on behalf of users. The provider can tweak which features the
scheduler is able to support using the provider manifest.

UCloud puts no strict requirements on how the job scheduler runs job and leaves this to the provider. For example, this
means that there are no strict requirements on how jobs are queued. Jobs can be run in any order which the provider sees
fit.

## Machine Types

UCloud requires users to select a machine type when starting any `Job`. This machine type allows providers to select the
appropriate infrastructure to schedule the `Job` on. For example, this might determine which Slurm queue a specific
`Job` goes into. Providers can register machine types through
UCloud's [accounting](/backend/accounting-service/README.md) module.

<!-- typedoc:dk.sdu.cloud.accounting.api.Product.Compute:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | No documentation |
| `pricePerUnit` | `Long` | No documentation |
| `category` | `ProductCategoryId` | No documentation |
| `description` | `String?` | No documentation |
| `availability` | `ProductAvailability?` | No documentation |
| `priority` | `Int?` | No documentation |
| `cpu` | `Int?` | No documentation |
| `memoryInGigs` | `Int?` | No documentation |
| `gpu` | `Int?` | No documentation |
| `balance` | `Long` | Included only with certain endpoints which support `includeBalance` |
| `type` | `("compute")` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

## Communication

---

__📝 NOTE:__ Not yet implemented.

---

All communication between UCloud and a provider is done via an HTTP(S) API, certain optional endpoints use a WebSocket
API. Note that the WebSocket protocol is an extension of HTTP, as a result the WebSocket API shares the same security
aspects as the HTTP API.

TLS is strictly required for all providers running in a production environment. The provider must use a valid
certificate, which hasn't expired and signed by a commonly recognized Certificate Authority (CA). TLS for HTTPS
connections are handled internally in UCloud by OpenJDK11+. Notably, this means that TLSv1.3 is supported. We encourage
providers to follow best practices. For inspiration, Mozilla hosts an
online [SSL configuration generator](https://ssl-config.mozilla.org).
Additionally, [this document](https://github.com/ssllabs/research/wiki/SSL-and-TLS-Deployment-Best-Practices) from SSL
Labs can provide a good starting point.

Providers should treat UCloud similarly. An integration module should ensure that all certificates served by UCloud are
valid and signed by a commonly recognized CA.

For __local development purposes only__ UCloud can communicate with a __local__ provider using HTTP. It is not possible
to configure UCloud to use self-signed certificates, and as a result it is not possible to run a local provider with a
self-signed certificate + TLS. This design choice has been made to simplify the code and avoid poorly configured UCloud
deployments.

## Authentication and Authorization

---

__📝 NOTE:__ Not yet implemented.

---

UCloud _and_ the provider authenticates and authorizes all ingoing requests. These requests are protected by short-lived
[JSON Web Tokens (JWT)](https://jwt.io)

| Token | Type | Description |
|-------|------|-------------|
| `accessToken` | [JWT](https://jwt.io) | A short-lived JWT token for authenticating regular requests |
| `refreshToken` | Opaque | An opaque token, with no explicit expiration, used for requesting new `accessToken`s |

__Table:__ The two token types used in UCloud <=> Provider authentication

Because JWTs are short-lived, every provider must renew their JWT periodically. Providers do this by using an opaque
token called the `refreshToken`. The diagram below shows how a provider can use their `refreshToken` to generate a new
`accessToken`.

<!--
# https://sequencediagram.org/
title Requesting an access-token

database Auth DB
participant UCloud
participant "Provider P" as p


p->UCloud: auth.refresh() authenticated with refreshToken
UCloud->Auth DB: validate(refreshToken)
Auth DB->UCloud: SecurityPrincipal representing P
UCloud->p: AccessToken representing P

==P can now use the access-token==

p->UCloud: jobs.control.update(...) authenticated with accessToken
UCloud->Auth DB: Fetch keypair for P
Auth DB->UCloud: Keypair
note over UCloud: Verify accessToken using keypair
UCloud->p: OK
-->

![](/backend/app-orchestrator-service/wiki/access_token_request.svg)

__Figure:__ A provider requesting a new `accessToken` using their `refreshToken`

All calls use
the [HTTP bearer authentication scheme](https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#authentication_schemes)
. As a result the `refreshToken` will be passed in the `Authorization` header like this:

```
HTTP/1.1 POST https://cloud.sdu.dk/auth/refresh
Authorization: Bearer $refreshToken
```

The `accessToken` is passed similarly:

```
HTTP/1.1 POST https://cloud.sdu.dk/some/call
Authorization: Bearer $accessToken
```

### Internals of `accessToken`s

In this section we describe the internals of the `accessToken` and how to verify an `accessToken` from UCloud. It is
important that all providers authenticate _every_ request they receive.

The payload of an `accessToken` is follows the same schema for both UCloud and providers.

```
{
  // Token properties
  "iat": 1234,
  "exp": 5678,
  "iss": "cloud.sdu.dk",
   
  // Authorization properties
  "role": "<ROLE>", // "SERVICE" if the token authenticates UCloud. "PROVIDER" if the token authenticates a provider.

  // User metadata
  "sub": "<USERNAME>" // A unique identifier for the provider or "_UCloud" if the token authenticates UCloud.
}
```

All JWTs signed by UCloud will use the `RS256` algorithm, internally this uses `RSASSA-PKCS1-v1_5` with `SHA-256` used
for the signature. UCloud uses a unique private & public keypair for every provider. The provider receives UCloud's
public key when the `refreshToken` of the provider is issued. UCloud will generate a new keypair if the provider's
`refreshToken` is revoked.

### Verifying `accessToken`s

As a provider, you must take the following steps to verify the authenticity of an `accessToken`:

1. Verify that the `accessToken` is signed with the `RS256` algorithm (`alg` field of the JWT header)
2. Verify that the `sub` field is equal to `"_UCloud"` (Note the '\_' prefix)
3. Verify that the `iat` (issued at) field is valid by comparing to the current time
   (See [RFC7519 Section 4.1.6](https://tools.ietf.org/html/rfc7519#section-4.1.6))
3. Verify that the `exp` (expires at) field is valid by comparing to the current time
   (See [RFC7519 Section 4.1.4](https://tools.ietf.org/html/rfc7519#section-4.1.4))
4. Verify that the `iss` (issuer) field is equal to `"cloud.sdu.dk"`
5. Verify that the `role` field is equal to `SERVICE`

It is absolutely critical that JWT verification is configured correctly. For example, some JWT verifiers are known for
having too relaxed defaults, which in the worst case will skip all verification. It is important that the verifier is
configured to _only_ accept the parameters mentioned above.

## Extending the Protocol to Support Verification of Client

---

__📝 NOTE:__ Very informal draft.

---

Some quick thoughts about how we can extend the protocol to support verification of the client sending the message. This
is extremely useful as it would make UCloud incapable of impersonating a user at a different provider.

1. Extend client-side RPC to include a signature of their message
   - This message should use public-private keypairs
   - Keypairs are generated in the browser
   - The public key is transferred to the provider _by_ the user
   - This will happen when the user connects to the provider
   - Possible library: https://github.com/kjur/jsrsasign
   - This will support keygen and signature we need
   - The signature should be passed in a header
2. UCloud receives this message, and extends it with additional information
3. UCloud includes, verbatim, the original request along with the signature
   - How do we make this developer friendly?
4. Provider verifies that the JWT is valid (by UCloud)   
5. Provider verifies that the original request has been signed
5. Provider verifies that the original request _also_ matches the additional context that UCloud provided

A possible alternative to signing the entire message is to sign a JWT (or any other document, we don't really need the
header). This JWT would provide similar security, the document should contain:

- `iat`: Issued at
- `exp`: Expires at
- `sub`: Username (UCloud)
- `project`: Project (UCloud)
- `callName`: Potentially, this could contain the name of the call we are performing. This would allow the provider to
   verify that the correct call is also being made.


## Recommended Reading

- [Introduction to the Provider API](./provider_api.md)