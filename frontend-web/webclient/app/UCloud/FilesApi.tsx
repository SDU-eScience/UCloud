import {
    CREATE_TAG,
    DELETE_TAG,
    Permission,
    PERMISSIONS_TAG,
    ResourceApi,
    ResourceApiActions,
    ResourceBrowseCallbacks,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";
import {BulkRequest, BulkResponse, PageV2} from "@/UCloud/index";
import FileCollectionsApi, {FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {
    Box,
    Button,
    ExternalLink,
    Flex,
    FtIcon,
    Icon,
    MainContainer,
    Markdown,
    Select,
    Text,
    TextArea,
    Truncate
} from "@/ui-components";
import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {fileName, resolvePath, getParentPath, readableUnixMode, sizeToString} from "@/Utilities/FileUtilities";
import {
    bulkRequestOf,
    copyToClipboard,
    displayErrorMessageOrDefault,
    doNothing,
    errorMessageOrDefault,
    extensionFromPath,
    ExtensionType,
    extensionType,
    inDevEnvironment,
    onDevSite,
    prettierString,
    removeTrailingSlash,
    stopPropagation,
    typeFromMime
} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {Operation, operationsToActions, ShortcutKey} from "@/ui-components/Operation";
import {ActionEntry, ActionItem, CommonActionShortcut} from "@/ui-components/Actions";
import {dialogStore} from "@/Dialog/DialogStore";
import {ItemRenderer} from "@/ui-components/Browse";
import {prettyFilePath, usePrettyFilePath} from "@/Files/FilePath";
import {launchOpenWithFastPath, OpenWithBrowser, OpenWithFastPath} from "@/Applications/OpenWith";
import {addStandardDialog, addStandardInputDialog} from "@/UtilityComponents";
import {ProductStorage} from "@/Accounting";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {Client} from "@/Authentication/HttpClientInstance";
import {apiCreate, apiUpdate, callAPI, InvokeCommand, useCloudAPI} from "@/Authentication/DataHook";
import metadataDocumentApi from "@/UCloud/MetadataDocumentApi";
import {Spacer} from "@/ui-components/Spacer";
import metadataNamespaceApi from "@/UCloud/MetadataNamespaceApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {SyncthingConfig, SyncthingDevice, SyncthingFolder} from "@/Syncthing/api";
import {Link, useParams} from "react-router-dom";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {addShareModal} from "@/Files/Shares";
import FileBrowse from "@/Files/FileBrowse";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import {filetypeinfo as fileType} from "magic-bytes.js";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";
import {
    FilesCopyRequestItem,
    FilesCreateDownloadRequestItem,
    FilesCreateDownloadResponseItem,
    FilesCreateFolderRequestItem,
    FilesCreateUploadRequestItem,
    FilesEmptyTrashRequestItem,
    FilesMoveRequestItem,
    FilesTransferRequestItem,
    FilesTrashRequestItem,
    FilesVisualizeRequest,
    FilesVisualizeResponse,
    UFile,
    UFileIncludeFlags,
    UFileSpecification,
    UFileStatus
} from "./UFile";
import AppRoutes from "@/Routes";
import {allowEditing, Editor, EditorApi, EditorLoadingState, Vfs} from "@/Editor/Editor";
import {IconButton} from "@/ui-components/IconButton";
import {CopyButton} from "@/ui-components/CopyButton";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {useDispatch} from "react-redux";
import {VirtualFile} from "@/Files/FileTree";
import {dateToString} from "@/Utilities/DateUtilities";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {FileWriteFailure, WriteFailureEvent} from "@/Files/Uploader";
import {GuessedFile} from "magic-bytes.js/dist/model/tree";
import {sendFailureNotification, sendInformationNotification, sendSuccessNotification} from "@/Notifications";
import {terminalOpen, terminalOpenTab} from "@/Terminal/State";
import {genericSet} from "@/Utilities/ReduxHooks";
import {registerJobBackgroundTask} from "@/Services/BackgroundTasks/JobBackgroundTask";
import {UcxSpinner} from "@/UCX/UcxView";

export function normalizeDownloadEndpoint(endpoint: string): string {
    const e = endpoint.replace("integration-module:8889", "localhost:8889");
    const queryParameter = `usernameHint=${b64EncodeUnicode(Client.activeUsername!)}`;
    if (e.indexOf("?") !== -1) {
        return e + "&" + queryParameter;
    } else {
        return e + "?" + queryParameter;
    }
}

export interface ExtraFileCallbacks {
    collection?: FileCollection;
    directory?: UFile;
    isModal?: boolean;
    startFileCreation(): void;
    startFolderCreation(): void;
    isSearch: boolean;
    // HACK(Jonas): This is because resource view is technically embedded, but is not in dialog, so it's allowed in
    // special case.
    allowMoveCopyOverride?: boolean;
    syncthingConfig?: SyncthingConfig;
    setSynchronization?: (file: UFile[], shouldAdd: boolean) => void;
    openFile(file: UFile, newWindow: boolean): void;
    copyToClipboard(files: UFile[], cut: boolean): void;
    canPasteFromClipboard(): boolean;
    pasteFromClipboard(): void;
    reloadCurrentFolderIfUnpaginated(path: string): void;
}

export type FileBrowseCallbacks = ResourceBrowseCallbacks<UFile, ProductStorage> & ExtraFileCallbacks;

const FILE_SYNCHRONIZATION_TAG = "file-synchronization";
const FILE_SELECTED_EMPTY_TRASH_TAG = "file-selected-empty-trash";
const COPY_SHORTCUT: CommonActionShortcut = {code: "KeyC", key: "C", modifier: "primary"};
const CUT_SHORTCUT: CommonActionShortcut = {code: "KeyX", key: "X", modifier: "primary"};
const PASTE_SHORTCUT: CommonActionShortcut = {code: "KeyV", key: "V", modifier: "primary"};
const RENAME_SHORTCUT: CommonActionShortcut = {code: "F2", key: "F2"};
const DELETE_SHORTCUT: CommonActionShortcut = {code: "Delete", key: "Delete"};
const COMPRESSION_FAST_PATH: OpenWithFastPath = {
    application: {name: "archiver"},
    machine: {preferredVcpuCount: 4}
};
const UNCOMPRESSION_FAST_PATH: OpenWithFastPath = {
    application: {name: "unarchiver"},
    machine: {preferredVcpuCount: 4}
};

const COMPRESSION_FORMATS = [
    {parameter: "ZIP", extension: ".zip"},
    {parameter: "GZIP", extension: ".tar.gz"},
    {parameter: "XZ", extension: ".tar.xz"},
    {parameter: "7Z", extension: ".7z"}
] as const;

type CompressionFormat = typeof COMPRESSION_FORMATS[number];

async function startCompression(
    folder: UFile,
    callbacks: FileBrowseCallbacks,
    format: CompressionFormat
): Promise<void> {
    const archiveName = `${fileName(removeTrailingSlash(folder.id))}${format.extension}`;
    const result = await launchOpenWithFastPath(folder, {
        ...COMPRESSION_FAST_PATH,
        parameters: {
            format: {type: "text", value: format.parameter}
        }
    });

    registerJobBackgroundTask({
        jobId: result.jobId,
        projectId: result.projectId,
        display: {
            icon: "heroArchiveBox",
            title: `Creating ${archiveName}`,
            runningTitle: `Creating ${archiveName}`,
            cancelTitle: "Stop compression?",
            cancelMessage: `Stop creating ${archiveName}?`,
            startingMessage: `Starting compression of ${archiveName}...`,
            successNotification: `${archiveName} is ready`,
            failureNotification: `Could not create ${archiveName}`,
            stateMessages: {
                IN_QUEUE: "Waiting for a machine",
                RUNNING: "Compressing archive",
                CANCELING: "Stopping compression",
                SUCCESS: "Archive created",
                FAILURE: "Compression failed",
            }
        },
        onSuccess: () => callbacks.reloadCurrentFolderIfUnpaginated(resolvePath(getParentPath(folder.id)))
    });
}

function isCompressedFile(file: UFile): boolean {
    const name = fileName(file.id).toLowerCase();
    return file.status.type === "FILE" && COMPRESSION_FORMATS.some(format => name.endsWith(format.extension));
}

async function startUncompression(file: UFile, callbacks: FileBrowseCallbacks): Promise<void> {
    const archiveName = fileName(file.id);
    const result = await launchOpenWithFastPath(file, UNCOMPRESSION_FAST_PATH);

    registerJobBackgroundTask({
        jobId: result.jobId,
        projectId: result.projectId,
        display: {
            icon: "heroArchiveBox",
            title: `Uncompressing ${archiveName}`,
            runningTitle: `Uncompressing ${archiveName}`,
            cancelTitle: "Stop uncompression?",
            cancelMessage: `Stop uncompressing ${archiveName}?`,
            startingMessage: `Starting uncompression of ${archiveName}...`,
            successNotification: `${archiveName} has been uncompressed`,
            failureNotification: `Could not uncompress ${archiveName}`,
            stateMessages: {
                IN_QUEUE: "Waiting for a machine",
                RUNNING: "Uncompressing archive",
                CANCELING: "Stopping uncompression",
                SUCCESS: "Archive uncompressed",
                FAILURE: "Uncompression failed",
            }
        },
        onSuccess: () => callbacks.reloadCurrentFolderIfUnpaginated(resolvePath(getParentPath(file.id)))
    });
}

export function hasReadPermission(permissions: Permission[]): boolean {
    return permissions.some(permission => permission === "READ" || permission === "EDIT" || permission === "ADMIN");
}

export function hasReadAndWritePermission(permissions: Permission[]): boolean {
    return permissions.some(permission => permission === "EDIT" || permission === "ADMIN");
}

export function hasAdminPermission(permissions: Permission[]): boolean {
    return permissions.includes("ADMIN");
}

function canCopyFiles(selected: UFile[]): boolean {
    return selected.length > 0 && selected.every(file => hasReadPermission(file.permissions.myself));
}

function canCutFiles(selected: UFile[], callbacks: FileBrowseCallbacks): boolean | string {
    if (callbacks.isSearch || selected.length === 0) return false;
    if ((callbacks.collection?.status.resolvedSupport?.support as FileCollectionSupport | undefined)?.files.isReadOnly) {
        return "File system is read-only";
    }
    return selected.every(file => hasReadAndWritePermission(file.permissions.myself)) &&
        selected.every(file => file.status.icon !== "DIRECTORY_TRASH");
}

function canPasteFiles(selected: UFile[], callbacks: FileBrowseCallbacks): boolean | string {
    if (selected.length !== 0 || callbacks.isSearch || !callbacks.canPasteFromClipboard()) return false;
    if ((callbacks.collection?.status.resolvedSupport?.support as FileCollectionSupport | undefined)?.files.isReadOnly) {
        return "File system is read-only";
    }
    return hasReadAndWritePermission(callbacks.collection?.permissions?.myself ?? []) ||
        "You do not have write permissions in this folder";
}

export function isSensitivitySupported(resource: UFile): boolean {
    // NOTE(Dan): This is a temporary frontend workaround. A proper backend solution will be implemented at a later
    // point in time. For the time being we will simply use a list of supported providers on the frontend. This list
    // contains the known production providers which support sensitive data. This list will also contain some "fake"
    // providers which are known to be used in development builds.
    if (inDevEnvironment() || onDevSite()) {
        switch (resource.specification.product.provider) {
            case "k8":
            case "K8":
            case "gok8s":
            case "k8s":
            case "ucloud":
                return true;

            default:
                return false;
        }
    } else {
        switch (resource.specification.product.provider) {
            case "ucloud":
                return true;

            default:
                return false;
        }
    }
}

export const FileSensitivityVersion = "1.0.0";
export const FileSensitivityNamespace = "sensitivity";
type SensitivityLevel = | "PRIVATE" | "SENSITIVE" | "CONFIDENTIAL";
let sensitivityTemplateId = "";

async function findSensitivityWithFallback(file: UFile): Promise<SensitivityLevel> {
    return (await findSensitivity(file)) ?? "PRIVATE";
}

export async function findSensitivity(file: UFile): Promise<SensitivityLevel | undefined> {
    if (!isSensitivitySupported(file)) return Promise.resolve("PRIVATE");

    if (!sensitivityTemplateId) {
        sensitivityTemplateId = await findTemplateId(file, FileSensitivityNamespace, FileSensitivityVersion);
        if (!sensitivityTemplateId) {
            return "PRIVATE";
        }
    }
    const entry = file.status.metadata?.metadata[sensitivityTemplateId]?.[0];
    if (entry?.type === "deleted") return undefined;
    return entry?.specification.document.sensitivity;
}

async function findTemplateId(file: UFile, namespace: string, version: string): Promise<string> {
    const template = Object.values(file.status.metadata?.templates ?? {}).find(it =>
        it.namespaceName === namespace && it.version == version
    );

    if (!template) {
        const page = await callAPI<PageV2<FileMetadataTemplateNamespace>>(
            MetadataNamespaceApi.browse({filterName: FileSensitivityNamespace, itemsPerPage: 250})
        );
        if (page.items.length === 0) return "";
        return page.items[0].id;
    }

    return template.namespaceId;
}

function useSensitivity(resource: UFile): SensitivityLevel | null {
    const [sensitivity, setSensitivity] = useState<SensitivityLevel | null>(null);
    useEffect(() => {
        let alive = true;

        (async () => {
            const value = await findSensitivityWithFallback(resource);
            if (alive) setSensitivity(value)
        })();

        return () => {
            alive = false;
        };
    }, []);
    return sensitivity;
}

class FilesApi extends ResourceApi<UFile, ProductStorage, UFileSpecification,
    ResourceUpdate, UFileIncludeFlags, UFileStatus, FileCollectionSupport, FileBrowseCallbacks> {
    constructor() {
        super("files");
        this.sortEntries = [];
    }

    public routingNamespace = "files";
    public title = "File";
    public productType = "STORAGE" as const

    public idIsUriEncoded = true;

    visualize(request: FilesVisualizeRequest): APICallParameters<FilesVisualizeRequest, FilesVisualizeResponse> {
        return apiUpdate(request, "/api/files", "visualize");
    }

    renderer: ItemRenderer<UFile, FileBrowseCallbacks> = {
    };

    private defaultRetrieveFlags: Partial<UFileIncludeFlags> = {
        includeMetadata: true,
        includeSizes: true,
        includeTimestamps: true,
        includeUnixInfo: true,
        allowUnsupportedInclude: true
    };

    public Properties = () => {
        const {id} = useParams<{id?: string}>();

        const [fileData, fetchFile] = useCloudAPI<UFile | null>({noop: true}, null);
        const [loadedFileId, setLoadedFileId] = useState<string>();

        React.useEffect(() => {
            if (!id) return;
            let active = true;
            void fetchFile(this.retrieve({
                id,
                includeUpdates: true,
                includeOthers: true,
                includeSupport: true,
                ...this.defaultRetrieveFlags
            })).finally(() => {
                if (active) setLoadedFileId(id);
            });
            return () => {
                active = false;
            };
        }, [fetchFile, id]);

        const file = fileData.data;

        if (!id) return <MainContainer main={<h1>Missing file id.</h1>} />;
        if (loadedFileId !== id || fileData.loading) {
            return <EditorLoadingState><UcxSpinner /></EditorLoadingState>;
        }
        if (!file) {
            return <EditorLoadingState>
                <h1><Link to={AppRoutes.files.drives()}>File not found. Click here to go to drives.</Link></h1>
            </EditorLoadingState>;
        }

        return <FilePreview initialFile={file} />
    }

    public retrieveActions(): ResourceApiActions<UFile, ProductStorage, FileBrowseCallbacks> {
        const operations = this.retrieveOperations();
        const findOperation = (predicate: (operation: Operation<UFile, FileBrowseCallbacks>) => boolean) => {
            const operation = operations.find(predicate);
            if (!operation) throw new Error("Missing file operation");
            const action = operationsToActions([operation]).actions[0];
            if (action === "divider" || !action) throw new Error("Invalid file operation");
            return action;
        };
        const byText = (text: string) => findOperation(operation => operation.text === text);
        const withOverrides = (
            action: ActionItem<UFile, FileBrowseCallbacks>,
            overrides: Partial<ActionItem<UFile, FileBrowseCallbacks>>
        ): ActionItem<UFile, FileBrowseCallbacks> => ({...action, ...overrides});
        const withoutShortcut = (action: ActionItem<UFile, FileBrowseCallbacks>) =>
            withOverrides(action, {shortcut: undefined});

        const open: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Open",
            enabled: selected => selected.length === 1,
            onClick: ([file], callbacks) => callbacks.openFile(file, false),
        };
        const openInNewWindow: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Open in new window",
            icon: "heroArrowTopRightOnSquare",
            enabled: selected => selected.length === 1,
            onClick: ([file], callbacks) => callbacks.openFile(file, true),
        };
        const copy: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Copy",
            icon: "heroDocumentDuplicate",
            enabled: canCopyFiles,
            onClick: (selected, callbacks) => callbacks.copyToClipboard(selected, false),
            shortcut: COPY_SHORTCUT,
        };
        const cut: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Cut",
            icon: "heroScissors",
            enabled: canCutFiles,
            onClick: (selected, callbacks) => callbacks.copyToClipboard(selected, true),
            shortcut: CUT_SHORTCUT,
        };
        const paste: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Paste",
            icon: "heroClipboard",
            enabled: canPasteFiles,
            onClick: (_, callbacks) => callbacks.pasteFromClipboard(),
            shortcut: PASTE_SHORTCUT,
        };

        const openWithApplication = withOverrides(withoutShortcut(byText("Open with...")), {
            text: "Application",
            icon: undefined,
        });
        const openWith: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Open with...",
            enabled: (selected, callbacks) => selected.length === 1 && callbacks.collection != null,
            onClick: doNothing,
            children: [
                openWithApplication,
                {
                    text: "Editor",
                    enabled: selected => selected.length === 1,
                    onClick: ([file], callbacks) => callbacks.navigate(AppRoutes.files.preview(file.id)),
                },
            ],
        };
        const compress: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Compress",
            icon: "heroArchiveBox",
            enabled: selected => selected.length === 1 && selected[0].status.type === "DIRECTORY",
            onClick: doNothing,
            children: COMPRESSION_FORMATS.map(format => ({
                text: selected => `${fileName(removeTrailingSlash(selected[0].id))}${format.extension}`,
                enabled: selected => selected.length === 1 && selected[0].status.type === "DIRECTORY",
                onClick: async ([folder], callbacks) => {
                    try {
                        await startCompression(folder, callbacks, format);
                    } catch (error) {
                        displayErrorMessageOrDefault(error, `Failed to start ${format.parameter} compression.`);
                    }
                }
            }))
        };
        const uncompress: ActionItem<UFile, FileBrowseCallbacks> = {
            text: "Uncompress",
            icon: "heroArchiveBox",
            enabled: selected => selected.length === 1 && isCompressedFile(selected[0]),
            onClick: async ([file], callbacks) => {
                try {
                    await startUncompression(file, callbacks);
                } catch (error) {
                    displayErrorMessageOrDefault(error, "Failed to start uncompression.");
                }
            }
        };
        const download = withoutShortcut(byText("Download"));
        const copyTo = withOverrides(withoutShortcut(byText("Copy to...")), {icon: undefined});
        const moveTo = withOverrides(withoutShortcut(byText("Move to...")), {icon: undefined});
        const transferTo = withOverrides(withoutShortcut(byText("Transfer to...")), {icon: undefined});
        const share = withOverrides(withoutShortcut(byText("Share")), {text: "Share with..."});
        const synchronization = findOperation(operation => operation.tag === FILE_SYNCHRONIZATION_TAG);
        const addToSynchronization = withOverrides(withoutShortcut(synchronization), {
            text: "Add to synchronization",
            icon: undefined,
            enabled: (selected, callbacks) => {
                const enabled = synchronization.enabled(selected, callbacks);
                return enabled === true && !areAllSynchronized(selected, callbacks);
            },
        });
        const rename = withOverrides(byText("Rename"), {shortcut: RENAME_SHORTCUT});
        const deleteAction = withOverrides(byText("Move to trash"), {
            text: "Delete",
            shortcut: DELETE_SHORTCUT,
            confirmationText: selected => selected.length === 1 ?
                "Are you sure you want to move this item to the trash?" :
                `Are you sure you want to move these ${selected.length} items to the trash?`,
        });
        const emptyTrash = withOverrides(
            withoutShortcut(findOperation(operation => operation.tag === FILE_SELECTED_EMPTY_TRASH_TAG)),
            {
                text: "Empty trash",
                confirmationText: "Are you sure you want to permanently delete everything in the trash?",
                confirmationButtonText: "Empty",
            }
        );
        const propertiesOperation = withoutShortcut(byText("Properties"));
        const properties = withOverrides(propertiesOperation, {
            icon: undefined,
            enabled: (selected, callbacks) => callbacks.viewProperties != null &&
                (selected.length === 1 || (selected.length === 0 && callbacks.directory != null)),
            onClick: (selected, callbacks) => callbacks.viewProperties!(selected[0] ?? callbacks.directory!),
        });
        const newFolder = withOverrides(withoutShortcut(byText("Create folder")), {text: "New folder"});
        const newFile = withOverrides(withoutShortcut(byText("Create file")), {text: "New file", icon: undefined});
        const openTerminal = withoutShortcut(byText("Open terminal"));

        const hiddenFromTopbar = new Set([
            "Copy to...",
            "Move to...",
            "Transfer to...",
            "Change sensitivity",
        ]);
        const topbarOrder = new Map([
            ["Go to parent folder", 0],
            ["Open with...", 1],
            ["Download", 2],
            ["Share", 3],
            ["Rename", 5],
            ["Move to trash", 6],
            ["Empty Trash", 7],
            ["Create folder", 8],
            ["Create file", 9],
            ["Upload files", 10],
            ["Open terminal", 11],
            ["Sync", 12],
            ["Properties", 13],
        ]);
        const topbarOperations = operations.filter(operation =>
            !hiddenFromTopbar.has(typeof operation.text === "string" ? operation.text : "") &&
            operation.tag !== FILE_SYNCHRONIZATION_TAG
        ).sort((a, b) => {
            const aOrder = typeof a.text === "string" ? topbarOrder.get(a.text) : undefined;
            const bOrder = typeof b.text === "string" ? topbarOrder.get(b.text) : undefined;
            if (aOrder == null) return bOrder == null ? 0 : -1;
            if (bOrder == null) return 1;
            return aOrder - bOrder;
        });
        const topbar = operationsToActions(topbarOperations);

        return {
            topbar: topbar.actions,
            appearance: action => {
                const appearance = topbar.appearance(action as ActionItem<UFile, FileBrowseCallbacks>);
                return appearance ? {...appearance, iconSize: 16, iconSpacing: "8px"} : undefined;
            },
            topbarMaxVisible: 4,
            contextMenu: [
                open,
                openInNewWindow,
                openWith,
                download,
                "divider",
                copy,
                cut,
                "divider",
                copyTo,
                moveTo,
                transferTo,
                "divider",
                share,
                compress,
                uncompress,
                addToSynchronization,
                "divider",
                rename,
                deleteAction,
                emptyTrash,
                "divider",
                newFolder,
                newFile,
                paste,
                "divider",
                openTerminal,
                "divider",
                properties,
            ],
        };
    }

    public retrieveOperations(): Operation<UFile, FileBrowseCallbacks>[] {
        const base = super.retrieveOperations()
            .filter(it => it.tag !== CREATE_TAG && it.tag !== PERMISSIONS_TAG && it.tag !== DELETE_TAG);
        const ourOps: Operation<UFile, FileBrowseCallbacks>[] = [
            {
                text: "Use this folder",
                primary: true,
                icon: "check",
                enabled: (selected, cb) => {
                    return selected.length === 0 && cb.onSelect !== undefined && cb.directory != null &&
                        (cb.onSelectRestriction == null || cb.onSelectRestriction(cb.directory) === true);
                },
                onClick: (selected, cb) => {
                    cb.onSelect?.(cb.directory ?? {
                        id: "",
                        status: {type: "DIRECTORY"},
                        permissions: {myself: []},
                        specification: {product: {id: "", provider: "", category: ""}, collection: ""},
                        owner: {createdBy: ""},
                        createdAt: 0,
                        updates: []
                    })
                },
                shortcut: ShortcutKey.F
            },
            {
                text: "Upload files",
                icon: "upload",
                primary: true,
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (!(selected.length === 0 && cb.onSelect === undefined)) {
                        return false;
                    }

                    if (!hasReadAndWritePermission(cb.collection?.permissions?.myself ?? [])) {
                        return "You do not have write permissions in this folder";
                    }
                    return true;
                },
                onClick: (_, cb) => {
                    cb.dispatch(genericSet({
                        property: "uploaderVisible", newValue: true,
                        defaultValue: false
                    }));
                },
                shortcut: ShortcutKey.U
            },
            {
                text: "Create folder",
                icon: "uploadFolder",
                primary: true,
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    if (cb.creationDisabled) return "Fetching folder...";
                    if (selected.length !== 0 || cb.startFolderCreation == null) return false;
                    if (cb.isCreating) return "You are already creating a folder";
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (!hasReadAndWritePermission(cb.collection?.permissions?.myself ?? [])) {
                        return "You do not have write permissions in this folder";
                    }
                    return true;
                },
                onClick: (selected, cb) => cb.startFolderCreation!(),
                shortcut: ShortcutKey.F,
                splitButtonGroupId: 'createOperations',
                color: "secondaryMain"
            },

            {
                text: "Open with...",
                icon: "heroArrowTopRightOnSquare",
                enabled: (selected, cb) => selected.length === 1 && cb.collection != null,
                onClick: (selected) => {
                    dialogStore.addDialog(
                        <OpenWithBrowser opts={{isModal: true}} file={selected[0]} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle,
                    );
                },
                shortcut: ShortcutKey.O
            },
            {
                text: "Go to parent folder",
                icon: "ftFolder",
                enabled(selected, cb) {
                    return selected.length === 1 && !cb.isModal && !cb.embedded && cb.isSearch;
                },
                onClick(selected, extra, all) {
                    const [file] = selected;
                    extra.navigate(AppRoutes.files.path(getParentPath(file.id)));
                },
                shortcut: ShortcutKey.V,
            },
            {
                text: "Rename",
                icon: "heroPencilSquare",
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (selected.some(it => it.status.icon === "DIRECTORY_TRASH")) return false;
                    return selected.length === 1 &&
                        selected.every(it => hasReadAndWritePermission(it.permissions.myself));
                },
                onClick: ([selected], cb) => {
                    const op = () => cb.startRenaming?.(selected, fileName(selected.id));
                    handleSyncthingWarning([selected], cb, op, "Renaming");
                },
                shortcut: ShortcutKey.F
            },
            {
                text: "Download",
                icon: "heroArrowDownTray",
                enabled: selected => selected.length > 0 && selected.every(it => it.status.type === "FILE"),
                onClick: async (selected, cb) => {
                    this.download(selected.map(it => it.id));
                },
                shortcut: ShortcutKey.D
            },
            {
                icon: "heroDocumentDuplicate",
                text: "Copy to...",
                enabled: (selected, cb) =>
                    (cb.isModal !== true || !!cb.allowMoveCopyOverride) &&
                    !cb.isSearch &&
                    selected.length > 0 &&
                    selected.every(it => hasReadPermission(it.permissions.myself)),
                onClick: (selected, cb) => {
                    this.copyModal(selected.map(it => it.id), selected[0].specification.product.provider, cb.reload);
                },
                shortcut: ShortcutKey.C
            },
            {
                icon: "heroPaperAirplane",
                text: "Transfer to...",
                enabled: (selected, cb) =>
                    !cb.isSearch &&
                    (cb.isModal !== true || !!cb.allowMoveCopyOverride) &&
                    selected.length > 0 &&
                    selected.every(it => hasReadPermission(it.permissions.myself)) &&
                    selected.every(it => it.status.type === "DIRECTORY"),
                onClick: (selected, cb) => {
                    const pathRef = {current: getParentPath(selected[0].id)};
                    dialogStore.addDialog(
                        <FileBrowse opts={{
                            isModal: true,
                            managesLocalProject: true,
                            selection: {
                                text: "Transfer",
                                show(res) {
                                    return res.status.type === "DIRECTORY" &&
                                        (
                                            res.specification.product.provider !== selected[0].specification.product.provider ||
                                            res.specification.product.provider === "go-slurm" ||
                                            res.specification.product.provider === "goslurm1" ||
                                            res.specification.product.provider === "gok8s" ||
                                            res.specification.product.provider === "k8s"
                                        );
                                },
                                onClick: async (res) => {
                                    const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                                    try {
                                        const result = await cb.invokeCommand(
                                            this.transfer({
                                                type: "bulk",
                                                items: selected.map(file => ({
                                                    sourcePath: file.id,
                                                    destinationPath: target + "/" + fileName(file.id)
                                                }))
                                            })
                                        );
                                        cb.reload();
                                        dialogStore.success();
                                        sendSuccessNotification("Files are now transferring...");
                                    } catch (e) {
                                        displayErrorMessageOrDefault(e, "Failed to move to folder");
                                    }
                                }
                            },
                            initialPath: pathRef.current,
                        }} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle
                    );
                },
                shortcut: ShortcutKey.T,
            },
            {
                icon: "heroFolderArrowRight",
                text: "Move to...",
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (selected.some(it => it.status.icon === "DIRECTORY_TRASH")) return false;
                    return (cb.isModal !== true || !!cb.allowMoveCopyOverride) &&
                        selected.length > 0 &&
                        selected.every(it => hasReadAndWritePermission(it.permissions.myself));
                },
                onClick: (selected, cb) => {
                    const op = () => this.moveModal(selected.map(it => it.id), selected[0].specification.product.provider, cb.reload);
                    handleSyncthingWarning(selected, cb, op, "Moving");
                },
                shortcut: ShortcutKey.M
            },
            {
                icon: "heroShare",
                text: "Share",
                enabled: (selected, cb) => {
                    if (Client.hasActiveProject) return false;
                    if (selected.length != 1) return false;

                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.sharesSupported === false) return false;

                    const isMissingPermissions = selected.some(it => !hasAdminPermission(it.permissions.myself));
                    const hasNonDirectories = selected.some(it => it.status.type != "DIRECTORY");

                    if (isMissingPermissions) {
                        return "You lack permissions to share this file. Only the owner of the file can share it!";
                    }

                    if (hasNonDirectories) {
                        return "You can only share a directory. To share a file put it in a directory and share the " +
                            "directory.";
                    }

                    const hasTrashFolder = selected.some(it => it.status.icon === "DIRECTORY_TRASH");
                    if (hasTrashFolder) {
                        return "You cannot share your trash";
                    }

                    return true;
                },
                onClick: async (selected, cb) => {
                    addShareModal({path: selected[0].id, product: selected[0].specification.product}, cb);
                },
                shortcut: ShortcutKey.S
            },
            {
                text: "Change sensitivity",
                icon: "sensitivity",
                enabled(selected, cb) {
                    if (cb.isSearch) return false;
                    if (!cb.syncthingConfig) return false;
                    if (!hasReadAndWritePermission(cb.collection?.permissions?.myself ?? [])) {
                        return false;
                    }

                    if (selected.length !== 1) return false;
                    const [file] = selected;
                    const syncthingEntry = cb.syncthingConfig.folders.find(it => file.id.startsWith(it.ucloudPath));
                    if (syncthingEntry) {
                        const isSynchronizationRoot = syncthingEntry.ucloudPath === file.id;
                        if (isSynchronizationRoot) {
                            return "Remove this folder from syncthing to change sensitivity.";
                        } else {
                            return "Remove synchronized parent folder from syncthing to change sensitivity.";
                        }
                    }

                    return true;
                },
                onClick(selected, extra) {
                    addFileSensitivityDialog(selected[0], extra.invokeCommand, extra.reload);
                },
                shortcut: ShortcutKey.H
            },
            {
                // Empty trash of current directory
                text: "Empty Trash",
                icon: "heroTrash",
                color: "errorMain",
                primary: true,
                enabled: (selected, cb) => {
                    const support = cb.collection?.status.resolvedSupport?.support;
                    const isTrashDirectory = cb.directory?.status.icon == "DIRECTORY_TRASH"
                    if (!support) return false;
                    if (!(support as FileCollectionSupport).files.trashSupported) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (
                        (isTrashDirectory && cb.directory && isReadonly(cb.directory.permissions.myself)) ||
                        (selected.length !== 0 && selected.every(it => it.status.icon === "DIRECTORY_TRASH" && isReadonly(it.permissions.myself)))) {
                        return "You cannot delete read-only files."
                    }
                    if (!(selected.length === 0 && cb.onSelect === undefined)) {
                        return false;
                    }
                    return isTrashDirectory;
                },
                onClick: async (_, cb) => {
                    addStandardDialog({
                        title: "Are you sure you wish to empty the trash?",
                        message: "You cannot recover deleted files!",
                        confirmText: "Empty trash",
                        addToFront: true,
                        cancelButtonColor: "primaryMain",
                        confirmButtonColor: "errorMain",
                        onConfirm: async () => {
                            try {
                                await cb.invokeCommand(
                                    this.emptyTrash(bulkRequestOf({id: cb.directory?.id ?? ""}))
                                );
                                const path = cb.directory?.specification.collection ?? ""
                                if (path === "") {
                                    cb.navigate("/drives");
                                } else {
                                    cb.navigate(AppRoutes.files.path(path));
                                }
                            } catch (e) {
                                displayErrorMessageOrDefault(e, "Failed to empty trash");
                            }

                        },
                        onCancel: doNothing,
                    });
                },
                shortcut: ShortcutKey.R
            },
            {
                icon: "refresh",
                text: "Sync",
                enabled: (files, extra) => files.length === 0 && !!extra.syncthingConfig && !extra.isModal,
                onClick: (selected, extra) =>
                    extra.navigate(`/syncthing?provider=${extra.collection?.specification.product.provider}`),
                shortcut: ShortcutKey.M
            },
            {
                // Item row synchronization
                text: synchronizationOpText,
                tag: FILE_SYNCHRONIZATION_TAG,
                icon: "refresh",
                enabled: (selected, cb) => !cb.isSearch && synchronizationOpEnabled(false, selected, cb),
                onClick: (selected, cb) => {
                    synchronizationOpOnClick(selected, cb);
                },
                shortcut: ShortcutKey.Y
            },
            {
                text: "Open terminal",
                primary: true,
                icon: "terminalSolid",
                enabled: (selected, cb) => {
                    if (cb.embedded) return false;

                    let support = cb.collection?.status?.resolvedSupport?.support;
                    if (!support) return false;
                    if (selected.length > 0) return false;

                    if (cb.isSearch) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (!(selected.length === 0 && cb.onSelect === undefined)) {
                        return false;
                    }

                    if (!hasReadAndWritePermission(cb.collection?.permissions?.myself ?? [])) {
                        return "You do not have write permissions in this folder";
                    }

                    return (support as FileCollectionSupport).files.openInTerminal === true;
                },
                onClick: (selected, cb) => {
                    const providerId = cb.collection?.status?.resolvedProduct?.category?.provider ?? "";
                    const folder = cb.directory?.id ?? "/";

                    cb.dispatch(terminalOpen());
                    cb.dispatch(terminalOpenTab({tab: {title: "Terminal", folder, providerId}}));
                },
                shortcut: ShortcutKey.O
            },
            {
                text: "Create file",
                icon: "heroDocumentPlus",
                enabled(selected, cb) {
                    if (cb.isSearch) return false;
                    if (cb.creationDisabled) return "Fetching folder...";
                    if (selected.length !== 0 || cb.startFileCreation == null) return false;
                    if (cb.isCreating) return "You are already creating a folder";
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (!hasReadAndWritePermission(cb.collection?.permissions?.myself ?? [])) {
                        return "You do not have write permissions in this folder";
                    }
                    return true;
                },
                onClick: (selected, cb) => {
                    cb.startFileCreation!();
                },
                shortcut: ShortcutKey.L,
                splitButtonGroupId: "createOperations",
                color: "textPrimary",
            },
            {
                icon: "heroTrash",
                text: "Move to trash",
                confirm: true,
                confirmationText: selected => selected.length === 1 ?
                    "Are you sure you want to move this item to the trash?" :
                    `Are you sure you want to move these ${selected.length} items to the trash?`,
                confirmationButtonText: "Delete",
                color: "errorMain",
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (cb.directory?.status.icon == "DIRECTORY_TRASH") {
                        return false;
                    }
                    return selected.length > 0 &&
                        selected.every(it => hasReadAndWritePermission(it.permissions.myself))
                        && selected.every(f => f.specification.product)
                        && selected.every(f => f.status.icon !== "DIRECTORY_TRASH");
                },
                onClick: async (selected, cb) => {
                    const op = async () => {
                        await cb.invokeCommand(
                            this.trash(bulkRequestOf(...selected.map(it => ({id: it.id}))))
                        );
                        cb.reload();
                    }
                    handleSyncthingWarning(selected, cb, op, "Deleting");
                },
                shortcut: ShortcutKey.R
            },
            {
                icon: "heroTrash",
                text: "Empty Trash",
                tag: FILE_SELECTED_EMPTY_TRASH_TAG,
                confirm: true,
                confirmationText: "Are you sure you want to permanently delete everything in the trash?",
                confirmationButtonText: "Empty",
                color: "errorMain",
                enabled: (selected, cb) => {
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if (cb.directory && isReadonly(cb.directory.permissions.myself)) {
                        return false;
                    }
                    return selected.length == 1 && selected[0].status.icon == "DIRECTORY_TRASH";
                },
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.emptyTrash(bulkRequestOf(...selected.map(it => ({id: it.id}))))
                    );
                    cb.reload()
                },
                shortcut: ShortcutKey.R
            },
        ];

        return base.concat(ourOps);
    }

    public transfer(request: BulkRequest<FilesTransferRequestItem>): APICallParameters<BulkRequest<{}>> {
        return apiUpdate(request, this.baseContext, "transfer");
    }

    public copy(request: BulkRequest<FilesCopyRequestItem>): APICallParameters<BulkRequest<FilesCopyRequestItem>> {
        return apiUpdate(request, this.baseContext, "copy");
    }

    public move(request: BulkRequest<FilesMoveRequestItem>): APICallParameters<BulkRequest<FilesMoveRequestItem>> {
        return apiUpdate(request, this.baseContext, "move");
    }

    public createUpload(
        request: BulkRequest<FilesCreateUploadRequestItem>
    ): APICallParameters<BulkRequest<FilesCreateUploadRequestItem>> {
        return apiCreate(request, this.baseContext, "upload");
    }

    public createDownload(
        request: BulkRequest<FilesCreateDownloadRequestItem>
    ): APICallParameters<BulkRequest<FilesCreateDownloadRequestItem>> {
        return apiCreate(request, this.baseContext, "download");
    }

    public createFolder(
        request: BulkRequest<FilesCreateFolderRequestItem>
    ): APICallParameters<BulkRequest<FilesCreateFolderRequestItem>> {
        return apiCreate(request, this.baseContext, "folder");
    }

    public trash(
        request: BulkRequest<FilesTrashRequestItem>
    ): APICallParameters<BulkRequest<FilesTrashRequestItem>> {
        return apiUpdate(request, this.baseContext, "trash");
    }

    public emptyTrash(
        request: BulkRequest<FilesEmptyTrashRequestItem>
    ): APICallParameters<BulkRequest<FilesEmptyTrashRequestItem>> {
        return apiUpdate(request, this.baseContext, "emptyTrash");
    }

    fileSelectorModalStyle = largeModalStyle;

    // -- Shared file operations -- 
    // TODO(Dan): We should probably add a feature flag for file types
    public async download(ids: string[]) {
        if (ids.length > 1) {
            sendInformationNotification("For downloading multiple files, you may need to enable pop-ups.");
        }

        const result = await callAPI<BulkResponse<FilesCreateDownloadResponseItem>>(
            this.createDownload(bulkRequestOf(
                ...ids.map(id => ({id})),
            ))
        );

        const responses = result?.responses ?? [];
        for (const {endpoint} of responses) {
            downloadFile(normalizeDownloadEndpoint(endpoint), responses.length > 1);
        }
    }

    public copyModal(ids: string[], provider: string, reload: (result: any) => void) {
        const pathRef = {current: getParentPath(ids[0])};
        dialogStore.addDialog(
            <FileBrowse opts={{
                isModal: true, managesLocalProject: true, selection: {
                    text: "Copy to",
                    show(res) {
                        return res.status.type === "DIRECTORY"
                    },
                    onClick: async (res) => {
                        const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                        try {
                            const result = await callAPI(
                                this.copy({
                                    type: "bulk",
                                    items: ids.map(id => ({
                                        oldId: id,
                                        conflictPolicy: "RENAME",
                                        newId: target + "/" + fileName(id)
                                    }))
                                })
                            );
                            reload(result);
                            dialogStore.success();
                            sendSuccessNotification("File copy will begin soon");
                            return true;
                        } catch (e) {
                            displayErrorMessageOrDefault(e, "Failed to move to folder");
                            return false;
                        }
                    }
                },
                additionalFilters: {
                    filterProvider: provider
                },
                initialPath: pathRef.current,
            }} />,
            doNothing,
            true,
            this.fileSelectorModalStyle
        );
    }

    public moveModal(ids: string[], provider: string, reload: (result: any) => void) {
        const pathRef = {current: getParentPath(ids[0])};
        dialogStore.addDialog(
            <FileBrowse opts={{
                isModal: true, managesLocalProject: true, selection: {
                    text: "Move to",
                    show(res) {
                        return res.status.type === "DIRECTORY"
                    },
                    onClick: async (res) => {
                        const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);

                        try {
                            const result = await callAPI(
                                this.move({
                                    type: "bulk",
                                    items: ids.map(id => ({
                                        oldId: id,
                                        conflictPolicy: "RENAME",
                                        newId: target + "/" + fileName(id)
                                    }))
                                })
                            );
                            reload(result);
                            dialogStore.success();
                            sendSuccessNotification("Files moved");
                        } catch (e) {
                            displayErrorMessageOrDefault(e, "Failed to move to folder");
                        }
                    }
                },
                initialPath: pathRef.current,
                additionalFilters: {filterProvider: provider}
            }} />,
            doNothing,
            true,
            this.fileSelectorModalStyle
        );
    }
}

function handleSyncthingWarning(files: UFile[], cb: ExtraFileCallbacks, op: () => void, operationText: "Moving" | "Deleting" | "Renaming"): void {
    if (!isAnySynchronized(files, cb)) {
        op();
    } else {
        addStandardDialog({
            title: "Syncthing warning",
            message: <div>
                {operationText} the folder(s) will break Syncthing synchronization for this folder.
                {(["Moving", "Renaming"] as typeof operationText[]).includes(operationText) ?
                    <div>
                        <br />
                        To learn how to move a folder or rename a folder with Syncthing, click <ExternalLink href={"https://docs.syncthing.net/users/faq.html#how-do-i-rename-move-a-synced-folder"}>here</ExternalLink>.
                    </div> : null}
            </div>,
            onConfirm: op,
            confirmText: "Continue"
        });
    }
}

function synchronizationOpText(files: UFile[], callbacks: FileBrowseCallbacks): string {
    const devices: SyncthingDevice[] = callbacks.syncthingConfig?.devices ?? [];
    if (devices.length === 0) return "Sync setup";

    if (areAllSynchronized(files, callbacks)) {
        return "Remove from sync";
    } else {
        return "Add to sync";
    }
}

function areAllSynchronized(files: UFile[], callbacks: FileBrowseCallbacks): boolean {
    const synchronized: SyncthingFolder[] = callbacks.syncthingConfig?.folders ?? [];
    const resolvedFiles = files.length === 0 ? (callbacks.directory ? [callbacks.directory] : []) : files;
    return resolvedFiles.length > 0 &&
        resolvedFiles.every(selected => synchronized.some(it => it.ucloudPath === selected.id));
}

function isAnySynchronized(files: UFile[], callbacks: ExtraFileCallbacks): boolean {
    const synchronizedFolders = callbacks.syncthingConfig?.folders;
    if (!synchronizedFolders) {
        // Note(Jonas): Is this undefined by default, or only if the call to the backend fails?
        // If it has failed and is therefore undefined, should we still warn the user what will
        // happen for a synchronized file, or just assume that it isn't sync'ed?
        return false;
    }

    const filePaths = files.map(it => it.id);
    return synchronizedFolders.find(it => it.ucloudPath && filePaths.includes(it.ucloudPath)) != null;
}

function synchronizationOpEnabled(isDir: boolean, files: UFile[], cb: FileBrowseCallbacks): boolean | string {
    const support = cb.collection?.status.resolvedSupport?.support;
    if (!support) return false;

    const isShare = cb.collection?.specification.product.id === "share";
    if (isShare) {
        return false;
    }

    if (cb.syncthingConfig === undefined) return false;
    if (cb.setSynchronization === undefined) return false;

    if (isDir && files.length !== 0) return false;
    if (!isDir && files.length === 0) return false;

    if (files.length > 0 && files.every(it => it.status.type !== "DIRECTORY")) return false;
    if (files.length > 0 && files.some(it => it.status.type !== "DIRECTORY")) return "You can only synchronize directories";

    if ((support as FileCollectionSupport).files.isReadOnly) {
        return "File system is read-only";
    }

    return true;
}

async function synchronizationOpOnClick(files: UFile[], cb: FileBrowseCallbacks) {
    const synchronized: SyncthingFolder[] = cb.syncthingConfig?.folders ?? [];
    const resolvedFiles = files.length === 0 ? (cb.directory ? [cb.directory] : []) : files;
    const allSynchronized = areAllSynchronized(files, cb);

    if (!cb.syncthingConfig) return;
    if (!allSynchronized) {
        const synchronizedFolderNames = synchronized.map(it => it.ucloudPath.split("/").pop());

        for (const folder of resolvedFiles) {
            if (synchronizedFolderNames.includes(folder.id.split("/").pop())) {
                sendFailureNotification("Folder with same name already exist in Syncthing");
                return;
            }
        }

        for (const folder of resolvedFiles) {
            const sensitivity = await findSensitivity(folder);
            if (sensitivity == "SENSITIVE" || sensitivity == "CONFIDENTIAL") {
                sendFailureNotification("Sensitive or confidential folders cannot be added to Syncthing");
                return;
            }
        }
    }

    const devices: SyncthingDevice[] = cb.syncthingConfig?.devices ?? [];
    if (devices.length === 0) {
        cb.navigate(`/syncthing?provider=${cb.collection?.specification.product.provider}`);
        return;
    }

    if (!cb.setSynchronization) return;


    cb.setSynchronization(files, !allSynchronized);

    sendSuccessNotification(`${allSynchronized ? "Removed from" : "Added to"} Syncthing`);
}

export function isReadonly(entries: Permission[]): boolean {
    return hasReadPermission(entries) && !hasReadAndWritePermission(entries);
}

async function queryTemplateName(name: string, invokeCommand: InvokeCommand, next?: string): Promise<string> {
    const result = await invokeCommand<PageV2<FileMetadataTemplateNamespace>>(metadataNamespaceApi.browse({
        itemsPerPage: 100,
        next
    }));

    const id = result?.items.find(it => it.specification.name === name)?.id;
    if (!id) {
        if (!result?.next) return "";
        return queryTemplateName(name, invokeCommand, result?.next ?? null);
    }

    return id;
}

export enum SensitivityLevelMap {
    INHERIT = "INHERIT",
    PRIVATE = "PRIVATE",
    CONFIDENTIAL = "CONFIDENTIAL",
    SENSITIVE = "SENSITIVE"
}

function SensitivityDialog({file, invokeCommand, onUpdated}: {
    file: UFile;
    invokeCommand: InvokeCommand;
    onUpdated(value: SensitivityLevelMap): void;
}): React.ReactNode {
    const originalSensitivity = useSensitivity(file) ?? "INHERIT" as SensitivityLevel;
    const selection = React.useRef<HTMLSelectElement | null>(null);
    const reason = React.useRef<HTMLInputElement>(null);

    const onUpdate = React.useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();
        e.stopPropagation();
        try {
            const value = selection.current?.value;
            const reasonText = reason.current?.value ?? "No reason provided."
            if (!value) return;
            if (value === SensitivityLevelMap.INHERIT) {
                // Find latest that is active and remove that one. At most one will be active.
                const entryToDelete = file.status.metadata?.metadata[sensitivityTemplateId]?.find(
                    it => ["approved", "not_required"].includes(it.status.approval.type)
                );
                if (!entryToDelete) {
                    // Note(Jonas): In this case, I believe that user is setting to "inherit", despite it already being
                    // the case, as it hasn't been set to anything yet, so do nothing.
                    dialogStore.success();
                    return;
                }
                await invokeCommand(
                    metadataDocumentApi.delete(
                        bulkRequestOf({
                            changeLog: reasonText,
                            id: entryToDelete.id
                        })
                    ),
                    {defaultErrorHandler: false}
                );
                onUpdated(value as SensitivityLevelMap);
            } else {
                if (!sensitivityTemplateId) {
                    sensitivityTemplateId = await queryTemplateName(sensitivityTemplateId, invokeCommand);
                    if (!sensitivityTemplateId) {
                        sendFailureNotification("Failed to change sensitivity.");
                        return;
                    }
                }

                await invokeCommand(
                    metadataDocumentApi.create(bulkRequestOf({
                        fileId: file.id,
                        metadata: {
                            changeLog: reasonText,
                            document: {
                                sensitivity: value,
                            },
                            templateId: sensitivityTemplateId,
                            version: FileSensitivityVersion
                        }
                    })),
                    {defaultErrorHandler: false}
                );

                onUpdated(value as SensitivityLevelMap);
            }

            dialogStore.success();
        } catch (e) {
            onUpdated(originalSensitivity as SensitivityLevelMap);
            displayErrorMessageOrDefault(e, "Failed to update sensitivity.")
        }
    }, []);

    return (<form id={"sensitivityDialog"} onSubmit={onUpdate} style={{width: "100%"}}>
        <Text fontSize={24} mb="12px">Change sensitivity</Text>
        <Select my="8px" id={"sensitivityDialogValue"} selectRef={selection}>
            {Object.keys(SensitivityLevelMap).map(it =>
                <option key={it} value={it} selected={it === originalSensitivity}>{prettierString(it)}</option>
            )}
        </Select>
        <TextArea
            id={"sensitivityDialogReason"}
            style={{marginTop: "6px", marginBottom: "6px"}}
            required
            inputRef={reason}
            width="100%"
            rows={4}
            placeholder="Reason for sensitivity change..."
            onKeyDown={stopPropagation}
        />
        <Spacer
            mt="12px"
            left={<Button color="errorMain" width="180px" onClick={() => dialogStore.failure()}>Cancel</Button>}
            right={<Button color="successMain" type={"submit"}>Update</Button>}
        />
    </form>);
}

function downloadFile(url: string, usePopup: boolean) {
    const element = document.createElement("a");
    element.setAttribute("href", url);
    if (usePopup) element.setAttribute("target", "_blank");
    document.body.appendChild(element);
    element.click();
    if (element.parentNode === document.body) {
        document.body.removeChild(element);
    }
}

export async function addFileSensitivityDialog(file: UFile, invokeCommand: InvokeCommand, onUpdated: (value: SensitivityLevelMap) => void): Promise<void> {
    if (!isSensitivitySupported(file)) {
        dialogStore.addDialog(
            <>
                <Heading.h2>
                    Sensitive files not supported <Icon name="warning" color="errorMain" size="32" />
                </Heading.h2>
                <p>
                    This provider (<ProviderTitle providerId={file.specification.product.provider} />) has declared
                    that they do not support sensitive data. This means that you <b>cannot/should not</b>:

                    <ul>
                        <li>Store sensitive data on this provider</li>
                        <li>It is not possible to mark files as confidential or sensitive</li>
                    </ul>
                </p>
                <p>
                    You can look at the providers own web-page for more information. We recommend that you use a
                    different provider if you need to store sensitive data.
                </p>
            </>,
            doNothing,
            true
        );
        return;
    }
    if (!hasReadAndWritePermission(file.permissions.myself ?? [])) {
        return;
    }

    // Note(Jonas): It should be initialized at this point, but let's make sure.
    if (!sensitivityTemplateId) {
        sensitivityTemplateId = await findTemplateId(file, FileSensitivityNamespace, FileSensitivityVersion);
    }

    dialogStore.addDialog(<SensitivityDialog file={file} invokeCommand={invokeCommand}
        onUpdated={onUpdated} />, () => undefined, true);
}

const api = new FilesApi();

export const MAX_PREVIEW_SIZE_IN_BYTES = PREVIEW_MAX_SIZE;

function isFileFileSizeExceeded(file: UFile) {
    const size = file.status.sizeInBytes;
    return size != null && size > MAX_PREVIEW_SIZE_IN_BYTES && size > 0;
}

export function FilePreview({initialFile}: {
    initialFile: UFile,
}): React.ReactNode {
    const [openFile, setOpenFile] = useState<[string, string | Uint8Array<ArrayBufferLike>]>(["", ""]);
    const [previewRequested, setPreviewRequested] = useState(false);
    const [drive, setDrive] = useState<FileCollection | null>(null);
    const [renamingFile, setRenamingFile] = useState<string>();
    const dirtyFileCountRef = React.useRef(0);
    const didUnmount = useDidUnmount();

    useEffect(() => {
        (async () => {
            const collection = await callAPI(
                FileCollectionsApi.retrieve({
                    id: initialFile.specification.collection,
                    includeSupport: true,
                })
            );

            if (!didUnmount.current) {
                setDrive(collection);
            }
        })();
    }, [initialFile]);

    const dispatch = useDispatch();

    const supportsTerminal = (drive?.status?.resolvedSupport?.support as FileCollectionSupport)
        ?.files?.openInTerminal === true;

    const vfs = useMemo(() => {
        return new PreviewVfs(initialFile);
    }, []);
    const initialDirectoryPath = initialFile.status.type === "DIRECTORY" ? initialFile.id + "/placeholder" : initialFile.id;

    const [vfsTitle, setTitle] = useState(
        initialFile.status.type === "DIRECTORY" ? initialFile.id : getParentPath(initialFile.id)
    );

    React.useEffect(() => {
        prettyFilePath(initialFile.status.type === "DIRECTORY" ? initialFile.id : getParentPath(initialFile.id)).then(t => {
            setTitle(t);
        })
    }, []);

    const mediaFileMetadata: null | {type: ExtensionType, data: string, error: string | null} = useMemo(() => {
        let [file, contentBuffer] = openFile;

        const isSvg = extensionFromPath(file) === "svg";
        if (typeof contentBuffer === "string") {
            if (previewRequested) {
                contentBuffer = new TextEncoder().encode(contentBuffer);
            } else if (isSvg) {
                return {
                    type: "image" as const,
                    data: URL.createObjectURL(
                        new Blob(
                            [contentBuffer],
                            {type: "image/svg+xml"}
                        )
                    ),
                    error: null
                }
            } else {
                return null;
            }
        }
        const foundFileType = getFileTypesFromContentBuffer(contentBuffer);
        let typeFromFileType =
            foundFileType.length > 0 ?
                typeFromMime(foundFileType[0]?.mime ?? "") : null;

        if (!typeFromFileType) {
            typeFromFileType = extensionType(extensionFromPath(file));
        }

        switch (typeFromFileType) {
            case "image":
            case "audio":
            case "video":
            case "pdf":
                return {
                    type: typeFromFileType,
                    data: URL.createObjectURL(
                        new Blob(
                            [contentBuffer] as unknown as BlobPart[],
                            {type: foundFileType[0]?.mime}
                        )
                    ),
                    error: null,
                };

            case "code":
            case "text":
            case "application":
            case "markdown":
            default: {
                const text = tryDecodeText(contentBuffer);
                if (text !== null) {
                    return {
                        type: typeFromFileType,
                        data: text,
                        error: null
                    };
                } else {
                    return {
                        type: "text",
                        data: "",
                        error: "Preview is not supported for this file.",
                    };
                }
            }
        }
    }, [openFile, previewRequested]);

    const editorRef = React.useRef<EditorApi>(null);

    const requestPreviewToggle = useCallback(() => {
        setPreviewRequested(p => !p);
    }, []);

    const ext = extensionType(extensionFromPath(openFile[0]));

    const node: React.ReactNode = useMemo(() => {
        if (mediaFileMetadata && mediaFileMetadata.error !== null) {
            return <div>{mediaFileMetadata?.error}</div>;
        }

        const elementKey = fileName(openFile[0]);

        switch (mediaFileMetadata?.type) {
            case "text":
            case "code":
                return null;
            case "image":
                // Note(Jonas): extensions like .HEIC will fall back to just showing the alt.
                return <img key={elementKey} className={Image} alt={elementKey} src={mediaFileMetadata.data} />
            case "audio":
                return <audio key={elementKey} className={Audio} controls src={mediaFileMetadata.data} />;
            case "video":
                return <video key={elementKey} className={Video} src={mediaFileMetadata.data} controls />;
            case "pdf":
                return <object key={elementKey} type="application/pdf" className={classConcat("fullscreen", PreviewObject)} data={mediaFileMetadata.data} />;
            case "markdown":
                return <div key={elementKey} className={MarkdownStyling}><Markdown>{mediaFileMetadata.data}</Markdown></div>;
        }

        return null;
    }, [mediaFileMetadata, openFile[0]]);

    const onSave = useCallback(async () => {
        const editor = editorRef.current;
        if (!editor) return;
        if (node) return;
        if (initialFile.status.type === "DIRECTORY" && editor.path === initialFile.id) return;

        const path = editor.path;

        await editor.notifyDirtyBuffer();
        await vfs.writeFile(path);

        const revert = editor.onFileSaved(path);
        const revertLocalSave = (e: WriteFailureEvent) => {
            const failedUpload = e.detail.find(it => it.targetPath + it.name === path);
            if (failedUpload) {
                revert();
                sendFailureNotification(failedUpload.error ?? `Upload for file ${fileName(failedUpload.name)} failed.`);
            }
        }

        window.addEventListener(FileWriteFailure, {handleEvent: revertLocalSave});
        window.setTimeout(() => window.removeEventListener(FileWriteFailure, {handleEvent: revertLocalSave}), 30_000);
    }, [vfs, node]);

    useEffect(() => {
        const listener = (ev: KeyboardEvent) => {
            const hasCtrl = ev.ctrlKey || ev.metaKey;
            if (ev.code === "KeyS" && hasCtrl) {
                ev.preventDefault();
                ev.stopPropagation();

                onSave().then(doNothing);
            }
            if (ev.code === "KeyB" && hasCtrl) {
                ev.preventDefault();
                ev.stopPropagation();

                requestPreviewToggle();
            }
        };

        window.addEventListener("keydown", listener);
        return () => {
            window.removeEventListener("keydown", listener);
        }
    }, [onSave, requestPreviewToggle]);

    const onOpenFile = useCallback((path: string, data: string | Uint8Array<ArrayBufferLike>) => {
        setPreviewRequested(false);
        setOpenFile(file => {
            const [currentPath] = file;
            if (path != currentPath || data !== file[1]) return [path, data];
            return file;
        });
    }, []);

    const openTerminal = useCallback(() => {
        if (!drive) return;
        const providerId = drive.specification.product.provider;
        const folder = initialFile.status.type === "DIRECTORY" ? initialFile.id : getParentPath(initialFile.id);

        dispatch(terminalOpen());
        dispatch(terminalOpenTab({tab: {title: "Terminal", folder, providerId}}));
    }, [drive, initialFile]);

    const newFolder = useCallback(async (path: string) => {
        const name = (await addStandardInputDialog({
            title: "What should the folder be called?",
            confirmText: "Create folder",
        })).result;

        await callAPI(api.createFolder(bulkRequestOf({
            id: getParentPath(path) + name,
            conflictPolicy: "REJECT",
        })));

        editorRef.current?.invalidateTree?.(getParentPath(path));
    }, [openFile[0]]);

    const newFile = useCallback(async (path: string) => {
        const name = (await addStandardInputDialog({
            title: "What should the file be called?",
            confirmText: "Create file",
        })).result;

        const newPath = getParentPath(path) + name;
        initEmptyFileUpload(newPath);

        setTimeout(() => {
            editorRef.current?.invalidateTree?.(getParentPath(path));
            editorRef.current?.openFile?.(newPath);
        }, 1000);
    }, [openFile[0]]);

    const onRename = React.useCallback(async ({newAbsolutePath, oldAbsolutePath, cancel}: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}): Promise<boolean> => {
        let success = false;
        if (cancel) {
            setRenamingFile(undefined);
            return false;
        }

        try {
            await callAPI(api.move({
                type: "bulk",
                items: [{oldId: oldAbsolutePath, newId: newAbsolutePath, conflictPolicy: "REJECT"}]
            }));
            setRenamingFile(undefined);

            vfs.moveFileContent(removeTrailingSlash(oldAbsolutePath), removeTrailingSlash(newAbsolutePath));

            success = true;
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to rename file");
        }

        return success;
    }, []);

    const actions = useCallback((file?: VirtualFile): ActionEntry<VirtualFile, null>[] => {
        const reload = () => {
            let path: string;
            if (file) {
                path = getParentPath(file.absolutePath);
            } else {
                path = getParentPath(initialFile.id);
            }
            editorRef.current?.invalidateTree(removeTrailingSlash(path));
        }

        if (!file) {
            return [{
                icon: "uploadFolder",
                text: "New folder",
                enabled: () => true,
                onClick: () => {
                    const suffix = initialFile.status.type === "DIRECTORY" ? "/placeholder" : "";
                    newFolder(initialFile.id + suffix).then(doNothing);
                },
            }, {
                text: "New file",
                enabled: () => true,
                onClick: () => {
                    const suffix = initialFile.status.type === "DIRECTORY" ? "/placeholder" : "";
                    newFile(initialFile.id + suffix).then(doNothing);
                },
            }];
        }

        return [
            {
                text: "Open",
                enabled: () => !file.isDirectory,
                onClick: () => editorRef.current?.openFile(file.absolutePath),
            },
            {
                icon: "heroArrowDownTray",
                text: "Download",
                enabled: () => !file.isDirectory,
                onClick: async () => {
                    api.download([file.absolutePath]);
                },
            },
            "divider",
            {
                text: "Copy to...",
                enabled: () => true,
                onClick: () => {
                    api.copyModal([file.absolutePath], initialFile.specification.product.provider, reload);
                },
            },
            {
                text: "Move to...",
                enabled: () => file.fileHint !== "DIRECTORY_TRASH",
                onClick: () => {
                    api.moveModal([file.absolutePath], initialFile.specification.product.provider, reload);
                },
            },
            "divider",
            {
                icon: "heroPencilSquare",
                text: "Rename",
                enabled: () => file.fileHint !== "DIRECTORY_TRASH",
                onClick: () => {
                    setRenamingFile(file.absolutePath);
                },
            },
            {
                icon: "heroTrash",
                text: "Move to trash",
                enabled: () => file.fileHint !== "DIRECTORY_TRASH",
                destructive: true,
                confirmationText: "Are you sure you want to move this item to the trash?",
                onClick: async () => {
                    await callAPI(
                        api.trash({
                            type: "bulk",
                            items: [{id: file.absolutePath}],
                        })
                    );
                    editorRef.current?.onFileDeleted(file.absolutePath);
                    reload();
                    sendSuccessNotification("File(s) moved to trash");
                },
            },
            "divider",
            {
                icon: "uploadFolder",
                text: "New folder",
                enabled: () => true,
                onClick: () => {
                    const suffix = file.isDirectory ? "/placeholder" : "";
                    newFolder(file.absolutePath + suffix).then(doNothing);
                },
            },
            {
                text: "New file",
                enabled: () => true,
                onClick: () => {
                    const suffix = file.isDirectory ? "/placeholder" : "";
                    newFile(file.absolutePath + suffix).then(doNothing);
                },
            },
            "divider",
            {
                text: "Properties",
                enabled: () => vfs.isReal(),
                onClick() {
                    vfs.getFileInfo(file.absolutePath).then(ufile => {
                        showFileProperties(ufile);
                    });
                },
            },
        ];
    }, []);

    return <Editor
        apiRef={editorRef}
        onRequestSave={onSave}
        promptSaveOnNavigate
        dirtyFileCountRef={dirtyFileCountRef}
        toolbarBeforeSettings={
            <>
                {ext === "markdown" ? <IconButton tooltip="Preview (Ctrl + B)" onClick={requestPreviewToggle} icon="heroMagnifyingGlass" /> : null}
            </>
        }
        statusBar={
            <>
                {!supportsTerminal ? null : <IconButton tooltip="Open terminal" onClick={openTerminal} icon="terminalSolid" color="textPrimary" />}
            </>
        }
        initialFolderPath={removeTrailingSlash(
            initialFile.status.type === "DIRECTORY" ? initialFile.id : getParentPath(initialFile.id)
        )}
        initialFilePath={initialFile.status.type === "FILE" ? initialFile.id : undefined}
        title={vfsTitle}
        vfs={vfs}
        showCustomContent={node != null}
        customContent={node}
        onOpenFile={onOpenFile}
        onRename={onRename}
        renamingFile={renamingFile}
        actions={actions}
        fileHeaderOperations={
            <>
                <IconButton compact tooltip="New file" onClick={() => newFile(initialDirectoryPath)} icon="heroDocumentPlus" />
                <IconButton compact tooltip="New folder" onClick={() => newFolder(initialDirectoryPath)} icon="heroFolderPlus" />
            </>
        }
        help={
            <Flex width="100%" height="100%" alignItems="center" justifyContent="center" flexDirection="column" gap="16px">
                <Icon name="heroDocument" size={64} color="textSecondary" />
                <Box>Select a file to edit.</Box>
                <Flex gap={"8px"}>
                    <Button onClick={() => newFile(initialDirectoryPath)}>New file</Button>
                    <Button onClick={() => newFolder(initialDirectoryPath)}>New folder</Button>
                </Flex>
            </Flex>
        }
        readOnly={!allowEditing()}
    />;
}

export function initEmptyFileUpload(filePath: string): void {
    window.dispatchEvent(new CustomEvent<WriteToFileEventProps>(EventKeys.WriteToFile, {
        detail: {
            path: filePath,
            content: " ",
        }
    }));
}

export async function downloadFileContent(path: string): Promise<Blob> {
    const download = await callAPI<BulkResponse<FilesCreateDownloadResponseItem>>(
        api.createDownload(bulkRequestOf({id: path}))
    );
    const downloadEndpoint = download?.responses[0]?.endpoint.replace("integration-module:8889", "localhost:9000");
    if (!downloadEndpoint) {
        throw window.Error("Unable to display preview. Try again later or with a different file.");
    }
    const content = await fetch(normalizeDownloadEndpoint(downloadEndpoint));
    return await content.blob();
}

const MAX_HEIGHT = `calc(100vw - 15px - 15px - 240px - var(${CSSVarCurrentSidebarStickyWidth}));`
const HEIGHT = "100%";

const MarkdownStyling = injectStyleSimple("markdown-styling", `
    max-width: 900px;
    width: 100%;
`);

const Audio = injectStyleSimple("preview-audio", `
    display: block;
    margin-top: auto;
    margin-bottom: auto;
`);

const Image = injectStyleSimple("preview-image", `
    display: block;
    object-fit: contain;
    max-width: ${MAX_HEIGHT}
    max-height: ${HEIGHT}
`);

const Video = injectStyleSimple("preview-video", `
    display: block;
    max-width: ${MAX_HEIGHT}
    max-height: ${HEIGHT}
`);

const PreviewObject = injectStyleSimple("preview-pdf", `
    display: block;
    max-width: ${MAX_HEIGHT}
    width: 100%;
    height: ${HEIGHT};
    max-height: ${HEIGHT}
`)

function tryDecodeText(buf: Uint8Array): string | null {
    try {
        const d = new TextDecoder("utf-8", {fatal: true});
        return d.decode(buf);
    } catch (e) {
        return null;
    }
}

function getFileTypesFromContentBuffer(contentBuffer: Uint8Array | null | undefined): GuessedFile[] {
    if (contentBuffer == null) return [];
    return fileType(contentBuffer).filter(it => it?.mime);
}

class PreviewVfs implements Vfs {
    private fetchedFiles: Record<string, string> = {};
    private folders: Record<string, VirtualFile[]> = {};
    private ufiles: Record<string, UFile> = {};
    private dirtyFileContent: Record<string, string> = {};

    constructor(previewedFile: UFile) {
        this.ufiles[previewedFile.id] = previewedFile;
    }

    isReal() {
        return true;
    }

    async listFiles(path: string): Promise<VirtualFile[]> {
        try {
            return this.folders[path] = await this.fetchFiles(path);
        } catch (e) {
            return [];
        }
    }

    async fetchFiles(path: string, next?: string): Promise<VirtualFile[]> {
        const result = await callAPI(api.browse({
            path,
            itemsPerPage: 250,
            sortBy: "PATH",
            next
        }));

        if (result.next) {
            return toVirtualFiles(result).concat(await this.fetchFiles(path, result.next));
        }

        return toVirtualFiles(result);
    }

    setDirtyFileContent(path: string, content: string): void {
        this.dirtyFileContent[path] = content;
    }

    public moveFileContent(oldPath: string, newPath: string) {
        if (this.dirtyFileContent[oldPath]) {
            const content = this.dirtyFileContent[oldPath];
            delete this.dirtyFileContent[oldPath];
            this.dirtyFileContent[newPath] = content;
        }

        if (this.fetchedFiles[oldPath]) {
            const content = this.fetchedFiles[oldPath];
            delete this.fetchedFiles[oldPath];
            this.fetchedFiles[newPath] = content;
        }
    }

    async readFile(path: string): Promise<string | Uint8Array> {
        const dirty = this.dirtyFileContent[path];
        if (dirty !== undefined) return dirty;
        if (this.fetchedFiles[path]) return this.fetchedFiles[path];

        const file = this.ufiles[path] ?? await callAPI(api.retrieve({id: path}));
        this.ufiles[path] = file;

        if (isFileFileSizeExceeded(file)) {
            throw window.Error("File is to large to preview.");
        }

        if (file.status.type !== "FILE") {
            throw window.Error("Only files can be previewed");
        };

        if (file.status.sizeInBytes === 0) {
            return "";
        }

        const contentBlob = await downloadFileContent(path);
        const contentBuffer = new Uint8Array(await contentBlob.arrayBuffer());
        const text = tryDecodeText(contentBuffer);
        if (!text) {
            return contentBuffer;
        } else {
            this.fetchedFiles[path] = text;
            return text;
        }
    }

    async getFileInfo(path: string): Promise<UFile> {
        return this.ufiles[path] ?? await callAPI(api.retrieve({id: path}));
    }

    async writeFile(path: string): Promise<void> {
        const content = this.dirtyFileContent[path];
        if (content === undefined) return;

        try {
            window.dispatchEvent(new CustomEvent<WriteToFileEventProps>(EventKeys.WriteToFile, {
                detail: {
                    path,
                    content,
                    notifyBackgroundTask: false,
                }
            }));
        } catch (e) {
            errorMessageOrDefault(e, "Failed to save file");
        }
    }
}

function toVirtualFiles(page: PageV2<UFile>): VirtualFile[] {
    return page.items.map(i => ({
        absolutePath: i.id,
        isDirectory: i.status.type === "DIRECTORY",
        requestedSyntax: extensionFromPath(i.id),
        fileHint: i.status.icon
    }));
}

export const EventKeys = {WriteToFile: "write-to-file-event"};

export interface WriteToFileEventProps {
    path: string;
    content: string;
    notifyBackgroundTask?: boolean;
}

const FilePropertiesClass = injectStyle("file-properties", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 24px;
        min-width: min(100%, 560px);
    }

    ${k} .header {
        display: flex;
        align-items: center;
        gap: 20px;
        padding-bottom: 20px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} .header-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 96px;
        height: 96px;
        flex: 0 0 96px;
    }

    ${k} .header-content {
        display: flex;
        flex-direction: column;
        min-width: 0;
        gap: 6px;
    }

    ${k} .type {
        color: var(--textSecondary);
        font-size: 13px;
        text-transform: uppercase;
        letter-spacing: .04em;
    }

    ${k} .path {
        color: var(--textSecondary);
        font-size: 13px;
    }

    ${k} .details {
        display: flex;
        flex-direction: column;
        margin: 0;
    }

    ${k} .details > div {
        display: grid;
        grid-template-columns: minmax(110px, .35fr) minmax(0, 1fr);
        align-items: center;
        gap: 20px;
        height: 52px;
        box-sizing: border-box;
        padding: 10px 0;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} .details dt {
        color: var(--textSecondary);
    }

    ${k} .details dd {
        min-width: 0;
        margin: 0;
        overflow-wrap: anywhere;
    }

    ${k} .actions {
        display: flex;
        justify-content: flex-end;
    }

    @media (max-width: 600px) {
        ${k} .header {
            align-items: flex-start;
        }

        ${k} .header-icon {
            width: 72px;
            height: 72px;
            flex-basis: 72px;
        }

        ${k} .header-icon > svg {
            width: 72px;
            height: 72px;
        }

        ${k} .details > div {
            grid-template-columns: minmax(90px, .35fr) minmax(0, 1fr);
            gap: 8px;
        }
    }
`);

export function showFileProperties(file: UFile): void {
    dialogStore.addDialog(
        <FileProperties file={file} routingNamespace={api.routingNamespace} />,
        doNothing,
        true,
    );
}

function FileProperties({file, routingNamespace}: {file: UFile, routingNamespace: string;}) {
    const prettyPath = usePrettyFilePath(file.id);

    return <div className={FilePropertiesClass}>
        <div className="header">
            <div className="header-icon">
                <FtIcon fileIcon={{type: file.status.type, ext: extensionFromPath(file.id)}} size={96} />
            </div>
            <div className="header-content">
                <Truncate fontSize={25}>{fileName(file.id)}</Truncate>
                <Truncate className="path" title={getParentPath(prettyPath)}>{getParentPath(prettyPath)}</Truncate>
            </div>
        </div>
        <dl className="details">
            <div><dt><b>Path:</b></dt><dd>
                <Flex alignItems="center" gap="8px" minWidth={0}>
                    <Truncate flexGrow={1} title={prettyPath}>{prettyPath}</Truncate>
                    <CopyButton tooltip="Copy file path" onClick={() => copyToClipboard(prettyPath)} />
                </Flex>
            </dd></div>
            <div><dt><b>Product:</b></dt><dd>
                {file.specification.product.id === file.specification.product.category ?
                    file.specification.product.id :
                    `${file.specification.product.id} / ${file.specification.product.category}`
                } @ <ProviderTitle providerId={file.specification.product.provider} />
            </dd></div>
            <div><dt><b>Created at:</b></dt><dd>{dateToString(file.createdAt)}</dd></div>
            {file.status.modifiedAt ? <div><dt><b>Modified at:</b></dt><dd>{dateToString(file.status.modifiedAt)}</dd></div> : null}
            {file.status.accessedAt ? <div><dt><b>Accessed at:</b></dt><dd>{dateToString(file.status.accessedAt)}</dd></div> : null}
            {file.status.sizeInBytes != null && file.status.type !== "DIRECTORY" ?
                <div><dt><b>Size:</b></dt><dd>{sizeToString(file.status.sizeInBytes)}</dd></div> : null}
            {file.status.sizeIncludingChildrenInBytes != null && file.status.type === "DIRECTORY" ?
                <div><dt><b>Size:</b></dt><dd>{sizeToString(file.status.sizeIncludingChildrenInBytes)}</dd></div> : null}
            {file.status.unixOwner != null && file.status.unixGroup != null ?
                <div><dt><b>UID/GID:</b></dt><dd>{file.status.unixOwner}/{file.status.unixGroup}</dd></div> : null}
            {file.status.unixMode != null ?
                <div><dt><b>Unix mode:</b></dt><dd>{readableUnixMode(file.status.unixMode)}</dd></div> : null}
        </dl>
        <div className="actions">
            <Link to={buildQueryString(`/${routingNamespace}`, {path: getParentPath(file.id)})}>
                <Button>View in folder</Button>
            </Link>
        </div>
    </div>
}

export {api};
export default api;
