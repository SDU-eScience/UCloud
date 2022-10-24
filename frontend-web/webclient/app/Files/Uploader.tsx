import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";
import styled from "styled-components";
import {default as ReactModal} from "react-modal";
import {Box, Divider, Flex, FtIcon, Icon, List, Truncate, Text} from "@/ui-components";
import {TextSpan} from "@/ui-components/Text";
import {
    errorMessageOrDefault,
    extensionFromPath,
    inSuccessRange,
    preventDefault
} from "@/UtilityFunctions";
import {fetcherFromDropOrSelectEvent} from "@/Files/HTML5FileSelector";
import {supportedProtocols, Upload, uploadCalculateSpeed, UploadState, uploadTrackProgress} from "@/Files/Upload";
import {ListRowStat} from "@/ui-components/List";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {Operation, Operations} from "@/ui-components/Operation";
import {api as FilesApi, FilesCreateUploadResponseItem} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {BulkResponse} from "@/UCloud";
import {ChunkedFileReader, createLocalStorageUploadKey, UPLOAD_LOCALSTORAGE_PREFIX} from "@/Files/ChunkedFileReader";
import {fileName, sizeToString} from "@/Utilities/FileUtilities";
import {FilesCreateUploadRequestItem} from "@/UCloud/FilesApi";
import {useSelector} from "react-redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ItemRenderer, ItemRow} from "@/ui-components/Browse";
import {BrowseType} from "@/Resource/BrowseType";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {Client} from "@/Authentication/HttpClientInstance";

const MAX_CONCURRENT_UPLOADS = 5;
const entityName = "Upload";
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
    const fullFilePath = upload.targetPath + "/" + theFile.fullPath;

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

const Uploader: React.FunctionComponent = () => {
    const [uploadPath] = useGlobal("uploadPath", "/");
    const [uploaderVisible, setUploaderVisible] = useGlobal("uploaderVisible", false);
    const [uploads, setUploads] = useGlobal("uploads", []);
    const [lookForNewUploads, setLookForNewUploads] = useState(false);

    const refresh = useSelector<ReduxObject, (() => void) | undefined>(state => state.header.refresh);

    const closeModal = useCallback(() => {
        setUploaderVisible(false);
    }, []);

    const toggleSet = useToggleSet(uploads);
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
        setUploads(uploads.filter(u => !batch.some(b => b === u)));
        toggleSet.uncheckAll();
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
        const matches = Object.keys(localStorage).filter(key => key.startsWith(UPLOAD_LOCALSTORAGE_PREFIX)).map(key =>
            key.replace(`${UPLOAD_LOCALSTORAGE_PREFIX}:`, "")
        ).filter(key => key.replace(`/${fileName(key)}`, "") === uploadPath);
        setPausedFilesInFolder(matches);
    }, [uploadPath, lookForNewUploads]);

    return <>
        <ReactModal
            isOpen={uploaderVisible}
            style={modalStyle}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={closeModal}
        >
            <div data-tag="uploadModal">
                <Operations
                    location="TOPBAR"
                    operations={operations}
                    selected={toggleSet.checked.items}
                    extra={callbacks}
                    entityNameSingular={entityName}
                />
                <Divider />

                <label htmlFor={"fileUploadBrowse"}>
                    <DropZoneBox onDrop={onSelectedFile} onDragEnter={preventDefault} onDragLeave={preventDefault}
                        onDragOver={preventDefault} slim={uploads.length > 0}>
                        <Flex width={320} alignItems={"center"} flexDirection={"column"}>
                            {uploads.length > 0 ? null : <UploaderArt />}
                            <Box ml={"-1.5em"}>
                                <TextSpan mr="0.5em"><Icon name="upload" /></TextSpan>
                                <TextSpan mr="0.3em">Drop files here or</TextSpan>
                                <i>browse</i>
                                <input
                                    id={"fileUploadBrowse"}
                                    type={"file"}
                                    style={{display: "none"}}
                                    onChange={onSelectedFile}
                                />
                            </Box>
                        </Flex>
                    </DropZoneBox>
                </label>

                <List childPadding={"8px"} bordered={false}>
                    {uploads.map((upload, idx) => (
                        <ItemRow
                            key={`${upload.row.rootEntry.name}-${idx}`}
                            browseType={BrowseType.Embedded}
                            renderer={renderer}
                            toggleSet={toggleSet}
                            operations={operations}
                            callbacks={callbacks}
                            itemTitle={entityName}
                            item={upload}
                        />
                    ))}
                </List>

                {pausedFilesInFolder.length === 0 ? null :
                    <div>
                        <Text>Uploads that can be resumed:</Text>
                        {pausedFilesInFolder.map(it => <Text bold key={it}>{fileName(it)}</Text>)}
                    </div>
                }
            </div>
        </ReactModal>
    </>;
};

interface UploadCallback {
    startUploads: (batch: Upload[]) => void;
    stopUploads: (batch: Upload[]) => void;
    pauseUploads: (batch: Upload[]) => void;
    resumeUploads: (batch: Upload[]) => void;
    clearUploads: (batch: Upload[]) => void;
}

const renderer: ItemRenderer<Upload> = {
    Icon: (props) => {
        const upload = props.resource;
        if (!upload) return null;
        return <FtIcon
            fileIcon={{
                type: upload.row.rootEntry.isDirectory ? "DIRECTORY" : "FILE",
                ext: extensionFromPath(upload.row.rootEntry.name)
            }}
            size={props.size}
        />
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        return <Truncate
            title={resource.row.rootEntry.name}
            width={["320px", "320px", "320px", "320px", "440px", "560px"]}
            fontSize={20}
        >
            {resource.row.rootEntry.name}
        </Truncate>
    },

    Stats: ({resource}) => {
        if (!resource) return null;
        return <>
            {!resource.fileSizeInBytes ? null :
                <ListRowStat icon={"upload"} color={"iconColor"} color2={"iconColor2"}>
                    {sizeToString(resource.progressInBytes + resource.initialProgress)}
                    {" / "}
                    {sizeToString(resource.fileSizeInBytes)}
                    {" "}
                    ({sizeToString(uploadCalculateSpeed(resource))}/s)
                </ListRowStat>
            }
            {!resource.error ? null : <ListRowStat icon={"close"} color={"red"}>
                <ErrorSpan>{resource.error}</ErrorSpan>
            </ListRowStat>}
        </>
    },

    ImportantStats: ({resource}) => {
        const upload = resource;
        if (!upload) return null;
        const {terminationRequested, paused, state, error} = upload;
        const iconName = terminationRequested || error ? "close" : "check";
        const iconColor = terminationRequested || error ? "red" : "green";
        return <>
            {state !== UploadState.DONE ? null : (
                paused ? null : <Box>
                    <Icon
                        name={iconName}
                        color={iconColor}
                    />
                </Box>
            )}
        </>;
    }
};

const operations: Operation<Upload, UploadCallback>[] = [
    {
        enabled: selected => selected.length > 0 && selected.every(it => it.state === UploadState.UPLOADING),
        onClick: (selected, cb) => cb.pauseUploads(selected),
        text: "Pause",
        icon: "pauseSolid"
    },
    {
        enabled: selected => selected.length > 0 &&
            selected.every(it => it.state === UploadState.PENDING || it.state === UploadState.UPLOADING),
        onClick: (selected, cb) => cb.stopUploads(selected),
        text: "Cancel",
        color: "red",
        icon: "trash",
        confirm: true,
    },
    {
        enabled: selected => selected.length > 0 && selected.every(it => it.paused),
        onClick: (selected, cb) => cb.resumeUploads(selected),
        text: "Resume",
        icon: "play",
        primary: true
    },
    {
        enabled: selected => selected.length > 0 && selected.every(it => it.state === UploadState.DONE),
        onClick: (selected, cb) => cb.clearUploads(selected),
        text: "Clear",
        icon: "close",
        color: "red"
    }
];

const ErrorSpan = styled.span`
    color: var(--white);
    border: 1px solid red;
    background-color: red;
    padding-left: 4px;
    padding-right: 4px;
    border-radius: 2px;
`

const UploaderArt: React.FunctionComponent = () => {
    return <UploadArtWrapper>
        <FtIcon fileIcon={{type: "FILE", ext: "png"}} size={"64px"} />
        <FtIcon fileIcon={{type: "FILE", ext: "pdf"}} size={"64px"} />
        <FtIcon fileIcon={{type: "DIRECTORY"}} size={"128px"} />
        <FtIcon fileIcon={{type: "FILE", ext: "mp3"}} size={"64px"} />
        <FtIcon fileIcon={{type: "FILE", ext: "mp4"}} size={"64px"} />
    </UploadArtWrapper>;
};

// Styles

const modalStyle = {
    // https://github.com/reactjs/react-modal/issues/62
    content: {
        borderRadius: "4px",
        bottom: "auto",
        minHeight: "10rem",
        left: "50%",
        maxHeight: "80vh",
        padding: "2rem",
        position: "fixed" as const,
        right: "auto",
        top: "50%",
        transform: "translate(-50%,-50%)",
        minWidth: "730px",
        width: "80vw",
        maxWidth: "60rem",
        background: ""
    }
};

const DropZoneBox = styled.div<{slim?: boolean}>`
    width: 100%;
    ${p => p.slim ? {height: "80px"} : {height: "280px"}}
    border-width: 2px;
    border-color: rgb(102, 102, 102);
    border-style: dashed;
    border-radius: 5px;
    margin: 16px 0 16px 0;
    display: flex;
    align-items: center;
    justify-content: center;

    & > p {
        margin: 25px;
    }
`;

const UploadArtWrapper = styled.div`
  svg:nth-child(1) {
    margin-top: -32px;
  }

  svg:nth-child(2) {
    margin-left: -32px;
  }

  svg:nth-child(5) {
    margin-top: -32px;
    margin-left: -32px;
    position: relative;
    z-index: -100;
  }
`;

export default Uploader;
