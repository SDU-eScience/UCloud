import * as React from "react";
import * as Modal from "react-modal";
import {Text, Progress, Icon, Button, ButtonGroup, Heading, Divider, OutlineButton, Select} from "ui-components";
import Dropzone from "react-dropzone";
import {Cloud} from "Authentication/SDUCloudObject";
import {ifPresent, iconFromFilePath, prettierString, timestampUnixMs, is5xxStatusCode, errorMessageOrDefault, addTrailingSlash} from "UtilityFunctions";
import {sizeToString, archiveExtensions, isArchiveExtension, statFileQuery, replaceHomeFolder} from "Utilities/FileUtilities";
import {bulkUpload, multipartUpload, UploadPolicy} from "./api";
import {connect} from "react-redux";
import {ReduxObject, Sensitivity} from "DefaultObjects";
import {Upload, UploadOperations, UploaderProps, UploaderStateProps} from ".";
import {setUploaderVisible, setUploads, setUploaderError, setLoading} from "Uploader/Redux/UploaderActions";
import {removeEntry} from "Utilities/CollectionUtilities";
import {Box, Flex} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Toggle} from "ui-components/Toggle";
import styled from "styled-components";
import {TextSpan} from "ui-components/Text";
import {Dispatch} from "redux";
import {FileIcon, overwriteDialog} from "UtilityComponents";
import {Spacer} from "ui-components/Spacer";
import {File as SDUCloudFile} from "Files";
import {Refresh} from "Navigation/Header";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import {SnackType} from "Snackbar/Snackbars";
import Error from "ui-components/Error";
import {snackbarStore} from "Snackbar/SnackbarStore";

const uploadsFinished = (uploads: Upload[]): boolean => uploads.every((it) => isFinishedUploading(it.uploadXHR));
const finishedUploads = (uploads: Upload[]): number => uploads.filter((it) => isFinishedUploading(it.uploadXHR)).length;
const isFinishedUploading = (xhr?: XMLHttpRequest): boolean => !!xhr && xhr.readyState === XMLHttpRequest.DONE;

const newUpload = (file: File, location: string): Upload => ({
    file,
    conflictFile: undefined,
    resolution: UploadPolicy.RENAME,
    sensitivity: "INHERIT",
    isUploading: false,
    progressPercentage: 0,
    extractArchive: false,
    uploadXHR: undefined,
    uploadEvents: [],
    isPending: false,
    parentPath: location
});

const addProgressEvent = (upload: Upload, e: ProgressEvent) => {
    const now = timestampUnixMs();
    upload.uploadEvents = upload.uploadEvents.filter(e => now - e.timestamp < 10_000);
    upload.uploadEvents.push({timestamp: now, progressInBytes: e.loaded});
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

    private readonly MAX_CONCURRENT_UPLOADS = 5;

    private onFilesAdded = async (files: File[]): Promise<void> => {
        if (files.some(it => it.size === 0)) snackbarStore.addSnack({message: "It is not possible to upload empty files.", type: SnackType.Information});
        if (files.some(it => it.name.length > 1025)) snackbarStore.addSnack({message: "Filenames can't exceed a length of 1024 characters.", type: SnackType.Information});
        const filteredFiles = files.filter(it => it.size > 0 && it.name.length < 1025).map(it => newUpload(it, this.props.location));
        if (filteredFiles.length == 0) return;

        this.props.setLoading(true);
        const promises: ({request: XMLHttpRequest, response: SDUCloudFile} | {status: number, response: string})[] = await Promise.all(filteredFiles.map(file =>
            Cloud.get<SDUCloudFile>(statFileQuery(`${this.props.location}/${file.file.name}`)).then(it => it).catch(it => it)
        ));

        promises.forEach((it, index) => {
            if ("status" in it || is5xxStatusCode(it.request.status)) filteredFiles[index].error = errorMessageOrDefault(it, "Could not reach backend, try again later");
            else if (it.request.status === 200) filteredFiles[index].conflictFile = it.response;
        });

        if (this.props.allowMultiple !== false) { // true if no value
            this.props.setUploads(this.props.uploads.concat(filteredFiles))
        } else {
            this.props.setUploads([filteredFiles[0]])
        }
        this.props.setLoading(false);
    };

    private beforeUnload = (e: {returnValue: string;}) => {
        e.returnValue = "foo";
        const finished = finishedUploads(this.props.uploads);
        const total = this.props.uploads.length;
        snackbarStore.addSnack({
            message: `${finished} out of ${total} files uploaded`,
            type: SnackType.Information
        });
        return e;
    };

    private startPending() {
        const remainingAllowedUploads = this.MAX_CONCURRENT_UPLOADS - this.props.activeUploads.length;
        for (let i = 0; i < remainingAllowedUploads; i++) {
            const index = this.props.uploads.findIndex(it => it.isPending);
            if (index !== -1) this.startUpload(index);
        }
    }

    private onUploadFinished(upload: Upload, xhr: XMLHttpRequest) {
        xhr.onloadend = () => {
            if (uploadsFinished(this.props.uploads))
                window.removeEventListener("beforeunload", this.beforeUnload);
            if (!!this.props.onFilesUploaded && uploadsFinished(this.props.uploads))
                this.props.onFilesUploaded(this.props.location);
            this.props.setUploads(this.props.uploads);
            this.startPending();
        }
        upload.uploadXHR = xhr;
        this.props.setUploads(this.props.uploads);
    }

    private startUpload = (index: number) => {
        const upload = this.props.uploads[index];
        if (this.props.activeUploads.length === this.MAX_CONCURRENT_UPLOADS) {
            upload.isPending = true;
            return;
        }
        upload.isPending = false;
        upload.isUploading = true;
        this.props.setUploads(this.props.uploads);

        window.addEventListener("beforeunload", this.beforeUnload);

        const setError = (err?: string) => {
            this.props.uploads[index].error = err;
            this.props.setUploads(this.props.uploads);
        }

        if (!upload.extractArchive) {
            multipartUpload({
                location: `${upload.parentPath}/${upload.file.name}`,
                file: upload.file,
                sensitivity: upload.sensitivity,
                policy: upload.resolution,
                onProgress: e => {
                    addProgressEvent(upload, e);
                    this.props.setUploads(this.props.uploads);
                },
                onError: err => setError(err),
            }).then(xhr => this.onUploadFinished(upload, xhr))
                .catch(e => setError(errorMessageOrDefault(e, "An error occurred uploading the file")))
        } else {
            bulkUpload({
                location: upload.parentPath,
                file: upload.file,
                sensitivity: upload.sensitivity,
                policy: upload.resolution,
                onProgress: e => {
                    addProgressEvent(upload, e);
                    this.props.setUploads(this.props.uploads);
                },
                onError: err => setError(err),
            }).then(xhr => this.onUploadFinished(upload, xhr))
                .catch(e => setError(errorMessageOrDefault(e, "An error occurred uploading the file")))
        }
    }

    private startAllUploads = (event: {preventDefault: () => void}) => {
        event.preventDefault();
        this.props.uploads.forEach(it => {if (!it.uploadXHR) it.isPending = true});
        this.startPending();
    }

    private removeUpload = (index: number) => {
        const files = this.props.uploads.slice();
        if (index < files.length) {
            const remainderFiles = removeEntry(files, index);
            this.props.setUploads(remainderFiles);
            this.startPending();
        }
    }

    private abort = async (index: number) => {
        const upload = this.props.uploads[index];
        if (!!upload.uploadXHR && upload.uploadXHR.readyState != XMLHttpRequest.DONE) {
            if (upload.resolution === UploadPolicy.OVERWRITE) {
                const result = await overwriteDialog();
                if (result.cancelled) return;
            }
            upload.uploadXHR.abort();
            this.removeUpload(index);
            this.startPending();
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
        const {uploads} = this.props;
        uploads[index].resolution = policy;
        this.props.setUploads(uploads);
    }

    render() {
        const {uploads, ...props} = this.props;
        return (
            <Modal isOpen={props.visible} shouldCloseOnEsc ariaHideApp={false} onRequestClose={() => this.props.setUploaderVisible(false)}
                style={this.modalStyle}
            >
                <div data-tag={"uploadModal"}>
                    <Spacer
                        left={<Heading>Upload Files</Heading>}
                        right={props.loading ? <Refresh onClick={() => undefined} spin /> : null}
                    />
                    <Divider />
                    {finishedUploads(uploads) > 0 ? (<OutlineButton mt="4px" mb="4px" color="green" fullWidth onClick={() => this.clearFinishedUploads()}>
                        Clear finished uploads
                </OutlineButton>) : null}
                    {uploads.filter(it => !it.isUploading).length >= 5 ?
                        <OutlineButton color="blue" fullWidth mt="4px" mb="4px" onClick={() => this.props.setUploads(uploads.filter(it => it.isUploading))}>
                            Clear unstarted uploads
                    </OutlineButton> : null}
                    <Box>
                        {uploads.map((upload, index) => (
                            <React.Fragment key={index}>
                                <UploaderRow
                                    location={props.location}
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
                        {uploads.filter(it => !it.isUploading).length > 1 && uploads.filter(it => !it.conflictFile).length ?
                            <Button fullWidth color="green" onClick={this.startAllUploads}>
                                <Icon name={"upload"} />{" "}Start all!</Button> : null}
                        <Dropzone onDrop={this.onFilesAdded}>
                            {({getRootProps, getInputProps}) =>
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
                        </Dropzone>
                    </Box>
                </div>
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
    {text: "Inherit", value: "INHERIT"},
    {text: "Private", value: "PRIVATE"},
    {text: "Confidential", value: "CONFIDENTIAL"},
    {text: "Sensitive", value: "SENSITIVE"}
]

const UploaderRow = (p: {
    upload: Upload,
    location: string,
    setSensitivity: (key: Sensitivity) => void,
    onExtractChange?: (value: boolean) => void,
    onUpload?: (e: React.MouseEvent<any>) => void,
    onDelete?: (e: React.MouseEvent<any>) => void,
    onAbort?: (e: React.MouseEvent<any>) => void
    onClear?: (e: React.MouseEvent<any>) => void
    setRewritePolicy?: (policy: UploadPolicy) => void
    onCheck?: (checked: boolean) => void
}) => {

    let fileInfo = p.location !== p.upload.parentPath ? (<Dropdown>
        <Icon style={{pointer: "cursor"}} ml="10px" name="info" color="white" color2="black" />
        <DropdownContent width="auto" visible colorOnHover={false} color="white" backgroundColor="black">
            Will be uploaded to: {addTrailingSlash(replaceHomeFolder(p.location, Cloud.homeFolder))}{p.upload.file.name}
        </DropdownContent>
    </Dropdown>) : null;

    const fileTitle = <span><b>{p.upload.file.name}</b> ({sizeToString(p.upload.file.size)}){fileInfo}<ConflictFile file={p.upload.conflictFile} /></span>;
    let body;
    if (!!p.upload.error) {
        body = <>
            <Box width={0.5}>
                {fileTitle}
            </Box>
            <Spacer pr="4px" width={0.5}
                left={<Text color="red">{p.upload.error}</Text>}
                right={<Button color="red" onClick={e => ifPresent(p.onDelete, c => c(e))} data-tag={"removeUpload"}>
                    <Icon name="close" />
                </Button>}
            />
        </>;
    } else if (!p.upload.isUploading) {
        body = <>
            <Box width={0.7}>
                <Spacer
                    left={fileTitle}
                    right={p.upload.conflictFile ? <PolicySelect setRewritePolicy={p.setRewritePolicy!} /> : null}
                />
                <br />
                {isArchiveExtension(p.upload.file.name) ?
                    <Flex data-tag="extractArchive">
                        <label>Extract archive?</label>
                        <Box ml="0.5em" />
                        <Toggle
                            scale={1.3}
                            checked={p.upload.extractArchive}
                            onChange={() => ifPresent(p.onExtractChange, c => c(!p.upload.extractArchive))}
                        />
                    </Flex> : null}
            </Box>
            <Error error={p.upload.error} />
            <Box width={0.3}>
                <ButtonGroup width="100%">
                    {!p.upload.isPending ?
                        <Button
                            data-tag={"startUpload"}
                            color="green"
                            disabled={!!p.upload.error}
                            onClick={e => ifPresent(p.onUpload, c => c(e))}
                        >
                            <Icon name="cloud upload" />Upload
                        </Button>
                        :
                        <Button color="blue" disabled>Pending</Button>
                    }
                    <Button color="red" onClick={e => ifPresent(p.onDelete, c => c(e))} data-tag={"removeUpload"}>
                        <Icon name="close" />
                    </Button>
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
    } else { // Uploading
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
            <ProgressBar upload={p.upload} />
            <Box width={0.22}>
                {!isFinishedUploading(p.upload.uploadXHR) ?
                    <Button
                        fullWidth
                        color="red"
                        onClick={e => ifPresent(p.onAbort, c => c(e))}
                        data-tag={"cancelUpload"}
                    >
                        Cancel
                    </Button>
                    :
                    <Button
                        fullWidth
                        color="red"
                        onClick={e => ifPresent(p.onClear, c => c(e))}
                        data-tag={"removeUpload"}
                    >
                        <Icon name="close" />
                    </Button>}
            </Box>
        </>;
    }

    return (
        <Flex flexDirection="row" data-tag={"uploadRow"}>
            <Box width={0.04} textAlign="center">
                <FileIcon fileIcon={iconFromFilePath(p.upload.file.name, "FILE", Cloud.homeFolder)} />
            </Box>
            <Flex width={0.96}>{body}</Flex>
        </Flex>
    );
};

const ProgressBar = ({upload}: {upload: Upload}) => (
    <Box width={0.45} ml="0.5em" mr="0.5em" pl="0.5" pr="0.5">
        <Progress
            active={upload.progressPercentage !== 100}
            color="green"
            label={`${upload.progressPercentage.toFixed(2)}% (${sizeToString(calculateSpeed(upload))}/s)`}
            percent={upload.progressPercentage}
        />
    </Box>
);

interface PolicySelect {setRewritePolicy: (policy: UploadPolicy) => void}
const PolicySelect = ({setRewritePolicy}: PolicySelect) =>
    <Flex mt="-12px" width="200px" mr="0.5em">
        <Select width="200px" defaultValue="Rename" onChange={e => setRewritePolicy(e.target.value.toUpperCase() as UploadPolicy)}>
            <option>Rename</option>
            <option>Overwrite</option>
        </Select>
    </Flex>;

interface ConflictFile {file?: SDUCloudFile}
const ConflictFile = ({file}: ConflictFile) => !!file ?
    <Box>File already exists in folder, {sizeToString(file.size!)}</Box> : null;

const mapStateToProps = ({uploader}: ReduxObject): UploaderStateProps => ({
    activeUploads: uploader.uploads.filter(it => it.uploadXHR && it.uploadXHR.readyState !== XMLHttpRequest.DONE),
    location: uploader.path,
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
    setUploaderVisible: visible => dispatch(setUploaderVisible(visible, Cloud.homeFolder)),
    setLoading: loading => dispatch(setLoading(loading)),
});

export default connect<UploaderStateProps, UploadOperations>(mapStateToProps, mapDispatchToProps)(Uploader);
