import * as React from "react";
import * as Modal from "react-modal";
import { Progress, Icon, Button, ButtonGroup, Heading, Divider, OutlineButton, Checkbox, Label, Select } from "ui-components";
import * as ReactDropzone from "react-dropzone/dist/index";
import { Cloud } from "Authentication/SDUCloudObject";
import { ifPresent, iconFromFilePath, infoNotification, uploadsNotifications, prettierString, timestampUnixMs, overwriteSwal } from "UtilityFunctions";
import { sizeToString, archiveExtensions, isArchiveExtension, statFileQuery } from "Utilities/FileUtilities";
import { bulkUpload, multipartUpload, UploadPolicy } from "./api";
import { connect } from "react-redux";
import { ReduxObject, Sensitivity } from "DefaultObjects";
import { Upload, UploadOperations, UploaderProps } from ".";
import { setUploaderVisible, setUploads, setUploaderError, setLoading } from "Uploader/Redux/UploaderActions";
import { removeEntry } from "Utilities/CollectionUtilities";
import { Box, Flex, Error } from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Toggle } from "ui-components/Toggle";
import styled from "styled-components";
import { TextSpan } from "ui-components/Text";
import { Dispatch } from "redux";
import { FileIcon } from "UtilityComponents";
import { Spacer } from "ui-components/Spacer";
import { File as SDUCloudFile } from "Files";
import { Refresh } from "Navigation/Header";

const uploadsFinished = (uploads: Upload[]): boolean => uploads.every((it) => isFinishedUploading(it.uploadXHR));
const finishedUploads = (uploads: Upload[]): number => uploads.filter((it) => isFinishedUploading(it.uploadXHR)).length;
const isFinishedUploading = (xhr?: XMLHttpRequest): boolean => !!xhr && xhr.readyState === XMLHttpRequest.DONE;

const newUpload = (file: File): Upload => ({
    file,
    conflictFile: undefined,
    resolution: UploadPolicy.RENAME,
    sensitivity: "PRIVATE",
    isUploading: false,
    progressPercentage: 0,
    extractArchive: false,
    uploadXHR: undefined,
    uploadEvents: []
});

const addProgressEvent = (upload: Upload, e: ProgressEvent) => {
    const now = timestampUnixMs();
    upload.uploadEvents = upload.uploadEvents.filter(e => now - e.timestamp < 10_000);
    upload.uploadEvents.push({ timestamp: now, progressInBytes: e.loaded });
    upload.progressPercentage = (e.loaded / e.total) * 100;
};

function calculateSpeed(upload: Upload): number {
    if (upload.uploadEvents.length === 0) return 0;

    const min = upload.uploadEvents[0];
    const max = upload.uploadEvents[upload.uploadEvents.length - 1];

    const timespan = max.timestamp - min.timestamp;
    const bytesTransferred = max.progressInBytes - min.progressInBytes;

    if (timespan === 0) return 0;
    return (bytesTransferred / timespan) * 1000;
}

class Uploader extends React.Component<UploaderProps> {

    private onFilesAdded = async (files: File[]) => {
        if (files.some(it => it.size === 0)) infoNotification("It is not possible to upload empty files.");
        if (files.some(it => it.name.length > 1025)) infoNotification("Filenames can't exceed a length of 1024 characters.");
        const filteredFiles = files.filter(it => it.size > 0 && it.name.length < 1025).map(it => newUpload(it));
        if (filteredFiles.length == 0) return;

        this.props.setLoading(true);
        const promises: { request: XMLHttpRequest, response: SDUCloudFile }[] = await Promise.all(filteredFiles.map(file =>
            Cloud.get<SDUCloudFile>(statFileQuery(`${this.props.location}/${file.file.name}`)).then(it => it).catch(it => it)
        ));
        promises.forEach((it, index) => {
            if (it.request.status === 200) filteredFiles[index].conflictFile = it.response;
        });

        if (this.props.allowMultiple !== false) { // true if no value
            this.props.setUploads(this.props.uploads.concat(filteredFiles))
        } else {
            this.props.setUploads([filteredFiles[0]])
        }
        this.props.setLoading(false);
    }

    private beforeUnload = (e: { returnValue: string; }) => {
        e.returnValue = "foo";
        uploadsNotifications(finishedUploads(this.props.uploads), this.props.uploads.length)
        return e;
    }

    private onUploadFinished(upload: Upload, xhr: XMLHttpRequest) {
        xhr.onloadend = () => {
            if (!!this.props.onFilesUploaded && uploadsFinished(this.props.uploads)) {
                window.removeEventListener("beforeunload", this.beforeUnload);
                this.props.onFilesUploaded(this.props.location);
            }
        }
        upload.uploadXHR = xhr;
        this.props.setUploads(this.props.uploads);
    }

    private startUpload = (index: number) => {
        const upload = this.props.uploads[index];
        upload.isUploading = true;
        this.props.setUploads(this.props.uploads);
        
        window.addEventListener("beforeunload", this.beforeUnload);
        if (!upload.extractArchive) {
            multipartUpload(
                `${this.props.location}/${upload.file.name}`,
                upload.file,
                upload.sensitivity,
                upload.resolution,
                e => {
                    addProgressEvent(upload, e);
                    this.props.setUploads(this.props.uploads);
                },
                err => this.props.setUploaderError(err)
            ).then(xhr => this.onUploadFinished(upload, xhr)); // FIXME Add error handling
        } else {
            bulkUpload(
                this.props.location,
                upload.file,
                upload.sensitivity,
                upload.resolution,
                e => {
                    addProgressEvent(upload, e);
                    this.props.setUploads(this.props.uploads);
                },
                err => this.props.setUploaderError(err)
            ).then(xhr => this.onUploadFinished(upload, xhr)); // FIXME Add error handling
        }
    }

    private startAllUploads = (event: { preventDefault: () => void }) => {
        event.preventDefault();
        const length = this.props.uploads.length;
        for (let i = 0; i < length; i++) {
            this.startUpload(i);
        }
    }

    private removeUpload = (index: number) => {
        const files = this.props.uploads.slice();
        if (index < files.length) {
            const remainderFiles = removeEntry(files, index);
            this.props.setUploads(remainderFiles);
        }
    }

    private abort = async (index: number) => {
        const upload = this.props.uploads[index];
        if (!!upload.uploadXHR && upload.uploadXHR.readyState != XMLHttpRequest.DONE) {
            if (upload.resolution === UploadPolicy.OVERWRITE) {
                const result = await overwriteSwal();
                if (!!result.dismiss) return;
            }
            upload.uploadXHR.abort();
            this.removeUpload(index);
        }
    }

    private onExtractChange = (index: number, value: boolean) => {
        const uploads = this.props.uploads;
        uploads[index].extractArchive = value;
        this.props.setUploads(uploads);
    }

    private updateSensitivity(index: number, sensitivity: Sensitivity) {
        const uploads = this.props.uploads;
        uploads[index].sensitivity = sensitivity;
        this.props.setUploads(uploads);
    }

    private readonly modalStyle = {
        // https://github.com/reactjs/react-modal/issues/62
        content: {
            borderRadius: "4px",
            bottom: "auto",
            minHeight: "10rem",
            left: "50%",
            maxHeight: "80vh",
            padding: "2rem",
            position: "fixed",
            right: "auto",
            top: "50%",
            transform: "translate(-50%,-50%)",
            minWidth: "20rem",
            width: "80%",
            maxWidth: "60rem"
        }
    }

    private clearUpload = (index: number) => this.props.setUploads(removeEntry(this.props.uploads, index))

    private clearFinishedUploads = () =>
        this.props.setUploads(this.props.uploads.filter(it => !isFinishedUploading(it.uploadXHR)));


    private setRewritePolicy(index: number, policy: UploadPolicy) {
        const { uploads } = this.props;
        uploads[index].resolution = policy;
        this.props.setUploads(uploads);
    }

    render() {
        const { uploads, ...props } = this.props;
        return (
            <Modal isOpen={props.visible} shouldCloseOnEsc ariaHideApp={false} onRequestClose={() => this.props.setUploaderVisible(false)}
                style={this.modalStyle}
            >
                <Spacer
                    left={<Heading>Upload Files</Heading>}
                    right={props.loading ? <Refresh onClick={() => undefined} spin /> : null}
                />
                <Divider />
                {props.error ?
                    <Box pt="0.5em" pr="0.5em" pl="0.5em">
                        <Error error={props.error} clearError={() => props.setUploaderError()} />
                    </Box> : null}
                {finishedUploads(uploads) > 0 ? (<OutlineButton mt="4px" mb="4px" color="green" fullWidth onClick={() => this.clearFinishedUploads()}>
                    Clear finished uploads
                </OutlineButton>) : null}
                <Box>
                    {uploads.map((upload, index) => (
                        <React.Fragment key={index}>
                            <UploaderRow
                                upload={upload}
                                setSensitivity={sensitivity => this.updateSensitivity(index, sensitivity)}
                                onExtractChange={value => this.onExtractChange(index, value)}
                                onUpload={() => this.startUpload(index)}
                                onDelete={it => (it.preventDefault(), this.removeUpload(index))}
                                onAbort={it => (it.preventDefault(), this.abort(index))}
                                onClear={it => (it.preventDefault(), this.clearUpload(index))}
                                setRewritePolicy={policy => this.setRewritePolicy(index, policy)}
                            />
                            <Divider />
                        </React.Fragment>
                    ))}
                    {uploads.filter(it => !it.isUploading).length > 1 && uploads.filter(it => !it.conflictFile) ?
                        <Button fullWidth color="green" onClick={this.startAllUploads}>
                            <Icon name={"upload"} />{" "}Start all!</Button> : null}
                    <ReactDropzone onDrop={this.onFilesAdded}>
                        {({ getRootProps, getInputProps }) =>
                            <DropZoneBox {...getRootProps()}>
                                <input {...getInputProps()} />
                                <p>
                                    <TextSpan mr="0.5em"><Icon name="upload" /></TextSpan>
                                    <TextSpan mr="0.3em">Drop files here or </TextSpan><a href="#">{" browse"}</a>
                                </p>
                                <p>
                                    <b>Bulk upload</b> supported for file types: <i><code>{archiveExtensions.join(", ")}</code></i>
                                </p>
                            </DropZoneBox>
                        }
                    </ReactDropzone>
                </Box>
            </Modal>

        );
    }
}

const DropZoneBox = styled(Box)`
    width: 100%;
    height: 100px; 
    border-width: 2px; 
    border-color: rgb(102, 102, 102); 
    border-style: dashed; 
    border-radius: 5px;
    margin: 16px 0 16px 0;

    & > p {
        margin: 16px;
    }
`;

const privacyOptions = [
    { text: "Private", value: "PRIVATE" },
    { text: "Confidential", value: "CONFIDENTIAL" },
    { text: "Sensitive", value: "SENSITIVE" }
]

const UploaderRow = (p: {
    upload: Upload,
    setSensitivity: (key: Sensitivity) => void,
    onExtractChange?: (value: boolean) => void,
    onUpload?: (e: React.MouseEvent<any>) => void,
    onDelete?: (e: React.MouseEvent<any>) => void,
    onAbort?: (e: React.MouseEvent<any>) => void
    onClear?: (e: React.MouseEvent<any>) => void
    setRewritePolicy?: (policy: UploadPolicy) => void
    onCheck?: (checked: boolean) => void
}) => {
    const fileTitle = <span><b>{p.upload.file.name}</b> ({sizeToString(p.upload.file.size)})<ConflictFile file={p.upload.conflictFile} /></span>;
    let body;

    if (!p.upload.isUploading) {
        body = <>
            <Box width={0.7}>
                <Spacer
                    left={fileTitle}
                    right={p.upload.conflictFile ? <PolicySelect setRewritePolicy={p.setRewritePolicy!} /> : null}
                />
                <br />
                {isArchiveExtension(p.upload.file.name) ?
                    <Flex>
                        <label>Extract archive?</label>
                        <Box ml="0.5em" />
                        <Toggle
                            checked={p.upload.extractArchive}
                            onChange={() => ifPresent(p.onExtractChange, c => c(!p.upload.extractArchive))}
                        />
                    </Flex> : null}
            </Box>
            <Box width={0.3}>
                <ButtonGroup width="100%">
                    <Button
                        color="green"
                        onClick={e => ifPresent(p.onUpload, c => c(e))}
                    ><Icon name="cloud upload" />Upload</Button>
                    <Button color="red" onClick={e => ifPresent(p.onDelete, c => c(e))}><Icon name="close" /></Button>
                </ButtonGroup>
                <Flex justifyContent="center" pt="0.3em">
                    <ClickableDropdown
                        chevron
                        trigger={prettierString(p.upload.sensitivity)}
                        onChange={key => p.setSensitivity(key as Sensitivity)}
                        options={privacyOptions}
                    />
                </Flex>
            </Box>
        </>;
    } else {
        body = <>
            <Box width={0.25}>
                {fileTitle}
                <br />
                {isArchiveExtension(p.upload.file.name) ?
                    (p.upload.extractArchive ?
                        <span><Icon name="checkmark" color="green" />Extracting archive</span> :
                        <span><Icon name="close" color="red" /> <i>Not</i> extracting archive</span>)
                    : null}
            </Box>

            <Box width={0.45} ml="0.5em" mr="0.5em" pl="0.5" pr="0.5">
                <Progress
                    active={p.upload.progressPercentage !== 100}
                    color="green"
                    label={`${p.upload.progressPercentage.toFixed(2)}% (${sizeToString(calculateSpeed(p.upload))}/s)`}
                    percent={p.upload.progressPercentage}
                />
            </Box>

            <Box width={0.22}>
                {!isFinishedUploading(p.upload.uploadXHR) ? <Button
                    fullWidth
                    color="red"
                    onClick={e => ifPresent(p.onAbort, c => c(e))}
                >Cancel</Button> : <Button
                    fullWidth
                    color="red"
                    onClick={e => ifPresent(p.onClear, c => c(e))}
                >
                        <Icon name="close" />
                    </Button>}
            </Box>
        </>;
    }

    return (
        <Flex flexDirection="row">
            <Box width={0.04} textAlign="center">
                <FileIcon fileIcon={iconFromFilePath(p.upload.file.name, "FILE", Cloud.homeFolder)} />
            </Box>
            <Flex width={0.96}>{body}</Flex>
        </Flex>
    );
}

interface PolicySelect { setRewritePolicy: (policy: UploadPolicy) => void }
const PolicySelect = ({ setRewritePolicy }: PolicySelect) =>
    <Flex mt="-12px" width="200px" mr="0.5em">
        <Select width="200px" defaultValue="Rename" onChange={e => setRewritePolicy(e.target.value.toUpperCase() as UploadPolicy)}>
            <option>Rename</option>
            <option>Overwrite</option>
        </Select>
    </Flex>

interface ConflictFile { file?: SDUCloudFile }
const ConflictFile = ({ file }: ConflictFile) => !!file ?
    <Box>File already exists in folder, {sizeToString(file.size)}</Box> : null;

const mapStateToProps = ({ files, uploader }: ReduxObject): any => ({
    activeUploads: uploader.uploads.filter(it => it.uploadXHR && it.uploadXHR.readyState !== XMLHttpRequest.DONE),
    location: files.path,
    visible: uploader.visible,
    allowMultiple: true,
    uploads: uploader.uploads,
    onFilesUploaded: uploader.onFilesUploaded,
    error: uploader.error,
    loading: uploader.loading
});

const mapDispatchToProps = (dispatch: Dispatch): UploadOperations => ({
    setUploads: uploads => dispatch(setUploads(uploads)),
    setUploaderError: err => dispatch(setUploaderError(err)),
    setUploaderVisible: visible => dispatch(setUploaderVisible(visible)),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect<UploaderProps, UploadOperations>(mapStateToProps, mapDispatchToProps)(Uploader);