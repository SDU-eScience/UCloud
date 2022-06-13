# End-to-End Verification of Communication

This design document covers the feature tracked in [UCloud#3367](https://github.com/sdu-escience/ucloud/issues/3367).

## Status

| Component | Description | Issue | Status |
|-----------|-------------|-------|--------|
| -         | -           | -     | -      |

## Motivation

UCloud, as an orchestrator of resources, has quite a lot of power over the providers that integrates with it. End-user
requests are typically received, authenticated and authorized by UCloud/Core. The core then proxies this
request to the service provider. In the proxy the Core only authenticates itself and not the end-user. The service
provider is simply meant to trust that the Core did its job correctly while authenticating the message. This has
traditionally not been a problem since UCloud didn't have many providers and were all mostly managed by the same
organizations.

However, as the number of potential providers has been growing, so has a concern that UCloud/Core would become
compromised and would start acting on the behalf of other users on a
provider's system. Such an attack would essentially give the provider's no additional lines of defense as they would be
authenticated (by UCloud) as a real user of the system.

This feature attempts to mitigate such an attack by allowing providers to independently verify that user requests are
coming from the user.

## Proposed Solution

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

### Digital Signature

For the sake of making the implementation easy, it has been decided to use JWTs for digital signatures. This has been
chosen since all components involved in UCloud are already capable of handling JWTs as they are a requirement for using
any kind of RPC with UCloud. We also believe that the public-key cryptography algorithms used in JWTs provide a strong
enough security to serve this purpose.

Once keys have been exchanged, then the private key must be securely stored in the frontend of UCloud. The frontend will
then become responsible for creating a JWT accompanying every RPC made towards UCloud/Core. The JWTs will use the RS512
algorithm. The payload of the JWT must use the following format:

```kotlin
data class RpcManifest(
    // Full name of the RPC call invoked (NOTE: this is the end-user API not the provider API)
    val call: String,

    // Unix timestamps in milliseconds
    val iat: Long, // When the client believes the request was issued
    val exp: Long, // When the client believes the request should expire

    val username: String, // The username that the client believes they have
    val project: String?, // The project that the client believes they are issuing this request in
)
```

The UCloud frontend must add this in the `UCloud-RPC-Signature` header. Similarly, the Core must pass it in the same
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

### Exchanging Keys

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

This approach would not use JWTs directly, but rather mimic the implementation of JWTs. In this scenario, the client
would send two headers. The first header would contain the payload (`RpcManifest`) and the second header would
contain a signed version of the payload. This would likely be implemented by taking a cryptographic hash of the payload
and then encrypting it with the private key (i.e. the challenge of WebAuthn would be the hash). Validation routines are,
again, similar to JWTs. The provider would compute the same hash of the payload, decrypt the signature using the public
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

## Concerns and Considerations

### Storing Keys in the UCloud Frontend

Storing keys in the UCloud frontend has some obvious concerns involved with it. As described in the proposals above,
this can lead to situations where a malicious UCloud frontend could start harvesting and stealing keys. As a result, a
scenario where the frontend is unable to actually read the private key would be ideal. Such a solution exists, WebAuthn.
Unfortunately, as described in Approach 2 this is not readily available for most end-users. As a result, this document
proposes that we consider it an acceptable risk that we are storing keys in `localStorage`.

To mitigate the risk of keys being stolen, a relatively short TTL of the public keys are crucial. We recommend keeping
this balanced with the inconvenience caused by having to reconnect and reconfigure the keys.

### Replay Attacks and UCloud/Core Maliciously Changing Real Requests

The `RpcManifest` is purposefully loose in the scope in which it acts. This allows UCloud/Core perform the required
modifications to the request payload. Unfortunately, this also means that UCloud/Core could easily replay or even
maliciously change requests (as long as it stays within the scope).

This issue is mainly mitigated by having a short TTL on the `RpcManifest`. Further mitigations can be implemented by
limiting the scope of the RPC even further. This is currently not considered a priority, due to time it would take to
implement. Examples could include limiting a file browsing request to a specific path.

### Time Synchronization between End-user and Provider
