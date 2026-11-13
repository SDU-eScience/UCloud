### Use 

To use the ResourceBrowser-component, a parent component written in React is used.

The minimum viable use of the ResourceBrowser-component is as follows, with added comments:

```typescript
export function Browser(): React.ReactNode {
    // Used to target the div element, that acts as the root of the ResourceBrowser
    const mountRef = React.useRef<HTMLDivElement | null>(null);
     // A reference to the ResourceBrowser, to target the API if necessary.
    const browserRef = React.useRef<ResourceBrowser<ResourceType> | null>(null);

    // useLayoutEffect as we only care about mounting
    // _after_ the <div>-element has been mounted.
    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            // Creates instance of class, sets up the DOM elements, 
            // and the setup of the callbacks in the Resource Browser
            new ResourceBrowser<ResourceType>(mount, "Resource Type", opts)
                .init(browserRef, FEATURES, "", browser => {
                // Sets column titles. First and second only here, 
                // but an additional 3 are possible.
                browser.setColumns([{name: "Title"}, {name: "Created at"}]);

                // Behavior for when a row is activated by double mouse-click,
                // or pressing enter.
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

                // The item has an icon to represent it. 
                // It is presented when no entries can be shown
                browser.setEmptyIcon(productTypeToIcon("ResourceTypeIcon"));

                // Filters that can be applied to the items that are fetched from the backend.
                browser.on("fetchFilters", () => [dateRangeFilters("Date created")]);

                // How to render the title (left-most column)
                browser.on("renderTitle", (item, title, row) => {
                    title.append(ResourceBrowser.defaultTitleRenderer(item.id, row));
                });

                // How to render stat1 (see also `renderStat2`-`renderStat4`)
                browser.on("renderStat1", (item, stat) => stat.innerText = item.createdAt}); 
                
               // Generate the breadcrumbs to be presented in the header 
                browser.on("generateBreadcrumbs", () => [
                    {title: browser.resourceName, absolutePath: ""}
                ]);
                
                // What to write when the page is empty. 
                // Usually handled with a switch on `reason`.
                browser.on("renderEmptyPage", reason => {
                    browser.emptyPageElement.reason.append(
                        `The page is empty for reason: ${EmptyReasonTag[reason]}`
                    );
                });

                // Callbacks to be used by actions
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
    }, []);

    // Hooks the resource browser-refresh into the Redux store-stored refresh-operation
    useSetRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    // MainContainer element containing the `div`-element the Resource Browser is mounted in.
    return <MainContainer main={
        <>
            <div ref={mountRef} />
            // The ResourceBrowserProjectSwitcher mounts the Project Switcher
            // inside the Resource Browser header
            <ResourceBrowserProjectSwitcher browser={browserRef.current} />
        </>
    } />
}
```

If the API calls would succeed, this would:

- Render a list of resources, with `title` and `stat1` filled out
- Automatically fetch more if the bottom of the page is reached, by scrolling.
- Renders the title of the resource in the breadcrumbs.
- Allow filtering of resources, based on a date range.
- Show operations that can be done on the selected resources.
- Adds the Project Switcher to the header of the component.
