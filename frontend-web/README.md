:orphan:

# Overview

UCloud uses a web application as frontend for its users.

This repository contains the frontend components used in UCloud, logic for contacting backend services and a test suite.

## Code Structure

Of notable npm-packages, the frontend uses the `React`-frameworks, along with the `Redux` library for managing some state<sup>\*</sup>. Additionally, the application uses the `React-Router` for navigation. The project is written in `TypeScript`.

Each category (category meaning e.g. Files, Applications, Dashboard, Activity), groups components by their association, i.e. every component referring to a category, will be found in the corresponding folder. Additionally, if a component has a reducer, it will be placed in a folder named `Redux`, along with the associated reducer actions, using the naming convention `<ComponentName>Reducer` and `<ComponentName>Actions`.

Data is retrieved from the backend by contacting the corresponding backend-services in charge of the data.

<sup>\*</sup> Some components use local state, if the contents are obsolete the moment the component is unmounted, e.g. forms.

## Application Architecture

.. figure:: /frontend-web/webclient/wiki/FrontEndDiagram.png
   :width: 70%
   :align: center

**Figure**: The application will on startup instantiate the Redux-store, which is then used for every connected component. Every component, that does not solely rely on local state, connects to the store and gets its state from there. When a component is mounted, updated or a user interaction happens, the current component can contact the backend using the CloudObject instance. When the backend responds with data, an action is created and sent to the reducer. A new state is then derived, and sent to the component, providing the component with its new state.

## Running

To run the project run the following commands, in the directory `./webclient/`:

- `npm install`
- `npm run start`

When the terminal outputs a list of entries, including `Local` and `Network`, the project is available at `http://localhost:9mast000/app`.

`npm install` will only be necessary to run on subsequent runs, if the package.json file has been updated since the last
time.

## Additional npm Commands

Additional npm commands are:

- `npm run clean` will delete the contents of the `./webclient/dist/` (if present).
- `npm run prepare-icons` will generate React components for icons defined in `./webclient/app/ui-components/components/`

## Testing

The front-end contains a test suite, implemented using Jest.

To run the test suite, use the command: `npm run test`.

The test files is located in the [tests](frontend-web/webclient/__tests__/README.html) folder.

## Security

Logging in to the site is done through Wayf on the production version, or with username/password combination on the development version, both as described in [auth-service](auth-service.html#authenticating-with-sducloud).

The [Cloud object](#ucloud-object) will validate the every new JWT-token received from the backend when refreshing. This is done throught the structure of the JWT, not the actual contents of the JWT.

On invalid token, the site will redirect to the login screen.

On token expiration, the frontend will try to refresh the token. Failing that, the currently held tokens are cleared, and the user is redirected to the login page.

### Roles

A logged in user can either be a `USER` or an `ADMIN`. The `USER` role only has access to a subset of the available sidebar options that the `ADMIN` role has, e.g. User Creation.

## Notable custom code

### UCloud object

The project utilizes JSON Web Tokens, which contain information regarding the (if any) currently logged in user.

To abstract away from this when contacting the backend, the codebase includes the `UCloud`-object, that has an instance exported for use, which contains the relevant HTTP operations (e.g. GET, PUT, POST, DELETE).

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

Websockets are also supported. This is used for both the Task system (TODO add link) and Notifications (TODO: Add link). It uses the same authentication system as for common HTTP-requests.

### Styling

Most of the styling is written using the Unstyled-functions `./frontend-web/webclient/app/Unstyled/index.ts/`. This is is written as normal CSS in strings, and is injected at runtime. The full API for `Unstyled` is:

#### `injectStyleSimple(title: string, fn: (k: string) => string)): string`:

The most commonly used. `title` is the classname, which is appended and with a unique number id, and passed as the argument for `fn`. E.g.
```
const nameOfClass = injectStyle("className", k => `
    ${k} {
        width: auto;
    }
`);
```

The function returns the name of the class, with no dot prefix.

- `injectStyleSimple(title: string, css: string): string`:

This is a short-hand for the above one. This wraps the content inside the name of the class and curly braces, so this is equivalent to the above example.
```
const nameOfClass = injectStyleSimple("className", `
    width: auto;
`);
```

The [&-operator](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_nesting/Using_CSS_nesting) could in theory be used in both, but is not in use, due to relatively low support as of this writing (<90%) and it being a working draft.

Overriding CSS-variables in the components is done by setting them in the `style` object property.

### Custom stores

Instead of Redux, some components instead utilize an external store that's connected to the component using the `useSyncExternalStore`-hook. This allows the programmer more flexibility for fetching and emitting updates, without having to use middleware libraries for Redux for async operations, for instance. Examples of this are the [TaskStore](webclient/app/Services/BackgroundTasks/BackgroundTask.tsx) and [Notifications](webclient/app/Notifications/index.tsx).

### AsyncCache store

The AsyncCache is used for caching stuff like drives/collections to be used between non-React focused components like the [ResourceBrowser-component](#Resource-Browser-component) (see below). They can be invalidated and stores resources in records, usually based on the `id` as key, e.g. using the folder `id`/`path` as key, will return the contents of the folder. 

The AsyncCache allows for fetching resources from the backend to the cache, retrieving from the cache locally and invalidating the cache. 
Invalidating of the cache is done on page-reload and can be done by the programmer.

The life-time of the AsyncCache is intended to be longer than the components using it, similar to the contents of the Redux-store.

### Resource Browser-component

The ResourceBrowser-component is built for speed and consistent interaction possibilites like keyboard-input and context menus on mouse right-clicks. It is used to render lists, e.g. Files, Drives, Project Members, Public Links, etc.

The ResourceBrowser-component handles the keyboard input, and mouse input for a consistent experience between different instances of the ResourceBrowser. It is written as a Javascript class, and must be instantiated in a React-component, that handles initialization in a `useLayoutEffect` hook. (TODO: Required handlers for implementing ResourceBrowser using component).

The ResourceBrowser-component works by creating elements using the DOM-api and modifying the contents. Each single representation of a resource is rendered in a `row` that contains:

- `star`: If any favoriting mechanism exists for the resource instance in question, this 
- `icon`: The icon to be rendered on the left-most side.
- `title`: The title contents to be rendered immediately to the right of the icon. This is commonly the resource name, e.g. file name for files, IP for public IP, etc.
- `stat1`, `stat2`, `stat3`: Fields with meta data on the resource. For a file it can be `modified at`, `sensitivity` or similar. How many `stats` are shown depends on the ResourceBrowser component props. When the modal is used as a selector (e.g. "Move to..."-operation), usually the content of `stat3` is replaced with the button to perform the selection.

The ResourceBrowser components supports navigating resources by typing the name, arrow keys, enter, among others. A context menu showing resource instance operations is shown on right-click.

Operations applicable to a resource instance will be presented to the user when one or more is selected. These operations are usually provided by the corresponding resource API. They will be presented both above the list-view, and also be presented on right-clicking on of the selected elements. Some operations, like creation, will be shown when no element is selected.

