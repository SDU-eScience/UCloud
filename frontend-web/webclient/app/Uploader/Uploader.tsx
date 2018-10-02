import * as React from "react";
import { Checkbox, Progress, Grid, Card, Button, Icon, Modal } from "semantic-ui-react";
import * as Dropzone from "react-dropzone/dist/index";
import { Cloud } from "Authentication/SDUCloudObject";
import { ifPresent, iconFromFilePath, infoNotification, uploadsNotifications } from "UtilityFunctions";
import { fileSizeToString } from "Utilities/FileUtilities";
import { bulkUpload, multipartUpload, BulkUploadPolicy } from "./api";
import { connect } from "react-redux";
import { ReduxObject } from "DefaultObjects";
import { Upload, UploaderProps } from ".";
import { setUploaderVisible, setUploads } from "Uploader/Redux/UploaderActions";
import { removeEntry } from "Utilities/CollectionUtilities";

const uploadsFinished = (uploads: Upload[]): boolean => uploads.every((it) => isFinishedUploading(it.uploadXHR));
const finishedUploads = (uploads: Upload[]): number => uploads.filter((it) => isFinishedUploading(it.uploadXHR)).length;
const isFinishedUploading = (xhr?: XMLHttpRequest): boolean => !!xhr && xhr.readyState === XMLHttpRequest.DONE;

const newUpload = (file: File): Upload => ({
    file,
    isUploading: false,
    progressPercentage: 0,
    extractArchive: false,
    uploadXHR: undefined
});

class Uploader extends React.Component<UploaderProps> {
    constructor(props) {
        super(props);
    }

    onFilesAdded = (files: File[]) => {
        if (files.some(it => it.size === 0)) infoNotification("It is not possible to upload empty files.");
        const filteredFiles = files.filter(it => it.size > 0).map(it => newUpload(it));
        if (filteredFiles.length == 0) return;
        if (this.props.allowMultiple !== false) { // true if no value
            this.props.dispatch(setUploads(this.props.uploads.concat(filteredFiles)))
        } else {
            this.props.dispatch(setUploads([filteredFiles[0]]))
        }
    }

    beforeUnload = (e) => {
        e.returnValue = "foo";
        uploadsNotifications(finishedUploads(this.props.uploads), this.props.uploads.length)
        return e;
    }

    startUpload = (index: number) => {
        const upload = this.props.uploads[index];
        upload.isUploading = true;
        this.props.dispatch(setUploads(this.props.uploads));
        const onThen = (xhr: XMLHttpRequest) => {
            xhr.onloadend = () => {
                if (!!this.props.onFilesUploaded && uploadsFinished(this.props.uploads)) {
                    window.removeEventListener("beforeunload", this.beforeUnload);
                    this.props.onFilesUploaded(this.props.location);
                }
            }
            upload.uploadXHR = xhr;
            this.props.dispatch(setUploads(this.props.uploads));
        };

        window.addEventListener("beforeunload", this.beforeUnload);
        if (!upload.extractArchive) {
            multipartUpload(`${this.props.location}/${upload.file.name}`, upload.file, e => {
                upload.progressPercentage = (e.loaded / e.total) * 100;
                this.props.dispatch(setUploads(this.props.uploads));
            }).then(xhr => onThen(xhr)); // FIXME Add error handling
        } else {
            bulkUpload(this.props.location, upload.file, BulkUploadPolicy.OVERWRITE, e => {
                upload.progressPercentage = (e.loaded / e.total) * 100;
                this.props.dispatch(setUploads(this.props.uploads));
            }).then(xhr => onThen(xhr)); // FIXME Add error handling
        }
    }

    startAllUploads = (event) => {
        event.preventDefault();
        const length = this.props.uploads.length;
        for (let i = 0; i < length; i++) {
            this.startUpload(i);
        }
    }

    removeUpload = (index: number) => {
        const files = this.props.uploads.slice();
        if (index < files.length) {
            const remainderFiles = removeEntry(files, index);
            this.props.dispatch(setUploads(remainderFiles));
        }
    }

    abort = (index: number) => {
        const upload = this.props.uploads[index];
        if (!!upload.uploadXHR && upload.uploadXHR.readyState != XMLHttpRequest.DONE) {
            upload.uploadXHR.abort();
            this.removeUpload(index);
        }
    }

    onExtractChange = (index: number, value: boolean) => {
        const uploads = this.props.uploads;
        uploads[index].extractArchive = value;
        this.props.dispatch(setUploads(uploads));
    }

    render() {
        return (
            <Modal open={this.props.visible} onClose={() => this.props.dispatch(setUploaderVisible(false))}>
                <Modal.Header content="Upload Files" />
                <Modal.Content scrolling>
                    <Modal.Description>
                        <div>
                            {this.props.uploads.map((upload, index) => (
                                <UploaderRow
                                    key={index}
                                    {...upload}
                                    onExtractChange={value => this.onExtractChange(index, value)}
                                    onUpload={() => this.startUpload(index)}
                                    onDelete={it => { it.preventDefault(); this.removeUpload(index) }}
                                    onAbort={it => { it.preventDefault(); this.abort(index) }}
                                />
                            ))}

                            {this.props.uploads.filter(it => !it.isUploading).length > 1 ?
                                <Button
                                    fluid
                                    positive
                                    icon="cloud upload"
                                    content="Start all!"
                                    className="start-all-btn"
                                    onClick={this.startAllUploads}
                                />
                                : null}

                            <Dropzone className="dropzone" onDrop={this.onFilesAdded}>
                                <p>
                                    <Icon name="cloud upload" />
                                    Drop files here or <a href="#" onClick={e => e.preventDefault()}>browse</a>
                                </p>
                                <p>
                                    <b>Bulk upload</b> supported for file types: <i><code>{archiveExtensions.join(", ")}</code></i>
                                </p>
                            </Dropzone>
                        </div>
                    </Modal.Description>
                </Modal.Content>
            </Modal>

        );
    }
}

const UploaderRow = (p: {
    file: File,
    extractArchive: boolean,
    isUploading: boolean,
    progressPercentage: number,
    uploadXHR?: XMLHttpRequest,
    onExtractChange?: (value: boolean) => void,
    onUpload?: (e: React.MouseEvent<any>) => void,
    onDelete?: (e: React.MouseEvent<any>) => void,
    onAbort?: (e: React.MouseEvent<any>) => void
}) => {
    const fileTitle = <span><b>{p.file.name}</b> ({fileSizeToString(p.file.size)})</span>;
    let body;

    if (!p.isUploading) {
        body = <>
            <Grid.Column width={11}>
                {fileTitle}
                <br />
                {
                    isArchiveExtension(p.file.name) ?
                        <Checkbox
                            toggle
                            label="Extract archive"
                            checked={p.extractArchive}
                            onChange={() => ifPresent(p.onExtractChange, c => c(!p.extractArchive))}
                        />
                        : null
                }
            </Grid.Column>
            <Grid.Column width={4}>
                <Button.Group fluid>
                    <Button
                        positive
                        icon="cloud upload"
                        content="Upload"
                        onClick={e => ifPresent(p.onUpload, c => c(e))}
                    />

                    <Button
                        icon="close"
                        onClick={e => ifPresent(p.onDelete, c => c(e))}
                    />
                </Button.Group>
            </Grid.Column>
        </>;
    } else {
        body = <>
            <Grid.Column width={4}>
                {fileTitle}
                <br />
                {
                    isArchiveExtension(p.file.name) ?
                        (p.extractArchive ?
                            <span><Icon name="checkmark" color="green" />Extracting archive</span> :
                            <span><Icon name="close" color="red" /> <i>Not</i> extracting archive</span>)
                        : null
                }
            </Grid.Column>

            <Grid.Column width={8}>
                <Progress
                    color="green"
                    indicating
                    total={100}
                    success={p.progressPercentage === 100}
                    value={p.progressPercentage}
                    content={`${p.progressPercentage.toFixed(2)}%`}
                />
            </Grid.Column>

            <Grid.Column width={3}>
                <Button icon="close" content="Cancel" disabled={isFinishedUploading(p.uploadXHR)} fluid negative onClick={(e) => ifPresent(p.onAbort, c => c(e))} />
            </Grid.Column>
        </>;
    }

    return <Card fluid>
        <Card.Content>
            <Grid divided stackable>
                <Grid.Column width={1}>
                    <Icon size="large" name={iconFromFilePath(p.file.name, "FILE", Cloud.homeFolder)} />
                </Grid.Column>
                {body}
            </Grid>
        </Card.Content>
    </Card>;
}

const archiveExtensions: string[] = [".tar.gz"]
const isArchiveExtension = (fileName: string): boolean => archiveExtensions.some(it => fileName.endsWith(it));

interface UploaderStateToProps {
    visible: boolean
    location: string
}

const mapStateToProps = ({ files, uploader }: ReduxObject): any => ({
    activeUploads: uploader.uploads.filter(it => it.uploadXHR && it.uploadXHR.readyState !== XMLHttpRequest.DONE),
    location: files.path,
    visible: uploader.visible,
    allowMultiple: true,
    uploads: uploader.uploads,
    onFilesUploaded: uploader.onFilesUploaded
});

export default connect(mapStateToProps)(Uploader);