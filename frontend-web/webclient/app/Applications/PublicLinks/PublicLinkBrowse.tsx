import {Product, productTypeToIcon, ProductV2, ProductV2Ingress} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import PublicLinkApi, {PublicLink, PublicLinkSupport} from "@/UCloud/PublicLinkApi";
import {
    CREATE_TAG,
    Permission,
    Resource,
    ResourceAclEntry,
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2,
} from "@/UCloud/ResourceApi";
import {bulkRequestOf, createHTMLElements, doNothing, extractErrorMessage, stopPropagation, timestampUnixMs} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, providerIcon} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {dialogStore} from "@/Dialog/DialogStore";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {ProductSelector} from "@/Products/Selector";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {Client} from "@/Authentication/HttpClientInstance";
import Flex from "@/ui-components/Flex";
import Button from "@/ui-components/Button";
import Input from "@/ui-components/Input";
import Text from "@/ui-components/Text";
import {Box, Label} from "@/ui-components";
import {useProjectId} from "@/Project/Api";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import {addProjectListener, removeProjectListener} from "@/Project/ReduxState";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {LicenseSupport} from "@/UCloud/LicenseApi";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeUpdates: true,
    includeOthers: true,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

const RESOURCE_NAME = "Public Links";
const PROJECT_CHANGE_LISTENER_ID = "public-links";
export function PublicLinkBrowse({opts}: {opts?: ResourceBrowserOpts<PublicLink>}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<PublicLink> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    if (!opts?.embedded && !opts?.isModal) {
        usePage("Public links", SidebarTabId.RESOURCES);
    }
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useEffect(() => {
        return () => removeProjectListener(PROJECT_CHANGE_LISTENER_ID);
    }, []);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<PublicLink>(mount, RESOURCE_NAME, opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([
                    {name: "Domain"},
                    {name: "", columnWidth: 0},
                    {name: "", columnWidth: 0},
                    {name: "In use with", columnWidth: 250},
                ]);

                supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(PublicLinkApi));
                addProjectListener(PROJECT_CHANGE_LISTENER_ID, p => {
                    supportByProvider.retrieve(p ?? "", () => retrieveSupportV2(PublicLinkApi));
                });

                browser.setEmptyIcon(productTypeToIcon("INGRESS"));

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        if (opts?.selection) {
                            if (opts.selection.show(resource) === true) {
                                opts.selection.onClick(resource);
                            }
                            return;
                        }

                        navigate(AppRoutes.resource.properties("public-links", resource.id));
                        return;
                    }

                    callAPI(PublicLinkApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts?.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        PublicLinkApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => [dateRanges, {
                    key: "filterState",
                    type: "options",
                    clearable: true,
                    icon: "radioEmpty",
                    options: [{
                        color: "textPrimary",
                        icon: "hashtag",
                        text: "Preparing",
                        value: "PREPARING",
                    }, {
                        color: "textPrimary",
                        icon: "hashtag",
                        text: "Ready",
                        value: "READY"
                    }, {
                        color: "textPrimary",
                        icon: "hashtag",
                        text: "Unavailable",
                        value: "UNAVAILABLE"
                    }],
                    text: "Status"
                }, {
                        type: "input",
                        icon: "user",
                        key: "filterCreatedBy",
                        text: "Created by"
                    }
                ]);

                browser.on("startRenderPage", () => {
                    const inputField = browser.renameField;
                    const parent = inputField?.parentElement;
                    const prefix = browser.root.querySelector(".PREFIX");
                    const postfix = browser.root.querySelector(".POSTFIX");
                    if (!parent) return;
                    if (prefix) parent.removeChild(prefix);
                    if (postfix) parent.removeChild(postfix);
                });

                browser.on("endRenderPage", () => {
                    const inputField = browser.renameField;
                    const parent = inputField?.parentElement;
                    if (!parent || parent.hidden) return;
                    {
                        if (inputField?.style.display !== "none") {
                            const newPrefix = createHTMLElements({
                                tagType: "span",
                                className: "PREFIX",
                                innerText: browser.renamePrefix,
                                style: {
                                    position: "absolute",
                                    top: inputField.style.top,
                                    left: "10px"
                                }
                            });
                            parent.prepend(newPrefix);

                            const prefixRect = newPrefix.getBoundingClientRect();
                            inputField.style.left = prefixRect.width + 10 + "px";
                            const newPostfix = createHTMLElements({
                                tagType: "span",
                                className: "POSTFIX",
                                style: {
                                    position: "absolute",
                                    top: inputField.style.top,
                                    left: prefixRect.width + inputField.getBoundingClientRect().width + 10 + "px"
                                }
                            })
                            newPostfix.innerText = browser.renameSuffix;
                            parent.prepend(newPostfix);
                        }
                    }
                });

                browser.on("renderRow", (link, row, dims) => {
                    const {provider} = link.specification.product;

                    if (provider) {
                        const icon = providerIcon(link.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                        row.title.append(ResourceBrowser.defaultTitleRenderer(link.specification.domain, row));
                    }

                    if (link.status.boundTo.length === 1) {
                        const [boundTo] = link.status.boundTo;
                        row.stat3.innerText = boundTo;
                    }
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your public links...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No public link found with active filters.")
                            else e.reason.append("This workspace has no public links.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your public links.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your public links. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => {
                    const callbacks: ResourceBrowseCallbacks<PublicLink> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        navigate,
                        reload: () => browser.refresh(),
                        startCreation: undefined, // Note(Jonas): This is to disable normal creation operation
                        cancelCreation: doNothing,
                        startRenaming(): void {},
                        viewProperties(res: PublicLink): void {
                            navigate(AppRoutes.resource.properties("public-links", res.id));
                        },
                        commandLoading: false,
                        invokeCommand: callAPI,
                        api: PublicLinkApi,
                        isCreating: false
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    const operations = PublicLinkApi.retrieveOperations();
                    const create = operations.find(it => it.tag === CREATE_TAG);
                    if (create) {
                        create.enabled = () => true;
                        create.onClick = () => {
                            dialogStore.addDialog(
                                <ProductSelectorWithPermissions isPublicLink products={supportByProvider.retrieveFromCacheOnly(Client.projectId ?? "")?.newProducts ?? []} placeholder="Type url..." dummyEntry={dummyEntry} title={PublicLinkApi.title} onCreate={async (entry, product) => {
                                    try {
                                        const domain = entry.id;
                                        const response = (await callAPI(PublicLinkApi.create(bulkRequestOf({
                                            domain,
                                            product: {
                                                id: product.name,
                                                category: product.category.name,
                                                provider: product.category.provider
                                            }
                                        })))).responses[0] as unknown as FindByStringId;

                                        snackbarStore.addSuccess("Public link created for " + domain, false);

                                        /* Note(Jonas): I can't find the creation function in the backend,
                                           but either I'm sending it in the wrong way, or permissions are ignored when creating them initially.

                                           Seems to be ignored in the backend
                                        */
                                        if (response) {
                                            for (const permission of entry.permissions.others ?? []) {
                                                const fixedPermissions: Permission[] = permission.permissions.find(it => it === "EDIT") ? ["READ", "EDIT"] : ["READ"];
                                                const newEntry: ResourceAclEntry = {
                                                    entity: {type: "project_group", projectId: permission.entity["projectId"], group: permission.entity["group"]},
                                                    permissions: fixedPermissions
                                                };

                                                await callAPI(
                                                    PublicLinkApi.updateAcl(bulkRequestOf(
                                                        {
                                                            id: response.id,
                                                            added: [newEntry],
                                                            deleted: [permission.entity]
                                                        }
                                                    ))
                                                );
                                            };

                                            browser.insertEntryIntoCurrentPage({...entry, id: domain});
                                            dialogStore.success();
                                            browser.refresh();
                                        }
                                    } catch (e) {
                                        snackbarStore.addFailure("Failed to create public link. " + extractErrorMessage(e), false);
                                        browser.refresh();
                                        return;
                                    }
                                }} />, () => {}
                            );
                        }
                    }
                    return operations.filter(it => it.enabled(entries, callbacks as any, entries))
                });

                browser.on("pathToEntry", entry => entry.id);
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, [])

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

const dummyEntry: PublicLink = {
    createdAt: timestampUnixMs(),
    status: {boundTo: [], state: "PREPARING"},
    specification: {domain: "", product: {category: "", id: "", provider: ""}},
    id: "$$$__ingressBeingCreated__$$$$",
    owner: {createdBy: ""},
    updates: [],
    permissions: {myself: []},
};

const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2Ingress, PublicLinkSupport>>({
    globalTtl: 60_000
});

interface CreationWithProductSelectorProps<T extends Resource> {
    onCreate(entry: T, product: ProductV2, support?: PublicLinkSupport): Promise<void>;
    dummyEntry: T;
    title: string;
    placeholder: string;
    products: ProductV2[];
    isPublicLink?: boolean;
}

// Has to work for licenses also.
export function ProductSelectorWithPermissions<T extends Resource>({onCreate, dummyEntry, title, placeholder, isPublicLink, products}: CreationWithProductSelectorProps<T>) {
    const [product, setSelectedProduct] = React.useState<ProductV2 | null>(null);
    const [support, setSupport] = React.useState<PublicLinkSupport | LicenseSupport>();
    const [entryId, setEntryId] = React.useState("");
    const [acls, setAcls] = React.useState<ResourceAclEntry[]>([]);
    const projectId = useProjectId();

    const setProductAndSupport = React.useCallback((p: ProductV2) => {
        setSelectedProduct(p);

        const availableProducts = supportByProvider.retrieveFromCacheOnly(Client.projectId ?? "");

        for (const provider of Object.values(availableProducts?.productsByProvider ?? [])) {
            for (const {support} of provider) {
                if (support.product.id === p.category.name && support.product.provider === p.category.provider) {
                    setSupport(support);
                    return;
                }
            }
        }
    }, []);

    return (<form onKeyDown={stopPropagation} onSubmit={e => {
        e.preventDefault();
        if (!product) return;
        let domain = entryId;
        if (isPublicLink && support) {
            const s = support as PublicLinkSupport;
            domain = s.domainPrefix + entryId + s.domainSuffix;
        }
        onCreate({...dummyEntry, id: domain, permissions: {others: acls}} as T, product);
    }}>
        <ProductSelector onSelect={setProductAndSupport} products={products} selected={product} />
        {!product ? null : (<Box mt="12px">
            {isPublicLink ? <TabbedCard>
                <TabbedCardTab name="Public link URL" icon="heroLink">
                    <Input placeholder={placeholder} onChange={e => setEntryId(e.target.value)} />
                    {entryId ? <Text mt="12px"> Will be available at <b>{(support?.["domainPrefix"] ?? "") + entryId + (support?.["domainSuffix"] ?? "")}</b></Text> : null}
                </TabbedCardTab>
            </TabbedCard> : null}
            {!Client.hasActiveProject ? null : (<Box mt="12px">
                <TabbedCard>
                    <TabbedCardTab name={"Permissions"} icon={"heroShare"}>
                        <Box mt="12px" maxHeight="400px" overflowY="auto">
                            <Text mb="12px">This is the permissions for the {title.toLocaleLowerCase()}. This can be changed later through 'Properties'.</Text>
                            <PermissionsTable acl={acls} anyGroupHasPermission={false} showMissingPermissionHelp={false} title={title} updateAcl={async (group, permission) => {
                                const acl = acls.find(it => it.entity["group"] === group);
                                if (acl) {
                                    if (acl.entity.type === "project_group") {
                                        if (permission) { // READ, EDIT, ADMIN
                                            acl.permissions = [permission]
                                        } else { // None
                                            acl.permissions = [];
                                        }
                                    }
                                } else if (permission) {
                                    acls.push({entity: {type: "project_group", group, projectId: projectId!}, permissions: [permission]})
                                }
                                setAcls([...acls]);
                            }} warning="Warning" />
                        </Box>
                    </TabbedCardTab>
                </TabbedCard>
            </Box>)}
        </Box>)}
        <Flex mt="12px" justifyContent="end"><Button disabled={product == null || (isPublicLink && !entryId)} type="submit">Create</Button></Flex>
    </form>);
}
