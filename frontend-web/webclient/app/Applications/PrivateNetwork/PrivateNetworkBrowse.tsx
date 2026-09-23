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
    ResourceBrowseHeaderControls,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts,
    createProjectSwitcherPortal,
} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router-dom";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {dialogStore} from "@/Dialog/DialogStore";
import {Box, Button, Flex, Input, Label, Text} from "@/ui-components";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {injectStyle} from "@/Unstyled";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import {isAdminOrPI} from "@/Project";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import {Product, productTypeToIcon, ProductV2, ProductV2PrivateNetwork} from "@/Accounting";
import {ProductSelector} from "@/Products/Selector";
import {Client} from "@/Authentication/HttpClientInstance";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import ProductReference = accounting.ProductReference;
import {useEffect} from "react";
import {sendFailureNotification} from "@/Notifications";

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
    search: true,
};

const DUMMY_ENTRY_ID = "dummy-private-network";

interface ParsedPrivateNetworkCidr {
    first: number;
    last: number;
    prefixLength: number;
}

function parsePrivateNetworkCidr(value: string): ParsedPrivateNetworkCidr | null {
    const parts = value.split("/");
    if (parts.length !== 2) {
        return null;
    }

    const addressIsValid = /^(?:0|[1-9]\d{0,2})\.(?:0|[1-9]\d{0,2})\.(?:0|[1-9]\d{0,2})\.(?:0|[1-9]\d{0,2})$/.test(parts[0]);
    if (!addressIsValid) {
        return null;
    }

    const octets = parts[0].split(".").map(Number);
    const octetsAreValid = octets.every(octet => octet <= 255);
    const prefixIsValid = /^(?:[0-9]|[12]\d|3[0-2])$/.test(parts[1]);
    if (!octetsAreValid || !prefixIsValid) {
        return null;
    }

    const prefixLength = Number(parts[1]);
    const blockSize = 2 ** (32 - prefixLength);
    const first = octets.reduce((value, octet) => value * 256 + octet, 0);
    if (first % blockSize !== 0) {
        return null;
    }

    return {
        first,
        last: first + blockSize - 1,
        prefixLength,
    };
}

const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport>>({
    globalTtl: 60_000
});

export function PrivateNetworkBrowse({
    opts,
    headerControls,
}: {
    opts?: ResourceBrowserOpts<PrivateNetwork>;
    headerControls?: ResourceBrowseHeaderControls;
}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<PrivateNetwork> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    if (!opts?.isModal) usePage("Private networks", SidebarTabId.RESOURCES);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    React.useEffect(() => {
        headerControls?.setRefresh?.(() => browserRef.current?.refresh());
        return () => {
            headerControls?.setRefresh?.(undefined);
        };
    }, [headerControls]);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<PrivateNetwork>(mount, "Private networks", opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([
                    {name: "Name"},
                    {name: "Subdomain", columnWidth: 220},
                    {name: "", columnWidth: 0},
                    {name: "", columnWidth: 0},
                ]);

                const dummyEntry: PrivateNetwork = {
                    id: DUMMY_ENTRY_ID,
                    specification: {name: "", product: placeholderProduct()},
                    createdAt: new Date().getTime(),
                    owner: {createdBy: ""},
                    status: {subdomain: "", members: []},
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

                    row.stat1.textContent = network.status.subdomain ?? "";

                    if (opts?.selection) {
                        const useButton = browser.defaultButtonRenderer(opts.selection, network);
                        if (useButton) {
                            row.stat2.append(useButton);
                        }
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
                    const callbacks: ResourceBrowseCallbacks<PrivateNetwork, Product> = {
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
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<PrivateNetwork, Product>;

                    const actions = PrivateNetworkApi.retrieveActions();
                    if (!Array.isArray(actions)) return actions;
                    const operations = actions;
                    const create = operations.find(it => it.tag === CREATE_TAG);
                    if (create) {
                        create.enabled = () => true;
                        create.onClick = async () => {
                            const support = await supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(PrivateNetworkApi));
                            dialogStore.addDialog(
                                <PrivateNetworkCreate
                                    products={support.newProducts}
                                    support={support}
                                    onCancel={() => {
                                        dialogStore.failure();
                                    }}
                                    onCreate={async (name, cidr, permissions, product) => {
                                        const network: PrivateNetwork = {
                                            ...dummyEntry,
                                            id: "",
                                            specification: {name, product, ...(cidr ? {cidr} : {})},
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
                                        } catch (e: any) {
                                            sendFailureNotification("Failed to create private network. " + extractErrorMessage(e));
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

                browser.on("search", async query => {
                    browser.searchQuery = query;
                    browser.currentPath = "/search";
                    browser.cachedData["/search"] = [];
                    browser.renderRows();
                    browser.renderOperations();
                    callAPI(PrivateNetworkApi.search({
                        query,
                        itemsPerPage: 250,
                        flags: {},
                    })).then(res => {
                        if (browser.currentPath !== "/search") return;
                        browser.registerPage(res, "/search", true);
                        browser.renderRows();
                        browser.renderBreadcrumbs();
                    })
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
    />;
}

interface PrivateNetworkCreateProps {
    products: ProductV2PrivateNetwork[];
    support: SupportByProviderV2<ProductV2PrivateNetwork, PrivateNetworkSupport>;

    onCreate(name: string, cidr: string, permissions: ResourceAclEntry[], product: ProductReference): void;

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

function PrivateNetworkCreate({onCreate, onCancel, products, support}: PrivateNetworkCreateProps) {
    const [product, setSelectedProduct] = React.useState<ProductV2 | null>(null);
    const [name, setName] = React.useState("");
    const [cidr, setCidr] = React.useState("");
    const [acl, setAcl] = React.useState<ResourceAclEntry[]>([]);
    const project = useProject().fetch();
    const projectId = useProjectId();

    const selectedSupport = product
        ? support.productsByProvider[product.category.provider]?.find(item =>
            item.product.name === product.name && item.product.category.name === product.category.name
        )?.support
        : undefined;
    const addressPools = selectedSupport?.addressPools ?? [];
    const excludedRanges = selectedSupport?.excludedRanges ?? [];
    const minPrefixLength = selectedSupport?.minPrefixLength ?? 16;
    const maxPrefixLength = selectedSupport?.maxPrefixLength ?? 24;

    const isNameValid = name.trim().length > 0;
    const parsedCidr = parsePrivateNetworkCidr(cidr.trim());
    const parsedPools = addressPools
        .map(parsePrivateNetworkCidr)
        .filter((range): range is ParsedPrivateNetworkCidr => range !== null);
    const parsedExcludedRanges = excludedRanges
        .map(excludedRange => ({cidr: excludedRange, range: parsePrivateNetworkCidr(excludedRange)}))
        .filter((item): item is {cidr: string, range: ParsedPrivateNetworkCidr} => item.range !== null);
    const prefixIsSupported = parsedCidr !== null &&
        parsedCidr.prefixLength >= minPrefixLength && parsedCidr.prefixLength <= maxPrefixLength;
    const cidrIsInsidePool = parsedCidr !== null && (addressPools.length === 0 || parsedPools.some(pool =>
        parsedCidr.first >= pool.first && parsedCidr.last <= pool.last
    ));
    const overlappingExcludedRange = parsedCidr === null ? undefined : parsedExcludedRanges.find(({range}) =>
        parsedCidr.first <= range.last && range.first <= parsedCidr.last
    );
    const isCidrValid = cidr.trim() === "" || (
        parsedCidr !== null && prefixIsSupported && cidrIsInsidePool && overlappingExcludedRange === undefined
    );

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
                <Label>Name<MandatoryField /></Label>
                <Input
                    autoFocus
                    placeholder={"My private network"}
                    value={name}
                    onChange={e => setName(e.target.value)}
                />
                <Text mt="8px" color={"textSecondary"}>
                    The subdomain is based on this name and gets a random suffix if needed.
                </Text>
            </Box>

            <Box>
                <Label>Choose a product<MandatoryField /></Label>
                <ProductSelector slim onSelect={setSelectedProduct} products={products} selected={product} />
                <div style={{color: "var(--textSecondary)"}}>This network can be used with machines
                    from <i>{shortProviderId}</i>.
                </div>
            </Box>

            <Box>
                <Label>Address range (optional)</Label>
                <Input placeholder="For example, 10.20.0.0/24" value={cidr} onChange={e => setCidr(e.target.value)} />
                {addressPools.length > 0 ? <Box mt="8px">
                    <Text mt={"8px"} color="textSecondary">Can be left blank and one will automatically be allocated for you.</Text>
                    <Text mt={"8px"} color="textSecondary">Available ranges</Text>
                    <ul style={{margin: "4px 0", paddingLeft: "20px", color: "var(--textSecondary)"}}>
                        {addressPools.map(addressPool => <li key={addressPool}>{addressPool}</li>)}
                    </ul>
                </Box> : null}
                {excludedRanges.length > 0 ? <Box mt="8px">
                    <Text color="textSecondary">Unavailable ranges</Text>
                    <ul style={{margin: "4px 0", paddingLeft: "20px", color: "var(--textSecondary)"}}>
                        {excludedRanges.map(excludedRange => <li key={excludedRange}>{excludedRange}</li>)}
                    </ul>
                </Box> : null}
                <Text mt="8px" color="textSecondary">
                    Subnet size must be between /{minPrefixLength} and /{maxPrefixLength}.
                </Text>
                {!isCidrValid ? <Text mt="8px" color="errorMain">
                    {parsedCidr === null ?
                        "Enter a valid address range, such as 10.20.0.0/24." :
                        !prefixIsSupported ?
                            `Choose a range between /${minPrefixLength} and /${maxPrefixLength} in size.` :
                            !cidrIsInsidePool ?
                                "Choose a range within one of the available ranges above." :
                                `This range includes an unavailable area: ${overlappingExcludedRange?.cidr}.`}
                </Text> : null}
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
                disabled={!isNameValid || !isCidrValid}
                onClick={() => {
                    if (!isNameValid || !isCidrValid) {
                        sendFailureNotification("Please provide a valid name and address range");
                        return;
                    }
                    if (!product) {
                        sendFailureNotification("Please select a product");
                        return;
                    }
                    onCreate(name.trim(),
                        cidr.trim(),
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
