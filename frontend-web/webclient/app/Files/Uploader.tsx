import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useGlobal} from "Utilities/ReduxHooks";
import styled from "styled-components";
import ReactModal from "react-modal";
import {Box, Divider, Flex, FtIcon, Icon, List, Truncate} from "ui-components";
import {TextSpan} from "ui-components/Text";
import {
    errorMessageOrDefault,
    extensionFromPath,
    inSuccessRange,
    preventDefault
} from "UtilityFunctions";
import {fetcherFromDropOrSelectEvent} from "Files/HTML5FileSelector";
import {supportedProtocols, Upload, UploadState} from "Files/Upload";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {useToggleSet} from "Utilities/ToggleSet";
import {Operation, Operations} from "ui-components/Operation";
import * as UCloud from "UCloud";
import FileApi = UCloud.file.orchestrator;
import {callAPI} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";
import {BulkResponse} from "UCloud";
import {ChunkedFileReader, createLocalStorageUploadKey} from "Files/ChunkedFileReader";
import {sizeToString} from "Utilities/FileUtilities";

const maxConcurrentUploads = 5;
const entityName = "Upload";
const maxChunkSize = 32 * 1000 * 1000;
const FOURTY_EIGHT_HOURS_IN_MILLIS = 2 * 24 * 3600 * 1000;

interface LocalStorageFileUploadInfo {
    chunk: number;
    size: number;
    response: FileApi.FilesCreateUploadResponseItem;
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
    if (uploadInfo !== null) reader.offset = uploadInfo.chunk;

    upload.initialProgress = reader.offset;
    upload.fileSizeInBytes = reader.fileSize();

    function sendChunk(chunk: ArrayBuffer): Promise<void> {
        return new Promise(((resolve, reject) => {
            const progressStart = upload.progressInBytes;
            const request = new XMLHttpRequest();
            request.open("POST", strategy!.endpoint);
            request.setRequestHeader("Chunked-Upload-Token", strategy!.token);
            request.setRequestHeader("Chunked-Upload-Offset", (reader.offset - chunk.byteLength).toString(10));
            request.setRequestHeader("Content-Type", "application/octet-stream");
            request.responseType = "text";

            request.upload.onprogress = (ev) => {
                upload.progressInBytes = progressStart + ev.loaded;
                if (upload.terminationRequested) {
                    upload.state = UploadState.DONE;
                    request.abort();
                }
            };

            request.onreadystatechange = () => {
                if (request.status === 0) return;
                if (inSuccessRange(request.status)) resolve();

                reject(errorMessageOrDefault({request, response: request.response}, "Upload failed"));
            };

            request.send(chunk);
        }))
    }

    while (!reader.isEof() && !upload.terminationRequested) {
        await sendChunk(await reader.readChunk(maxChunkSize));

        const expiration = new Date().getTime() + FOURTY_EIGHT_HOURS_IN_MILLIS;
        localStorage.setItem(
            createLocalStorageUploadKey(fullFilePath),
            JSON.stringify({chunk: reader.offset, size: upload.fileSizeInBytes, response: strategy!, expiration} as LocalStorageFileUploadInfo)
        );
    }

    localStorage.removeItem(createLocalStorageUploadKey(fullFilePath));
}

const Uploader: React.FunctionComponent = () => {
    const [uploadPath] = useGlobal("uploadPath", "/");
    const [uploaderVisible, setUploaderVisible] = useGlobal("uploaderVisible", false);
    const [uploads, setUploads] = useGlobal("uploads", []);
    const [lookForNewUploads, setLookForNewUploads] = useState(false);

    const closeModal = useCallback(() => {
        setUploaderVisible(false);
    }, []);

    const toggleSet = useToggleSet(uploads);
    const startUploads = useCallback(async (batch: Upload[]) => {
        let activeUploads = 0;
        for (const u of uploads) {
            if (u.state === UploadState.UPLOADING) activeUploads++;
        }

        const maxUploadsToUse = maxConcurrentUploads - activeUploads;
        if (maxUploadsToUse > 0) {
            const creationRequests: FileApi.FilesCreateUploadRequestItem[] = [];
            const actualUploads: Upload[] = [];
            const resumingUploads: Upload[] = [];

            for (const upload of batch) {
                if (upload.state !== UploadState.PENDING) continue;
                if (creationRequests.length >= maxUploadsToUse) break;

                const fullFilePath = upload.targetPath + "/" + upload.row.rootEntry.name;

                const item = fetchValidUploadFromLocalStorage(fullFilePath);
                if (item !== null) {
                    upload.uploadResponse = item.response;
                    resumingUploads.push(upload);
                    upload.state = UploadState.UPLOADING;
                    continue;
                }

                upload.state = UploadState.UPLOADING;
                creationRequests.push({
                    supportedProtocols,
                    conflictPolicy: upload.conflictPolicy,
                    path: fullFilePath,
                });

                actualUploads.push(upload);
            }

            if (actualUploads.length + resumingUploads.length === 0) return;

            try {
                const responses = (await callAPI<BulkResponse<FileApi.FilesCreateUploadResponseItem>>(
                    FileApi.files.createUpload(bulkRequestOf(...creationRequests))
                )).responses;

                for (const [index, response] of responses.entries()) {
                    const upload = actualUploads[index];
                    upload.uploadResponse = response;
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

    const callbacks: UploadCallback = useMemo(() => {
        return {startUploads, stopUploads};
    }, [startUploads, stopUploads]);

    const onSelectedFile = useCallback(async (e) => {
        e.preventDefault();
        e.stopPropagation();
        const fileFetcher = fetcherFromDropOrSelectEvent(e);
        const newUploads: Upload[] = fileFetcher.map(it => ({
            row: it,
            progressInBytes: 0,
            state: UploadState.PENDING,
            conflictPolicy: "RENAME",
            targetPath: uploadPath,
            initialProgress: 0
        }));

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
        }
    }, [lookForNewUploads, startUploads]);

    return <>
        <ReactModal
            isOpen={uploaderVisible}
            style={modalStyle}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={closeModal}
        >
            <div data-tag={"uploadModal"}>
                <Operations
                    location={"TOPBAR"}
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

                <List>
                    {uploads.map((upload, idx) => (
                        <ListRow
                            key={`${upload.row.rootEntry.name}-${idx}`}
                            isSelected={toggleSet.checked.has(upload)}
                            select={() => toggleSet.toggle(upload)}
                            icon={
                                <FtIcon
                                    fileIcon={{
                                        type: upload.row.rootEntry.isDirectory ? "DIRECTORY" : "FILE",
                                        ext: extensionFromPath(upload.row.rootEntry.name)
                                    }}
                                    size={"42px"}
                                />
                            }
                            left={
                                <Truncate
                                    title={upload.row.rootEntry.name}
                                    width={["320px", "320px", "320px", "320px", "440px", "560px"]}
                                    fontSize={20}
                                >
                                    {upload.row.rootEntry.name}
                                </Truncate>
                            }
                            leftSub={
                                <ListStatContainer>
                                    {!upload.fileSizeInBytes ? null :
                                        <ListRowStat icon={"upload"} color={"iconColor"} color2={"iconColor2"}>
                                            {sizeToString(upload.progressInBytes + upload.initialProgress)}
                                            {" / "}
                                            {sizeToString(upload.fileSizeInBytes)}
                                        </ListRowStat>
                                    }
                                </ListStatContainer>
                            }
                            right={
                                <Operations
                                    row={upload}
                                    location={"IN_ROW"}
                                    operations={operations}
                                    selected={toggleSet.checked.items}
                                    extra={callbacks}
                                    entityNameSingular={entityName}
                                />
                            }
                        />
                    ))}
                </List>
            </div>
        </ReactModal>
    </>;
};

interface UploadCallback {
    startUploads: (batch: Upload[]) => void;
    stopUploads: (batch: Upload[]) => void;
}

const operations: Operation<Upload, UploadCallback>[] = [
    {
        enabled: selected => selected.length > 0 &&
            selected.every(it => it.state === UploadState.PENDING || it.state === UploadState.UPLOADING),
        onClick: (selected, cb) => cb.stopUploads(selected),
        text: "Cancel",
        color: "red",
        icon: "trash",
        confirm: true,
        primary: true,
    }
];

const UploaderArt: React.FunctionComponent = props => {
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
