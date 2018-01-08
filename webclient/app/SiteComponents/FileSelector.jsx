import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Modal, Button} from 'react-bootstrap';

let files = [
    {path: {name: "Hi"}}, {path: {name: "Ho"}}, {path: {name: "He"}}
];


class FileSelector extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            selectedFile: "",
            files: [],
            modalShown: false,
            breadcrumbs: [],
        };
        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
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
        this.setState(() => ({files: files}));
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
                        <BreadCrumbs breadcrumbs={this.state.breadcrumbs}/>
                    </Modal.Header>
                    <FileSelectorBody onClick={(file) => this.setSelectedFile(file)} files={this.state.files}/>
                </Modal>
            </div>)
    }
}

function BreadCrumbs(props) {
    let breadcrumbs = props.breadcrumbs.slice();
    let breadcrumbsList = breadcrumbs.map(breadcrumb =>
        <li className="breadcrumb-item">
            <a onClick="changePath(breadcrumb.second)"> {breadcrumb.first}</a>
        </li>
    );
    return (
        <ol className="breadcrumb">
            {breadcrumbsList}
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

// From Vue
/*
data() {
    return {
        path: '/',
        files: [],
        loading: true,
        breadcrumbs: [],
        isShown: false,
        selectedFile: null
    }
};


mounted() {
    $.getJSON("/api/getFiles", {path: this.path}).then((files) => {
        this.files = files.sort((a, b) => {
            if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
                return -1;
            else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
                return 1;
            else {
                return a.path.name.localeCompare(b.path.name);
            }
        });
        this.loading = false;
    });
    $.getJSON("/api/getBreadcrumbs", {path: this.path}).then((breadcrumbs) => {
        this.breadcrumbs = breadcrumbs
    });
    window.addEventListener('keydown', this.escapeKeyListener);
}
watch: {
    path: function () {
        this.loading = true;
        this.files = [];
        $.getJSON('/api/getFiles', {path: this.path}).then((files) => {
            this.files = files.sort((a, b) => {
                if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
                    return -1;
                else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
                    return 1;
                else {
                    return a.path.name.localeCompare(b.path.name);
                }
            });
            this.loading = false;
        });
        $.getJSON("/api/getBreadcrumbs", {path: this.path}).then((breadcrumbs) => {
            this.breadcrumbs = breadcrumbs
        });
    }
}

methods = {
    escapeKeyListener($event) {
        if ($event.key === 'Escape' && this.isShown) {
            this.hide()
        }
    },

    changePath: function (newPath) {
        this.path = newPath;
    },

    hide: function () {
        this.isShown = false;
    },

    setFile: function (file) {
        this.selectedFile = file;
        this.$emit('select', file);
        this.hide();
    },

    show: function () {
        this.isShown = true;
    },

    computeIcon: function (file) {
        if (file.type === "DIRECTORY") return "ion-folder";
        else return "ion-android-document";
    },

    handleSelection: function (file) {
        if (file.type === "DIRECTORY") {
            this.changePath(file.path.path);
        } else {
            this.setFile(file);
            this.hide();
        }
    },

    createFolder() {
        let currentDir = this.breadcrumbs[this.breadcrumbs.length - 1].second;
        swal({
            title: "Create a folder",
            text: "Input the folder name:",
            input: "text",
            showCancelButton: true,
            inputPlaceholder: "Folder name...",
            confirmButtonText: "Create folder",
            preConfirm: (text) => {
                if (text === "")
                    swal.showValidationError("You need to enter a folder name.");
            }
        }).then((inputValue) => {
            if (inputValue.dismiss !== 'cancel') {
                $.getJSON("/api/createDir", {dirPath: currentDir + inputValue.value}, (result) => {
                    if (result === 200) {
                        swal("Success", "Folder " + currentDir + inputValue.value + " created", "success");
                    } else {
                        swal("Error", "Folder " + currentDir + inputValue.value + " was not created", "error");
                    }
                });
            }
        });
    },
};*/