# End-to-End verification of communication

This design document covers the feature tracked in [UCloud#3367](https://github.com/sdu-escience/ucloud/issues/3367).

## Status

| Component (*) | Description                                                     | Issue                                                       | Status (+) |
|---------------|-----------------------------------------------------------------|-------------------------------------------------------------|------------|
| FE            | Create a call name lookup table                                 | [#3491](https://github.com/sdu-escience/ucloud/issues/3491) | Done       |
| IM            | Create a call name lookup table (user API to provider)          | [#3492](https://github.com/sdu-escience/ucloud/issues/3492) | Done       |
| BE            | Update connection API to allow for optional signing             | [#3493](https://github.com/sdu-escience/ucloud/issues/3493) | Done       |
| BE            | Update provider API to expose information about signing         | [#3494](https://github.com/sdu-escience/ucloud/issues/3494) | Done       |
| FE            | Introduce a JWS (signing) library into the frontend             | [#3495](https://github.com/sdu-escience/ucloud/issues/3495) | Done       |
| IMS           | Expand the connection procedure to save a public key            | [#3496](https://github.com/sdu-escience/ucloud/issues/3496) | Done       |
| FE            | Update connection dashboard to be active when no key is present | [#3497](https://github.com/sdu-escience/ucloud/issues/3497) | Done       |
| FE            | Introduce message signing                                       | [#3498](https://github.com/sdu-escience/ucloud/issues/3498) | Done       |
| BE            | Forward RPC signature to providers                              | [#3499](https://github.com/sdu-escience/ucloud/issues/3499) | Done       |
| IMU           | Introduce E2E verification procedure                            | [#3500](https://github.com/sdu-escience/ucloud/issues/3500) | Done       |
| FE            | Introduce key invalidation in response to 482 status            | [#3501](https://github.com/sdu-escience/ucloud/issues/3501) | Backlog    |

(*): __FE__ = Frontend. __BE__ = Backend (Core). __IM__ = Integration module. __IMU__ = Integration module (User mode).
__IMS__ = Integration module (Server mode).

(+): See issue for up-to-date status

## Motivation

UCloud, as an orchestrator of resources, has quite a lot of power over the providers that integrates with it. End-user
requests are typically received, authenticated and authorized by UCloud/Core. The core then proxies this
request to the service provider. In the proxy the Core only authenticates itself and not the end-user. The service
provider is simply meant to trust that the Core did its job correctly while authenticating the message. This has
traditionally not been a problem since UCloud didn't have many providers and were all mostly managed by the same
organizations.

However, as the number of potential providers has been growing, so has a concern that UCloud/Core would become
compromised and would start acting on the behalf of other users on a provider's system. Such an attack would essentially
give the provider's no additional lines of defense as they would be authenticated (by UCloud) as a real user of the
system.

This feature attempts to mitigate such an attack by allowing providers to independently verify that user requests are
coming from the user.

## Proposed solution

The proposed solution involves extending the RPC protocol between the end-user and UCloud/Core. This will allow the
end-user to optionally add a digital signature of their request. If such a signature is received by the Core, then it
must be proxied as is to the responsible provider.

The digital signature must be created in such a way that the Core cannot use the signature to forge requests on behalf
of the end-user. This must be done while still giving the Core the flexibility required to do its job. For example, the
Core should still be allowed to augment a user request with additional information. Commonly, this occurs when a
resource is being created/modified where the original request is extended to include information about the resource
itself.

For the sake of adoption, it is absolutely crucial that UCloud helps facilitate the creation and exchange of keys
required for such a digital signature. However, the Core must _never_ see any of the keys involved in this process.

### Digital signature

For the sake of making the implementation easy, it has been decided to use JWSs for digital signatures. This has been
chosen since all components involved in UCloud are already capable of handling JWSs as they are a requirement for using
any kind of RPC with UCloud. We also believe that the public-key cryptography algorithms used in JWSs provide a strong
enough security to serve this purpose.

Once keys have been exchanged, then the private key must be securely stored in the frontend of UCloud. The frontend will
then become responsible for creating a JWSs accompanying every RPC made towards UCloud/Core. The JWSs will use the RS512
algorithm. The payload of the JWSs must use the following format:

```kotlin
data class IntentToCall(
    // Full name of the RPC call invoked (NOTE: this is the end-user API not the provider API)
    val call: String,

    // Unix timestamps in milliseconds
    val iat: Long, // When the client believes the request was issued
    val exp: Long, // When the client believes the request should expire

    val username: String, // The username that the client believes they have
    val project: String?, // The project that the client believes they are issuing this request in
)
```

The UCloud frontend must add this in the `UCloud-Signed-Intent` header. Similarly, the Core must pass it in the same
header to the provider.

Integrations supporting this signature should validate as much information as possible. If any information appears to be
wrong, then the request must be rejected and logged appropriately. The request should be rejected with the
non-standard HTTP status code of `482`. This will allow the UCloud frontend to display an appropriate error message.
Providers cannot assume that such a rejection means UCloud is acting maliciously since the signature and payload is
fully controlled by the end-user. The table below summarizes how to validate each field of the signature.

| Signature field | UCloud/Core field                                       | Notes                               |
|-----------------|---------------------------------------------------------|-------------------------------------|
| `call`          | Mapping received RPC to the corresponding user API      |                                     |
| `iat` and `exp` | The current timestamp based on the provider's own clock | See the section below for concerns. |
| `username`      | The `UCloud-Username` header passed from UCloud         |                                     |
| `project`       | The resource sent by UCloud/Core (if any)               |                                     |

### Exchanging keys

#### Approach 1: Key generation in UCloud frontend exchange during connection

In this approach, the UCloud frontend will generate both a private and public key just before initiating a connection.
The public key is then transferred to the provider during the connection procedure.

For the sake of simplicity, we assume that the same key is shared amongst all providers for a user. We need to do this
because the frontend rarely knows which provider it is speaking to and changing this would be a fairly significant
change.

1. Frontend loads. The frontend immediately checks if a key-pair has already been generated. This key is saved to
   `localStorage`.
2. User clicks on "Connect"
3. UCloud/Core queries the provider for a redirect URL and forwards it to the frontend
4. The frontend `POST`s the public key to this redirect URL
5. The provider remembers this public key until the end of the connection process
6. Normal connection procedure takes place
7. If successful, the provider saves the public key and connects it to the end-user
7. The provider notifies UCloud/Core about a successful connection

#### Approach 2: WebAuthn + Approach 1

This approach is similar to Approach 1, except that it uses the WebAuthn API to sign messages instead of a
private key stored in `localStorage`. This increases the security by moving the keys out of easily accessible storage.
Under this scenario neither UCloud/Core or the UCloud frontend would have access to the private key involved.

This approach would not use JWSs directly, but rather mimic the implementation of JWSs. In this scenario, the client
would send two headers. The first header would contain the payload (`IntentToCall`) and the second header would
contain a signed version of the payload. This would likely be implemented by taking a cryptographic hash of the payload
and then encrypting it with the private key (i.e. the challenge of WebAuthn would be the hash). Validation routines are,
again, similar to JWSs. The provider would compute the same hash of the payload, decrypt the signature using the public
key and compare the two hashes.

The procedure would work as follows:

1. Frontend loads. The frontend immediately checks if a key-pair has already been generated. If not, the user is
   prompted to create one, requiring user presence.
2. Public keys are pushed to providers as described in Approach 1.

This approach is significantly better than Approach 1 since the private keys are not accessible by either
the Core or the frontend. Assuming no vulnerabilities in the WebAuthn implementation, this eliminates the possibility
of a malicious client stealing private keys.

Note that we cannot generate the key on the provider's origin, since the key signing must be performed in the UCloud
frontend.

Unfortunately, WebAuthn appears to be a realistic option due to browser and hardware support. At the moment, it appears
that many hardware and browser combinations simply require a physical hardware token. This is fine for optional security
which goes beyond the basics, but in many cases this feature would be seen as mandatory security and not optional. As
a result, we end up with basically gating users from using our software for no good reason. Approach 1 is not
perfect, but they would limit the amount of damage a full UCloud compromise could result in.

## Concerns and considerations

### Storing keys in the UCloud frontend

Storing keys in the UCloud frontend has some obvious concerns involved with it. As described in the proposals above,
this can lead to situations where a malicious UCloud frontend could start harvesting and stealing keys. As a result, a
scenario where the frontend is unable to actually read the private key would be ideal. Such a solution exists, WebAuthn.
Unfortunately, as described in Approach 2 this is not readily available for most end-users. As a result, this document
proposes that we consider it an acceptable risk that we are storing keys in `localStorage`.

To mitigate the risk of keys being stolen, a relatively short TTL of the public keys are crucial. We recommend keeping
this balanced with the inconvenience caused by having to reconnect and reconfigure the keys.

### Replay attacks and UCloud/Core maliciously changing real requests

The `IntentToCall` is purposefully loose in the scope in which it acts. This allows UCloud/Core perform the required
modifications to the request payload. Unfortunately, this also means that UCloud/Core could easily replay or even
maliciously change requests (as long as it stays within the scope).

This issue is mainly mitigated by having a short TTL on the `IntentToCall`. Further mitigations can be implemented by
limiting the scope of the RPC even further. This is currently not considered a priority, due to time it would take to
implement. Examples could include limiting a file browsing request to a specific path.

### Time synchronization between end-user and provider

Relying on client time means that we have the risk of client time and end-user time is out-of-sync. This is not a new
thing, since we already rely on client timing for 2FA. For the most part, we must simply deal with invalid keys in a
good way (see below).

### Key invalidation

Given that keys are never known by UCloud/Core, it makes sense that UCloud/Core cannot initiate a key invalidation
event. Thus, it must be provider specific how to invalidate a key. The integration module could create a CLI for this
which is invokable by the end-user.

The user experience of key invalidation is likely not going to be very good. It seems that for the most part we must
rely only on the expiration of keys.

### Creating keys for every device

Given that keys are stored directly in the client, in becomes a requirement that users create a key for every device.
This becomes problematic, since key exchange is tied directly to the connection process. At a minimum, this means that
the frontend must prompt the user to connect if no local key has been exchanged with a given provider.

Furthermore, this highlights the importance of having a good UI for prompting connections from the user. A card on the
dashboard is probably not invasive enough.

### Dealing with invalid keys in the frontend

The frontend will know about failures related to an invalid key because of the HTTP status code. It is crucial that the
Core correctly forwards this status code. Additionally, when a frontend is presented with such a key, it should
immediately invalidate its own key and display an appropriate prompt for renewing the key.
