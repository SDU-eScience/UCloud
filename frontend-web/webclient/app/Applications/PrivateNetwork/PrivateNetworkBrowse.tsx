import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf, doNothing, extractErrorMessage} from "@/UtilityFunctions";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import PrivateNetworkApi, {PrivateNetwork, PrivateNetworkSupport} from "@/UCloud/PrivateNetworkApi";
import {
    CREATE_TAG,
    Permission,
    placeholderProduct,
    ResourceAclEntry,
    ResourceBrowseCallbacks, retrieveSupportV2, SupportByProviderV2,
} from "@/UCloud/ResourceApi";
import {accounting, FindByStringId} from "@/UCloud";
import {
    addProjectSwitcherInPortal,
    checkIsWorkspaceAdmin,
    dateRangeFilters,
    EmptyReasonTag,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts,
} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router-dom";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {dialogStore} from "@/Dialog/DialogStore";
import {Box, Button, Flex, Input, Label, Text} from "@/ui-components";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {injectStyle} from "@/Unstyled";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import {isAdminOrPI} from "@/Project";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import {productTypeToIcon, ProductV2, ProductV2PrivateNetwork} from "@/Accounting";
import {ProductSelector} from "@/Products/Selector";
import {Client} from "@/Authentication/HttpClientInstance";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import ProductReference = accounting.ProductReference;
import {useEffect} from "react";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeOthers: true,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
    projectSwitcher: true,
};

const DUMMY_ENTRY_ID = "dummy-private-network";

const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport>>({
    globalTtl: 60_000
});

export function PrivateNetworkBrowse({opts}: { opts?: ResourceBrowserOpts<PrivateNetwork> }): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<PrivateNetwork> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    usePage("Private networks", SidebarTabId.RESOURCES);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<PrivateNetwork>(mount, "Private networks", opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([
                    {name: "Name"},
                    {name: "Subdomain", columnWidth: 220},
                    {name: "Members", columnWidth: 220},
                    {name: "", columnWidth: 0},
                ]);

                const dummyEntry: PrivateNetwork = {
                    id: DUMMY_ENTRY_ID,
                    specification: {name: "", subdomain: "", product: placeholderProduct()},
                    createdAt: new Date().getTime(),
                    owner: {createdBy: ""},
                    status: {members: []},
                    permissions: {myself: []},
                    updates: [],
                };

                browser.on("open", (_oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("private-networks", resource.id));
                        return;
                    }

                    callAPI(PrivateNetworkApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts?.additionalFilters,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.on("unhandledShortcut", () => {
                });

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        PrivateNetworkApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                        })
                    );

                    if (path !== browser.currentPath) return;
                    browser.registerPage(result, path, false);
                });

                browser.setEmptyIcon(productTypeToIcon("PRIVATE_NETWORK"));

                browser.on("fetchFilters", () => [
                    dateRanges,
                    {
                        type: "input",
                        icon: "user",
                        key: "filterCreatedBy",
                        text: "Created by"
                    }
                ]);

                browser.on("renderRow", (network, row) => {
                    if (network.id !== DUMMY_ENTRY_ID) {
                        row.title.append(ResourceBrowser.defaultTitleRenderer(network.specification.name || network.id, row));
                    }

                    row.stat1.textContent = network.specification.subdomain;

                    if (opts?.selection) {
                        const useButton = browser.defaultButtonRenderer(opts.selection, network);
                        if (useButton) {
                            row.stat3.append(useButton);
                        }
                    } else {
                        row.stat3.textContent = network.status.members.length === 0
                            ? "Not in use"
                            : network.status.members.join(", ");
                    }
                });

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your private networks...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0) {
                                e.reason.append("No private network found with active filters.");
                            } else {
                                e.reason.append("This workspace has no private networks.");
                            }
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your private networks.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your private networks. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => {
                    const callbacks: ResourceBrowseCallbacks<PrivateNetwork> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        navigate,
                        reload: () => browser.refresh(),
                        cancelCreation: doNothing,
                        viewProperties(res: PrivateNetwork): void {
                            navigate(AppRoutes.resource.properties("private-networks", res.id));
                        },
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        api: PrivateNetworkApi,
                        isCreating: false,
                    };
                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<PrivateNetwork>;

                    const operations = PrivateNetworkApi.retrieveOperations();
                    const create = operations.find(it => it.tag === CREATE_TAG);
                    if (create) {
                        create.enabled = () => true;
                        create.onClick = async () => {
                            const products = (await supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(PrivateNetworkApi))).newProducts;
                            dialogStore.addDialog(
                                <PrivateNetworkCreate
                                    products={products}
                                    onCancel={() => {
                                        dialogStore.failure();
                                    }}
                                    onCreate={async (name, subdomain, permissions, product) => {
                                        console.log({name, subdomain, permissions, product})
                                        const network: PrivateNetwork = {
                                            ...dummyEntry,
                                            id: "",
                                            specification: {name, subdomain, product},
                                            owner: {createdBy: ""},
                                        };

                                        browser.insertEntryIntoCurrentPage(network);
                                        browser.renderRows();
                                        browser.selectAndShow(it => it === network);

                                        try {
                                            const response = (await callAPI(
                                                PrivateNetworkApi.create(
                                                    bulkRequestOf(network.specification)
                                                )
                                            )).responses[0] as unknown as FindByStringId | PrivateNetwork;

                                            const id = (response as FindByStringId).id ?? (response as PrivateNetwork).id;
                                            if (!id) {
                                                throw new Error("Missing id in create response");
                                            }
                                            network.id = id;

                                            for (const permission of permissions) {
                                                const fixedPermissions: Permission[] =
                                                    permission.permissions.find(it => it === "EDIT") ? ["READ", "EDIT"] : ["READ"];
                                                const newEntry: ResourceAclEntry = {
                                                    entity: {
                                                        type: "project_group",
                                                        projectId: permission.entity["projectId"],
                                                        group: permission.entity["group"],
                                                    },
                                                    permissions: fixedPermissions,
                                                };

                                                await callAPI(
                                                    PrivateNetworkApi.updateAcl(bulkRequestOf(
                                                        {
                                                            id,
                                                            added: [newEntry],
                                                            deleted: [permission.entity],
                                                        }
                                                    ))
                                                );
                                            }

                                            dialogStore.success();
                                            browser.refresh();
                                        } catch (e) {
                                            snackbarStore.addFailure("Failed to create private network. " + extractErrorMessage(e), false);
                                            browser.refresh();
                                        }
                                    }}
                                />,
                                () => {
                                },
                                true,
                                slimModalStyle,
                            );
                        };
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
            <div ref={mountRef}/>
            {switcher}
        </>}
    />;
}

interface PrivateNetworkCreateProps {
    products: ProductV2PrivateNetwork[];

    onCreate(name: string, subdomain: string, permissions: ResourceAclEntry[], product: ProductReference): void;

    onCancel: () => void;
}

const Container = injectStyle("private-network-creation-container", k => `
    ${k} {
        display: flex;
        gap: 12px;
        flex-direction: column;
        margin-bottom: 24px;
    }
`);

function PrivateNetworkCreate({onCreate, onCancel, products}: PrivateNetworkCreateProps) {
    const [product, setSelectedProduct] = React.useState<ProductV2 | null>(null);
    const [name, setName] = React.useState("");
    const [subdomain, setSubdomain] = React.useState("");
    const [acl, setAcl] = React.useState<ResourceAclEntry[]>([]);
    const project = useProject().fetch();
    const projectId = useProjectId();

    const isNameValid = name.trim().length > 0;
    const isSubdomainValid = subdomain.trim().length > 0 && !subdomain.includes(".");

    let shortProviderId = "the selected provider";
    if (product) {
        shortProviderId = getShortProviderTitle(product.category.provider);
    }

    useEffect(() => {
        if (products.length === 1) {
            setSelectedProduct(products[0]);
        }
    }, [products]);

    return (<div>
        <div className={Container}>
            <Box>
                <Heading.h3>Create a private network</Heading.h3>
                <Box mt={"8px"}>
                    Private networks connect your jobs within an isolated internal network.
                    They are only accessible within your workspace.
                </Box>
            </Box>

            <Box>
                <Label>Name<MandatoryField/></Label>
                <Input
                    autoFocus
                    placeholder={"My private network"}
                    value={name}
                    onChange={e => setName(e.target.value)}
                />
            </Box>

            <Box>
                <Label>Subdomain<MandatoryField/></Label>
                <Input
                    placeholder={"my-network"}
                    value={subdomain}
                    pattern={"^([A-Za-z]|[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9])$"}
                    onChange={e => setSubdomain(e.target.value.toLowerCase())}
                />
                <Text mt="8px" color={"textSecondary"}>
                    Use letters, numbers, and hyphens only. Dots are not allowed.
                </Text>
            </Box>

            <Box>
                <Label>Choose a product<MandatoryField/></Label>
                <ProductSelector slim onSelect={setSelectedProduct} products={products} selected={product}/>
                <div style={{color: "var(--textSecondary)"}}>This network can be used with machines
                    from <i>{shortProviderId}</i>.
                </div>
            </Box>

            {!projectId || !isAdminOrPI(project.status.myRole) ? null : (<Box mb={"20px"}>
                <Label>Choose access</Label>
                <Box maxHeight="400px" overflowY="auto">
                    <Text mb="12px">
                        By default, only you and the project administrators can use this private network in new jobs.
                        You can modify these permissions later on the <b>Properties</b> page.
                    </Text>
                    <PermissionsTable
                        acl={acl}
                        anyGroupHasPermission={false}
                        showMissingPermissionHelp={false}
                        replaceWriteWithUse
                        warning="Warning"
                        title={"Private network"}
                        updateAcl={async (group, permission) => {
                            const aclEntry = acl.find(it => it.entity["group"] === group);
                            if (aclEntry) {
                                if (aclEntry.entity.type === "project_group") {
                                    aclEntry.permissions = permission ? [permission] : [];
                                }
                            } else if (permission) {
                                acl.push({
                                    entity: {type: "project_group", group, projectId: projectId!},
                                    permissions: [permission],
                                });
                            }
                            setAcl([...acl]);
                        }}
                    />
                </Box>
            </Box>)}
        </div>

        <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"0 -20px -20px -20px"} background={"var(--dialogToolbar)"}
              gap={"8px"}>
            <Button color={"errorMain"} type="button" onClick={onCancel}>Cancel</Button>
            <Button
                color={"successMain"}
                disabled={!isNameValid || !isSubdomainValid}
                onClick={() => {
                    if (!isNameValid || !isSubdomainValid) {
                        snackbarStore.addFailure("Please provide a valid name and subdomain", false);
                        return;
                    }
                    if (!product) {
                        snackbarStore.addFailure("Please select a product", false);
                        return;
                    }
                    onCreate(name.trim(),
                        subdomain.trim().toLowerCase(),
                        acl,
                        {id: product.name, category: product.category.name, provider: product.category.provider},
                    );
                }}
            >
                Create
            </Button>
        </Flex>
    </div>);
}
