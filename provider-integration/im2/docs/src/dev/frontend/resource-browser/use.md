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
                    browser.emptyPageElement.reason.append(`The page is empty for reason: ${EmptyReasonTag[reason]}`);
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
