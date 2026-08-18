import * as React from "react";
import {useDispatch} from "react-redux";
import {useLocation, useNavigate} from "react-router-dom";
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
    ContainerRepositoryImage,
    ContainerRepositoryImageLayer,
    ContainerRepositorySpecification,
    ContainerRepositorySupport,
} from "@/UCloud/ContainerRepositoriesApi";
import {
    CREATE_TAG,
    Permission, PERMISSIONS_TAG,
    PROPERTIES_TAG,
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
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Client} from "@/Authentication/HttpClientInstance";
import {addProjectListener, removeProjectListener} from "@/Project/ReduxState";
import {dialogStore} from "@/Dialog/DialogStore";
import {ProductSelector} from "@/Products/Selector";
import {Box, Button, Flex, Input, Label, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import {PermissionsTable, ResourcePermissionEditor} from "@/Resource/PermissionEditor";
import {useProject} from "@/Project/cache";
import {isAdminOrPI} from "@/Project";
import {useProjectId} from "@/Project/Api";
import {injectStyle} from "@/Unstyled";
import {defaultModalStyle, slimModalStyle} from "@/Utilities/ModalUtilities";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {dateToString} from "@/Utilities/DateUtilities";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {Operation} from "@/ui-components/Operation";
import {sendFailureNotification} from "@/Notifications";
import ContainerRepositoryInstructions from "./Instructions";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeOthers: true,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: false,
    sorting: true,
    locationBar: false,
    breadcrumbTitles: true,
    projectSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

const RESOURCE_NAME = "Container registry";
const PROJECT_CHANGE_LISTENER_ID = "container-repositories";
const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2Storage, ContainerRepositorySupport>>({
    globalTtl: 60_000,
});

const ContainerRepositoryBrowserStyle = injectStyle("container-repository-browser", k => `
    ${k} header .header-first-row .location {
        min-width: 0;
        margin-left: 0 !important;
    }

    ${k} header .header-first-row .location input.location-bar {
        width: calc(100% - 10px);
        min-width: 0;
    }

    ${k} header .header-first-row .location ul {
        gap: 0;
    }

    ${k} header .header-first-row .location ul li::before {
        margin: 0 8px;
    }

    ${k} header .header-first-row .location ul li:first-child::before {
        content: unset;
        margin: 0;
    }
`);

type ImageGroupEntry = ContainerRepositoryImage & {
    entryType: "image-group";
    repositoryId: string;
    rootName: string;
};

type ImageEntry = ContainerRepositoryImage & {
    entryType: "image";
    repositoryId: string;
};

type LayerEntry = ContainerRepositoryImageLayer & {
    entryType: "layer";
};

type BrowserEntry = ContainerRepository | ImageGroupEntry | ImageEntry | LayerEntry;

type RegistryInstructions = {
    providerId: string;
    server: string;
    imageReference: string;
};

function isRepository(entry: BrowserEntry): entry is ContainerRepository {
    return "specification" in entry;
}

function isImage(entry: BrowserEntry): entry is ImageEntry {
    return "entryType" in entry && entry.entryType === "image";
}

function isImageGroup(entry: BrowserEntry): entry is ImageGroupEntry {
    return "entryType" in entry && entry.entryType === "image-group";
}

function isLayer(entry: BrowserEntry): entry is LayerEntry {
    return "entryType" in entry && entry.entryType === "layer";
}

export default function ContainerRepositoryBrowse({
    opts,
    headerControls,
}: {
    opts?: ResourceBrowserOpts<ContainerRepository>;
    headerControls?: ResourceBrowseHeaderControls;
}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<BrowserEntry> | null>(null);
    const openTriggeredByPath = React.useRef<string | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const location = useLocation();
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    usePage(RESOURCE_NAME, SidebarTabId.FILES);

    React.useEffect(() => {
        headerControls?.setRefresh?.(() => browserRef.current?.refresh());
        return () => headerControls?.setRefresh?.(undefined);
    }, [headerControls]);

    React.useEffect(() => {
        return () => removeProjectListener(PROJECT_CHANGE_LISTENER_ID);
    }, []);

    React.useEffect(() => {
        const path = getQueryParamOrElse(location.search, "path", "/");
        const browser = browserRef.current;
        if (browser && browser.currentPath !== path) {
            openTriggeredByPath.current = path;
            browser.open(path);
        }
    }, [location.search]);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            const repositoriesByPath = new Map<string, ContainerRepository>();
            const imageGroupsByPath = new Map<string, ImageGroupEntry>();
            const imagesByPath = new Map<string, ImageEntry>();
            new ResourceBrowser<BrowserEntry>(mount, RESOURCE_NAME, opts as unknown as ResourceBrowserOpts<BrowserEntry>).init(
                browserRef,
                FEATURES,
                "/",
                browser => {
                    browser.root.classList.add(ContainerRepositoryBrowserStyle);
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

                    browser.on("skipOpen", (_oldPath, _newPath, resource) => resource != null && isLayer(resource));

                    browser.on("open", (_oldPath, newPath, resource) => {
                        if (openTriggeredByPath.current === newPath) {
                            openTriggeredByPath.current = null;
                        } else if (!opts?.embedded && !opts?.isModal) {
                            navigate(`/container-repositories?path=${encodeURIComponent(newPath)}`);
                        }
                        if (resource && isRepository(resource)) {
                            repositoriesByPath.set(newPath, resource);
                            browser.renderBreadcrumbs();
                            browser.renderOperations();
                            browser.setColumns([
                                {name: "Image name"},
                                {name: "Tags", columnWidth: 160},
                                {name: "", columnWidth: 1},
                                {name: "", columnWidth: 1},
                            ]);
                            callAPI(ContainerRepositoriesApi.browseImages({
                                repositoryId: resource.id,
                                itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                            })).then(result => {
                                browser.registerPage({
                                    ...result,
                                    items: result.items.map(image => imageGroupEntry(image, resource)),
                                }, newPath, true);
                                browser.renderRows();
                            }).catch(error => sendFailureNotification("Failed to browse container images. " + extractErrorMessage(error)));
                            return;
                        }
                        if (resource && isImageGroup(resource)) {
                            imageGroupsByPath.set(newPath, resource);
                            browser.renderBreadcrumbs();
                            browser.renderOperations();
                            browser.setColumns([
                                {name: "Tag"},
                                {name: "Media type", columnWidth: 220},
                                {name: "Layers", columnWidth: 100},
                                {name: "Compressed size", columnWidth: 190},
                            ]);
                            callAPI(ContainerRepositoriesApi.browseImages({
                                repositoryId: resource.repositoryId,
                                repository: resource.repository,
                                itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                            })).then(result => {
                                browser.registerPage({
                                    ...result,
                                    items: result.items.map(image => ({
                                        ...image,
                                        entryType: "image" as const,
                                        repositoryId: resource.repositoryId,
                                    })),
                                }, newPath, true);
                                browser.renderRows();
                            }).catch(error => sendFailureNotification("Failed to browse image tags. " + extractErrorMessage(error)));
                            return;
                        }
                        if (resource && isImage(resource)) {
                            imagesByPath.set(newPath, resource);
                            browser.renderBreadcrumbs();
                            browser.renderOperations();
                            browser.setColumns([
                                {name: "Layer digest"},
                                {name: "Media type", columnWidth: 300},
                                {name: "Platforms", columnWidth: 180},
                                {name: "Compressed size", columnWidth: 190},
                            ]);
                            const layers = resource.layers ?? [];
                            browser.registerPage({
                                itemsPerPage: layers.length,
                                items: layers.map(layer => ({...layer, entryType: "layer" as const})),
                            }, newPath, true);
                            browser.renderRows();
                            return;
                        }

                        const repository = repositoriesByPath.get(newPath);
                        if (repository) {
                            browser.setColumns([
                                {name: "Image name"},
                                {name: "Tags", columnWidth: 160},
                                {name: "", columnWidth: 1},
                                {name: "", columnWidth: 1},
                            ]);
                            callAPI(ContainerRepositoriesApi.browseImages({
                                repositoryId: repository.id,
                                itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                            })).then(result => {
                                browser.registerPage({
                                    ...result,
                                    items: result.items.map(image => imageGroupEntry(image, repository)),
                                }, newPath, true);
                                browser.renderRows();
                            }).catch(error => sendFailureNotification("Failed to browse container images. " + extractErrorMessage(error)));
                            return;
                        }

                        const imageGroup = imageGroupsByPath.get(newPath);
                        if (imageGroup) {
                            browser.setColumns([
                                {name: "Tag"},
                                {name: "Media type", columnWidth: 220},
                                {name: "Layers", columnWidth: 100},
                                {name: "Compressed size", columnWidth: 190},
                            ]);
                            callAPI(ContainerRepositoriesApi.browseImages({
                                repositoryId: imageGroup.repositoryId,
                                repository: imageGroup.repository,
                                itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                            })).then(result => {
                                browser.registerPage({
                                    ...result,
                                    items: result.items.map(image => ({
                                        ...image,
                                        entryType: "image" as const,
                                        repositoryId: imageGroup.repositoryId,
                                    })),
                                }, newPath, true);
                                browser.renderRows();
                            }).catch(error => sendFailureNotification("Failed to browse image tags. " + extractErrorMessage(error)));
                            return;
                        }

                        const image = imagesByPath.get(newPath);
                        if (image) {
                            browser.setColumns([
                                {name: "Layer digest"},
                                {name: "Media type", columnWidth: 300},
                                {name: "Platforms", columnWidth: 180},
                                {name: "Compressed size", columnWidth: 190},
                            ]);
                            const layers = image.layers ?? [];
                            browser.registerPage({
                                itemsPerPage: layers.length,
                                items: layers.map(layer => ({...layer, entryType: "layer" as const})),
                            }, newPath, true);
                            browser.renderRows();
                            return;
                        }

                        const pathParts = newPath.split("/").filter(Boolean);
                        if (pathParts.length > 0) {
                            const repositoryPath = `/${pathParts[0]}`;
                            callAPI(ContainerRepositoriesApi.retrieve({
                                id: decodeBrowserComponent(pathParts[0]),
                                includeOthers: true,
                            })).then(repository => {
                                repositoriesByPath.set(repositoryPath, repository);
                                browser.renderBreadcrumbs();
                                browser.renderOperations();
                                if (pathParts.length === 1) {
                                    browser.setColumns([
                                        {name: "Image name"},
                                        {name: "Tags", columnWidth: 160},
                                        {name: "", columnWidth: 1},
                                        {name: "", columnWidth: 1},
                                    ]);
                                    return callAPI(ContainerRepositoriesApi.browseImages({
                                        repositoryId: repository.id,
                                        itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                                    })).then(result => browser.registerPage({
                                        ...result,
                                        items: result.items.map(entry => imageGroupEntry(entry, repository)),
                                    }, newPath, true));
                                }

                                const imageGroupPath = `${repositoryPath}/${pathParts[1]}`;
                                const physicalRepository = decodeBrowserComponent(pathParts[1]);
                                const imageGroup: ImageGroupEntry = {
                                    kind: "IMAGE",
                                    name: imageGroupName(repository.specification.name, physicalRepository),
                                    repository: physicalRepository,
                                    tag: "",
                                    tagCount: 0,
                                    digest: "",
                                    mediaType: "",
                                    sizeInBytes: 0,
                                    layers: [],
                                    entryType: "image-group",
                                    repositoryId: repository.id,
                                    rootName: repository.specification.name,
                                };
                                imageGroupsByPath.set(imageGroupPath, imageGroup);
                                browser.renderBreadcrumbs();
                                browser.renderOperations();
                                if (pathParts.length === 2) {
                                    browser.setColumns([
                                        {name: "Tag"},
                                        {name: "Media type", columnWidth: 220},
                                        {name: "Layers", columnWidth: 100},
                                        {name: "Compressed size", columnWidth: 190},
                                    ]);
                                    return callAPI(ContainerRepositoriesApi.browseImages({
                                        repositoryId: repository.id,
                                        repository: imageGroup.repository,
                                        itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                                    })).then(result => browser.registerPage({
                                        ...result,
                                        items: result.items.map(entry => ({
                                            ...entry,
                                            entryType: "image" as const,
                                            repositoryId: repository.id,
                                        })),
                                    }, newPath, true));
                                }

                                const tag = decodeBrowserComponent(pathParts[2]);
                                return callAPI(ContainerRepositoriesApi.browseImages({
                                    repositoryId: repository.id,
                                    repository: imageGroup.repository,
                                    tag,
                                    itemsPerPage: 1,
                                })).then(result => {
                                    const entry = result.items[0];
                                    if (!entry) throw new Error("Container image not found");
                                    const imageEntry: ImageEntry = {
                                        ...entry,
                                        entryType: "image",
                                        repositoryId: repository.id,
                                    };
                                    imagesByPath.set(newPath, imageEntry);
                                    browser.setColumns([
                                        {name: "Layer digest"},
                                        {name: "Media type", columnWidth: 300},
                                        {name: "Platforms", columnWidth: 180},
                                        {name: "Compressed size", columnWidth: 190},
                                    ]);
                                    const layers = imageEntry.layers ?? [];
                                    browser.registerPage({
                                        itemsPerPage: layers.length,
                                        items: layers.map(layer => ({...layer, entryType: "layer" as const})),
                                    }, newPath, true);
                                });
                            }).then(() => browser.renderRows()).catch(error => {
                                sendFailureNotification("Failed to open container repository path. " + extractErrorMessage(error));
                            });
                            return;
                        }

                        browser.setColumns([
                            {name: "Repository name", sortById: "name"},
                            {name: "Provider", columnWidth: 150},
                            {name: "Created by", sortById: "createdBy", columnWidth: 250},
                            {name: "Created at", sortById: "createdAt", columnWidth: 160},
                        ]);
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
                        const repository = repositoriesByPath.get(path);
                        if (repository) {
                            const result = await callAPI(ContainerRepositoriesApi.browseImages({
                                repositoryId: repository.id,
                                itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                                next: browser.cachedNext[path] ?? undefined,
                            }));
                            if (path !== browser.currentPath) return;
                            browser.registerPage({
                                ...result,
                                items: result.items.map(image => imageGroupEntry(image, repository)),
                            }, path, false);
                            return;
                        }
                        const imageGroup = imageGroupsByPath.get(path);
                        if (imageGroup) {
                            const result = await callAPI(ContainerRepositoriesApi.browseImages({
                                repositoryId: imageGroup.repositoryId,
                                repository: imageGroup.repository,
                                itemsPerPage: defaultRetrieveFlags.itemsPerPage,
                                next: browser.cachedNext[path] ?? undefined,
                            }));
                            if (path !== browser.currentPath) return;
                            browser.registerPage({
                                ...result,
                                items: result.items.map(image => ({
                                    ...image,
                                    entryType: "image" as const,
                                    repositoryId: imageGroup.repositoryId,
                                })),
                            }, path, false);
                            return;
                        }
                        if (imagesByPath.has(path)) return;
                        const result = await callAPI(ContainerRepositoriesApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                        }));

                        if (path !== browser.currentPath) return;
                        browser.registerPage(result, path, false);
                    });

                    browser.on("renderRow", (entry, row) => {
                        const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                        row.title.append(icon);
                        if (isImageGroup(entry)) {
                            ResourceBrowser.icons.renderIcon({
                                name: "heroCube",
                                color: "textPrimary",
                                color2: "textPrimary",
                                height: 64,
                                width: 64,
                            }).then(setIcon);
                            row.title.append(ResourceBrowser.defaultTitleRenderer(entry.name || entry.repository, row));
                            row.stat1.innerText = (entry.tagCount ?? 0).toLocaleString();
                            return;
                        }
                        if (isImage(entry)) {
                            ResourceBrowser.icons.renderIcon({
                                name: "heroCube",
                                color: "textPrimary",
                                color2: "textPrimary",
                                height: 64,
                                width: 64,
                            }).then(setIcon);
                            const imageGroup = imageGroupsByPath.get(browser.currentPath);
                            const imageName = imageGroup?.name || entry.repository.split("/").pop() || entry.repository;
                            row.title.append(ResourceBrowser.defaultTitleRenderer(`${imageName}:${entry.tag}`, row));
                            const mediaType = friendlyMediaType(entry.mediaType);
                            row.stat1.innerText = mediaType.label;
                            row.stat1.title = mediaType.title;
                            row.stat2.innerText = (entry.layers?.length ?? 0).toLocaleString();
                            row.stat3.innerText = formatBytes(entry.sizeInBytes);
                            return;
                        }
                        if (isLayer(entry)) {
                            ResourceBrowser.icons.renderIcon({
                                name: "heroSquare3Stack3D",
                                color: "textPrimary",
                                color2: "textPrimary",
                                height: 64,
                                width: 64,
                            }).then(setIcon);
                            row.title.append(ResourceBrowser.defaultTitleRenderer(entry.digest, row));
                            const mediaType = friendlyMediaType(entry.mediaType);
                            row.stat1.innerText = mediaType.label;
                            row.stat1.title = mediaType.title;
                            row.stat2.innerText = entry.platforms?.join(", ") || "All platforms";
                            row.stat3.innerText = formatBytes(entry.sizeInBytes);
                            return;
                        }

                        const repository = entry;
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

                    browser.on("generateBreadcrumbs", path => {
                        const result = [{title: RESOURCE_NAME, absolutePath: "/"}];
                        let absolutePath = "";
                        const components = path.split("/").filter(Boolean);
                        for (let index = 0; index < components.length; index++) {
                            const component = components[index];
                            absolutePath += `/${component}`;
                            const repository = repositoriesByPath.get(absolutePath);
                            const imageGroup = imageGroupsByPath.get(absolutePath);
                            const image = imagesByPath.get(absolutePath);
                            let title = decodeBrowserComponent(component);
                            if (repository) title = repository.specification.name;
                            if (imageGroup) title = imageGroup.name;
                            if (image) title = image.tag;
                            if (index === 1 && !imageGroup) {
                                const parentRepository = repositoriesByPath.get(`/${components[0]}`);
                                if (parentRepository) {
                                    title = imageGroupName(
                                        parentRepository.specification.name,
                                        decodeBrowserComponent(component),
                                    );
                                }
                            }
                            result.push({title, absolutePath});
                        }
                        return result;
                    });
                    browser.on("pathToEntry", entry => {
                        if (isRepository(entry)) return `/${entry.id}`;
                        const parent = browser.currentPath === "/" ? "" : browser.currentPath;
                        if (isImageGroup(entry)) return `${parent}/${encodeURIComponent(entry.repository)}`;
                        if (isImage(entry)) return `${parent}/${encodeURIComponent(entry.tag)}`;
                        return `${parent}/${encodeURIComponent(entry.digest)}`;
                    });
                    browser.on("nameOfEntry", entry => {
                        if (isRepository(entry)) return entry.specification.name;
                        if (isImageGroup(entry)) return entry.name;
                        if (isImage(entry)) return entry.tag;
                        return entry.digest;
                    });
                    browser.on("sort", page => page.sort((a, b) => nameOfBrowserEntry(a).localeCompare(nameOfBrowserEntry(b))));
                    browser.on("unhandledShortcut", () => {});
                    browser.setEmptyIcon("heroArchiveBox");

                    browser.on("renderEmptyPage", reason => {
                        const emptyPage = browser.emptyPageElement;
                        switch (reason.tag) {
                            case EmptyReasonTag.LOADING:
                                emptyPage.reason.append("Loading container repository contents...");
                                break;
                            case EmptyReasonTag.EMPTY:
                                if (imagesByPath.has(browser.currentPath)) {
                                    emptyPage.reason.append("This image has no layers.");
                                } else if (imageGroupsByPath.has(browser.currentPath)) {
                                    emptyPage.reason.append("This image has no tags.");
                                } else if (repositoriesByPath.has(browser.currentPath)) {
                                    emptyPage.reason.append("This repository has no tagged images.");
                                } else {
                                    emptyPage.reason.append("This workspace has no container repositories.");
                                }
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

                        const registryInstructions = getRegistryInstructions(
                            browser.currentPath,
                            repositoriesByPath,
                            imageGroupsByPath,
                            imagesByPath,
                            supportByProvider.retrieveFromCacheOnly(Client.projectId ?? ""),
                        );

                        const configureRegistryOp: Operation<BrowserEntry, unknown> | null = registryInstructions ? {
                            text: "Configure Docker",
                            icon: "heroWrenchScrewdriver",
                            enabled: () => true,
                            onClick: () => {
                                dialogStore.addDialog(
                                    <ContainerRepositoryInstructions {...registryInstructions} />,
                                    doNothing,
                                    true,
                                    defaultModalStyle,
                                );
                            },
                        } : null;

                        if (imageGroupsByPath.has(browser.currentPath)) {
                            const repositoryPath = `/${browser.currentPath.split("/").filter(Boolean)[0]}`;
                            const repositoryPermissions = repositoriesByPath.get(repositoryPath)?.permissions.myself ?? [];
                            const canDeleteImages = repositoryPermissions.includes("EDIT") || repositoryPermissions.includes("ADMIN");
                            const deleteImageOperation: Operation<BrowserEntry, unknown> = {
                                text: "Delete tag",
                                icon: "trash",
                                color: "errorMain",
                                confirm: true,
                                confirmationText: "Delete the selected tag? Shared layers and other tags will not be deleted.",
                                enabled: entries => canDeleteImages && entries.length > 0 && entries.every(isImage),
                                onClick: async entries => {
                                    const images = entries.filter(isImage);
                                    try {
                                        await callAPI(ContainerRepositoriesApi.deleteImage(bulkRequestOf(...images.map(image => ({
                                            repositoryId: image.repositoryId,
                                            repository: image.repository,
                                            tag: image.tag,
                                        })))));
                                        for (const image of images) {
                                            browser.removeEntryFromCurrentPage(entry =>
                                                isImage(entry) && entry.repository === image.repository && entry.tag === image.tag,
                                            );
                                        }
                                        const group = imageGroupsByPath.get(browser.currentPath);
                                        if (group) group.tagCount = Math.max(0, group.tagCount - images.length);
                                        browser.renderRows();
                                    } catch (error: any) {
                                        sendFailureNotification("Failed to delete container image. " + extractErrorMessage(error));
                                    }
                                },
                            };
                            return configureRegistryOp ? [configureRegistryOp, deleteImageOperation] : [deleteImageOperation];
                        } else if (repositoriesByPath.has(browser.currentPath)) {
                            return configureRegistryOp ? [configureRegistryOp] : [];
                        } else if (imagesByPath.has(browser.currentPath)) {
                            return configureRegistryOp ? [configureRegistryOp] : [];
                        } else {
                            const callbacks = browser.dispatchMessage(
                                "fetchOperationsCallback",
                                fn => fn(),
                            ) as ResourceBrowseCallbacks<ContainerRepository, ProductStorage, ContainerRepositorySpecification>;
                            const operations = (ContainerRepositoriesApi.retrieveOperations() as unknown as Operation<BrowserEntry, typeof callbacks>[])
                                .filter(operation => operation.tag !== PROPERTIES_TAG);
                            const create = operations.find(operation => operation.tag === CREATE_TAG);
                            if (create) create.onClick = () => openCreation(browser);

                            const permissionOp = operations.find(op => op.tag === PERMISSIONS_TAG)!;
                            permissionOp.enabled = (selected, cb) => {
                                if (selected.length !== 1) return false;
                                const entry = selected[0] as ContainerRepository;

                                return entry.owner.project != null &&
                                    entry.permissions.myself.some(it => it === "ADMIN");
                            };
                            permissionOp.onClick = ([resource]) => dialogStore.addDialog(
                                <ResourcePermissionEditor
                                    reload={() => browser.refresh()}
                                    entity={resource as ContainerRepository}
                                    api={ContainerRepositoriesApi}
                                    accessDescription={
                                        <Text mb="12px">
                                            By default, only you and the project administrators can use this repository.
                                            You can modify these permissions later on the <b>Properties</b> page.
                                        </Text>
                                    }
                                    showMissingPermissionHelp={false}
                                    noPermissionsWarning="Warning"
                                    readLabel="Pull"
                                    readIcon="heroArrowDownTray"
                                    writeLabel="Push"
                                    writeIcon="heroArrowUpTray"
                                />,
                                doNothing,
                                true,
                                slimModalStyle,
                            );

                            const enabledOps = operations.filter(operation => operation.enabled(selected, callbacks, selected));
                            if (configureRegistryOp) enabledOps.push(configureRegistryOp as unknown as Operation<BrowserEntry, typeof callbacks>);
                            return enabledOps;
                        }
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

async function openCreation(browser: ResourceBrowser<BrowserEntry>): Promise<void> {
    const support = await supportByProvider.retrieve(
        Client.projectId ?? "",
        () => retrieveSupportV2(ContainerRepositoriesApi),
    );
    const products: ProductV2Storage[] = [];
    for (const entries of Object.values(support.productsByProvider)) {
        for (const entry of entries) {
            if ((entry.support as ContainerRepositorySupport).containerRepositories?.enabled !== true) continue;
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
                Container repositories store container images for your workspace and control access to them.
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

function getRegistryInstructions(
    path: string,
    repositoriesByPath: Map<string, ContainerRepository>,
    imageGroupsByPath: Map<string, ImageGroupEntry>,
    imagesByPath: Map<string, ImageEntry>,
    supportByProvider: SupportByProviderV2<ProductV2Storage, ContainerRepositorySupport> | undefined,
): RegistryInstructions | null {
    const pathComponents = path.split("/").filter(Boolean);
    if (pathComponents.length === 0) return null;

    const repositoryPath = `/${pathComponents[0]}`;
    const repository = repositoriesByPath.get(path) ?? repositoriesByPath.get(repositoryPath);
    if (!repository) return null;

    const product = repository.specification.product;
    const support = supportByProvider?.productsByProvider[product.provider]?.find(entry =>
        entry.product.name === product.id && entry.product.category.name === product.category,
    );
    const registry = support?.support.containerRepositories;
    if (!registry?.enabled || !registry.server) return null;

    const image = imagesByPath.get(path);
    const imageGroup = imageGroupsByPath.get(path);
    const imageReference = image
        ? `${image.repository}:${image.tag}`
        : imageGroup
            ? `${imageGroup.repository}:<tag>`
            : `${repository.specification.name}/<image>:<tag>`;

    return {
        providerId: product.provider,
        server: registry.server,
        imageReference,
    };
}

function nameOfBrowserEntry(entry: BrowserEntry): string {
    if (isRepository(entry)) return entry.specification.name;
    if (isImageGroup(entry)) return entry.repository;
    if (isImage(entry)) return entry.tag;
    return entry.digest;
}

function imageGroupEntry(image: ContainerRepositoryImage, repository: ContainerRepository): ImageGroupEntry {
    return {
        ...image,
        name: imageGroupName(repository.specification.name, image.repository),
        entryType: "image-group",
        repositoryId: repository.id,
        rootName: repository.specification.name,
    };
}

function imageGroupName(root: string, repository: string): string {
    return relativeRepositoryPath(root, repository) || root;
}

function relativeRepositoryPath(root: string, repository: string): string {
    if (repository === root) return "";
    const prefix = root + "/";
    return repository.startsWith(prefix) ? repository.slice(prefix.length) : repository;
}

function decodeBrowserComponent(component: string): string {
    try {
        return decodeURIComponent(component);
    } catch {
        return component;
    }
}

function friendlyMediaType(mediaType: string | null | undefined): {label: string; title: string} {
    const labels: Record<string, string> = {
        "application/vnd.oci.image.index.v1+json": "OCI image index",
        "application/vnd.oci.image.manifest.v1+json": "OCI image",
        "application/vnd.oci.image.layer.v1.tar": "OCI layer",
        "application/vnd.oci.image.layer.v1.tar+gzip": "OCI layer (gzip)",
        "application/vnd.oci.image.layer.v1.tar+zstd": "OCI layer (zstd)",
        "application/vnd.oci.image.layer.nondistributable.v1.tar": "OCI external layer",
        "application/vnd.oci.image.layer.nondistributable.v1.tar+gzip": "OCI external layer (gzip)",
        "application/vnd.oci.image.layer.nondistributable.v1.tar+zstd": "OCI external layer (zstd)",
        "application/vnd.docker.distribution.manifest.v2+json": "Docker image",
        "application/vnd.docker.distribution.manifest.list.v2+json": "Docker image index",
        "application/vnd.docker.image.rootfs.diff.tar.gzip": "Docker layer (gzip)",
        "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip": "Docker external layer (gzip)",
    };
    if (!mediaType) return {label: "Unknown", title: ""};
    return {label: labels[mediaType] ?? mediaType, title: mediaType};
}

function formatBytes(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes < 0) return "Unknown";
    const units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit++;
    }
    return `${value.toLocaleString(undefined, {maximumFractionDigits: unit === 0 ? 0 : 1})} ${units[unit]}`;
}
