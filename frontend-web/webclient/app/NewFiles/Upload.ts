import {FileUploadEvent} from "NewFiles/HTML5FileSelector";
import * as UCloud from "UCloud";
import {GetElementType, PropType} from "UtilityFunctions";
import FileApi = UCloud.file.orchestrator;

export type WriteConflictPolicy = NonNullable<PropType<FileApi.FilesCreateUploadRequestItem, "conflictPolicy">>;
export type UploadProtocol = NonNullable<GetElementType<PropType<FileApi.FilesCreateUploadRequestItem, "supportedProtocols">>>;

export enum UploadState {
    NOT_STARTED,
    PENDING,
    UPLOADING
}

export interface Upload {
    row: FileUploadEvent;
    state: UploadState;
    fileSizeInBytes?: number;
    progressInBytes: number;
    error?: string;
    targetPath: string;
    conflictPolicy: WriteConflictPolicy;
    protocol?: UploadProtocol;
}

export const supportedUploadProtocols: UploadProtocol[] = ["CHUNKED"];
