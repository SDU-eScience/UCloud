import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Modal, Button} from 'react-bootstrap';
import {Cloud} from "../../authentication/SDUCloudObject";
import {buildBreadCrumbs, sortFiles} from "../UtilityFunctions";

class FileSelector extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            parameterName: "",
            isSource: false,
            selectedFile: {},
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
        this.state.onFileSelectionChange(file);
    }

    getFiles(path) {
        this.setState(() => ({loading: true}));
        Cloud.get(`files?path=/${path}`).then(files => {
            this.setState(() => ({
                files: sortFiles(files),
                loading: false,
                currentPath: path,
            }));
        });
    }

    createFolder() {
        // TODO
    }

    render() {
        return (
            <div>
                <Button bsStyle="primary" onClick={this.openModal}>Browse files</Button>
                <SelectedFile selectedFile={this.state.selectedFile}/>
                <Modal show={this.state.modalShown} onHide={this.closeModal}>
                    <Modal.Header closeButton>
                        <Modal.Title>File selector</Modal.Title>
                        <LoadingIcon loading={this.state.loading}/>
                    </Modal.Header>
                    <BreadCrumbs path={this.state.currentPath} getFiles={this.getFiles}/>
                    <FileSelectorBody loading={this.state.loading} onClick={(file) => this.setSelectedFile(file)}
                                      files={this.state.files} getFiles={this.getFiles}/>
                </Modal>
            </div>)
    }
}

function BreadCrumbs(props) {
    if (!props.path) {
        return null;
    }
    let pathsMapping = buildBreadCrumbs(props.path);
    let i = 0;
    let breadcrumbs = pathsMapping.map(path =>
        <li key={i++} className="breadcrumb-item">
            <a onClick={() => props.getFiles(`${path.actualPath}`)}>{path.local}</a>
        </li>
    );
    return (
        <ol className="breadcrumb">
            {breadcrumbs}
        </ol>)
}

function FileSelectorBody(props) {
    let noFiles = !props.files.length ? <h4>
        <small>No files in current folder.</small>
    </h4> : null;
    return (
        <Modal.Body>
            <div className="pre-scrollable">
                {noFiles}
                <table className="table-datatable table table-striped table-hover mv-lg">
                    <thead>
                    <tr>
                        <th>Filename</th>
                    </tr>
                    </thead>
                    <FileList files={props.files} onClick={(file) => props.onClick(file)} getFiles={props.getFiles}/>
                </table>
            </div>
            <Button className="btn btn-info" onClick={() => props.createFolder}>
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
                    <td className={"ios-document"} onClick={() => props.getFiles(file.path.path)}> {file.path.name}</td>
                </tr>
            );
        } else {
            return (
                <tr key={i++} className="gradeA row-settings">
                    <td className={"ios-file"} onClick={() => props.onClick(file)}> {file.path.name}</td>
                </tr>)
        }
    });
    return (
        <tbody>
        {filesList}
        </tbody>
    )
}

function SelectedFile(props) {
    if (props.selectedFile.path) {
        return (<div>Currently selected file: {props.selectedFile.path.name}</div>)
    } else {
        return null;
    }
}

export default FileSelector;