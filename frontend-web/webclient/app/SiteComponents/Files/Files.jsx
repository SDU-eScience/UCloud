import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import { Dropdown, Button, Icon, Table, Header, Form, Input, Container, Grid, Responsive } from "semantic-ui-react";
import { PaginationButtons, EntriesPerPageSelector } from "../Pagination";
import { BreadCrumbs } from "../Breadcrumbs/Breadcrumbs";
import * as uf from "../../UtilityFunctions";
import { KeyCode } from "../../DefaultObjects";
import * as Actions from "../../Actions/Files";
import { updatePageTitle } from "../../Actions/Status";
import { changeUppyFilesOpen } from "../../Actions/UppyActions";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon } from "../UtilityComponents";

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
        const { uppy, files, filesPerPage, currentFilesPage, path, loading, history, currentPath, refetchFiles,
            fetchNewFiles, openUppy, checkFile, updateFilesPerPage, updateFiles } = this.props;
        const totalPages = Math.ceil(this.props.files.length / filesPerPage);
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
            .filter(f => uf.getFilenameFromPath(f.path).toLowerCase().includes(this.state.searchText.toLowerCase()));
        const masterCheckboxChecked = shownFiles.length === shownFiles.filter(file => file.isChecked).length && shownFiles.length > 0;
        // Lambdas
        const goTo = (pageNumber) => {
            this.props.goToPage(pageNumber, files);
            this.resetFolderObject();
        };
        const selectedFiles = shownFiles.filter(file => file.isChecked);
        const rename = () => {
            const firstSelectedFile = selectedFiles[0];
            this.startEditFile(files.findIndex((f) => f.path === firstSelectedFile.path), firstSelectedFile.path);
        }
        const navigate = (path) => {
            fetchNewFiles(path)
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
                                mobileOnly={true}
                            />
                        </Responsive>
                        <FilesTable
                            handleKeyDown={this.handleKeyDown}
                            creatingNewFolder={this.state.creatingNewFolder}
                            creatingFolderName={this.state.creatingFolderName}
                            editFolder={this.state.editFolder}
                            renameFile={this.startEditFile}
                            updateEditFileName={this.updateEditFileName}
                            updateCreateFolderName={this.updateCreateFolderName}
                            files={shownFiles}
                            loading={loading}
                            masterCheckbox={masterCheckboxChecked}
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
                            <PaginationButtons
                                currentPage={currentFilesPage}
                                totalPages={totalPages}
                                toPage={(pageNumber) => goTo(pageNumber)}
                            />
                        </FilesTable>
                        <EntriesPerPageSelector
                            entriesPerPage={filesPerPage}
                            totalPages={totalPages}
                            onChange={(newSize) => updateFilesPerPage(newSize, files)}
                        >{"    Files per page"}
                        </EntriesPerPageSelector>
                        <BallPulseLoading loading={loading} />
                    </Grid.Column>
                    <Responsive as={Grid.Column} computer={3} minWidth={992}>
                        <ContextBar
                            selectedFiles={selectedFiles}
                            currentPath={path}
                            createFolder={() => this.createFolder(currentPath)}
                            getFavorites={this.getFavorites}
                            onClick={openUppy}
                            refetch={() => refetchFiles(path)}
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

const ContextBar = ({ getFavorites, onClick, currentPath, selectedFiles, searchText, updateText, createFolder, refetch, ...props }) => (
    <div>
        <ContextButtons upload={onClick} createFolder={createFolder} mobileOnly={false} />
        <br /><br /><br />
        <FileOptions selectedFiles={selectedFiles} refetch={refetch} rename={props.rename} />
    </div>
);

const ContextButtons = ({ upload, createFolder, mobileOnly }) => (
    <React.Fragment>
        <p>
            <Button color="blue" fluid onClick={() => upload()}> Upload Files </Button>
        </p>
        <p>
            <Button basic fluid onClick={() => createFolder()}> New folder</Button>
        </p>
        {mobileOnly ? (<React.Fragment><br /><br /><br /><br /></React.Fragment>) : null}
    </React.Fragment>
);


const FileOptions = ({ selectedFiles, refetch, rename }) => {
    if (!selectedFiles.length) {
        return null;
    }
    const toFileText = (files) => {
        if (selectedFiles.length > 1) {
            return `${selectedFiles.length} files selected.`;
        } else {
            const filename = uf.getFilenameFromPath(selectedFiles[0].path);
            if (filename.length > 10) {
                return filename.slice(0, 17) + "...";
            } else {
                return filename;
            }
        }
    };
    const fileText = toFileText(selectedFiles);
    const rights = uf.getCurrentRights(selectedFiles, Cloud);
    const downloadDisabled = (selectedFiles.length > 1 || selectedFiles[0].sensitivityLevel === "SENSITIVE");
    return (
        <div>
            <h3>{fileText}</h3>
            <p>
                <Link disabled={selectedFiles.length !== 1}
                    to={`/fileInfo/${selectedFiles[0].path}/`}>
                    <Button color="blue" fluid>
                        <Icon name="settings" /> Properties
                    </Button>
                </Link>
            </p>
            <p>
                <Button fluid basic
                    disabled={selectedFiles.length > 1}
                    onClick={() => uf.shareFile(selectedFiles[0].path, Cloud)}>
                    <Icon name="share alternate" /> Share
                </Button>
            </p>
            <p>
                <Button disabled={downloadDisabled || selectedFiles[0].type === "DIRECTORY"} basic fluid
                    onClick={() => uf.downloadFile(selectedFiles[0].path, Cloud)}>
                    <Icon name="download" /> Download
                </Button>
            </p>
            <p>
                <Button fluid basic
                    onClick={() => uf.rename()}
                    disabled={rights.rightsLevel < 3 || selectedFiles.length !== 1}>
                    <Icon name="edit" />
                    Rename
                </Button>
            </p>
            <p>
                <Button color="red" fluid
                    disabled={rights.rightsLevel < 3}
                    onClick={() => uf.batchDeleteFiles(selectedFiles.map((it) => it.path), Cloud, refetch)}>
                    <Icon name="trash" />
                    Delete
                </Button>
            </p>
        </div>
    );
};


export const FilesTable = (props) => {
    if (props.loading) {
        return null;
    }
    const noFiles = (!props.files.length && !props.creatingNewFolder) ?
        <Table.Row>
            <Table.Cell>
                <Header>There are no files in current folder</Header>
            </Table.Cell>
        </Table.Row> : null;
    let hasCheckbox = (!!props.selectOrDeselectAllFiles);
    let masterCheckbox = (hasCheckbox) ? (
        <span className="checkbox-margin">
            <input
                className={`hidden-checkbox ${props.masterCheckbox ? "" : "fileData"}`}
                onClick={e => e.stopPropagation()}
                checked={props.masterCheckbox}
                type="checkbox"
                onChange={(e) => props.selectOrDeselectAllFiles(e.target.checked)}
            />
        </span>
    ) : null;
    let hasFavoriteButton = (!!props.favoriteFile);

    let sortingFunction = (!!props.sortFiles) ? props.sortFiles : () => 0;
    let sortingIconFunction = (!!props.sortingIcon) ? props.sortingIcon : () => "";

    return (
        <Table unstackable basic="very" padded="very">
            <Table.Header>
                {noFiles}
                {!noFiles ? (
                    <Table.Row>
                        <Table.HeaderCell className="checkbox-header" onClick={() => sortingFunction("typeAndName", "typeAndName")}>
                            {masterCheckbox}
                            Filename
                            <span className={"pull-right " + sortingIconFunction("typeAndName")} />
                        </Table.HeaderCell>
                        <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => sortingFunction("modifiedAt", "number")}>
                            <span>
                                Modified
                                <span className={"pull-right " + sortingIconFunction("modifiedAt")} />
                            </span>
                        </Responsive>
                        <Responsive minWidth={768} as={Table.HeaderCell} onClick={() => null}>
                            <span>
                                Members
                                <span className={"pull-right " + sortingIconFunction("owner")} />
                            </span>
                        </Responsive>
                        <Table.HeaderCell />
                    </Table.Row>) : null}
            </Table.Header>
            <FilesList
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
                    <Button color="primary" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</Button>
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
}

const File = ({ file, favoriteFile, beingRenamed, addOrRemoveFile, owner, hasCheckbox, forceInlineButtons, ...props }) => (
    <Table.Row className="fileRow">
        <Table.Cell className="table-cell-padding-left">
            {(hasCheckbox) ? (
                <span className={`checkbox-margin ${props.masterCheckbox ? "" : "fileData"}`}>
                    <input
                        checked={file.isChecked}
                        type="checkbox"
                        className="hidden-checkbox"
                        onClick={(e) => addOrRemoveFile(e.target.checked, file)}
                    />
                </span>
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
            {(!!favoriteFile) ? <Favorited file={file} favoriteFile={favoriteFile} /> : null}
        </Table.Cell>
        <Responsive as={Table.Cell} minWidth={768}>{new Date(file.modifiedAt).toLocaleString()}</Responsive>
        <Responsive as={Table.Cell} minWidth={768}>{owner}</Responsive>
        <Table.Cell>
            <Icon className="fileData" name="share alternate" onClick={() => uf.shareFile(file.path, Cloud)} />
            <MobileButtons
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
}

const FileName = ({ name, beingRenamed, renameName, type, updateEditFileName, size, link, handleKeyDown }) => {
    const color = type === "DIRECTORY" ? "blue" : "grey";
    const icon = (
        <FileIcon
            color={color}
            name={type === "DIRECTORY" ? "folder" : uf.getTypeFromFile(name)}
            size={size} link={link}
            className="create-folder"
        />
    );
    return beingRenamed ?
        <React.Fragment>
            {icon}
            <Input
                value={renameName}
                onChange={(e) => updateEditFileName(e)}
                onKeyDown={(e) => handleKeyDown(e.keyCode, false)}
                autoFocus
            />
            <Button.Group floated="right">
                <Button color="primary" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</Button>
                <Button onClick={() => handleKeyDown(KeyCode.ESC, true)}>✗</Button>
            </Button.Group>
        </React.Fragment> :
        <span>{icon}{name}</span>
};

const Favorited = ({ file, favoriteFile }) =>
    file.favorited ?
        (<Icon onClick={() => favoriteFile(file.path)} name="star" className="favorite-padding" />) :
        (<Icon name="star outline" className="fileData favorite-padding" onClick={() => favoriteFile(file.path)} />);

const MobileButtons = ({ file, forceInlineButtons, rename, refetch, ...props }) => {
    const move = () => {
        props.showFileSelector(true);
        props.setDisallowedPaths([file.path]);
        props.setFileSelectorCallback((newPath) => {
            const currentPath = file.path;
            const newPathForFile = `${newPath}/${uf.getFilenameFromPath(file.path)}`;
            Cloud.post(`/files/move?path=${currentPath}&newPath=${newPathForFile}`).then(() => refetch());
            props.showFileSelector(false);
            props.setFileSelectorCallback(null);
            props.setDisallowedPaths([]);
        });
    };
    const copy = () => {
        props.showFileSelector(true);
        props.setFileSelectorCallback((newPath) => {
            const currentPath = file.path;
            const newPathForFile = `${newPath}/${uf.getFilenameFromPath(file.path)}`;
            Cloud.get(`/files/stat?path=${newPath}/${uf.getFilenameFromPath(file.path)}`).catch(({ request }) => {
                if (request.status === 404) {
                    const newPathForFile = `${newPath}/${uf.getFilenameFromPath(file.path)}`;
                    Cloud.post(`/files/copy?path=${currentPath}&newPath=${newPathForFile}`).then(() => {
                        props.showFileSelector(false);
                        props.setFileSelectorCallback(null);
                        refetch();
                    });
                } else {
                    alert(`An error occurred. ${request.statusCode}`)
                }
            });
        });
    };
    return (<span className={(!forceInlineButtons) ? "hidden-lg" : ""}>
        <Dropdown direction="left" icon="ellipsis horizontal">
            <Dropdown.Menu>
                <Dropdown.Item onClick={() => uf.shareFile(file.path, Cloud)}>
                    Share file
                </Dropdown.Item>
                {file.type === "FILE" ? <Dropdown.Item onClick={() => uf.downloadFile(file.path, Cloud)}>
                    Download file
                </Dropdown.Item> : null}
                {rename && !uf.isFixedFolder(file.path, Cloud.homeFolder) ? <Dropdown.Item onClick={() => rename(file.path)}>
                    Rename file
                </Dropdown.Item> : null}
                <Dropdown.Item onClick={() => copy(file.path)}>
                    Copy file
                </Dropdown.Item>
                {!uf.isFixedFolder(file.path, Cloud.homeFolder) ? <Dropdown.Item onClick={() => move()}>
                    Move file
                </Dropdown.Item> : null}
                {!uf.isFixedFolder(file.path, Cloud.homeFolder) ? <Dropdown.Item onClick={() => uf.showFileDeletionPrompt(file.path, Cloud, refetch)}>
                    Delete file
                </Dropdown.Item> : null}
                <Dropdown.Item>
                    <Link to={`/fileInfo/${file.path}/`} className="black-text">
                        Properties
                    </Link>
                </Dropdown.Item>
            </Dropdown.Menu>
        </Dropdown>
    </span>);
}



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
}

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