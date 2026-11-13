## Network calls

UCloud has three different options for network calls, that components can use:

- `callAPI`
- `fetch`
- `WSFactory#open`

<figure class="diagram" id="ucloud-fe-network-calls">

```mermaid
flowchart LR
    subgraph UCloud frontend<TODO>
        A["Frontend component"]:::diagram-external-ucloud
        A <--> B["WSFactory"]:::diagram-external-ucloud
        A <--> C["HttpClient"]:::diagram-external-ucloud
        A <--> D["Fetch"]:::diagram-external-ucloud
    end
    E["Backend service"]:::diagram-external-ucloud
    B <--> E
    C <--> E
    D <--> E
```

<figcaption>
    The different options the UCloud frontend has for communicating with a backend.
</figcaption>
</figure>


### callAPI

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

### `fetch`

If authentication is not needed for the request, [`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API) can be used instead.

```typescript
await fetch(Client.computeURL("/api/endpoint", "/retrieve"),
    {
        method: "GET",
        headers: {
            Accept: "application/json"
        },
    }
);
```

### `open` (WebSocket)

When continuously listening for messages is preferable, the `open` (WSFactory.open) method is used instead. One examples are job updates, that listens for state changes, job consumption, or file-browser that streams file results when searching.

```typescript
const conn = WSFactory.open(
    "/endpoint", {
    init: async conn => {
        await conn.subscribe({
            call: "endpoint.follow",
            payload: {id},
            handler: message => callback(message)
        });
        conn.close();
    }
});
```

## HttpClient - lib.ts

A singleton named `Client` is the instantiation of the `HttpClient` that is used to make authenticated calls to the backend. It exposes some additional functionality, like `activeUsername()`, `isLoggedIn()` and `projectId()`, with the latter only to be used when the `useProjectId`-hook can't be used.

Making backend calls should go through `callAPI` or `fetch`, and not be done by using the `call` function directly.

Relevant code can be found in [`/app/Authentication/lib.ts`](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Authentication/lib.ts)

### WebSocketFactory - ws.ts

A singleton named `WSFactory`, of the type `WebSocketFactory`, is used to establish websocket connections to the backend. A connection must provide a URL, payload and handler callback to manage the messages.

Relevant code can be found in [`/app/Authentication/lib.ts`](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Authentication/ws.ts)

`WebSocketFactory` depends on the `Client` singleton.
