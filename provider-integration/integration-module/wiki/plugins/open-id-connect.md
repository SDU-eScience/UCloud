# `OpenIdConnectPlugin`

---

__🗒 NOTE:__Very informal configuration. This is meant mostly to serve as a reference for later. The reason we are not
writing more complete documentation right now is the lack of a proper configuration system.

---

The OIDC plugin allows the integration module to use any OIDC provider for handling connections. The OIDC provider is
used for providing authentication. A mapping is made to a user on the local system through an extension. The extension
is developed entirely by the provider. As a result, the provider can choose to automatically create a user in response
to successful authentication. An example extension for this is provided in `./example-extensions/oidc-extension`. This
script is created in Python and automatically creates a user if one doesn't already exist.

## Configuring Keycloak

The plugin, so far, has been tested exclusively with [Keycloak](https://www.keycloak.org/). Keycloak is an identity and
access management system. It allows you to aggregate multiple identity-providers into a single system which is then
exposed to UIM through OpenID Connect.

UCloud ships with Keycloak as part of the `launcher` script. You simply have to select the appropriate menu entry when
launching the rest of UCloud. After starting the `launcher` you should be able to access Keycloak at
http://localhost:61241/admin/master/console. You should be able to login with the credentials `admin`/`admin`.

To configure UIM to use this plugin, you must supply some configuration specific to Keycloak. The relevant options are:

```
val certificate: String,
val authEndpoint: String,
val tokenEndpoint: String,
val clientId: String,
val clientSecret: String,
```

To begin with, you must create a client. You do this by selecting "Clients" from the left sidebar.

Click "Create"

Enter an ID, this will correspond to the `clientId` property. The "Client Protocol" should be set to `openid-connect`.
Click "Save".

Under "Access Type" set it to "confidential".

Add `http://integration-module:8889/connection/oidc-cb` in the "Valid Redirect URLs" section.

Click "Save" at the bottom of the screen.

You should now be able to copy the `clientSecret` by using the `Credentials` tab which appears after saving.

You should set `authEndpoint` to `http://keycloak:8080/realms/master/protocol/openid-connect/auth`.

You should set `tokenEndpoint` to `http://keycloak:8080/realms/master/protocol/openid-connect/token`.

You can retrieve the certificate by going to http://localhost:61241/realms/master/protocol/openid-connect/certs. This
will return a number of keys encoded as JWK. You should copy the JSON object which has `"alg": "RS256"`, which is the
key that will be used to sign responses. You will need to convert this key from JWK to PEM (PKCS#8), for example using 
https://keytool.online/. The `certificate` should be set to the PEM version of the key.
