import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import { Modal, Dropdown, Button, Icon, Table, Header, Input, Grid, Responsive, Checkbox } from "semantic-ui-react";
import { dateToString } from "../../Utilities/DateUtilities";
import * as Pagination from "../Pagination";
import { BreadCrumbs } from "../Breadcrumbs/Breadcrumbs";
import * as uf from "../../UtilityFunctions";
import { KeyCode } from "../../DefaultObjects";
import * as Actions from "../../Actions/Files";
import { updatePageTitle } from "../../Actions/Status";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon } from "../UtilityComponents";
import { Uploader } from "../Uploader";
import { File } from "../../types/types";
import { History } from "history";

interface FilesProps {
    // Redux Props
    path: string
    files: File[]
    match: { params: string[] }
    filesPerPage: number // FIXME Remove when serverside pagination is introduced
    currentFilesPage: number // FIXME Remove when serverside pagination is introduced
    loading: boolean
    fileSelectorShown: boolean
    fileSelectorLoading: boolean
    disallowedPaths: string[]
    fileSelectorCallback: Function
    fileSelectorPath: string
    fileSelectorFiles: File[]
    history: History

    // Redux operations
    fetchFiles: (path: string, silent: boolean) => void
    fetchSelectorFiles: (path: string) => void
    showFilesSelector: (open: boolean) => void
    setFileSelectorCallback: (callback: Function) => void
    checkFile: (checked: boolean, files: File[], newFile: File) => void
    goToPage: (pageNumber: number, files: File[]) => void
    setPageTitle: () => void
    updateFiles: (files: File[]) => void
    updateFilesPerPage: (pageSize: number, files: File[]) => void
    showFileSelector: (open: boolean) => void
    setDisallowedPaths: (disallowedPaths: string[]) => void
}

interface FilesState { // FIXME Remove this by adding state to props
    lastSorting: {
        name: string
        asc: boolean
    }
    creatingNewFolder: boolean
    editFolderIndex: number
}

class Files extends React.Component<FilesProps, FilesState> {
    constructor(props) {
        super(props);
        const urlPath = props.match.params[0];
        const { history } = props;
        if (!urlPath) {
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
        this.props.setPageTitle();
    }

    resetFolderEditing = (): void =>
        this.setState(() => ({
            creatingNewFolder: false,
            editFolderIndex: -1
        }));

    componentDidMount() {
        this.props.fetchFiles(this.props.match.params[0], false);
    }

    handleKeyDown = (value: number, newFolder: boolean, name: string): void => {
        if (value === KeyCode.ESC) {
            this.resetFolderEditing();
        } else if (value === KeyCode.ENTER) {
            const { path, fetchFiles, files } = this.props;
            const fileNames = files.map((it) => uf.getFilenameFromPath(it.path))
            if (uf.isInvalidPathName(name, fileNames)) return;
            const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
            if (newFolder) {
                Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
                    if (uf.inSuccessRange(request.status)) {
                        this.resetFolderEditing();
                        fetchFiles(path, true);
                    }
                }).catch(() => this.resetFolderEditing());
            } else {
                // FIXME When transitioned to server-side pagination, remove offset below
                const originalFilename = files[this.state.editFolderIndex + this.props.filesPerPage * this.props.currentFilesPage].path;
                Cloud.post(`/files/move?path=${originalFilename}&newPath=${directoryPath}`)
                    .then(({ request }) => {
                        if (uf.inSuccessRange(request.status)) {
                            this.resetFolderEditing();
                            fetchFiles(this.props.path, true);
                        }
                    }).catch(() => this.resetFolderEditing())
            }
        }
    }

    shouldComponentUpdate(nextProps, _nextState) {
        if (nextProps.path !== nextProps.match.params[0]) {
            this.props.fetchFiles(nextProps.match.params[0], false);
        }
        return true;
    }

    checkAllFiles = (checked: boolean) => {
        const { currentFilesPage, filesPerPage, files, updateFiles } = this.props;
        files.forEach(file => file.isChecked = false);
        if (checked) {
            files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
                .forEach(file => file.isChecked = true);
        }
        updateFiles(files);
    }

    sortFilesBy = (name: string, type: string) => {
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

    createFolder = () => {
        this.resetFolderEditing();
        this.setState(() => ({ creatingNewFolder: true }));
    }

    startEditFile = (index: number) => {
        this.resetFolderEditing();
        this.setState(() => ({ editFolderIndex: index }));
    }

    render() {
        // PROPS
        const { files, filesPerPage, currentFilesPage, path, loading, history, fetchFiles,
            checkFile, updateFilesPerPage, updateFiles } = this.props;
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage);
        // Master Checkbox
        const checkedFilesCount = shownFiles.filter(file => file.isChecked).length;
        const masterCheckboxChecked = shownFiles.length === checkedFilesCount && files.length > 0;
        const indeterminate = checkedFilesCount < shownFiles.length && checkedFilesCount > 0;
        // Lambdas
        const goTo = (pageNumber) => {
            this.props.goToPage(pageNumber, files);
            this.resetFolderEditing();
        };
        const selectedFiles = shownFiles.filter(file => file.isChecked);
        const rename = () => {
            const firstSelectedFile = selectedFiles[0];
            this.startEditFile(files.findIndex((f) => f.path === firstSelectedFile.path));
        };
        const navigate = (path) => history.push(`/files/${path}`);
        const projectNavigation = (projectPath) => history.push(`/metadata/${projectPath}`);
        return (
            <Grid>
                <Grid.Column computer={13} tablet={16}>
                    <Grid.Row>
                        <Responsive
                            as={ContextButtons}
                            maxWidth={991}
                            createFolder={() => this.createFolder()}
                            currentPath={path}
                        />
                        <BreadCrumbs currentPath={path} navigate={(newPath) => navigate(newPath)} />
                        <Icon className="float-right" link circular name="sync" onClick={() => fetchFiles(path, true)} loading={loading} />
                    </Grid.Row>
                    <Pagination.List
                        currentPage={currentFilesPage}
                        itemsPerPage={filesPerPage}
                        loading={loading}
                        pageRenderer={(page) => (
                            <FilesTable
                                sortingIcon={(name: string) => uf.getSortingIcon(this.state.lastSorting, name)}
                                sortFiles={this.sortFilesBy}
                                masterCheckBox={
                                    <Checkbox
                                        className="hidden-checkbox checkbox-margin"
                                        onClick={(_e, d) => this.checkAllFiles(d.checked)}
                                        checked={masterCheckboxChecked}
                                        indeterminate={indeterminate}
                                        onChange={(e) => e.stopPropagation()}
                                    />}
                                allowCopyAndMove
                                creatingNewFolder={this.state.creatingNewFolder}
                                handleKeyDown={this.handleKeyDown}
                                files={page.items}
                                onCheckFile={checkFile}
                                fetchFiles={fetchFiles}
                                path={path}
                                onFavoriteFile={(filePath) => updateFiles(uf.favorite(files, filePath, Cloud))}
                                editFolderIndex={this.state.editFolderIndex}
                                projectNavigation={projectNavigation}
                                startEditFile={this.startEditFile}
                                setDisallowedPaths={this.props.setDisallowedPaths}
                                setFileSelectorCallback={this.props.setFileSelectorCallback}
                                showFileSelector={this.props.showFileSelector}
                            />
                        )}
                        onItemsPerPageChanged={(pageSize) => { this.resetFolderEditing(); updateFilesPerPage(pageSize, files) }}
                        results={{ items: shownFiles, itemsInTotal: files.length, itemsPerPage: filesPerPage, pageNumber: currentFilesPage }}
                        onPageChanged={(pageNumber) => goTo(pageNumber)}
                    />
                </Grid.Column>
                <Responsive as={Grid.Column} computer={3} minWidth={992}>
                    <ContextBar
                        selectedFiles={selectedFiles}
                        currentPath={path}
                        createFolder={() => this.createFolder()}
                        refetch={() => fetchFiles(path, true)}
                        fetchFiles={fetchFiles}
                        showFileSelector={this.props.showFileSelector}
                        setFileSelectorCallback={this.props.setFileSelectorCallback}
                        setDisallowedPaths={this.props.setDisallowedPaths}
                        rename={rename}
                        projectNavigation={projectNavigation}
                    />
                </Responsive>
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
            </Grid>);
    }
}

export const FilesTable = ({
    files, masterCheckBox = null, showFileSelector, setFileSelectorCallback, setDisallowedPaths, startEditFile = null,
    sortingIcon, editFolderIndex = -1, sortFiles, handleKeyDown, onCheckFile, fetchFiles,
    projectNavigation = null, path, creatingNewFolder = false, allowCopyAndMove = false, onFavoriteFile
}) => {
    return (
        <Table unstackable basic="very" padded="very">
            <FilesTableHeader masterCheckBox={masterCheckBox} sortingIcon={sortingIcon} sortFiles={sortFiles} />
            <Table.Body>
                <CreateFolder creatingNewFolder={creatingNewFolder} handleKeyDown={handleKeyDown} />
                {files.map((f, i) => (
                    <Table.Row className="file-row" key={i}>
                        <FilenameAndIcons
                            file={f}
                            onFavoriteFile={(filePath) => onFavoriteFile(filePath)}
                            beingRenamed={editFolderIndex === i}
                            hasCheckbox={masterCheckBox !== null}
                            onKeyDown={handleKeyDown}
                            onCheckFile={(checked, newFile) => onCheckFile(checked, files, newFile)}
                        />
                        <Responsive as={Table.Cell} minWidth={768}>{dateToString(f.modifiedAt)}</Responsive>
                        <Responsive as={Table.Cell} minWidth={768}>{uf.getOwnerFromAcls(f.acl)}</Responsive>
                        <MobileButtons
                            projectNavigation={projectNavigation}
                            allowCopyAndMove={allowCopyAndMove}
                            file={f}
                            rename={!!startEditFile ? () => startEditFile(i) : null}
                            refetch={() => fetchFiles(path, true)}
                            showFileSelector={showFileSelector}
                            setFileSelectorCallback={setFileSelectorCallback}
                            setDisallowedPaths={setDisallowedPaths}
                        />
                    </Table.Row>)
                )}
            </Table.Body>
        </Table>
    )
}

const FilesTableHeader = ({ sortingIcon, sortFiles, masterCheckBox = null }) => (
    <Table.Header>
        <Table.Row>
            <Table.HeaderCell className="filename-row" onClick={() => sortFiles("typeAndName", "typeAndName")}>
                {masterCheckBox}
                Filename
                <Icon floated="right" name={sortingIcon("typeAndName")} />
            </Table.HeaderCell>
            <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => sortFiles("modifiedAt", "number")}>
                Modified
                <Icon floated="right" name={sortingIcon("modifiedAt")} />
            </Responsive>
            <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => null}>
                Members
                <Icon floated="right" name={sortingIcon("owner")} />
            </Responsive>
            <Table.HeaderCell />
        </Table.Row>
    </Table.Header>
);

const ContextBar = ({ currentPath, selectedFiles, createFolder, ...props }) => (
    <div>
        <ContextButtons refetch={props.refetch} currentPath={currentPath} createFolder={createFolder} />
        <br /><br /><br />
        <FileOptions projectNavigation={props.projectNavigation} selectedFiles={selectedFiles} rename={props.rename} {...props} />
    </div>
);

const ContextButtons = ({ currentPath, createFolder, refetch }) => (
    <div>
        <Modal trigger={<Button color="blue" className="context-button-margin" fluid>Upload Files</Button>}>
            <Modal.Header content="Upload Files" />
            <Modal.Content scrolling>
                <Modal.Description>
                    <Uploader location={currentPath} onFilesUploaded={refetch} />
                </Modal.Description>
            </Modal.Content>
        </Modal>
        <Button basic className="context-button-margin" fluid onClick={() => createFolder()} content="New folder" />
    </div>
);

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

const PredicatedCheckbox = ({ predicate, checked, onClick }) =>
    predicate ? (
        <Checkbox
            checked={checked}
            type="checkbox"
            className="hidden-checkbox checkbox-margin"
            onClick={onClick}
        />
    ) : null;

const PredicatedFavorite = ({ predicate, file, onClick }) =>
    predicate ? (
        <Icon
            color="blue"
            name={file.favorited ? "star" : "star outline"}
            className={`${file.favorited ? "" : "file-data"} favorite-padding`}
            onClick={onClick}
        />
    ) : null;

const GroupIcon = ({ isProject }: { isProject: boolean }) => isProject ? (<Icon className="group-icon-padding" name="users" />) : null;

export function FilenameAndIcons({ file, beingRenamed = null, size = "big", onKeyDown = null, onCheckFile = null, hasCheckbox = false, onFavoriteFile = null }) {
    const color = file.type === "DIRECTORY" ? "blue" : "grey";
    const fileName = uf.getFilenameFromPath(file.path);
    const nameLink = (uf.isDirectory(file) ?
        <Link to={`/files/${file.path}`}>
            {fileName}
        </Link> : fileName);
    const checkbox = <PredicatedCheckbox predicate={hasCheckbox} checked={file.isChecked} onClick={(_e, { checked }) => onCheckFile(checked, file)} />
    const icon = (
        <FileIcon
            color={color}
            name={file.type === "DIRECTORY" ? "folder" : uf.iconFromFilePath(file.path)}
            size={size} link={file.link}
            className="create-folder"
        />
    );
    return beingRenamed ?
        <Table.Cell className="table-cell-padding-left">
            {checkbox}
            {icon}
            <Input
                defaultValue={fileName}
                onKeyDown={(e) => onKeyDown(e.keyCode, false, e.target.value)}
                autoFocus
                transparent
                action={<Button onClick={() => onKeyDown(KeyCode.ESC, false)}>✗</Button>}
            />
        </Table.Cell> :
        <Table.Cell className="table-cell-padding-left">
            {checkbox}
            {icon}{nameLink}
            <GroupIcon isProject={uf.isProject(file)} />
            <PredicatedFavorite predicate={!!onFavoriteFile} file={file} onClick={() => onFavoriteFile(file.path)} />
        </Table.Cell>
};

function FileOptions({ selectedFiles, rename, ...props }) {
    if (!selectedFiles.length) return null;
    const fileText = uf.toFileText(selectedFiles);
    const rights = uf.getCurrentRights(selectedFiles, Cloud);
    const moveDisabled = selectedFiles.some(f => uf.isFixedFolder(f.path, Cloud.homeFolder));
    const downloadDisabled = (selectedFiles.length > 1 || selectedFiles.some(f => f.sensitivityLevel === "SENSITIVE")); // FIXME Should be function
    return (
        <div>
            <Header as="h3">{fileText}</Header>
            <Button className="context-button-margin" color="blue" fluid basic
                disabled={selectedFiles.length !== 1}
                icon="settings" content="Properties"
                as={Link} to={`/fileInfo/${selectedFiles[0].path}/`}
            />
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
            <EditOrCreateProjectButton projectNavigation={props.projectNavigation} file={selectedFiles[0]} disabled={selectedFiles.length > 1} />
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

const MobileButtons = ({ file, rename, allowCopyAndMove = false, ...props }) => (
    <Table.Cell>
        <Dropdown direction="left" icon="ellipsis horizontal">
            <Dropdown.Menu>
                <Dropdown.Item content="Share file" onClick={() => uf.shareFiles([file.path], Cloud)} />
                <Dropdown.Item content="Download" onClick={() => uf.downloadFiles([file.path], Cloud)} />
                <PredicatedDropDownItem
                    predicate={!!rename && !uf.isFixedFolder(file.path, Cloud.homeFolder)}
                    onClick={() => rename(file.path)}
                    content="Rename file"
                />
                <PredicatedDropDownItem content="Copy file" predicate={allowCopyAndMove} onClick={() => copy([file], props)} />
                <PredicatedDropDownItem predicate={allowCopyAndMove && !uf.isFixedFolder(file.path, Cloud.homeFolder)} onClick={() => move([file], props)} content="Move file" />
                <PredicatedDropDownItem
                    predicate={!uf.isFixedFolder(file.path, Cloud.homeFolder)}
                    content="Delete file"
                    onClick={() => uf.showFileDeletionPrompt(file.path, Cloud, props.refetch)}
                />
                <Dropdown.Item content="Properties" as={Link} to={`/fileInfo/${file.path}/`} className="black-text" />
                <EditOrCreateProject projectNavigation={props.projectNavigation} file={file} />
            </Dropdown.Menu>
        </Dropdown>
    </Table.Cell>
);

function EditOrCreateProjectButton({ file, disabled, projectNavigation }) {
    const canBeProject = uf.canBeProject(file, Cloud.homeFolder);
    const projectButton = (
        <Button className="context-button-margin" fluid basic icon="users" disabled={disabled || !canBeProject}
            content={uf.isProject(file) ? "Edit Project" : "Create Project"}
            onClick={uf.isProject(file) ? null : () => uf.createProject(file.path, Cloud, projectNavigation)}
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

function EditOrCreateProject({ file, projectNavigation = null }) {
    const canBeProject = uf.canBeProject(file, Cloud.homeFolder);
    if (!canBeProject || !projectNavigation) { return null; }
    const projectLink = uf.isProject(file) ? (
        <Link to={`/metadata/${file.path}/`} className="black-text">
            Edit Project
        </Link>) : "Create Project";
    return (
        <Dropdown.Item onClick={uf.isProject(file) ? null : () => uf.createProject(file.path, Cloud, projectNavigation)}>
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
            Cloud.get(`/files/stat?path=${newPath}/${uf.getFilenameFromPath(currentPath)}`).then(({ request }) => { // TODO Should just try and copy rather than stat'ing
                if (request.status === 200) uf.failureNotification("File already exists")
            }).catch(({ request }) => {
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
    fetchFiles: (path, silent) => {
        dispatch(Actions.updatePath(path));
        if (!silent) dispatch(Actions.setLoading(true));
        dispatch(Actions.fetchFiles(path, uf.sortFilesByTypeAndName, true))
    },
    fetchSelectorFiles: (path) => dispatch(Actions.fetchFileselectorFiles(path)),
    showFileSelector: (open) => dispatch(Actions.fileSelectorShown(open)),
    setFileSelectorCallback: (callback) => dispatch(Actions.setFileSelectorCallback(callback)),
    checkFile: (checked, files, newFile) => {
        files.find(file => file.path === newFile.path).isChecked = checked;
        dispatch(Actions.updateFiles(files));
    },
    goToPage: (pageNumber, files) => { // FIXME Can be reduced in complexity when pagination is server side
        files.forEach(f => f.isChecked = false);
        dispatch(Actions.updateFiles(files));
        dispatch(Actions.toPage(pageNumber));
    },
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    updateFiles: (files) => dispatch(Actions.updateFiles(files)), // FIXME Redundant when pagination is moved to backend
    updateFilesPerPage: (newSize, files) => dispatch(Actions.updateFilesPerPage(newSize, files)),
    setDisallowedPaths: (disallowedPaths) => dispatch(Actions.setDisallowedPaths(disallowedPaths))
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);