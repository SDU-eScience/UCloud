import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {default as ReactModal} from "react-modal";
import {Box, Flex, FtIcon, Icon, Truncate, Text, Relative} from "@/ui-components";
import {TextSpan} from "@/ui-components/Text";
import {
    errorMessageOrDefault,
    extensionFromPath,
    inSuccessRange,
    preventDefault
} from "@/UtilityFunctions";
import {fetcherFromDropOrSelectEvent} from "@/Files/HTML5FileSelector";
import {supportedProtocols, Upload, uploadCalculateSpeed, UploadState, uploadTrackProgress} from "@/Files/Upload";
import {api as FilesApi, FilesCreateUploadResponseItem} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {BulkResponse} from "@/UCloud";
import {fileName, sizeToString} from "@/Utilities/FileUtilities";
import {ChunkedFileReader, createLocalStorageUploadKey, UPLOAD_LOCALSTORAGE_PREFIX} from "@/Files/ChunkedFileReader";
import {FilesCreateUploadRequestItem} from "@/UCloud/FilesApi";
import {useSelector} from "react-redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {Client} from "@/Authentication/HttpClientInstance";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import {TextClass} from "@/ui-components/Text";
import {formatDistance} from "date-fns";
import {removeUploadFromStorage} from "@/Files/ChunkedFileReader";
import {Spacer} from "@/ui-components/Spacer";
import {largeModalStyle} from "@/Utilities/ModalUtilities";

const MAX_CONCURRENT_UPLOADS = 5;
const maxChunkSize = 16 * 1000 * 1000;
const UPLOAD_EXPIRATION_MILLIS = 2 * 24 * 3600 * 1000;

interface LocalStorageFileUploadInfo {
    offset: number;
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

    const files = await upload.row.fetcher();
    if (files.length === 0) return;
    if (files.length > 1) {
        upload.error = "Folder uploads not yet supported";
        upload.state = UploadState.DONE;
        return;
    }

    if (strategy.protocol !== "CHUNKED") {
        upload.error = "Upload not supported for this provider";
        upload.state = UploadState.DONE;
        return;
    }

    const theFile = files[0];
    const fullFilePath = (upload.targetPath + theFile.fullPath);

    const reader = new ChunkedFileReader(theFile.fileObject);

    const uploadInfo = fetchValidUploadFromLocalStorage(fullFilePath);
    if (uploadInfo !== null) reader.offset = uploadInfo.offset;

    upload.initialProgress = reader.offset;
    upload.fileSizeInBytes = reader.fileSize();

    upload.resume = createResumeable(reader, upload, strategy, fullFilePath);
    await upload.resume();
}

function createResumeable(
    reader: ChunkedFileReader,
    upload: Upload,
    strategy: FilesCreateUploadResponseItem,
    fullFilePath: string
) {
    return async () => {
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
    };

    function sendChunk(chunk: ArrayBuffer): Promise<void> {
        return new Promise(((resolve, reject) => {
            const progressStart = upload.progressInBytes;
            const request = new XMLHttpRequest();

            request.open("POST", strategy!.endpoint.replace("integration-module:8889", "localhost:9000"));
            request.setRequestHeader("Chunked-Upload-Token", strategy!.token);
            request.setRequestHeader("Chunked-Upload-Offset", (reader.offset - chunk.byteLength).toString(10));
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
    const [uploads, setUploads] = useGlobal("uploads", []);
    const [lookForNewUploads, setLookForNewUploads] = useState(false);

    const refresh = useSelector<ReduxObject, (() => void) | undefined>(state => state.header.refresh);

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

                const fullFilePath = upload.targetPath + "/" + upload.row.rootEntry.name;

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
            // Find possible entries in 
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
                cpy = cpy.filter(it => it !== upload.targetPath + "/" + upload.row.rootEntry.name);
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
        const fileFetcher = fetcherFromDropOrSelectEvent(e);
        const newUploads: Upload[] = fileFetcher.map(it => ({
            row: it,
            progressInBytes: 0,
            state: UploadState.PENDING,
            conflictPolicy: "RENAME" as const,
            targetPath: uploadPath,
            initialProgress: 0,
            uploadEvents: []
        })).filter(it => !it.row.rootEntry.isDirectory);

        setUploads(uploads.concat(newUploads));
        startUploads(newUploads);
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

    const uploadFilePaths = uploads.map(it => it.row.rootEntry.name);
    const resumables = pausedFilesInFolder.filter(it => !uploadFilePaths.includes(fileName(it)));

    return <>
        <ReactModal
            isOpen={uploaderVisible}
            style={modalStyle}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={closeModal}
        >
            <div style={{maxHeight: "calc(80vh - 40px - 2px)", height: "calc(80vh - 40px - 2px)", overflowY: "hidden"}} className={DropZoneWrapper} data-has-uploads={hasUploads} data-tag="uploadModal">
                <div className="title" style={{height: "55px"}}>
                    <Flex onClick={closeModal}>
                        <Box ml="auto" />
                        <Icon mr="8px" mt="8px" cursor="pointer" color="var(--white)" size="16px" name="close" />
                    </Flex>
                    <div className={classConcat(TextClass, UploaderText)} data-has-uploads={hasUploads} />
                    <Text color="white">{uploadingText}</Text>
                </div>
                <div className="uploads" style={{overflowY: "scroll", width: "100%", maxHeight: "calc(80vh - 200px)"}}>
                    {uploads.map((upload, idx) => (
                        <UploadRow
                            key={`${"upload.row.rootEntry.name"}-${idx}`}
                            upload={upload}
                            callbacks={callbacks}
                        />
                    ))}
                </div>
                <Flex minHeight={"150px"} justifyContent="center" marginTop="auto">
                    <label style={{width: "100%"}} htmlFor={"fileUploadBrowse"}>
                        <div className={DropZoneBox} onDrop={onSelectedFile} onDragEnter={preventDefault} onDragLeave={preventDefault}
                            onDragOver={preventDefault} data-slim={hasUploads}>
                            <div data-has-uploads={hasUploads} className={UploadMoreClass}>
                                {hasUploads ? null :
                                    <UploaderArt />
                                }
                                <div className="upload-more-text" color="white">
                                    <TextSpan mr="0.5em"><Icon hoverColor="white" name="upload" /></TextSpan>
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
                        marginTop: hasUploads ? "-60px" : "-18px",
                        marginBottom: "4px",
                        marginLeft: "4px",
                        marginRight: "4px"
                    }}>
                        <Text color="text">Drag files to resume: </Text>
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
                                    right={<Icon cursor="pointer" name="close" color="red" mr="12px" onClick={() => {
                                        setPausedFilesInFolder(files => files.filter(file => file !== it));
                                        removeUploadFromStorage(it);
                                    }} />}
                                />
                            </div>
                        )}
                    </div>
                }
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
        let speed = uploadCalculateSpeed(upload);
        if (speed === 0) continue;
        timeRemaining += (upload.fileSizeInBytes ?? 0 - upload.progressInBytes) / speed;
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
    ${k}::after {
        content: "Upload files";
        margin-left: 28px;
        color: var(--white);
        font-size: 25px;
    }

    ${k}[data-has-uploads="true"] {
        margin-top: -22px;
    }

    ${k}[data-has-uploads="true"]::after {
        content: "File uploads";
        margin-left: auto;
        margin-right: auto;
    }
`);

const UploadMoreClass = injectStyle("upload-more", k => `
    ${k} {
        align-items: center;
        text-align: center;
        flex-direction: column;
        margin-top: 82px;
    }
    
    ${k} > div.upload-more-text {
        color: var(--white);
        margin-top: 18px;
    }

    ${k}[data-has-uploads="true"] {
        display: flex;
        background-color: transparent; 
        color: var(--white);
        height: 70px;
        width: 100%;
        align-items: center;
        border-width: 2px;
        border-color: var(--white);
        border-style: dashed;
        border-radius: 24px;
    }
`);

const UploaderRowClass = injectStyle("uploader-row", k => `
    ${k} {
        border-radius: 24px;
        background-color: var(--white); 
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

    return <div className={UploaderRowClass} data-has-error={upload.error != null}>
        <div>
            <div><FtIcon fileIcon={{type: "FILE", ext: extensionFromPath(upload.row.rootEntry.name)}} size="32px" /></div>
            <div>
                <Truncate maxWidth="270px" fontSize="18px">{upload.row.rootEntry.name}</Truncate>
                <Text fontSize="12px">{sizeToString(upload.fileSizeInBytes ?? 0)}</Text>
            </div>
            <div />
            {inProgress ? <Flex mr="16px">
                <Text style={{fontSize: "var(--secondaryText)"}}>
                    {sizeToString(upload.progressInBytes + upload.initialProgress)}
                    {" / "}
                    {sizeToString(upload.fileSizeInBytes ?? 0)}
                    {" "}
                    ({sizeToString(uploadCalculateSpeed(upload))}/s)
                </Text>
                <Box mr="8px" />
                {showPause ? <Icon cursor="pointer" onMouseLeave={() => setHoverPause(false)} onClick={() => callbacks.pauseUploads([upload])} name="pauseSolid" color="blue" /> : null}
                {showCircle ? <Icon color="blue" name="notchedCircle" spin onMouseEnter={() => setHoverPause(true)} /> : null}
                <Icon name="close" cursor="pointer" ml="8px" color="red" onClick={() => callbacks.stopUploads([upload])} />
            </Flex> :
                <>
                    {paused ? <Icon cursor="pointer" mr="8px" name="play" onClick={() => callbacks.resumeUploads([upload])} color="blue" /> : null}
                    <Icon mr="16px" cursor="pointer" name={stopped ? "close" : "check"} onClick={() => {
                        callbacks.clearUploads([upload]);
                        upload.row.fetcher().then(files => {
                            if (files.length === 0) return;
                            if (files.length > 1) {
                                upload.error = "Folder uploads not yet supported";
                                upload.state = UploadState.DONE;
                                return;
                            }

                            const theFile = files[0];
                            const fullFilePath = upload.targetPath + "/" + theFile.fullPath;
                            removeUploadFromStorage(fullFilePath);
                        });
                    }} color={stopped ? "red" : "blue"} />
                </>}
        </div>
        <div className="error-box">
            {upload.error ? <div className={ErrorSpan}>{upload.error}</div> : null}
        </div>
    </div>
}

const ErrorSpan = injectStyleSimple("error-span", `
    color: var(--white);
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

const modalStyle = ({
    content: {
        ...largeModalStyle.content,
        minHeight: "460px",
        backgroundColor: "#4D8BFC",
        minWidth: "250px",
        width: "600px",
        maxWidth: "600px",
    }
});

const DropZoneWrapper = injectStyle("dropzone-wrapper", k => `
    ${k}[data-has-uploads="false"] {
        border-width: 2px;
        border-color: var(--white);
        border-style: dashed;
        border-radius: 5px;
    }
`);

const DropZoneBox = injectStyle("dropzone-box", k => `
    ${k} {
        width: 100%;
        height: 280px;
        margin: 16px 0 16px 0;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    ${k}[data-slim="true"] {
        height: 0px;
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
