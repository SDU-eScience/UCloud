import React from 'react';
import {BallPulseLoading} from './LoadingIcon';
import {Modal, Button, Breadcrumb} from 'react-bootstrap';
import {Cloud} from "../../authentication/SDUCloudObject";
import Breadcrumbs from "./Breadcrumbs"
import {sortFilesByTypeAndName, createFolder} from "../UtilityFunctions";

class FileSelector extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            parameter: props.parameter,
            selectedFile: {path: {path: ""}},
            isSource: props.isSource,
            currentPath: `/home/${Cloud.username}`,
            loading: false,
            files: [],
            modalShown: false,
            breadcrumbs: [],
            onFileSelectionChange: props.onFileSelectionChange,
        };
        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
        this.getFiles = this.getFiles.bind(this);
        this.setSelectedFile = this.setSelectedFile.bind(this);
    }

    componentDidMount() {
        this.getFiles(`/home/${Cloud.username}`);
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

    setSelectedFile(file) {
        this.setState(() => ({
            selectedFile: file,
            modalShown: false,
        }));
        this.state.onFileSelectionChange(file, this.state.parameter, this.state.isSource);
    }

    getFiles(path) {
        this.setState(() => ({loading: true}));
        Cloud.get(`files?path=${path}`).then(files => {
            this.setState(() => ({
                files: sortFilesByTypeAndName(files, true),
                loading: false,
                currentPath: path,
            }));
        });
    }

    render() {
        return (
            <div>
                <div className="input-group col-sm-12">
                    <span className="input-group-btn">
                        <Button onClick={this.openModal}
                                type="button"
                                className="btn btn-default">Browse files
                        </Button>
                    </span>
                    <input className="form-control readonly" required={!this.state.parameter.optional} type="text"
                           placeholder={"No file selected"}
                           value={this.state.selectedFile.path.path}/>
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
                <table className="table table-hover">
                    <thead>
                    <tr>
                        <th>Filename</th>
                    </tr>
                    </thead>
                    <FileList files={props.files} onClick={props.onClick} getFiles={props.getFiles}/>
                </table>
            </div>
            <Button className="btn btn-info" onClick={() => createFolder(props.currentPath)}>
                Create new folder
            </Button>
        </Modal.Body>)
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