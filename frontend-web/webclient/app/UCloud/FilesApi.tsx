import {
    CREATE_TAG, DELETE_TAG, PERMISSIONS_TAG,
    Resource,
    ResourceApi, ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {FileIconHint, FileType} from "Files";
import {BulkRequest, BulkResponse} from "UCloud/index";
import {FileCollection, FileCollectionSupport} from "UCloud/FileCollectionsApi";
import {SidebarPages} from "ui-components/Sidebar";
import {Box, Button, FtIcon, Link} from "ui-components";
import * as React from "react";
import {fileName, getParentPath, readableUnixMode, sizeToString} from "Utilities/FileUtilities";
import {doNothing, extensionFromPath, removeTrailingSlash} from "UtilityFunctions";
import {Operation} from "ui-components/Operation";
import {UploadProtocol, WriteConflictPolicy} from "Files/Upload";
import {bulkRequestOf} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {FilesBrowse} from "Files/Files";
import {ResourceProperties} from "Resource/Properties";
import {ItemRenderer} from "ui-components/Browse";
import HighlightedCard from "ui-components/HighlightedCard";
import {MetadataBrowse} from "Files/Metadata/Documents/Browse";
import {FileMetadataHistory} from "UCloud/MetadataDocumentApi";
import {FileFavoriteToggle} from "Files/FavoriteToggle";
import {PrettyFilePath} from "Files/FilePath";
import {dateToString} from "Utilities/DateUtilities";
import {buildQueryString} from "Utilities/URIUtilities";
import {OpenWith} from "Applications/OpenWith";
import {FilePreview} from "Files/Preview";
import {ProductStorage} from "Accounting";

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

interface ExtraCallbacks {
    collection?: FileCollection;
}

class FilesApi extends ResourceApi<UFile, ProductStorage, UFileSpecification,
    ResourceUpdate, UFileIncludeFlags, UFileStatus, FileCollectionSupport> {
    constructor() {
        super("files");
        this.sortEntries = [];
        this.sortEntries.push({
            column: "PATH",
            icon: "id",
            title: "Filename",
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
    }

    routingNamespace = "files";
    title = "File";
    page = SidebarPages.Files;

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
                enabled: (selected, cb) => selected.length === 0 && cb.onSelect !== undefined,
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
                enabled: (selected, cb) => selected.length === 0 && cb.onSelect === undefined,
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
                enabled: (selected) => selected.length === 1,
                onClick: (selected, cb) => {
                    cb.startRenaming?.(selected[0], fileName(selected[0].id));
                }
            },
            {
                text: "Download",
                icon: "download",
                enabled: (selected, cb) => selected.length === 1 && selected[0].status.type === "FILE",
                onClick: async (selected, cb) => {
                    // TODO(Dan): We should probably add a feature flag for file types
                    const result = await cb.invokeCommand<BulkResponse<FilesCreateDownloadResponseItem>>(
                        this.createDownload(bulkRequestOf(
                            ...selected.map(it => ({id: it.id})),
                        ))
                    );

                    const endpoint = result?.responses[0];
                    if (endpoint) {
                        window.location.href = endpoint.endpoint;
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
                    const pathRef = {current: ""};
                    dialogStore.addDialog(
                        <FilesBrowse embedded pathRef={pathRef} onSelect={async res => {
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
                enabled: (selected, cb) =>
                    cb.embedded !== true &&
                    selected.length > 0 &&
                    selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN")),
                onClick: (selected, cb) => {
                    const pathRef = {current: ""};
                    dialogStore.addDialog(
                        <FilesBrowse embedded={true} pathRef={pathRef} onSelect={async (res) => {
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

                            dialogStore.success();
                        }} />,
                        doNothing,
                        true,
                        this.fileSelectorModalStyle
                    );
                }
            },
            {
                icon: "trash",
                text: "Move to trash",
                confirm: true,
                color: "red",
                enabled: (selected, cb) =>
                    selected.length > 0 &&
                    selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN")),
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.trash(bulkRequestOf(...selected.map(it => ({id: it.id}))))
                    );
                    cb.reload();
                }
            }
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

    private fileSelectorModalStyle = {
        content: {
            borderRadius: "6px",
            top: "80px",
            left: "25%",
            right: "25%",
            background: ""
        }
    };
}

export default new FilesApi();
