import {
    CREATE_TAG,
    DELETE_TAG,
    Permission,
    PERMISSIONS_TAG,
    ResourceApi,
    ResourceBrowseCallbacks,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";
import {BulkRequest, BulkResponse, PageV2} from "@/UCloud/index";
import FileCollectionsApi, {FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {Box, Button, Flex, Icon, Markdown, Select, Text, TextArea} from "@/ui-components";
import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {
    bulkRequestOf,
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
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {dialogStore} from "@/Dialog/DialogStore";
import {ItemRenderer} from "@/ui-components/Browse";
import {prettyFilePath} from "@/Files/FilePath";
import {OpenWithBrowser} from "@/Applications/OpenWith";
import {addStandardDialog, addStandardInputDialog} from "@/UtilityComponents";
import {ProductStorage} from "@/Accounting";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {Client} from "@/Authentication/HttpClientInstance";
import {apiCreate, apiUpdate, callAPI, InvokeCommand, useCloudAPI} from "@/Authentication/DataHook";
import metadataDocumentApi from "@/UCloud/MetadataDocumentApi";
import {Spacer} from "@/ui-components/Spacer";
import metadataNamespaceApi from "@/UCloud/MetadataNamespaceApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {SyncthingConfig, SyncthingDevice, SyncthingFolder} from "@/Syncthing/api";
import {useParams} from "react-router";
import {Feature, hasFeature} from "@/Features";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {getProviderTitle, ProviderTitle} from "@/Providers/ProviderTitle";
import {addShareModal} from "@/Files/Shares";
import FileBrowse from "@/Files/FileBrowse";
import {classConcat, injectStyleSimple} from "@/Unstyled";
import fileType from "magic-bytes.js";
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
    UFile,
    UFileIncludeFlags,
    UFileSpecification,
    UFileStatus
} from "./UFile";
import AppRoutes from "@/Routes";
import {Editor, EditorApi, Vfs} from "@/Editor/Editor";
import {TooltipV2} from "@/ui-components/Tooltip";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {useDispatch} from "react-redux";
import {VirtualFile} from "@/Files/FileTree";

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
    isSearch: boolean;
    // HACK(Jonas): This is because resource view is technically embedded, but is not in dialog, so it's allowed in
    // special case.
    allowMoveCopyOverride?: boolean;
    syncthingConfig?: SyncthingConfig;
    setSynchronization?: (file: UFile[], shouldAdd: boolean) => void;
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
    ResourceUpdate, UFileIncludeFlags, UFileStatus, FileCollectionSupport> {
    constructor() {
        super("files");
        this.sortEntries = [];
    }

    public routingNamespace = "files";
    public title = "File";
    public productType = "STORAGE" as const

    public idIsUriEncoded = true;

    renderer: ItemRenderer<UFile, ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks> = {};

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

        React.useEffect(() => {
            if (!id) return;
            fetchFile(this.retrieve({
                id,
                includeUpdates: true,
                includeOthers: true,
                includeSupport: true,
                ...this.defaultRetrieveFlags
            }))
        }, [id]);

        const file = fileData.data;

        if (!id) return <h1>Missing file id.</h1>;
        if (!file) return <></>;

        return <FilePreview initialFile={file} />
    }

    public retrieveOperations(): Operation<UFile, ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks>[] {
        const base = super.retrieveOperations()
            .filter(it => it.tag !== CREATE_TAG && it.tag !== PERMISSIONS_TAG && it.tag !== DELETE_TAG);
        const ourOps: Operation<UFile, ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks>[] = [
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

                    if (cb.collection?.permissions?.myself?.some(perm => perm === "ADMIN" || perm === "EDIT") != true) {
                        return "You do not have write permissions in this folder";
                    }
                    return true;
                },
                onClick: (_, cb) => {
                    cb.dispatch({
                        type: "GENERIC_SET", property: "uploaderVisible", newValue: true,
                        defaultValue: false
                    });
                },
                shortcut: ShortcutKey.U
            },
            {
                text: "Create folder",
                icon: "uploadFolder",
                primary: true,
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    if (selected.length !== 0 || cb.startCreation == null) return false;
                    if (cb.isCreating) return "You are already creating a folder";
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (cb.collection?.permissions?.myself?.some(perm => perm === "ADMIN" || perm === "EDIT") != true) {
                        return "You do not have write permissions in this folder";
                    }
                    return true;
                },
                onClick: (selected, cb) => cb.startCreation!(),
                shortcut: ShortcutKey.F
            },
            {
                text: "Launch with...",
                icon: "open",
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
                shortcut: ShortcutKey.P,
            },
            {
                text: "Rename",
                icon: "rename",
                enabled: (selected, cb) => {
                    if (cb.isSearch) return false;
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (selected.some(it => it.status.icon === "DIRECTORY_TRASH")) return false;
                    return selected.length === 1 &&
                        selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN"));
                },
                onClick: (selected, cb) => {
                    cb.startRenaming?.(selected[0], fileName(selected[0].id));
                },
                shortcut: ShortcutKey.F
            },
            {
                text: "Download",
                icon: "download",
                enabled: selected => selected.length > 0 && selected.every(it => it.status.type === "FILE"),
                onClick: async (selected, cb) => {
                    this.download(selected.map(it => it.id));
                },
                shortcut: ShortcutKey.D
            },
            {
                icon: "copy",
                text: "Copy to...",
                enabled: (selected, cb) =>
                    (cb.isModal !== true || !!cb.allowMoveCopyOverride) &&
                    !cb.isSearch &&
                    selected.length > 0 &&
                    selected.every(it => it.permissions.myself.some(p => p === "READ" || p === "ADMIN")),
                onClick: (selected, cb) => {
                    this.copyModal(selected.map(it => it.id), selected[0].specification.product.provider, cb.reload);
                },
                shortcut: ShortcutKey.C
            },
            {
                icon: "heroPaperAirplane",
                text: "Transfer to...",
                enabled: (selected, cb) =>
                    hasFeature(Feature.TRANSFER_TO) &&
                    !cb.isSearch &&
                    (cb.isModal !== true || !!cb.allowMoveCopyOverride) &&
                    selected.length > 0 &&
                    selected.every(it => it.permissions.myself.some(p => p === "READ" || p === "ADMIN")),
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
                                            res.specification.product.provider === "goslurm1"
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
                                        snackbarStore.addSuccess("Files are now transferring...", false);
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
                icon: "move",
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
                        selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN"));
                },
                onClick: (selected, cb) => {
                    this.moveModal(selected.map(it => it.id), selected[0].specification.product.provider, cb.reload);
                },
                shortcut: ShortcutKey.M
            },
            {
                icon: "share",
                text: "Share",
                enabled: (selected, cb) => {
                    if (Client.hasActiveProject) return false;
                    if (selected.length != 1) return false;

                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if ((support as FileCollectionSupport).files.sharesSupported === false) return false;

                    const isMissingPermissions = selected.some(it => !it.permissions.myself.some(p => p === "ADMIN"));
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
                    if (cb.collection?.permissions?.myself?.some(perm => perm === "ADMIN" || perm === "EDIT") != true) {
                        return false;
                    }
                    return selected.length === 1;
                },
                onClick(selected, extra) {
                    addFileSensitivityDialog(selected[0], extra.invokeCommand, extra.reload);
                },
                shortcut: ShortcutKey.H
            },
            {
                // Empty trash of current directory
                text: "Empty Trash",
                icon: "trash",
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
                icon: "refresh",
                enabled: (selected, cb) => !cb.isSearch && synchronizationOpEnabled(false, selected, cb),
                onClick: (selected, cb) => {
                    synchronizationOpOnClick(selected, cb)
                },
                shortcut: ShortcutKey.Y
            },
            {
                text: "Open terminal",
                primary: true,
                icon: "terminalSolid",
                enabled: (selected, cb) => {
                    let support = cb.collection?.status?.resolvedSupport?.support;
                    if (!support) return false;
                    if (selected.length > 0) return false;
                    if (!hasFeature(Feature.INLINE_TERMINAL)) return false;
                    return (support as FileCollectionSupport).files.openInTerminal === true;
                },
                onClick: (selected, cb) => {
                    const providerId = cb.collection?.status?.resolvedProduct?.category?.provider ?? "";
                    const providerTitle = getProviderTitle(providerId);
                    const folder = cb.directory?.id ?? "/";

                    cb.dispatch({type: "TerminalOpen"});
                    cb.dispatch({type: "TerminalOpenTab", tab: {title: providerTitle, folder}});
                },
                shortcut: ShortcutKey.O
            },
            {
                icon: "trash",
                text: "Move to trash",
                confirm: true,
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
                        selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN"))
                        && selected.every(f => f.specification.product)
                        && selected.every(f => f.status.icon !== "DIRECTORY_TRASH");
                },
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.trash(bulkRequestOf(...selected.map(it => ({id: it.id}))))
                    );
                    cb.reload();
                },
                shortcut: ShortcutKey.R
            },
            {
                icon: "trash",
                text: "Empty Trash",
                confirm: true,
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

        return ourOps.concat(base);
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
            snackbarStore.addInformation("For downloading multiple files, you may need to enable pop-ups.", false, 8000);
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
                            snackbarStore.addSuccess("Files copied", false);
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
                            snackbarStore.addSuccess("Files moved", false);
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

function synchronizationOpText(files: UFile[], callbacks: ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks): string {
    const devices: SyncthingDevice[] = callbacks.syncthingConfig?.devices ?? [];
    if (devices.length === 0) return "Sync setup";

    const synchronized: SyncthingFolder[] = callbacks.syncthingConfig?.folders ?? [];
    const resolvedFiles = files.length === 0 ? (callbacks.directory ? [callbacks.directory] : []) : files;

    const allSynchronized = resolvedFiles.every(selected => synchronized.some(it => it.ucloudPath === selected.id));

    if (allSynchronized) {
        return "Remove from sync";
    } else {
        return "Add to sync";
    }
}

function synchronizationOpEnabled(isDir: boolean, files: UFile[], cb: ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks): boolean | string {
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

async function synchronizationOpOnClick(files: UFile[], cb: ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks) {
    const synchronized: SyncthingFolder[] = cb.syncthingConfig?.folders ?? [];
    const resolvedFiles = files.length === 0 ? (cb.directory ? [cb.directory] : []) : files;
    const allSynchronized = resolvedFiles.every(selected => synchronized.some(it => it.ucloudPath === selected.id));

    if (!cb.syncthingConfig) return;
    if (!allSynchronized) {
        const synchronizedFolderNames = synchronized.map(it => it.ucloudPath.split("/").pop());

        for (const folder of resolvedFiles) {
            if (synchronizedFolderNames.includes(folder.id.split("/").pop())) {
                snackbarStore.addFailure("Folder with same name already exist in Syncthing", false)
                return;
            }
        }

        for (const folder of resolvedFiles) {
            const sensitivity = await findSensitivity(folder);
            if (sensitivity == "SENSITIVE") {
                snackbarStore.addFailure("Folder marked as sensitive cannot be added to Syncthing", false)
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

    snackbarStore.addSuccess(`${allSynchronized ? "Removed from" : "Added to"} Syncthing`, false);
}

export function isReadonly(entries: Permission[]): boolean {
    const isAdmin = entries.includes("ADMIN");
    const isEdit = entries.includes("EDIT") || isAdmin;
    const isRead = entries.includes("READ");
    return isRead && !isEdit;
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
    const selection = React.useRef<HTMLSelectElement>(null);
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
            } else {
                if (!sensitivityTemplateId) {
                    sensitivityTemplateId = await queryTemplateName(sensitivityTemplateId, invokeCommand);
                    if (!sensitivityTemplateId) {
                        snackbarStore.addFailure("Failed to change sensitivity.", false);
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
    document.body.removeChild(element);
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
    if (file.permissions.myself?.some(perm => perm === "ADMIN" || perm === "EDIT") != true) {
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
    const [openFile, setOpenFile] = useState<[string, string | Uint8Array]>(["", ""]);
    const [previewRequested, setPreviewRequested] = useState(false);
    const [drive, setDrive] = useState<FileCollection | null>(null);
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

    const [vfsTitle, setTitle] = useState(getParentPath(initialFile.id));

    React.useEffect(() => {
        prettyFilePath(getParentPath(initialFile.id)).then(t => {
            setTitle(t);
        })
    }, []);

    useEffect(() => {
        setPreviewRequested(false);
    }, [openFile[0]]);

    const mediaFileMetadata: null | {type: ExtensionType, data: string, error: string | null} = useMemo(() => {
        let [file, contentBuffer] = openFile;
        if (typeof contentBuffer === "string") {
            if (previewRequested) {
                contentBuffer = new TextEncoder().encode(contentBuffer);
            } else {
                return null;
            }
        }
        const foundFileType = getFileTypesFromContentBuffer(contentBuffer);
        let typeFromFileType =
            foundFileType.length > 0 ?
                typeFromMime(foundFileType[0].mime ?? "") : null;

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
                            [contentBuffer],
                            {type: foundFileType[0].mime}
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
        editorRef.current?.notifyDirtyBuffer().then(() => {
            setPreviewRequested(p => !p);
        });
    }, []);

    const onSave = useCallback(async () => {
        const editor = editorRef.current;
        if (!editor) return;
        if (!hasFeature(Feature.INTEGRATED_EDITOR)) return;

        await editor.notifyDirtyBuffer();
        await vfs.writeFile(editor.path);

        snackbarStore.addSuccess("File has been saved", false, 800);
    }, [vfs]);

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


    if (initialFile.status.type !== "FILE") return null;

    let node: React.ReactNode = null;

    const ext = extensionType(extensionFromPath(openFile[0]));

    switch (mediaFileMetadata?.type) {
        case "text":
        case "code":
            node = null;
            break;
        case "image":
            node = <img className={Image} alt={fileName(initialFile.id)} src={mediaFileMetadata.data} />
            break;
        case "audio":
            node = <audio className={Audio} controls src={mediaFileMetadata.data} />;
            break;
        case "video":
            node = <video className={Video} src={mediaFileMetadata.data} controls />;
            break;
        case "pdf":
            node = <object type="application/pdf" className={classConcat("fullscreen", PreviewObject)} data={mediaFileMetadata.data} />;
            break;
        case "markdown":
            node = <div className={MarkdownStyling}><Markdown>{mediaFileMetadata.data}</Markdown></div>;
            break;
    }

    if (mediaFileMetadata && mediaFileMetadata.error !== null) {
        node = <div>{mediaFileMetadata?.error}</div>;
    }

    const onOpenFile = useCallback((path: string, data: string | Uint8Array) => {
        setOpenFile([path, data]);
    }, []);

    const openTerminal = useCallback(() => {
        if (!drive) return;
        const providerId = drive.specification.product.provider;
        const providerTitle = getProviderTitle(providerId) ?? providerId;
        const folder = getParentPath(initialFile.id);

        dispatch({type: "TerminalOpen"});
        dispatch({type: "TerminalOpenTab", tab: {title: providerTitle, folder}});
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
        window.dispatchEvent(new CustomEvent<WriteToFileEventProps>(EventKeys.WriteToFile, {
            detail: {
                path: newPath,
                content: "",
            }
        }));

        // TODO(Jonas): Add check that file exists or even can be created (has active allocation)

        setTimeout(() => {
            editorRef.current?.invalidateTree?.(getParentPath(path));
            editorRef.current?.openFile?.(newPath);
        }, 200);
    }, [openFile[0]]);

    const operations = useCallback((file: VirtualFile): Operation<any>[] => {
        if (!hasFeature(Feature.INTEGRATED_EDITOR)) return [];
        const reload = () => {
            editorRef.current?.invalidateTree(removeTrailingSlash(getParentPath(initialFile.id)));
        }
        return [
            {
                icon: "heroFolderPlus",
                text: "New folder",
                enabled: () => true,
                onClick: () => {
                    const suffix = file.isDirectory ? "/placeholder" : "";
                    newFolder(file.absolutePath + suffix).then(doNothing);
                },
                shortcut: ShortcutKey.F,
            },
            {
                icon: "heroDocumentPlus",
                text: "New file",
                enabled: () => true,
                onClick: () => {
                    const suffix = file.isDirectory ? "/placeholder" : "";
                    newFile(file.absolutePath + suffix).then(doNothing);
                },
                shortcut: ShortcutKey.G,
            },
            /* {
                // TODO(Jonas): This is not as easy as the others to implement.
                icon: "edit",
                text: "Rename",
                enabled: () => true,
                onClick: () => {
                    prompt("Yeah, todo, sorry.")
                },
                shortcut: ShortcutKey.E
            }, */
            {
                icon: "trash",
                text: "Move to trash",
                enabled: () => true,
                onClick: async () => {
                    await callAPI(
                        api.trash({
                            type: "bulk",
                            items: [{id: file.absolutePath}],
                        })
                    );
                    reload();
                    snackbarStore.addSuccess("File(s) moved to trash", false);
                },
                shortcut: ShortcutKey.R
            },
            {
                icon: "copy",
                text: "Copy file",
                enabled: () => true,
                onClick: () => {
                    api.copyModal([file.absolutePath], initialFile.specification.product.provider, reload);
                },
                shortcut: ShortcutKey.C
            },
            {
                icon: "move",
                text: "Move file",
                enabled: () => true,
                onClick: () => {
                    api.moveModal([file.absolutePath], initialFile.specification.product.provider, reload);
                },
                shortcut: ShortcutKey.M
                // MOVE
            },
            {
                icon: "download",
                text: "Download file",
                enabled: () => true,
                onClick: async () => {
                    api.download([file.absolutePath]);
                },
                shortcut: ShortcutKey.D
                // DOWNLOAD
            },
        ];
    }, []);

    return <Editor
        apiRef={editorRef}
        toolbarBeforeSettings={
            <>
                {ext === "markdown" ?
                    <TooltipV2 tooltip={"Preview (ctrl+b)"} contentWidth={80}>
                        <Icon
                            name={"heroMagnifyingGlass"}
                            size={"20px"}
                            cursor={"pointer"}
                            onClick={requestPreviewToggle}
                        />
                    </TooltipV2> : null}

                {!hasFeature(Feature.INTEGRATED_EDITOR) ? null :
                    <TooltipV2 tooltip={"Save (ctrl+s)"} contentWidth={100}>
                        <Icon
                            name={"floppyDisk"}
                            size={"20px"}
                            cursor={"pointer"}
                            onClick={onSave}
                        />
                    </TooltipV2>
                }
            </>
        }
        toolbar={
            <>
                {!supportsTerminal || !hasFeature(Feature.INLINE_TERMINAL) || !hasFeature(Feature.INTEGRATED_EDITOR) ? null :
                    <TooltipV2 tooltip={"Open terminal"} contentWidth={130}>
                        <Icon
                            name={"terminalSolid"}
                            size={"20px"}
                            cursor={"pointer"}
                            onClick={openTerminal}
                        />
                    </TooltipV2>
                }
            </>
        }
        initialFolderPath={removeTrailingSlash(getParentPath(initialFile.id))}
        initialFilePath={initialFile.id}
        title={vfsTitle}
        vfs={vfs}
        showCustomContent={node != null}
        customContent={node}
        onOpenFile={onOpenFile}
        operations={operations}
        fileHeaderOperations={
            <>
                <Icon name="heroDocumentPlus" cursor="pointer" size="18px" onClick={() => newFile(initialFile.id)} />
                <Icon name="heroFolderPlus" cursor="pointer" size="18px" onClick={() => newFolder(initialFile.id)} />
            </>
        }
        help={
            <Flex mx="auto" mt="150px">
                <Box>
                    <Text mb="12px">Create new file and start working</Text>
                    <Flex mx="auto">
                        <Button mx="auto" onClick={() => newFile(initialFile.id)}>New file</Button>
                        <Button mx="auto" onClick={() => newFolder(initialFile.id)}>New folder</Button>
                    </Flex>
                </Box>
            </Flex>
        }
        readOnly={!hasFeature(Feature.INTEGRATED_EDITOR)}
    />;
}

async function downloadFileContent(path: string): Promise<Blob> {
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
const HEIGHT = "calc(100vh - 100px);"

const MarkdownStyling = injectStyleSimple("markdown-styling", `
    max-width: 900px;
    width: 100%;
`);

const Audio = injectStyleSimple("preview-audio", `
    margin-top: auto;
    margin-bottom: auto;
`);

const Image = injectStyleSimple("preview-image", `
    object-fit: contain;
    max-width: ${MAX_HEIGHT}
    max-height: ${HEIGHT}
`);

const Video = injectStyleSimple("preview-video", `
    max-width: ${MAX_HEIGHT}
    max-height: ${HEIGHT}
`);

const PreviewObject = injectStyleSimple("preview-pdf", `
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

function getFileTypesFromContentBuffer(contentBuffer: Uint8Array) {
    return fileType(contentBuffer).filter(it => it.mime);
}

class PreviewVfs implements Vfs {
    private fetchedFiles: Record<string, string> = {};
    private folders: Record<string, VirtualFile[]> = {};
    private ufiles: Record<string, UFile> = {};
    private dirtyFileContent: Record<string, string> = {};

    constructor(previewedFile: UFile) {
        this.ufiles[previewedFile.id] = previewedFile;
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
            return text;
        }
    }

    async writeFile(path: string): Promise<void> {
        const content = this.dirtyFileContent[path];
        if (content === undefined) return;

        try {
            window.dispatchEvent(new CustomEvent<WriteToFileEventProps>(EventKeys.WriteToFile, {
                detail: {
                    path,
                    content
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
    }));
}

export const EventKeys = {WriteToFile: "write-to-file-event"};

export interface WriteToFileEventProps {
    path: string;
    content: string;
}

export {api};
export default api;
