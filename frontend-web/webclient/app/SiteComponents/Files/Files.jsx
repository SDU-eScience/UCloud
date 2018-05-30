import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
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
import { FileSelectorModal } from "./FileSelector";
import { FileIcon } from "../UtilityComponents";
import { Uploader } from "../Uploader";

class Files extends React.Component {
    constructor(props) {
        super(props);
        const urlPath = props.match.params[0];
        const { history, fetchNewFiles } = props;
        if (urlPath) {
            fetchNewFiles(urlPath);
        } else {
            fetchNewFiles(Cloud.homeFolder);
            history.push(`/files/${Cloud.homeFolder}/`);
        }
        this.state = {
            lastSorting: {
                name: "typeAndName",
                asc: true
            },
            creatingNewFolder: false,
            editFolderIndex: -1
        };
        this.checkAllFiles = this.checkAllFiles.bind(this);
        this.sortFilesBy = this.sortFilesBy.bind(this);
        this.handleKeyDown = this.handleKeyDown.bind(this);
        this.resetFolderEditing = this.resetFolderEditing.bind(this);
        this.startEditFile = this.startEditFile.bind(this);
    }

    resetFolderEditing() {
        this.setState(() => ({
            creatingNewFolder: false,
            editFolderIndex: -1
        }));
    }

    handleKeyDown(value, newFolder, name) {
        if (value === KeyCode.ESC) {
            this.resetFolderEditing();
        } else if (value === KeyCode.ENTER) {
            const { path, refetchFiles, files } = this.props;
            const fileNames = files.map((it) => uf.getFilenameFromPath(it.path))
            if (newFolder) {
                if (uf.isInvalidPathName(name, fileNames)) { return }
                const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
                name ? Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
                    if (uf.inSuccessRange(request.status)) {
                        this.resetFolderEditing();
                        refetchFiles(path);
                    }
                }).catch((failure) =>
                    this.resetFolderEditing()
                ) : this.resetFolderEditing();
            } else {
                if (uf.isInvalidPathName(name, fileNames)) { return }
                const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
                const originalFilename = files[this.state.editFolderIndex].path;
                name ? Cloud.post(`/files/move?path=${originalFilename}&newPath=${directoryPath}`)
                    .then(({ request }) => {
                        if (uf.inSuccessRange(request.status)) {
                            // TODO Overwrite filename;
                            this.resetFolderEditing();
                            refetchFiles(this.props.path);
                        }
                    }).catch((failure) =>
                        this.resetFolderEditing() // TODO Handle failure
                    ) : this.resetFolderEditing();
            }
        }
    }

    checkAllFiles(checked) {
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
        this.resetFolderEditing();
        this.setState(() => ({ creatingNewFolder: true }));
    }

    startEditFile(index) {
        this.resetFolderEditing();
        this.setState(() => ({ editFolderIndex: index }));
    }

    render() {
        // PROPS
        const { files, filesPerPage, currentFilesPage, path, loading, history, currentPath, refetchFiles,
            fetchNewFiles, checkFile, updateFilesPerPage, updateFiles } = this.props;
        const totalPages = Math.ceil(this.props.files.length / filesPerPage);
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage);
        // Lambdas
        const goTo = (pageNumber) => {
            this.props.goToPage(pageNumber, files);
            this.resetFolderEditing();
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
                        <DefaultLoading loading={loading} color="black" size="big" />
                        <Responsive maxWidth={991}>
                            <ContextButtons
                                createFolder={() => this.createFolder(currentPath)}
                                currentPath={currentPath}
                                mobileOnly={true}
                            />
                        </Responsive>
                        <FilesTable
                            allowCopyAndMove
                            handleKeyDown={this.handleKeyDown}
                            creatingNewFolder={this.state.creatingNewFolder}
                            editFolderIndex={this.state.editFolderIndex}
                            renameFile={this.startEditFile}
                            files={shownFiles}
                            loading={loading}
                            sortingIcon={(name) => uf.getSortingIcon(this.state.lastSorting, name)}
                            checkFile={(checked, newFile) => checkFile(checked, files, newFile)}
                            sortFiles={this.sortFilesBy}
                            onFavoriteFile={(filePath) => updateFiles(uf.favorite(files, filePath, Cloud))}
                            checkAllFiles={this.checkAllFiles}
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
                    </Grid.Column>
                    <Responsive as={Grid.Column} computer={3} minWidth={992}>
                        <ContextBar
                            selectedFiles={selectedFiles}
                            currentPath={path}
                            createFolder={() => this.createFolder(currentPath)}
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
                    setSelectedFile={this.props.fileSelectorCallback}
                    disallowedPaths={this.props.disallowedPaths}
                />
            </React.Fragment>);
    }
}

const ContextBar = ({ currentPath, selectedFiles, createFolder, ...props }) => (
    <div>
        <ContextButtons currentPath={currentPath} createFolder={createFolder} />
        <br /><br /><br />
        <FileOptions selectedFiles={selectedFiles} rename={props.rename} {...props} />
    </div>
);

const ContextButtons = ({ currentPath, upload, createFolder }) => (
    <div>
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

export function FilesTable({ allowCopyAndMove = false, refetch = () => null, ...props }) {
    if (props.loading) { return null; }
    let hasCheckbox = (!!props.checkAllFiles);
    const checkedFilesCount = props.files.filter(file => file.isChecked).length;
    const masterCheckboxChecked = props.files.length === checkedFilesCount && props.files.length > 0;
    const indeterminate = checkedFilesCount < props.files.length && checkedFilesCount > 0;
    const masterCheckbox = (hasCheckbox) ? (
        <Checkbox
            className="hidden-checkbox checkbox-margin"
            onClick={(e, d) => props.checkAllFiles(d.checked)}
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
                allowCopyAndMove={allowCopyAndMove}
                refetch={refetch}
                fetchFiles={props.fetchFiles}
                creatingNewFolder={props.creatingNewFolder}
                editFolderIndex={props.editFolderIndex}
                renameFile={props.renameFile}
                handleKeyDown={props.handleKeyDown}
                hasCheckbox={hasCheckbox}
                files={props.files}
                onFavoriteFile={props.onFavoriteFile}
                selectedFiles={props.selectedFiles}
                checkFile={props.checkFile}
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
}

const CreateFolder = ({ creatingNewFolder, handleKeyDown }) => (
    !creatingNewFolder ? null : (
        <Table.Row>
            <Table.Cell>
                <Icon name="folder" color="blue" size="big" className="create-folder" />
                <Input
                    transparent
                    className="create-folder-input"
                    onKeyDown={(e) => handleKeyDown(e.keyCode, true, e.target.value)}
                    placeholder="Folder name..."
                    autoFocus
                />
            </Table.Cell>
            <Responsive as={Table.Cell} /><Responsive as={Table.Cell} /><Table.Cell />
        </Table.Row>
    )
);

function FilesList(props) {
    const filesList = props.files.map((file, index) =>
        (<FileRow
            key={index}
            index={index}
            file={file}
            allowCopyAndMove={props.allowCopyAndMove}
            handleKeyDown={props.handleKeyDown}
            beingRenamed={props.editFolderIndex === index}
            renameFile={props.renameFile}
            checkFile={props.checkFile}
            onFavoriteFile={props.onFavoriteFile}
            hasCheckbox={props.hasCheckbox}
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
                handleKeyDown={props.handleKeyDown}
            />
            {filesList}
        </Table.Body>);
}

const PredicatedCheckbox = ({ predicate, checked, onClick }) => (
    predicate ? (
        <Checkbox
            checked={checked}
            type="checkbox"
            className="hidden-checkbox checkbox-margin"
            onClick={onClick}
        />
    ) : null
);

const PredicatedRating = ({ predicate, file, onClick, rating }) =>
    predicate ? (
        <Rating
            rating={rating}
            className={`${file.favorited ? "" : "file-data"} favorite-padding`}
            onClick={onClick}
        />
    ) : null;

const GroupIcon = ({ isProject }) => isProject ? (<Icon className="group-icon-padding" name="users" />) : null;

const FileRow = ({ file, onFavoriteFile, beingRenamed, checkFile, owner, ...props }) => (
    <Table.Row className="file-row">
        <Table.Cell className="table-cell-padding-left">
            <PredicatedCheckbox
                predicate={props.hasCheckbox}
                checked={file.isChecked}
                onClick={(e, { checked }) => checkFile(checked, file)}
            />
            <File
                file={file}
                handleKeyDown={props.handleKeyDown}
                beingRenamed={beingRenamed}
                fetchFiles={props.fetchFiles}
            />
            <GroupIcon isProject={uf.isProject(file)} />
            <PredicatedRating
                predicate={!!onFavoriteFile}
                file={file}
                rating={file.favorited ? 1 : 0}
                className={`${file.favorited ? "" : "file-data"} favorite-padding`}
                onClick={() => onFavoriteFile(file.path)}
            />
        </Table.Cell>
        <Responsive as={Table.Cell} minWidth={768}>{new Date(file.modifiedAt).toLocaleString()}</Responsive>
        <Responsive as={Table.Cell} minWidth={768}>{owner}</Responsive>
        <Table.Cell>
            <Icon className="file-data" name="share alternate" onClick={() => uf.shareFiles([file.path], Cloud)} />
            <MobileButtons
                allowCopyAndMove={props.allowCopyAndMove}
                file={file}
                rename={props.renameFile ? () => props.renameFile(props.index) : undefined}
                refetch={props.refetch}
                showFileSelector={props.showFileSelector}
                setFileSelectorCallback={props.setFileSelectorCallback}
                setDisallowedPaths={props.setDisallowedPaths}
            />
        </Table.Cell>
    </Table.Row>
);

function File({ file, beingRenamed, update, link, ...props }) {
    const fileName = (
        <FileNameAndIcon
            type={file.type}
            name={uf.getFilenameFromPath(file.path)}
            beingRenamed={beingRenamed}
            handleKeyDown={props.handleKeyDown}
            link={file.link}
            size={"big"}
        />);
    if (file.type === "FILE") {
        return (<span>{fileName}</span>);
    } else {
        return beingRenamed ?
            (<span>{fileName}</span>) :
            (<Link to={`/files/${file.path}`} onClick={() => props.fetchFiles(file.path)}>
                {fileName}
            </Link>);
    }
};

function FileNameAndIcon({ name, beingRenamed, type, size, link, handleKeyDown }) {
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
                defaultValue={name}
                onKeyDown={(e) => handleKeyDown(e.keyCode, false, e.target.value)}
                autoFocus
                transparent
                action={<Button onClick={() => handleKeyDown(KeyCode.ESC, false)}>✗</Button>}
            />
        </React.Fragment> :
        <span>{icon}{name}</span>
};

function FileOptions({ selectedFiles, rename, ...props }) {
    if (!selectedFiles.length) return null;
    const fileText = uf.toFileText(selectedFiles);
    const rights = uf.getCurrentRights(selectedFiles, Cloud);
    const moveDisabled = selectedFiles.some(f => uf.isFixedFolder(f.path, Cloud.homeFolder));
    const downloadDisabled = (selectedFiles.length > 1 || selectedFiles.some(f => f.sensitivityLevel === "SENSITIVE"));
    return (
        <div>
            <Header as="h3">{fileText}</Header>
            <Link to={`/fileInfo/${selectedFiles[0].path}/`} disabled={selectedFiles.length !== 1}>
                <Button className="context-button-margin" color="blue" fluid basic
                    disabled={selectedFiles.length !== 1}
                    icon="settings" content="Properties"
                />
            </Link>
            <Button className="context-button-margin" fluid basic
                onClick={() => uf.shareFiles(selectedFiles.map(f => f.path), Cloud)}
                icon="share alternate" content="Share"
            />
            <Button className="context-button-margin" basic fluid disabled={downloadDisabled}
                onClick={() => uf.downloadFiles(selectedFiles.map(f => f.path), Cloud)}
                icon="download" content="Download"
            />
            <Button className="context-button-margin" fluid basic
                onClick={() => rename()}
                disabled={rights.rightsLevel < 3 || selectedFiles.length !== 1}
                icon="edit" content="Rename"
            />
            <Button className="context-button-margin" fluid basic
                onClick={() => move(selectedFiles, props)}
                disabled={rights.rightsLevel < 3 || moveDisabled}
                icon="move" content="Move"
            />
            <Button className="context-button-margin" fluid basic
                onClick={() => copy(selectedFiles, props)}
                disabled={rights.rightsLevel < 3}
                icon="copy" content="Copy"
            />
            <EditOrCreateProjectButton file={selectedFiles[0]} disabled={selectedFiles.length > 1} />
            <Button className="context-button-margin" color="red" fluid basic
                disabled={rights.rightsLevel < 3}
                onClick={() => uf.batchDeleteFiles(selectedFiles.map((it) => it.path), Cloud, props.refetch)}
                icon="trash" content="Delete"
            />
        </div>
    );
};

const PredicatedDropDownItem = ({ predicate, content, onClick }) =>
    predicate ? <Dropdown.Item content={content} onClick={onClick} /> : null;

const MobileButtons = ({ file, Buttons, rename, ...props }) => (
    <Dropdown direction="left" icon="ellipsis horizontal">
        <Dropdown.Menu>
            <Dropdown.Item content="Share file" onClick={() => uf.shareFiles([file.path], Cloud)} />
            <Dropdown.Item content="Download" onClick={() => uf.downloadFiles([file.path], Cloud)} />
            <PredicatedDropDownItem
                predicate={rename && !uf.isFixedFolder(file.path, Cloud.homeFolder)}
                onClick={() => rename(file.path)}
                content="Rename file"
            />
            {props.allowCopyAndMove ?
                <React.Fragment>
                    <Dropdown.Item content="Copy file" onClick={() => copy([file], props)} />
                    <PredicatedDropDownItem
                        predicate={!uf.isFixedFolder(file.path, Cloud.homeFolder)}
                        onClick={() => move([file], props)} content="Move file"
                    />
                </React.Fragment> : null}
            <PredicatedDropDownItem
                predicate={!uf.isFixedFolder(file.path, Cloud.homeFolder)}
                content="Delete file"
                onClick={() => uf.showFileDeletionPrompt(file.path, Cloud, props.refetch)}
            />
            <Dropdown.Item>
                <Link to={`/fileInfo/${file.path}/`} className="black-text">
                    Properties
                </Link>
            </Dropdown.Item>
            <EditOrCreateProject file={file} />
        </Dropdown.Menu>
    </Dropdown>
);

function EditOrCreateProjectButton({ file, disabled }) {
    const canBeProject = uf.isDirectory(file) && !uf.isFixedFolder(file.path, Cloud.homeFolder) && !uf.isLink(file);
    const projectButton = (
        <Button className="context-button-margin" fluid basic icon="users" disabled={disabled || !canBeProject}
            content={uf.isProject(file) ? "Edit Project" : "Create Project"}
            onClick={uf.isProject(file) ? null : () => uf.createProject(file.path, Cloud)}
        />
    );
    if (uf.isProject(file)) {
        return (
            <Link to={`/metadata/${file.path}/`} className="context-button-margin">
                {projectButton}
            </Link>);
    } else {
        return projectButton;
    }
}

function EditOrCreateProject({ file }) {
    const canBeProject = uf.isDirectory(file) && !uf.isFixedFolder(file.path, Cloud.homeFolder) && !uf.isLink(file);
    if (!canBeProject) { return null; }
    const projectLink = uf.isProject(file) ? (
        <Link to={`/metadata/${file.path}/`} className="black-text">
            Edit Project
        </Link>) : "Create Project";
    return (
        <Dropdown.Item onClick={uf.isProject(file) ? null : () => uf.createProject(file.path, Cloud)}>
            {projectLink}
        </Dropdown.Item>)
}

function copy(files, operations) {
    let i = 0;
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.showFileSelector(true);
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        operations.showFileSelector(false);
        operations.setFileSelectorCallback(null);
        operations.setDisallowedPaths([]);
        files.forEach((f) => {
            const currentPath = f.path;
            Cloud.get(`/files/stat?path=${newPath}/${uf.getFilenameFromPath(currentPath)}`).catch(({ request }) => {
                if (request.status === 404) {
                    const newPathForFile = `${newPath}/${uf.getFilenameFromPath(currentPath)}`;
                    Cloud.post(`/files/copy?path=${currentPath}&newPath=${newPathForFile}`).then(() => i++).catch(() =>
                        uf.failureNotification(`An error occured copying file ${currentPath}.`)
                    ).then(() => {
                        if (i === files.length) { operations.refetch(); uf.successNotification("File copied."); }
                    });
                } else {
                    uf.failureNotification(`An error occurred, please try again later.`)
                }
            })
        });
    });
};

function move(files, operations) {
    operations.showFileSelector(true);
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        files.forEach((f) => {
            let currentPath = f.path;
            let newPathForFile = `${newPath}/${uf.getFilenameFromPath(currentPath)}`;
            Cloud.post(`/files/move?path=${currentPath}&newPath=${newPathForFile}`).then(() => {
                const fromPath = uf.getFilenameFromPath(currentPath);
                const toPath = uf.replaceHomeFolder(newPathForFile, Cloud.homeFolder);
                uf.successNotification(`${fromPath} moved to ${toPath}`);
                operations.refetch();
            }).catch(() => uf.failureNotification("An error occurred, please try again later"));
            operations.showFileSelector(false);
            operations.setFileSelectorCallback(null);
            operations.setDisallowedPaths([]);
        })
    })
}

Files.propTypes = {
    files: PropTypes.array.isRequired,
    filesPerPage: PropTypes.number.isRequired,
    currentFilesPage: PropTypes.number.isRequired,
    favFilesCount: PropTypes.number.isRequired,
    checkedFilesCount: PropTypes.number.isRequired,
    loading: PropTypes.bool.isRequired,
    path: PropTypes.string.isRequired,
};

const mapStateToProps = (state) => {
    const { files, filesPerPage, currentFilesPage, loading, path, fileSelectorFiles, fileSelectorPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths } = state.files;
    const favFilesCount = files.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const checkedFilesCount = files.filter(file => file.isChecked).length; // HACK to ensure changes to file checkings are rendered.
    return {
        files, filesPerPage, currentFilesPage, loading, path, favFilesCount,
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
        dispatch(Actions.setLoading(true));
        dispatch(Actions.fetchFiles(path, uf.sortFilesByTypeAndName, true));
    },
    updatePath: (path) => dispatch(updatePath(path)),
    fetchSelectorFiles: (path) => dispatch(Actions.fetchFileselectorFiles(path)),
    showFileSelector: (open) => dispatch(Actions.fileSelectorShown(open)),
    setFileSelectorCallback: (callback) =>
        dispatch(Actions.setFileSelectorCallback(callback)),
    checkFile: (checked, files, newFile) => {
        files.find(file => file.path === newFile.path).isChecked = checked;
        dispatch(Actions.updateFiles(files));
    },
    goToPage: (pageNumber, files) => {
        files.forEach(f => f.isChecked = false);
        dispatch(Actions.updateFiles(files));
        dispatch(Actions.toPage(pageNumber));
    },
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    updateFiles: (files) => dispatch(Actions.updateFiles(files)),
    updateFilesPerPage: (newSize, files) => dispatch(Actions.updateFilesPerPage(newSize, files)),
    setDisallowedPaths: (disallowedPaths) => dispatch(Actions.setDisallowedPaths(disallowedPaths))
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);