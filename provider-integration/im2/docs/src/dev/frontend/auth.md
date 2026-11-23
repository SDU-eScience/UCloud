# Authentication

Authentication the end-user can happen in one of two ways:

- Authentication with OIDC
- Authentication with password

A OIDC-user will go through the OIDC-system for initial authentication, while a password user will be be prompted for as username and password directly in the UCloud frontend.

For a password-user, 2FA is required, and the user will be prompted to enable it on first login, with a challenge every following login. For OIDC users, 2FA is optional, but encouraged.

```mermaid
flowchart LR
    subgraph loginprocess["UCloud/Frontend login process"]
        A["User"]:::diagram-external-user
        B["Login"]:::diagram-external-window
        C["OIDC"]
        D["2FA"]
        E["Dashboard"]:::diagram-external-window
    end
    
    A -->|Redirected to| B
    B -->|As OIDC user| C
    B -->|As password user| D
    D -->|Redirects to| E
    C -->|Redirects to| E
    C -->|Has 2FA enabled| D
    
    class B diagram-external-window
```

```mermaid
flowchart LR
    subgraph frontend["UCloud/Frontend"]
        A["Frontend component"]:::diagram-external-ucloud
        A --> C["HttpClient"]:::diagram-external-ucloud
        A --> B["WSFactory"]:::diagram-external-ucloud
        A --> D["Fetch"]:::diagram-external-ucloud
    end
    E["UCloud/Core"]:::diagram-external-ucloud
    C --> E
    D --> E
    B --> C
    
    class frontend diagram-external-window
```
