import React from 'react';
import {BallPulseLoading} from './LoadingIcon';
import {Modal, Button, Breadcrumb, Table} from 'react-bootstrap';
import {Cloud} from "../../authentication/SDUCloudObject";
import Breadcrumbs from "./Breadcrumbs"
import {sortFilesByTypeAndName, createFolder} from "../UtilityFunctions";
import PromiseKeeper from "../PromiseKeeper";
import {DashboardModal} from "uppy/lib/react";

class FileSelector extends React.Component {
    constructor(props) {
        super(props);
        let file = props.initialFile ? props.initialFile : {path: {path: ""}};
        this.state = {
            promises: new PromiseKeeper(),
            returnObject: props.returnObject,
            selectedFile: file,
            currentPath: `/home/${Cloud.username}`,
            loading: false,
            files: [],
            modalShown: false,
            uppyOpen: false,
            breadcrumbs: [],
            onFileSelectionChange: props.onFileSelectionChange,
            uppyOnUploadSuccess: null,
        };
        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
        this.getFiles = this.getFiles.bind(this);
        this.setSelectedFile = this.setSelectedFile.bind(this);
        this.openUppy = this.openUppy.bind(this);
        this.onUppyClose = this.onUppyClose.bind(this);
    }

    openUppy() {
        let uppyOnUploadSuccess = (file, resp, uploadURL) => {
            // TODO This is a hack.
            let apiIndex = uploadURL.indexOf("/api/");
            if (apiIndex === -1) throw "Did not expect upload URL to not contain /api/";

            let apiEndpoint = uploadURL.substring(apiIndex + 5)
            
            Cloud.head(apiEndpoint).then(it => {
                console.log("Got a response back!");
                let path = it.request.getResponseHeader("File-Location");
                let lastSlash = path.lastIndexOf("/");
                if (lastSlash === -1) throw "Could not parse name of path: " + path;
                let name = path.substring(lastSlash + 1);
                let fileObject = {
                    path: {
                        host: "tempZone",
                        path: path,
                        uri: `storage://tempZone${path}`, // TODO The old format is being deprecated, hence the hardcoded string.
                        name: name,
                    }
                };

                this.setSelectedFile(fileObject);
            });
        };

        this.props.uppy.on("upload-success", uppyOnUploadSuccess);
        this.setState(() => ({
            uppyOpen: true,
            uppyOnUploadSuccess: uppyOnUploadSuccess,
        }));

    }

    componentDidMount() {
        this.getFiles(`/home/${Cloud.username}`);
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
        
        // Clean up in uppy. TODO potentially refactor this
        let uppyOnUploadSuccess = this.state.uppyOnUploadSuccess;
        if (uppyOnUploadSuccess !== null) {
            this.props.uppy.off("upload-success", uppyOnUploadSuccess);
        }
        this.props.uppy.reset();
    }

    openModal() {
        this.setState(() => ({
            modalShown: true
        }));
    }

    closeModal() {
        this.setState(() => ({
            modalShown: false
        }));
    }

    onUppyClose() {
        this.props.uppy.off("upload-success", this.state.uppyOnUploadSuccess);
        this.setState(() => ({
            uppyOpen: false,
            uppyOnUploadSuccess: null,
        }));
        this.props.uppy.reset();
    }

    setSelectedFile(file) {
        console.log(file);
        let fileCopy = { path: Object.assign({}, file.path) }; 
        console.log(fileCopy);
        this.setState(() => ({
            selectedFile: fileCopy,
            modalShown: false,
        }));
        this.state.onFileSelectionChange(fileCopy, this.state.returnObject);
    }

    getFiles(path) {
        this.setState(() => ({loading: true}));
        this.state.promises.makeCancelable(Cloud.get(`files?path=${path}`)).promise.then(req => {
            this.setState(() => ({
                files: sortFilesByTypeAndName(req.response, true),
                loading: false,
                currentPath: path,
            }));
        });
    }

    render() {
        let uploadButton, uppyModal;
        uploadButton = uppyModal = null;
        if (this.props.allowUpload) {
            uploadButton = this.props.allowUpload ?
                (<UploadButton onClick={this.openUppy}/>) : null;
            uppyModal = (<DashboardModal
                uppy={this.props.uppy}
                closeModalOnClickOutside
                open={this.state.uppyOpen}
                onRequestClose={this.onUppyClose}
            />)
        }
        return (
            <div>
                <div className="input-group col-sm-12">
                    <span className="input-group-btn">
                        <Button onClick={this.openModal}
                                type="button"
                                className="btn btn-default">Browse files
                        </Button>
                    </span>
                    <input className="form-control readonly" required={this.props.isRequired} type="text"
                           placeholder={"No file selected"}
                           value={this.state.selectedFile.path.path}/>
                    {uploadButton}
                </div>
                <Modal show={this.state.modalShown} onHide={this.closeModal}>
                    <Modal.Header closeButton>
                        <Modal.Title>File selector</Modal.Title>
                    </Modal.Header>
                    <Breadcrumbs path={this.state.currentPath} getFiles={this.getFiles}/>
                    <BallPulseLoading loading={this.state.loading}/>
                    <FileSelectorBody loading={this.state.loading} onClick={this.setSelectedFile}
                                      files={this.state.files} getFiles={this.getFiles}
                                      currentPath={this.state.currentPath}/>
                </Modal>
                {uppyModal}
            </div>)
    }
}

function FileSelectorBody(props) {
    if (props.loading) {
        return null;
    }
    let noFiles = !props.files.length ? <h4>
        <small>No files in current folder.</small>
    </h4> : null;
    return (
        <Modal.Body>
            <div className="pre-scrollable">
                {noFiles}
                <Table className="table table-hover">
                    <thead>
                    <tr>
                        <th>Filename</th>
                    </tr>
                    </thead>
                    <FileList files={props.files} onClick={props.onClick} getFiles={props.getFiles}/>
                </Table>
            </div>
            <Button className="btn btn-info" onClick={() => createFolder(props.currentPath)}>
                Create new folder
            </Button>
        </Modal.Body>)
}

function UploadButton(props) {
    return (
        <span className="input-group-addon btn btn-info" onClick={() => props.onClick()}>Upload file</span>
    );
}

function FileList(props) {
    if (!props.files.length) {
        return null;
    }
    let files = props.files.slice();
    let i = 0;
    let filesList = files.map(file => {
        if (file.type === "DIRECTORY") {
            return (
                <tr key={i++} className="gradeA row-settings">
                    <td onClick={() => props.getFiles(file.path.path)}><em
                        className="ion-android-folder"/> {file.path.name}
                    </td>
                </tr>
            );
        } else {
            return (
                <tr key={i++} className="gradeA row-settings">
                    <td onClick={() => props.onClick(file)}><span className="ion-android-document"/> {file.path.name}
                    </td>
                </tr>)
        }
    });
    return (
        <tbody>
        {filesList}
        </tbody>
    )
}

export default FileSelector;