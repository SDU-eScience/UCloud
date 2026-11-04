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
        callAPI(usageReportRetrieve({start: 0, end: new Date().getTime()})).then(result => {
            /* Do stuff with result */
        });
    });

    /* Code */
}
```

`callAPI` takes authentication into account. This also includes refreshing tokens when expired, and automically logging the user out, if the the tokens have been invalidated.
Relevant code can be found in [Authentication/DataHook.ts](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Authentication/DataHook.ts)

If authentication is not needed for the request, [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API) can be used instead.


## HttpClient - lib.ts

A singleton named `Client` is the instantiation of the `HttpClient` that is used to make authenticated calls to the backend. It exposes some additional functionality, like `activeUsername()`, `isLoggedIn()` and `projectId()`, with the latter only to be used when the `useProjectId`-hook can't be used.

Making backend calls should go through `callAPI` or `fetch`, and not be done by using the `call` function directly.

Relevant code can be found in [`/app/Authentication/lib.ts`](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Authentication/lib.ts)
