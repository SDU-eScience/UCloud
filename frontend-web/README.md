# SDUCloud Frontend

The frontend for SDUCloud is in this repository. See [webclient](./webclient)
for the actual code.

Contains the code for the frontend components used at [SDUCloud](https://cloud.sdu.dk/), logic for contacting backend services and a test suite.

## Running

To run the project run the following commands, in the directory `./webclient/`:

- `npm install`
- `npm run start`

When the terminal outputs `Compiled successfully.`, the project is available at `http://localhost:9000/app`.

`npm install` will only be necessary to run on subsequent runs, if the package.json file has been updated since the last
time.

## Additional npm Commands

Additional npm commands are:

- `npm run clean` will delete the contents of the `./webclient/dist/` (if present).
- `npm run prepare-icons` will generate React components for icons defined in `./webclient/app/ui-components/components/`

## Testing

The front-end contains a test suite, implemented using Jest.

To run the test suite, use the command: `npm run test`.

The test files is located in the [tests](./webclient/__tests__/) folder.

## Security

Logging in to the site is done through Wayf on the production version, or with username/password combination on the development version, both as described in [auth-service](../auth-service#authenticating-with-sducloud).

The SDUCloud object (as describe in [SDUCloud](#sducloud)) will validate the every new JWT-token received from the backend when refreshing. This is done throught the structure of the JWT, not the actual contents of the JWT.

On invalid token, the site will redirect to the login screen.

On token expiration, the frontend will try to refresh the token. Failing that, the currently held tokens are cleared, and the user is redirected to the login page.

### Roles

A logged in user can either be a `USER` or an `ADMIN`. The `USER` role only has access to a subset of the available sidebar options that the `ADMIN` role has, e.g. User Creation.

## Notable custom code

### SDUCloud

The project utilizes JSON Web Tokens, which contain information regarding the (if any) currently logged in user.

To abstract away from this when contacting the backend, the codebase includes the `SDUCloud`-object, that has an instance exported for use, which contains the relevant HTTP operations (e.g. GET, PUT, POST, DELETE).

This means contacting a Files-service with a `get`-operation would be done as shown below:

```typescript
try {
    const { request, response } = Cloud.get<File[]>("files-service/operation");
} catch (e) {
    /* Error handling */
}
```

Here, the request in the deconstruction of the object is the XMLHttpRequest made, and response is the result, which must match the type defined in the generic (defaults to `any`).

The `e` in the event of a failing promise, matches the structure of `{ request, response }` but lacks type safety.

Calls made to the backend are prefixed with `api/` followed by the service called, followed by the operation.
