import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {default as ReactModal} from "react-modal";
import {
    Box,
    Flex,
    FtIcon,
    Icon,
    Truncate,
    Text,
    Button,
} from "@/ui-components";
import {TextSpan} from "@/ui-components/Text";
import {
    delay,
    errorMessageOrDefault,
    extensionFromPath,
    inSuccessRange,
    preventDefault
} from "@/UtilityFunctions";
import {PackagedFile, filesFromDropOrSelectEvent} from "@/Files/HTML5FileSelector";
import {
    supportedProtocols,
    Upload,
    uploadCalculateSpeed,
    UploadState,
    uploadStore,
    uploadTrackProgress,
    useUploads
} from "@/Files/Upload";
import {api as FilesApi, WriteToFileEventKey, WriteToFileEventProps} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import {BulkResponse} from "@/UCloud";
import {fileName, getParentPath, sizeToString} from "@/Utilities/FileUtilities";
import {
    ChunkedFileReader,
    createLocalStorageFolderUploadKey,
    createLocalStorageUploadKey,
    UPLOAD_LOCALSTORAGE_PREFIX
} from "@/Files/ChunkedFileReader";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {Client} from "@/Authentication/HttpClientInstance";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import {TextClass} from "@/ui-components/Text";
import {formatDistance} from "date-fns";
import {removeUploadFromStorage} from "@/Files/ChunkedFileReader";
import * as Heading from "@/ui-components/Heading";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import {FilesCreateUploadRequestItem, FilesCreateUploadResponseItem} from "@/UCloud/UFile";
import {TooltipV2} from "@/ui-components/Tooltip";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {UploadConfig} from "@/Files/Upload";
import {ProgressCircle} from "@/Services/BackgroundTasks/BackgroundTask";
import {getStartOfDay} from "@/Utilities/DateUtilities";

interface LocalStorageFileUploadInfo {
    offset: number;
    size: number;
    strategy: FilesCreateUploadResponseItem;
    expiration: number;
}

function toPackagedFile(path: string, content: string): PackagedFile {
    const file = new File([content], path);
    return {
        fileObject: file,
        fullPath: fileName(path),
        name: fileName(path),
        webkitRelativePath: file.name,
        size: file.size,
        type: "FILE",
        isDirectory: false,
        lastModified: new Date().getTime(),
        lastModifiedDate: getStartOfDay(new Date()),
    };
}

function fetchValidUploadFromLocalStorage(path: string): LocalStorageFileUploadInfo | null {
    const item = localStorage.getItem(createLocalStorageUploadKey(path));
    if (item === null) return null;

    const parsed = JSON.parse(item) as LocalStorageFileUploadInfo;
    if (parsed.expiration < new Date().getTime()) return null;

    return parsed;
}

async function processUpload(upload: Upload) {
    const strategy = upload.uploadResponse;
    if (!strategy) {
        upload.error = "Internal client error";
        upload.state = UploadState.DONE;
        return;
    }

    if (strategy.protocol !== "CHUNKED" && strategy.protocol !== "WEBSOCKET" && strategy.protocol !== "WEBSOCKET_V1" && strategy.protocol !== "WEBSOCKET_V2") {
        upload.error = "Upload not supported for this provider";
        upload.state = UploadState.DONE;
        return;
    }

    if (strategy.protocol === "WEBSOCKET") {
        if (upload.folderName) {
            strategy.protocol = "WEBSOCKET_V2";
        } else {
            strategy.protocol = "WEBSOCKET_V1";
        }
    }

    if (strategy.protocol === "WEBSOCKET_V1" || strategy.protocol === "CHUNKED") {
        const theFile = upload.row!;
        const fullFilePath = (upload.targetPath + (theFile.fullPath.startsWith("/") ? "" : "/") + theFile.fullPath);

        const reader = new ChunkedFileReader(theFile.fileObject);

        const uploadInfo = fetchValidUploadFromLocalStorage(fullFilePath);
        if (uploadInfo !== null) reader.offset = uploadInfo.offset;

        upload.initialProgress = reader.offset;
        upload.fileSizeInBytes = reader.fileSize();

        upload.resume = protocolHandlerChunkedAndWebSocketV1(reader, upload, strategy, fullFilePath);
        await upload.resume();
    } else if (strategy.protocol === "WEBSOCKET_V2") {
        upload.initialProgress = 0;
        upload.resume = protocolHandlerWebSocketV2(upload, strategy, upload.folderName ?? "");
        await upload.resume();
    }
}

enum FolderUploadMessageType {
    // ONLY ADD STUFF AT THE BOTTOM
    OK,
    CHECKSUM, // DO NOT REMOVE, YOU WILL BREAK THE PROTOCOL
    CHUNK,
    SKIP,
    LISTING,
    FILES_COMPLETED,
    // ADD STUFF HERE
}

function computeFileChecksum(file: PackagedFile, upload: Upload): Promise<string> {
    const start = Date.now();
    return new Promise<string>(async (resolve) => {
        const reader = new ChunkedFileReader(file.fileObject);
        const shaSumWorkerModule = await import("@/Files/ShaSumWorker?worker");
        const shaSumWorker = new shaSumWorkerModule.default();

        shaSumWorker.onmessage = e => {
            resolve(e.data);
        }

        shaSumWorker.postMessage({type: "Start"});

        while (!reader.isEof() && !upload.terminationRequested) {
            const chunk = await reader.readChunk(UploadConfig.maxChunkSize);
            shaSumWorker.postMessage({type: "Update", data: chunk});
        }

        shaSumWorker.postMessage({type: "End"});
    });
}

function protocolHandlerWebSocketV2(
    upload: Upload,
    strategy: FilesCreateUploadResponseItem,
    folderPath: string
) {
    const files: Map<number, PackagedFile> = new Map();
    const filesTracked: Map<number, boolean> = new Map();
    let totalSize = 0;
    let dataSent = 0;

    let fileIndex = 0;
    let readQueue: [PackagedFile, number][] = [];
    let uploadSocket: WebSocket;
    let startedUploads = 0;

    return async () => {
        uploadSocket = new WebSocket(
            strategy!.endpoint.replace("integration-module:8889", "localhost:9000")
                .replace("http://", "ws://").replace("https://", "wss://")
        );

        uploadSocket.binaryType = "arraybuffer";

        let didInit = false;
        await new Promise((resolve) => {
            async function loop() {
                if (uploadSocket.readyState === WebSocket.CLOSED) {
                    return;
                }

                if (uploadSocket.readyState !== WebSocket.OPEN) {
                    window.setTimeout(loop, 50);
                    return;
                }

                if (!didInit) {
                    didInit = true;
                    for (let i = 0; i < UploadConfig.MAX_CONCURRENT_UPLOADS_IN_FOLDER; i++) {
                        startLoop(i);
                    }
                }

                if (upload.filesCompleted >= upload.filesDiscovered) {
                    await sendDirectoryListing();
                }

                upload.fileSizeInBytes = totalSize;
                upload.progressInBytes = dataSent - uploadSocket.bufferedAmount;
                uploadTrackProgress(upload);

                window.setTimeout(loop, 50);
            }
            window.setTimeout(loop, 50);

            async function sendDirectoryListing() {
                const fileList = await upload.fileFetcher!();
                if (fileList == null) {
                    resolve(true);
                    upload.state = UploadState.DONE;
                    uploadSocket.close();
                    return;
                }

                const newEntries: number[] = [];
                for (const file of fileList) {
                    files.set(fileIndex, file);
                    newEntries.push(fileIndex);
                    fileIndex++;
                    upload.filesDiscovered++;
                }

                const capacity = 1024 * 64;
                const rawBuffer = new ArrayBuffer(capacity);
                const buffer = new DataView(rawBuffer);
                let bufferCursor = 0;
                const encoder = new TextEncoder();

                function putU32(value: number) {
                    buffer.setInt32(bufferCursor, value, false);
                    bufferCursor += 4;
                }

                function putU64(value: number) {
                    buffer.setBigInt64(bufferCursor, BigInt(value), false);
                    bufferCursor += 8;
                }

                function putString(data: string) {
                    bufferCursor += 4;
                    const {written} = encoder.encodeInto(data, new Uint8Array(rawBuffer, bufferCursor))
                    buffer.setInt32(bufferCursor - 4, written, false);
                    bufferCursor += written;
                }

                function flush() {
                    if (bufferCursor > 1) {
                        const view = new Uint8Array(rawBuffer, 0, bufferCursor);
                        uploadSocket.send(view);
                    }
                    buffer.setInt8(0, FolderUploadMessageType.LISTING);
                    bufferCursor = 1;
                }

                function remainingCapacity() {
                    return capacity - bufferCursor;
                }

                flush(); // Prepare the buffer

                for (const id of newEntries) {
                    const f = files.get(id)!;
                    if (remainingCapacity() < 1024 * 4) flush();

                    putU32(id);
                    putU64(f.size);
                    putU64(f.lastModified);
                    putString(f.fullPath.split("/").slice(2).join("/"));
                    totalSize += f.size;
                    filesTracked.set(id, true);
                }
                flush();
            }

            uploadSocket.addEventListener("message", async (message) => {
                const frame = new DataView(message.data as ArrayBuffer);
                let frameCursor = 0;

                function getU8(): number {
                    const result = frame.getUint8(frameCursor);
                    frameCursor += 1;
                    return result;
                }

                function getU32(): number {
                    const result = frame.getUint32(frameCursor);
                    frameCursor += 4;
                    return result;
                }

                function frameRemaining(): number {
                    return frame.byteLength - frameCursor;
                }

                while (frameRemaining() > 0) {
                    const messageType = getU8();

                    switch (messageType as FolderUploadMessageType) {
                        case FolderUploadMessageType.OK: {
                            const fileId = getU32();
                            const theFile = files.get(fileId);
                            if (theFile) {
                                readQueue.push([theFile, fileId]);
                            }
                            break;
                        }

                        case FolderUploadMessageType.FILES_COMPLETED: {
                            upload.filesCompleted = getU32();
                            break;
                        }

                        case FolderUploadMessageType.SKIP: {
                            // Skip this file, since existing version of file appears to be identical
                            const fileId = getU32();
                            dataSent += files.get(fileId)?.size ?? 0;
                            break;
                        }
                    }
                }
            });

            uploadSocket.addEventListener("close", async (event) => {
                if (!upload.paused) {
                    localStorage.removeItem(createLocalStorageFolderUploadKey(folderPath))
                }
                resolve(true);
            });
        });
    }

    async function startLoop(id: number) {
        while (!upload.terminationRequested && uploadSocket.readyState === WebSocket.OPEN) {
            if (startedUploads - upload.filesCompleted >= UploadConfig.MAX_CONCURRENT_UPLOADS_IN_FOLDER) {
                await delay(1);
                continue;
            }

            const entry = readQueue.pop();
            if (!entry) {
                await delay(1);
                continue;
            }

            const [theFile, fileId] = entry;

            try {
                awaiting[theFile.fullPath] = 0;
                const reader = new ChunkedFileReader(theFile.fileObject);
                delete awaiting[theFile.fullPath];
                startedUploads++;

                if (theFile.size === 0) {
                    const meta = constructMessageMeta(FolderUploadMessageType.CHUNK, fileId);
                    const message = concatArrayBuffers(meta, new ArrayBuffer(0));
                    sent[theFile.fullPath] = 0;
                    await sendWsChunk(uploadSocket, message)
                } else {
                    while (!reader.isEof() && !upload.terminationRequested) {
                        const [message, chunkSize] = await constructUploadChunk(reader, fileId);
                        await sendWsChunk(uploadSocket, message);
                        sent[theFile.fullPath] = (sent[theFile.fullPath] ?? 0) + chunkSize;

                        dataSent += chunkSize;
                    }
                }
            } catch (e) {
                snackbarStore.addFailure(`Error occurred while uploading file ${theFile.fullPath}. Content might be corrupt or missing.`, false);
                const rawBuffer = new ArrayBuffer(5);
                const buffer = new DataView(rawBuffer);
                buffer.setInt8(0, FolderUploadMessageType.SKIP);
                buffer.setInt32(1, fileId, false);
                const view = new Uint8Array(rawBuffer, 0, 5);
                uploadSocket.send(view);
            }

            if (upload.terminationRequested) {
                if (!upload.paused) {
                    if (uploadSocket.readyState === WebSocket.OPEN) {
                        uploadSocket.close();
                    }
                }
            }
        }
    }

    function constructMessageMeta(type: FolderUploadMessageType, fileId: number): ArrayBuffer {
        const buf = new ArrayBuffer(5);
        const view = new DataView(buf);
        view.setUint8(0, type);
        view.setUint32(1, fileId, false);
        return buf;
    }

    async function constructUploadChunk(
        reader: ChunkedFileReader,
        fileId: number
    ): Promise<[ArrayBuffer, number]> {
        const chunk = await reader.readChunk(UploadConfig.maxChunkSize);
        const meta = constructMessageMeta(FolderUploadMessageType.CHUNK, fileId);
        return [concatArrayBuffers(meta, chunk), chunk.byteLength];
    }
}

function concatArrayBuffers(a: ArrayBuffer, b: ArrayBuffer): ArrayBuffer {
    const c = new Uint8Array(a.byteLength + b.byteLength);
    c.set(new Uint8Array(a), 0);
    c.set(new Uint8Array(b), a.byteLength);
    return c.buffer
}

const sent: Record<string, number> = {};
window["sent"] = sent;

const awaiting: Record<string, number> = {};
window["awaiting"] = awaiting;

function sendWsChunk(connection: WebSocket, chunk: ArrayBuffer): Promise<void> {
    return new Promise((resolve) => {
        _sendWsChunk(connection, chunk, resolve);
    });
}

function _sendWsChunk(connection: WebSocket, chunk: ArrayBuffer, onComplete: () => void) {
    if (connection.bufferedAmount + chunk.byteLength < UploadConfig.MAX_WS_BUFFER) {
        connection.send(chunk);
        onComplete();
    } else {
        window.setTimeout(() => {
            _sendWsChunk(connection, chunk, onComplete)
        }, 50);
    }
}


function protocolHandlerChunkedAndWebSocketV1(
    reader: ChunkedFileReader,
    upload: Upload,
    strategy: FilesCreateUploadResponseItem,
    fullFilePath: string
) {
    // NOTE(Dan): I am not sure if this is needed, but it was changed to allow file uploads through WEBSOCKET_V2. I
    // am just putting this here in case any of the code below actually requires it.
    upload.filesDiscovered = 1;

    return async () => {
        if (strategy.protocol === "CHUNKED") {
            while (!reader.isEof() && !upload.terminationRequested) {
                await sendChunk(await reader.readChunk(UploadConfig.maxChunkSize));

                const expiration = new Date().getTime() + UploadConfig.UPLOAD_EXPIRATION_MILLIS;
                localStorage.setItem(
                    createLocalStorageUploadKey(fullFilePath),
                    JSON.stringify({
                        offset: reader.offset,
                        size: upload.fileSizeInBytes,
                        strategy: strategy!,
                        expiration
                    } as LocalStorageFileUploadInfo)
                );
            }

            if (!upload.paused) {
                localStorage.removeItem(createLocalStorageUploadKey(fullFilePath));
            } else {
                upload.resume = protocolHandlerChunkedAndWebSocketV1(reader, upload, strategy, fullFilePath);
            }
        } else {
            const uploadSocket = new WebSocket(
                strategy!.endpoint.replace("integration-module:8889", "localhost:9000")
                    .replace("http://", "ws://").replace("https://", "wss://")
            );

            uploadSocket.onerror = ev => {
                if (navigator.onLine) {
                    upload.error = "An error ocurred uploading the file.";
                } else {
                    upload.error = "File upload failed due to missing internet connection."
                }
                upload.state = UploadState.DONE;
            }

            const progressStart = upload.progressInBytes;
            reader.offset = progressStart;

            await new Promise((resolve, reject) => {
                uploadSocket.addEventListener("message", async (message) => {
                    upload.progressInBytes = parseInt(message.data);
                    uploadTrackProgress(upload);

                    const expiration = new Date().getTime() + UploadConfig.UPLOAD_EXPIRATION_MILLIS;
                    localStorage.setItem(
                        createLocalStorageUploadKey(fullFilePath),
                        JSON.stringify({
                            offset: upload.progressInBytes,
                            size: upload.fileSizeInBytes,
                            strategy: strategy!,
                            expiration
                        } as LocalStorageFileUploadInfo)
                    );

                    if (parseInt(message.data) === upload.fileSizeInBytes) {
                        localStorage.removeItem(createLocalStorageUploadKey(fullFilePath));
                        upload.state = UploadState.DONE;
                        uploadSocket.close()
                        resolve(true);
                    }

                    if (upload.terminationRequested) {
                        if (!upload.paused) {
                            localStorage.removeItem(createLocalStorageUploadKey(fullFilePath));
                        }
                        uploadSocket.close();
                        resolve(true);
                    }
                });

                uploadSocket.addEventListener("open", async (event) => {
                    uploadSocket.send(`${progressStart} ${reader.fileSize().toString()}`)

                    while (!reader.isEof() && !upload.terminationRequested) {
                        const chunk = await reader.readChunk(UploadConfig.maxChunkSize);
                        await sendWsChunk(uploadSocket, chunk);
                    }
                });
            });
        }
    };

    function sendChunk(chunk: ArrayBuffer): Promise<void> {
        return new Promise(((resolve, reject) => {
            const progressStart = upload.progressInBytes;
            const request = new XMLHttpRequest();

            request.open("POST", strategy!.endpoint.replace("integration-module:8889", "localhost:9000"));
            request.setRequestHeader("Chunked-Upload-Token", strategy!.token);
            request.setRequestHeader("Chunked-Upload-Offset", (reader.offset - chunk.byteLength).toString(10));
            request.setRequestHeader("Chunked-Upload-Total-Size", reader.fileSize().toString());
            request.setRequestHeader("UCloud-Username", b64EncodeUnicode(Client.username!));
            request.responseType = "text";

            request.upload.onprogress = ev => {
                upload.progressInBytes = progressStart + ev.loaded;
                uploadTrackProgress(upload);
                if (upload.terminationRequested) {
                    upload.state = UploadState.DONE;
                    if (!upload.paused) request.abort();
                }
            };

            request.onreadystatechange = () => {
                if (request.status === 0) return;
                if (inSuccessRange(request.status)) resolve();

                reject(errorMessageOrDefault({request, response: request.response}, "Upload failed"));
            };

            request.send(chunk);
        }));
    }
}

function findResumableUploadsFromUploadPath(uploadPath: string): string[] {
    return Object.keys(localStorage).filter(key => key.startsWith(UPLOAD_LOCALSTORAGE_PREFIX)).map(key =>
        key.replace(`${UPLOAD_LOCALSTORAGE_PREFIX}:`, "")
    ).filter(key => key.replace(`/${fileName(key)}`, "") === uploadPath);
}

async function startUploads(batch: Upload[], setLookForNewUploads: (b: boolean) => void) {
    const activeUploads =
        uploadStore.getSnapshot().reduce((acc, u) => u.state === UploadState.UPLOADING ? acc + 1 : acc, 0);
    const maxUploadsToRemaining = UploadConfig.MAX_CONCURRENT_UPLOADS - activeUploads;
    if (maxUploadsToRemaining > 0) {
        const creationRequests: FilesCreateUploadRequestItem[] = [];
        const actualUploads: Upload[] = [];

        for (const upload of batch) {
            if (upload.state !== UploadState.PENDING) continue;
            if (creationRequests.length >= maxUploadsToRemaining) break;

            const uploadType = upload.folderName ? "FOLDER" : "FILE";
            const fullFilePath = uploadType === "FOLDER" && upload.folderName ?
                upload.targetPath + "/" + upload.folderName :
                upload.targetPath + "/" + upload.name;

            upload.state = UploadState.UPLOADING;
            creationRequests.push({
                supportedProtocols,
                type: uploadType,
                conflictPolicy: upload.conflictPolicy,
                id: fullFilePath,
            });

            actualUploads.push(upload);
        }

        if (actualUploads.length === 0) return;

        try {
            if (creationRequests.length > 0) {
                const responses = (await callAPI<BulkResponse<FilesCreateUploadResponseItem>>(
                    FilesApi.createUpload(bulkRequestOf(...creationRequests))
                )).responses;

                for (const [index, response] of responses.entries()) {
                    const upload = actualUploads[index];
                    upload.uploadResponse = response;
                }
            }

            for (const upload of actualUploads) {
                processUpload(upload)
                    .then(() => {
                        upload.state = UploadState.DONE;
                        setLookForNewUploads(true);
                    })
                    .catch(e => {
                        if (typeof e === "string") {
                            upload.error = e;
                            upload.state = UploadState.DONE;
                        }
                    });
            }
        } catch (e) {
            const errorMessage = errorMessageOrDefault(e, "Unable to start upload");
            for (let i = 0; i < creationRequests.length; i++) {
                actualUploads[i].state = UploadState.DONE;
                actualUploads[i].error = errorMessage;
            }
            return;
        }
    }
}

const Uploader: React.FunctionComponent = () => {
    const [uploadPath] = useGlobal("uploadPath", "/");
    const [uploaderVisible, setUploaderVisible] = useGlobal("uploaderVisible", false);
    const [uploads, setUploads] = useUploads();
    const [lookForNewUploads, setLookForNewUploads] = useState(false);

    const refresh = useRefresh();

    const closeModal = useCallback(() => {
        setUploaderVisible(false);
    }, []);

    const callbacks: UploadCallback = useMemo(() => ({
        startUploads: b => startUploads(b, setLookForNewUploads),
        stopUploads: b => uploadStore.stopUploads(b),
        pauseUploads: b => uploadStore.pauseUploads(b),
        resumeUploads: b => uploadStore.resumeUploads(b, setLookForNewUploads),
        clearUploads: b => uploadStore.clearUploads(b, setPausedFilesInFolder),
    }), [startUploads]);

    const onSelectedFile = useCallback(async (e: {stopPropagation(): void; preventDefault(): void}, isResuming = false) => {
        e.preventDefault();
        e.stopPropagation();

        const allUploads: Upload[] = uploads;
        const events = await filesFromDropOrSelectEvent(e);
        for (const u of events) {
            switch (u.type) {
                case "single": {
                    const theFile = await u.file;
                    let didFetch = false;
                    allUploads.push({
                        name: theFile.name,
                        row: theFile,
                        progressInBytes: 0,
                        filesCompleted: 0,
                        filesDiscovered: 0,
                        state: UploadState.PENDING,
                        conflictPolicy: "RENAME",
                        targetPath: uploadPath,
                        fileFetcher: () => {
                            if (!didFetch) {
                                didFetch = true;
                                return Promise.resolve([theFile]);
                            } else {
                                return Promise.resolve(null);
                            }
                        },
                        initialProgress: 0,
                        uploadEvents: []
                    });
                    break;
                }

                case "folder": {
                    allUploads.push({
                        name: u.folderName,
                        folderName: u.folderName,
                        fileFetcher: u.fileFetcher,
                        progressInBytes: 0,
                        filesCompleted: 0,
                        filesDiscovered: 0,
                        state: UploadState.PENDING,
                        conflictPolicy: "RENAME",
                        targetPath: uploadPath,
                        initialProgress: 0,
                        uploadEvents: []
                    });
                    break;
                }
            }

            if (isResuming) {
                const upload = allUploads.at(-1);
                if (upload) {
                    const path = uploadPath + "/" + upload.name;
                    const item = fetchValidUploadFromLocalStorage(path);
                    if (item !== null) {
                        upload.uploadResponse = item.strategy;
                        upload.progressInBytes = item.offset;
                        upload.fileSizeInBytes = item.size;
                        continue;
                    }
                } else {
                    console.warn("Failed to find file");
                }
            }
        }

        setUploads(allUploads);
        startUploads(allUploads, setLookForNewUploads);
    }, [uploads]);

    const stopGapMethodForUploadingFilesFromTheEditor = React.useCallback((e: CustomEvent<WriteToFileEventProps>) => {
        e.stopImmediatePropagation();
        e.stopPropagation();
        e.preventDefault();
        const {path, content} = e.detail;
        const allUploads: Upload[] = uploads;
        let didFetch = false;
        const packaged = toPackagedFile(path, content);

        allUploads.push({
            name: fileName(path),
            row: packaged,
            progressInBytes: 0,
            filesCompleted: 0,
            filesDiscovered: 0,
            state: UploadState.PENDING,
            conflictPolicy: "REPLACE",
            targetPath: getParentPath(path),
            fileFetcher: async () => {
                if (didFetch) return null;
                didFetch = true;
                return [packaged];
            },
            initialProgress: 0,
            uploadEvents: []
        });

        setUploads(allUploads);
        startUploads(allUploads, setLookForNewUploads);
    }, [uploads]);

    useEffect(() => {
        const oldOnDrop = document.ondrop;
        const oldOnDragOver = document.ondragover;
        const oldOnDragEnter = document.ondragenter;
        const oldOnDragLeave = document.ondragleave;
        window.addEventListener(WriteToFileEventKey, stopGapMethodForUploadingFilesFromTheEditor);

        if (uploaderVisible) {
            document.ondrop = onSelectedFile;
            document.ondragover = preventDefault;
            document.ondragenter = preventDefault;
            document.ondragleave = preventDefault;
        }

        return () => {
            document.ondrop = oldOnDrop;
            document.ondragover = oldOnDragOver;
            document.ondragenter = oldOnDragEnter;
            document.ondragleave = oldOnDragLeave;
        };
    }, [onSelectedFile, uploaderVisible]);

    useEffect(() => {
        // Note(Jonas): This causes this entire component to re-render every 500ms.
        const interval = setInterval(() => {
            setUploads([...uploads]);
        }, 500);

        return () => {
            clearInterval(interval);
        }
    }, [uploads]);

    useEffect(() => {
        if (lookForNewUploads) {
            setLookForNewUploads(false);
            startUploads(uploads, setLookForNewUploads);
            const shouldReload = uploads.every(it => it.state === UploadState.DONE) &&
                uploads.some(it => it.targetPath === uploadPath && !it.terminationRequested);
            if (shouldReload && uploaderVisible && window.location.pathname === "/app/files") {
                refresh?.();
            } else if (shouldReload) {
                snackbarStore.addSuccess("File upload(s) finished.", true);
            }
        }
    }, [lookForNewUploads, startUploads, refresh, uploadPath, uploaderVisible]);


    const [pausedFilesInFolder, setPausedFilesInFolder] = useState<string[]>([]);

    useEffect(() => {
        const matches = findResumableUploadsFromUploadPath(uploadPath);
        setPausedFilesInFolder(matches);
    }, [uploadPath]);


    const hasUploads = uploads.length > 0;
    const uploadsInProgress = uploads.filter(it => it.state === UploadState.UPLOADING)
    const uploadTimings = getUploadTimings(uploadsInProgress);
    let uploadingText = uploadsInProgress.length === 0 ? null : (
        `Uploading at ${sizeToString(uploadTimings.uploadSpeed)}/s`
    );

    if (uploadTimings.timeRemaining !== 0) {
        uploadingText += ` - Approximately ${formatDistance(uploadTimings.timeRemaining * 1000, 0)}`;
    }

    return <ReactModal
        isOpen={uploaderVisible}
        style={modalStyle}
        shouldCloseOnEsc
        ariaHideApp={false}
        onRequestClose={closeModal}
        className={CardClass}
    >
        <div className={DropZoneWrapper} data-has-uploads={hasUploads} data-tag="uploadModal">
            <div>
                <Flex>
                    <div className={classConcat(TextClass, UploaderText)} data-has-uploads={hasUploads}>Upload files</div>
                    {uploads.length > 0 && uploads.find(upload => uploadIsTerminal(upload)) !== null ?
                        <Button mt="7px" ml="auto" onClick={() => setUploads(uploads.filter(u => !uploadIsTerminal(u)))}>Clear finished uploads</Button>
                        : null}
                </Flex>
                <Text fontSize={10} className={UploaderSpeedTextClass}>{uploadingText}</Text>
            </div>
            <div style={{
                // Note(Jonas): Modal height, row with close button, file upload text height, top and bottom padding
                maxHeight: `calc(${modalStyle.content?.maxHeight} - 24px - 37.5px - 20px - 20px)`,
                overflowY: "auto"
            }}>
                <div className="uploads" style={{width: "100%"}}>
                    {uploads.map((upload, idx) => (
                        <UploaderRow
                            key={`${upload.name}-${idx}`}
                            upload={upload}
                            callbacks={callbacks}
                        />
                    ))}
                </div>
                <Flex justifyContent="center">
                    <label style={{width: "100%", height: !hasUploads ? undefined : "70px", marginBottom: "8px"}}
                        htmlFor={"fileUploadBrowse"}>
                        <div className={DropZoneBox} onDrop={onSelectedFile} onDragEnter={preventDefault}
                            onDragLeave={preventDefault}
                            onDragOver={preventDefault} data-slim={hasUploads}>
                            <div data-has-uploads={hasUploads} className={UploadMoreClass}>
                                {hasUploads ? null :
                                    <UploaderArt />
                                }
                                <div className="upload-more-text" style={{marginTop: "22px"}}>
                                    <TextSpan mr="0.5em"><Icon hoverColor="primaryContrast"
                                        name="upload" /></TextSpan>
                                    <TextSpan mr="0.3em">Drop files or folders here or</TextSpan>
                                    <i style={{cursor: "pointer"}}>browse</i>
                                    <input
                                        id={"fileUploadBrowse"}
                                        type={"file"}
                                        style={{display: "none"}}
                                        multiple
                                        onChange={onSelectedFile}
                                    />
                                </div>
                            </div>
                        </div>
                    </label>
                </Flex>
                {pausedFilesInFolder.length === 0 ? null :
                    <div style={{
                        marginBottom: "4px",
                        marginLeft: "8px",
                        marginRight: "4px"
                    }}>
                        <Heading.h4>Resumable uploads</Heading.h4>
                        {pausedFilesInFolder.map(it => {
                            const item = fetchValidUploadFromLocalStorage(it);
                            return <TaskRow
                                key={it}
                                icon={<FtIcon
                                    fileIcon={{type: "FILE", ext: extensionFromPath(fileName(it))}}
                                    size="32px" />
                                }
                                title={fileName(it)}
                                progress={item ? uploadProgressText(item.offset, item.size) : null}
                                isPaused
                                pause={<label htmlFor="fileUploadBrowseResume">
                                    <input
                                        id={"fileUploadBrowseResume"}
                                        type={"file"}
                                        style={{display: "none"}}
                                        onChange={e => {
                                            setPausedFilesInFolder(files => files.filter(file => file !== it));
                                            onSelectedFile(e, true);
                                        }}
                                    />
                                    <Icon cursor="pointer" title="Resume upload" name="play"
                                        color="primaryMain" mr="12px" />
                                </label>}
                                removeOrCancel={<Icon cursor="pointer" title="Remove" name="close" color="errorMain" onClick={() => {
                                    setPausedFilesInFolder(files => files.filter(file => file !== it));
                                    removeUploadFromStorage(it);
                                }} />}
                                progressInfo={{stopped: true, indeterminate: true, limit: 1, progress: 0}}
                            />
                        })}
                    </div>
                }
            </div>
        </div>
    </ReactModal>;
};

function getUploadTimings(uploads: Upload[]): {
    uploadSpeed: number;
    timeRemaining: number;
} {
    let uploadSpeed = 0;
    let timeRemaining = 0;

    for (const upload of uploads) {
        const speed = uploadCalculateSpeed(upload);
        if (speed === 0) continue;
        timeRemaining += ((upload.fileSizeInBytes ?? 0) - upload.progressInBytes) / speed;
        uploadSpeed += speed;
    }

    return {uploadSpeed, timeRemaining};
}

type HandleUploadsFunction = (batch: Upload[]) => void;

export interface UploadCallback {
    startUploads: HandleUploadsFunction;
    stopUploads: HandleUploadsFunction;
    pauseUploads: HandleUploadsFunction;
    resumeUploads: HandleUploadsFunction;
    clearUploads: HandleUploadsFunction;
}

const UploaderText = injectStyle("uploader-text", k => `
    ${k} {
        margin-left: 10px;
        margin-top: 6px;
        color: var(--textPrimary);
        font-size: 25px;
    }
`);

const UploaderSpeedTextClass = injectStyle("uploader-speed-text", k => `
    ${k} {
        margin: 10px;
        color: var(--textPrimary);
    }
`);

const UploadMoreClass = injectStyle("upload-more", k => `
    ${k} {
        align-items: center;
        text-align: center;
        flex-direction: column;
    }
    
    ${k}[data-has-uploads="true"] {
        display: flex;
        height: 70px;
        width: 100%;
        align-items: center;
        border-width: 2px;
        border-color: var(--textPrimary);
        border-style: dashed;
        border-radius: 10px;
    }
`);

export const TaskRowClass = injectStyle("uploader-row", k => `
    ${k} {
        border-radius: 10px;
        height: 64px;
        width: 100%;
        margin-top: 12px;
        margin-bottom: 12px;
    }
    
    ${k} > div > .text {
        margin-top: auto;
        margin-bottom: auto;
        margin-left: 8px;
        font-size: 12px; 
    }

    ${k} > div > .text > div:first-child {
        font-weight: 800;
    }

    ${k}[data-has-error="true"] {
        height: 90px;
    }

    ${k} > div.error-box {
        margin-top: 4px;
        width: 100%;
        border-radius: 10px;
    }

    ${k}:hover {
        background-color: var(--rowHover);
    }
`);


export function uploadIsTerminal(upload: Upload): boolean {
    return !upload.paused && (upload.terminationRequested || upload.error != null || upload.state === UploadState.DONE);
}

function uploadProgressText(progressInBytes: number, fileSizeInBytes: number): string {
    return `${sizeToString(progressInBytes)} / ${sizeToString(fileSizeInBytes)}`
}

export function UploaderRow({upload, callbacks}: {upload: Upload, callbacks: UploadCallback}): React.ReactNode {
    const isPaused = upload.paused;
    const inProgress = !upload.terminationRequested && !upload.paused && !upload.error && upload.state !== UploadState.DONE;
    const stopped = upload.terminationRequested || !!upload.error;

    const progressInfo = {stopped: stopped && !isPaused, progress: upload.progressInBytes, limit: upload.fileSizeInBytes ?? 1, indeterminate: false};
    const right = `${uploadProgressText(upload.progressInBytes, upload.fileSizeInBytes ?? 0)} (${sizeToString(uploadCalculateSpeed(upload))}/s)`;
    const icon = <FtIcon fileIcon={{type: upload.folderName ? "DIRECTORY" : "FILE", ext: extensionFromPath(upload.name)}} size="24px" />;

    const title = upload.folderName ?? upload.name;
    const removeOperation = <TooltipV2 tooltip={"Click to remove row"}>
        <Icon
            cursor="pointer"
            name={"close"}
            onClick={() => {
                callbacks.clearUploads([upload]);
                const fullFilePath = upload.targetPath + "/" + upload.name;
                removeUploadFromStorage(fullFilePath);
                upload.state = UploadState.DONE;
            }}
        />
    </TooltipV2>;

    return upload.folderName ?
        <TaskRow
            error={upload.error}
            icon={icon}
            title={`${title} - Uploaded ${upload.filesCompleted} of ${upload.filesDiscovered} ${upload.filesDiscovered > 1 ? "files" : "file"}`}
            progress={right}
            removeOrCancel={inProgress ? <Icon name="close" cursor="pointer" color="errorMain"
                onClick={() => callbacks.stopUploads([upload])} /> : removeOperation}
            progressInfo={progressInfo}
        /> : <TaskRow
            error={upload.error}
            icon={icon}
            title={title}
            progress={right}
            isPaused={isPaused}
            pause={inProgress || isPaused ? (
                upload.paused ?
                    <Icon cursor="pointer" name="play" onClick={() => callbacks.resumeUploads([upload])} color="primaryMain" /> :
                    <Icon cursor="pointer" name="pauseSolid" onClick={() => callbacks.pauseUploads([upload])} color="primaryMain" />
            ) : null}
            removeOrCancel={inProgress ? <Icon name="close" cursor="pointer" color="errorMain"
                onClick={() => callbacks.stopUploads([upload])} />
                : removeOperation}
            progressInfo={progressInfo}
        />;
}

export function TaskRow({title, body, progress, icon, progressInfo, removeOrCancel, pause, error, isPaused}: {
    icon: React.ReactNode;
    title: string | React.ReactNode;
    body?: string | React.ReactNode;
    progress: string | React.ReactNode;
    error?: string;
    removeOrCancel: React.ReactNode;
    pause?: React.ReactNode;
    isPaused?: boolean;
    progressInfo: {
        indeterminate: boolean;
        stopped: boolean;
        progress: number;
        limit: number;
    };
}): React.ReactNode {
    const hasError = error != null;
    const [hovering, setHovering] = useState(false);

    React.useCallback(() => {
        setHovering(false);
    }, [pause != null]);

    const setIsHovering = React.useCallback(() => {
        if (pause) {
            setHovering(true);
        }
    }, [pause]);

    const setNotHovering = React.useCallback(() => {
        setHovering(false);
    }, []);

    return (<div className={TaskRowClass} data-has-error={hasError}>
        <Flex height={hasError ? "calc(100% - 28px)" : "100%"}>
            <Box ml="8px" my="auto">{icon}</Box>
            <div className="text">
                <Truncate>{title}</Truncate>
                {body ? <Truncate>{body}</Truncate> : null}
                <Truncate>{progress}</Truncate>
            </div>
            <Box mr="auto" />
            <Box my="auto" mr="4px" height="32px" onMouseEnter={setIsHovering} onMouseLeave={setNotHovering}>
                {isPaused || hovering ? <Box p="4px" width="32px" onClick={setNotHovering}>{pause}</Box> : (
                    <ProgressCircle
                        indeterminate={!progressInfo.stopped && progressInfo.indeterminate}
                        failures={progressInfo.stopped ? progressInfo.limit : 0}
                        successes={progressInfo.progress}
                        total={progressInfo.limit}
                        finishedColor="successLight"
                        pendingColor="secondaryDark"
                        size={32}
                    />)}
            </Box>
            <Box mt="19px" mr="8px">{removeOrCancel}</Box>
        </Flex>
        <div className="error-box">
            {hasError ? <div className={ErrorSpan}>{error}</div> : null}
        </div>
    </div>)
}

const ErrorSpan = injectStyleSimple("error-span", `
    color: white;
    border: 1px solid var(--errorMain);
    background-color: var(--errorMain);
    padding-left: 6px;
    padding-right: 6px;
    border-radius: 12px;
    margin-right: 4px;
    margin-left: 4px;
`);

export function TaskProgress({progress, limit, stopped}: {stopped: boolean; progress: number; limit: number}): React.JSX.Element {
    return <NewAndImprovedProgress
        limitPercentage={stopped ? 0 : 100}
        height="8px"
        width="100%"
        percentage={progress / limit * 100}
        progressColor={{start: "primaryMain", end: "primaryMain"}}
        limitColor={{start: "errorMain", end: "errorMain"}}
    />;
}

const UploaderArt: React.FunctionComponent = () => {
    return <div className={UploadArtWrapper}>
        <FtIcon fileIcon={{type: "FILE", ext: "png"}} size={"64px"} />
        <FtIcon fileIcon={{type: "FILE", ext: "pdf"}} size={"64px"} />
        <FtIcon fileIcon={{type: "DIRECTORY"}} size={"128px"} />
        <FtIcon fileIcon={{type: "FILE", ext: "mp3"}} size={"64px"} />
        <FtIcon fileIcon={{type: "FILE", ext: "mp4"}} size={"64px"} />
    </div>;
};

// Styles

const modalStyle: ReactModal.Styles = ({
    content: {
        ...largeModalStyle.content,
        left: `calc(50vw - 225px)`,
        minWidth: "250px",
        width: "450px",
        maxWidth: "600px",
        height: "auto",
        overflowY: "hidden",
    },
    overlay: largeModalStyle.overlay
});

const DropZoneWrapper = injectStyle("dropzone-wrapper", k => `
    ${k} {
        height: auto;
    }
`);

const DropZoneBox = injectStyle("dropzone-box", k => `
    ${k} {
        width: 100%;        
        display: flex;
        justify-content: center;
    }

    ${k}[data-slim="false"] {
        height: 240px;
        align-items: center;
    }

    ${k} > p {
        margin: 25px;
    }
`);

const UploadArtWrapper = injectStyle("upload-art", k => `   
    ${k} > svg:nth-child(1) {
        margin-top: -32px;
    }

    ${k} > svg:nth-child(2) {
        margin-left: -32px;
    }

    ${k} > svg:nth-child(5) {
        margin-top: -32px;
        margin-left: -32px;
        position: relative;
        z-index: -100;
    }
`);

export default Uploader;
