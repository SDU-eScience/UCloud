import {
    CREATE_TAG, DELETE_TAG, PERMISSIONS_TAG,
    ResourceApi, ResourceBrowseCallbacks,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";
import {BulkRequest, BulkResponse, PageV2} from "@/UCloud/index";
import {FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {Box, Button, Error, Flex, Icon, Link, Select, Text, TextArea, Truncate} from "@/ui-components";
import * as React from "react";
import {
    fileName,
    getParentPath,
    readableUnixMode,
    sizeToString
} from "@/Utilities/FileUtilities";
import {
    bulkRequestOf,
    displayErrorMessageOrDefault, doNothing, inDevEnvironment, onDevSite, prettierString, removeTrailingSlash
} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {dialogStore} from "@/Dialog/DialogStore";
import {ItemRenderer} from "@/ui-components/Browse";
import {usePrettyFilePath} from "@/Files/FilePath";
import {dateToString} from "@/Utilities/DateUtilities";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {OpenWithBrowser} from "@/Applications/OpenWith";
import {addStandardDialog} from "@/UtilityComponents";
import {ProductStorage} from "@/Accounting";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {Client} from "@/Authentication/HttpClientInstance";
import {apiCreate, apiUpdate, callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import metadataDocumentApi from "@/UCloud/MetadataDocumentApi";
import {Spacer} from "@/ui-components/Spacer";
import metadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import MetadataNamespaceApi from "@/UCloud/MetadataNamespaceApi";
import {useEffect, useState} from "react";
import {SyncthingConfig, SyncthingDevice, SyncthingFolder} from "@/Syncthing/api";
import {useParams} from "react-router";
import {Feature, hasFeature} from "@/Features";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {addShareModal} from "@/Files/Shares";
import FileBrowse from "@/Files/FileBrowse";
import {injectStyle, makeClassName} from "@/Unstyled";
import {PredicatedLoadingSpinner} from "@/LoadingIcon/LoadingIcon";
import {usePage} from "@/Navigation/Redux";
import {ExtensionType, extensionFromPath, extensionType, isLightThemeStored, languageFromExtension, typeFromMime} from "@/UtilityFunctions";
import {Markdown} from "@/ui-components";
import {classConcat, injectStyleSimple} from "@/Unstyled";
import fileType from "magic-bytes.js";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {useSelector} from "react-redux";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";
import {
    FilesCopyRequestItem,
    FilesCreateDownloadRequestItem,
    FilesCreateDownloadResponseItem,
    FilesCreateFolderRequestItem,
    FilesCreateUploadRequestItem,
    FilesEmptyTrashRequestItem,
    FilesMoveRequestItem,
    FilesTrashRequestItem,
    UFile,
    UFileIncludeFlags,
    UFileSpecification,
    UFileStatus
} from "./UFile";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

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

const classA = makeClassName("A");
const classB = makeClassName("B");
const FilePropertiesLayout = injectStyle("file-properties-layout", k => `
    ${k} > ${classA.dot} {
        padding: 15px;
        width: 100%;
    }

    ${k} > ${classB.dot} {
        font-size: 14px;
        width: 340px;
        min-width: 240px;
        padding: 15px;
        height: 100vh;
    }
`);

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

        const prettyPath = usePrettyFilePath(id ?? "");
        const downloadRef = React.useRef(new Uint8Array());
        usePage(prettyPath, SidebarTabId.FILES);

        const [, invokeCommand] = useCloudCommand();

        const downloadFunction = React.useMemo(() => {
            return this.retrieveOperations().find(it => it.text === "Download");
        }, []);

        const file = fileData.data;

        const downloadFile = React.useCallback(() => {
            if (!id || !downloadFunction || !file) return;
            if (downloadRef.current.length > 0) {
                const url = window.URL.createObjectURL(
                    new Blob([new Uint8Array(downloadRef.current, 0, downloadRef.current.length)])
                );
                const a = document.createElement("a");
                a.style.display = "none";
                a.href = url;
                a.download = fileName(id);
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
            } else {
                downloadFunction.onClick([file], {invokeCommand} as ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks);
            }
        }, [downloadRef, file, id]);

        if (!id) return <h1>Missing file id.</h1>

        if (fileData.loading) return <PredicatedLoadingSpinner loading />

        if (fileData.error) return <Error error={fileData.error.why} />;
        if (!file) return <></>;

        return <Flex className={FilePropertiesLayout}>
            <div className="A">
                {file.status.type !== "FILE" ? null :
                    <FilePreview file={file} contentRef={downloadRef} />
                }
            </div>

            <div className="B">
                <div><b>Path:</b> <Truncate title={prettyPath}>{prettyPath}</Truncate></div>
                <div>
                    <b>Product: </b>
                    {file.specification.product.id === file.specification.product.category ?
                        <>{file.specification.product.id}</> :
                        <>{file.specification.product.id} / {file.specification.product.category}</>
                    }
                </div>
                <Flex gap="8px">
                    <b>Provider: </b>
                    <ProviderTitle providerId={file.specification.product.provider} />
                </Flex>
                <div><b>Created at:</b> {dateToString(file.createdAt)}</div>
                {file.status.modifiedAt ?
                    <div><b>Modified at:</b> {dateToString(file.status.modifiedAt)}</div> : null}
                {file.status.accessedAt ?
                    <div><b>Accessed at:</b> {dateToString(file.status.accessedAt)}</div> : null}
                {file.status.sizeInBytes != null && file.status.type !== "DIRECTORY" ?
                    <div><b>Size:</b> {sizeToString(file.status.sizeInBytes)}</div> : null}
                {file.status.sizeIncludingChildrenInBytes != null && file.status.type === "DIRECTORY" ?
                    <div><b>Size:</b> {sizeToString(file.status.sizeIncludingChildrenInBytes)}
                    </div> : null
                }
                {file.status.unixOwner != null && file.status.unixGroup != null ?
                    <div><b>UID/GID</b>: {file.status.unixOwner}/{file.status.unixGroup}</div> :
                    null
                }
                {file.status.unixMode != null ?
                    <div><b>Unix mode:</b> {readableUnixMode(file.status.unixMode)}</div> :
                    null
                }
                <Box mt={"16px"} mb={"8px"}>
                    <Link to={buildQueryString(`/${this.routingNamespace}`, {path: getParentPath(file.id)})}>
                        <Button fullWidth>View in folder</Button>
                    </Link>
                </Box>
                <Box mt={"16px"} mb={"8px"} onClick={downloadFile}>
                    <Button fullWidth>Download file</Button>
                </Box>
            </div>
        </Flex>
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
                onClick: (selected, cb) => {
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
                text: "Rename",
                icon: "rename",
                enabled: (selected, cb) => {
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
                    // TODO(Dan): We should probably add a feature flag for file types

                    if (selected.length > 1) {
                        snackbarStore.addInformation("For downloading multiple files, you may need to enable pop-ups.", false, 8000);
                    }

                    const result = await cb.invokeCommand<BulkResponse<FilesCreateDownloadResponseItem>>(
                        this.createDownload(bulkRequestOf(
                            ...selected.map(it => ({id: it.id})),
                        ))
                    );

                    const responses = result?.responses ?? [];
                    for (const {endpoint} of responses) {
                        downloadFile(normalizeDownloadEndpoint(endpoint), responses.length > 1);
                    }
                },
                shortcut: ShortcutKey.D
            },
            {
                icon: "copy",
                text: "Copy to...",
                enabled: (selected, cb) =>
                    (cb.isModal !== true || !!cb.allowMoveCopyOverride) &&
                    selected.length > 0 &&
                    selected.every(it => it.permissions.myself.some(p => p === "READ" || p === "ADMIN")),
                onClick: (selected, cb) => {
                    const pathRef = {current: getParentPath(selected[0].id)};
                    dialogStore.addDialog(
                        <FileBrowse opts={{
                            isModal: true, overrideDisabledKeyhandlers: true, selection: {
                                text: "Copy to",
                                show(res) {return res.status.type === "DIRECTORY"},
                                onClick: async (res) => {
                                    const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                                    try {
                                        await cb.invokeCommand(
                                            this.copy({
                                                type: "bulk",
                                                items: selected.map(file => ({
                                                    oldId: file.id,
                                                    conflictPolicy: "RENAME",
                                                    newId: target + "/" + fileName(file.id)
                                                }))
                                            })
                                        );
                                        cb.reload();
                                        dialogStore.success();
                                        snackbarStore.addSuccess("Files copied", false);
                                    } catch (e) {
                                        displayErrorMessageOrDefault(e, "Failed to move to folder");
                                    }
                                }
                            },
                            additionalFilters: {
                                filterProvider: selected[0].specification.product.provider
                            },
                            initialPath: pathRef.current,
                        }} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle
                    );
                },
                shortcut: ShortcutKey.C
            },
            {
                icon: "move",
                text: "Move to...",
                enabled: (selected, cb) => {
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
                    const pathRef = {current: getParentPath(selected[0].id)};
                    dialogStore.addDialog(
                        <FileBrowse opts={{
                            isModal: true, overrideDisabledKeyhandlers: true, selection: {
                                text: "Move to",
                                show(res) {return res.status.type === "DIRECTORY"},
                                onClick: async (res) => {
                                    const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);

                                    try {
                                        await cb.invokeCommand(
                                            this.move({
                                                type: "bulk",
                                                items: selected.map(file => ({
                                                    oldId: file.id,
                                                    conflictPolicy: "RENAME",
                                                    newId: target + "/" + fileName(file.id)
                                                }))
                                            })
                                        );
                                        cb.reload();
                                        dialogStore.success();
                                        snackbarStore.addSuccess("Files moved", false);
                                    } catch (e) {
                                        displayErrorMessageOrDefault(e, "Failed to move to folder");
                                    }
                                }
                            },
                            initialPath: pathRef.current,
                            additionalFilters: {filterProvider: selected[0].specification.product.provider}
                        }} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle
                    );
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
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if ((support as FileCollectionSupport)) { /* ??? */}

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
                    if (!support) return false;
                    if (!(support as FileCollectionSupport).files.trashSupported) return false;
                    if ((support as FileCollectionSupport).files.isReadOnly) {
                        return "File system is read-only";
                    }
                    if (!(selected.length === 0 && cb.onSelect === undefined)) {
                        return false;
                    }
                    return cb.directory?.status.icon == "DIRECTORY_TRASH";
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
                            await cb.invokeCommand(
                                this.emptyTrash(bulkRequestOf({id: cb.directory?.id ?? ""}))
                            );
                            cb.navigate("/drives");
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
                enabled: (selected, cb) => synchronizationOpEnabled(false, selected, cb),
                onClick: (selected, cb) => {
                    synchronizationOpOnClick(selected, cb)
                },
                shortcut: ShortcutKey.Y
            },
            {
                text: "Open terminal",
                primary: true,
                icon: "terminalSolid",
                enabled: () => hasFeature(Feature.INLINE_TERMINAL),
                onClick: (selected, cb) => {
                    cb.dispatch({type: "TerminalOpen"});
                    cb.dispatch({type: "TerminalOpenTab", tab: {title: "Hippo"}});
                },
                shortcut: ShortcutKey.O
            },
            {
                icon: "trash",
                text: "Move to trash",
                confirm: true,
                color: "errorMain",
                enabled: (selected, cb) => {
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

function fileSize(file: UFile, support?: FileCollectionSupport): undefined | number {
    if (!support) return undefined;
    if (file.status.type === "FILE") {
        if (support.stats.sizeInBytes) {
            return file.status.sizeInBytes;
        }
    } else if (file.status.type === "DIRECTORY") {
        if (support.stats.sizeIncludingChildrenInBytes) {
            return file.status.sizeIncludingChildrenInBytes;
        }
    }

    return undefined;
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
        cb.navigate("/syncthing");
        return;
    }

    if (!cb.setSynchronization) return;


    cb.setSynchronization(files, !allSynchronized);

    snackbarStore.addSuccess(`${allSynchronized ? "Removed from" : "Added to"} Syncthing`, false);
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

function SensitivityDialog({file, invokeCommand, onUpdated}: {file: UFile; invokeCommand: InvokeCommand; onUpdated(value: SensitivityLevelMap): void;}): JSX.Element {
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

                onUpdated(value as SensitivityLevelMap);

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
                <Heading.h2>Sensitive files not supported <Icon name="warning" color="errorMain" size="32" /></Heading.h2>
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

    dialogStore.addDialog(<SensitivityDialog file={file} invokeCommand={invokeCommand} onUpdated={onUpdated} />, () => undefined, true);
}

const api = new FilesApi();

const monacoCache = new AsyncCache<any>();

async function getMonaco(): Promise<any> {
    return monacoCache.retrieve("", async () => {
        const monaco = await (import("monaco-editor"));
        self.MonacoEnvironment = {
            getWorker: function (workerId, label) {
                switch (label) {
                    case 'json':
                        return getWorkerModule('/monaco-editor/esm/vs/language/json/json.worker?worker', label);
                    case 'css':
                    case 'scss':
                    case 'less':
                        return getWorkerModule('/monaco-editor/esm/vs/language/css/css.worker?worker', label);
                    case 'html':
                    case 'handlebars':
                    case 'razor':
                        return getWorkerModule('/monaco-editor/esm/vs/language/html/html.worker?worker', label);
                    case 'typescript':
                    case 'javascript':
                        return getWorkerModule('/monaco-editor/esm/vs/language/typescript/ts.worker?worker', label);
                    default:
                        return getWorkerModule('/monaco-editor/esm/vs/editor/editor.worker?worker', label);
                }


                function getWorkerModule(moduleUrl, label) {
                    return new Worker(self.MonacoEnvironment!.getWorkerUrl!(moduleUrl, label), {
                        name: label,
                        type: 'module'
                    });
                }
            }
        };
        return monaco;
    })
}

export const MAX_PREVIEW_SIZE_IN_BYTES = PREVIEW_MAX_SIZE;

export function FilePreview({file, contentRef}: {file: UFile, contentRef?: React.MutableRefObject<Uint8Array>}): React.ReactNode {
    let oldOnResize: typeof window.onresize;

    React.useEffect(() => {
        oldOnResize = window.onresize;
        return () => {window.onresize = oldOnResize}
    }, [])
    const [type, setType] = useState<ExtensionType>(null);
    const [loading, invokeCommand] = useCloudCommand();

    const codePreview = React.useRef<HTMLDivElement>(null);

    const [data, setData] = useState("");
    const [error, setError] = useState<string | null>(null);

    const fetchData = React.useCallback(async () => {
        const size = file.status.sizeInBytes;
        if (file.status.type !== "FILE") return;
        if (!loading && size != null && size < MAX_PREVIEW_SIZE_IN_BYTES && size > 0) {
            try {
                const download = await invokeCommand<BulkResponse<FilesCreateDownloadResponseItem>>(
                    api.createDownload(bulkRequestOf({id: file.id})),
                    {defaultErrorHandler: false}
                );
                const downloadEndpoint = download?.responses[0]?.endpoint.replace("integration-module:8889", "localhost:9000");
                if (!downloadEndpoint) {
                    setError("Unable to display preview. Try again later or with a different file.");
                    return;
                }
                const content = await fetch(normalizeDownloadEndpoint(downloadEndpoint));
                const contentBlob = await content.blob();
                const contentBuffer = new Uint8Array(await contentBlob.arrayBuffer());

                const foundFileType = fileType(contentBuffer);
                if (contentRef) contentRef.current = contentBuffer;
                if (foundFileType.length === 0) {
                    const text = tryDecodeText(contentBuffer);
                    if (text !== null) {
                        if ([".md", ".markdown"].some(ending => file.id.endsWith(ending))) {
                            setType("markdown")
                        } else setType("text");
                        setData(text);
                        setError(null);
                    } else {
                        setError("Preview is not supported for this file.");
                    }
                } else {
                    const typeFromFileType = typeFromMime(foundFileType[0].mime ?? "") ?? extensionType(foundFileType[0].typename);

                    switch (typeFromFileType) {
                        case "image":
                        case "audio":
                        case "video":
                        case "pdf":
                            setData(URL.createObjectURL(new Blob([contentBlob], {type: foundFileType[0].mime})));
                            setError(null);
                            break;
                        case "code":
                        case "text":
                        case "application":
                        case "markdown":
                        default:
                            const text = tryDecodeText(contentBuffer);
                            if (text !== null) {
                                setType("text");
                                setData(text);
                                setError(null);
                            } else {
                                setError("Preview is not supported for this file.");
                            }
                            break;
                    }

                    setType(typeFromFileType);
                }
            } catch (e) {
                setError("Unable to display preview. Try again later or with a different file.");
            }
        } else if (size != null && size >= MAX_PREVIEW_SIZE_IN_BYTES) {
            setError("File is too large to preview");
        } else if (!size || size <= 0) {
            setError("File is empty");
        } else {
            setError("Preview is not supported for this file.");
        }
    }, [file]);

    useEffect(() => {
        fetchData();
    }, [file]);

    const lightTheme = useSelector((red: ReduxObject) => red.sidebar.theme)
    React.useEffect(() => {
        if (type === "text" || type === "code") {
            const theme = lightTheme === "light" ? "light" : "vs-dark";
            getMonaco().then(monaco => monaco.editor.setTheme(theme));
        }
    }, [lightTheme, type]);

    React.useLayoutEffect(() => {
        const node = codePreview.current;
        if (!node) return;
        if (type === "text" || type === "code") {
            getMonaco().then(monaco => {
                const count = monaco.editor.getEditors().length;
                if (count > 0) return;
                monaco.editor.create(node, {
                    value: data,
                    language: languageFromExtension(extensionFromPath(file.id)),
                    readOnly: true,
                    minimap: {enabled: false},
                    theme: isLightThemeStored() ? "light" : "vs-dark",
                });
                window.onresize = () => {
                    monaco.editor.getEditors().forEach(it => it.layout());
                };
            });
            return () => {
                getMonaco().then(monaco => monaco.editor.getEditors().forEach(it => it.dispose()));
            }
        }
        return () => void 0;
    }, [type, data]);

    if (file.status.type !== "FILE") return null;
    if ((loading || data === "") && !error) return <PredicatedLoadingSpinner loading />

    let node: JSX.Element | null;

    switch (type) {
        case "text":
        case "code":
            node = <div ref={codePreview} className={classConcat(Editor, "fullscreen")} />;
            break;
        case "image":
            node = <img className={Image} alt={fileName(file.id)} src={data} />
            break;
        case "audio":
            node = <audio className={Audio} controls src={data} />;
            break;
        case "video":
            node = <video className={Video} src={data} controls />;
            break;
        case "pdf":
            node = <object type="application/pdf" className={classConcat("fullscreen", PreviewObject)} data={data} />;
            break;
        case "markdown":
            node = <div className={MarkdownStyling}><Markdown>{data}</Markdown></div>;
            break;
        default:
            node = <div />
            break;
    }

    if (error !== null) {
        node = <div>{error}</div>;
    }

    return <div className={ItemWrapperClass}>{node}</div>;
}

const MAX_HEIGHT = `calc(100vw - 15px - 15px - 240px - var(${CSSVarCurrentSidebarStickyWidth}));`
const HEIGHT = "calc(100vh - 30px);"

const MarkdownStyling = injectStyleSimple("markdown-styling", `
    max-width: ${MAX_HEIGHT};
    width: 100%;
`);

const Audio = injectStyleSimple("preview-audio", `
    margin-top: auto;
    margin-bottom: auto;
`);

const Editor = injectStyleSimple("m-editor", `
    height: ${HEIGHT}
    width: 100%;
`);

const Image = injectStyleSimple("preview-image", `
    object-fit: contain;
    max-width: ${MAX_HEIGHT}
    max-height: ${HEIGHT}
`);

const Video = injectStyleSimple("preview-video", `
    max-width: ${MAX_HEIGHT}
`);

const PreviewObject = injectStyleSimple("preview-pdf", `
    max-width: ${MAX_HEIGHT}
    width: 100%;
    max-height: ${HEIGHT}
`)

const ItemWrapperClass = injectStyle("item-wrapper", k => `
    ${k} {
        display: flex;
        justify-content: center;
        height: 100%;
        width: 100%;
        border-radius: 6px;
        overflow: hidden;
        box-shadow: var(--defaultShadow);
    }
`);

function tryDecodeText(buf: Uint8Array): string | null {
    try {
        const d = new TextDecoder("utf-8", {fatal: true});
        return d.decode(buf);
    } catch (e) {
        return null;
    }
}

export {api};
export default api;
