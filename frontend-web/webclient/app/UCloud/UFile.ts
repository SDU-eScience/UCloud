import {UploadProtocol, WriteConflictPolicy} from "@/Files/Upload";
import {Resource, ResourceIncludeFlags, ResourceSpecification, ResourceStatus, ResourceUpdate} from "./ResourceApi";
import {FileIconHint, FileType} from "@/Files";
import {FileMetadataHistory} from "./MetadataDocumentApi";

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
    allowUnsupportedInclude?: boolean;
    path?: string;
}

export interface FilesMoveRequestItem {
    oldId: string;
    newId: string;
    conflictPolicy: WriteConflictPolicy;
}

export type FilesCopyRequestItem = FilesMoveRequestItem;

export interface FilesCreateFolderRequestItem {
    id: string;
    conflictPolicy: WriteConflictPolicy;
}

export interface FilesCreateUploadRequestItem {
    id: string;
    type: ("FILE"|"FOLDER");
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

export interface FilesTrashRequestItem {
    id: string;
}

export interface FilesEmptyTrashRequestItem {
    id: string;
}