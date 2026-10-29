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
endRenderPage | None | daopsdakopsd | Optional |
open | string, string, ResourceType? | <TODO> | Required |
skipOpen | string, string, ResourceType? | A check called before `open` to allow skipping calling open. Returns a boolean | Optional |
refresh | None | A optional callback called before the `refresh`-functionality in the resourcebrowser-component | Optional
wantToFetchNextPage | string | Callback for when the end of the page is reached, and the last page returned a defined `next` entry. | <TODO>
search | string | A callback for when a search is made by the user
onSearchHidden | None | A callback that's invoked when the search-bar is hidden
copy | ResourceType[], string | A callback for when a copy action is done using your keyboard. | Optional 
move | ResourceType[], string | A callback for when a cut action is done using your keyboard, or dragging a entry into a valid target | Optional
starClicked | ResourceType | Callback for when the star is clicked | Optional
renderEmptyPage | EmptyReason | Callback for what to render, depending on error-codes or PageV2 contents  


<TODO>
    "fetchOperations": () => OperationOrGroup<T, any>[] | ResourceBrowserActions<T, any>;
    "fetchOperationsCallback": () => unknown | null;
    "fetchBrowserFeatures": () => ControlDescription[] | undefined;
<TODO>

| validDropTarget | ResourceType | Returns true if the hovered entry is a valid target, e.g. folder when dragging files | Optional
    "renderDropIndicator": (selectedEntries: T[], currentTarget: string | null) => void;

    "generateBreadcrumbs": (path: string) => ({title: string, absolutePath: string}[]) | "custom-rendering";
    "locationBarVisibilityUpdated": (visible: boolean) => void;

    "generateTabCompletionEntries": (prompt: string, shouldFetch: boolean, lastSelectedIndex: number) => string[] | Promise<void>;
    "renderLocationBar": (prompt: string) => {rendered: string, normalized: string};

    "pathToEntry": (entry: T) => string;
    "nameOfEntry": (entry: T) => string;

    "sort": (page: T[]) => void;
    "fetchFilters": () => Filter[];
    "renderUtilityControls": (container: HTMLElement) => void;
}

### Implementation


### Use 

To use the ResourceBrowser-component, a parent component written in React is used.

```typescript
export function Browser(): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<NetworkIP> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    usePage("Public IPs", SidebarTabId.RESOURCES);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);
    
    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<NetworkIP>(mount, "Public IPs", opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([
                    {name: "IP address"},
                    {name: "", columnWidth: 0},
                    {name: "", columnWidth: 0},
                    {name: "In use with", columnWidth: 250},
                ]);

                supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(NetworkIPApi));
                addProjectListener(PROJECT_CHANGE_LISTENER_ID, p => {
                    supportByProvider.retrieve(p ?? "", () => retrieveSupportV2(NetworkIPApi));
                })

                const dummyEntry: NetworkIP = {
                    id: DUMMY_ENTRY_ID,
                    specification: {product: {category: "", id: "", provider: ""}},
                    createdAt: new Date().getTime(),
                    owner: {createdBy: ""},
                    status: {boundTo: [], state: "PREPARING"},
                    permissions: {myself: []},
                    updates: [],
                };

                browser.on("skipOpen", (_oldPath, _newPath, resource) => {
                    if (!resource || !opts?.selection) return false;
                    if (opts.selection.show(resource) === true) opts.selection.onClick(resource);
                    return true;
                });

                browser.on("open", (_oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("public-ips", resource.id));
                        return;
                    }

                    callAPI(NetworkIPApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts?.additionalFilters,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        NetworkIPApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.setEmptyIcon(productTypeToIcon("NETWORK_IP"));

                browser.on("fetchFilters", () => [dateRanges]);

                browser.on("renderRow", (ip, row, dims) => {
                    if (ip.id !== DUMMY_ENTRY_ID) {
                        const icon = providerIcon(ip.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                        row.title.append(ResourceBrowser.defaultTitleRenderer(ip.status.ipAddress ?? ip.id, row));
                    }

                    if (opts?.selection) {
                        const useButton = browser.defaultButtonRenderer(opts.selection, ip);
                        if (useButton) row.stat3.append(useButton);
                    } else if (ip.status.boundTo.length === 1) {
                        const [boundTo] = ip.status.boundTo;
                        row.stat3.innerText = boundTo;
                    }
                });

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your public IPs...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No network IP found with active filters.")
                            else e.reason.append("This workspace has no public IPs.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your public IPs.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your public IPs. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => {
                    const callbacks: ResourceBrowseCallbacks<NetworkIP, ProductNetworkIP> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        navigate,
                        reload: () => browser.refresh(),
                        cancelCreation: doNothing,
                        viewProperties(res: NetworkIP): void {
                            navigate(AppRoutes.resource.properties("public-ips", res.id));
                        },
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        api: NetworkIPApi,
                        isCreating: false
                    };
                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<NetworkIP, ProductNetworkIP>;

                    const actions = NetworkIPApi.retrieveActions();
                    if (!Array.isArray(actions)) return actions;
                    const operations = actions;
                    const create = operations.find(it => it.tag === CREATE_TAG);
                    if (create) {
                        create.enabled = () => true;
                        create.onClick = async () => {
                            try {
                                const products = (await supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(NetworkIPApi))).newProducts;
                                dialogStore.addDialog(
                                    <NetworkIpCreate
                                        onCancel={() => {
                                            dialogStore.failure();
                                        }}
                                        products={products}
                                        onCreate={async (product, ports, permissions) => {
                                            const productReference = {
                                                id: product.name,
                                                category: product.category.name,
                                                provider: product.category.provider
                                            };

                                            const networkIP = {
                                                ...dummyEntry,
                                                id: "",
                                                specification: {
                                                    product: productReference
                                                },
                                                owner: {createdBy: ""},
                                            } as NetworkIP;

                                            try {
                                                const response = (await callAPI(
                                                    NetworkIPApi.create(
                                                        bulkRequestOf({
                                                            product: productReference,
                                                            domain: "",
                                                        })
                                                    )
                                                )).responses[0] as unknown as FindByStringId;

                                                networkIP.id = response.id;

                                                await callAPI(NetworkIPApi.updateFirewall({
                                                    type: "bulk",
                                                    items: [{
                                                        id: networkIP.id,
                                                        firewall: {
                                                            openPorts: ports,
                                                        }
                                                    }]
                                                }));

                                                for (const permission of permissions) {
                                                    const fixedPermissions: Permission[] = permission.permissions.find(it => it === "EDIT") ? ["READ", "EDIT"] : ["READ"];
                                                    const newEntry: ResourceAclEntry = {
                                                        entity: {type: "project_group", projectId: permission.entity["projectId"], group: permission.entity["group"]},
                                                        permissions: fixedPermissions
                                                    };

                                                    await callAPI(
                                                        NetworkIPApi.updateAcl(bulkRequestOf(
                                                            {
                                                                id: response.id,
                                                                added: [newEntry],
                                                                deleted: [permission.entity]
                                                            }
                                                        ))
                                                    );
                                                }

                                                const newNetworkIP = await callAPI(NetworkIPApi.retrieve({id: networkIP.id}));
                                                browser.insertEntryIntoCurrentPage(newNetworkIP);
                                                browser.renderRows();
                                                browser.selectAndShow(it => it === newNetworkIP);
                                                dialogStore.success();
                                            } catch (e: any) {
                                                sendFailureNotification("Failed to activate public IP. " + extractErrorMessage(e));
                                                browser.refresh();
                                                return;
                                            }
                                        }}
                                    />,
                                    () => {},
                                    true,
                                    slimModalStyle,
                                );
                            } catch (e) {
                                displayErrorMessageOrDefault(e, "Failed to fetch products for creating public IP")
                            }
                        }
                    }
                    return operations.filter(it => it.enabled(entries, callbacks, entries));
                });
            });
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {headerControls?.projectSwitcherTarget
                ? createProjectSwitcherPortal(headerControls.projectSwitcherTarget)
                : switcher}
        </>}
    />
}
