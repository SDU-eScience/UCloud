## Network calls

UCloud has three different options for network calls, that components can use:

- `callAPI`
- `fetch`
- WSFactory

<!-- https://www.mermaideditor.io/#pako:eJx9Vd9r2zAQ_leC95JBC_4du4xBkyb0ITDYCnvY9iBLp0TEsYystgul__skxVYsq5khBN139-n03Z30FmBOILgLaM1f8R4JOdt-_93M1Nc9VzuB2v2MCt5IaMjZrL_7-aa3zTA_tryBRn4ewbMvt7dfZ8v5zx8bhCUXJx9czR-lbFc1-zD0Yb4Bifc9YPe2KS0RPjgZrX_1plkH4oVh-DMJXZ6J1-fVylk9OKvgJujkqYYuuHsLKtSB_pd7OGqZzPpG_eHDTvDnhqx4zYUCPlHzKYwqZTboyOqTMneo6W5VSkwjrWBHJE42JII0RBroAPOGjKGcxAUpFSRBSDZGaEQzqpGaNWCtSZJGWRa8K64WMKMMm6xPrVNb52iNKvzSnsNQFxRRrJwMxAUBw40rkkHUm5_gr9TGkEaLGCkjUdt1jDcTKqAJXoxhS0fKxSLMR9BAuSiSLDRySJXruiEuJYkogmwMW8owK_O8HEE2yzyFpDLScwxdN2GsAFEYoZbQVqZHBr4IEpRpOxKCv1r100WWmf2NeYsqqCdyDM1xcRgo-8qp3PvmnkQO5bawTbJMUVIVI-iJydovkBowJk9X8ulBv9ZnwDBOQkOa0sL18PZEUgpWPctR7DcyUcJ3Wb9A4_Sh9fHEag-PbLev1c_YqzRLQq0Sde1hkpdIH0dAjaTqtW7P2q0aHENWFTHNPwDvaxn9N1o5xKZjcYKATByGXPN0kRa698wV6Ipo--uMjfouQQUa7AOT1axV3c0wa1Ejr0zu2MMv6gj1SlYzCs6VYqOOagDQDqxsQw16u198LNmLkePKuI0cbI62GA2X044b7hID-feINnt3SM15e2WSDORPkTZ7Z5Eftre6r_3j2Rp4qGoXd5TPDv5mqDu4nFYVA_nXkzZ7XWLkhSePLM7yBCrX4UJJUiBaBqJecz_WNoOFfQGxYKq7UO1HExzncT51sQxVGeFIK6ceTeik2sENt2N2wW2sfSh3gl0eY4ihoKEWiBN02o7bus_lXX3_AFrr02o -->

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
