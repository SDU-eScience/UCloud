import React from "react";
import PropTypes from "prop-types";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Modal, Button, Table, FormGroup, InputGroup } from "react-bootstrap";
import { Icon } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { BreadCrumbs } from "../Breadcrumbs"
import { sortFilesByTypeAndName, getFilenameFromPath, getTypeFromFile, getParentPath, isInvalidPathName, inSuccessRange } from "../../UtilityFunctions";
import PromiseKeeper from "../../PromiseKeeper";
import { DashboardModal } from "uppy/lib/react";
import { dispatch } from "redux";
import { changeUppyRunAppOpen } from "../../Actions/UppyActions";
import { KeyCode } from "../../DefaultObjects";
import "./Files.scss";

class FileSelector extends React.Component {
    constructor(props, context) {
        super(props, context);
        this.state = {
            promises: new PromiseKeeper(),
            returnObject: props.returnObject,
            currentPath: `${Cloud.homeFolder}`,
            loading: false,
            files: [],
            modalShown: false,
            breadcrumbs: [],
            uppyOnUploadSuccess: null,
            creatingFolderName: null
        };

        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
        this.fetchFiles = this.fetchFiles.bind(this);
        this.setSelectedFile = this.setSelectedFile.bind(this);
        this.onUppyClose = this.onUppyClose.bind(this);
        this.uppyOnUploadSuccess = this.uppyOnUploadSuccess.bind(this);
        this.startCreateNewFolder = this.startCreateNewFolder.bind(this);
        this.updateCreateFolderName = this.updateCreateFolderName.bind(this);
        this.handleKeyDown = this.handleKeyDown.bind(this);
    }

    startCreateNewFolder() {
        if (this.state.creatingFolderName == null) {
            this.setState(() => ({ creatingFolderName: "" }));
        } else {
            this.handleKeyDown(KeyCode.ENTER);
        }
    }

    resetCreateFolder() {
        this.setState(() => ({ creatingFolderName: null }));
    }

    handleKeyDown(value) {
        if (value === KeyCode.ESC) {
            this.resetCreateFolder();
        } else if (value === KeyCode.ENTER) {
            const { currentPath, files } = this.state;
            const fileNames = files.map((it) => getFilenameFromPath(it.path))
            const name = this.state.creatingFolderName;
            if (isInvalidPathName(name, fileNames)) { return }
            const directoryPath = `${currentPath.endsWith("/") ? currentPath + name : currentPath + "/" + name}`;
            name ? Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
                if (inSuccessRange(request.status)) {
                    // TODO Push mock folder
                    this.resetCreateFolder();
                    this.fetchFiles(currentPath);
                }
            }).catch((failure) => {
                console.log(`failure: ${failure}`)
                this.resetCreateFolder() // TODO Handle failure
            }) : this.resetCreateFolder();
        }
    }

    uppyOnUploadSuccess(file, resp, uploadURL) {
        if (!this.props.allowUpload) return;
        // TODO This is a hack.
        let apiIndex = uploadURL.indexOf("/api/");
        if (apiIndex === -1) throw "Did not expect upload URL to not contain /api/";

        let apiEndpoint = uploadURL.substring(apiIndex + 5);

        Cloud.head(apiEndpoint).then(it => {
            console.log("Got a response back!");
            let path = it.request.getResponseHeader("File-Location");
            let lastSlash = path.lastIndexOf("/");
            if (lastSlash === -1) throw "Could not parse name of path: " + path;
            let name = path.substring(lastSlash + 1);
            let fileObject = {
                path: path,
                name: name,
            };
            this.props.uploadCallback(fileObject);
        });
    };

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

    componentDidMount() {
        this.fetchFiles(`/home/${Cloud.username}`);
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    onUppyClose() {
        this.props.uppy.off("upload-success", this.state.uppyOnUploadSuccess);
        this.setState(() => ({
            uppyOnUploadSuccess: null,
        }));
    }

    setSelectedFile(file) {
        let fileCopy = { path: file.path };
        this.setState(() => ({
            modalShown: false,
            creatingFolderName: null
        }));
        this.props.uploadCallback(fileCopy);
    }

    updateCreateFolderName(creatingFolderName) {
        this.setState(() => ({
            creatingFolderName: creatingFolderName ? creatingFolderName : ""
        }))
    }

    fetchFiles(path) {
        this.setState(() => ({ loading: true, creatingFolderName: null }));
        this.state.promises.makeCancelable(Cloud.get(`files?path=${path}`)).promise.then(req => {
            this.setState(() => ({
                files: sortFilesByTypeAndName(req.response, true),
                loading: false,
                currentPath: path
            }));
        });
    }

    render() {
        const onUpload = () => {
            if (!this.props.allowUpload) return;
            this.context.store.dispatch(changeUppyRunAppOpen(true));
            let uppy = this.props.uppy;
            uppy.reset();
            uppy.once("upload-success", this.uppyOnUploadSuccess);
        };
        const path = this.props.path ? this.props.path : "";
        const uploadButton = this.props.allowUpload ? (<UploadButton onClick={onUpload} />) : null;
        const removeButton = this.props.remove ? (<RemoveButton onClick={this.props.remove} />) : null;
        return (
            <div>
                <div className="input-group">
                    <span className="input-group-btn">
                        <Button onClick={this.openModal}>Browse files</Button>
                    </span>
                    <input className="form-control readonly" required={this.props.isRequired} type="text"
                        placeholder={"No file selected"}
                        value={path} />
                    {uploadButton}
                    {removeButton}
                </div>
                <FileSelectorModal
                    show={this.state.modalShown}
                    onHide={this.closeModal}
                    currentPath={this.state.currentPath}
                    navigate={this.fetchFiles}
                    files={this.state.files}
                    loading={this.state.loading}
                    creatingFolderName={this.state.creatingFolderName}
                    onClick={this.setSelectedFile}
                    fetchFiles={this.fetchFiles}
                    handleKeyDown={this.handleKeyDown}
                    updateText={this.updateCreateFolderName}
                    createFolder={this.startCreateNewFolder}
                />
            </div>)
    }
}

export const FileSelectorModal = (props) =>
    <Modal show={props.show} onHide={props.onHide}>
        <Modal.Header closeButton>
            <Modal.Title>File selector</Modal.Title>
        </Modal.Header>
        <BreadCrumbs currentPath={props.currentPath} navigate={props.fetchFiles} />
        <BallPulseLoading loading={props.loading} />
        <FileSelectorBody
            onlyAllowFolders={props.onlyAllowFolders}
            canSelectFolders={props.canSelectFolders}
            creatingFolderName={props.creatingFolderName}
            loading={props.loading}
            onClick={props.onClick}
            files={props.files}
            fetchFiles={props.fetchFiles}
            currentPath={props.currentPath}
            handleKeyDown={props.handleKeyDown}
            updateText={props.updateText}
            createFolder={props.createFolder}
        />
    </Modal>

const FileSelectorBody = (props) => {
    if (props.loading) {
        return null;
    }
    if (!props.files.length) {
        return (
            <h4 className="col-md-offset-1">
                <small>No files in current folder.</small>
            </h4>
        );
    }
    const files = (!!props.onlyAllowFolders) ? props.files.filter(f => f.type === "DIRECTORY") : props.files;
    return (
        <Modal.Body>
            <div className="pre-scrollable">
                <Table className="table table-hover">
                    <thead>
                        <tr>
                            <th>Filename</th>
                        </tr>
                    </thead>
                    <tbody>
                        <CreatingFolder
                            creatingFolderName={props.creatingFolderName}
                            handleKeyDown={props.handleKeyDown}
                            updateText={props.updateText}
                        />
                        <ReturnFolder currentPath={removeTrailingSlash(props.currentPath)} fetchFiles={props.fetchFiles} />
                        <CurrentFolder currentPath={removeTrailingSlash(props.currentPath)} onlyAllowFolders={props.onlyAllowFolders} onClick={props.onClick} />
                        <FileList files={files} onClick={props.onClick} fetchFiles={props.fetchFiles} canSelectFolders={props.canSelectFolders} />
                    </tbody>
                </Table>
            </div>
            {props.createFolder != null ? <Button className="btn btn-info" onClick={() => props.createFolder()}>
                Create new folder
            </Button> : null}
        </Modal.Body>)
};

const CurrentFolder = ({ currentPath, onlyAllowFolders, onClick }) =>
    onlyAllowFolders ?
        <tr className="row-settings clickable-row pointer-cursor">
            <td onClick={() => onClick(getParentPath(currentPath))}>
                <a><i className="ion-android-folder" /> .</a>
            </td>
            <td><Button onClick={() => onClick(currentPath)} className="pull-right">Select</Button></td>
        </tr>
        : null;

const CreatingFolder = ({ creatingFolderName, updateText, handleKeyDown }) => (
    (creatingFolderName == null) ? null : (
        <tr>
            <td>
                <FormGroup>
                    <div className="form-inline"> 
                        <InputGroup>
                            <i className="ion-android-folder create-folder-placement" />
                        </InputGroup>
                        <InputGroup>
                            <input
                                onKeyDown={(e) => handleKeyDown(e.keyCode, true)}
                                className="form-control"
                                type="text"
                                placeholder="Folder name..."
                                value={creatingFolderName ? creatingFolderName : ""}
                                onChange={(e) => updateText(e.target.value)}
                                autoFocus
                            />
                            <span className="input-group-addon hidden-lg btn-info btn" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</span>
                            <span className="input-group-addon hidden-lg btn" onClick={() => handleKeyDown(KeyCode.ESC, true)}>✗</span>
                        </InputGroup>
                    </div>
                </FormGroup>
            </td><td></td>
        </tr>
    )
);

const ReturnFolder = ({ currentPath, fetchFiles }) =>
    !(currentPath !== Cloud.homeFolder) || !(currentPath !== "/home") ? null : (
        <tr className="row-settings clickable-row pointer-cursor">
            <td onClick={() => fetchFiles(getParentPath(currentPath))}>
                <a><i className="ion-android-folder" /> ..</a>
            </td>
            <td/>
        </tr>);

const UploadButton = ({ onClick }) => (<span className="input-group-addon btn btn-info" onClick={() => onClick()}>Upload file</span>);
const RemoveButton = ({ onClick }) => (<span className="input-group-addon btn btn" onClick={() => onClick()}>✗</span>)

const FileList = ({ files, fetchFiles, onClick, canSelectFolders }) => {
    return !files.length ? null :
        (<React.Fragment>
            {files.map((file, index) =>
                file.type === "FILE" ?
                    (<tr key={index} className="gradeA row-settings pointer-cursor">
                        <td onClick={() => onClick(file)}><Icon
                            className={getTypeFromFile(file.path)} /> {getFilenameFromPath(file.path)}
                        </td><td></td>
                    </tr>)
                    : (<tr key={index} className="row-settings clickable-row pointer-cursor">
                        <td onClick={() => fetchFiles(file.path)}>
                            <a><i className="ion-android-folder" /> {getFilenameFromPath(file.path)}</a>
                        </td>
                        <td>{canSelectFolders ? <Button onClick={() => onClick(file.path)} className="pull-right">Select</Button> : null}</td>
                    </tr>)
            )}
        </React.Fragment>);
}

const removeTrailingSlash = (path) => path.endsWith("/") ? path.slice(0, path.length - 1) : path;

export default FileSelector;