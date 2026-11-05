### Use 

To use the ResourceBrowser-component, a parent component written in React is used.

The minimum viable use of the ResourceBrowser-component is as follows, with added comments:

```typescript
export function Browser(): React.ReactNode {
    // Used to target the div element, that acts as the root of the ResourceBrowser
    const mountRef = React.useRef<HTMLDivElement | null>(null);
     // A reference to the ResourceBrowser, to target the API if necessary.
    const browserRef = React.useRef<ResourceBrowser<ResourceType> | null>(null);
    // A workaround to place a React-component ProjectSelector in the ResourceBrowser tree
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    // useLayoutEffect as we only care about mounting _after_ the <div>-element has been mounted.
    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            // Creates instance of class, sets up the DOM elements, and the setup of the callbacks
            // in the Resource Browser
            new ResourceBrowser<NetworkIP>(mount, "Resource Type", opts).init(browserRef, FEATURES, "", browser => {
                // Sets column titles. First and second only here, but an additional 3 are possible.
                browser.setColumns([{name: "Title"}, {name: "Created at"}]);

                // Behavior for when a row is activated by double mouse-click, or pressing enter.
                browser.on("open", (_oldPath, newPath, resource) => {
                    callAPI(ResourceApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters
                    })).then(result => {
                        // Caches the page at the path, for quicker future access.
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                // Any shortcut not already handled by default could be handled here
                browser.on("unhandledShortcut", () => {});

                // The bottom of the items fetched so far has been reached.
                // If the items provided a `next` value, there's more to be fetched.
                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        ResourceApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                        })
                    )
                    
                    // Appends the result to the existing cached results
                    browser.registerPage(result, path, false);
                });

                // The item has an icon to represent it. It is presented when no entries can be shown
                browser.setEmptyIcon(productTypeToIcon("ResourceTypeIcon"));

                // Filters that can be applied to the items that are fetched from the backend.
                browser.on("fetchFilters", () => [dateRangeFilters("Date created")]);

                browser.on("renderTitle", (item, title, row) => title.append(ResourceBrowser.defaultTitleRenderer(item.id, row)));

                browser.on("renderStat1", (item, stat) => stat.innerText = item.createdAt}); 

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);
                browser.on("renderEmptyPage", reason => {
                    browser.emptyPageElement.reason.append(`The page is empty for reason: ${EmptyReasonTag[reason]}`);
                });

                // Callbacks
                browser.on("fetchOperationsCallback", () => ({
                    dispatch,
                    navigate,
                    invokeCommand: call => callAPI(call),
                    api: ResourceApi,
                });

                // Returns all operations. Filtering based on active items is handled elsewhere.
                browser.on("fetchOperations", () => ResourceApi.retrieveActions());
            });
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    // Hooks the resource browser-refresh into the Redux store-stored refresh-operation
    useSetRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    // MainContainer element containing the `div`-element the Resource Browser is mounted in.
    // The `switcher` refers to the project switcher that's defined and added above.
    return <MainContainer main={
        <>
            <div ref={mountRef} />
            {switcher}  
        </>
    } />
}
```
