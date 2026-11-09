### Features

The ResourceBrowser accepts a combination of `features` that allow and disallow functionality in the mounted component.

All of these are optional.

| *Name*                          | *Description*                                                                                                                                      |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| dragToSelect                    | Enables drag to select with mouse.                                                                                                                 |
| supportsMove                    | Whether the resource kind supports moving, e.g. files                                                                                              | 
| supportsCopy                    | Whether the resource kind supports copying, e.g. files                                                                                             |
| locationBar                     | If the component should show an editable location bar at the top                                                                                   |
| showHeaderInEmbedded            | TODO                                                                                                                                               |
| showStar                        | Whether or not to render a star on the left side of an entries' row. Used for "favoriting".                                                        |
| renderSpinnerWhenLoading        | If the component should render the loading spinner on initial load.                                                                                | 
| breadcrumbsSeparatedBySlashes   | The component can show breadcrumbs in the header. Enable to add slashes between, for instance for file paths.                                      | 
| search                          | Is search enabled for the component                                                                                                                | 
| filters                         | Are filters enabled for the component                                                                                                              | 
| breadcrumbTitles                | Add the breadcrumb value to the html tags 'title' attribute.                                                                                       |
| showColumnTitles                | Usually disabled for embedded resource-browsers, enables titles for columns. Required if sorting is allowed.                                       |
| sorting                         | Enables sorting by column. Only works if `showColumnTitles` is also enabled. In addition, you must also set `sortById` on the appropriate columns. |
| projectSwitcher                 | Adds a HTML-element to the header to allocate space for the project-switcher component.                                                            |

### Operations

The ResourceBrowser-component has a series of operations that must be defined, and can be defined:

| Name                         | Required                        |
|------------------------------|---------------------------------|
| [unmount](#unmount)                      | No. Handled by ResourceBrowser. |
| [rowSelectionUpdated](#rowselectionupdated)          | Optional                        |
| [beforeShortcut](#beforeshortcut)               | No. Handled by ResourceBrowser. |
| [unhandledShortcut](#unhandledshortcut)            | Required                        |
| [startRenderPage](#startrenderpage)              | Optional                        |
| [renderTitle](#rendertitle)                  | Yes                             |
| [renderStat1](#renderstat1)                  | No                              |
| [renderStat2](#renderstat2)                  | No                              |
| [renderStat3](#renderstat3)                  | No                              |
| [renderStat4](#renderstat4)                  | No                              |
| [endRenderPage](#endrenderpage)                | Optional                        |
| [skipOpen](#skipopen)                     | Optional                        |
| [open](#open)                         | Required                        |
| [refresh](#refresh)                      | Optional                        |
| [wantToFetchNextPage](#wanttofetchnextpage)          | Required                        |
| [search](#search)                       | Optional                        |
| [onSearchHidden](#onsearchhidden)               | Optional                        |
| [copy](#copy)                         | Optional                        |
| [move](#move)                         | Optional                        |
| [starClicked](#starclicked)                  | Optional                        |
| [renderEmptyPage](#renderemptypage)              | Required                        |
| [fetchOperations](#fetchoperations)              | Required                        |
| [fetchOperationsCallback](#fetchoperationscallback)      | Required                        |
| [fetchBrowserFeatures](#fetchbrowserfeatures)         | Optional                        |
| [validDropTarget](#validdroptarget)              | Optional                        |
| [renderDropIndicator](#renderdropindicator)          | <TODO>                          |
| [generateBreadcrumbs](#generatebreadcrumbs)          | <TODO>                          |
| [locationBarVisibilityUpdated](#locationbarvisibilityupdated) | <TODO>                          |
| [generateTabCompletionEntries](#generatetabcompletionentries) | Optional                        |
| [renderLocationBar](#renderlocationbar)            | Optional                        |
| [pathToEntry](#pathtoentry)                  | Required                        |
| [nameOfEntry](#nameofentry)                  | Optional                        |
| [sort](#sort)                         | <TODO>                          |
| [fetchFilters](#fetchfilters)                 | <TODO>                          |
| [renderUtilityControls](#renderutilitycontrols)        | <TODO>                          |

### unmount

`() => void;`

An operation that is called to initiate teardown of the component when it is no longer mounted in the React-DOM.

### rowSelectionUpdated

`() => void;`

Called when the selection of entries changes.

### beforeShortcut

`(ev: KeyboardEvent) => void;`

Called before a keyboard event is passed to the shortcut handlers, to close menus

### unhandledShortcut

`(ev: KeyboardEvent) => void;`

Called if a keyboard shortcut entered is not defined in the ResourceBrowser-component, and allows extending the available shortcuts when using the component. 

### startRenderPage 

`() => void;`

Called before rendering of the first element.

### renderTitle 

`(entry: T, title: HTMLElement, row: ResourceBrowserRow, size: ContainerSize, dims: RenderDimensions) => void;`

Called when rendering each row. Used for rendering a locally unique ID for the row.

### renderStat1 

`(entry: T, stat: HTMLElement, row: ResourceBrowserRow, size: ContainerSize) => void;`

Called when rendering each row. Used for rendering the first `stat` in the row. While optional, at least one stat is expected. 

### renderStat2 

`(entry: T, stat: HTMLElement, row: ResourceBrowserRow, size: ContainerSize) => void;`

Called when rendering each row. Used for rendering the second `stat` in the row.

### renderStat3 

`(entry: T, stat: HTMLElement, row: ResourceBrowserRow, size: ContainerSize) => void;`

Called when rendering each row. Used for rendering the third `stat` in the row.

### renderStat4 

`(entry: T, stat: HTMLElement, row: ResourceBrowserRow, size: ContainerSize) => void;`

Called when rendering each row. Used for rendering the fourth `stat` in the row.

### endRenderPage 

`() => void;`

A callback, called after the final row has been rendered. Can be used to make batched calls, built up from rendering of rows. 

### skipOpen 

`(oldPath: string, path: string, resource?: T) => boolean;`

A check called before `open` to allow skipping calling open. Returns a boolean. | 

### open 

`(oldPath: string, path: string, resource?: T) => void;`

Is called when double-clicking or pressing "Enter" on an active row. For a folder, this would navigate to the contents of the folder, for a run, this would navigate to the job results. It is not called if `skipOpen` returns true. 

### refresh 

`() => void;`

An optional callback called before the `refresh`-functionality in the Resource Browser-component 

### wantToFetchNextPage 

`(path: string) => Promise<void>;`

Callback for when the end of the page is reached, and the last page returned, contains a defined `next` entry. 

### search 

`(query: string) => void;`

A callback for when a search is made by the user

### onSearchHidden 

`() => void;`

A callback that's invoked when the search-bar is hidden.

### copy

`(entries: T[], target: string) => void;`

A callback for when a copy action is done using your keyboard. | Optional 

### move

`(entries: T[], target: string) => void;`

A callback for when a cut action is done using your keyboard, or dragging a entry into a valid target | Optional

### starClicked

`(entry: T) => void;`

Callback for when the star is clicked

### renderEmptyPage

`(reason: EmptyReason) => void;`

Callback for what to render, depending on error-codes or PageV2 contents.

### fetchOperations

`() => OperationOrGroup<T, any>[] | ResourceBrowserActions<T, any>;`

Returns the available operations or actions for the resources.

### fetchOperationsCallback

`() => unknown | null;`

Returns an object with methods and values the actions/operations will use (e.g. `navigate`, `dispatch`, and `ResourceApi`)

### fetchBrowserFeatures

`() => ControlDescription[] | undefined;`

ResourceBrowser-specific operations. Currently only in use for Files with F2 for renaming entries.

### validDropTarget

`(entry: T) => boolean;`

Returns true if the hovered entry is a valid target, e.g. folder when dragging files.

### renderDropIndicator

`(selectedEntries: T[], currentTarget: string | null) => void;`

<TODO>

### generateBreadcrumbs

`(path: string) => ({title: string, absolutePath: string}[]) | custom-rendering;`

<TODO>

### locationBarVisibilityUpdated

`(visible: boolean) => void;`

<TODO>

### generateTabCompletionEntries

`(prompt: string, shouldFetch: boolean, lastSelectedIndex: number) => string[] | Promise<void>;`

Returns a list possible completions based on the current locationbar text.

### renderLocationBar

`(prompt: string) => {rendered: string, normalized: string};`

Based on the `prompt`, returns a `rendered` and `normalized` version of the input, with the `rendered` used for presenting to the user, and `normalized` being used behind the scenes. For instance, a drive ID in the `normalized` version will be replaced with the drive name in the `rendered` version, but the drive ID is necessary for navigation. 

### pathToEntry

`(entry: T) => string;`

Given an entry, returns a unique identifier for the entry.

### nameOfEntry

`(entry: T) => string;`

Given an entry, returns a presentable name for for the entry. `pathToEntry` is used as fallback.

### sort

`(page: T[]) => void;`

A sorting function used for local sorting. E.g. when a folder is inserted in the frontend optimistically.

### fetchFilters

`() => Filter[];`

Returns the filters for the browser. This is dynamic, as some filters should only be active for projects, for example.

### renderUtilityControls

`(container: HTMLElement) => void;`

<TODO>
