import {Client} from "Authentication/HttpClientInstance";
import {ReduxObject, Sensitivity} from "DefaultObjects";
import {File as CloudFile} from "Files";
import {Refresh} from "Navigation/Header";
import * as React from "react";
import Dropzone from "react-dropzone";
import * as Modal from "react-modal";
import {connect} from "react-redux";
import {RouteComponentProps, withRouter} from "react-router";
import {Dispatch} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {
    Button,
    ButtonGroup,
    Divider,
    Heading,
    Icon,
    OutlineButton,
    Progress,
    Select,
    Text,
    Truncate
} from "ui-components";
import {Box, Flex} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import Error from "ui-components/Error";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import {Toggle} from "ui-components/Toggle";
import {setLoading, setUploaderError, setUploaderVisible, setUploads} from "Uploader/Redux/UploaderActions";
import {removeEntry} from "Utilities/CollectionUtilities";
import {
    archiveExtensions, getParentPath,
    isArchiveExtension, replaceHomeOrProjectFolder,
    resolvePath, sizeToString, statFileQuery
} from "Utilities/FileUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {FileIcon, overwriteDialog} from "UtilityComponents";
import {
    addTrailingSlash,
    errorMessageOrDefault,
    iconFromFilePath,
    ifPresent,
    is5xxStatusCode,
    prettierString,
    timestampUnixMs
} from "UtilityFunctions";
import {Upload, UploaderProps, UploaderStateProps, UploadOperations} from ".";
import {bulkUpload, multipartUpload, UploadPolicy} from "./api";

const uploadsFinished = (uploads: Upload[]): boolean => uploads.every((it) => isFinishedUploading(it.uploadXHR));
const finishedUploads = (uploads: Upload[]): number => uploads.filter((it) => isFinishedUploading(it.uploadXHR)).length;
const isFinishedUploading = (xhr?: XMLHttpRequest): boolean => !!xhr && xhr.readyState === XMLHttpRequest.DONE;

export const newUpload = (file: File, path: string): Upload => ({
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
    path,
    uploadSize: 1
});

const addProgressEvent = (upload: Upload, e: ProgressEvent): void => {
    const now = timestampUnixMs();
    upload.uploadEvents = upload.uploadEvents.filter(evt => now - evt.timestamp < 10_000);
    upload.uploadEvents.push({timestamp: now, progressInBytes: e.loaded});
    upload.progressPercentage = (e.loaded / e.total) * 100;
    upload.uploadSize = e.total;
};

export function calculateUploadSpeed(upload: Upload): number {
    if (upload.uploadEvents.length === 0) return 0;

    const min = upload.uploadEvents[0];
    const max = upload.uploadEvents[upload.uploadEvents.length - 1];

    const timespan = max.timestamp - min.timestamp;
    const bytesTransferred = max.progressInBytes - min.progressInBytes;

    if (timespan === 0) return 0;
    return (bytesTransferred / timespan) * 1000;
}

/* NOTE! Changing this to a functional component causes issues.
    The onProgress inside `startUpload` seems to cause issues, as it doesn't re-evalute `props.uploads`,
    overwriting uploads wrongly.
*/
interface UploaderState {
    finishedUploadPaths: Set<string>;
}

class Uploader extends React.Component<UploaderProps & RouteComponentProps, UploaderState> {

    public state = {
        finishedUploadPaths: new Set<string>()
    };

    // Otherwise {this} gets overwritten by the handler and we can't access the props
    private boundBeforeUnload = this.beforeUnload.bind(this);

    private readonly MAX_CONCURRENT_UPLOADS = 5;

    private readonly modalStyle = {
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

    public render(): JSX.Element {
        const {uploads} = this.props;
        return (
            <Modal
                isOpen={this.props.visible}
                shouldCloseOnEsc
                ariaHideApp={false}
                onRequestClose={this.closeModal}
                style={this.modalStyle}
            >
                <div data-tag={"uploadModal"}>
                    <Spacer
                        left={<Heading>Upload Files</Heading>}
                        right={(
                            <>
                                {this.props.loading ? <Refresh spin /> : null}
                                <Icon
                                    name="close"
                                    cursor="pointer"
                                    data-tag="modalCloseButton"
                                    onClick={this.closeModal}
                                />
                            </>
                        )}
                    />
                    <Divider />
                    {finishedUploads(uploads) > 0 ? (
                        <OutlineButton
                            mt="4px"
                            mb="4px"
                            color="green"
                            fullWidth
                            onClick={this.clearFinishedUploads}
                        >
                            Clear finished uploads
                        </OutlineButton>
                    ) : null}
                    {uploads.filter(it => !it.isUploading).length < 5 ? null : (
                        <OutlineButton
                            color="blue"
                            fullWidth
                            mt="4px"
                            mb="4px"
                            onClick={() => this.props.setUploads(uploads.filter(it => it.isUploading))}
                        >
                            Clear unstarted uploads
                        </OutlineButton>
                    )}
                    <div>
                        {uploads.map((upload, index) => (
                            <React.Fragment key={index}>
                                <UploaderRow
                                    location={this.props.path}
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
                        {uploads.filter(it => !it.isUploading).length > 1 &&
                            uploads.filter(it => !it.conflictFile).length ? (
                                <Button fullWidth color="green" onClick={this.startAllUploads}>
                                    <Icon name={"upload"} />{" "}Start all!
                                </Button>
                            ) : null}
                        <Dropzone onDrop={this.onFilesAdded}>
                            {({getRootProps, getInputProps}) => (
                                <DropZoneBox {...getRootProps()}>
                                    <input {...getInputProps()} />
                                    <p>
                                        <TextSpan mr="0.5em"><Icon name="upload" /></TextSpan>
                                        <TextSpan mr="0.3em">Drop files here or </TextSpan><a href="#">browse</a>
                                    </p>
                                    <p>
                                        <b>Bulk upload</b> supported for file types:
                                        <i><code>{archiveExtensions.join(", ")}</code></i>
                                    </p>
                                </DropZoneBox>
                            )}
                        </Dropzone>
                    </div>
                </div>
            </Modal>
        );
    }

    private checkForDuplicates(files: File[]): string[] {
        const uploadNames = this.props.uploads.map(it => it.file.name);
        const newFiles = files.map(it => it.name);
        const duplicates: string[] = [];
        for (const name of newFiles) {
            if (uploadNames.includes(name)) {
                duplicates.push(name);
            }
        }
        return duplicates;
    }

    private onFilesAdded = async (files: File[]): Promise<void> => {
        if (files.some(it => it.size === 0)) {
            snackbarStore.addSnack({
                message: "It is not possible to upload empty files.",
                type: SnackType.Information
            });
        }

        if (files.some(it => it.name.length > 1025)) {
            snackbarStore.addSnack({
                message: "Filenames can't exceed a length of 1024 characters.",
                type: SnackType.Information
            });
        }

        const duplicates = this.checkForDuplicates(files);

        if (duplicates.length > 0) {
            snackbarStore.addSnack({
                message: `You are already added files ${duplicates.join(", ")}`,
                type: SnackType.Information
            });
        }

        const filteredFiles = files
            .filter(it => it.size > 0 && it.name.length < 1025)
            .map(it => newUpload(it, this.props.path + `/${it.name}`));

        if (filteredFiles.length === 0) return;

        this.props.setLoading(true);
        type PromiseType = ({request: XMLHttpRequest; response: CloudFile} | {status: number; response: string});
        const promises: PromiseType[] = await Promise.all(filteredFiles.map(file =>
            Client
                .get<CloudFile>(statFileQuery(`${this.props.path}/${file.file.name}`))
                .then(it => it)
                .catch(it => it)
        ));

        promises.forEach((it, index) => {
            if ("status" in it || is5xxStatusCode(it.request.status))
                filteredFiles[index].error = errorMessageOrDefault(it, "Could not reach backend, try again later");
            else if (it.request.status === 200) filteredFiles[index].conflictFile = it.response;
        });

        if (this.props.allowMultiple !== false) { // true if no value
            this.props.setUploads(this.props.uploads.concat(filteredFiles));
        } else {
            this.props.setUploads([filteredFiles[0]]);
        }
        this.props.setLoading(false);
    };

    private beforeUnload(e: {returnValue: string}): {returnValue: string} {
        e.returnValue = "foo";
        const finished = finishedUploads(this.props.uploads);
        const total = this.props.uploads.length;
        snackbarStore.addSnack({
            message: `${finished} out of ${total} files uploaded`,
            type: SnackType.Information
        });
        return e;
    }

    private startPending(): void {
        const remainingAllowedUploads = this.MAX_CONCURRENT_UPLOADS - this.props.activeUploads.length;
        for (let i = 0; i < remainingAllowedUploads; i++) {
            const index = this.props.uploads.findIndex(it => it.isPending);
            if (index !== -1) this.startUpload(index);
        }
    }

    private onUploadFinished(upload: Upload, xhr: XMLHttpRequest): void {
        xhr.onloadend = () => {
            if (uploadsFinished(this.props.uploads))
                window.removeEventListener("beforeunload", this.boundBeforeUnload);
            this.props.setUploads(this.props.uploads);
            this.startPending();
        };
        this.state.finishedUploadPaths.add(getParentPath(upload.path));
        upload.uploadXHR = xhr;
        this.props.setUploads(this.props.uploads);
    }

    private startUpload(index: number): void {
        const upload = this.props.uploads[index];
        if (this.props.activeUploads.length === this.MAX_CONCURRENT_UPLOADS) {
            upload.isPending = true;
            return;
        }
        upload.isPending = false;
        upload.isUploading = true;
        this.props.setUploads(this.props.uploads);

        window.addEventListener("beforeunload", this.boundBeforeUnload);

        const setError = (err?: string): void => {
            this.props.uploads[index].error = err;
            this.props.setUploads(this.props.uploads);
        };

        const uploadParams = {
            file: upload.file,
            sensitivity: upload.sensitivity,
            policy: upload.resolution,
            onProgress: (e: ProgressEvent) => {
                addProgressEvent(upload, e);
                this.props.setUploads(this.props.uploads);
            },
            onError: (err: string) => setError(err),
        };

        if (!upload.extractArchive) {
            multipartUpload({
                location: upload.path,
                ...uploadParams
            }).then(xhr => this.onUploadFinished(upload, xhr))
                .catch(e => setError(errorMessageOrDefault(e, "An error occurred uploading the file")));
        } else {
            bulkUpload({
                location: getParentPath(upload.path),
                ...uploadParams
            }).then(xhr => this.onUploadFinished(upload, xhr))
                .catch(e => setError(errorMessageOrDefault(e, "An error occurred uploading the file")));
        }
    }

    private startAllUploads = (event: {preventDefault: () => void}): void => {
        event.preventDefault();
        this.props.uploads.forEach(it => {
            if (!it.uploadXHR) it.isPending = true;
        });
        this.startPending();
    };

    private removeUpload = (index: number): void => {
        const files = this.props.uploads.slice();
        if (index < files.length) {
            const remainderFiles = removeEntry(files, index);
            this.props.setUploads(remainderFiles);
            this.startPending();
        }
    };

    private async abort(index: number): Promise<void> {
        const upload = this.props.uploads[index];
        if (!!upload.uploadXHR && upload.uploadXHR.readyState !== XMLHttpRequest.DONE) {
            if (upload.resolution === UploadPolicy.OVERWRITE) {
                const result = await overwriteDialog();
                if (result.cancelled) return;
            }
            upload.uploadXHR.abort();
            this.removeUpload(index);
            this.startPending();
        }
    }

    private onExtractChange(index: number, value: boolean): void {
        this.props.uploads[index].extractArchive = value;
        this.props.setUploads(this.props.uploads);
    }

    private updateSensitivity(index: number, sensitivity: Sensitivity): void {
        this.props.uploads[index].sensitivity = sensitivity;
        this.props.setUploads(this.props.uploads);
    }

    private clearUpload(index: number): void {
        this.props.setUploads(removeEntry(this.props.uploads, index));
    }

    private clearFinishedUploads = (): void => {
        this.props.setUploads(this.props.uploads.filter(it => !isFinishedUploading(it.uploadXHR)));
    };

    private setRewritePolicy(index: number, policy: UploadPolicy): void {
        this.props.uploads[index].resolution = policy;
        this.props.setUploads(this.props.uploads);
    }

    private closeModal = (): void => {
        this.props.setUploaderVisible(false);
        if (finishedUploads(this.props.uploads) !== this.props.uploads.length || this.props.uploads.length === 0) {
            return;
        }
        const path = getQueryParamOrElse(this.props, "path", "");
        if ([...this.state.finishedUploadPaths].map(it => addTrailingSlash(it)).includes(addTrailingSlash(path))) {
            this.props.parentRefresh();
        }
    };
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

const privacyOptions: Array<{text: string; value: Sensitivity}> = [
    {text: "Inherit", value: "INHERIT"},
    {text: "Private", value: "PRIVATE"},
    {text: "Confidential", value: "CONFIDENTIAL"},
    {text: "Sensitive", value: "SENSITIVE"}
];

const UploaderRow = (p: {
    upload: Upload;
    location: string;
    setSensitivity: (key: Sensitivity) => void;
    onExtractChange?: (value: boolean) => void;
    onUpload?: (e: React.MouseEvent<any>) => void;
    onDelete?: (e: React.MouseEvent<any>) => void;
    onAbort?: (e: React.MouseEvent<any>) => void;
    onClear?: (e: React.MouseEvent<any>) => void;
    setRewritePolicy?: (policy: UploadPolicy) => void;
    onCheck?: (checked: boolean) => void;
}): JSX.Element => {
    const fileInfo = resolvePath(p.location) === resolvePath(getParentPath(p.upload.path)) ? null : (
        <Dropdown>
            <Icon cursor="pointer" ml="10px" name="info" color="white" color2="black" />
            <DropdownContent width="auto" visible colorOnHover={false} color="white" backgroundColor="black">
                Will be uploaded
                to: {replaceHomeOrProjectFolder(p.upload.path, Client)}
            </DropdownContent>
        </Dropdown>
    );

    const fileTitle = (
        <Box>
            <Truncate
                title={p.upload.file.name}
                width={["320px", "320px", "320px", "320px", "440px", "560px"]}
                mb="-4px"
                fontSize={20}
            >
                {p.upload.file.name}
            </Truncate>
            ({sizeToString(p.upload.file.size)}){fileInfo}<ConflictFile file={p.upload.conflictFile} />
        </Box>
    );
    let body: React.ReactNode;
    if (p.upload.error) {
        body = (
            <>
                <Box width={"50%"}>
                    {fileTitle}
                </Box>
                <Spacer
                    pr="4px"
                    width={0.5}
                    left={(<Text color="red">{p.upload.error}</Text>)}
                    right={(
                        <Button
                            color="red"
                            onClick={e => ifPresent(p.onDelete, c => c(e))}
                            data-tag="removeUpload"
                        >
                            <Icon name="close" />
                        </Button>
                    )}
                />
            </>
        );
    } else if (!p.upload.isUploading) {
        body = (
            <>
                <Box width="80%">
                    <Spacer
                        left={fileTitle}
                        right={p.upload.conflictFile ? <PolicySelect setRewritePolicy={p.setRewritePolicy!} /> : null}
                    />
                    <br />
                    {!isArchiveExtension(p.upload.file.name) ? null : (
                        <Flex data-tag="extractArchive">
                            <label>Extract archive?</label>
                            <Box ml="0.5em" />
                            <Toggle
                                scale={1.3}
                                checked={p.upload.extractArchive}
                                onChange={() => ifPresent(p.onExtractChange, c => c(!p.upload.extractArchive))}
                            />
                        </Flex>
                    )}
                </Box>
                <Error error={p.upload.error} />
                <Box width="165px">
                    <UploaderButtonGroup width="165px">
                        {p.upload.isPending ? <Button color="blue" disabled>Pending</Button> : (
                            <Button
                                data-tag="startUpload"
                                color="green"
                                disabled={!!p.upload.error}
                                onClick={e => ifPresent(p.onUpload, c => c(e))}
                            >
                                <Icon name="upload" />Upload
                            </Button>)}
                        <Button color="red" onClick={e => ifPresent(p.onDelete, c => c(e))} data-tag="removeUpload">
                            <Icon name="close" />
                        </Button>
                    </UploaderButtonGroup>
                    <Flex justifyContent="center">
                        <ClickableDropdown
                            width="150px"
                            chevron
                            trigger={`Sensitivity: ${prettierString(p.upload.sensitivity)}`}
                            onChange={p.setSensitivity}
                            options={privacyOptions}
                        />
                    </Flex>
                </Box>
            </>
        );
    } else { // Uploading
        body = (
            <>
                <Box width="100%">
                    {fileTitle}
                    <br />
                    {isArchiveExtension(p.upload.file.name) ?
                        (p.upload.extractArchive ?
                            <span><Icon name="check" color="green" />Extracting archive</span> :
                            <span><Icon name="close" color="red" /> <i>Not</i> extracting archive</span>)
                        : null}
                </Box>
                <ProgressBar upload={p.upload} />
                <Box width={0.22}>
                    {!isFinishedUploading(p.upload.uploadXHR) ? (
                        <Button
                            fullWidth
                            color="red"
                            onClick={e => ifPresent(p.onAbort, c => c(e))}
                            data-tag={"cancelUpload"}
                        >
                            Cancel
                        </Button>
                    ) : (
                            <Button
                                fullWidth
                                color="red"
                                onClick={e => ifPresent(p.onClear, c => c(e))}
                                data-tag={"removeUpload"}
                            >
                                <Icon name="close" />
                            </Button>
                        )}
                </Box>
            </>
        );
    }

    return (
        <Flex flexDirection="row" data-tag={"uploadRow"}>
            <Box width={0.04} textAlign="center">
                <FileIcon fileIcon={iconFromFilePath(p.upload.file.name, "FILE", Client)} />
            </Box>
            <Flex width="100%">{body}</Flex>
        </Flex>
    );
};

const UploaderButtonGroup = styled(ButtonGroup)`
    & > ${Button}:last-child, .last {
        width: 40px;
    }

    & > ${Button}:first-child, .first {
        width: 115px;
    }
`;

const ProgressBar = ({upload}: {upload: Upload}): JSX.Element => (
    <Box width={0.45} ml="0.5em" mr="0.5em" pl="0.5" pr="0.5">
        <Progress
            active={upload.progressPercentage !== 100}
            color="green"
            label={`${upload.progressPercentage.toFixed(2)}% (${sizeToString(calculateUploadSpeed(upload))}/s)`}
            percent={upload.progressPercentage}
        />
    </Box>
);

interface PolicySelect {
    setRewritePolicy: (policy: UploadPolicy) => void;
}

const PolicySelect = ({setRewritePolicy}: PolicySelect): JSX.Element => (
    <Flex mt="-38px" width="150px" mr="0.5em">
        <Select
            width="200px"
            defaultValue="Rename"
            onChange={e => setRewritePolicy(e.target.value.toUpperCase() as UploadPolicy)}
        >
            <option>Rename</option>
            <option>Overwrite</option>
        </Select>
    </Flex>
);

interface ConflictFile {
    file?: CloudFile;
}

const ConflictFile = ({file}: ConflictFile): JSX.Element | null => !file ? null :
    <div>File already exists in folder, {sizeToString(file.size!)}</div>;

const mapStateToProps = ({uploader}: ReduxObject): UploaderStateProps => ({
    activeUploads: uploader.uploads.filter(it => it.uploadXHR && it.uploadXHR.readyState !== XMLHttpRequest.DONE),
    path: uploader.path,
    visible: uploader.visible,
    allowMultiple: true,
    uploads: uploader.uploads,
    error: uploader.error,
    loading: uploader.loading,
    parentRefresh: uploader.onFilesUploaded
});

const mapDispatchToProps = (dispatch: Dispatch): UploadOperations => ({
    setUploads: uploads => dispatch(setUploads(uploads)),
    setUploaderError: err => dispatch(setUploaderError(err)),
    setUploaderVisible: visible => dispatch(setUploaderVisible(visible, Client.homeFolder)),
    setLoading: loading => dispatch(setLoading(loading)),
});

export default connect<UploaderStateProps, UploadOperations>(mapStateToProps, mapDispatchToProps)(withRouter(Uploader));
