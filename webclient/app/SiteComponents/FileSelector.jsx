import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Modal, Button} from 'react-bootstrap';
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from 'react-router';
import {buildBreadCrumbs, sortFiles} from "../UtilityFunctions";

class FileSelector extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            selectedFile: "",
            currentPath: `/home/${Cloud.username}`,
            files: [],
            modalShown: false,
            breadcrumbs: [],
        };
        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
        this.getFiles = this.getFiles.bind(this);
    }

    componentDidMount() {
        this.getFiles();
    }

    // Experimental syntax, can remove this.n.bind(this) when valid:
    /*
    openModal = () => {
        this.setState(() => ({ modalShown: true }));
    }
    */

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
    }

    getFiles() {
        this.setState(() => ({loading: true}));
        Cloud.get(`files?path=/home/${Cloud.username}`).then(files => {
            this.setState(() => ({
                files: sortFiles(files),
                loading: false,
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
                        <BreadCrumbs breadcrumbs={this.state.currentPath}/>
                    </Modal.Header>
                    <FileSelectorBody loading={this.state.loading} onClick={(file) => this.setSelectedFile(file)}
                                      files={this.state.files}/>
                </Modal>
            </div>)
    }
}

function BreadCrumbs(props) {
    if (!props.currentPath) {
        return null;
    }
    let pathsMapping = buildBreadCrumbs(props.currentPath);
    let i = 0;
    let breadcrumbs = pathsMapping.map(path =>
        <li key={i++} className="breadcrumb-item">
            <Link to={`files/${path.actualPath}`}>{path.local}</Link>
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
            <LoadingIcon loading={props.loading}/>
            <div className="modal-body pre-scrollable">
                {noFiles}
                <table className="table-datatable table table-striped table-hover mv-lg">
                    <thead>
                    <tr>
                        <th>Filename</th>
                    </tr>
                    </thead>
                    <LoadingIcon isLoading={props.loading}/>
                    <FileList files={props.files} onClick={(file) => props.onClick(file)}/>
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
    let filesList = files.map(file =>
        <tr key={i++} className="gradeA row-settings">
            <td className={"ios-file"/*computeIcon(file)*/}
                onClick={() => props.onClick(file)}> {file.path.name}</td>
        </tr>
    );
    return (
        <tbody>
        {filesList}
        </tbody>
    )
}

function SelectedFile(props) {
    if (props.selectedFile) {
        return (<div>Currently selected file: {props.selectedFile.path.name}</div>)
    } else {
        return null;
    }
}

export default FileSelector;