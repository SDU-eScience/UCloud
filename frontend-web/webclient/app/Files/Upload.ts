import * as UCloud from "@/UCloud";
import {GetElementType, PropType, timestampUnixMs} from "@/UtilityFunctions";
import FileApi = UCloud.file.orchestrator;
import {useSyncExternalStore} from "react";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import {PackagedFile} from "./HTML5FileSelector";

export type WriteConflictPolicy = NonNullable<PropType<FileApi.FilesCreateUploadRequestItem, "conflictPolicy">>;
export type UploadProtocol = NonNullable<GetElementType<PropType<FileApi.FilesCreateUploadRequestItem, "supportedProtocols">>>;

export enum UploadState {
    PENDING,
    UPLOADING,
    DONE
}

export interface Upload {
    row: PackagedFile[];
    folderName?: string;
    state: UploadState;
    fileSizeInBytes?: number;
    currentFileId: number;
    initialProgress: number;
    progressInBytes: number;
    error?: string;
    targetPath: string;
    conflictPolicy: WriteConflictPolicy;
    uploadResponse?: FileApi.FilesCreateUploadResponseItem;
    terminationRequested?: true;
    paused?: true;
    resume?: () => Promise<void>;
    uploadEvents: {timestamp: number, currentFileId: number, progressInBytes: number}[];
}

export function uploadTrackProgress(upload: Upload): void {
    const now = timestampUnixMs();
    upload.uploadEvents = upload.uploadEvents.filter(evt => now - evt.timestamp < 10_000);
    upload.uploadEvents.push({timestamp: now, currentFileId: upload.currentFileId, progressInBytes: upload.progressInBytes});
}

export function uploadCalculateSpeed(upload: Upload): number {
    if (upload.uploadEvents.length === 0) return 0;

    const min = upload.uploadEvents[0];
    const max = upload.uploadEvents[upload.uploadEvents.length - 1];

    const timespan = max.timestamp - min.timestamp;
    const bytesTransferred = max.progressInBytes - min.progressInBytes;

    if (timespan === 0) return 0;
    return (bytesTransferred / timespan) * 1000;
}

const uploadStore = new class extends ExternalStoreBase {
    private uploads: Upload[] = [];

    public setUploads(uploads: Upload[]) {
        this.uploads = uploads;
        this.emitChange();
    }

    public getSnapshot() {
        return this.uploads;
    }
}

export function useUploads(): [Upload[], (u: Upload[]) => void] {
    const uploads = useSyncExternalStore(s => uploadStore.subscribe(s), () => uploadStore.getSnapshot());
    return [uploads, u => uploadStore.setUploads(u)];
}

export const supportedProtocols: UploadProtocol[] = ["CHUNKED", "WEBSOCKET"];
