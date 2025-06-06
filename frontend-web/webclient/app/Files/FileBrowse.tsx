import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {useEffect, useLayoutEffect, useRef} from "react";
import {useDispatch} from "react-redux";
import {getQueryParam, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import MainContainer from "@/ui-components/MainContainer";
import {
    addContextSwitcherInPortal,
    checkIsWorkspaceAdmin,
    EmptyReasonTag,
    OperationOrGroup,
    placeholderImage,
    providerIcon,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts,
    ColumnTitleList,
    SelectionMode,
    checkCanConsumeResources,
    favoriteRowIcon
} from "@/ui-components/ResourceBrowser";
import FilesApi, {
    addFileSensitivityDialog,
    ExtraFileCallbacks,
    FileSensitivityNamespace,
    FileSensitivityVersion, isReadonly,
    isSensitivitySupported,
} from "@/UCloud/FilesApi";
import {fileName, getParentPath, pathComponents, resolvePath, sizeToString} from "@/Utilities/FileUtilities";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {
    createHTMLElements,
    defaultErrorHandler,
    displayErrorMessageOrDefault,
    doNothing,
    extensionFromPath,
    extensionType,
    extractErrorMessage,
    randomUUID,
    timestampUnixMs
} from "@/UtilityFunctions";
import {FileIconHint, FileType} from "@/Files/index";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {SvgFt} from "@/ui-components/FtIcon";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {dateToDateStringOrTime, dateToString} from "@/Utilities/DateUtilities";
import {callAPI as baseCallAPI} from "@/Authentication/DataHook";
import {accounting, BulkResponse, compute, FindByStringId, PageV2} from "@/UCloud";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {bulkRequestOf} from "@/UtilityFunctions";
import metadataDocumentApi, {FileMetadataDocument, FileMetadataDocumentOrDeleted, FileMetadataHistory} from "@/UCloud/MetadataDocumentApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ResourceBrowseCallbacks, ResourceOwner, ResourcePermissions, SupportByProvider} from "@/UCloud/ResourceApi";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import ProductReference = accounting.ProductReference;
import {Operation} from "@/ui-components/Operation";
import {visualizeWhitespaces} from "@/Utilities/TextUtilities";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {divHtml, image} from "@/Utilities/HTMLUtilities";
import * as Sync from "@/Syncthing/api";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {TruncateClass} from "@/ui-components/Truncate";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {FilesMoveRequestItem, UFile, UFileIncludeFlags} from "@/UCloud/UFile";
import {sidebarFavoriteCache} from "./FavoriteCache";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {HTMLTooltip} from "@/ui-components/Tooltip";
import {Feature, hasFeature} from "@/Features";

export enum SensitivityLevel {
    "INHERIT" = "Inherit",
    "PRIVATE" = "Private",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
}

// Cached network data
// =====================================================================================================================
// This is stored outside the component since we want to be able to share it across all instances of the same component.
const folderCache = new AsyncCache<UFile>({globalTtl: 15_000});
const collectionCache = new AsyncCache<FileCollection>({globalTtl: 15_000});
const collectionCacheForCompletion = new AsyncCache<FileCollection[]>({globalTtl: 60_000});
const trashCache = new AsyncCache<UFile>();
const metadataTemplateCache = new AsyncCache<string>();

const defaultRetrieveFlags: Partial<UFileIncludeFlags> & {itemsPerPage: number} = {
    includeMetadata: true,
    includeSizes: true,
    includeTimestamps: true,
    includeUnixInfo: true,
    allowUnsupportedInclude: true,
    itemsPerPage: 250,
};

const SEARCH = "/search";

const FEATURES: ResourceBrowseFeatures = {
    dragToSelect: true,
    supportsMove: true,
    supportsCopy: true,
    locationBar: true,
    showStar: true,
    renderSpinnerWhenLoading: true,
    search: true,
    sorting: true,
    filters: true,
    contextSwitcher: true,
    showHeaderInEmbedded: true,
    showColumnTitles: true,
    breadcrumbTitles: true,
}

interface AdditionalResourceBrowserOpts {
    initialPath?: string;
    managesLocalProject?: boolean; // TODO(Jonas): Having both managesLocalProject and initialProject might not be for the best.
    initialProject?: string;
    additionalOperations?: Operation<UFile, ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks>[];
}
let lastActiveProject: string | undefined = "";
type SortById = "PATH" | "MODIFIED_AT" | "SIZE";
const rowTitles: ColumnTitleList<SortById> = [{name: "Name", sortById: "PATH"}, {name: "", columnWidth: 32}, {name: "Modified at", sortById: "MODIFIED_AT", columnWidth: 160}, {name: "Size", sortById: "SIZE", columnWidth: 100}];

const RESOURCE_NAME = "File";
function FileBrowse({opts}: {opts?: ResourceBrowserOpts<UFile> & AdditionalResourceBrowserOpts}): React.ReactNode {
    const navigate = useNavigate();
    const location = useLocation();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<UFile> | null>(null);
    const openTriggeredByPath = useRef<string | null>(null);
    const dispatch = useDispatch();
    if (!opts?.embedded && !opts?.isModal) {
        usePage("Files", SidebarTabId.FILES);
    }
    const isInitialMount = useRef<boolean>(true);
    useEffect(() => {
        isInitialMount.current = false;

        // Invalidate cache if necessary on active project changes
        if (lastActiveProject !== Client.projectId) {
            collectionCacheForCompletion.invalidateAll();
        }
        lastActiveProject = Client.projectId;

        // Note(Jonas): If the user reloads the page to '/search', we don't have any info cached, so we navigate to '/drives' instead.
        // ONLY on component mount.
        if (getQueryParam(location.search, "path") === "/search") {
            navigate("/drives");
        }
    }, []);

    const isSelector = !!opts?.selection;
    const selectorPathRef = useRef(opts?.initialPath ?? "/");
    const activeProject = useRef(opts?.initialProject ?? Client.projectId);

    function callAPI<T>(parameters: APICallParameters<unknown, T>): Promise<T> {
        return baseCallAPI({
            ...parameters,
            projectOverride: activeProject.current ?? ""
        });
    }

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        search: !opts?.isModal,
        filters: !opts?.embedded?.hideFilters,
    }


    const didUnmount = useDidUnmount();

    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        let searching = "";
        let lastActiveFilePath = "";
        if (mount && !browserRef.current) {
            new ResourceBrowser<UFile>(mount, RESOURCE_NAME, opts).init(browserRef, features, undefined, browser => {
                browser.setColumns(rowTitles);

                // Syncthing data
                // =========================================================================================================
                let syncthingConfig: Sync.SyncthingConfig | undefined = undefined;
                let syncthingProduct: compute.ComputeProductSupportResolved | null = null;

                // Metadata utilities
                // =========================================================================================================
                // This mostly involves fetching and updating data related to the sensitivity and favorite attributes.
                const findTemplateId = async (file: UFile, namespace: string, version: string): Promise<string> => {
                    const template = Object.values(file.status.metadata?.templates ?? {}).find(it =>
                        it.namespaceName === namespace && it.version == version
                    );

                    if (!template) {
                        return metadataTemplateCache.retrieve(namespace, async () => {
                            const page = await callAPI<PageV2<FileMetadataTemplateNamespace>>(
                                MetadataNamespaceApi.browse({filterName: namespace, itemsPerPage: 250})
                            );
                            if (page.items.length === 0) return "";
                            return page.items[0].id;
                        });
                    }

                    return template.namespaceId;
                }

                const findFavoriteStatus = async (file: UFile): Promise<boolean> => {
                    const templateId = await findTemplateId(file, "favorite", "1.0.0");
                    if (!templateId) return false;

                    const entry = file.status.metadata?.metadata[templateId]?.[0];
                    if (!entry || entry.type === "deleted") return false;
                    return entry.specification.document.favorite;
                };

                const findSensitivity = async (file: UFile): Promise<SensitivityLevel | null> => {
                    if (!isSensitivitySupported(file)) return null;

                    const sensitivityTemplateId = await findTemplateId(file, FileSensitivityNamespace,
                        FileSensitivityVersion);
                    if (!sensitivityTemplateId) return SensitivityLevel.PRIVATE;

                    const entry = file.status.metadata?.metadata[sensitivityTemplateId]?.[0];
                    if (!entry || entry.type === "deleted") return SensitivityLevel.PRIVATE;
                    return entry.specification.document.sensitivity;
                };

                function translateFilters(record?: Record<string, string>): Record<string, string> {
                    const result = {...record};
                    const showHiddenFiles = result["showHiddenFiles"] == "true";
                    delete result["showHiddenFiles"];
                    result["filterHiddenFiles"] = (!showHiddenFiles).toString();
                    return result;
                }

                // Operations
                // =========================================================================================================
                // This is split into two sections. The first section contains generic functions which can be re-used for
                // various purposes. All of these operations will optimistically update the UI and send the appropriate
                // network calls. The second section related to the operations which show up in the context menu and the
                // header.
                const setFavoriteStatus = async (file: UFile, isFavorite: boolean, render: boolean = true) => {
                    const templateId = await findTemplateId(file, "favorite", "1.0.0");
                    if (!templateId) return;
                    if (!file.status.metadata) file.status.metadata = {metadata: {}, templates: {}};
                    const currentMetadata: FileMetadataHistory = file.status.metadata;
                    const favorites: FileMetadataDocumentOrDeleted[] = currentMetadata.metadata[templateId] ?? [];
                    let mostRecentStatusId = "";
                    for (let i = 0; i < favorites.length; i++) {
                        if (favorites[i].id === "fake_entry") continue;
                        if (favorites[i].type !== "metadata") continue;

                        mostRecentStatusId = favorites[i].id;
                        break;
                    }

                    favorites.unshift({
                        type: "metadata",
                        status: {
                            approval: {type: "not_required"},
                        },
                        createdAt: 0,
                        createdBy: "",
                        specification: {
                            templateId: templateId!,
                            changeLog: "",
                            document: {favorite: isFavorite},
                            version: "1.0.0",
                        },
                        id: "fake_entry",
                    });

                    currentMetadata.metadata[templateId] = favorites;
                    file.status.metadata = currentMetadata;

                    if (!isFavorite) {
                        sidebarFavoriteCache.remove(file.id);
                        callAPI(
                            metadataDocumentApi.delete(
                                bulkRequestOf({
                                    changeLog: "Remove favorite",
                                    id: mostRecentStatusId
                                })
                            )
                        ).then(doNothing);
                    } else {
                        callAPI(
                            metadataDocumentApi.create(bulkRequestOf({
                                fileId: file.id,
                                metadata: {
                                    document: {favorite: isFavorite},
                                    version: "1.0.0",
                                    changeLog: "New favorite status",
                                    templateId: templateId
                                }
                            }))
                        ).then(({responses}: BulkResponse<FindByStringId>) => {
                            currentMetadata.metadata[templateId][0].id = responses[0].id;
                            sidebarFavoriteCache.add({
                                path: file.id,
                                metadata: currentMetadata.metadata[templateId][0] as FileMetadataDocument
                            });
                        });
                    }

                    if (render) browser.renderRows();
                };

                const fakeFile = (
                    path: string,
                    opts?: {
                        type?: FileType,
                        hint?: FileIconHint,
                        modifiedAt?: number,
                        size?: number
                    }
                ): UFile => {
                    const page = browser.cachedData[browser.currentPath] ?? [];
                    let likelyProduct: ProductReference = {id: "", provider: "", category: ""};
                    if (page.length > 0) likelyProduct = page[0].specification.product;

                    let likelyOwner: ResourceOwner = {createdBy: Client.username ?? "", project: Client.projectId};
                    if (page.length > 0) likelyOwner = page[0].owner;

                    let likelyPermissions: ResourcePermissions = {myself: ["ADMIN", "READ", "EDIT"]};
                    if (page.length > 0) likelyPermissions = page[0].permissions;

                    return {
                        createdAt: opts?.modifiedAt ?? 0,
                        owner: likelyOwner,
                        permissions: likelyPermissions,
                        specification: {
                            product: likelyProduct,
                            collection: ""
                        },
                        id: path,
                        updates: [],
                        status: {
                            type: opts?.type ?? "FILE",
                            modifiedAt: opts?.modifiedAt,
                            sizeInBytes: opts?.size
                        }
                    }
                };

                const insertFakeEntry = (
                    name: string,
                    opts?: {
                        type?: FileType,
                        hint?: FileIconHint,
                        modifiedAt?: number,
                        size?: number
                    }
                ): string => {
                    const page = browser.cachedData[browser.currentPath] ?? [];
                    const existing = page.find(it => fileName(it.id) === name);
                    let actualName: string = name;
                    if (existing != null) {
                        if (opts?.type !== "DIRECTORY") {
                            const hasExtension = name.includes(".");
                            const baseName = name.substring(0, hasExtension ? name.lastIndexOf(".") : undefined);
                            const extension = hasExtension ? name.substring(name.lastIndexOf(".") + 1) : undefined;

                            let attempt = 1;
                            while (true) {
                                actualName = `${baseName}(${attempt})`;
                                if (hasExtension) actualName += `.${extension}`;
                                if (page.find(it => fileName(it.id) === actualName) === undefined) break;
                                attempt++;
                            }
                        } else {
                            return existing.id;
                        }
                    }

                    const path = resolvePath(browser.currentPath) + "/" + actualName;
                    browser.insertEntryIntoCurrentPage(fakeFile(path, opts));
                    return path;
                }

                const copyOrMove = (files: UFile[], target: string, shouldMove: boolean, opts?: {suffix?: string}) => {
                    const initialPath = browser.currentPath;
                    const isMovingFromCurrentDirectory = files.every(it => it.id.startsWith(initialPath + "/"));
                    const suffix = opts?.suffix ?? "";

                    // Prepare the payload and verify that we can in fact do this
                    const requests: FilesMoveRequestItem[] = [];
                    const undoRequests: FilesMoveRequestItem[] = [];
                    for (const file of files) {
                        const oldId = file.id;
                        const newId = target + "/" + fileName(file.id) + suffix;
                        if (shouldMove && (oldId === newId || oldId === target)) {
                            // Invalid. TODO(Dan): Should we write that you cannot do this?
                            return;
                        } else {
                            requests.push({oldId, newId, conflictPolicy: "RENAME"})
                            undoRequests.push({oldId: newId, newId: oldId, conflictPolicy: "RENAME"});
                        }
                    }

                    const requestPayload = bulkRequestOf(...requests);

                    // Optimistically update the user-interface to contain the new state
                    let lastEntry: string | null = null;
                    for (const entry of files) {
                        if (target === browser.currentPath) {
                            lastEntry = insertFakeEntry(
                                fileName(entry.id),
                                {
                                    type: entry.status.type,
                                    modifiedAt: timestampUnixMs(),
                                    size: 0
                                }
                            );
                        }

                        // NOTE(Dan): Putting this after the insertion is consistent with the most common
                        // backends, even though it produces surprising results.
                        if (shouldMove) browser.removeEntryFromCurrentPage(it => it.id === entry.id);
                    }

                    browser.renderRows();
                    if (lastEntry) {
                        const idx = browser.findVirtualRowIndex(it => it.id === lastEntry);
                        if (idx !== null) {
                            browser.ensureRowIsVisible(idx, true, true);
                            browser.select(idx, SelectionMode.SINGLE);
                        }
                    }

                    // Perform the requested action

                    const call = shouldMove ?
                        FilesApi.move(requestPayload) :
                        FilesApi.copy(requestPayload);

                    callAPI(call).catch(err => {
                        snackbarStore.addFailure(extractErrorMessage(err), false);
                        browser.refresh();
                    });

                    if (shouldMove) {
                        if (hasFeature(Feature.COMPONENT_STORED_CUT_COPY)) {
                            ResourceBrowser.addUndoAction(RESOURCE_NAME, () => {
                                if (browser.currentPath === initialPath && isMovingFromCurrentDirectory) {
                                    for (const file of files) {
                                        insertFakeEntry(
                                            fileName(file.id),
                                            {
                                                type: file.status.type,
                                                hint: file.status.icon,
                                                modifiedAt: file.status.modifiedAt,
                                                size: file.status.sizeInBytes
                                            }
                                        );
                                    }
                                    browser.renderRows();
                                }

                                callAPI(FilesApi.move(bulkRequestOf(...undoRequests)));
                            });
                        } else {
                            browser._undoStack.unshift(() => {
                                if (browser.currentPath === initialPath && isMovingFromCurrentDirectory) {
                                    for (const file of files) {
                                        insertFakeEntry(
                                            fileName(file.id),
                                            {
                                                type: file.status.type,
                                                hint: file.status.icon,
                                                modifiedAt: file.status.modifiedAt,
                                                size: file.status.sizeInBytes
                                            }
                                        );
                                    }
                                    browser.renderRows();
                                }

                                callAPI(FilesApi.move(bulkRequestOf(...undoRequests)));
                            });
                        }
                    }
                };

                const performMoveToTrash = async (files: UFile[]) => {
                    if (files.length === 0) return;

                    try {
                        const collectionId = pathComponents(browser.currentPath)[0];
                        try {
                            const trash = await trashCache.retrieve(collectionId, async () => {
                                const potentialFolder = await callAPI(FilesApi.retrieve({id: `/${collectionId}/Trash`}));
                                if (potentialFolder.status.icon === "DIRECTORY_TRASH") return potentialFolder;
                                throw "Could not find trash folder";
                            });

                            if (files.some(it => it.id.startsWith(trash.id))) {
                                // Note(Jonas): If we are in the trash folder, then maybe don't move to the same folder on delete.
                                return;
                            }
                        } catch (ignored) {
                            // The trash folder probably doesn't exist yet.
                        }

                        for (const f of files) {
                            browser.removeEntryFromCurrentPage(file => file.id === f.id);
                        }

                        browser.renderRows();
                        await callAPI(FilesApi.trash(bulkRequestOf(...files.map(it => ({id: it.id})))));
                        snackbarStore.addSuccess(`${files.length} file(s) moved to trash.`, false)
                    } catch (e) {
                        displayErrorMessageOrDefault(e, "Failed to delete files");
                        browser.refresh();
                    }
                };

                let shouldRemoveFakeDirectory = true;
                const fakeFileName = ".00000000000000000000$NEW_DIR"
                const showCreateDirectory = () => {
                    const fakePath = resolvePath(browser.currentPath) + "/" + fakeFileName.split("/")[0];
                    browser.removeEntryFromCurrentPage(it => it.id === fakePath);
                    shouldRemoveFakeDirectory = false;
                    insertFakeEntry(fakeFileName, {type: "DIRECTORY"});
                    const idx = browser.findVirtualRowIndex(it => it.id === fakePath);
                    if (idx !== null) browser.ensureRowIsVisible(idx, true);

                    browser.showRenameField(
                        it => it.id === fakePath,
                        () => {
                            browser.removeEntryFromCurrentPage(it => it.id === fakePath);
                            if (!browser.renameValue) return;

                            const realPath = resolvePath(browser.currentPath) + "/" + browser.renameValue;
                            insertFakeEntry(browser.renameValue.split("/")[0], {type: "DIRECTORY"});
                            const idx = browser.findVirtualRowIndex(it => it.id === realPath);
                            if (idx !== null) {
                                browser.ensureRowIsVisible(idx, true, true);
                                browser.select(idx, SelectionMode.SINGLE);
                            }

                            callAPI(FilesApi.createFolder(bulkRequestOf({id: realPath, conflictPolicy: "RENAME"})))
                                .catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                    browser.refresh();
                                });
                        },
                        () => {
                            if (shouldRemoveFakeDirectory) browser.removeEntryFromCurrentPage(it => it.id === fakePath);
                        },
                        ""
                    );

                    shouldRemoveFakeDirectory = true;
                };

                const startRenaming = (path: string) => {
                    browser.showRenameField(
                        it => it.id === path,
                        () => {
                            if (!browser.renameValue) return; // No change
                            const parentPath = resolvePath(getParentPath(path));
                            const page = browser.cachedData[parentPath] ?? [];
                            const actualFile = page.find(it => fileName(it.id) === fileName(path));
                            if (actualFile) {
                                const oldId = actualFile.id;
                                actualFile.id = parentPath + "/" + browser.renameValue;
                                page.sort((a, b) => fileName(a.id).localeCompare(fileName(b.id)));
                                const newRow = browser.findVirtualRowIndex(it => it.id === actualFile.id);
                                if (newRow != null) {
                                    browser.ensureRowIsVisible(newRow, true, true);
                                    browser.select(newRow, SelectionMode.SINGLE);
                                }

                                if (oldId === actualFile.id) return; // No change

                                sidebarFavoriteCache.renameInCached(oldId, actualFile.id);

                                callAPI(FilesApi.move(bulkRequestOf({
                                    oldId,
                                    newId: actualFile.id,
                                    conflictPolicy: "REJECT"
                                }))).catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                    sidebarFavoriteCache.renameInCached(actualFile.id, oldId); // Revert on failure
                                    browser.refresh();
                                });

                                if (hasFeature(Feature.COMPONENT_STORED_CUT_COPY)) {
                                    ResourceBrowser.addUndoAction(RESOURCE_NAME, () => {
                                        callAPI(FilesApi.move(bulkRequestOf({
                                            oldId: actualFile.id,
                                            newId: oldId,
                                            conflictPolicy: "REJECT"
                                        })));

                                        sidebarFavoriteCache.renameInCached(actualFile.id, oldId); // Revert on undo
                                        actualFile.id = oldId;
                                        browser.renderRows();
                                    });
                                } else {
                                    browser._undoStack.unshift(() => {
                                        callAPI(FilesApi.move(bulkRequestOf({
                                            oldId: actualFile.id,
                                            newId: oldId,
                                            conflictPolicy: "REJECT"
                                        })));

                                        sidebarFavoriteCache.renameInCached(actualFile.id, oldId); // Revert on undo
                                        actualFile.id = oldId;
                                        browser.renderRows();
                                    });
                                }
                            }
                        },
                        doNothing,
                        fileName(path)
                    );
                };

                browser.on("fetchFilters", () => [{
                    type: "checkbox",
                    key: "showHiddenFiles",
                    text: "Show hidden files",
                    icon: "heroMagnifyingGlass",
                }]);

                browser.on("fetchOperations", () => {
                    function groupOperations<R>(ops: Operation<UFile, R>[]): OperationOrGroup<UFile, R>[] {
                        const result: OperationOrGroup<UFile, R>[] = [];
                        let i = 0;
                        for (; i < ops.length && result.length < 4; i++) {
                            const op = ops[i];
                            result.push(op);
                        }

                        const overflow: Operation<UFile, R>[] = [];
                        for (; i < ops.length; i++) {
                            overflow.push(ops[i]);
                        }

                        if (overflow.length > 0) {
                            result.push({
                                color: "secondaryMain",
                                icon: "ellipsis",
                                text: "",
                                iconRotation: 90,
                                operations: overflow,
                            })
                        }

                        return result;
                    }

                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks;
                    const enabledOperations = FilesApi.retrieveOperations().filter(op => op.enabled(selected, callbacks, selected));
                    if (opts?.additionalOperations) {
                        opts.additionalOperations.forEach(op => {
                            if (op.enabled(selected, callbacks, selected)) enabledOperations.push(op);
                        })
                    }
                    return groupOperations(enabledOperations);
                });

                browser.on("fetchOperationsCallback", () => {
                    const path = browser.currentPath ?? "";
                    const components = pathComponents(path);
                    const folder = folderCache.retrieveFromCacheOnly(path);

                    const isSearch = path === SEARCH;

                    const collectionId = isSearch ? pathComponents(lastActiveFilePath)[0] : components[0];
                    const collection = collectionCache.retrieveFromCacheOnly(collectionId);
                    if (!collection) return null;

                    const supportByProvider: SupportByProvider = {productsByProvider: {}};
                    supportByProvider.productsByProvider[collection.specification.product.provider] = [collection.status.resolvedSupport!];

                    const callbacks: ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks = {
                        supportByProvider,
                        collection: collection,
                        isSearch,
                        directory: folder,
                        dispatch: dispatch,
                        embedded: opts?.embedded,
                        isModal: opts?.isModal ?? false,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        navigate: to => navigate(to),
                        reload: () => browser.refresh(),
                        syncthingConfig,
                        setSynchronization(files: UFile[], shouldAdd: boolean): void {
                            if (!syncthingConfig) return;
                            if (!collection?.specification.product.provider) return;
                            const newConfig = deepCopy(syncthingConfig);

                            const folders = newConfig?.folders ?? []

                            for (const file of files) {
                                if (shouldAdd) {
                                    const newFolders = [...folders];
                                    newConfig.folders = newFolders;

                                    if (newFolders.every(it => it.ucloudPath !== file.id)) {
                                        newFolders.push({id: randomUUID(), ucloudPath: file.id});
                                    }
                                } else {
                                    newConfig.folders = folders.filter(it => it.ucloudPath !== file.id);
                                }
                            }

                            callAPI(Sync.api.updateConfiguration({
                                provider: collection?.specification.product.provider,
                                productId: "syncthing",
                                config: newConfig
                            })).then(() => {
                                syncthingConfig = newConfig
                                browser.renderOperations();
                            }).catch(e => {
                                if (didUnmount.current) return;
                                defaultErrorHandler(e);
                            });
                        },
                        startCreation(): void {
                            showCreateDirectory();
                        },
                        cancelCreation: doNothing,
                        startRenaming(resource: UFile): void {
                            startRenaming(resource.id);
                        },
                        viewProperties(res: UFile): void {
                            navigate(AppRoutes.resource.properties(FilesApi.routingNamespace, res.id))
                        },
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        api: FilesApi,
                        isCreating: false,
                        onSelectRestriction: opts?.selection?.show ? file => {
                            const restriction = opts.selection!.show!(file);
                            if (typeof restriction === "string") return false;
                            return restriction && !file.id.endsWith(fakeFileName);
                        } : undefined,
                        onSelect: opts?.selection?.onClick,
                    };

                    return callbacks;
                });

                // Rendering of rows and empty pages
                // =========================================================================================================
                const renderFileIconFromProperties = async (
                    extension: string,
                    isDirectory: boolean,
                    hint?: FileIconHint
                ): Promise<string> => {
                    const hasExt = !!extension;
                    const type = extension ? extensionType(extension.toLocaleLowerCase()) : "binary";

                    const width = 60;
                    const height = 60;

                    if (hint || isDirectory) {
                        let name: IconName;
                        let color: ThemeColor = "FtFolderColor";
                        let color2: ThemeColor = "FtFolderColor2";
                        switch (hint) {
                            case "DIRECTORY_JOBS":
                                name = "ftResultsFolder";
                                break;
                            case "DIRECTORY_SHARES":
                                name = "ftSharesFolder";
                                break;
                            case "DIRECTORY_STAR":
                                name = "ftFavFolder";
                                break;
                            case "DIRECTORY_TRASH":
                                color = "errorMain";
                                color2 = "errorLight";
                                name = "trash";
                                break;
                            default:
                                name = "ftFolder";
                                break;
                        }

                        return ResourceBrowser.icons.renderIcon({name, color, color2, width, height});
                    }

                    return ResourceBrowser.icons.renderSvg(
                        "file-" + extension,
                        () => <SvgFt color={getCssPropertyValue("FtIconColor")} color2={getCssPropertyValue("FtIconColor2")} hasExt={hasExt}
                            ext={extension} type={type} width={width} height={height} />,
                        width,
                        height
                    );
                };

                const renderFileIcon = (file: UFile): Promise<string> => {
                    const ext = file.id.indexOf(".") !== -1 ? extensionFromPath(file.id) : undefined;
                    const ext4 = ext?.substring(0, 4) ?? "File";
                    return renderFileIconFromProperties(ext4, file.status.type === "DIRECTORY", file.status.icon);
                };

                browser.on("renderRow", (file, row, containerWidth) => {
                    row.container.setAttribute("data-file", file.id);

                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon);

                    if (syncthingConfig?.folders.find(it => it.ucloudPath === file.id)) {
                        const iconWrapper = createHTMLElements({
                            tagType: "div",
                            style: {
                                position: "relative",
                                left: "24px",
                                top: "-2px",
                                backgroundColor: "var(--primaryMain)",
                                height: "12px",
                                width: "12px",
                                padding: "4px",
                                borderRadius: "8px",
                                cursor: "pointer"
                            }
                        });

                        HTMLTooltip(iconWrapper, divHtml("Synchronized with Syncthing"), {tooltipContentWidth: 230})

                        icon.append(iconWrapper);
                        const [syncThingIcon, setSyncthingIcon] = ResourceBrowser.defaultIconRenderer();
                        setIconStyling(syncThingIcon);
                        iconWrapper.appendChild(syncThingIcon);
                        ResourceBrowser.icons.renderIcon({name: "check", color: "fixedWhite", color2: "fixedWhite", width: 30, height: 30}).then(setSyncthingIcon);
                    }

                    const title = ResourceBrowser.defaultTitleRenderer(fileName(file.id), row);
                    row.title.append(title);

                    if (isReadonly(file.permissions.myself)) {
                        const iconWrapper = createHTMLElements({
                            tagType: "div",
                            style: {
                                position: "relative",
                                left: "24px",
                                top: "22px",
                                backgroundColor: "var(--primaryMain)",
                                height: "12px",
                                width: "12px",
                                padding: "4px",
                                borderRadius: "8px",
                                cursor: "pointer"
                            }
                        });

                        HTMLTooltip(iconWrapper, divHtml("Read-only"), {tooltipContentWidth: 108})

                        icon.append(iconWrapper);
                        const [readonlyIcon, setReadonlyIcon] = ResourceBrowser.defaultIconRenderer();
                        setIconStyling(readonlyIcon);
                        iconWrapper.appendChild(readonlyIcon);
                        ResourceBrowser.icons.renderIcon({name: "heroInformationCircle", color: "fixedWhite", color2: "fixedWhite", width: 30, height: 30}).then(setReadonlyIcon);
                    }

                    const modifiedAt = file.status.modifiedAt ?? file.status.accessedAt ?? timestampUnixMs();
                    row.stat2.replaceChildren(createHTMLElements({
                        tagType: "div",
                        style: {marginTop: "auto", marginBottom: "auto"},
                        innerText: opts?.selection ?
                            dateToDateStringOrTime(modifiedAt) :
                            row.stat2.innerText = dateToString(modifiedAt)
                    }));

                    if (opts?.selection && !file.id.endsWith(fakeFileName) /* Note(Jonas): Disallow using folder being created */) {
                        const button = browser.defaultButtonRenderer(opts.selection, file);
                        if (button) {
                            row.stat3.replaceChildren(button);
                        }
                    } else {
                        if (file.status.sizeIncludingChildrenInBytes != null || file.status.type !== "DIRECTORY") {
                            row.stat3.replaceChildren(createHTMLElements({
                                tagType: "div",
                                style: {marginTop: "auto", marginBottom: "auto"},
                                innerText: sizeToString(file.status.sizeIncludingChildrenInBytes ?? file.status.sizeInBytes ?? null)
                            }));
                        }
                    }

                    const isOutOfDate = () => row.container.getAttribute("data-file") !== file.id;

                    const favoriteIcon = favoriteRowIcon(row);

                    findFavoriteStatus(file).then(async isFavorite => {
                        const filledStarColor: ThemeColor = "favoriteColor";
                        const unfilledStarColor: ThemeColor = "favoriteColorEmpty";
                        const icon = await ResourceBrowser.icons.renderIcon({
                            name: (isFavorite ? "starFilled" : "starEmpty"),
                            color: (isFavorite ? filledStarColor : unfilledStarColor),
                            color2: "iconColor2",
                            height: 64,
                            width: 64
                        });

                        row.star.setAttribute("data-favorite", isFavorite.toString());

                        if (isOutOfDate()) return;

                        favoriteIcon.src = icon;
                    });

                    findSensitivity(file).then(sensitivity => {
                        if (isOutOfDate()) return;
                        row.stat1.innerHTML = ""; // NOTE(Dan): Clear the container regardless
                        if (!sensitivity) return;

                        const badge = divHtml("");
                        badge.classList.add("sensitivity-badge");
                        badge.classList.add(sensitivity.toString().toUpperCase());
                        badge.innerText = sensitivity.toString().charAt(0);
                        badge.style.cursor = "pointer";
                        badge.onclick = () => addFileSensitivityDialog(file, call => callAPI(call), () => {
                            browserRef.current?.refresh();
                        });

                        HTMLTooltip(badge, divHtml("File's sensitivity is " + sensitivity.toString().toLocaleLowerCase()));
                        row.stat1.append(badge);
                    });

                    renderFileIcon(file).then(url => {
                        if (isOutOfDate()) return;
                        setIcon(url);
                    });
                });

                ResourceBrowser.icons.renderIcon({
                    name: "ftFolder",
                    color: "FtFolderColor",
                    color2: "FtFolderColor2",
                    height: 256,
                    width: 256
                }).then(icon => {
                    const fragment = document.createDocumentFragment();
                    fragment.append(image(icon, {height: 60, width: 60}));
                    browser.defaultEmptyGraphic = fragment;
                });

                browser.on("renderEmptyPage", reason => {
                    // NOTE(Dan): The reasons primarily come from the prefetch() function which fetches the data. If you
                    // want to recognize new error codes, then you should add the logic in prefetch() first.
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your files...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            e.reason.append("This folder is empty");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to this folder. " +
                                "Check to see if it still exists and you have the appropriate permissions to access it.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("This folder is currently unavailable, try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }

                    if (reason.information === "Invalid file type") {
                        navigate(AppRoutes.files.preview(browser.currentPath));
                    }
                });

                // Rendering of breadcrumbs and the location bar
                // =========================================================================================================
                browser.on("generateBreadcrumbs", path => {
                    if (path === SEARCH) {
                        // Disallow locationbar when we are in search-mode.
                        browser.features.locationBar = false;
                        return [{absolutePath: SEARCH, title: `Search results for "${browser.searchQuery}"`}]
                    }

                    collectionCacheForCompletion.retrieve("", () =>
                        callAPI(FileCollectionsApi.browse({
                            itemsPerPage: 250,
                            filterMemberFiles: "all",
                        })).then(res => res.items)
                    ).then(doNothing);

                    // Note(Jonas): Allow locationbar as we are not in search mode.
                    browser.features.locationBar = true;

                    const components = pathComponents(path);
                    const collection = collectionCache.retrieveFromCacheOnly(components[0]);
                    const collectionName = collection ?
                        `${collection.specification.title} (${components[0]})` :
                        components[0];

                    let builder = "";
                    const result: {title: string, absolutePath: string}[] = [];
                    for (let i = 0; i < components.length; i++) {
                        const component = components[i];
                        builder += "/";
                        builder += component;

                        result.push({title: i === 0 ? collectionName : component, absolutePath: builder});
                    }

                    let pIcon = browser.header.querySelector("div.header-first-row > div.provider-icon");
                    if (!pIcon) {
                        const disallowNavigation = opts?.isModal || opts?.embedded;
                        const providerIconWrapper = createHTMLElements({
                            tagType: "div",
                            className: "provider-icon",
                            style: disallowNavigation ? {} : {cursor: "pointer"}
                        });
                        if (!disallowNavigation) providerIconWrapper.title = "Go to drives";
                        providerIconWrapper.style.marginRight = "6px";
                        const navbar = browser.header.querySelector("div.header-first-row");
                        if (navbar) navbar.prepend(providerIconWrapper);
                        if (!disallowNavigation) {
                            providerIconWrapper.onclick = () => {
                                navigate(AppRoutes.files.drives());
                            }
                        }
                        pIcon = providerIconWrapper;
                    }

                    pIcon.replaceChildren();

                    if (pIcon) {
                        const icon = providerIcon(collection?.specification.product.provider ?? "", {
                            fontSize: "22px", width: "35px", height: "35px"
                        });
                        icon.style.marginRight = "8px";
                        pIcon.replaceChildren(icon);
                    }

                    if (browser.opts.embedded || browser.opts.selector) {
                        if (!browser.header.querySelector("div.header-first-row > div.drive-icon-dropdown")) {
                            const [driveIcon, setDriveIcon] = ResourceBrowser.defaultIconRenderer();
                            driveIcon.className = "drive-icon-dropdown";
                            driveIcon.style.cursor = "pointer";
                            driveIcon.style.minWidth = "18px";
                            driveIcon.style.width = "18px";
                            const url = browser.header.querySelector("div.header-first-row");
                            url?.prepend(driveIcon);
                            browser.header.setAttribute("shows-dropdown", "");

                            ResourceBrowser.icons.renderIcon({name: "chevronDownLight", color: "textPrimary", color2: "textPrimary", height: 32, width: 32}).then(setDriveIcon);
                            driveIcon.onclick = e => {
                                e.stopImmediatePropagation();
                                const rect = driveIcon.getBoundingClientRect();
                                temporaryDriveDropdownFunction(browser, rect.x, rect.y + rect.height);
                            }
                        }
                    }

                    return result;
                });

                browser.on("renderLocationBar", prompt => {
                    let path = prompt;
                    if (path.length === 0) return {rendered: path, normalized: path};
                    if (path.startsWith("/")) path = path.substring(1);
                    let endOfFirstComponent = path.indexOf("/");
                    if (endOfFirstComponent === -1) endOfFirstComponent = path.length;

                    let collectionId: string | null = null;

                    const firstComponent = path.substring(0, endOfFirstComponent);
                    if (firstComponent === "~") {
                        const currentComponents = pathComponents(browser.currentPath);
                        if (currentComponents.length > 0) collectionId = currentComponents[0];
                    } else {
                        const parenthesisStart = firstComponent.indexOf("(");
                        const parenthesisEnd = firstComponent.indexOf(")");
                        if (parenthesisStart !== -1 && parenthesisEnd !== -1 && parenthesisStart < parenthesisEnd) {
                            const parsedNumber = parseInt(firstComponent.substring(parenthesisStart + 1, parenthesisEnd));
                            if (!isNaN(parsedNumber) && parsedNumber > 0) {
                                collectionId = parsedNumber.toString();
                            }
                        } else {
                            const parsedNumber = parseInt(firstComponent);
                            if (!isNaN(parsedNumber) && parsedNumber > 0) {
                                collectionId = parsedNumber.toString();
                            }
                        }
                    }

                    if (collectionId === null) {
                        return {rendered: prompt, normalized: prompt};
                    }

                    let collection = collectionCache.retrieveFromCacheOnly(collectionId);
                    if (collection === undefined) {
                        const entries = collectionCacheForCompletion.retrieveFromCacheOnly("") ?? [];
                        for (const entry of entries) {
                            if (entry.id === collectionId) {
                                collection = entry;
                                break;
                            }
                        }
                    }
                    const collectionName = collection ? `${collection.specification.title} (${collectionId})` : collectionId;
                    const remainingPath = path.substring(endOfFirstComponent);

                    return {
                        rendered: `/${collectionName}${remainingPath}`,
                        normalized: `/${collectionId}${remainingPath}`
                    };
                });

                browser.on("generateTabCompletionEntries", (pathPrefix, allowFetch) => {
                    const parentPath =
                        pathPrefix === "/" ?
                            "/" :
                            pathPrefix.endsWith("/") ?
                                pathPrefix :
                                resolvePath(getParentPath(pathPrefix));

                    let autoCompletionEntries: string[] | null;
                    let page: UFile[] | FileCollection[] | null;
                    if (parentPath === "/") {
                        // Auto-complete drives
                        const collections = collectionCacheForCompletion.retrieveFromCacheOnly("") ?? null;
                        page = collections;
                        autoCompletionEntries = collections?.map(it => it.specification.title.toLowerCase()) ?? null;
                    } else {
                        // Auto-complete folders
                        const cached = browser.cachedData[parentPath]?.filter(it => it.status.type === "DIRECTORY");
                        page = cached ?? null;
                        autoCompletionEntries = cached?.map(it => fileName(it.id).toLowerCase()) ?? null;
                    }

                    if (page == null || autoCompletionEntries == null) {
                        if (!allowFetch) return [];

                        if (parentPath === "/") {
                            return collectionCacheForCompletion.retrieve("", () =>
                                callAPI(
                                    FileCollectionsApi.browse({
                                        itemsPerPage: 250,
                                        filterMemberFiles: "all",
                                        ...opts?.additionalFilters
                                    })
                                ).then(res => res.items)
                            ).then(doNothing);
                        } else {
                            return prefetch(parentPath).then(doNothing);
                        }
                    }

                    const fileNamePrefix = pathPrefix.endsWith("/") ?
                        "" :
                        fileName(pathPrefix).toLowerCase();

                    const result: string[] = [];
                    for (let i = 0; i < autoCompletionEntries.length; i++) {
                        const entry = autoCompletionEntries[i];
                        if (entry.startsWith(fileNamePrefix)) {
                            const match = page[i]["id"];
                            result.push(match);
                        }
                    }

                    return result;
                });

                // Network requests
                // =========================================================================================================
                const lastFetch: Record<string, number> = {};
                const inflightRequests: Record<string, Promise<boolean>> = {};


                const prefetch = (path: string): Promise<boolean> => {
                    // TODO(Dan): We can probably replace this with AsyncCache?
                    const now = timestampUnixMs();
                    if (now - (lastFetch[path] ?? 0) < 500) return inflightRequests[path] ?? Promise.resolve(true);
                    lastFetch[path] = now;
                    delete browser.emptyReasons[path];

                    const promise =
                        callAPI(
                            FilesApi.browse({
                                path,
                                ...defaultRetrieveFlags,
                                ...translateFilters(browser.browseFilters),
                                ...opts?.additionalFilters
                            })
                        )
                            .then(result => {
                                browser.registerPage(result, path, true);
                                return false;
                            }).catch(err => {
                                // TODO(Dan): This partially contains logic which can be re-used.
                                const statusCode = err["request"]?.["status"] ?? 500;
                                const errorCode: string | null = err["response"]?.["errorCode"]?.toString() ?? null;
                                const errorWhy: string | null = err["response"]?.["why"]?.toString() ?? null;

                                let tag: EmptyReasonTag;
                                if (errorCode === "MAINTENANCE") {
                                    tag = EmptyReasonTag.UNABLE_TO_FULFILL;
                                } else if (statusCode < 500) {
                                    tag = EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS;
                                } else {
                                    tag = EmptyReasonTag.UNABLE_TO_FULFILL;
                                }

                                browser.emptyReasons[path] = {
                                    tag,
                                    information: errorWhy ?? undefined
                                };

                                return false;
                            })
                            .finally(() => delete inflightRequests[path]);

                    inflightRequests[path] = promise;
                    return promise;
                };

                browser.on("skipOpen", (oldPath, newPath, resource) => resource?.id === fakeFileName);
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource?.status.type === "FILE") {
                        if (opts?.selection) {
                            const doShow = opts.selection.show ?? (() => true);
                            if (doShow(resource)) {
                                opts.selection.onClick(resource);
                            }
                            browser.open(oldPath, true);
                            return;
                        }
                        navigate(`/files/properties/${encodeURIComponent(resource.id)}/`)
                        return;
                    }

                    if (newPath !== SEARCH) lastActiveFilePath = newPath;

                    if (openTriggeredByPath.current === newPath) {
                        openTriggeredByPath.current = null;
                    } else if (!isSelector) {
                        //                                                     Note(Jonas): Edge case that we want to navigate to FileTable on breadcrumb click (Job/View)
                        if (!isInitialMount.current && (oldPath !== newPath || opts?.embedded)) {
                            navigate("/files?path=" + encodeURIComponent(newPath));
                        }
                    }

                    if (!isSelector) {
                        dispatch({
                            type: "GENERIC_SET", payload: {
                                property: "uploadPath",
                                newValue: newPath, defaultValue: newPath
                            }
                        });
                    }

                    const collectionId = pathComponents(newPath)[0];

                    folderCache
                        .retrieve(newPath, () => callAPI(FilesApi.retrieve({id: newPath})))
                        .then(() => browser.renderOperations());

                    collectionCache
                        .retrieve(collectionId, () => callAPI(
                            FileCollectionsApi.retrieve({
                                id: collectionId,
                                includeOthers: true,
                                includeSupport: true,
                                ...opts?.additionalFilters
                            })
                        )).then(() => {
                            if (!opts?.embedded) {
                                const collection = collectionCache.retrieveFromCacheOnly(collectionId);
                                if (!collection?.specification.product.provider) return;

                                Sync.fetchProducts(collection.specification.product.provider).then(products => {
                                    if (products.length > 0) {
                                        syncthingProduct = products[0];
                                        if (didUnmount.current) return;
                                        Sync.fetchConfig(syncthingProduct?.product.category.provider)
                                            .then(config => {
                                                syncthingConfig = config
                                                browser.renderRows();
                                                browser.renderOperations();
                                            }).catch(doNothing);
                                    }
                                });
                            }

                            browser.renderBreadcrumbs();
                            browser.renderOperations();
                            browser.locationBar.dispatchEvent(new Event("input"));
                        });

                    prefetch(newPath).then(wasCached => {
                        // NOTE(Dan): When wasCached is true, then the previous renderPage() already had the correct data.
                        if (wasCached) return;
                        if (browser.currentPath !== newPath) return;
                        browser.renderRows();
                    });

                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        FilesApi.browse({
                            path,
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...translateFilters(browser.browseFilters),
                            ...opts?.additionalFilters
                        })
                    );

                    if (path !== browser.currentPath) return;

                    browser.registerPage(result, path, false);
                });

                browser.on("fetchBrowserFeatures", () => {
                    return [{name: "Rename", shortcut: {keys: "F2"}}];
                });

                browser.on("search", query => {
                    let currentPath = browser.currentPath;
                    if (currentPath === SEARCH) currentPath = searching;
                    else lastActiveFilePath = currentPath;

                    browser.emptyReasons[SEARCH] = {
                        tag: EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS,
                        information: browser.searchQuery ? "" : "Search query is empty."
                    };

                    searching = currentPath;
                    browser.open(SEARCH);
                    browser.cachedData[SEARCH] = [];
                    browser.searchQuery = query;
                    browser.renderRows();
                    browser.renderOperations();
                    browser.renderBreadcrumbs();
                    const connection = WSFactory.open(
                        "/files",
                        {
                            reconnect: false,
                            init: async (conn) => {
                                try {
                                    await conn.subscribe({
                                        call: "files.streamingSearch",
                                        payload: {
                                            query,
                                            flags: {},
                                            currentFolder: currentPath
                                        },
                                        handler: (message) => {
                                            if (browser.currentPath !== SEARCH) {
                                                connection.close();
                                                return;
                                            }

                                            if (message.payload["type"] === "result") {
                                                const result = message.payload["batch"];
                                                // TODO(Jonas): Handle page change before adding results.
                                                const data = browser.cachedData[browser.currentPath] ?? [];
                                                data.push(...result);
                                                browser.renderRows();
                                                browser.renderOperations();
                                            } else if (message.payload["type"] === "end_of_results") {
                                                connection.close();
                                            }
                                        }
                                    });
                                } catch (e) {
                                    displayErrorMessageOrDefault(e, "Failed to fetch search results.");
                                }
                            }
                        }
                    )
                });

                browser.on("searchHidden", () => {
                    browser.open(lastActiveFilePath, true);
                });

                // Event handlers related to user input
                // =========================================================================================================
                // This section includes handlers for clicking various UI elements and handling shortcuts.
                browser.on("unhandledShortcut", (ev) => {
                    let didHandle = true;
                    if (ev.ctrlKey || ev.metaKey) {
                        switch (ev.code) {
                            case "Backspace": {
                                performMoveToTrash(browser.findSelectedEntries());
                                break;
                            }
                            default: {
                                didHandle = false;
                                break;
                            }
                        }
                    } else if (ev.altKey) {
                        switch (ev.code) {
                            default: {
                                didHandle = false;
                                break;
                            }
                        }
                    } else {
                        switch (ev.code) {
                            case "Delete": {
                                performMoveToTrash(browser.findSelectedEntries());
                                break;
                            }

                            case "F2": {
                                const selected = browser.findSelectedEntries();
                                if (selected.length === 1) {
                                    startRenaming(selected[0].id);
                                }
                                break;
                            }

                            default: {
                                didHandle = false;
                                break;
                            }
                        }
                    }

                    if (didHandle) {
                        ev.preventDefault();
                        ev.stopPropagation();
                    }
                });

                browser.on("copy", (entries, target) => copyOrMove(entries, target, false));
                browser.on("move", (entries, target) => copyOrMove(entries, target, true));

                browser.on("starClicked", (entry) => {
                    (async () => {
                        const currentStatus = await findFavoriteStatus(entry);
                        await setFavoriteStatus(entry, !currentStatus);
                    })();
                });

                // Drag-and-drop
                // =========================================================================================================
                browser.on("validDropTarget", file => file.status.type === "DIRECTORY");
                browser.on("renderDropIndicator", (selectedFiles, target) => {
                    const content = browser.entryDragIndicatorContent;
                    if (selectedFiles.length === 0) {
                        content.append("No files");
                        return;
                    }

                    const icon = image(placeholderImage, {width: 16, height: 16});
                    content.append(icon);
                    renderFileIcon(selectedFiles[0]).then(url => icon.src = url);

                    let text: string;
                    if (selectedFiles.length === 1) {
                        if (target) text = `${fileName(selectedFiles[0].id).substring(0, 30)} into ${fileName(target).substring(0, 30)}`
                        else text = fileName(selectedFiles[0].id).substring(0, 50);
                    } else {
                        text = `${selectedFiles.length} files`;
                        if (target) text += ` into ${fileName(target).substring(0, 30)}`;
                    }

                    content.append(visualizeWhitespaces(text));
                });

                // Utilities required for the ResourceBrowser to understand the structure of the file-system
                // =========================================================================================================
                // This usually includes short functions which describe when certain actions should take place and what
                // the internal structure of a file is.
                browser.on("pathToEntry", f => f.id);
                browser.on("nameOfEntry", f => fileName(f.id));
                browser.on("sort", page => {
                    const sortBy = browser.browseFilters["sortBy"] as SortById;
                    // Note(Jonas): Default to "ascending" behavior if none is set.
                    const descending = browser.browseFilters["sortDirection"] === "descending";
                    if (sortBy === "MODIFIED_AT") {
                        if (descending) {
                            return page.sort((b, a) => (a.status.modifiedAt ?? 0) - (b.status.modifiedAt ?? 0));
                        } else {
                            return page.sort((a, b) => (a.status.modifiedAt ?? 0) - (b.status.modifiedAt ?? 0));
                        }
                    } else if (sortBy === "SIZE") {
                        if (descending) {
                            return page.sort((b, a) => (a.status.sizeInBytes ?? 0) - (b.status.sizeInBytes ?? 0));
                        } else {
                            return page.sort((a, b) => (a.status.sizeInBytes ?? 0) - (b.status.sizeInBytes ?? 0));
                        }
                    }

                    // sortBy === undefined || sortBy === "PATH"
                    if (descending) {
                        return page.sort((b, a) => a.id.localeCompare(b.id));
                    } else {
                        return page.sort((a, b) => a.id.localeCompare(b.id));
                    }
                });
            });
        }

        const b = browserRef.current;
        if (b) {
            b.header.setAttribute("data-no-gap", "");
            b.renameField.style.left = "74px";
        }

        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround, setLocalProject ? {setLocalProject, initialProject: activeProject.current} : undefined);
    }, []);

    const setLocalProject = opts?.managesLocalProject ? (projectId?: string) => {
        const b = browserRef.current;
        if (b) {
            b.canConsumeResources = checkCanConsumeResources(projectId ?? null, {api: FilesApi});
        }
        activeProject.current = projectId;
        clearAndFetchCollections();
    } : undefined;

    useLayoutEffect(() => {
        const b = browserRef.current;
        if (!b) return;

        if (opts?.initialPath !== undefined) {
            b.canConsumeResources = checkCanConsumeResources(Client.projectId ?? null, {api: FilesApi});

            if (selectorPathRef.current === "") {
                clearAndFetchCollections();
            } else {
                b.open(selectorPathRef.current);
            }
        } else {
            const path = getQueryParamOrElse(location.search, "path", "");
            if (path) {
                openTriggeredByPath.current = path;
                b.open(path);
            }
        }
    }, [location.search]);

    if (!opts?.isModal && !opts?.embedded) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />;


    function clearAndFetchCollections() {
        const b = browserRef.current;
        if (!b) return;

        collectionCacheForCompletion.invalidateAll();
        collectionCacheForCompletion.retrieveWithInvalidCache("", () => callAPI(
            FileCollectionsApi.browse({
                itemsPerPage: 250,
                filterMemberFiles: "all",
                ...opts?.additionalFilters,
            })
        ).then(res => {
            const [first] = res.items;
            if (first) {
                selectorPathRef.current = first.id;
                b.open(selectorPathRef.current);
            }
            b.dispatchMessage("generateBreadcrumbs", f => f(selectorPathRef.current));
            return res.items;
        }));
    }
}

export default FileBrowse;

function matchesFilter(filter: string, collection: FileCollection): boolean {
    if (!filter) return true;
    const lCFilter = filter.toLocaleLowerCase();
    const title = collection.specification.title.toLocaleLowerCase();
    const id = collection.id;
    return title.toLocaleLowerCase().includes(lCFilter) || id.includes(lCFilter);
}

// Note(Jonas): Temporary as there should be a better solution, not because the element is temporary
function temporaryDriveDropdownFunction(browser: ResourceBrowser<unknown>, posX: number, posY: number): void {
    const filteredCollections = collectionCacheForCompletion.retrieveFromCacheOnly("") ?? [];

    function generateElements(filter?: string): HTMLLIElement[] {
        const result = filteredCollections.filter(it => matchesFilter(filter ?? "", it)).map(collection => {
            const wrapper = document.createElement("li");
            const pIcon = providerIcon(collection.specification.product.provider, {width: "30px", height: "30px", fontSize: "22px"});
            wrapper.append(pIcon);
            const span = document.createElement("span");
            wrapper.append(span);
            span.innerText = `${collection.specification.title} (${collection.id})`;
            span.className = TruncateClass;
            return wrapper;
        });
        if (result.length !== 0) return result;

        return [createHTMLElements<HTMLLIElement>({
            tagType: "li",
            children: [],
            innerText: "No results found",
            style: {pointerEvents: "none"}
        })];
    }

    function generateHandlers(filter: string): (() => void)[] {
        return filteredCollections.filter(it => matchesFilter(filter, it)).map(collection => {
            return () => {
                if (collection.id !== browser.currentPath.split("/").filter(it => it)[0]) {
                    browser.open(`/${collection.id}`);
                }
                browser.closeContextMenu();
            }
        });
    }

    const ROW_HEIGHT = 46;
    const maxHeight = ROW_HEIGHT * 10 + ROW_HEIGHT / 2;

    const onKeyUp = function onKeyUp(filter: string) {
        const elements = generateElements(filter);
        browser.setToContextMenuEntries(elements, generateHandlers(filter), true, maxHeight);
    }

    browser.prepareContextMenu(posX, posY, filteredCollections.length, maxHeight);
    browser.addContextMenuSearchField(onKeyUp);
    browser.setToContextMenuEntries(generateElements(undefined), generateHandlers(""), true, maxHeight);
}

function setIconStyling(icon: HTMLDivElement) {
    icon.style.height = "8px";
    icon.style.width = "8px";
    icon.style.minWidth = icon.style.minHeight = "";
    icon.style.marginLeft = "-2px";
    icon.style.marginTop = "-2px";
    icon.style.display = "block";
}