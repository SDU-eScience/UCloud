# UCloud frontend development

## Introduction

<!-- Already covered in introduction.md? -->
The UCloud frontend is the default way of communicating with the UCloud platform for the end user.

The UI acts as a centralised point, that can communicate with different connected providers to make use of the features available, taking in to account what features are not.

Managing of drives, files, runs, and other resources are available to the user through the interface, with the resource's relevant actions.

The implementation is in the folder [`/frontend-web/webclient/`](https://github.com/SDU-eScience/UCloud/tree/master/frontend-web/webclient/).

## Technologies

The UCloud UI is written in Typescript, with the front-end library React. Some use of the library Redux exists in the codebase, but it is rarely used when developing new features.

Where the performance of React has not been adequate, Vanilla TypeScript/ECMAScript has been used instead. An example of this is the ResourceBrowser component, where very little use of React is present.

## Installation

### Running the UI

For a local dev environment, the `launcher`-tool, available in the root folder of the repository, can be run, which will automatically initialize and start the environment.
The UI will be available at `ucloud.localhost.direct` and will use the local environment as the backend. An admin user will be generated with the following login info:

- Username: user
- Password: mypassword

If using another backend is required, the frontend can be run using two commands, while in the `webclient` folder:

First:
- `npm i`

And depending on what backend is needed:
- For `dev` run `npm run start` (requires VPN access)
- For `production` run `npm run start:prod`

Logins for these can be provided upon request.

Some editors will only run type checking on open files and not the entire project. To have the compiler type check the frontend, run `npm run watch`.

## On adding NPM packages

Preferably keep new NPM packages to a minimum as they are something that might need to be updated, increasingly through issues with security.
Whether or not to add a package will be up to the programmer, depending on saving time through offloading the complexity to the package or similar.
If a package is added to `package.json` file, the version must only consist of the version number, fixing it to that version.

This means no `~`, `^`, `>=`, `>`, `<=`, `<`, `x`, `*` or `latest`, as this provides less control over which version of the package will be installed.

## Styling

Styling for the frontend is mostly done with the function `injectStyle`. This function dynamically injects CSS rules to a stylesheet at run-time.

The injected CSS is mostly injected as is, with the exception of the interpolation of the classnames.

### Creating a class with rules:

```typescript
const NameOfClass = injectStyle("class-name", cl => `
    ${cl} {
        background-color: var(--errorMain);
    }

    ${cl}:hover {
        background-color: var(--primaryMain);
    }
`);
```



### Using the class:


```typescript
function Component(): React.ReactNode {
    return <div className={NameOfClass} />
}
```

The function `injectStyleSimple` can also be used if the code doesn't require pseudo-classes, for instance:

```typescript
const NameOfClass = injectStyleSimple("class-name", `
    background-color: red;
`);
```

Using the class is the same as previously described above.

The function appends a number, to ensure uniqueness. The above could for instance return the string `class-name90`.

If just a classname is needed without any rules, see `makeClassName` function.

Relevant code can be found in [/Unstyled/index.ts](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Unstyled/index.ts)

## Network calls

The `callAPI`-function is used for contacting the backend, fetching data and posting updates.

The first parameter `APICallParameter`, is an object that contains HTTP method, optional body, and other relevant arguments for the network call.

Helper functions to create an `APICallParameter`-object exist, that map to different network actions. See `apiCreate`, `apiBrowse`, `apiRetrieve`, `apiSearch`, `apiUpdate`, and `apiDelete`.

Example usage:

```typescript
export function usageReportRetrieve(request: {start: number; end: number;}): APICallParameters<{start: number; end: number;}> {
    return apiRetrieve(request, "/api/usageReport");
}

function Component(): React.ReactNode {
    /* Code */
    
    React.useEffect(() => {
        const result = await callAPI(usageReportRetrieve({start: 0, end: new Date().getTime()}));
        /* Do stuff with result */
    });

    /* Code */
}
```

`callAPI` takes authentication into account. If authentication is not needed for the request, [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API) can be used instead.

Relevant code can be found in [Authentication/DataHook.ts](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Authentication/DataHook.ts)

## HttpClient - lib.ts

A singleton named `Client` is the instantiation of the `HttpClient` that is used to make authenticated calls to the backend. It exposes some additional functionality, like `activeUsername()`, `isLoggedIn()` and `projectId()`, with the latter only to be used when the `useProjectId`-hook can't be used.

Making backend calls should go through `callAPI` or similar, and not be done by using the `call` function directly.

Relevant code can be found in [`/app/Authentication/lib.ts`](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Authentication/lib.ts)

## Adding a route to a component

When creating a new component that needs its own route-path, the approach to follow is in the file `Core.tsx`.

If frontend authentication is required for accessing the component, the component should be wrapped in the function `requireAuth`, like so:

```jsx
<Route 
    path={AppRoutes.dev.pathForAuthenticatedComponent()}
    element={React.createElement(requireAuth(MyNewComponent))} 
/>
```

If authentication is not needed, this can be omitted, like so:

```jsx
<Route path={AppRoutes.dev.pathForComponent()} element={<MyNewComponent />} />
```

The route-path must be added to the `AppRoutes`.

Relevant code can be found in:

- `Core.tsx` found in [`/app/Core.tsx`](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Core.tsx)
- `AppRoutes` found in [`/app/Routes.ts`](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Routes.ts)

## Color references and icons

The `Playground` component can be accessed at `/app/playground` and has a list of colors used on the site.
Additionally, every icon currently available can be viewed here, showing the name of it by hovering with the mouse.

The component is intended for experimenting and the component is not available in any non-local environment.

Icons can be added to the site, by adding the file as an SVG in the folder `/app/ui-components/icons`, then running the command `npm run refresh-icons`.
The icon is now available to use in the `name` parameter for the Icon-component.

## Baseline for CSS features

Most of the CSS used for UCloud is written and injected with the `injectStyle`-function, which means the CSS rules will not be transformed in any way to adhere to the quirks of different browsers, or delayed deployments of features in one browser over another.

Because of this, no bleeding edge CSS features are to be used for development, with at least a 95 % target. See [caniuse.com](http://caniuse.com). <!-- Re-phrase? Different value? Worth bringing up at all? -->


## Testing

UCloud has an E2E-testing suite that will run automatically after an update to the `master`-branch. This can be triggered by the admin <!--correct role?--> of the UCloud Github repository.

To run the tests for your local environment, the following commands are available:

| Command                           | Uses                                                                                                                                                         |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `npx playwright test`             | Run the tests in the terminal.                                                                                                                              | 
| `npx playwright test --ui`        |  **Recommended** Opens a user-friendly interface in a browser window that shows images of the running tests and allows for viewing relevant network-calls.  |
| `npx playwright test --headed`    | Runs every test, opening a new browser instance for each.                                                                                                    |
| `npx playwright show-report`      | Opens the report generated by a previously run test suite.                                                                                                   |

To add tests to the suite, the files are located in [UCloud/tree/master/frontend-web/webclient/tests](https://github.com/SDU-eScience/UCloud/tree/master/frontend-web/webclient/tests).

The suite consists of files mapping to different parts of UCloud functionality. These are:

| Test-file            | Area                                                                                                                                               |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `accounting.spec.ts` | Applying for resources and accounting validation                                                                                                   |
| `compute.spec.ts`    | Running jobs, checking restrictions are correctly applied for optional parameters, and mounting folders, multinode, connecting to other jobs, etc. |
| `drive.spec.ts`      | Drives and drive-operations                                                                                                                        |
| `file.spec.ts`       | Files and file-operations                                                                                                                          |
| `resources.spec.ts`  | Creating resources (SSH-keys, public links, etc.) and using them with compute resources.                                                           |
| `user.spec.ts`       | Logging in, validating that links in user-menu works, and links on login page.                                                                     |

The other notable files in the test folder are `user.setup.ts` and `shared.ts`.

The first of the two, `user.setup.ts`. This is the script that sets up the different contexts of users to be used in the tests. The contexts refers to four users in total: a personal workspace user, a project PI, a project admin, and a project user. The created users are required for the majority of the tests, so it rarely makes sense to not have the script run prior to running the tests.

`shared.ts` is a library that aims to match function-calls with actions on UCloud, e.g. `Files.upload()` that will upload a file, `Applications.toggleFavorite()` used for favoriting an application.
When writing a test for a feature, most of it should consist of calls to this library. If the functionality for an action isn't there, it's likely it should be added.

Tests can be added to the existing test suite, but tests must not be removed, as most are part of a compliance requirement. <!--(link to PDF?)-->

Relevant code can be found in `/testing`, with the output of a test-suite run in `/test-results`.

### Coding-style preferences

`let` is to be used over `var`, due to the reduced lifetime of `let`.

For function definitions, TypeScript/ECMAScript allows using both `function` and `const`. `function` is preferred, due to the different lifetimes and positioning requirements in files between the two options.
If the function is exported, `function` is usually the one to use. If the function is local to the file, the `const` approach is fine to use.

If a magic string/number is present more than once, it's to be extracted into a constant. This is not the case for styling (pixels, colors, etc.) in components. <!--(This is probably a given. Remove?)-->

<!--
### Icon strategy

This is something we probably should discuss offline, but finding a good icon when needed seems increasingly hard, but the solution might just be to not have them at all in some cases.

A [blog](https://tonsky.me/blog/tahoe-icons/) wrote about this issue on MacOS, and it seems reasonable. This would also involve simplifying some icons. Not sure why I never wondered why the "Create" operation has "upload" as its default icon. Something like this could probably be replaced by a +-symbol that's usable among all resources.

So what this section should contain is how to gauge whether or not using an icon is needed.

!-->