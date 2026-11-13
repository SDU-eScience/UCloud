# UCloud frontend architecture

Some notable architecture choices are describe here.

### React Router

The library `React Router` is used to routing. What the [main container](./frontend-layout.md) shows is decided by the URL, and will mount the relevant component in most cases. While using `React Router` is not notable, some aspects to how the frontend can lead to different components than expected. 

The UCloud frontend has some cases where the component will not be mounted as expected. These include:

- If the user is not logged in, and the component requires it.
    - The user will be redirected to the login page.
- The Service License Agreement is required to be accepted and has not yet been accepted.
    - The user will be redirected to read and accept the SLA.
- Two factor authentication is required, but not yet enabled.
    - The user is redirected to enable 2FA.

Some components require the user-role to be ADMIN, or above, in a project to access, and some require the user to be a UCloud Admin. These checks are handled inside the components themselves, and not at the router level. They also handle the navigation, if navigation is needed.

### React Redux and External Stores

The frontend uses both the Redux store and custom external stores. The Redux store is not used with the content expected to be serializable and exists between sessions. The Redux store is populated on page load with calls to the backend.

<figure>

```mermaid
flowchart LR
    subgraph UCloud frontend
        A["Frontend component"]:::diagram-external-cog-6-tooth
        B["Redux store"]:::diagram-external-cog-6-tooth
        C["External store"]:::diagram-external-cog-6-tooth
        A -->|Dispatches actions| B
        B -->|Reads from Redux| A
        A -->|Triggers calls| C
        C -->|Subscribes to changes| A
    end
```

</figure>

The external stores keep more state about the contents and can be used in a non-React context. These stores can make calls directly from the stores themselves, and do not rely on dispatching the actions with the contents to be stored.
