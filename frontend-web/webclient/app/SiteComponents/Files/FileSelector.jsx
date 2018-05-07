import React from "react";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Modal, FormGroup, InputGroup } from "react-bootstrap";
import { Icon, Button, List } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { BreadCrumbs } from "../Breadcrumbs"
import { sortFilesByTypeAndName, getFilenameFromPath, getTypeFromFile, getParentPath, isInvalidPathName, inSuccessRange, removeTrailingSlash } from "../../UtilityFunctions";
import PromiseKeeper from "../../PromiseKeeper";
import { dispatch } from "redux";
import { changeUppyRunAppOpen } from "../../Actions/UppyActions";
import { KeyCode } from "../../DefaultObjects";
import "./Files.scss";
import "../Styling/Shared.scss";

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
                console.warn(`failure: ${failure}`);
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
            {...props}
        />
    </Modal>

const FileSelectorBody = (props) => {
    if (props.loading) {
        return null;
    }
    const files = (!!props.onlyAllowFolders) ?
        props.files.filter(f => f.type === "DIRECTORY") : props.files;
    return (
        <Modal.Body>
            <List divided size={"large"}>
                <List.Header>
                    Filename
                    </List.Header>
                <CreatingFolder
                    creatingFolderName={props.creatingFolderName}
                    handleKeyDown={props.handleKeyDown}
                    updateText={props.updateText}
                />
                <ReturnFolder
                    currentPath={props.currentPath}
                    parentPath={removeTrailingSlash(getParentPath(props.currentPath))}
                    fetchFiles={props.fetchFiles}
                    onClick={props.onClick}
                    canSelectFolders={props.canSelectFolders}
                />
                <CurrentFolder currentPath={removeTrailingSlash(props.currentPath)} onlyAllowFolders={props.onlyAllowFolders} onClick={props.onClick} />
                <FileList files={files} onClick={props.onClick} fetchFiles={props.fetchFiles} canSelectFolders={props.canSelectFolders} />
            </List>
            {props.createFolder != null ? <Button className="btn btn-info" onClick={() => props.createFolder()}>
                Create new folder
            </Button> : null}
        </Modal.Body>)
};

const CurrentFolder = ({ currentPath, onlyAllowFolders, onClick }) =>
    onlyAllowFolders ?
        <List.Item className="pointer-cursor itemPadding">
            <List.Content floated="right">
                <Button onClick={() => onClick(currentPath)}>Select</Button>
            </List.Content>
            <List.Icon name="folder" />
            <List.Content onClick={() => onClick(getParentPath(currentPath))}>
                {`${getFilenameFromPath(currentPath)} (Current folder)`}
            </List.Content>
        </List.Item>
        : null;

const CreatingFolder = ({ creatingFolderName, updateText, handleKeyDown }) => (
    (creatingFolderName == null) ? null : (
        <List.Item className="itemPadding">
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
        </List.Item>
    )
);

const ReturnFolder = ({ currentPath, parentPath, fetchFiles, onClick, canSelectFolders }) =>
    !(currentPath !== Cloud.homeFolder) || !(currentPath !== "/home") ? null : (
        <List.Item className="pointer-cursor itemPadding">
            {canSelectFolders ? (
                <List.Content floated="right">
                    <Button onClick={() => onClick(parentPath)}>Select</Button>
                </List.Content>) : null}

            <List.Icon name="folder" color="blue" />
            <List.Content onClick={() => fetchFiles(parentPath)}>
                ..
            </List.Content>
        </List.Item>);

const UploadButton = ({ onClick }) => (<span className="input-group-addon btn btn-info" onClick={() => onClick()}>Upload file</span>);
const RemoveButton = ({ onClick }) => (<span className="input-group-addon btn btn" onClick={() => onClick()}>✗</span>)

const FileList = ({ files, fetchFiles, onClick, canSelectFolders }) =>
    !files.length ? null :
        (<React.Fragment>
            {files.map((file, index) =>
                file.type === "FILE" ?
                    (<List.Item key={index} className="itemPadding pointer-cursor">
                        <List.Content onClick={() => onClick(file)}>
                            <Icon className={getTypeFromFile(file.path)} /> {getFilenameFromPath(file.path)}
                        </List.Content>
                    </List.Item>)
                    : (<List.Item key={index} className="itemPadding pointer-cursor">
                        <List.Content floated="right">
                            {canSelectFolders ?
                                <Button onClick={() => onClick(file.path)} className="pull-right">Select</Button>
                                : null}
                        </List.Content>
                        <List.Icon name="folder" />
                        <List.Content onClick={() => fetchFiles(file.path)}>
                            {getFilenameFromPath(file.path)}
                        </List.Content>
                    </List.Item>)
            )}
        </React.Fragment>);

export default FileSelector;