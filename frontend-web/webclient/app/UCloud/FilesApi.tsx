import {
    CREATE_TAG, DELETE_TAG, FindById, PERMISSIONS_TAG,
    Resource,
    ResourceApi, ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {FileIconHint, FileType} from "@/Files";
import {BulkRequest, BulkResponse, PageV2} from "@/UCloud/index";
import {FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {SidebarPages} from "@/ui-components/Sidebar";
import {Box, Button, FtIcon, Link, Select, Text, TextArea} from "@/ui-components";
import * as React from "react";
import {
    fileName,
    getParentPath,
    readableUnixMode,
    sizeToString
} from "@/Utilities/FileUtilities";
import {displayErrorMessageOrDefault, doNothing, extensionFromPath, prettierString, removeTrailingSlash} from "@/UtilityFunctions";
import {Operation} from "@/ui-components/Operation";
import {UploadProtocol, WriteConflictPolicy} from "@/Files/Upload";
import {bulkRequestOf, SensitivityLevelMap} from "@/DefaultObjects";
import {dialogStore} from "@/Dialog/DialogStore";
import {FilesBrowse} from "@/Files/Files";
import {ResourceProperties} from "@/Resource/Properties";
import {CheckboxFilter} from "@/Resource/Filter";
import {ItemRenderer} from "@/ui-components/Browse";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {MetadataBrowse} from "@/Files/Metadata/Documents/Browse";
import {FileMetadataHistory} from "@/UCloud/MetadataDocumentApi";
import {FileFavoriteToggle} from "@/Files/FavoriteToggle";
import {PrettyFilePath} from "@/Files/FilePath";
import {dateToString} from "@/Utilities/DateUtilities";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {OpenWith} from "@/Applications/OpenWith";
import {FilePreview} from "@/Files/Preview";
import {addStandardDialog, addStandardInputDialog, Sensitivity} from "@/UtilityComponents";
import {ProductStorage} from "@/Accounting";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {ListRowStat} from "@/ui-components/List";
import SharesApi from "@/UCloud/SharesApi";
import {BrowseType} from "@/Resource/BrowseType";
import {Client} from "@/Authentication/HttpClientInstance";
import {callAPI, InvokeCommand} from "@/Authentication/DataHook";
import metadataDocumentApi from "@/UCloud/MetadataDocumentApi";
import {Spacer} from "@/ui-components/Spacer";
import metadataNamespaceApi, {FileMetadataTemplate, FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import MetadataNamespaceApi from "@/UCloud/MetadataNamespaceApi";
import {useEffect, useState} from "react";

export type UFile = Resource<ResourceUpdate, UFileStatus, UFileSpecification>;

export interface UFileStatus extends ResourceStatus {
    type: FileType;
    icon?: FileIconHint;
    sizeInBytes?: number;
    sizeIncludingChildrenInBytes?: number;
    modifiedAt?: number;
    accessedAt?: number;
    unixMode?: number;
    unixOwner?: number;
    unixGroup?: number;
    metadata?: FileMetadataHistory;
}

export interface UFileSpecification extends ResourceSpecification {
    collection: string;
}

export interface UFileIncludeFlags extends ResourceIncludeFlags {
    includePermissions?: boolean;
    includeTimestamps?: boolean;
    includeSizes?: boolean;
    includeUnixInfo?: boolean;
    includeMetadata?: boolean;
    path?: string;
}

export interface FilesMoveRequestItem {
    oldId: string;
    newId: string;
    conflictPolicy: WriteConflictPolicy;
}

type FilesCopyRequestItem = FilesMoveRequestItem;

export interface FilesCreateFolderRequestItem {
    id: string;
    conflictPolicy: WriteConflictPolicy;
}

export interface FilesCreateUploadRequestItem {
    id: string;
    supportedProtocols: UploadProtocol[];
    conflictPolicy: WriteConflictPolicy;
}

export interface FilesCreateUploadResponseItem {
    endpoint: string;
    protocol: UploadProtocol;
    token: string;
}

export interface FilesCreateDownloadRequestItem {
    id: string;
}

export interface FilesCreateDownloadResponseItem {
    endpoint: string;
}

interface FilesTrashRequestItem {
    id: string;
}

interface FilesEmptyTrashRequestItem {
    id: string;
}

interface ExtraCallbacks {
    collection?: FileCollection;
    directory?: UFile;
}

const FileSensitivityVersion = "1.0.0";
const FileSensitivityNamespace = "sensitivity";
type SensitivityLevel = | "PRIVATE" | "SENSITIVE" | "CONFIDENTIAL";
let sensitivityTemplateId = "";
async function findSensitivityWithFallback(file: UFile): Promise<SensitivityLevel> {
    return (await findSensitivity(file)) ?? "PRIVATE";
}

async function findSensitivity(file: UFile): Promise<SensitivityLevel | undefined> {
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
        this.sortEntries.push({
            column: "PATH",
            icon: "id",
            title: "Name",
            helpText: "By the file's name"
        }, {
            column: "MODIFIED_AT",
            icon: "edit",
            title: "Modified time",
            helpText: "When the file was last modified"
        }, {
            column: "SIZE",
            icon: "fullscreen",
            title: "Size",
            helpText: "By size of the file"
        });
        this.filterWidgets = [];
        this.filterPills = [];

        this.registerFilter(CheckboxFilter("search", "filterHiddenFiles", "Show hidden files", true));
    }

    routingNamespace = "files";
    title = "File";
    page = SidebarPages.Files;
    productType = "STORAGE" as const

    idIsUriEncoded = true;

    renderer: ItemRenderer<UFile> = {
        MainTitle({resource}) {return <>{resource ? fileName(resource.id) : ""}</>},
        Icon(props: {resource?: UFile, size: string}) {
            const file = props.resource;
            const favoriteComponent = parseInt(props.size.replace("px", "")) > 40 ? null :
                <FileFavoriteToggle file={file} />;
            const icon = !file ?
                <FtIcon fileIcon={{type: "DIRECTORY"}} size={props.size} /> :
                <FtIcon
                    iconHint={file.status.icon}
                    fileIcon={{type: file.status.type, ext: extensionFromPath(fileName(file.id))}}
                    size={props.size}
                />;
            return <>{favoriteComponent}{icon}</>
        },
        ImportantStats({resource, callbacks}) {
            if (!resource) return null;
            const sensitivity = useSensitivity(resource);

            return <div style={{cursor: "pointer"}} onClick={() => addFileSensitivityDialog(resource, callbacks.invokeCommand, callbacks.reload)}>
                <Sensitivity sensitivity={sensitivity ?? "PRIVATE"} />
            </div>;
        },
        Stats({resource}) {
            if (resource == null) return null;
            const size = resource.status.sizeIncludingChildrenInBytes ?? resource.status.sizeInBytes;
            return <>
                {size === undefined ? null : <ListRowStat icon={"rulerSolid"}>{sizeToString(size)}</ListRowStat>}
                {resource.status.unixMode === undefined ? null :
                    <ListRowStat icon={"verified"}>{readableUnixMode(resource.status.unixMode)}</ListRowStat>}
            </>;
        }
    };

    private defaultRetrieveFlags: Partial<UFileIncludeFlags> = {
        includeMetadata: true,
        includeSizes: true,
        includeTimestamps: true,
        includeUnixInfo: true
    };

    public Properties = (props) => {
        return <ResourceProperties
            {...props} api={this}
            showMessages={false} showPermissions={false} showProperties={false}
            flagsForRetrieve={this.defaultRetrieveFlags}
            InfoChildren={props => {
                const file = props.resource as UFile;
                return <>
                    <HighlightedCard color={"purple"} title={"Location"} icon={"mapMarkedAltSolid"}>
                        <div><b>Path:</b> <PrettyFilePath path={file.id} /></div>
                        <div>
                            <b>Product: </b>
                            {file.specification.product.id} / {file.specification.product.category}
                        </div>
                        <div><b>Provider: </b> {file.specification.product.provider}</div>
                        <Box mt={"16px"} mb={"8px"}>
                            <Link to={buildQueryString(`/${this.routingNamespace}`, {path: getParentPath(file.id)})}>
                                <Button fullWidth>View in folder</Button>
                            </Link>
                        </Box>
                    </HighlightedCard>
                    <HighlightedCard color={"purple"} title={"Properties"} icon={"properties"}>
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
                    </HighlightedCard>
                </>
            }}
            ContentChildren={props => (
                <>
                    <HighlightedCard color={"purple"}>
                        <MetadataBrowse
                            file={props.resource as UFile}
                            metadata={(props.resource as UFile).status.metadata ?? {metadata: {}, templates: {}}}
                            reload={props.reload}
                        />
                    </HighlightedCard>
                    {(props.resource as UFile).status.type !== "FILE" ? null :
                        <HighlightedCard color={"purple"} title={"Preview"} icon={"search"}>
                            <FilePreview file={props.resource as UFile} />
                        </HighlightedCard>
                    }
                </>
            )}
        />;
    };

    public retrieveOperations(): Operation<UFile, ResourceBrowseCallbacks<UFile> & ExtraCallbacks>[] {
        const base = super.retrieveOperations()
            .filter(it => it.tag !== CREATE_TAG && it.tag !== PERMISSIONS_TAG && it.tag !== DELETE_TAG);
        const ourOps: Operation<UFile, ResourceBrowseCallbacks<UFile> & ExtraCallbacks>[] = [
            {
                text: "Use this folder",
                primary: true,
                canAppearInLocation: (loc) => loc === "TOPBAR",
                enabled: (selected, cb) => {
                    return selected.length === 0 && cb.onSelect !== undefined && cb.directory != null &&
                        (cb.onSelectRestriction == null || cb.onSelectRestriction(cb.directory));
                },
                onClick: (selected, cb) => {
                    cb.onSelect?.({
                        id: "",
                        status: {type: "DIRECTORY"},
                        permissions: {myself: []},
                        specification: {product: {id: "", provider: "", category: ""}, collection: ""},
                        owner: {createdBy: ""},
                        createdAt: 0,
                        updates: []
                    })
                }
            },
            {
                text: "Upload files",
                icon: "upload",
                primary: true,
                canAppearInLocation: location => location === "SIDEBAR",
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
            },
            {
                text: "Create folder",
                icon: "uploadFolder",
                color: "blue",
                primary: true,
                canAppearInLocation: loc => loc === "SIDEBAR",
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
            },
            {
                text: "Open with...",
                icon: "open",
                enabled: (selected, cb) => selected.length === 1 && cb.collection != null,
                onClick: (selected, cb) => {
                    if (cb.collection) {
                        dialogStore.addDialog(
                            <OpenWith file={selected[0]} collection={cb.collection} />,
                            doNothing,
                            true,
                            this.fileSelectorModalStyle
                        );
                    }
                }
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
                    return selected.length === 1 &&
                        selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN"));
                },
                onClick: (selected, cb) => {
                    cb.startRenaming?.(selected[0], fileName(selected[0].id));
                }
            },
            {
                text: "Download",
                icon: "download",
                enabled: (selected, cb) => selected.length >= 1 && selected.every(it => it.status.type === "FILE"),
                onClick: async (selected, cb) => {
                    // TODO(Dan): We should probably add a feature flag for file types
                    const result = await cb.invokeCommand<BulkResponse<FilesCreateDownloadResponseItem>>(
                        this.createDownload(bulkRequestOf(
                            ...selected.map(it => ({id: it.id})),
                        ))
                    );

                    const responses = result?.responses ?? [];
                    for (const {endpoint} of responses) {
                        downloadFile(endpoint);
                    }
                }
            },
            {
                icon: "copy",
                text: "Copy to...",
                enabled: (selected, cb) =>
                    cb.embedded !== true &&
                    selected.length > 0 &&
                    selected.every(it => it.permissions.myself.some(p => p === "READ" || p === "ADMIN")),
                onClick: (selected, cb) => {
                    const pathRef = {current: getParentPath(selected[0].id)};
                    dialogStore.addDialog(
                        <FilesBrowse browseType={BrowseType.Embedded} pathRef={pathRef} onSelect={async res => {
                            const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);

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
                        }} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle
                    );
                }
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
                    return cb.embedded !== true &&
                        selected.length > 0 &&
                        selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN"));
                },
                onClick: (selected, cb) => {
                    const pathRef = {current: getParentPath(selected[0].id)};
                    dialogStore.addDialog(
                        <FilesBrowse browseType={BrowseType.Embedded} pathRef={pathRef} onSelect={async (res) => {
                            const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);

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
                        }} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle
                    );
                }
            },
            {
                icon: "share",
                text: "Share",
                enabled: (selected, cb) => {
                    if (Client.hasActiveProject) {return false;}
                    if (selected.length === 0) return false;
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
                    const username = await addStandardInputDialog({
                        title: "Share",
                        help: <>The username of the user you wish to share this file with</>,
                        addToFront: true,
                        confirmText: "Share",
                        width: "100%"
                    });

                    await cb.invokeCommand<BulkResponse<FindById>>(
                        SharesApi.create(
                            bulkRequestOf(
                                ...selected.map(file => ({
                                    sharedWith: username.result,
                                    sourceFilePath: file.id,
                                    permissions: ["READ" as const],
                                    product: file.specification.product
                                }))
                            )
                        )
                    ).then(it => {
                        if (it?.responses) {
                            cb.history.push(`/shares/outgoing`);
                        }
                    });
                }
            },
            {
                icon: "trash",
                text: "Move to trash",
                confirm: true,
                color: "red",
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
                }
            },
            {
                text: "Change sensitivity",
                icon: "sensitivity",
                enabled(selected, cb) {
                    if (cb.collection?.permissions?.myself?.some(perm => perm === "ADMIN" || perm === "EDIT") != true) {
                        return false;
                    }
                    return selected.length === 1;
                },
                onClick(selected, extra) {
                    addFileSensitivityDialog(selected[0], extra.invokeCommand, extra.reload);
                }
            },
            {
                // Empty trash of current directory
                text: "Empty Trash",
                icon: "trash",
                color: "red",
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
                    if (cb.directory?.status.icon == "DIRECTORY_TRASH") {
                        return true;
                    }
                    return false;
                },
                onClick: async (_, cb) => {
                    addStandardDialog({
                        title: "Are you sure you wish to empty the trash?",
                        message: "You cannot recover deleted files!",
                        confirmText: "Empty trash",
                        addToFront: true,
                        cancelButtonColor: "blue",
                        confirmButtonColor: "red",
                        onConfirm: async () => {
                            await cb.invokeCommand(
                                this.emptyTrash(bulkRequestOf({id: cb.directory?.id ?? ""}))
                            );
                            cb.reload()
                        },
                        onCancel: doNothing,
                    });
                },
            },
            {
                icon: "trash",
                text: "Empty Trash",
                confirm: true,
                color: "red",
                enabled: (selected, cb) => {
                    const support = cb.collection?.status.resolvedSupport?.support;
                    if (!support) return false;
                    if (selected.length == 1 && selected[0].status.icon == "DIRECTORY_TRASH") {
                        return true;
                    }
                    return false
                },
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.emptyTrash(bulkRequestOf(...selected.map(it => ({id: it.id}))))
                    );
                    cb.reload()
                }
            },
        ];

        return ourOps.concat(base);
    }

    public copy(request: BulkRequest<FilesCopyRequestItem>): APICallParameters<BulkRequest<FilesCopyRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/copy",
            parameters: request,
            payload: request
        };
    }

    public move(request: BulkRequest<FilesMoveRequestItem>): APICallParameters<BulkRequest<FilesMoveRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/move",
            parameters: request,
            payload: request
        };
    }

    public createUpload(
        request: BulkRequest<FilesCreateUploadRequestItem>
    ): APICallParameters<BulkRequest<FilesCreateUploadRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/upload",
            parameters: request,
            payload: request
        };
    }

    public createDownload(
        request: BulkRequest<FilesCreateDownloadRequestItem>
    ): APICallParameters<BulkRequest<FilesCreateDownloadRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/download",
            parameters: request,
            payload: request
        };
    }

    public createFolder(
        request: BulkRequest<FilesCreateFolderRequestItem>
    ): APICallParameters<BulkRequest<FilesCreateFolderRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/folder",
            parameters: request,
            payload: request
        };
    }

    public trash(
        request: BulkRequest<FilesTrashRequestItem>
    ): APICallParameters<BulkRequest<FilesTrashRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/trash",
            parameters: request,
            payload: request
        };
    }

    public emptyTrash(
        request: BulkRequest<FilesEmptyTrashRequestItem>
    ): APICallParameters<BulkRequest<FilesEmptyTrashRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/emptyTrash",
            parameters: request,
            payload: request
        };
    }

    fileSelectorModalStyle = largeModalStyle;
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

function SensitivityDialog({file, invokeCommand, reload}: {file: UFile; invokeCommand: InvokeCommand; reload: () => void;}): JSX.Element {
    const originalSensitivity = useSensitivity(file) ?? "INHERIT";
    const selection = React.useRef<HTMLSelectElement>(null);
    const reason = React.useRef<HTMLTextAreaElement>(null);

    const onUpdate = React.useCallback(async (e: React.SyntheticEvent) => {
        e.preventDefault();
        e.stopPropagation();
        try {
            const value = selection.current?.value;
            const reasonText = reason.current?.value ?? "No reason provided."
            if (!value) return;
            if (value === SensitivityLevelMap.INHERIT) {
                // Find latest that is active and remove that one. At most one will be active.
                const entryToDelete = file.status.metadata?.metadata[sensitivityTemplateId].find(
                    it => ["approved", "not_required"].includes(it.status.approval.type)
                );
                if (!entryToDelete) return;
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
            }

            reload();
            dialogStore.success();
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to update sensitivity.")
        }
    }, []);

    return (<form onSubmit={onUpdate} style={{width: "600px", height: "270px"}}>
        <Text fontSize={24} mb="12px">Change sensitivity</Text>
        <Select my="8px" selectRef={selection} defaultValue={originalSensitivity ?? SensitivityLevelMap.INHERIT}>
            {Object.keys(SensitivityLevelMap).map(it =>
                <option key={it} value={it}>{prettierString(it)}</option>
            )}
        </Select>
        <TextArea style={{marginTop: "6px", marginBottom: "6px"}} required ref={reason} width="100%" rows={4} placeholder="Reason for sensitivity change..." />
        <Spacer
            mt="12px"
            left={<Button color="red" width="180px" onClick={() => dialogStore.failure()}>Cancel</Button>}
            right={<Button color="green">Update</Button>}
        />
    </form>);
}

function downloadFile(url: string) {
    const element = document.createElement("a");
    element.setAttribute("href", url);
    element.style.display = "none";
    element.download = url;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
}

async function addFileSensitivityDialog(file: UFile, invokeCommand: InvokeCommand, reload: () => void): Promise<void> {
    if (file.permissions.myself?.some(perm => perm === "ADMIN" || perm === "EDIT") != true) {
        return;
    }

    // Note(Jonas): It should be initialized at this point, but let's make sure.
    if (!sensitivityTemplateId) {
        sensitivityTemplateId = await findTemplateId(file, FileSensitivityNamespace, FileSensitivityVersion);
    }

    dialogStore.addDialog(<SensitivityDialog file={file} invokeCommand={invokeCommand} reload={reload} />, () => undefined, true);
}

const api = new FilesApi();

export {api};
