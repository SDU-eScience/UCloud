# Authentication and Authorization in SDUCloud

## Authenticating with SDUCloud

SDUCloud provides various backends for authentication. These are all
implemented in the authentication service. As of 09/05/19 the following
backends are supported:

- Authentication with username/password
- Authentication via WAYF

Below we will be covering the technical details of each backend.

All authentication information for a user is stored in a PostgreSQL table and
is managed only by the authentication service. We will cover the concrete
data stored later. A column in the table is used for differentiating which
authentication backend is used.

WAYF is considered the primary authentication backend.

### Password

Users can login with a simple username/password combination. Only
administrators of the system can create new users. These are created in the
"Admin" panel of the SDUCloud web interface.

We allow users to change their password through the web interface (by going
to "Settings" in the user menu). Users are required to provide their current
password in order to change to a new one.

We do not provide any automatic way of recovering a password authenticated
user.

Currently no password policy is implemented.

Login attempts are logged through normal
[auditing](../service-common/wiki/auditing.md). We do not currently limit
number of incorrect login attempts.

Passwords are stored following recommendations by
[OWASP](https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/Password_Storage_Cheat_Sheet.md).
Password hashing is provided via OpenJDK8. Specifically we use
`PBKDF2WithHmacSHA512` with the following parameters:

- Salt length: 16 bytes (Generated via `SecureRandom`)
- Iterations: 10000
- Key length: 256

### WAYF

[WAYF](https://wayf.dk) (Where Are You From) is a danish identity federation
for research and infrastructure. WAYF provides authentication via the local
organization (e.g. SDU). At a technical level this is implemented with SAML.

SDUCloud implements the SAML integration by using Onelogin's library for SAML
(`com.onelogin:java-saml-core`, see the `build.gradle` of `auth-service` for
the specific version). Minor modifications to the library were made to
support a different webserver (ktor). These changes are implemented in
`dk.sdu.cloud.auth.services.saml.SamlRequestProcessor`.

The SAML library is configured according to [WAYF's own recommendations](https://wayf.dk/da/s%C3%A5dan-f%C3%A5r-du-din-webtjeneste-p%C3%A5-wayf).

## Two Factor Authentication (2FA)

SDUCloud, for the most part, relies on the user's organization to enforce
best practices. SDUCloud can be configured to require additional factors of
authentication via WAYF. On top of this SDUCloud allows you to optionally add
TOTP based two-factor authentication.

## Sessions and Tokens

Once a user has been successfully authenticated with SDUCloud a number of
tokens are issued for the user. These tokens are used during the
authentication workflow.

The table below summarizes the tokens and where they are stored when the web
interface is being used.

| Name           | Token Type            | Storage Type     | Purpose                                               |
| -------------- | --------------------- | ---------------- | ----------------------------------------------------- |
| `accessToken`  | [JWT](https://jwt.io) | Local Storage    | Authenticate API calls                                |
| `refreshToken` | Opaque                | HTTP Only Cookie | Used for creating new `accessToken`s                  |
| `csrfToken`    | Opaque                | Local Storage    | Used in combination with `refreshToken` to avoid CSRF |

The `accessToken` is used to authenticate all API calls. It is passed as a
bearer token in the `Authorization` header. The token contains a JSON web
token. These are tokens which contain a JSON encoded payload and are signed
by the authority issuing them. The JWTs in SDUCloud are signed with the
`SHA256withRSA` algorithm. Each microservice can verify a JWT (without
contacting a central server) by using the authentication's service public
certificate and additionally verifying that the issuer is set to
`cloud.sdu.dk`.

We store the `accessToken` in [local
storage](https://developer.mozilla.org/en-US/docs/Web/API/Web_Storage_API/Local_storage).
This is a technical requirement given that our JavaScript code must be able
to attach the token to each request in the `Authorization` header. However,
storing secret tokens, such as the `accessToken`, in local storage can be
problematic since it can easily be stolen by malicious JavaScript. This could
happen in case of a successful XSS attack. To minimize the impact of a
successful attack we ensure that the JWTs are relatively short lived (10
minutes). We do not implement a JWT blacklist and rely only on the short
expiry time of the JWTs.

Given that JWTs expire after 10 minutes we need a different mechanism for
keeping the user logged in. For this we use the `refreshToken`. The refresh
token is an opaque token which can be used to generate a new `accessToken`. A
user can keep using the same refresh token for creating new access tokens
until the refresh token is invalidated. A refresh token will be invalidated
once the user logs out.

In order to protect against XSS attacks the `refreshToken` is stored as a
cookie with the following flags:

- `HttpOnly`
- `SameSite Strict`
- `Secure`
- Expires after 30 days

When refreshing the token via a cookie the `csrfToken` must be passed in the
`X-CSRFToken` header. If the CSRF token is not present or does not match the
server's records the token will not be refreshed.

## JWT Payload

The following is an example of a JWT payload. This payload is decodable by
anyone who has a JWT. As a result it is crucial that we do not store
sensitive data in it.

```javascript
{
  // Token properties
  "iat": 1234,
  "exp": 5678,
  "iss": "cloud.sdu.dk",

  // Authorization properties
  "role": "ADMIN",
  "aud": ["all:write"],

  // Extension metadata
  "extendedByChain": [],

  // Session reference
  "publicSessionReference": "ref",

  // User metadata
  "sub": "user1",
  "principalType": "password",
  "firstNames": "User",
  "lastName": "User",
  "uid": 10
}
```

### Token Properties

These properties are about the token itself and are used as part of the verification process.

| Property | Description                                                                                    |
| -------- | ---------------------------------------------------------------------------------------------- |
| `iat`    | Unix timestamp indicating when the token was issued at                                         |
| `exp`    | Unix timestamp indicating when the token will expire                                           |
| `iss`    | The issuer of this token. For SDUCloud this should always be `cloud.sdu.dk`                    |
| `jti`    | A unique ID of this token. If this field is present then this JWT is used as a one-time token. |

#### One-Time Tokens

All users are allowed to use their normal JWT to create a one-time token.
This one-time token is designed to be used only once and we keep a list of
already claimed JWTs to ensure this. A one-time token is created for a
specific security scope and this scope must be covered by the JWT requesting
it. The JWT created will only last for thirty seconds.

These tokens are useful in situations where we need to pass tokens that
otherwise could be dangerous. In SDUCloud this is, for example, used for
handling downloads. Restrictions in how browsers work, mean that we cannot
pass the token through the header and we must pass in through either cookies
or the URL. In this instance we chose to pass it in the URL but using a
one-time token to mitigate the dangers of doing this.

### Authorization in SDUCloud

These properties are related to the authorization mechanisms of SDUCloud.
This is tightly related to how the RPC layer is defined. This is described
further in [Auditing](../service-common/wiki/auditing.md) and [Writing Service
Interface](../service-common/wiki/writing_service_interfaces.md).

| Property | Description                                             |
| -------- | ------------------------------------------------------- |
| `role`   | The role of this user. See table and description below. |
| `aud`    | A list of security scopes. See below.                   |

#### Roles

SDUCloud contains a few basic roles used for global authorization. These
attributes only refer to the global authorization, more fine-grained
authorization is done at the service level. A microservice can declare that a
single API endpoint should only be accessible to users with a given role.

The table below shows the global roles in SDUCloud:

| Role      | Description                                                           |
| --------- | --------------------------------------------------------------------- |
| `USER`    | A 'normal' end-user                                                   |
| `ADMIN`   | An administrator of system. Has access to certain privileged actions. |
| `SERVICE` | An internal (micro)service. Has access to certain privileged actions. |

#### Security Scopes

A security scope in SDUCloud puts a limit on which calls a JWT can be used
for. In order to understand security scopes in SDUCloud we must first
understand some of the metadata associated with API endspoints. All API
endpoints in SDUCloud have metadata associated with them, the table below
summarizes the important metadata:

| Property      | Description                                                                                                                                           |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `namespace`   | Each endpoint belongs to a collection. We refer to the collection's name as the namespace. Namespaces can be hierarchical and are separated by a `.`. |
| `name`        | A unique name for the API endpoint within it's collection.                                                                                            |
| `accessRight` | `read` if the operation is purely read otherwise `write`.                                                                                             |

A security scope is a string combining these three properties in the
following format: `${path}:${accessRight}:${metadata}`. The `path` can be
some part of the namespace or it can refer to just a specific call by
combining the namespace with the name. The security scope grants the
`accessRight` for all calls that are children of the `path`. A special
namespace exists called `all` which can be used for any and all namespaces.

Below are some examples:

| Scope                        | Grants                                                                 |
| ---------------------------- | ---------------------------------------------------------------------- |
| `all:write`                  | Grants read/write access to all endpoints.                             |
| `files:read`                 | Grants read only access to all endpoints within the `files` namespace  |
| `files:write`                | Grants read/write access to all endpoints within the `files` namespace |
| `files.listAtDirectory:read` | Grants read access to the `files.listAtDirectory` endpoint             |
| `a.b.c.d.e:read`             | Grants read access to the hierarchy at `a.b.c.d.e`                     |

The `metadata` section of the security scope is optional. It encodes a
dictionary. Entries are separated by a `,` and each entry is encoded as:
`${base64encode(key)}!${base64encode(value)}`. The `metadata` section can be
used to store call specific metadata. This could for example be used to
create an `accessToken` which can only be used to read files from a specific
directory.

The default scope for tokens created by the web interface is `all:write`.
Security scopes are an important part of token extension.

### Token Extension

Token extension is a mechanism used for when a service needs to perform
actions on behalf of a user that cannot be performed immediately. For
example, an application service might need to upload files back into the
system after a long computation. The service cannot use the user's JWT since
that will have expired by the time the computation is done.

Services are allowed to extend a user JWT for a set of security scopes. The
extended token will be a tuple of `accessToken` and optionally a
`refreshToken`. The `accessToken` (either returned directly or created later)
will only be valid for the given security scope.

The authentication service contains a whitelist of security scopes each
service is allowed to ask for. Any request not covered by this whitelist will
be rejected. This minimizes the dangers of a single service being compromised.

The service that extended the token is added to `extendedByChain`. This allows
us to track requests performed by a service on behalf of a user.

### Session References

A session reference is an opaque token which has a 1:1 mapping with a
`refreshToken`. This allows us, in combination with auditing information, to
determine from which request a JWT was minted. The session reference is not
considered secret and does not allow a user to create new JWTs.

### User Metadata

| Property        | Description                                |
| --------------- | ------------------------------------------ |
| `sub`           | The username of this user.                 |
| `principalType` | The type of users. (wayf/password/service) |
| `firstNames`    | First name(s).                             |
| `lastName`      | Last name.                                 |
| `uid`           | Unique user ID                             |