import * as React from "react";
import {
    Checkbox,
    Progress,
    Grid,
    Card,
    Button,
    Icon} from 'semantic-ui-react';
import * as Dropzone from "react-dropzone/dist/index";
import "./index.scss";
import { ifPresent, fileSizeToString, iconFromFilePath } from "UtilityFunctions";
import { bulkUpload, multipartUpload, BulkUploadPolicy } from "./api";

interface Upload {
    file: File
    isUploading: boolean
    progressPercentage: number
    extractArchive: boolean
    uploadXHR?: XMLHttpRequest
}

interface UploaderState {
    uploads: Upload[]
}

interface UploaderProps {
    allowMultiple?: boolean
    location: string
    onFilesUploaded?: () => void
}

const uploadsFinished = (uploads: Upload[]): boolean => uploads.every((it) => it.uploadXHR.readyState === 4);

const newUpload = (file: File): Upload => {
    return {
        file,
        isUploading: false,
        progressPercentage: 0,
        extractArchive: false,
        uploadXHR: null
    }
}

export class Uploader extends React.Component<UploaderProps, UploaderState> {
    constructor(props: UploaderProps) {
        super(props);

        this.state = {
            uploads: []
        };
    }

    componentWillUnmount() {
        this.state.uploads.forEach(it => {
            if (!!it.uploadXHR) {
                if (it.uploadXHR.readyState != XMLHttpRequest.DONE) {
                    console.log("Aborting", it);
                    it.uploadXHR.abort();
                }
            }
        });
    }

    // TODO The upload component should be able to continue in background?
    onFilesAdded(files: File[]) {
        const filteredFiles = files.filter(it => it.size > 0).map(it => newUpload(it));
        if (filteredFiles.length == 0) return;

        if (this.props.allowMultiple !== false) { // true if no value
            this.setState(() => ({ uploads: this.state.uploads.concat(filteredFiles) }));
        } else {
            this.setState(() => ({ uploads: [filteredFiles[0]] }))
        }
    }

    // TODO - The .then()'s are the same.
    startUpload(index: number) {
        const upload = this.state.uploads[index];
        upload.isUploading = true;
        this.setState(() => ({ uploads: this.state.uploads }));

        if (!upload.extractArchive) {
            multipartUpload(`${this.props.location}/${upload.file.name}`, upload.file, e => {
                upload.progressPercentage = (e.loaded / e.total) * 100;
                this.setState({ uploads: this.state.uploads });
            }).then(xhr => {
                xhr.onloadend = () => {
                    if (!!this.props.onFilesUploaded && uploadsFinished(this.state.uploads)) {
                        this.props.onFilesUploaded();
                    }
                }
                upload.uploadXHR = xhr;
                this.setState({ uploads: this.state.uploads });
            });
        } else {
            bulkUpload(this.props.location, upload.file, BulkUploadPolicy.OVERWRITE, e => {
                upload.progressPercentage = (e.loaded / e.total) * 100;
                this.setState({ uploads: this.state.uploads });
            }).then(xhr => {
                xhr.onloadend = () => {
                    if (!!this.props.onFilesUploaded && uploadsFinished(this.state.uploads)) {
                        this.props.onFilesUploaded();
                    }
                }
                upload.uploadXHR = xhr;
                this.setState({ uploads: this.state.uploads });
            });
        }
    }

    startAllUploads(event) {
        event.preventDefault();
        const length = this.state.uploads.length;
        for (let i = 0; i < length; i++) {
            this.startUpload(i);
        }
    }

    removeUpload(index: number) {
        const files = this.state.uploads.slice();
        if (index < files.length) {
            const remainderFiles = files.slice(0, index).concat(files.slice(index + 1));
            this.setState({ uploads: remainderFiles });
        }
    }

    abort(index: number) {
        const upload = this.state.uploads[index];
        if (!!upload.uploadXHR && upload.uploadXHR.readyState != XMLHttpRequest.DONE) {
            upload.uploadXHR.abort();
            this.removeUpload(index);
        }
    }

    onExtractChange(index: number, value: boolean) {
        const uploads = this.state.uploads;
        uploads[index].extractArchive = value;
        this.setState({ uploads });
    }

    render() {
        return (
            <div>
                {
                    this.state.uploads.map((upload, index) => (
                        <UploaderRow
                            key={index}
                            {...upload}
                            onExtractChange={value => this.onExtractChange(index, value)}
                            onUpload={() => this.startUpload(index)}
                            onDelete={it => { it.preventDefault(); this.removeUpload(index) }}
                            onAbort={it => { it.preventDefault(); this.abort(index) }}
                        />
                    ))
                }

                {
                    this.state.uploads.filter(it => !it.isUploading).length > 1 ?
                        <Button
                            fluid
                            positive
                            icon="cloud upload"
                            content="Start all!"
                            className="start-all-btn"
                            onClick={this.startAllUploads.bind(this)}
                        />
                        : null
                }

                <Dropzone className="dropzone" onDrop={this.onFilesAdded.bind(this)}>
                    <p>
                        <Icon name="cloud upload" />
                        Drop files here or <a href="#" onClick={e => e.preventDefault()}>browse</a>
                    </p>
                    <p>
                        <b>Bulk upload</b> supported for file types: <i><code>{archiveExtensions.join(", ")}</code></i>
                    </p>
                </Dropzone>
            </div>
        );
    }
}

const UploaderRow = (p: {
    file: File,
    extractArchive: boolean,
    isUploading: boolean,
    progressPercentage?: number,
    onExtractChange?: (value: boolean) => void,
    onUpload?: (e: React.MouseEvent<any>) => void,
    onDelete?: (e: React.MouseEvent<any>) => void,
    onAbort?: (e: React.MouseEvent<any>) => void
}) => {
    const fileTitle = <span><b>{p.file.name}</b> ({fileSizeToString(p.file.size)})</span>;
    let body;

    if (!p.isUploading) {
        body = <React.Fragment>
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
        </React.Fragment>;
    } else {
        body = <React.Fragment>
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
                <Button icon="close" content="Cancel" fluid negative onClick={(e) => ifPresent(p.onAbort, c => c(e))} />
            </Grid.Column>
        </React.Fragment>;
    }

    return <Card fluid>
        <Card.Content>
            <Grid divided stackable>
                <Grid.Column width={1}>
                    <Icon size="large" name={iconFromFilePath(p.file.name)} />
                </Grid.Column>
                {body}
            </Grid>
        </Card.Content>
    </Card>;
}

const archiveExtensions: string[] = [".tar.gz"]
const isArchiveExtension = (fileName: string): boolean => archiveExtensions.some(it => fileName.endsWith(it));