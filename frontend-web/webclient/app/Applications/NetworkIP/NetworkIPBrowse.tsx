import {productTypeToIcon, ProductV2, ProductV2NetworkIP} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf, displayErrorMessageOrDefault, extractErrorMessage, stopPropagation} from "@/UtilityFunctions";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import NetworkIPApi, {NetworkIP, NetworkIPSupport} from "@/UCloud/NetworkIPApi";
import {
    CREATE_TAG, Permission, ResourceAclEntry,
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2,
} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {doNothing} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addProjectSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, providerIcon} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {addProjectListener} from "@/Project/ReduxState";
import {dialogStore} from "@/Dialog/DialogStore";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProductSelector} from "@/Products/Selector";
import {Box, Button, ExternalLink, Flex, Label, Text} from "@/ui-components";
import {FirewallTable, parseAndValidatePorts} from "./FirewallEditor";
import {compute, FindByStringId} from "@/UCloud";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {useEffect} from "react";
import {injectStyle} from "@/Unstyled";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {isAdminOrPI} from "@/Project";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {useProject} from "@/Project/cache";
import Routes from "@/Routes";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeOthers: true,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
    projectSwitcher: true,
};

const DUMMY_ENTRY_ID = "dummy";

const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2NetworkIP, NetworkIPSupport>>({
    globalTtl: 60_000
});

const PROJECT_CHANGE_LISTENER_ID = "public-links";
export function NetworkIPBrowse({opts}: {opts?: ResourceBrowserOpts<NetworkIP>}): React.ReactNode {
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

                browser.on("fetchFilters", () => [
                    dateRanges,
                    {
                        type: "input",
                        icon: "user",
                        key: "filterCreatedBy",
                        text: "Created by"
                    }
                ]);

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
                    const callbacks: ResourceBrowseCallbacks<NetworkIP> = {
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
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<NetworkIP>;

                    const operations = NetworkIPApi.retrieveOperations();
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

                                            browser.insertEntryIntoCurrentPage(networkIP);
                                            browser.renderRows();
                                            browser.selectAndShow(it => it === networkIP);

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

                                                dialogStore.success();
                                                browser.refresh();
                                            } catch (e) {
                                                snackbarStore.addFailure("Failed to activate public IP. " + extractErrorMessage(e), false);
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
            {switcher}
        </>}
    />
}

interface CreationWithFirewallProps {
    onCreate(product: ProductV2, ports: compute.PortRangeAndProto[], permissions: ResourceAclEntry[]): void;
    products: ProductV2NetworkIP[];
    onCancel: () => void;
}

const Container = injectStyle("ip-creation-container", k => `
    ${k} {
        display: flex;
        gap: 24px;
        flex-direction: column;
    }
`);


export function NetworkIpCreate({onCreate, onCancel, products}: CreationWithFirewallProps) {
    const [product, setSelectedProduct] = React.useState<ProductV2 | null>(null);
    const [ports, setPorts] = React.useState<compute.PortRangeAndProto[]>([])

    const onAddRow = React.useCallback((first: string, last: string, protocol: "TCP" | "UDP") => {
        const {valid, firstPort, lastPort} = parseAndValidatePorts(first, last);
        if (!valid) return;
        setPorts(ports => {
            ports.push({start: firstPort, end: lastPort, protocol});
            return [...ports];
        });
    }, []);

    const onRemoveRow = React.useCallback((idx: number) => {
        setPorts(p => p.filter((_, i) => i !== idx));
    }, []);

    useEffect(() => {
        if (products.length === 1) {
            setSelectedProduct(products[0]);
        }
    }, [products]);

    const [acl, setAcl] = React.useState<ResourceAclEntry[]>([]);
    const project = useProject().fetch();
    const projectId = project.id;

    let shortProviderId = "the selected provider";
    if (product) {
        shortProviderId = getShortProviderTitle(product.category.provider);
    }

    return (<div className={Container}>
        <Box>
            <Heading.h3>Create a public IP</Heading.h3>
            <Box mt={"8px"}>
                Public IP addresses provide direct access to specific jobs, useful for workloads not accessible via
                HTTP or remote desktop.{" "}
                <ExternalLink color={"primaryMain"} href={"/app" + Routes.resources.sshKeys()}>SSH access</ExternalLink>
                {" "}does <i>not</i> require a public IP.
            </Box>
        </Box>

        <Box>
            <Label>Choose a product<MandatoryField /></Label>
            <ProductSelector slim onSelect={setSelectedProduct} products={products} selected={product} />
            <div style={{color: "var(--textSecondary)"}}>This IP can be used with machines from <i>{shortProviderId}</i>.</div>
        </Box>

        <Box onSubmit={e => {e.stopPropagation(); e.preventDefault();}}>
            <Label>Configure the firewall</Label>
            <Box mt={"8px"} my={"16px"}>
                This controls how incoming traffic can access the IP address.
                Add at least one rule now; you can update it later on the <b>Properties</b> page.
            </Box>

            <FirewallTable showCard={false} isCreating didChange={false} onAddRow={onAddRow} onRemoveRow={onRemoveRow} openPorts={ports} />
        </Box>

        {!projectId || !isAdminOrPI(project.status.myRole) ? null : (<Box mb={"20px"}>
            <Label>Choose access</Label>
            <Box maxHeight="400px" overflowY="auto">
                <Text mb="12px">
                    By default, only you and the project administrators can use this public IP in new jobs.
                    You can modify these permissions later on the <b>Properties</b> page.
                </Text>
                <PermissionsTable
                    acl={acl}
                    anyGroupHasPermission={false}
                    showMissingPermissionHelp={false}
                    replaceWriteWithUse
                    warning="Warning"
                    title={"Public IP"}
                    updateAcl={async (group, permission) => {
                        const aclEntry = acl.find(it => it.entity["group"] === group);
                        if (aclEntry) {
                            if (aclEntry.entity.type === "project_group") {
                                if (permission) { // READ, EDIT, ADMIN
                                    aclEntry.permissions = [permission]
                                } else { // None
                                    aclEntry.permissions = [];
                                }
                            }
                        } else if (permission) {
                            acl.push({entity: {type: "project_group", group, projectId: projectId!}, permissions: [permission]})
                        }
                        setAcl([...acl]);
                    }}
                />
            </Box>
        </Box>)}

        <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"}>
            <Button color={"errorMain"} type="button" onClick={onCancel}>Cancel</Button>
            <Button
                color={"successMain"}
                disabled={product == null || ports.length === 0}
                onClick={() => onCreate(product!, ports, acl)}
            >
                Create
            </Button>
        </Flex>
    </div>);
}
