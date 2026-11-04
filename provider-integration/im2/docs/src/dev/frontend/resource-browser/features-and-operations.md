### Features

The ResourceBrowser accepts a combination of `features` that allow and disallow functionality in the mounted component.

All of these are optional.

| *Name*                          | *Description* |
|---------------------------------|------------------------------------|
| dragToSelect                    | Enables drag to select with mouse. |
| supportsMove                    | Whether the resource kind supports moving, e.g. files | 
| supportsCopy                    | Whether the resource kind supports copying, e.g. files |
| locationBar                     | If the component should show an editable location bar at the top |
| showHeaderInEmbedded            | TODO |
| showStar                        | Whether or not to render a star on the left side of an entries' row. Used for "favoriting". |
| renderSpinnerWhenLoading        | If the component should render the loading spinner on initial load. | 
| breadcrumbsSeparatedBySlashes   | The component can show breadcrumbs in the header. Enable to add slashes between, for instance for file paths. | 
| search                          | Is search enabled for the component | 
| filters                         | Are filters enabled for the component | 
| breadcrumbTitles                | Add the breadcrumb value to the html tags 'title' attribute. |
| showColumnTitles                | Usually disabled for embedded resource-browsers, enables titles for columns. Required if sorting is allowed.  |
| sorting                         | Enables sorting by column. Only works if `showColumnTitles` is also enabled. In addition, you must also set `sortById` on the appropriate columns.          |
| projectSwitcher                 | Adds a HTML-element to the header to allocate space for the project-switcher component. |

### Operations

The ResourceBrowser-component has a series of operations that must be defined, and can be defined:

| Name   | args | Description | Required |
|--------|------|-------------|----------|
| unmount                 | None | An operation that is called to initiate teardown of the component when it is no longer mounted in the React-DOM. | Managed by the ResourceBrowser-component itself | Not relevant |
| rowSelectionUpdated     | None | Called when the selection of entries changes | Optional |
| beforeShortcut    | KeyboardEvent | Called before a keyboard event is passed to the shortcut handlers, to close menus | Managed by the ResourceBrowser-component itself
| unhandledShortcut | KeyboardEvent | Called if a keyboard shortcut entered is not defined in the ResourceBrowser-component, and allows extending the shortcuts when using the component. | Required
| startRenderPage | None | Called before rendering of the first element.  | Optional
| renderRow | ResourceType, ResourceBrowserRow, RenderDimensions | asdopkasdpko | Required
| endRenderPage | None | A callback, called after the final row has been rendered. Can be used to make batched calls, built up from rendering of rows. | Optional |
| open | string, string, ResourceType? | `open` is called when double-clicking or pressing "Enter" on an active row. For a folder, this would navigate to the contents of the folder, for a run, this would navigate to the job results. | Required |
| skipOpen | string, string, ResourceType? | A check called before `open` to allow skipping calling open. Returns a boolean. | Optional |
| refresh | None | A optional callback called before the `refresh`-functionality in the resourcebrowser-component | Optional
| wantToFetchNextPage | string | Callback for when the end of the page is reached, and the last page returned a defined `next` entry. | Required
| search | string | A callback for when a search is made by the user | Optional
| onSearchHidden | None | A callback that's invoked when the search-bar is hidden | Optional
| copy | ResourceType[], string | A callback for when a copy action is done using your keyboard. | Optional 
| move | ResourceType[], string | A callback for when a cut action is done using your keyboard, or dragging a entry into a valid target | Optional
| starClicked | ResourceType | Callback for when the star is clicked | Optional
| renderEmptyPage | EmptyReason | Callback for what to render, depending on error-codes or PageV2 contents | Required
| fetchOperations | () => OperationOrGroup<T, any>[] \| ResourceBrowserActions<T, any> | Required
| fetchOperationsCallback | () => unknown | null | Returns an object with methods and values the actions/operations will use (e.g. `navigate`, `dispatch`, and `ResourceApi`) | Required |
| fetchBrowserFeatures": () => ControlDescription[] \| undefined | ResourceBrowser-specific operations. Currently only in use for Files with F2 for renaming entries | Optional |
| validDropTarget | ResourceType | Returns true if the hovered entry is a valid target, e.g. folder when dragging files | Optional
| renderDropIndicator | (selectedEntries: T[], currentTarget: string | null) => void | <TODO>
| generateBreadcrumbs | {title: string, absolutePath: string}[] | "custom-rendering" | | <TODO>
    "locationBarVisibilityUpdated": (visible: boolean) => void;
| generateTabCompletionEntries | (prompt: string, shouldFetch: boolean, lastSelectedIndex: number) => string[] \| Promise<void> | Returns a list possible completions based on the current locationbar text. | Optional |
| renderLocationBar | (prompt: string) => {rendered: string, normalized: string} | Based on the `prompt`, returns a `rendered` and `normalized` version of the input, with the `rendered` used for presenting to the user, and `normalized` being used behind the scenes. For instance, a drive ID in the `normalized` version will be replaced with the drive name in the `rendered` version, but the drive ID is necessary for navigation. | Optional |

| pathToEntry | (entry: T) => string | Given an entry, returns a unique identifier for the entry. | Required |
| nameOfEntry | (entry: T) => string | Given an entry, returns a presentable name for for the entry. `pathToEntry` is used as fallback. | Optional |
| sort | ResourceType[] | A sorting function used for local sorting. E.g. when a folder is inserted in the frontend optimistically. |
| fetchFilters | () => <TODO> | Returns the filters for the browser. This is dynamic, as some filters should only be active for projects, for example.  |
| renderUtilityControls | (container: HTMLElement) => void; |
