import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import { Modal, Dropdown, Button, Icon, Table, Header, Input, Grid, Responsive, Checkbox, Divider, SemanticSIZES } from "semantic-ui-react";
import { dateToString } from "Utilities/DateUtilities";
import * as Pagination from "Pagination";
import { BreadCrumbs } from "Breadcrumbs/Breadcrumbs";
import * as uf from "UtilityFunctions";
import { KeyCode } from "DefaultObjects";
import * as Actions from "./Redux/FilesActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon } from "UtilityComponents";
import { Uploader } from "Uploader";
import { Page } from "Types";
import {
    FilesProps, SortBy, SortOrder, FilesStateProps, FilesOperations, MockedTableProps, File, FilesTableProps,
    EditOrCreateProjectButtonProps, CreateFolderProps, PredicatedDropDownItemProps
} from ".";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";

class Files extends React.Component<FilesProps> {
    constructor(props) {
        super(props);
        const urlPath = props.match.params[0];
        const { history } = props;
        if (!urlPath) {
            history.push(`/files/${Cloud.homeFolder}/`);
        }
        props.setPageTitle();
        props.prioritizeFileSearch();
    }

    componentDidMount() {
        const { match, page, fetchFiles, fetchSelectorFiles, sortOrder, sortBy } = this.props;
        fetchFiles(match.params[0], page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
        fetchSelectorFiles(Cloud.homeFolder, page.pageNumber, page.itemsPerPage);
    }

    handleKeyDown = (key: number, newFolder: boolean, name: string): void => {
        if (key === KeyCode.ESC) {
            this.props.resetFolderEditing();
        } else if (key === KeyCode.ENTER) {
            const { path, fetchPageFromPath, page } = this.props;
            const fileNames = page.items.map((it) => uf.getFilenameFromPath(it.path))
            if (uf.isInvalidPathName(name, fileNames)) return;
            const directoryPath = `${uf.addTrailingSlash(path)}${name}`;
            if (newFolder) {
                Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
                    if (uf.inSuccessRange(request.status)) {
                        this.props.resetFolderEditing();
                        fetchPageFromPath(directoryPath, page.itemsPerPage, this.props.sortOrder, this.props.sortBy);
                    }
                }).catch(() => this.props.resetFolderEditing());
            } else {
                const originalFilename = page.items[this.props.editFileIndex].path;
                Cloud.post(`/files/move?path=${originalFilename}&newPath=${directoryPath}`)
                    .then(({ request }) => {
                        if (uf.inSuccessRange(request.status)) {
                            this.props.resetFolderEditing();
                            fetchPageFromPath(directoryPath, page.itemsPerPage, this.props.sortOrder, this.props.sortBy);
                        }
                    }).catch(() => this.props.resetFolderEditing())
            }
        }
    }

    shouldComponentUpdate(nextProps, _nextState): boolean {
        const { fetchFiles, page, loading, sortOrder, sortBy } = this.props;
        if (nextProps.path !== nextProps.match.params[0] && !loading) {
            fetchFiles(nextProps.match.params[0], page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
        }
        return true;
    }

    checkAllFiles = (checked: boolean): void => {
        const { page, updateFiles } = this.props;
        page.items.forEach(file => file.isChecked = checked);
        updateFiles(page);
    }

    render() {
        // Props
        const { page, path, loading, history, fetchFiles, checkFile, updateFiles, sortBy, sortOrder, error } = this.props;
        const selectedFiles = page.items.filter(file => file.isChecked);
        // Master Checkbox
        const checkedFilesCount = selectedFiles.length;
        const masterCheckboxChecked = page.items.length === checkedFilesCount && page.items.length > 0;
        const indeterminate = checkedFilesCount < page.items.length && checkedFilesCount > 0;
        // Lambdas
        const goTo = (pageNumber) => {
            this.props.fetchFiles(path, page.itemsPerPage, pageNumber, this.props.sortOrder, this.props.sortBy);
            this.props.resetFolderEditing();
        };
        const fetch = () => fetchFiles(path, page.itemsPerPage, page.pageNumber, this.props.sortOrder, this.props.sortBy);
        const rename = () => {
            const firstSelectedFile = selectedFiles[0];
            this.props.setEditingFileIndex(page.items.findIndex((f) => f.path === firstSelectedFile.path));
        };
        const navigate = (path: string) => history.push(`/files/${path}`);
        const projectNavigation = (projectPath: string) => history.push(`/metadata/${projectPath}`);
        const fetchPageFromPath = (path) => {
            this.props.fetchPageFromPath(path, page.itemsPerPage, sortOrder, sortBy);
            this.props.updatePath(uf.getParentPath(path)); navigate(uf.getParentPath(path));
        };
        return (
            <Grid>
                <Grid.Column computer={13} tablet={16}>
                    <Grid.Row>
                        <Responsive
                            as={ContextButtons}
                            maxWidth={991}
                            createFolder={() => this.props.setCreatingFolder(true)}
                            currentPath={path}
                        />
                        <BreadCrumbs currentPath={path} navigate={(newPath) => navigate(newPath)} />
                    </Grid.Row>
                    <Pagination.List
                        loading={loading}
                        onRefreshClick={fetch}
                        errorMessage={error}
                        onErrorDismiss={this.props.dismissError}
                        customEmptyPage={
                            this.props.creatingFolder ? (
                                <MockTable creatingFolder={this.props.creatingFolder} handleKeyDown={this.handleKeyDown} />) : (
                                    <Header.Subheader content="No files in current folder" />
                                )}
                        pageRenderer={(page) => (
                            <FilesTable
                                sortFiles={(sortBy: SortBy) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING, sortBy)}
                                sortingIcon={(name: SortBy) => uf.getSortingIcon(sortBy, sortOrder, name)}
                                masterCheckbox={
                                    <Checkbox
                                        className="hidden-checkbox checkbox-margin"
                                        onClick={(_, d) => this.checkAllFiles(d.checked)}
                                        checked={masterCheckboxChecked}
                                        indeterminate={indeterminate}
                                        onChange={(e) => e.stopPropagation()}
                                    />}
                                allowCopyAndMove
                                creatingNewFolder={this.props.creatingFolder}
                                handleKeyDown={this.handleKeyDown}
                                files={page.items}
                                onCheckFile={(checked: boolean, file: File) => checkFile(checked, page, file)}
                                fetchFiles={(path: string) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                                fetchPageFromPath={fetchPageFromPath}
                                onFavoriteFile={(filePath: string) => updateFiles(uf.favoriteFileFromPage(page, filePath, Cloud))}
                                editFolderIndex={this.props.editFileIndex}
                                projectNavigation={projectNavigation}
                                startEditFile={this.props.setEditingFileIndex}
                                setDisallowedPaths={this.props.setDisallowedPaths}
                                setFileSelectorCallback={this.props.setFileSelectorCallback}
                                showFileSelector={this.props.showFileSelector}
                            />
                        )}
                        onItemsPerPageChanged={(pageSize) => { this.props.resetFolderEditing(); fetchFiles(path, pageSize, 0, sortOrder, sortBy) }}
                        page={page}
                        onPageChanged={(pageNumber) => goTo(pageNumber)}
                    />
                </Grid.Column>
                <Responsive as={Grid.Column} computer={3} minWidth={992}>
                    <ContextBar
                        selectedFiles={selectedFiles}
                        currentPath={path}
                        createFolder={() => this.props.setCreatingFolder(true)}
                        refetch={() => fetch()}
                        fetchPageFromPath={fetchPageFromPath}
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
                    path={this.props.fileSelectorPath}
                    fetchFiles={(path, pageNumber, itemsPerPage) => this.props.fetchSelectorFiles(path, pageNumber, itemsPerPage)}
                    loading={this.props.fileSelectorLoading}
                    errorMessage={this.props.fileSelectorError}
                    onErrorDismiss={() => this.props.onFileSelectorErrorDismiss()}
                    onlyAllowFolders
                    canSelectFolders
                    page={this.props.fileSelectorPage}
                    setSelectedFile={this.props.fileSelectorCallback}
                    disallowedPaths={this.props.disallowedPaths}
                />
            </Grid>);
    }
}

// Used for creation of folder in empty folder
const MockTable = ({ handleKeyDown, creatingFolder }: MockedTableProps) => (
    <Table unstackable basic="very">
        <FilesTableHeader masterCheckBox={null} sortingIcon={() => null} sortFiles={null} />
        <Table.Body><CreateFolder creatingNewFolder={creatingFolder} handleKeyDown={handleKeyDown} /></Table.Body>
    </Table>
)

export const FilesTable = ({
    files, masterCheckbox, showFileSelector, setFileSelectorCallback, setDisallowedPaths, sortingIcon, editFolderIndex,
    sortFiles, handleKeyDown, onCheckFile, fetchFiles, startEditFile, projectNavigation, creatingNewFolder,
    allowCopyAndMove, onFavoriteFile, fetchPageFromPath
}: FilesTableProps) => {
    return (
        <Table unstackable basic="very">
            <FilesTableHeader masterCheckBox={masterCheckbox} sortingIcon={sortingIcon} sortFiles={sortFiles} />
            <Table.Body>
                <CreateFolder creatingNewFolder={creatingNewFolder} handleKeyDown={handleKeyDown} />
                {files.map((f: File, i: number) => (
                    <Table.Row className="file-row" key={i}>
                        <FilenameAndIcons
                            file={f}
                            onFavoriteFile={onFavoriteFile}
                            beingRenamed={editFolderIndex === i}
                            hasCheckbox={masterCheckbox !== null}
                            onKeyDown={handleKeyDown}
                            onCheckFile={(checked: boolean, newFile: File) => onCheckFile(checked, newFile)}
                        />
                        <Responsive as={Table.Cell} minWidth={768}>{dateToString(f.modifiedAt)}</Responsive>
                        <Responsive as={Table.Cell} minWidth={768}>{uf.getOwnerFromAcls(f.acl)}</Responsive>
                        <MobileButtons
                            projectNavigation={projectNavigation}
                            allowCopyAndMove={allowCopyAndMove}
                            file={f}
                            rename={!!startEditFile ? () => startEditFile(i) : null}
                            fetchPageFromPath={fetchPageFromPath}
                            refetch={fetchFiles}
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

const FilesTableHeader = ({ sortingIcon, sortFiles = (_) => null, masterCheckBox = null }) => (
    <Table.Header>
        <Table.Row>
            <Table.HeaderCell className="filename-row" onClick={() => sortFiles(SortBy.PATH)}>
                {masterCheckBox}
                Filename
                <Icon className="float-right" name={sortingIcon("PATH")} />
            </Table.HeaderCell>
            <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => sortFiles(SortBy.MODIFIED_AT)}>
                Modified
                <Icon className="float-right" name={sortingIcon("MODIFIED_AT")} />
            </Responsive>
            <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => sortFiles(SortBy.ACL)}>
                Members
                <Icon className="float-right" name={sortingIcon("ACL")} />
            </Responsive>
            <Table.HeaderCell />
        </Table.Row>
    </Table.Header>
);

const ContextBar = ({ currentPath, selectedFiles, createFolder, ...props }) => (
    <div>
        <ContextButtons refetch={props.refetch} currentPath={currentPath} createFolder={createFolder} />
        <Divider />
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
        <Button as={Link} to={`/filesearch`} basic className="context-button-margin" fluid content="Advanced Search" color="green" />
    </div>
);

const CreateFolder = ({ creatingNewFolder, handleKeyDown }: CreateFolderProps) => (
    !creatingNewFolder ? null : (
        <Table.Row>
            <Table.Cell>
                <Icon name="folder" color="blue" size="big" className="create-folder" />
                <Input
                    fluid
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

interface FilenameAndIcons {
    file: File
    beingRenamed: boolean
    hasCheckbox: boolean
    size: SemanticSIZES
    onKeyDown: (a: number, b: boolean, c: string) => void
    onCheckFile: (c: boolean, f: File) => void
    onFavoriteFile: (p: string) => void
}

function FilenameAndIcons({ file, beingRenamed = false, size = "big", onKeyDown = null, onCheckFile = null, hasCheckbox = false, onFavoriteFile = null }) {
    const color = file.type === "DIRECTORY" ? "blue" : "grey";
    const fileName = uf.getFilenameFromPath(file.path);
    const checkbox = <PredicatedCheckbox predicate={hasCheckbox} checked={file.isChecked} onClick={(_e, { checked }) => onCheckFile(checked, file)} />
    const icon = (
        <FileIcon
            color={color}
            name={uf.isDirectory(file) ? "folder" : uf.iconFromFilePath(file.path)}
            size={size} link={file.link}
        />
    );
    const nameLink = (uf.isDirectory(file) ?
        <Link to={`/files/${file.path}`}>
            {icon}{fileName}
        </Link> : <React.Fragment>{icon}{fileName}</React.Fragment>);
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
            {nameLink}
            <GroupIcon isProject={uf.isProject(file)} />
            <PredicatedFavorite predicate={!!onFavoriteFile && !file.path.startsWith(`${Cloud.homeFolder}Favorites/`)} file={file} onClick={() => onFavoriteFile(file.path)} />
        </Table.Cell>
};

function FileOptions({ selectedFiles, rename, ...props }) {
    if (!selectedFiles.length) return null;
    const fileText = uf.toFileText(selectedFiles);
    const rights = uf.getCurrentRights(selectedFiles, Cloud);
    const moveDisabled = selectedFiles.some(f => uf.isFixedFolder(f.path, Cloud.homeFolder));
    const downloadDisabled = !uf.downloadAllowed(selectedFiles);
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

const PredicatedDropDownItem = ({ predicate, content, onClick }: PredicatedDropDownItemProps) =>
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

function EditOrCreateProjectButton({ file, disabled, projectNavigation }: EditOrCreateProjectButtonProps) {
    const canBeProject = uf.canBeProject(file, Cloud.homeFolder);
    const isProject = uf.isProject(file);
    return isProject ? (
        <Button
            as={Link}
            to={`/metadata/${file.path}/`}
            className="context-button-margin"
            fluid basic icon="users"
            disabled={disabled || !canBeProject}
            content="Edit Project"
        />
    ) : (
        <Button
            className="context-button-margin"
            fluid basic
            icon="users"
            disabled={disabled || !canBeProject}
            content={"Create Project"}
            onClick={() => uf.createProject(file.path, Cloud, projectNavigation)}
        />
    );
}

function EditOrCreateProject({ file, projectNavigation = null }) {
    const canBeProject = uf.canBeProject(file, Cloud.homeFolder);
    if (!canBeProject || !projectNavigation) return null;
    return uf.isProject(file) ? (
        <Dropdown.Item as={Link} to={`/metadata/${file.path}/`} content="Edit Project" />
    ) : (
        <Dropdown.Item onClick={() => uf.createProject(file.path, Cloud, projectNavigation)} content="Create Project" />
    );
}

function copy(files: File[], operations): void {
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
                        if (i === files.length) { operations.fetchPageFromPath(newPathForFile); uf.successNotification("File copied."); }
                    });
                } else {
                    uf.failureNotification(`An error occurred, please try again later.`)
                }
            })
        });
    });
};

function move(files: File[], operations): void {
    operations.showFileSelector(true);
    const parentPath = uf.getParentPath(files[0].path);
    operations.setDisallowedPaths([parentPath].concat(files.map(f => f.path)));
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        files.forEach((f) => {
            const currentPath = f.path;
            const newPathForFile = `${newPath}/${uf.getFilenameFromPath(currentPath)}`;
            Cloud.post(`/files/move?path=${currentPath}&newPath=${newPathForFile}`).then(() => {
                const fromPath = uf.getFilenameFromPath(currentPath);
                const toPath = uf.replaceHomeFolder(newPathForFile, Cloud.homeFolder);
                uf.successNotification(`${fromPath} moved to ${toPath}`);
                operations.fetchPageFromPath(newPathForFile);
            }).catch(() => uf.failureNotification("An error occurred, please try again later"));
            operations.showFileSelector(false);
            operations.setFileSelectorCallback(null);
            operations.setDisallowedPaths([]);
        });
    })
}

const mapStateToProps = ({ files }): FilesStateProps => {
    const { page, loading, path, fileSelectorPage, fileSelectorPath, sortBy, sortOrder, editFileIndex, creatingFolder,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, fileSelectorLoading, error, fileSelectorError } = files;
    const favFilesCount = page.items.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const checkedFilesCount = page.items.filter(file => file.isChecked).length; // HACK to ensure changes to file checkings are rendered.
    return {
        error, fileSelectorError, page, loading, path, checkedFilesCount, favFilesCount, fileSelectorPage, fileSelectorPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, sortOrder, sortBy, editFileIndex, creatingFolder, fileSelectorLoading
    }
};

const mapDispatchToProps = (dispatch): FilesOperations => ({
    prioritizeFileSearch: () => dispatch(setPrioritizedSearch("files")),
    onFileSelectorErrorDismiss: () => dispatch(Actions.setFileSelectorError(null)),
    dismissError: () => dispatch(Actions.setErrorMessage()),
    fetchFiles: (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy) => {
        dispatch(Actions.updatePath(path));
        dispatch(Actions.setLoading(true));
        dispatch(Actions.fetchFiles(path, itemsPerPage, pageNumber, sortOrder, sortBy))
    },
    fetchPageFromPath: (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy) => {
        dispatch(Actions.setLoading(true));
        dispatch(Actions.fetchPageFromPath(path, itemsPerPage, sortOrder, sortBy))
    },
    updatePath: (path: string) => dispatch(Actions.updatePath(path)),
    fetchSelectorFiles: (path: string, pageNumber: number, itemsPerPage: number) => dispatch(Actions.fetchFileselectorFiles(path, pageNumber, itemsPerPage)),
    showFileSelector: (open: boolean) => dispatch(Actions.fileSelectorShown(open)),
    setFileSelectorCallback: (callback) => dispatch(Actions.setFileSelectorCallback(callback)),
    checkFile: (checked: boolean, page: Page<File>, newFile: File) => { // FIXME: Make an action instead with path?
        page.items.find(file => file.path === newFile.path).isChecked = checked;
        dispatch(Actions.updateFiles(page));
    },
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    updateFiles: (page: Page<File>) => dispatch(Actions.updateFiles(page)),
    setDisallowedPaths: (disallowedPaths: string[]) => dispatch(Actions.setDisallowedPaths(disallowedPaths)),
    setCreatingFolder: (creating) => dispatch(Actions.setCreatingFolder(creating)),
    setEditingFileIndex: (index) => dispatch(Actions.setEditingFile(index)),
    resetFolderEditing: () => dispatch(Actions.resetFolderEditing())
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);