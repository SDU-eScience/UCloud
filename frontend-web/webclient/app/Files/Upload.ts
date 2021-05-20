import {FileUploadEvent} from "Files/HTML5FileSelector";
import * as UCloud from "UCloud";
import {GetElementType, PropType} from "UtilityFunctions";
import FileApi = UCloud.file.orchestrator;

export type WriteConflictPolicy = NonNullable<PropType<FileApi.FilesCreateUploadRequestItem, "conflictPolicy">>;
export type UploadProtocol = NonNullable<GetElementType<PropType<FileApi.FilesCreateUploadRequestItem, "supportedProtocols">>>;

export enum UploadState {
    PENDING,
    UPLOADING,
    DONE
}

export interface Upload {
    row: FileUploadEvent;
    state: UploadState;
    fileSizeInBytes?: number;
    initialProgress: number;
    progressInBytes: number;
    error?: string;
    targetPath: string;
    conflictPolicy: WriteConflictPolicy;
    uploadResponse?: FileApi.FilesCreateUploadResponseItem;
    terminationRequested?: true;
}

export const supportedProtocols: UploadProtocol[] = ["CHUNKED"];
