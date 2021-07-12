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
import {accounting, BulkRequest, file} from "UCloud/index";
import FileMetadataHistory = file.orchestrator.FileMetadataHistory;
import {FileCollectionSupport} from "UCloud/FileCollectionsApi";
import ProductNS = accounting.ProductNS;
import {SidebarPages} from "ui-components/Sidebar";
import {FtIcon} from "ui-components";
import * as React from "react";
import {fileName} from "Utilities/FileUtilities";
import * as H from "history";
import {buildQueryString} from "Utilities/URIUtilities";
import {doNothing, extensionFromPath, removeTrailingSlash} from "UtilityFunctions";
import {Operation} from "ui-components/Operation";
import {UploadProtocol, WriteConflictPolicy} from "Files/Upload";
import FilesCreateDownloadRequestItem = file.orchestrator.FilesCreateDownloadRequestItem;
import {bulkRequestOf} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import Files, {FilesBrowse} from "Files/Files";
import {ResourceProperties} from "Resource/Properties";

export interface UFile extends Resource<ResourceUpdate, UFileStatus, UFileSpecification> {

}

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

interface FilesTrashRequestItem {
    id: string;
}

class FilesApi extends ResourceApi<UFile, ProductNS.Storage, UFileSpecification,
    ResourceUpdate, UFileIncludeFlags, UFileStatus, FileCollectionSupport> {
    routingNamespace = "files";
    title = "File";
    page = SidebarPages.Files;

    idIsUriEncoded = true;

    InlineTitleRenderer = ({resource}) => <>{fileName((resource as UFile).id)}</>;
    TitleRenderer = this.InlineTitleRenderer;
    IconRenderer = (props: { resource: UFile | null, size: string }) => {
        if (!props.resource) {
            return <FtIcon fileIcon={{type: "DIRECTORY"}} size={props.size}/>;
        }
        return <FtIcon
            iconHint={props.resource.status.icon}
            fileIcon={{type: props.resource.status.type, ext: extensionFromPath(fileName(props.resource.id))}}
            size={props.size}
        />
    };
    Properties = (props) => {
        return <ResourceProperties
            {...props} api={this}
            showMessages={false} showPermissions={false}
        />;
    };

    constructor() {
        super("files");
    }

    retrieveOperations(): Operation<UFile, ResourceBrowseCallbacks<UFile>>[] {
        const base = super.retrieveOperations()
            .filter(it => it.tag !== CREATE_TAG && it.tag !== PERMISSIONS_TAG && it.tag !== DELETE_TAG);
        const ourOps: Operation<UFile, ResourceBrowseCallbacks<UFile>>[] = [
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
                icon: "rename",
                text: "Rename",
                enabled: (selected, cb) =>
                    cb.startRenaming !== undefined &&
                    selected.length === 1 &&
                    selected.every(it => it.permissions.myself.some(p => p === "EDIT" || p === "ADMIN")),
                onClick: (selected, cb) => {
                    console.log(cb.startRenaming, selected[0]);
                    cb.startRenaming?.(selected[0], fileName(selected[0].id));
                }
            },
            {
                icon: "download",
                text: "Download",
                enabled: (selected, cb) =>
                    selected.length === 1 &&
                    selected.every(it => it.permissions.myself.some(p => p === "READ" || p === "ADMIN")) &&
                    selected.every(it => it.status.type === "FILE"),
                onClick: (selected, cb) => {

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
                        <FilesBrowse embedded={true} pathRef={pathRef} onSelect={async (res) => {
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
                        }}/>,
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
                        }}/>,
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

    copy(request: BulkRequest<FilesCopyRequestItem>): APICallParameters<BulkRequest<FilesCopyRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/copy",
            parameters: request,
            payload: request
        };
    }

    move(request: BulkRequest<FilesMoveRequestItem>): APICallParameters<BulkRequest<FilesMoveRequestItem>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "/move",
            parameters: request,
            payload: request
        };
    }

    createUpload(
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

    createDownload(
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

    createFolder(
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

    trash(
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
