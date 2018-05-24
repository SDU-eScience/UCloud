import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import {
    Modal,
    Dropdown,
    Button,
    Icon,
    Table,
    Header,
    Input,
    Grid,
    Responsive,
    Checkbox,
    Rating
} from "semantic-ui-react";

import * as Pagination from "../Pagination";
import { BreadCrumbs } from "../Breadcrumbs/Breadcrumbs";
import * as uf from "../../UtilityFunctions";
import { KeyCode } from "../../DefaultObjects";
import * as Actions from "../../Actions/Files";
import { updatePageTitle } from "../../Actions/Status";
import { changeUppyFilesOpen } from "../../Actions/UppyActions";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon } from "../UtilityComponents";
import { Uploader } from "../Uploader";

class Files extends React.Component {
    constructor(props) {
        super(props);
        const urlPath = props.match.params[0];
        const { history, updatePath, setPageTitle, fetchNewFiles } = props;
        if (urlPath) {
            fetchNewFiles(urlPath);
        } else {
            fetchNewFiles(Cloud.homeFolder);
            history.push(`/files/${Cloud.homeFolder}/`);
        }
        setPageTitle();
        this.props.uppy.run();
        this.state = {
            searchText: "",
            lastSorting: {
                name: "typeAndName",
                asc: true
            },
            creatingNewFolder: false,
            creatingFolderName: "",
            editFolder: {
                index: -1,
                name: ""
            }
        };
        this.selectOrDeselectAllFiles = this.selectOrDeselectAllFiles.bind(this);
        this.sortFilesBy = this.sortFilesBy.bind(this);
        this.updateCreateFolderName = this.updateCreateFolderName.bind(this);
        this.handleKeyDown = this.handleKeyDown.bind(this);
        this.resetFolderObject = this.resetFolderObject.bind(this);
        this.updateEditFileName = this.updateEditFileName.bind(this);
        this.startEditFile = this.startEditFile.bind(this);
    }

    resetFolderObject() {
        this.setState(() => ({
            creatingFolderName: "",
            creatingNewFolder: false,
            editFolder: {
                index: -1,
                name: ""
            }
        }));
    }

    handleKeyDown(value, isNew) {
        if (value === KeyCode.ESC) {
            this.resetFolderObject();
        } else if (value === KeyCode.ENTER) {
            const { path, refetchFiles, files } = this.props;
            const fileNames = files.map((it) => uf.getFilenameFromPath(it.path))
            if (isNew) {
                const name = this.state.creatingFolderName;
                if (uf.isInvalidPathName(name, fileNames)) { return }
                const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
                name ? Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
                    if (uf.inSuccessRange(request.status)) {
                        this.resetFolderObject();
                        refetchFiles(path);
                    }
                }).catch((failure) =>
                    this.resetFolderObject()
                ) : this.resetFolderObject();
            } else {
                const name = this.state.editFolder.name;
                if (uf.isInvalidPathName(name, fileNames)) { return }
                const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
                const originalFilename = files[this.state.editFolder.index].path;
                name ? Cloud.post(`/files/move?path=${originalFilename}&newPath=${directoryPath}`)
                    .then(({ request }) => {
                        if (uf.inSuccessRange(request.status)) {
                            // TODO Overwrite filename;
                            this.resetFolderObject();
                            refetchFiles(this.props.path);
                        }
                    }).catch((failure) =>
                        this.resetFolderObject() // TODO Handle failure
                    ) : this.resetFolderObject();
            }
        }
    }

    selectOrDeselectAllFiles(checked) {
        const { currentFilesPage, filesPerPage, files, updateFiles } = this.props;
        files.forEach(file => file.isChecked = false);
        if (checked) {
            files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
                .forEach(file => file.isChecked = true);
        }
        updateFiles(files);
    }

    sortFilesBy(name, type) {
        const { files, updateFiles, filesPerPage } = this.props;
        let sortedFiles = [];
        const asc = (this.state.lastSorting.name === name) ? !this.state.lastSorting.asc : true;
        switch (type) {
            case "number": {
                sortedFiles = uf.sortByNumber(files, name, asc);
                break;
            }
            case "string": {
                sortedFiles = uf.sortByString(files, name, asc);
                break;
            }
            case "typeAndName": {
                sortedFiles = uf.sortFilesByTypeAndName(files, asc);
                break;
            }
            case "sensitivityLevel":
                sortedFiles = uf.sortFilesBySensitivity(files, asc);
                break;
            default: {
                sortedFiles = files;
                break;
            }
        }
        if (sortedFiles.length > filesPerPage) {
            sortedFiles.forEach((file) => file.isChecked = false);
        }
        updateFiles(sortedFiles);
        this.setState(() => ({
            lastSorting: { name, asc }
        }));
    }

    createFolder() {
        this.resetFolderObject();
        this.setState(() => ({ creatingNewFolder: true }));
    }

    updateCreateFolderName(creatingFolderName) {
        this.setState(() => ({ creatingFolderName }));
    }

    updateEditFileName(e) {
        e.preventDefault();
        let editFolder = { ...this.state.editFolder };
        editFolder.name = e.target.value;
        this.setState(() => ({ editFolder }));
    }

    startEditFile(index, path) {
        this.resetFolderObject();
        this.setState(() => ({
            editFolder: {
                fullPath: path,
                name: uf.getFilenameFromPath(path),
                index
            }
        }));
    }

    render() {
        // PROPS
        const { files, filesPerPage, currentFilesPage, path, loading, history, currentPath, refetchFiles,
            fetchNewFiles, openUppy, checkFile, updateFilesPerPage, updateFiles } = this.props;
        const totalPages = Math.ceil(this.props.files.length / filesPerPage);
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
            .filter(f => uf.getFilenameFromPath(f.path).toLowerCase().includes(this.state.searchText.toLowerCase()));
        // Lambdas
        const goTo = (pageNumber) => {
            this.props.goToPage(pageNumber, files);
            this.resetFolderObject();
        };
        const selectedFiles = shownFiles.filter(file => file.isChecked);
        const rename = () => {
            const firstSelectedFile = selectedFiles[0];
            this.startEditFile(files.findIndex((f) => f.path === firstSelectedFile.path), firstSelectedFile.path);
        };
        const navigate = (path) => {
            fetchNewFiles(path);
            history.push(`/files/${path}`);
        };
        return (
            <React.Fragment>
                <Grid>
                    <Grid.Column computer={13} tablet={16}>
                        <BreadCrumbs currentPath={path} navigate={(newPath) => navigate(newPath)} />
                        <Responsive maxWidth={991}>
                            <ContextButtons
                                upload={openUppy}
                                createFolder={() => this.createFolder(currentPath)}
                                currentPath={currentPath}
                                mobileOnly={true}
                            />
                        </Responsive>
                        <FilesTable
                            allowCopyAndMove
                            handleKeyDown={this.handleKeyDown}
                            creatingNewFolder={this.state.creatingNewFolder}
                            creatingFolderName={this.state.creatingFolderName}
                            editFolder={this.state.editFolder}
                            renameFile={this.startEditFile}
                            updateEditFileName={this.updateEditFileName}
                            updateCreateFolderName={this.updateCreateFolderName}
                            files={shownFiles}
                            loading={loading}
                            sortingIcon={(name) => uf.getSortingIcon(this.state.lastSorting, name)}
                            addOrRemoveFile={(checked, newFile) => checkFile(checked, files, newFile)}
                            sortFiles={this.sortFilesBy}
                            favoriteFile={(filePath) => updateFiles(uf.favorite(files, filePath, Cloud))}
                            selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}
                            forceInlineButtons={true}
                            refetch={() => refetchFiles(path)}
                            fetchFiles={fetchNewFiles}
                            showFileSelector={this.props.showFileSelector}
                            setFileSelectorCallback={this.props.setFileSelectorCallback}
                            setDisallowedPaths={this.props.setDisallowedPaths}
                        >
                            <Pagination.Buttons
                                currentPage={currentFilesPage}
                                totalPages={totalPages}
                                toPage={(pageNumber) => goTo(pageNumber)}
                            />
                        </FilesTable>
                        <Pagination.EntriesPerPageSelector
                            entriesPerPage={filesPerPage}
                            onChange={(newSize) => updateFilesPerPage(newSize, files)}
                            content="Files per page"
                        />
                        <BallPulseLoading loading={loading} />
                    </Grid.Column>
                    <Responsive as={Grid.Column} computer={3} minWidth={992}>
                        <ContextBar
                            selectedFiles={selectedFiles}
                            currentPath={path}
                            createFolder={() => this.createFolder(currentPath)}
                            onClick={openUppy}
                            refetch={() => refetchFiles(path)}
                            fetchFiles={fetchNewFiles}
                            showFileSelector={this.props.showFileSelector}
                            setFileSelectorCallback={this.props.setFileSelectorCallback}
                            setDisallowedPaths={this.props.setDisallowedPaths}
                            rename={rename}
                        />
                    </Responsive>
                </Grid>
                <FileSelectorModal
                    show={this.props.fileSelectorShown}
                    onHide={() => this.props.showFileSelector(false)}
                    currentPath={this.props.fileSelectorPath}
                    fetchFiles={(path) => this.props.fetchSelectorFiles(path)}
                    loading={this.props.fileSelectorLoading}
                    onlyAllowFolders
                    canSelectFolders
                    files={this.props.fileSelectorFiles}
                    onClick={this.props.fileSelectorCallback}
                    disallowedPaths={this.props.disallowedPaths}
                />
            </React.Fragment >);
    }
}

const ContextBar = ({ currentPath, onClick, selectedFiles, createFolder, ...props }) => (
    <div>
        <ContextButtons currentPath={currentPath} upload={onClick} createFolder={createFolder} />
        <br /><br /><br />
        <FileOptions selectedFiles={selectedFiles} rename={props.rename} {...props} />
    </div>
);

const ContextButtons = ({ currentPath, upload, createFolder }) => (
    <div>
        {/* <Button color="blue" className="context-button-margin" fluid onClick={() => upload()}> Upload Files </Button> */}
        <Modal trigger={<Button color="blue" className="context-button-margin" fluid>Upload Files</Button>}>
            <Modal.Header>
                Upload Files
            </Modal.Header>

            <Modal.Content scrolling>
                <Modal.Description>
                    <Uploader location={currentPath} />
                </Modal.Description>
            </Modal.Content>
        </Modal>
        <Button basic className="context-button-margin" fluid onClick={() => createFolder()}> New folder</Button>
    </div>
);

const NoFiles = ({ noFiles, children }) =>
    noFiles ? (
        <Table.Row>
            <Table.Cell>
                <Header>There are no files in current folder</Header>
            </Table.Cell>
        </Table.Row>) : children;

export const FilesTable = (props) => {
    if (props.loading) {
        return null;
    }
    let hasCheckbox = (!!props.selectOrDeselectAllFiles);
    const checkedFilesCount = props.files.filter(file => file.isChecked).length;
    const masterCheckboxChecked = props.files.length === checkedFilesCount && props.files.length > 0;
    const indeterminate = checkedFilesCount < props.files.length && checkedFilesCount > 0;
    let masterCheckbox = (hasCheckbox) ? (
        <Checkbox
            className="hidden-checkbox checkbox-margin"
            onClick={(e, d) => props.selectOrDeselectAllFiles(d.checked)}
            checked={masterCheckboxChecked}
            indeterminate={indeterminate}
            onChange={(e) => e.stopPropagation()}
        />
    ) : null;

    let sortingFunction = (!!props.sortFiles) ? props.sortFiles : () => 0;
    let sortingIconFunction = (!!props.sortingIcon) ? props.sortingIcon : () => "";

    return (
        <Table unstackable basic="very" padded="very">
            <Table.Header>
                <NoFiles noFiles={(!props.files.length && !props.creatingNewFolder)}>
                    <Table.Row>
                        <Table.HeaderCell className="filename-row" onClick={() => sortingFunction("typeAndName", "typeAndName")}>
                            {masterCheckbox}
                            Filename
                            <Icon floated="right" name={sortingIconFunction("typeAndName")} />
                        </Table.HeaderCell>
                        <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => sortingFunction("modifiedAt", "number")}>
                            Modified
                            <Icon floated="right" name={sortingIconFunction("modifiedAt")} />
                        </Responsive>
                        <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => null}>
                            Members
                            <Icon floated="right" name={sortingIconFunction("owner")} />
                        </Responsive>
                        <Table.HeaderCell />
                    </Table.Row>
                </NoFiles>
            </Table.Header>
            <FilesList
                allowCopyAndMove={!!props.allowCopyAndMove}
                refetch={props.refetch}
                fetchFiles={props.fetchFiles}
                creatingNewFolder={props.creatingNewFolder}
                creatingFolderName={props.creatingFolderName}
                updateCreateFolderName={props.updateCreateFolderName}
                updateEditFileName={props.updateEditFileName}
                editFolder={props.editFolder}
                renameFile={props.renameFile}
                handleKeyDown={props.handleKeyDown}
                hasCheckbox={hasCheckbox}
                files={props.files}
                favoriteFile={props.favoriteFile}
                selectedFiles={props.selectedFiles}
                addOrRemoveFile={props.addOrRemoveFile}
                forceInlineButtons={props.forceInlineButtons}
                showFileSelector={props.showFileSelector}
                setFileSelectorCallback={props.setFileSelectorCallback}
                setDisallowedPaths={props.setDisallowedPaths}
            />
            <Table.Footer>
                <Table.Row>
                    <Table.Cell colSpan="4" textAlign="center">
                        {props.children}
                    </Table.Cell>
                </Table.Row>
            </Table.Footer>
        </Table>

    );
};

const CreateFolder = ({ creatingNewFolder, creatingFolderName, updateText, handleKeyDown }) => (
    !creatingNewFolder ? null : (
        <Table.Row>
            <Table.Cell>
                <Icon name="folder" color="blue" size="big" className="create-folder" />
                <Input
                    className="create-folder-input"
                    onKeyDown={(e) => handleKeyDown(e.keyCode, true)}
                    placeholder="Folder name..."
                    value={creatingFolderName ? creatingFolderName : ""}
                    onChange={(e) => updateText(e.target.value)}
                    autoFocus
                />
                <Button.Group floated="right">
                    <Button color="blue" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</Button>
                    <Button onClick={() => handleKeyDown(KeyCode.ESC, true)}>✗</Button>
                </Button.Group>
            </Table.Cell>
            <Responsive as={Table.Cell} /><Responsive as={Table.Cell} /><Table.Cell />
        </Table.Row>
    )
);

const FilesList = (props) => {
    const filesList = props.files.map((file, index) =>
        (<File
            key={index}
            index={index}
            file={file}
            allowCopyAndMove={props.allowCopyAndMove}
            handleKeyDown={props.handleKeyDown}
            beingRenamed={props.editFolder ? index === props.editFolder.index : undefined}
            updateName={props.updateEditFileName}
            renameName={props.editFolder ? props.editFolder.name : undefined}
            renameFile={props.renameFile}
            updateEditFileName={props.updateEditFileName}
            addOrRemoveFile={props.addOrRemoveFile}
            favoriteFile={props.favoriteFile}
            hasCheckbox={props.hasCheckbox}
            forceInlineButtons={props.forceInlineButtons}
            owner={uf.getOwnerFromAcls(file.acl, Cloud)}
            refetch={props.refetch}
            fetchFiles={props.fetchFiles}
            showFileSelector={props.showFileSelector}
            setFileSelectorCallback={props.setFileSelectorCallback}
            setDisallowedPaths={props.setDisallowedPaths}
        />)
    );
    return (
        <Table.Body>
            <CreateFolder
                creatingNewFolder={props.creatingNewFolder}
                creatingFolderName={props.creatingFolderName}
                updateText={props.updateCreateFolderName}
                handleKeyDown={props.handleKeyDown}
            />
            {filesList}
        </Table.Body>);
};

const File = ({ file, favoriteFile, beingRenamed, addOrRemoveFile, owner, forceInlineButtons, ...props }) => (
    <Table.Row className="file-row">
        <Table.Cell className="table-cell-padding-left">
            {(props.hasCheckbox) ? (
                <Checkbox
                    checked={file.isChecked}
                    type="checkbox"
                    className="hidden-checkbox checkbox-margin"
                    onClick={(e, { checked }) => addOrRemoveFile(checked, file)}
                />
            ) : null}
            <FileType
                type={file.type}
                path={file.path}
                link={file.link}
                updateEditFileName={props.updateEditFileName}
                handleKeyDown={props.handleKeyDown}
                beingRenamed={beingRenamed}
                renameName={props.renameName}
                update={props.updateName}
                fetchFiles={props.fetchFiles}
            />
            {uf.isProject(file) ? <Icon className="group-icon-padding" name="users" /> : null}
            {(!!favoriteFile) ?
                <Rating
                    rating={file.favorited ? 1 : 0}
                    className={`${file.favorited ? "" : "file-data"} favorite-padding`}
                    onClick={() => favoriteFile(file.path)}
                /> : null
            }
        </Table.Cell>
        <Responsive as={Table.Cell} minWidth={768}>{new Date(file.modifiedAt).toLocaleString()}</Responsive>
        <Responsive as={Table.Cell} minWidth={768}>{owner}</Responsive>
        <Table.Cell>
            <Icon className="file-data" name="share alternate" onClick={() => uf.shareFiles([file.path], Cloud)} />
            <MobileButtons
                allowCopyAndMove={props.allowCopyAndMove}
                file={file}
                forceInlineButtons={forceInlineButtons}
                rename={props.renameFile ? (path) => props.renameFile(props.index, path) : undefined}
                refetch={props.refetch}
                showFileSelector={props.showFileSelector}
                setFileSelectorCallback={props.setFileSelectorCallback}
                setDisallowedPaths={props.setDisallowedPaths}
            />
        </Table.Cell>
    </Table.Row>
);

const FileType = ({ type, path, beingRenamed, update, link, ...props }) => {
    const fileName = (
        <FileName
            type={type}
            updateEditFileName={props.updateEditFileName}
            name={uf.getFilenameFromPath(path)}
            beingRenamed={beingRenamed}
            handleKeyDown={props.handleKeyDown}
            renameName={props.renameName}
            update={update}
            link={link}
            size={"big"}
        />);
    if (type === "FILE") {
        return (<span>{fileName}</span>);
    } else {
        return beingRenamed ?
            (<span>{fileName}</span>) :
            (<Link to={`/files/${path}`} onClick={() => props.fetchFiles(path)}>
                {fileName}
            </Link>);
    }
};

const FileName = ({ name, beingRenamed, renameName, type, updateEditFileName, size, link, handleKeyDown }) => {
    const color = type === "DIRECTORY" ? "blue" : "grey";
    const icon = (
        <FileIcon
            color={color}
            name={type === "DIRECTORY" ? "folder" : uf.iconFromFilePath(name)}
            size={size} link={link}
            className="create-folder"
        />
    );
    return beingRenamed ?
        <React.Fragment>
            {icon}
            <Input
                value={renameName}
                onChange={(e) => { updateEditFileName(e)}}
                onKeyDown={(e) => { handleKeyDown(e.keyCode, false)}}
                autoFocus
            />
            <Button.Group floated="right">
                <Button color="twitter" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</Button>
                <Button onClick={() => handleKeyDown(KeyCode.ESC, true)}>✗</Button>
            </Button.Group>
        </React.Fragment> :
        <span>{icon}{name}</span>
};

const FileOptions = ({ selectedFiles, rename, ...props }) => {
    if (!selectedFiles.length) return null;
    const fileText = uf.toFileText(selectedFiles);
    const rights = uf.getCurrentRights(selectedFiles, Cloud);
    const moveDisabled = selectedFiles.some(f => uf.isFixedFolder(f.path, Cloud.homeFolder));
    const downloadDisabled = (selectedFiles.length > 1 || selectedFiles.some(f => f.sensitivityLevel === "SENSITIVE"));
    return (
        <div>
            <Header as="h3">{fileText}</Header>
            <Link to={`/fileInfo/${selectedFiles[0].path}/`} disabled={selectedFiles.length !== 1}>
                <Button className="context-button-margin" color="blue" fluid disabled={selectedFiles.length !== 1}>
                    <Icon name="settings" /> Properties
                </Button>
            </Link>
            <Button className="context-button-margin" fluid basic
                onClick={() => uf.shareFiles(selectedFiles.map(f => f.path), Cloud)}>
                <Icon name="share alternate" /> Share
            </Button>
            <Button className="context-button-margin" basic fluid disabled={downloadDisabled}

                onClick={() => uf.downloadFile(selectedFiles[0].path, Cloud)}>
                <Icon name="download" /> Download
            </Button>
            <Button className="context-button-margin" fluid basic
                onClick={() => rename()}
                disabled={rights.rightsLevel < 3 || selectedFiles.length !== 1}>
                <Icon name="edit" /> Rename
            </Button>
            <Button className="context-button-margin" fluid basic
                onClick={() => move(selectedFiles, props)}
                disabled={rights.rightsLevel < 3}>
                <Icon name="move" /> Move
            </Button>
            <Button className="context-button-margin" fluid basic
                onClick={() => copy(selectedFiles, props)}
                disabled={rights.rightsLevel < 3}>
                <Icon name="copy" /> Copy
            </Button>
            <Button className="context-button-margin" color="red" fluid
                disabled={rights.rightsLevel < 3}
                onClick={() => uf.batchDeleteFiles(selectedFiles.map((it) => it.path), Cloud, props.refetch)}>
                <Icon name="trash" /> Delete
            </Button>
        </div>
    );
};


const MobileButtons = ({ file, forceInlineButtons, rename, ...props }) => (
    <Dropdown direction="left" icon="ellipsis horizontal">
        <Dropdown.Menu>
            <Dropdown.Item onClick={() => uf.shareFiles([file.path], Cloud)}>
                Share file
            </Dropdown.Item>
            <Dropdown.Item onClick={() => uf.downloadFile(file.path, Cloud)}>
                Download
            </Dropdown.Item>
            {rename && !uf.isFixedFolder(file.path, Cloud.homeFolder) ? <Dropdown.Item onClick={() => rename(file.path)}>
                Rename file
            </Dropdown.Item> : null}
            {props.allowCopyAndMove ?
            <React.Fragment>
                <Dropdown.Item onClick={() => copy([file], props)}>
                Copy file
            </Dropdown.Item>
            {!uf.isFixedFolder(file.path, Cloud.homeFolder) ? <Dropdown.Item onClick={() => move([file], props)}>
                Move file
            </Dropdown.Item> : null}</React.Fragment>: null}
            {!uf.isFixedFolder(file.path, Cloud.homeFolder) ? <Dropdown.Item color="red" onClick={() => uf.showFileDeletionPrompt(file.path, Cloud, props.refetch)}>
                Delete file
            </Dropdown.Item> : null}
            <Dropdown.Item>
                <Link to={`/fileInfo/${file.path}/`} className="black-text">
                    Properties
                </Link>
            </Dropdown.Item>
            <EditOrCreateProject
                canBeProject={uf.isDirectory(file) && !uf.isFixedFolder(file.path, Cloud.homeFolder) && !uf.isLink(file)}
                isProject={uf.isProject(file)}
                path={file.path}
                Type={Dropdown.Item}
            />
        </Dropdown.Menu>
    </Dropdown>
);

const EditOrCreateProject = ({ canBeProject, isProject, path, Type }) => (
    canBeProject ?
        <Type onClick={isProject ? null : () => uf.createProject(path, Cloud)}>
            {isProject ?
                <Link to={`/metadata/${path}/`} className="black-text">
                    Edit Project
            </Link> :
                "Create Project"
            }
        </Type> : null
);

const copy = (files, operations) => {
    let i = 0;
    operations.showFileSelector(true);
    operations.setFileSelectorCallback((newPath) => {
        operations.showFileSelector(false);
        operations.setFileSelectorCallback(null);    
        files.forEach((f) => {
            const currentPath = f.path;
            Cloud.get(`/files/stat?path=${newPath}/${uf.getFilenameFromPath(currentPath)}`).catch(({ request }) => {
                if (request.status === 404) {
                    const newPathForFile = `${newPath}/${uf.getFilenameFromPath(currentPath)}`;
                    Cloud.post(`/files/copy?path=${currentPath}&newPath=${newPathForFile}`).catch(() =>
                        uf.failureNotification(`An error occured copying file ${currentPath}.`)
                    ).then(() => {
                        if (++i === files.length) { operations.refetch(); uf.successNotification("File copied."); }
                    });
                } else {
                    uf.failureNotification(`An error occurred, please try again later.`)
                }
            }
        )});
    });
};

const move = (files, operations) => {
    operations.showFileSelector(true);
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.setFileSelectorCallback((newPath) => {
        files.forEach((f) => {
            const currentPath = f.path;
            const newPathForFile = `${newPath}/${uf.getFilenameFromPath(currentPath)}`;
            Cloud.post(`/files/move?path=${currentPath}&newPath=${newPathForFile}`).then(() => {
                uf.successNotification(`${uf.getFilenameFromPath(currentPath)} moved to ${uf.getParentPath(newPathForFile)}`);
                operations.refetch();
            }).catch(() => uf.failureNotification("An error occurred, please try again later"));
            operations.showFileSelector(false);
            operations.setFileSelectorCallback(null);
            operations.setDisallowedPaths([]);
        })
    })
};

Files.propTypes = {
    files: PropTypes.array.isRequired,
    filesPerPage: PropTypes.number.isRequired,
    currentFilesPage: PropTypes.number.isRequired,
    favFilesCount: PropTypes.number.isRequired,
    checkedFilesCount: PropTypes.number.isRequired,
    loading: PropTypes.bool.isRequired,
    path: PropTypes.string.isRequired,
    uppy: PropTypes.object,
    uppyOpen: PropTypes.bool.isRequired,
};

const mapStateToProps = (state) => {
    const { files, filesPerPage, currentFilesPage, loading, path, fileSelectorFiles, fileSelectorPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths } = state.files;
    const { uppyFiles, uppyFilesOpen } = state.uppy;
    const favFilesCount = files.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const checkedFilesCount = files.filter(file => file.isChecked).length; // HACK to ensure changes to file checkings are rendered.
    return {
        files, filesPerPage, currentFilesPage, loading, path, uppy: uppyFiles, uppyOpen: uppyFilesOpen, favFilesCount,
        checkedFilesCount, fileSelectorFiles, fileSelectorPath, fileSelectorShown, fileSelectorCallback, disallowedPaths
    }
};

const mapDispatchToProps = (dispatch) => ({
    fetchNewFiles: (path) => {
        dispatch(Actions.updatePath(path));
        dispatch(Actions.setLoading(true));
        dispatch(Actions.fetchFiles(path, uf.sortFilesByTypeAndName, true))
    },
    refetchFiles: (path) => {
        dispatch(Actions.fetchFiles(path, uf.sortFilesByTypeAndName, true));
    },
    updatePath: (path) => dispatch(updatePath(path)),
    fetchSelectorFiles: (path) => {
        dispatch(Actions.fetchFileselectorFiles(path));
    },
    showFileSelector: (open) => {
        dispatch(Actions.fileSelectorShown(open));
    },
    setFileSelectorCallback: (callback) =>
        dispatch(Actions.setFileSelectorCallback(callback)),
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    checkFile: (checked, files, newFile) => {
        files.find(file => file.path === newFile.path).isChecked = checked;
        dispatch(Actions.updateFiles(files));
    },
    goToPage: (pageNumber, files) => {
        files.forEach(f => f.isChecked = false);
        dispatch(Actions.updateFiles(files));
        dispatch(Actions.toPage(pageNumber));
    },
    updateFiles: (files) =>
        dispatch(Actions.updateFiles(files)),
    updateFilesPerPage: (newSize, files) =>
        dispatch(Actions.updateFilesPerPage(newSize, files)),
    openUppy: () => dispatch(changeUppyFilesOpen(true)),
    setDisallowedPaths: (disallowedPaths) =>
        dispatch(Actions.setDisallowedPaths(disallowedPaths))
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);
