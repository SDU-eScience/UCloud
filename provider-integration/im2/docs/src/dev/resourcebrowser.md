## ResourceBrowser

The ResourceBrowser is a component intended to bring a user interface for resource, with features a user would expect for a native file explorer. The component is written in Vanilla TypeScript, but is mounted within the React-mounted root. The resource browser 

Additionally, keyboard navigation, either by arrow-keys or shortcuts, are available when using the component.

### Motivation

The component was written to accomodate rendering directories containing thousands of files, without any significant slowdowns. In addition, the component supports keyboard inputs, like navigation and shortcuts. The component replaced the previous custom 'List'-component, that was written in React, which had major slowdowns for larger directories. Initially, the component was written with file-browsing in mind, which is why some of the operations relate heavily to functionality to files.

### Features

The ResourceBrowser accepts a combination of `features` that allow and disallow functionality in the mounted component.

All of these are optional.

| Name                            | Description |
| dragToSelect                    | Enables drag to select with mouse. | 
| supportsMove                    | Whether the resource kind supports moving, e.g. files 
| supportsCopy                    | Whether the resource kind supports copying, e.g. files
| locationBar                     | If the component should show an editable location bar at the top
| showHeaderInEmbedded            | TODO
| showStar                        | Whether or not to render a star on the left side of an entries' row. Used for "favoriting".
| renderSpinnerWhenLoading        | If the component should render the loading spinner on initial load.
| breadcrumbsSeparatedBySlashes   | The component can show breadcrumbs in the header. Enable to add slashes between, for instance for file paths.
| search                          | Is search enabled for the component
| filters                         | Are filters enabled for the component
| breadcrumbTitles                | Add the breadcrumb value to the html tags 'title' attribute.
| showColumnTitles                | Usually disabled for embedded resource-browsers, enables titles for columns. Required if sorting is allowed. 
| sorting                         | Enables sorting by column. Only works if `showColumnTitles` is also enabled. In addition, you must also set `sortById` on the appropriate columns.          |
| projectSwitcher                 | Adds a HTML-element to the header to allocate space for the project-switcher component.

### Operations

The ResourceBrowser-component has a series of operations that must be defined:

*Name* | *args* | *description* | *required* 
unmount                 | None | An operation that is called to initiate teardown of the component when it is no longer mounted in the React-DOM. | Managed by the ResourceBrowser-component itself
rowSelectionUpdated     | None | Called when the selection of entries changes | Optional
beforeShortcut    | KeyboardEvent | Called before a keyboard event is passed to the shortcut handlers, to close menus | Managed by the ResourceBrowser-component itself
unhandledShortcut | KeyboardEvent | Called if a keyboard shortcut entered is not defined in the ResourceBrowser-component, and allows extending the shortcuts when using the component. | Required
startRenderPage | None | Called before rendering of the first element.  | Optional
renderRow | ResourceType, ResourceBrowserRow, RenderDimensions | asdopkasdpko | Required
endRenderPage | None | <TODO>  | Optional |
open | string, string, ResourceType? | `open` is called when double-clicking or pressing "Enter" on an active row. For a folder, this would navigate to the contents of the folder, for a run, this would navigate to the job results. | Required |
skipOpen | string, string, ResourceType? | A check called before `open` to allow skipping calling open. Returns a boolean. | Optional |
refresh | None | A optional callback called before the `refresh`-functionality in the resourcebrowser-component | Optional
wantToFetchNextPage | string | Callback for when the end of the page is reached, and the last page returned a defined `next` entry. | Required
search | string | A callback for when a search is made by the user | Optional
onSearchHidden | None | A callback that's invoked when the search-bar is hidden | Optional
copy | ResourceType[], string | A callback for when a copy action is done using your keyboard. | Optional 
move | ResourceType[], string | A callback for when a cut action is done using your keyboard, or dragging a entry into a valid target | Optional
starClicked | ResourceType | Callback for when the star is clicked | Optional
renderEmptyPage | EmptyReason | Callback for what to render, depending on error-codes or PageV2 contents | Required


<TODO>
    "fetchOperations": () => OperationOrGroup<T, any>[] | ResourceBrowserActions<T, any>;
    "fetchOperationsCallback": () => unknown | null;
    "fetchBrowserFeatures": () => ControlDescription[] | undefined;
<TODO>

| validDropTarget | ResourceType | Returns true if the hovered entry is a valid target, e.g. folder when dragging files | Optional
    "renderDropIndicator": (selectedEntries: T[], currentTarget: string | null) => void;
| generateBreadcrumbs | {title: string, absolutePath: string}[] | "custom-rendering";
    "locationBarVisibilityUpdated": (visible: boolean) => void;

    "generateTabCompletionEntries": (prompt: string, shouldFetch: boolean, lastSelectedIndex: number) => string[] | Promise<void>;
    "renderLocationBar": (prompt: string) => {rendered: string, normalized: string};

| pathToEntry | (entry: T) => string | Given an entry, returns a unique identifier for the entry. | Required |
| nameOfEntry | (entry: T) => string | Given an entry, returns a presentable name for for the entry. `pathToEntry` is used as fallback. | Optional |
| sort | ResourceType[] | A sorting function used for local sorting. E.g. when a folder is inserted in the frontend optimistically. |
| fetchFilters | () => <TODO> | Returns the filters for the browser. This is dynamic, as some filters should only be active for projects, for example.  |
| renderUtilityControls | (container: HTMLElement) => void; | 

### Implementation

See <TODO-LINK>USE</TODO-LINK> for implementing a component that uses the ResourceBrowser.

The ResourceBrowser-component


### Use 

To use the ResourceBrowser-component, a parent component written in React is used.

The minimum viable use of the ResourceBrowser-component is as follows, with added comments:

```typescript
export function Browser(): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ResourceType> | null>(null);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<NetworkIP>(mount, "Resource Type", opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([{name: "Title"}, {name: "Created at"}]);

                browser.on("open", (_oldPath, newPath, resource) => {
                    callAPI(ResourceApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        ResourceApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                        })
                    )

                    browser.registerPage(result, path, false);
                });

                browser.setEmptyIcon(productTypeToIcon("ResourceTypeIcon"));

                browser.on("fetchFilters", () => []);

                browser.on("renderTitle", (item, title, row) => title.append(ResourceBrowser.defaultTitleRenderer(item.id, row)));

                browser.on("renderStat1", (item, stat) => stat.innerText = item.createdAt}); 

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    e.reason.append("The page is empty for reason");
                });

                browser.on("fetchOperationsCallback", () => ({
                    dispatch,
                    navigate,
                    invokeCommand: call => callAPI(call),
                    api: ResourceApi,
                });

                browser.on("fetchOperations", () => ResourceApi.retrieveActions());
            });
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <MainContainer main={<div ref={mountRef} />} />
}
```
