import React from "react";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import PropTypes from "prop-types";
import { Modal, Icon, Button, List, Input } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { BreadCrumbs } from "../Breadcrumbs/Breadcrumbs";
import { getFilenameFromPath, iconFromFilePath, getParentPath, isInvalidPathName, inSuccessRange, removeTrailingSlash } from "../../UtilityFunctions";
import * as uf from "../../UtilityFunctions";
import PromiseKeeper from "../../PromiseKeeper";
import { changeUppyRunAppOpen } from "../../Actions/UppyActions";
import { KeyCode } from "../../DefaultObjects";
import { FileIcon } from "../UtilityComponents";
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
            creatingFolder: false
        };

        this.openModal = this.openModal.bind(this);
        this.closeModal = this.closeModal.bind(this);
        this.fetchFiles = this.fetchFiles.bind(this);
        this.setSelectedFile = this.setSelectedFile.bind(this);
        this.onUppyClose = this.onUppyClose.bind(this);
        this.uppyOnUploadSuccess = this.uppyOnUploadSuccess.bind(this);
        this.startCreateNewFolder = this.startCreateNewFolder.bind(this);
        this.handleKeyDown = this.handleKeyDown.bind(this);
    }

    startCreateNewFolder() {
        if (!this.state.creatingFolder) {
            this.setState(() => ({ creatingFolder: true }));
        } else {
            this.handleKeyDown(KeyCode.ENTER);
        }
    }

    resetCreateFolder() {
        this.setState(() => ({ creatingFolder: false }));
    }

    handleKeyDown(key, name) {
        if (key === KeyCode.ESC) {
            this.resetCreateFolder();
        } else if (key === KeyCode.ENTER) {
            const { currentPath, files } = this.state;
            const fileNames = files.map((it) => getFilenameFromPath(it.path));
            if (isInvalidPathName(name, fileNames)) { return }
            const directoryPath = `${currentPath.endsWith("/") ? currentPath + name : currentPath + "/" + name}`;
            !!name ? Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
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

    // FIXME: Merge two following functions into one
    openModal() {
        this.setState(() => ({ modalShown: true }));
    }

    closeModal() {
        this.setState(() => ({ modalShown: false }));
    }

    componentDidMount() {
        this.fetchFiles(Cloud.homeFolder);
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
            creatingFolder: false
        }));
        this.props.uploadCallback(fileCopy);
    }

    fetchFiles(path) {
        this.setState(() => ({ loading: true, creatingFolder: false }));
        this.state.promises.makeCancelable(Cloud.get(`files?path=${path}`)).promise.then(req => {
            this.setState(() => ({
                files: uf.sortFilesByTypeAndName(req.response, true),
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
            <React.Fragment>
                <Input
                    className="readonly mobile-padding"
                    required={this.props.isRequired}
                    placeholder={"No file selected"}
                    value={path}
                    action
                >
                    <input />
                    <Button type="button" onClick={this.openModal} content="Browse" color="blue" />
                    {uploadButton}
                    {removeButton}
                </Input>
                <FileSelectorModal
                    show={this.state.modalShown}
                    onHide={this.closeModal}
                    currentPath={this.state.currentPath}
                    navigate={this.fetchFiles}
                    files={this.state.files}
                    loading={this.state.loading}
                    creatingFolder={this.state.creatingFolder}
                    setSelectedFile={this.setSelectedFile}
                    fetchFiles={this.fetchFiles}
                    handleKeyDown={this.handleKeyDown}
                    createFolder={this.startCreateNewFolder}
                    canSelectFolders={this.props.canSelectFolders}
                />
            </React.Fragment>)
    }
}

export const FileSelectorModal = (props) => (
    // FIXME closeOnDimmerClick is a fix caused by modal incompatibility. See ModalFix.scss
    <Modal open={props.show} onClose={props.onHide} closeOnDimmerClick={false} size="large">
        <Modal.Header>
            File selector
            <Button floated="right" circular icon="cancel" type="button" onClick={props.onHide} />
        </Modal.Header>
        <Modal.Content scrolling>
            <BreadCrumbs currentPath={props.currentPath} navigate={props.fetchFiles} />
            <DefaultLoading size="big" color="black" loading={props.loading}/>
            <FileSelectorBody {...props} />
        </Modal.Content>
    </Modal>
);

const FileSelectorBody = ({ disallowedPaths = [], onlyAllowFolders = false, ...props }) => {
    const files = (onlyAllowFolders ?
        props.files.filter(f => uf.isDirectory(f)) : props.files)
        .filter((it) => !disallowedPaths.some((d) => d === it.path));
    // FIXME removetrailingslash usage needed?
    return (
        <React.Fragment>
            <List divided size="large">
                <List.Header>
                    Filename
                </List.Header>
                <CreatingFolder
                    creatingFolder={props.creatingFolder}
                    handleKeyDown={props.handleKeyDown}
                />
                <ReturnFolder
                    currentPath={props.currentPath}
                    parentPath={removeTrailingSlash(getParentPath(props.currentPath))}
                    fetchFiles={props.fetchFiles}
                    setSelectedFile={props.setSelectedFile}
                    canSelectFolders={props.canSelectFolders}
                />
                <CurrentFolder
                    currentPath={removeTrailingSlash(props.currentPath)}
                    onlyAllowFolders={onlyAllowFolders}
                    onClick={props.onClick}
                />
                <FileList files={files} setSelectedFile={props.setSelectedFile} fetchFiles={props.fetchFiles} canSelectFolders={props.canSelectFolders} />
            </List>
            {props.createFolder != null ? <Button onClick={() => props.createFolder()}>
                Create new folder
            </Button> : null}
        </React.Fragment>)
};


// FIXME CurrentFolder and Return should share exact same traits
const CurrentFolder = ({ currentPath, onlyAllowFolders, onClick }) =>
    onlyAllowFolders ? (
        <List.Item className="pointer-cursor itemPadding">
            <List.Content floated="right">
                <Button onClick={() => onClick(currentPath)}>Select</Button>
            </List.Content>
            <List.Icon name="folder" color="blue" />
            <List.Content onClick={() => onClick(getParentPath(currentPath))}>
                {`${getFilenameFromPath(uf.replaceHomeFolder(currentPath, Cloud.homeFolder))} (Current folder)`}
            </List.Content>
        </List.Item>
    ) : null;

const CreatingFolder = ({ creatingFolder, updateText, handleKeyDown }) => (
    (!creatingFolder) ? null : (
        <List.Item className="itemPadding">
            <List.Content>
                <List.Icon name="folder" color="blue" />
                <Input
                    onKeyDown={(e) => handleKeyDown(e.keyCode, e.target.value)}
                    placeholder="Folder name..."
                    autoFocus
                    transparent
                />
                <Button floated="right" onClick={() => handleKeyDown(KeyCode.ESC)}>✗</Button>
            </List.Content>
        </List.Item>
    )
);

const ReturnFolder = ({ currentPath, parentPath, fetchFiles, onClick, canSelectFolders }) =>
    !(currentPath !== Cloud.homeFolder) || !(currentPath !== "/home") ? null : (
        <List.Item className="pointer-cursor itemPadding" onClick={() => fetchFiles(parentPath)}>
            {canSelectFolders ? (
                <List.Content floated="right">
                    <Button onClick={() => onClick(parentPath)}>Select</Button>
                </List.Content>) : null}
            <List.Icon name="folder" color="blue" />
            <List.Content content=".." />
        </List.Item>);

const UploadButton = ({ onClick }) => (<Button type="button" content="Upload File" onClick={() => onClick()} />);
const RemoveButton = ({ onClick }) => (<Button type="button" content="✗" onClick={() => onClick()} />);
const FolderSelection = ({ canSelectFolders, setSelectedFile }) => canSelectFolders ?
    (<Button onClick={setSelectedFile} floated="right">Select</Button>) : null;

const FileList = ({ files, fetchFiles, setSelectedFile, canSelectFolders }) =>
    !files.length ? null :
        (<React.Fragment>
            {files.map((file, index) =>
                file.type === "FILE" ?
                    (<List.Item
                        key={index}
                        icon={uf.iconFromFilePath(file.path)}
                        content={uf.getFilenameFromPath(file.path)}
                        onClick={() => setSelectedFile(file)}
                        className="itemPadding pointer-cursor"
                    />)
                    : (<List.Item key={index} className="itemPadding pointer-cursor">
                        <List.Content floated="right">
                            <FolderSelection
                                canSelectFolders={canSelectFolders}
                                file={file}
                                setSelectedFile={() => setSelectedFile(file)}
                            />
                        </List.Content>
                        <List.Content onClick={() => fetchFiles(file.path)}>
                            <FileIcon name="folder" link={file.link} color="blue" />
                            {getFilenameFromPath(file.path)}
                        </List.Content>
                    </List.Item>)
            )}
        </React.Fragment>);

FileSelector.contextTypes = {
    store: PropTypes.object
};

export default FileSelector;