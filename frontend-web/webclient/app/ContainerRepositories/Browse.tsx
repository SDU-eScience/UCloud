import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router-dom";
import {
    EmptyReasonTag,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts,
    ResourceBrowseHeaderControls,
    addProjectSwitcherInPortal,
    checkIsWorkspaceAdmin,
    createProjectSwitcherPortal,
    providerIcon,
} from "@/ui-components/ResourceBrowser";
import MainContainer from "@/ui-components/MainContainer";
import {callAPI} from "@/Authentication/DataHook";
import {
    api as ContainerRepositoriesApi,
    ContainerRepository,
    ContainerRepositorySpecification,
    ContainerRepositorySupport,
} from "@/UCloud/ContainerRepositoriesApi";
import {
    CREATE_TAG,
    Permission,
    ResourceAclEntry,
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2,
    supportV2ProductMatch,
} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {FindByStringId} from "@/UCloud";
import {ProductStorage, ProductV2, ProductV2Storage} from "@/Accounting";
import {bulkRequestOf, doNothing, extractErrorCode, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Client} from "@/Authentication/HttpClientInstance";
import {addProjectListener, removeProjectListener} from "@/Project/ReduxState";
import {dialogStore} from "@/Dialog/DialogStore";
import {ProductSelector} from "@/Products/Selector";
import {Box, Button, Flex, Input, Label, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {useProject} from "@/Project/cache";
import {isAdminOrPI} from "@/Project";
import {useProjectId} from "@/Project/Api";
import {injectStyle} from "@/Unstyled";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {dateToString} from "@/Utilities/DateUtilities";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeOthers: true,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: false,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    projectSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

const RESOURCE_NAME = "Container repositories";
const PROJECT_CHANGE_LISTENER_ID = "container-repositories";
const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2Storage, ContainerRepositorySupport>>({
    globalTtl: 60_000,
});

export default function ContainerRepositoryBrowse({
    opts,
    headerControls,
}: {
    opts?: ResourceBrowserOpts<ContainerRepository>;
    headerControls?: ResourceBrowseHeaderControls;
}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ContainerRepository> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    usePage("Container registry", SidebarTabId.FILES);

    React.useEffect(() => {
        headerControls?.setRefresh?.(() => browserRef.current?.refresh());
        return () => headerControls?.setRefresh?.(undefined);
    }, [headerControls]);

    React.useEffect(() => {
        return () => removeProjectListener(PROJECT_CHANGE_LISTENER_ID);
    }, []);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<ContainerRepository>(mount, RESOURCE_NAME, opts).init(
                browserRef,
                FEATURES,
                "/",
                browser => {
                    browser.setColumns([
                        {name: "Repository name", sortById: "name"},
                        {name: "Provider", columnWidth: 150},
                        {name: "Created by", sortById: "createdBy", columnWidth: 250},
                        {name: "Created at", sortById: "createdAt", columnWidth: 160},
                    ]);

                    const fetchSupport = (projectId?: string) => {
                        supportByProvider
                            .retrieve(projectId ?? "", () => retrieveSupportV2(ContainerRepositoriesApi))
                            .then(() => browser.renderOperations());
                    };

                    fetchSupport(Client.projectId);
                    addProjectListener(PROJECT_CHANGE_LISTENER_ID, projectId => fetchSupport(projectId ?? undefined));

                    browser.on("skipOpen", (_oldPath, _newPath, resource) => resource != null);

                    browser.on("open", (_oldPath, newPath, resource) => {
                        if (resource) return;
                        callAPI(ContainerRepositoriesApi.browse({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                        })).then(result => {
                            browser.registerPage(result, newPath, true);
                            browser.renderRows();
                        });
                    });

                    browser.on("wantToFetchNextPage", async path => {
                        const result = await callAPI(ContainerRepositoriesApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                        }));

                        if (path !== browser.currentPath) return;
                        browser.registerPage(result, path, false);
                    });

                    browser.on("renderRow", (repository, row) => {
                        const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                        row.title.append(icon);
                        ResourceBrowser.icons.renderIcon({
                            name: "heroArchiveBox",
                            color: "textPrimary",
                            color2: "textPrimary",
                            height: 64,
                            width: 64,
                        }).then(setIcon);

                        row.title.append(ResourceBrowser.defaultTitleRenderer(repository.specification.name, row));
                        const provider = providerIcon(repository.specification.product.provider);
                        provider.style.marginRight = "8px";
                        row.stat1.append(provider);
                        row.stat1.append(getShortProviderTitle(repository.specification.product.provider));
                        if (repository.owner.createdBy !== "_ucloud") {
                            row.stat2.append(ResourceBrowser.defaultTitleRenderer(repository.owner.createdBy, row));
                        }
                        row.stat3.innerText = dateToString(repository.createdAt);
                    });

                    browser.on("generateBreadcrumbs", () => [{title: RESOURCE_NAME, absolutePath: "/"}]);
                    browser.on("pathToEntry", repository => repository.id);
                    browser.on("nameOfEntry", repository => repository.specification.name);
                    browser.on("sort", page => page.sort((a, b) =>
                        a.specification.name.localeCompare(b.specification.name)));
                    browser.on("unhandledShortcut", () => {});
                    browser.setEmptyIcon("heroArchiveBox");

                    browser.on("renderEmptyPage", reason => {
                        const emptyPage = browser.emptyPageElement;
                        switch (reason.tag) {
                            case EmptyReasonTag.LOADING:
                                emptyPage.reason.append("We are fetching your container repositories...");
                                break;
                            case EmptyReasonTag.EMPTY:
                                emptyPage.reason.append("This workspace has no container repositories.");
                                break;
                            case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS:
                                emptyPage.reason.append("We could not find any container repositories.");
                                emptyPage.providerReason.append(reason.information ?? "");
                                break;
                            case EmptyReasonTag.UNABLE_TO_FULFILL:
                                emptyPage.reason.append("We are currently unable to show your container repositories. Try again later.");
                                emptyPage.providerReason.append(reason.information ?? "");
                                break;
                        }
                    });

                    browser.on("fetchOperationsCallback", () => {
                        const callbacks: ResourceBrowseCallbacks<
                            ContainerRepository,
                            ProductStorage,
                            ContainerRepositorySpecification
                        > = {
                            supportByProvider: supportByProvider.retrieveFromCacheOnly(Client.projectId ?? "") ?? {
                                productsByProvider: {},
                            },
                            dispatch,
                            isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                            navigate,
                            reload: () => browser.refresh(),
                            startCreation: doNothing,
                            cancelCreation: doNothing,
                            commandLoading: false,
                            invokeCommand: callAPI,
                            api: ContainerRepositoriesApi,
                            isCreating: false,
                        };
                        return callbacks;
                    });

                    browser.on("fetchOperations", () => {
                        const selected = browser.findSelectedEntries();
                        const callbacks = browser.dispatchMessage(
                            "fetchOperationsCallback",
                            fn => fn(),
                        ) as ResourceBrowseCallbacks<ContainerRepository, ProductStorage, ContainerRepositorySpecification>;
                        const operations = ContainerRepositoriesApi.retrieveOperations();
                        const create = Array.isArray(operations)
                            ? operations.find(operation => operation.tag === CREATE_TAG)
                            : undefined;
                        if (create) create.onClick = () => openCreation(browser);
                        return Array.isArray(operations)
                            ? operations.filter(operation => operation.enabled(selected, callbacks, selected))
                            : operations;
                    });
                },
            );
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => browserRef.current?.refresh());
    }

    return <MainContainer main={<>
        <div ref={mountRef} />
        {headerControls?.projectSwitcherTarget
            ? createProjectSwitcherPortal(headerControls.projectSwitcherTarget)
            : switcher}
    </>} />;
}

async function openCreation(browser: ResourceBrowser<ContainerRepository>): Promise<void> {
    const support = await supportByProvider.retrieve(
        Client.projectId ?? "",
        () => retrieveSupportV2(ContainerRepositoriesApi),
    );
    const products: ProductV2Storage[] = [];
    for (const entries of Object.values(support.productsByProvider)) {
        for (const entry of entries) {
            if ((entry.support as ContainerRepositorySupport).containerRepositories !== true) continue;
            if ((entry.support as ContainerRepositorySupport).collection?.usersCanCreate !== true) continue;
            products.push(supportV2ProductMatch(entry.product, support));
        }
    }

    dialogStore.addDialog(
        <ContainerRepositoryCreate
            products={products}
            onCancel={() => dialogStore.failure()}
            onCreate={async (product, name, permissions) => {
                const productReference = {
                    id: product.name,
                    category: product.category.name,
                    provider: product.category.provider,
                };
                const repositoryBeingCreated = {
                    owner: {createdBy: Client.username ?? ""},
                    updates: [],
                    createdAt: timestampUnixMs(),
                    status: {},
                    id: name,
                    specification: {name, product: productReference},
                    permissions: {myself: []},
                } as ContainerRepository;

                browser.insertEntryIntoCurrentPage(repositoryBeingCreated);
                browser.renderRows();

                try {
                    const response = (await callAPI(ContainerRepositoriesApi.create(bulkRequestOf({
                        name,
                        product: productReference,
                    })))).responses[0] as unknown as FindByStringId;
                    repositoryBeingCreated.id = response.id;

                    for (const permission of permissions) {
                        const fixedPermissions: Permission[] = permission.permissions.includes("EDIT")
                            ? ["READ", "EDIT"]
                            : ["READ"];
                        const newEntry: ResourceAclEntry = {
                            entity: {
                                type: "project_group",
                                projectId: permission.entity["projectId"],
                                group: permission.entity["group"],
                            },
                            permissions: fixedPermissions,
                        };
                        await callAPI(ContainerRepositoriesApi.updateAcl(bulkRequestOf({
                            id: response.id,
                            added: [newEntry],
                            deleted: [permission.entity],
                        })));
                    }

                    dialogStore.success();
                    browser.refresh();
                } catch (error: any) {
                    browser.refresh();
                    throw error;
                }
            }}
        />,
        doNothing,
        true,
        slimModalStyle,
    );
}

interface ContainerRepositoryCreateProps {
    products: ProductV2Storage[];
    onCreate(product: ProductV2, name: string, permissions: ResourceAclEntry[]): Promise<void>;
    onCancel(): void;
}

const CreationContainer = injectStyle("container-repository-creation-container", k => `
    ${k} {
        display: flex;
        gap: 24px;
        flex-direction: column;
    }
`);

function ContainerRepositoryCreate({products, onCreate, onCancel}: ContainerRepositoryCreateProps): React.ReactNode {
    const [product, setProduct] = React.useState<ProductV2 | null>(null);
    const [name, setName] = React.useState("");
    const [acl, setAcl] = React.useState<ResourceAclEntry[]>([]);
    const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
    const project = useProject().fetch();
    const projectId = useProjectId();

    React.useEffect(() => {
        if (products.length === 1) setProduct(products[0]);
    }, [products]);

    return <form className={CreationContainer} onSubmit={async event => {
        event.preventDefault();
        event.stopPropagation();
        if (product && name.trim()) {
            try {
                await onCreate(product, name.trim(), acl);
            } catch (error: any) {
                const message = extractErrorCode(error) === 409
                    ? "The name selected must be globally unique at the service provider."
                    : "Failed to create container repository. " + extractErrorMessage(error);
                setErrorMessage(message);
            }
        }
    }}>
        <Box>
            <Heading.h3>Create a container repository</Heading.h3>
            <Box mt="8px">
                Container repositories store container images for your projects and can be used to control access to them.
            </Box>
        </Box>

        <Label>
            Choose a name<MandatoryField />
            <Input
                autoFocus
                placeholder="Enter repository name..."
                onChange={event => {
                    setName(event.target.value);
                    setErrorMessage(null);
                }}
                onKeyDown={event => {
                    if (event.key !== "Escape") event.stopPropagation();
                }}
            />
        </Label>

        {errorMessage ? <Text color="errorMain">{errorMessage}</Text> : null}

        <Box>
            <Label>Choose a product<MandatoryField /></Label>
            <ProductSelector products={products} selected={product} onSelect={setProduct} slim />
        </Box>

        {!projectId || !isAdminOrPI(project.status.myRole) ? null : <Box>
            <Label>Choose access</Label>
            <Box maxHeight="400px" overflowY="auto">
                <p>
                    By default, only you and the project administrators can use this repository. You can modify these
                    permissions later.
                </p>
                <PermissionsTable
                    acl={acl}
                    anyGroupHasPermission={false}
                    showMissingPermissionHelp={false}
                    warning="Warning"
                    title="Container repository"
                    readLabel="Pull"
                    readIcon="heroArrowDownTray"
                    writeLabel="Push"
                    writeIcon="heroArrowUpTray"
                    updateAcl={async (group, permission) => {
                        const aclEntry = acl.find(entry => entry.entity["group"] === group);
                        if (aclEntry) {
                            aclEntry.permissions = permission ? [permission] : [];
                        } else if (permission) {
                            acl.push({
                                entity: {type: "project_group", group, projectId},
                                permissions: [permission],
                            });
                        }
                        setAcl([...acl]);
                    }}
                />
            </Box>
        </Box>}

        <Flex justifyContent="end" px="20px" py="12px" margin="-20px" background="var(--dialogToolbar)" gap="8px">
            <Button color="errorMain" type="button" onClick={onCancel}>Cancel</Button>
            <Button color="successMain" disabled={product == null || !name.trim()} type="submit">Create</Button>
        </Flex>
    </form>;
}
