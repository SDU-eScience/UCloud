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
    name: string;
    row?: PackagedFile;
    fileFetcher?: () => Promise<PackagedFile[] | null>;
    folderName?: string;
    state: UploadState;
    fileSizeInBytes?: number;
    filesCompleted: number;
    filesDiscovered: number;
    initialProgress: number;
    progressInBytes: number;
    error?: string;
    targetPath: string;
    conflictPolicy: WriteConflictPolicy;
    uploadResponse?: FileApi.FilesCreateUploadResponseItem;
    terminationRequested?: true;
    paused?: true;
    resume?: () => Promise<void>;
    uploadEvents: {timestamp: number, filesCompleted: number, progressInBytes: number}[];
}

export function uploadTrackProgress(upload: Upload): void {
    const now = timestampUnixMs();
    upload.uploadEvents = upload.uploadEvents.filter(evt => now - evt.timestamp < 10_000);
    upload.uploadEvents.push({timestamp: now, filesCompleted: upload.filesCompleted, progressInBytes: upload.progressInBytes});
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

export const UploadConfig = {
    MAX_CONCURRENT_UPLOADS: 5,
    MAX_CONCURRENT_UPLOADS_IN_FOLDER: 256,
    maxChunkSize: 16 * 1000 * 1000,
    UPLOAD_EXPIRATION_MILLIS: 2 * 24 * 3600 * 1000,
    MAX_WS_BUFFER: 1024 * 1024 * 16 * 4,
}

export const uploadStore = new class extends ExternalStoreBase {
    private uploads: Upload[] = [];

    public setUploads(uploads: Upload[]) {
        this.uploads = uploads;
        this.emitChange();
    }

    public stopUploads(batch: Upload[]): void {
        for (const upload of batch) {
            // (??? is this a TODO or comment?)
            // Find possible entries in resumables
            upload.terminationRequested = true;
        }
        this.emitChange();
    }

    public pauseUploads(batch: Upload[]): void {
        for (const upload of batch) {
            upload.terminationRequested = true;
            upload.paused = true;
            upload.state = UploadState.PENDING;
        }
        this.emitChange();
    }

    public resumeUploads(batch: Upload[], setLookForNewUploads: (l: boolean) => void): void {
        batch.forEach(async it => {
            it.terminationRequested = undefined;
            it.paused = undefined;
            it.state = UploadState.UPLOADING;
            it.resume?.().then(() => {
                it.state = UploadState.DONE;
                setLookForNewUploads(true);
            }).catch(e => {
                if (typeof e === "string") {
                    it.error = e;
                    it.state = UploadState.DONE;
                }
            });
        });
        this.emitChange();
    }

    public clearUploads(batch: Upload[], setPausedFilesInFolder: React.Dispatch<React.SetStateAction<string[]>>): void {
        /* Note(Jonas): This is intended as pointer equality. Does this make sense in a Javascript context? */
        /* Note(Jonas): Yes. */
        this.uploads = this.uploads.filter(u => !batch.some(b => b === u)); // Note(Jonas): iterates through uploads and omits the ones in the list in the arguments
        // Note(Jonas): Find possible entries in paused uploads and remove it. 
        setPausedFilesInFolder(entries => {
            let cpy = [...entries];
            for (const upload of batch) {
                cpy = cpy.filter(it => it !== upload.targetPath + "/" + upload.name);
            }
            return cpy;
        });
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
