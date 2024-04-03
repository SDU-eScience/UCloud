import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {default as ReactModal} from "react-modal";
import {Box, Flex, FtIcon, Icon, Truncate, Text} from "@/ui-components";
import {TextSpan} from "@/ui-components/Text";
import {
    errorMessageOrDefault,
    extensionFromPath,
    inSuccessRange,
    preventDefault
} from "@/UtilityFunctions";
import {PackagedFile, filesFromDropOrSelectEvent} from "@/Files/HTML5FileSelector";
import {supportedProtocols, Upload, uploadCalculateSpeed, UploadState, uploadTrackProgress, useUploads} from "@/Files/Upload";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import {BulkResponse} from "@/UCloud";
import {fileName, sizeToString} from "@/Utilities/FileUtilities";
import {ChunkedFileReader, createLocalStorageFolderUploadKey, createLocalStorageUploadKey, UPLOAD_LOCALSTORAGE_PREFIX} from "@/Files/ChunkedFileReader";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {Client} from "@/Authentication/HttpClientInstance";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import {TextClass} from "@/ui-components/Text";
import {formatDistance} from "date-fns";
import {removeUploadFromStorage} from "@/Files/ChunkedFileReader";
import {Spacer} from "@/ui-components/Spacer";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import {FilesCreateUploadRequestItem, FilesCreateUploadResponseItem} from "@/UCloud/UFile";

const MAX_CONCURRENT_UPLOADS = 5;
const MAX_CONCURRENT_UPLOADS_IN_FOLDER = 5;
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

function fetchValidFolderUploadFromLocalStorage(path: string): LocalStorageFolderUploadInfo | null {
    const item = localStorage.getItem(createLocalStorageFolderUploadKey(path));
    if (item === null) return null;

    const parsed = JSON.parse(item) as LocalStorageFolderUploadInfo;
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

    const files = await upload.row;
    if (files.length === 0) return;

    if (strategy.protocol !== "CHUNKED" && strategy.protocol !== "WEBSOCKET") {
        upload.error = "Upload not supported for this provider";
        upload.state = UploadState.DONE;
        return;
    }


    if (upload.folderName) {
        const theFiles = files;

        const uploadInfo = fetchValidFolderUploadFromLocalStorage(upload.folderName);

        upload.initialProgress = 0;
        upload.fileSizeInBytes = upload.row.reduce((sum, current) => sum + current.size, 0);

        upload.resume = createResumeableFolder(upload, strategy, upload.folderName);
        await upload.resume();
    } else {
        const theFile = files[0];
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
    OK,
    CHECKSUM,
    CHUNK,
    SKIP
}

function createResumeableFolder(
    upload: Upload,
    strategy: FilesCreateUploadResponseItem,
    folderPath: string
) {
    const files: Map<number, PackagedFile> = new Map();
    let totalSize = 0;

    let fileIndex = 0;
    for (const f of upload.row) {
        files.set(fileIndex, f);
        fileIndex++;
    }
        
    return async () => {
        const uploadSocket = new WebSocket(
            strategy!.endpoint.replace("integration-module:8889", "localhost:9000")
                .replace("http://", "ws://").replace("https://", "wss://")
        );

        uploadSocket.binaryType = "arraybuffer";

        await new Promise((resolve, reject) => {
            uploadSocket.addEventListener("message", async (message) => {
                const frame = new DataView(message.data as ArrayBuffer);
                const messageType = frame.getUint8(0);
                const fileId = frame.getUint32(1);

                switch (messageType as FolderUploadMessageType) {
                    case FolderUploadMessageType.OK: {
                        console.log(`Should upload file ${fileId}`);
                        const key = fileId;
                        const theFile = files.get(fileId);

                        if (theFile) {
                            const reader = new ChunkedFileReader(theFile.fileObject);

                            while (!reader.isEof() && !upload.terminationRequested) {
                                const message = await constructUploadChunk(reader, FolderUploadMessageType.CHUNK, key);
                                await sendWsChunk(uploadSocket, message);

                                upload.progressInBytes += message.byteLength;
                                uploadTrackProgress(upload);

                                const expiration = new Date().getTime() + UPLOAD_EXPIRATION_MILLIS;
                                localStorage.setItem(
                                    createLocalStorageFolderUploadKey(folderPath),
                                    JSON.stringify({
                                        size: totalSize,
                                        strategy: strategy!,
                                        expiration
                                    } as LocalStorageFolderUploadInfo)
                                );
                            }
                            upload.filesCompleted++;
                            uploadTrackProgress(upload);
                        }

                        break;
                    }
                    case FolderUploadMessageType.CHECKSUM: {
                        console.log(`Checksum for file ${fileId}: ...`);
                        break;
                    }
                    case FolderUploadMessageType.SKIP: {
                        console.log(`Should skip file ${fileId}`);

                        upload.progressInBytes += files.get(fileId)?.size ?? 0;
                        upload.filesCompleted++;
                        uploadTrackProgress(upload);

                        break;
                    }
                }
            });

            uploadSocket.addEventListener("close", async (event) => {
                upload.filesCompleted = upload.row.length; 
                resolve(true);
            });

            uploadSocket.addEventListener("open", async (event) => {
                // Generate and send file listing
                const listing: string[] = [];
                
                for (const [id, f] of files) {
                    const path = f.fullPath.split("/").slice(2).join("/");
                    listing.push(`${id} ${path} ${f.size} ${f.lastModified}`);
                    totalSize += f.size;
                }

                uploadSocket.send(listing.join("\n"));
            });
        });
    }

    function constructMessageMeta(type: FolderUploadMessageType, fileId: number): ArrayBuffer {
        const buf = new ArrayBuffer(5);
        new DataView(buf).setUint8(0, type);
        new DataView(buf).setUint32(1, fileId, false);
        return buf;
    }

    async function constructUploadChunk(reader: ChunkedFileReader, type: FolderUploadMessageType, fileId: number): Promise<ArrayBuffer> {
        const chunk = await reader.readChunk(maxChunkSize);
        const meta = constructMessageMeta(FolderUploadMessageType.CHUNK, fileId);
        return concatArrayBuffers(meta, chunk);
    }
}

function concatArrayBuffers(a: ArrayBuffer, b: ArrayBuffer): ArrayBuffer {
    const a1 = new Uint8Array(a);
    const b1 = new Uint8Array(b);
    const c = new Uint8Array(a1.byteLength + b1.byteLength);
    
    c.set(a1, 0);
    c.set(b1, a1.length);

    return c.buffer
}

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

                const uploadType = upload.row.length > 1 ? "FOLDER" : "FILE";
                const fullFilePath = uploadType === "FOLDER" && upload.folderName ?
                    upload.targetPath + "/" + upload.folderName
                    : upload.targetPath + "/" + upload.row[0].name;

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
                cpy = cpy.filter(it => it !== upload.targetPath + "/" + upload.row[0].name);
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
        const files = await filesFromDropOrSelectEvent(e);

        // Collect 
        const singleFileUploads: Upload[] = files.filter(f => f.fullPath.split("/").length === 2)
            .map(f => ({
                row: [f],
                progressInBytes: 0,
                filesCompleted: 0,
                state: UploadState.PENDING,
                conflictPolicy: "RENAME" as const,
                targetPath: uploadPath,
                initialProgress: 0,
                uploadEvents: []
            }));

        const folderUploadFiles = files.filter(f => f.fullPath.split("/").length > 2);
        const folderUploads: Upload[] = [];
        const topLevelFolders = folderUploadFiles.map(it => it.fullPath.split("/")[1]).filter((val, index, arr) => arr.indexOf(val) === index)

        for (const topLevelFolder of topLevelFolders) {
            const folderFiles = folderUploadFiles.filter(it => it.fullPath.startsWith(`/${topLevelFolder}/`));

            folderUploads.push({
                folderName: topLevelFolder,
                row: folderFiles,
                progressInBytes: 0,
                filesCompleted: 0,
                state: UploadState.PENDING,
                conflictPolicy: "RENAME" as const,
                targetPath: uploadPath,
                initialProgress: 0,
                uploadEvents: []
            });
        }

        setUploads(uploads.concat(singleFileUploads, folderUploads));
        startUploads(singleFileUploads.concat(folderUploads));
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

    const uploadFilePaths = uploads.map(it => it.row[0].name);
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
                        <Box ml="auto" />
                        <Icon mr="8px" mt="8px" cursor="pointer" color="primaryContrast" size="16px" name="close" />
                    </Flex>
                    <div className={classConcat(TextClass, UploaderText)} data-has-uploads={hasUploads}>Upload files</div>
                    <Text color="white">{uploadingText}</Text>
                </div>
                <div style={{
                    // Note(Jonas): Modal height, row with close button, file upload text height, top and bottom padding
                    maxHeight: `calc(${modalStyle.content?.maxHeight} - 24px - 37.5px - 20px - 20px)`, overflowY: "scroll"
                }}>
                    <div className="uploads" style={{width: "100%"}}>
                        {uploads.map((upload, idx) => (
                            <UploadRow
                                key={`${upload.row[0].name}-${idx}`}
                                upload={upload}
                                callbacks={callbacks}
                            />
                        ))}
                    </div>
                    <Flex justifyContent="center">
                        <label style={{width: "100%", height: !hasUploads ? undefined : "70px", marginBottom: "8px"}} htmlFor={"fileUploadBrowse"}>
                            <div className={DropZoneBox} onDrop={onSelectedFile} onDragEnter={preventDefault} onDragLeave={preventDefault}
                                onDragOver={preventDefault} data-slim={hasUploads}>
                                <div data-has-uploads={hasUploads} className={UploadMoreClass}>
                                    {hasUploads ? null :
                                        <UploaderArt />
                                    }
                                    <div className="upload-more-text" style={{marginTop: "22px"}}>
                                        <TextSpan mr="0.5em"><Icon hoverColor="primaryContrast" name="upload" /></TextSpan>
                                        <TextSpan mr="0.3em">Drop files here or</TextSpan>
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
                                                <FtIcon fileIcon={{type: "FILE", ext: extensionFromPath(fileName(it))}} size="32px" />
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
                                                <Icon cursor="pointer" title="Resume upload" name="play" color="primaryMain" mr="12px" />
                                            </label>
                                            <Icon cursor="pointer" title="Remove" name="close" color="errorMain" mr="12px" onClick={() => {
                                                setPausedFilesInFolder(files => files.filter(file => file !== it));
                                                removeUploadFromStorage(it);
                                            }} />
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


function UploadRow({upload, callbacks}: {upload: Upload, callbacks: UploadCallback}): JSX.Element {
    const [hoverPause, setHoverPause] = React.useState(false);
    const inProgress = !upload.terminationRequested && !upload.paused && !upload.error && upload.state !== UploadState.DONE;
    const paused = upload.paused;
    const showPause = hoverPause && !paused;
    const showCircle = !hoverPause && !paused;
    const stopped = upload.terminationRequested || upload.error;

    return upload.folderName ? (
        <div className={UploaderRowClass} data-has-error={upload.error != null}>
            <div>
                <div><FtIcon fileIcon={{type: "DIRECTORY"}} size="32px" /></div>
                <div>
                    <Truncate maxWidth="270px" color="var(--textPrimary)" fontSize="18px">{upload.folderName}</Truncate>
                    <Text fontSize="12px">Uploaded {upload.filesCompleted} of {upload.row.length} {upload.row.length > 1 ? "files" : "file"}</Text>
                </div>
                <div />
                <Flex mr="16px">
                    <Text style={{fontSize: "var(--secondaryText)"}}>
                        {sizeToString(upload.progressInBytes + upload.initialProgress)}
                        {" / "}
                        {sizeToString(upload.fileSizeInBytes ?? 0)}
                        {" "}
                        ({sizeToString(uploadCalculateSpeed(upload))}/s)
                    </Text>
                    <Box mr="8px" />
                    {inProgress ? <>
                        {showPause ? <Icon cursor="pointer" onMouseLeave={() => setHoverPause(false)} onClick={() => callbacks.pauseUploads([upload])} name="pauseSolid" color="primaryMain" /> : null}
                        {showCircle ? <Icon color="primaryMain" name="notchedCircle" spin onMouseEnter={() => setHoverPause(true)} /> : null}
                        <Icon name="close" cursor="pointer" ml="8px" color="errorMain" onClick={() => callbacks.stopUploads([upload])} />
                    </>
                        :
                        <>
                            {paused ? <Icon cursor="pointer" mr="8px" name="play" onClick={() => callbacks.resumeUploads([upload])} color="primaryMain" /> : null}
                            <Icon mr="16px" cursor="pointer" name={stopped ? "close" : "check"} onClick={() => {
                                callbacks.clearUploads([upload]);
                                const fullFilePath = upload.targetPath + "/" + upload.row[0].name;
                                removeUploadFromStorage(fullFilePath);
                                upload.state = UploadState.DONE;
                                if (upload.row.length === 0) return;
                            }} color={stopped ? "errorMain" : "primaryMain"} />
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
                <div><FtIcon fileIcon={{type: "FILE", ext: extensionFromPath(upload.row[0].name)}} size="32px" /></div>
                <div>
                    <Truncate maxWidth="270px" color="var(--textPrimary)" fontSize="18px">{upload.row[0].name}</Truncate>
                    <Text fontSize="12px">{sizeToString(upload.fileSizeInBytes ?? 0)}</Text>
                </div>
                <div />
                <Flex mr="16px">
                    <Text style={{fontSize: "var(--secondaryText)"}}>
                        {sizeToString(upload.progressInBytes + upload.initialProgress)}
                        {" / "}
                        {sizeToString(upload.fileSizeInBytes ?? 0)}
                        {" "}
                        ({sizeToString(uploadCalculateSpeed(upload))}/s)
                    </Text>
                    <Box mr="8px" />
                    {inProgress ? <>
                        {showPause ? <Icon cursor="pointer" onMouseLeave={() => setHoverPause(false)} onClick={() => callbacks.pauseUploads([upload])} name="pauseSolid" color="primaryMain" /> : null}
                        {showCircle ? <Icon color="primaryMain" name="notchedCircle" spin onMouseEnter={() => setHoverPause(true)} /> : null}
                        <Icon name="close" cursor="pointer" ml="8px" color="errorMain" onClick={() => callbacks.stopUploads([upload])} />
                    </>
                        :
                        <>
                            {paused ? <Icon cursor="pointer" mr="8px" name="play" onClick={() => callbacks.resumeUploads([upload])} color="primaryMain" /> : null}
                            <Icon mr="16px" cursor="pointer" name={stopped ? "close" : "check"} onClick={() => {
                                callbacks.clearUploads([upload]);
                                const fullFilePath = upload.targetPath + "/" + upload.row[0].name;
                                removeUploadFromStorage(fullFilePath);
                                upload.state = UploadState.DONE;
                                if (upload.row.length === 0) return;
                                if (upload.row.length > 1) {
                                    upload.error = "Missing folder name";
                                    upload.state = UploadState.DONE;
                                    return;
                                }
                            }} color={stopped ? "errorMain" : "primaryMain"} />
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
        left: `calc(50vw - 300px)`,
        minWidth: "250px",
        width: "600px",
        maxWidth: "600px",
        height: "auto",
        overflowY: "hidden",
    }
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
