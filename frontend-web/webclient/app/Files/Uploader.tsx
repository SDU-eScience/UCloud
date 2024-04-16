import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {default as ReactModal} from "react-modal";
import {Box, Flex, FtIcon, Icon, Truncate, Text} from "@/ui-components";
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
    uploadTrackProgress,
    useUploads
} from "@/Files/Upload";
import {addFileSensitivityDialog, api as FilesApi} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import {BulkResponse} from "@/UCloud";
import {fileName, sizeToString} from "@/Utilities/FileUtilities";
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
import {formatDistance, getUnixTime} from "date-fns";
import {removeUploadFromStorage} from "@/Files/ChunkedFileReader";
import {Spacer} from "@/ui-components/Spacer";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import {FilesCreateUploadRequestItem, FilesCreateUploadResponseItem} from "@/UCloud/UFile";
import {Simulate} from "react-dom/test-utils";

const MAX_CONCURRENT_UPLOADS = 5;
const MAX_CONCURRENT_UPLOADS_IN_FOLDER = 256;
const maxChunkSize = 16 * 1000 * 1000;
const UPLOAD_EXPIRATION_MILLIS = 2 * 24 * 3600 * 1000;
const MAX_WS_BUFFER = 1024 * 1024 * 16 * 4

interface LocalStorageFileUploadInfo {
    offset: number;
    size: number;
    strategy: FilesCreateUploadResponseItem;
    expiration: number;
}

interface LocalStorageFolderUploadInfo {
    size: number;
    strategy: FilesCreateUploadResponseItem;
    expiration: number;
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

    if (strategy.protocol !== "CHUNKED" && strategy.protocol !== "WEBSOCKET") {
        upload.error = "Upload not supported for this provider";
        upload.state = UploadState.DONE;
        return;
    }


    if (upload.folderName) {
        upload.initialProgress = 0;
        // upload.fileSizeInBytes = upload.row.reduce((sum, current) => sum + current.size, 0);

        upload.resume = createResumeableFolder(upload, strategy, upload.folderName);
        await upload.resume();
    } else {
        const theFile = upload.row!;
        const fullFilePath = (upload.targetPath + "/" + theFile.fullPath);

        const reader = new ChunkedFileReader(theFile.fileObject);

        const uploadInfo = fetchValidUploadFromLocalStorage(fullFilePath);
        if (uploadInfo !== null) reader.offset = uploadInfo.offset;

        upload.initialProgress = reader.offset;
        upload.fileSizeInBytes = reader.fileSize();

        upload.resume = createResumeable(reader, upload, strategy, fullFilePath);
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
            const chunk = await reader.readChunk(maxChunkSize);
            shaSumWorker.postMessage({type: "Update", data: chunk});
        }

        shaSumWorker.postMessage({type: "End"});
    });
}

function createResumeableFolder(
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

                if (uploadSocket.readyState !== WebSocket.OPEN) return;

                if (!didInit) {
                    didInit = true;
                    for (let i = 0; i < MAX_CONCURRENT_UPLOADS_IN_FOLDER; i++) {
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
                    console.log("No more files!");
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
                    upload.filesCompleted = upload.filesDiscovered;
                    localStorage.removeItem(createLocalStorageFolderUploadKey(folderPath))
                }
                resolve(true);
            });
        });
    }

    async function startLoop(id: number) {
        while (!upload.terminationRequested && uploadSocket.readyState === WebSocket.OPEN) {
            if (startedUploads - upload.filesCompleted >= MAX_CONCURRENT_UPLOADS_IN_FOLDER) {
                await delay(1);
                continue;
            }

            const entry = readQueue.pop();
            if (!entry) {
                await delay(1);
                continue;
            }

            const [theFile, fileId] = entry;

            awaiting[theFile.fullPath] = 0;
            const reader = new ChunkedFileReader(theFile.fileObject);
            delete awaiting[theFile.fullPath];
            startedUploads++;

            if (theFile.size === 0) {
                console.log("Sending empty file", theFile);
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

            if (upload.terminationRequested) {
                if (!upload.paused) {
                } else {
                    if (uploadSocket.readyState === WebSocket.OPEN) {
                        uploadSocket.close();
                        upload.filesCompleted = 0;
                        upload.resume = createResumeableFolder(upload, strategy, folderPath);
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
        const chunk = await reader.readChunk(maxChunkSize);
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
    if (connection.bufferedAmount + chunk.byteLength < MAX_WS_BUFFER) {
        connection.send(chunk);
        onComplete();
    } else {
        window.setTimeout(() => {
            console.log("Waiting...", connection.bufferedAmount);
            _sendWsChunk(connection, chunk, onComplete)
        }, 50);
    }
}


function createResumeable(
    reader: ChunkedFileReader,
    upload: Upload,
    strategy: FilesCreateUploadResponseItem,
    fullFilePath: string
) {
    return async () => {
        if (strategy.protocol === "CHUNKED") {
            while (!reader.isEof() && !upload.terminationRequested) {
                await sendChunk(await reader.readChunk(maxChunkSize));

                const expiration = new Date().getTime() + UPLOAD_EXPIRATION_MILLIS;
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
                upload.resume = createResumeable(reader, upload, strategy, fullFilePath);
            }
        } else {
            const uploadSocket = new WebSocket(
                strategy!.endpoint.replace("integration-module:8889", "localhost:9000")
                    .replace("http://", "ws://").replace("https://", "wss://")
            );
            const progressStart = upload.progressInBytes;
            reader.offset = progressStart;

            await new Promise((resolve, reject) => {
                uploadSocket.addEventListener("message", async (message) => {
                    upload.progressInBytes = parseInt(message.data);
                    uploadTrackProgress(upload);

                    const expiration = new Date().getTime() + UPLOAD_EXPIRATION_MILLIS;
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
                        const chunk = await reader.readChunk(maxChunkSize);
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

const Uploader: React.FunctionComponent = () => {
    const [uploadPath] = useGlobal("uploadPath", "/");
    const [uploaderVisible, setUploaderVisible] = useGlobal("uploaderVisible", false);
    const [uploads, setUploads] = useUploads();
    const [lookForNewUploads, setLookForNewUploads] = useState(false);

    const refresh = useRefresh();

    const closeModal = useCallback(() => {
        setUploaderVisible(false);
    }, []);

    const startUploads = useCallback(async (batch: Upload[]) => {
        let activeUploads = 0;
        for (const u of uploads) {
            if (u.state === UploadState.UPLOADING) activeUploads++;
        }

        const maxUploadsToUse = MAX_CONCURRENT_UPLOADS - activeUploads;
        if (maxUploadsToUse > 0) {
            const creationRequests: FilesCreateUploadRequestItem[] = [];
            const actualUploads: Upload[] = [];
            const resumingUploads: Upload[] = [];

            for (const upload of batch) {
                if (upload.state !== UploadState.PENDING) continue;
                if (creationRequests.length + resumingUploads.length >= maxUploadsToUse) break;

                const uploadType = upload.folderName ? "FOLDER" : "FILE";
                const fullFilePath = uploadType === "FOLDER" && upload.folderName ?
                    upload.targetPath + "/" + upload.folderName
                    : upload.targetPath + "/" + upload.name;

                const item = fetchValidUploadFromLocalStorage(fullFilePath);
                if (item !== null) {
                    upload.uploadResponse = item.strategy;
                    resumingUploads.push(upload);
                    upload.state = UploadState.UPLOADING;
                    continue;
                }

                upload.state = UploadState.UPLOADING;
                creationRequests.push({
                    supportedProtocols,
                    type: uploadType,
                    conflictPolicy: upload.conflictPolicy,
                    id: fullFilePath,
                });

                actualUploads.push(upload);
            }

            if (actualUploads.length + resumingUploads.length === 0) return;

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

                for (const upload of [...actualUploads, ...resumingUploads]) {
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
                /* TODO(jonas): This needs to be handled for resuming uploads, I think. */
                const errorMessage = errorMessageOrDefault(e, "Unable to start upload");
                for (let i = 0; i < creationRequests.length; i++) {
                    actualUploads[i].state = UploadState.DONE;
                    actualUploads[i].error = errorMessage;
                }
                return;
            }
        }
    }, [uploads]);

    const stopUploads = useCallback((batch: Upload[]) => {
        for (const upload of batch) {
            // Find possible entries in resumables
            upload.terminationRequested = true;
        }
    }, []);

    const pauseUploads = useCallback((batch: Upload[]) => {
        for (const upload of batch) {
            upload.terminationRequested = true;
            upload.paused = true;
            upload.state = UploadState.PENDING;
        }
    }, []);

    const resumeUploads = useCallback((batch: Upload[]) => {
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
    }, [uploads]);

    const clearUploads = useCallback((batch: Upload[]) => {
        /* Note(Jonas): This is intended as pointer equality. Does this make sense in a Javascript context? */
        /* Note(Jonas): Yes. */
        setUploads(uploads.filter(u => !batch.some(b => b === u)));
        // Note(Jonas): Find possible entries in paused uploads and remove it. 
        setPausedFilesInFolder(entries => {
            let cpy = [...entries];
            for (const upload of batch) {
                cpy = cpy.filter(it => it !== upload.targetPath + "/" + upload.name);
            }
            return cpy;
        });
    }, [uploads]);

    const callbacks: UploadCallback = useMemo(() => (
        {startUploads, stopUploads, pauseUploads, resumeUploads, clearUploads}
    ), [startUploads, stopUploads]);

    const onSelectedFile = useCallback(async (e) => {
        e.preventDefault();
        e.stopPropagation();

        let allUploads: Upload[] = [];
        const events = filesFromDropOrSelectEvent(e);
        for (const u of events) {
            switch (u.type) {
                case "single": {
                    allUploads.push({
                        name: u.file.name,
                        row: u.file,
                        progressInBytes: 0,
                        filesCompleted: 0,
                        filesDiscovered: 1,
                        state: UploadState.PENDING,
                        conflictPolicy: "RENAME" as const,
                        targetPath: uploadPath,
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
                        conflictPolicy: "RENAME" as const,
                        targetPath: uploadPath,
                        initialProgress: 0,
                        uploadEvents: []
                    });
                    break;
                }
            }
        }

        setUploads(allUploads);
        startUploads(allUploads);
    }, [uploads]);

    useEffect(() => {
        const oldOnDrop = document.ondrop;
        const oldOnDragOver = document.ondragover;
        const oldOnDragEnter = document.ondragenter;
        const oldOnDragLeave = document.ondragleave;

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
            startUploads(uploads);
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
    }, [uploadPath, lookForNewUploads]);


    const hasUploads = uploads.length > 0;
    const uploadsInProgress = uploads.filter(it => it.state === UploadState.UPLOADING)
    const uploadTimings = getUploadTimings(uploadsInProgress);
    let uploadingText = uploadsInProgress.length === 0 ? null : (
        `Uploading at ${sizeToString(uploadTimings.uploadSpeed)}/s`
    );

    if (uploadTimings.timeRemaining !== 0) {
        uploadingText += ` - Approximately ${formatDistance(uploadTimings.timeRemaining, 0)}`;
    }

    const uploadFilePaths = uploads.map(it => it.name);
    const resumables = pausedFilesInFolder.filter(it => !uploadFilePaths.includes(fileName(it)));

    return <>
        <ReactModal
            isOpen={uploaderVisible}
            style={modalStyle}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={closeModal}
            className={CardClass}
        >
            <div className={DropZoneWrapper} data-has-uploads={hasUploads} data-tag="uploadModal">
                <div>
                    <Flex onClick={closeModal}>
                        <Box ml="auto"/>
                        <Icon mr="8px" mt="8px" cursor="pointer" color="primaryContrast" size="16px" name="close"/>
                    </Flex>
                    <div className={classConcat(TextClass, UploaderText)} data-has-uploads={hasUploads}>Upload files
                    </div>
                    <Text color="white">{uploadingText}</Text>
                </div>
                <div style={{
                    // Note(Jonas): Modal height, row with close button, file upload text height, top and bottom padding
                    maxHeight: `calc(${modalStyle.content?.maxHeight} - 24px - 37.5px - 20px - 20px)`,
                    overflowY: "scroll"
                }}>
                    <div className="uploads" style={{width: "100%"}}>
                        {uploads.map((upload, idx) => (
                            <UploadRow
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
                                        <UploaderArt/>
                                    }
                                    <div className="upload-more-text" style={{marginTop: "22px"}}>
                                        <TextSpan mr="0.5em"><Icon hoverColor="primaryContrast"
                                                                   name="upload"/></TextSpan>
                                        <TextSpan mr="0.3em">Drop files or folders here or</TextSpan>
                                        <i style={{cursor: "pointer"}}>browse</i>
                                        <input
                                            id={"fileUploadBrowse"}
                                            type={"file"}
                                            style={{display: "none"}}
                                            onChange={onSelectedFile}
                                        />
                                    </div>
                                </div>
                            </div>
                        </label>
                    </Flex>
                    {resumables.length === 0 ? null :
                        <div style={{
                            marginBottom: "4px",
                            marginLeft: "8px",
                            marginRight: "4px"
                        }}>
                            {resumables.map(it =>
                                <div className={UploaderRowClass} key={it}>
                                    <Spacer paddingTop="20px"
                                            left={<>
                                                <div>
                                                    <FtIcon
                                                        fileIcon={{type: "FILE", ext: extensionFromPath(fileName(it))}}
                                                        size="32px"/>
                                                </div>
                                                <div>
                                                    <Truncate maxWidth="270px" fontSize="18px">{fileName(it)}</Truncate>
                                                </div>
                                            </>}
                                            right={<>
                                                <label htmlFor="fileUploadBrowseResume">
                                                    <input
                                                        id={"fileUploadBrowseResume"}
                                                        type={"file"}
                                                        style={{display: "none"}}
                                                        onChange={onSelectedFile}
                                                    />
                                                    <Icon cursor="pointer" title="Resume upload" name="play"
                                                          color="primaryMain" mr="12px"/>
                                                </label>
                                                <Icon cursor="pointer" title="Remove" name="close" color="errorMain"
                                                      mr="12px" onClick={() => {
                                                    setPausedFilesInFolder(files => files.filter(file => file !== it));
                                                    removeUploadFromStorage(it);
                                                }}/>
                                            </>}
                                    />
                                </div>
                            )}
                        </div>
                    }
                </div>
            </div>
        </ReactModal>
    </>;
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

interface UploadCallback {
    startUploads: HandleUploadsFunction;
    stopUploads: HandleUploadsFunction;
    pauseUploads: HandleUploadsFunction;
    resumeUploads: HandleUploadsFunction;
    clearUploads: HandleUploadsFunction;
}

const UploaderText = injectStyle("uploader-text", k => `
    ${k} {
        margin-left: 12px;
        color: var(--textPrimary);
        font-size: 25px;
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
        border-radius: 24px;
    }
`);

const UploaderRowClass = injectStyle("uploader-row", k => `
    ${k} {
        border-radius: 24px;
        border: 1px solid var(--textPrimary);
        height: 70px;
        width: 100%;
        margin-top: 12px;
        margin-bottom: 12px;
    }

    ${k} > div:first-child {
        display: flex;
        align-items: center;
        padding-top: 12px;
    }
    
    ${k}[data-has-error="true"] {
        height: 90px;
    }

    ${k} > div.error-box {
        width: 100%;
        border-radius: 16px;
    }

    ${k} > div > div:first-child {
        margin-left: 16px;
    }

    ${k} > div > div:nth-child(2) {
        vertical-align: middle;
        margin-left: 8px;
    }
    
    ${k} > div > div:nth-child(3) {
        display: flex;
        flex-grow: 1;
    }
`);


function UploadRow({upload, callbacks}: { upload: Upload, callbacks: UploadCallback }): JSX.Element {
    const [hoverPause, setHoverPause] = React.useState(false);
    const inProgress = !upload.terminationRequested && !upload.paused && !upload.error && upload.state !== UploadState.DONE;
    const paused = upload.paused;
    const showPause = hoverPause && !paused;
    const showCircle = !hoverPause && !paused;
    const stopped = upload.terminationRequested || upload.error;

    return upload.folderName ? (
        <div className={UploaderRowClass} data-has-error={upload.error != null}>
            <div>
                <div><FtIcon fileIcon={{type: "DIRECTORY"}} size="32px"/></div>
                <div>
                    <Truncate maxWidth="270px" color="var(--textPrimary)" fontSize="18px">{upload.folderName}</Truncate>
                    <Text
                        fontSize="12px">Uploaded {upload.filesCompleted} of {upload.filesDiscovered} {upload.filesDiscovered > 1 ? "files" : "file"}</Text>
                </div>
                <div/>
                <Flex mr="16px">
                    <Text style={{fontSize: "var(--secondaryText)"}}>
                        {sizeToString(upload.progressInBytes + upload.initialProgress)}
                        {" / "}
                        {sizeToString(upload.fileSizeInBytes ?? 0)}
                        {" "}
                        ({sizeToString(uploadCalculateSpeed(upload))}/s)
                    </Text>
                    <Box mr="8px"/>
                    {inProgress ? <>
                            {showPause ? <Icon cursor="pointer" onMouseLeave={() => setHoverPause(false)}
                                               onClick={() => callbacks.pauseUploads([upload])} name="pauseSolid"
                                               color="primaryMain"/> : null}
                            {showCircle ? <Icon color="primaryMain" name="notchedCircle" spin
                                                onMouseEnter={() => setHoverPause(true)}/> : null}
                            <Icon name="close" cursor="pointer" ml="8px" color="errorMain"
                                  onClick={() => callbacks.stopUploads([upload])}/>
                        </>
                        :
                        <>
                            {paused ? <Icon cursor="pointer" mr="8px" name="play"
                                            onClick={() => callbacks.resumeUploads([upload])}
                                            color="primaryMain"/> : null}
                            <Icon mr="16px" cursor="pointer" name={stopped ? "close" : "check"} onClick={() => {
                                callbacks.clearUploads([upload]);
                                const fullFilePath = upload.targetPath + "/" + upload.name;
                                removeUploadFromStorage(fullFilePath);
                                upload.state = UploadState.DONE;
                            }} color={stopped ? "errorMain" : "primaryMain"}/>
                        </>}
                </Flex>
            </div>
            <div className="error-box">
                {upload.error ? <div className={ErrorSpan}>{upload.error}</div> : null}
            </div>
        </div>
    ) : (
        <div className={UploaderRowClass} data-has-error={upload.error != null}>
            <div>
                <div><FtIcon fileIcon={{type: "FILE", ext: extensionFromPath(upload.name)}} size="32px"/></div>
                <div>
                    <Truncate maxWidth="270px" color="var(--textPrimary)"
                              fontSize="18px">{upload.name}</Truncate>
                    <Text fontSize="12px">{sizeToString(upload.fileSizeInBytes ?? 0)}</Text>
                </div>
                <div/>
                <Flex mr="16px">
                    <Text style={{fontSize: "var(--secondaryText)"}}>
                        {sizeToString(upload.progressInBytes + upload.initialProgress)}
                        {" / "}
                        {sizeToString(upload.fileSizeInBytes ?? 0)}
                        {" "}
                        ({sizeToString(uploadCalculateSpeed(upload))}/s)
                    </Text>
                    <Box mr="8px"/>
                    {inProgress ? <>
                            {showPause ? <Icon cursor="pointer" onMouseLeave={() => setHoverPause(false)}
                                               onClick={() => callbacks.pauseUploads([upload])} name="pauseSolid"
                                               color="primaryMain"/> : null}
                            {showCircle ? <Icon color="primaryMain" name="notchedCircle" spin
                                                onMouseEnter={() => setHoverPause(true)}/> : null}
                            <Icon name="close" cursor="pointer" ml="8px" color="errorMain"
                                  onClick={() => callbacks.stopUploads([upload])}/>
                        </>
                        :
                        <>
                            {paused ? <Icon cursor="pointer" mr="8px" name="play"
                                            onClick={() => callbacks.resumeUploads([upload])}
                                            color="primaryMain"/> : null}
                            <Icon mr="16px" cursor="pointer" name={stopped ? "close" : "check"} onClick={() => {
                                callbacks.clearUploads([upload]);
                                const fullFilePath = upload.targetPath + "/" + upload.name;
                                removeUploadFromStorage(fullFilePath);
                                upload.state = UploadState.DONE;
                            }} color={stopped ? "errorMain" : "primaryMain"}/>
                        </>}
                </Flex>
            </div>
            <div className="error-box">
                {upload.error ? <div className={ErrorSpan}>{upload.error}</div> : null}
            </div>
        </div>
    );
}

const ErrorSpan = injectStyleSimple("error-span", `
    color: white;
    border: 1px solid red;
    background-color: red;
    padding-left: 4px;
    padding-right: 4px;
    border-radius: 12px;
    margin-right: 16px;
`);

const UploaderArt: React.FunctionComponent = () => {
    return <div className={UploadArtWrapper}>
        <FtIcon fileIcon={{type: "FILE", ext: "png"}} size={"64px"}/>
        <FtIcon fileIcon={{type: "FILE", ext: "pdf"}} size={"64px"}/>
        <FtIcon fileIcon={{type: "DIRECTORY"}} size={"128px"}/>
        <FtIcon fileIcon={{type: "FILE", ext: "mp3"}} size={"64px"}/>
        <FtIcon fileIcon={{type: "FILE", ext: "mp4"}} size={"64px"}/>
    </div>;
};

// Styles

const modalStyle: ReactModal.Styles = ({
    content: {
        ...largeModalStyle.content,
        left: `calc(50vw - 300px)`,
        minWidth: "250px",
        width: "600px",
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
