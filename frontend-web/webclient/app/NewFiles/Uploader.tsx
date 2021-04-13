import * as React from "react";
import {useGlobal} from "Utilities/ReduxHooks";
import styled from "styled-components";
import ReactModal from "react-modal";
import {Spacer} from "ui-components/Spacer";
import {Divider, FtIcon, Heading, Icon, List, Truncate} from "ui-components";
import {TextSpan} from "ui-components/Text";
import {useCallback, useEffect, useMemo} from "react";
import {extensionFromPath, preventDefault} from "UtilityFunctions";
import {fetcherFromDropOrSelectEvent} from "NewFiles/HTML5FileSelector";
import {Upload, UploadState} from "NewFiles/Upload";
import {ListRow} from "ui-components/List";
import {useToggleSet} from "Utilities/ToggleSet";
import {Operation, Operations} from "ui-components/Operation";

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

const DropZoneBox = styled.div<{ slim?: boolean }>`
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
    margin-right: -32px;
  }

  svg:nth-child(5) {
    margin-top: -32px;
    margin-left: -32px;
    position: relative;
    z-index: -100;
  }
`;

const UploaderArt: React.FunctionComponent = props => {
    return <UploadArtWrapper>
        <FtIcon fileIcon={{type: "FILE", ext: "png"}} size={"64px"}/>
        <FtIcon fileIcon={{type: "FILE", ext: "pdf"}} size={"64px"}/>
        <FtIcon fileIcon={{type: "DIRECTORY"}} size={"128px"}/>
        <FtIcon fileIcon={{type: "FILE", ext: "mp3"}} size={"64px"}/>
        <FtIcon fileIcon={{type: "FILE", ext: "mp4"}} size={"64px"}/>
    </UploadArtWrapper>;
};

const Uploader: React.FunctionComponent = props => {
    const [uploadPath, setUploadPath] = useGlobal("uploadPath", "/");
    const [uploaderVisible, setUploaderVisible] = useGlobal("uploaderVisible", false);
    const [uploads, setUploads] = useGlobal("uploads", []);

    const closeModal = useCallback(() => {
        setUploaderVisible(false);
    }, []);

    const onSelectedFile = useCallback(async (e) => {
        e.preventDefault();
        const fileFetcher = fetcherFromDropOrSelectEvent(e);
        setUploads(uploads.concat(fileFetcher.map(it => ({
            row: it,
            progressInBytes: 0,
            state: UploadState.NOT_STARTED,
            conflictPolicy: "RENAME",
            targetPath: uploadPath
        }))));
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

    const toggleSet = useToggleSet(uploads);
    const callbacks: UploadCallback = useMemo(() => {
        return {};
    }, []);

    return <>
        <ReactModal
            isOpen={uploaderVisible}
            style={modalStyle}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={closeModal}
        >
            <div data-tag={"uploadModal"}>
                <Spacer
                    left={<Heading>Upload Files</Heading>}
                    right={(
                        <>
                            <Icon
                                name="close"
                                cursor="pointer"
                                data-tag="modalCloseButton"
                                onClick={closeModal}
                            />
                        </>
                    )}
                />

                <Divider/>

                <label htmlFor={"fileUploadBrowse"}>
                    <DropZoneBox onDrop={onSelectedFile} onDragEnter={preventDefault} onDragLeave={preventDefault}
                                 onDragOver={preventDefault} slim={uploads.length > 0}>
                        <p>
                            {uploads.length > 0 ? null : <UploaderArt/>}
                            <TextSpan mr="0.5em"><Icon name="upload"/></TextSpan>
                            <TextSpan mr="0.3em">Drop files here or</TextSpan>
                            <a href="#">browse</a>
                            <input
                                id={"fileUploadBrowse"}
                                type={"file"}
                                style={{display: "none"}}
                                onChange={onSelectedFile}
                            />
                        </p>
                    </DropZoneBox>
                </label>

                {uploads.length === 0 ? null : <>
                    <Operations
                        location={"TOPBAR"}
                        operations={operations}
                        selected={toggleSet.checked.items}
                        extra={callbacks}
                        entityNameSingular={entityName}
                    />
                    <Divider/>

                    <List>
                        {uploads.map((upload, idx) => (
                            <ListRow
                                key={`${upload.row.rootEntry.name}-${idx}`}
                                isSelected={toggleSet.checked.has(upload)}
                                select={() => toggleSet.toggle(upload)}
                                left={
                                    <>
                                        <FtIcon
                                            fileIcon={{
                                                type: upload.row.rootEntry.isDirectory ? "DIRECTORY" : "FILE",
                                                ext: extensionFromPath(upload.row.rootEntry.name)
                                            }}
                                            size={"42px"}
                                        />

                                        <Truncate
                                            title={upload.row.rootEntry.name}
                                            width={["320px", "320px", "320px", "320px", "440px", "560px"]}
                                            ml={"8px"}
                                            fontSize={20}
                                            children={upload.row.rootEntry.name}
                                        />
                                    </>
                                }
                                right={
                                    <>
                                        <Operations
                                            row={upload}
                                            location={"IN_ROW"}
                                            operations={operations}
                                            selected={toggleSet.checked.items}
                                            extra={callbacks}
                                            entityNameSingular={entityName}
                                        />
                                    </>
                                }
                            />
                        ))}
                    </List>
                </>}
            </div>
        </ReactModal>
    </>;
};

interface UploadCallback {

}

const entityName = "Upload";
const operations: Operation<Upload, UploadCallback>[] = [
    {
        enabled: selected => selected.length > 0 && selected.every(it => it.state === UploadState.NOT_STARTED),
        onClick: (selected, cb) => {

        },
        text: "Start",
        color: "green",
        icon: "upload",
        primary: true,
    },
    {
        enabled: selected => selected.length > 0 && selected.every(it => it.state !== UploadState.NOT_STARTED),
        onClick: (selected, cb) => {

        },
        text: "Cancel",
        color: "red",
        icon: "trash",
        confirm: true,
        primary: true,
    }
];

export default Uploader;
