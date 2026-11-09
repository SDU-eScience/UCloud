# Documentation

This chapter covers the style of the UCloud documentation.

## Diagrams

A diagram draws each component as a card. A card has a colored icon tile, a type on the first line, and a name with
optional attributes on the second line. The type tells what the component is. The name tells which instance it is.

Write the card text as `type<br/>name | attribute`, for example `Pod<br/>Dynamo frontend | port: 8000`.

### Views

Write one figure per question. Read the views from the outside in, and state the view in the caption.

| View | Question it answers | Reader |
|------|---------------------|--------|
| System context | Who uses the system, and which systems does it talk to? | A new reader, a manager |
| Container | Which applications and data stores make up the system? | An architect, an operator |
| Component | Which parts live inside one container? | A developer |
| Dynamic | How does one flow work, step by step? | A developer, a support engineer |
| Deployment | Which machine runs what, and how does traffic reach it? | An operator |

A container is a running application or a data store. It is not a Kubernetes container. A component is a part of one
container, and not a part of the user interface.

### Captions

A caption says what the figure shows and what it does not show. Write a sentence about the figure.

The views index names the view of every figure. The caption does not repeat the view name as a label, because a label
breaks the reading flow.

An example: "The parts of UCloud/IM that serve inference, and the systems they touch. It does not show how a request is
handled inside a part."

### Icons

Use one icon for one concept, everywhere in the documentation. Add an icon when a new concept appears.

Use the name that the [glossary](../glossary.md) defines for an element, in prose and in diagrams. Link the first
mention of an element in a page to its entry in the glossary.

| Concept | Icon | Class | Used for |
|---------|------|-------|----------|
| UCloud mark | <span class="diagram-icon diagram-external diagram-icon-ucloud"></span> | `ucloud` | UCloud/Core, UCloud/Proxy, and the UCloud services |
| UCloud/IM mark | <span class="diagram-icon diagram-provider diagram-icon-ucloud"></span> | `ucloud` | UCloud/IM |
| Kubernetes mark | <span class="diagram-icon diagram-provider diagram-icon-kubernetes"></span> | `kubernetes` | A Kubernetes cluster, a namespace, and a Kubernetes object |
| Person | <span class="diagram-icon diagram-provider diagram-icon-user"></span> | `user` | An end-user, an administrator, and an identity |
| Group of people | <span class="diagram-icon diagram-external diagram-icon-user-group"></span> | `user-group` | A group of users, a set of clients, and a project |
| Window | <span class="diagram-icon diagram-external diagram-icon-window"></span> | `window` | A browser and a web interface |
| Puzzle piece | <span class="diagram-icon diagram-provider diagram-icon-puzzle-piece"></span> | `puzzle-piece` | An application and a software package |
| Script | <span class="diagram-icon diagram-external diagram-icon-code-bracket"></span> | `code-bracket` | A tool, a script, and a configuration file |
| Cube | <span class="diagram-icon diagram-provider diagram-icon-cube"></span> | `cube` | A container, a job that runs a container, and the sandbox that runs the tools of a chat |
| Globe | <span class="diagram-icon diagram-provider diagram-icon-globe-alt"></span> | `globe-alt` | An API and a gateway: the HTTP entry point of a component |
| Server | <span class="diagram-icon diagram-provider diagram-icon-server"></span> | `server` | A server, a host, and a service |
| Chip | <span class="diagram-icon diagram-provider diagram-icon-cpu-chip"></span> | `cpu-chip` | Compute: a CPU or GPU resource, and a machine slice |
| Folder | <span class="diagram-icon diagram-provider diagram-icon-folder"></span> | `folder` | A filesystem, a directory, and a set of files |
| Database | <span class="diagram-icon diagram-provider diagram-icon-circle-stack"></span> | `circle-stack` | A database, a table, and other storage |
| Document | <span class="diagram-icon diagram-provider diagram-icon-document-text"></span> | `document-text` | A record: an audit entry, a catalog entry, and a report |
| Magnifying glass | <span class="diagram-icon diagram-provider diagram-icon-magnifying-glass"></span> | `magnifying-glass` | Discovery and a search for data |
| Gear | <span class="diagram-icon diagram-provider diagram-icon-cog-6-tooth"></span> | `cog-6-tooth` | A service component: middleware, an orchestrator, and an operator |
| Calculator | <span class="diagram-icon diagram-external diagram-icon-calculator"></span> | `calculator` | Cost: usage, billing, and credits |
| Chat bubble | <span class="diagram-icon diagram-external diagram-icon-chat-bubble-left-right"></span> | `chat-bubble-left-right` | A chat and a request to a chat API |

Write the class of a node as `diagram-external-<name>` or `diagram-provider-<name>`. A group uses the same classes.

### Colors

The color of the icon tile shows who controls the component.

| Owner | Tile | Ink | Used for |
|-------|------|-----|----------|
| External | `#95C0F3` | `#03499D` | The users, the client applications, and UCloud/Core |
| Provider | `#D8B0D8` | `#A74EA7` | UCloud/IM, the provider's cluster, and the provider's systems |

Use the pink tile for everything the provider controls. A component on the provider's hardware is pink, also when a
user starts it. The job container of a user is an example.

### Brand marks

UCloud and Kubernetes have their own marks. Draw a mark in the ink color of the tile, like the other icons. The UCloud
mark has one shape for UCloud/Core and for UCloud/IM. The color of the tile tells the two apart.

### Sources

The generic icons come from [Heroicons](https://heroicons.com), under the MIT license. The files are in
`diagram-icons/heroicons`, together with the license. Add a new Heroicon to that folder.

An icon from another set goes into a folder of its own, with the license of that set. Do not mix two sets in one
folder.

The UCloud mark derives from the logo in [Branding and identity for UCloud](./branding.md). The Kubernetes mark derives
from the Kubernetes logo, a trademark of the CNCF. The product marks are in `diagram-icons/marks`.

## New Feature Pages

A new feature page needs:

- one sentence that places the feature in the system;
- one container or component view of the feature;
- a dynamic view, when the feature has a flow with steps.

Do not add a code-level view. Code lives in the reference pages.
