import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import { Button, Table, Dropdown, MenuItem, Glyphicon, FormGroup, InputGroup } from "react-bootstrap";
import { PaginationButtons, EntriesPerPageSelector } from "../Pagination";
import { BreadCrumbs } from "../Breadcrumbs";
import {
    sortFilesByTypeAndName,
    sortFilesBySensitivity,
    shareFile,
    favorite,
    getOwnerFromAcls,
    renameFile,
    showFileDeletionPrompt,
    getCurrentRights,
    downloadFile,
    toLowerCaseAndCapitalize,
    getSortingIcon,
    sortByNumber,
    sortByString,
    getTypeFromFile,
    inSuccessRange,
    getFilenameFromPath
} from "../../UtilityFunctions";
import { KeyCode } from "../../DefaultObjects";
import Uppy from "uppy";
import { fetchFiles, updateFilesPerPage, updateFiles, setLoading, updatePath, toPage } from "../../Actions/Files";
import { updatePageTitle } from "../../Actions/Status";
import { changeUppyFilesOpen } from "../../Actions/UppyActions";

class Files extends React.Component {
    constructor(props) {
        super(props);
        const urlPath = props.match.params[0];
        const { history, updatePath, setPageTitle } = props;
        if (urlPath) {
            updatePath(urlPath);
        } else {
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
        this.updateSearchText = this.updateSearchText.bind(this);
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
            const { path, refetchFiles } = this.props;
            if (isNew) {
                const name = this.state.creatingFolderName;
                const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
                name ? Cloud.post("/files/directory", { path: directoryPath }).then(({ request }) => {
                    if (inSuccessRange(request.status)) {
                        // TODO Push mock folder
                        this.resetFolderObject();
                        refetchFiles(this.props.path);
                    }
                }).catch((failure) =>
                    this.resetFolderObject() // TODO Handle failure
                ) : this.resetFolderObject();
            } else {
                const name = this.state.editFolder.name;
                const directoryPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
                const originalFilename = this.props.files[this.state.editFolder.index].path.path;
                name ? Cloud.post("/files/move", { path: originalFilename, newPath: directoryPath })
                    .then(({ request }) => {
                        if (inSuccessRange(request.status)) {
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

    updateSearchText(e) {
        e.preventDefault();
        const value = e.target.value;
        this.setState(() => ({ searchText: value }));
        const files = this.props.files;
        files.forEach(file => file.isChecked = false);
        this.props.updateFiles(files);
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
                sortedFiles = sortByNumber(files, name, asc);
                break;
            }
            case "string": {
                sortedFiles = sortByString(files, name, asc);
                break;
            }
            case "typeAndName": {
                sortedFiles = sortFilesByTypeAndName(files, asc);
                break;
            }
            case "sensitivityLevel":
                sortedFiles = sortFilesBySensitivity(files, asc);
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
        this.setState(() => ({ creatingFolderName }))
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
                name: getFilenameFromPath(path),
                index
            }
        }));
    }

    componentWillReceiveProps(nextProps) {
        const { path, fetchNewFiles } = this.props;
        let newPath = nextProps.match.params[0];
        if (!newPath) {
            this.props.history.push(`/files/${Cloud.homeFolder}`);
        } else if (path !== newPath) {
            fetchNewFiles(newPath);
            this.resetFolderObject();
        }
    }

    render() {
        // PROPS
        const { files, filesPerPage, currentFilesPage, path, loading, history, currentPath, fetchFiles, openUppy, checkFile, updateFilesPerPage } = this.props;
        const totalPages = Math.ceil(this.props.files.length / filesPerPage);
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
            .filter(f => getFilenameFromPath(f.path).toLowerCase().includes(this.state.searchText.toLowerCase()));
        const masterCheckboxChecked = shownFiles.length === shownFiles.filter(file => file.isChecked).length;
        // Lambdas
        const goTo = (pageNumber, files) => {
            this.props.goToPage(pageNumber, files);
            this.resetFolderObject();
        };
        const selectedFiles = shownFiles.filter(file => file.isChecked);
        const rename = () => {
            const firstSelectedFile = selectedFiles[0];
            this.startEditFile(files.findIndex((f) => f.path === firstSelectedFile.path), firstSelectedFile.path);
        }
        const refetchFilesWithCurrentPath = () => fetchFiles(path, sortFilesByTypeAndName, this.state.lastSorting.asc);
        return (
            <React.StrictMode>
                <section>
                    <div className="col-lg-10">
                        <BreadCrumbs currentPath={path} navigate={(newPath) => history.push(`/files/${newPath}`)} />
                        <ContextButtons upload={openUppy} createFolder={() => this.createFolder(currentPath)} mobileOnly={true} />
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
                            sortingIcon={(name) => getSortingIcon(this.state.lastSorting, name)}
                            addOrRemoveFile={(checked, newFile) => checkFile(checked, files, newFile)}
                            sortFiles={this.sortFilesBy}
                            favoriteFile={(filePath) => updateFiles(favorite(files, filePath, Cloud))}
                            selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}
                            forceInlineButtons={true}
                            refetch={refetchFilesWithCurrentPath}
                        />
                        <BallPulseLoading loading={loading} />
                        <PaginationButtons
                            currentPage={currentFilesPage}
                            totalPages={totalPages}
                            toPage={(pageNumber) => goTo(pageNumber, files)}
                        />
                        <EntriesPerPageSelector
                            entriesPerPage={filesPerPage}
                            totalPages={totalPages}
                            handlePageSizeSelection={(newSize) => updateFilesPerPage(newSize, files)}
                        >
                            Files per page
                    </EntriesPerPageSelector>
                    </div>
                    <ContextBar
                        selectedFiles={selectedFiles}
                        currentPath={path}
                        createFolder={() => this.createFolder(currentPath)}
                        getFavorites={this.getFavorites}
                        onClick={openUppy}
                        searchText={this.state.searchText}
                        updateText={this.updateSearchText}
                        refetch={refetchFilesWithCurrentPath}
                        rename={rename}
                    />
                </section>
            </React.StrictMode>);
    }
}

const SearchBar = ({ searchText, updateText }) => (
    <div className="input-group" style={{ margin: "20px 0 20px" }}>
        <input
            className="form-control"
            type="text"
            placeholder="Search in current files..."
            value={searchText ? searchText : ""}
            onChange={(e) => updateText(e)}
        />
        <span className="input-group-addon">
            <span className="glyphicon glyphicon-search" />
        </span>
    </div>
);

const ContextBar = ({ getFavorites, onClick, currentPath, selectedFiles, searchText, updateText, createFolder, refetch, ...props }) => (
    <div className="col-lg-2 visible-lg">
        <SearchBar searchText={searchText} updateText={updateText} />
        <ContextButtons upload={onClick} createFolder={createFolder} mobileOnly={false} />
        <br />
        <FileOptions selectedFiles={selectedFiles} refetch={refetch} rename={props.rename} />
    </div>
);

const ContextButtons = ({ upload, createFolder, mobileOnly }) => (
    <span className={mobileOnly ? "hidden-lg" : ""}>
        <button className="btn btn-primary btn-block"
            onClick={() => upload()}>
            <span className="ion-android-upload pull-left" /> Upload Files
        </button>
        {mobileOnly ? null : (<br />)}
        <button className="btn btn-default btn-block"
            onClick={() => createFolder()}>
            <span className="ion-folder pull-left" /> New folder
        </button>
        {mobileOnly ? (<br />) : null}
    </span>
);


const FileOptions = ({ selectedFiles, refetch, rename }) => {
    if (!selectedFiles.length) {
        return null;
    }
    const toFileText = (files) => {
        if (selectedFiles.length > 1) {
            return `${selectedFiles.length} files selected.`;
        } else {
            const filename = getFilenameFromPath(selectedFiles[0].path);
            if (filename.length > 10) {
                return filename.slice(0, 17) + "...";
            } else {
                return filename;
            }
        }
    };
    const fileText = toFileText(selectedFiles);
    const rights = getCurrentRights(selectedFiles, Cloud);
    const downloadDisabled = (selectedFiles.length > 1 || selectedFiles[0].sensitivityLevel === "SENSITIVE");
    return (
        <div>
            <h3>{fileText}</h3>
            <p>
                <Link disabled={selectedFiles.length !== 1} className="btn btn-primary ripple btn-block"
                    to={`/fileInfo/${selectedFiles[0].path}/`}>
                    <span className="ion-ios-settings-strong pull-left" />Properties
                </Link>
            </p>
            <p>
                <Button type="button" className="btn btn-default ripple btn-block"
                    disabled={selectedFiles.length > 1}
                    onClick={() => shareFile(selectedFiles[0].path, Cloud)}><span
                        className="ion-share pull-left" /> Share
                </Button>
            </p>
            <p>
                <Button disabled={downloadDisabled || selectedFiles[0].type === "DIRECTORY"} className="btn btn-default ripple btn-block"
                    onClick={() => downloadFile(selectedFiles[0].path, Cloud)}>
                    <span className="ion-ios-download pull-left" />
                    Download
                </Button>
            </p>
            <p>
                <Button type="button" className="btn btn-default btn-block ripple"
                    onClick={() => rename()}
                    disabled={rights.rightsLevel < 3 || selectedFiles.length !== 1}>
                    <span className="ion-ios-compose pull-left" />
                    Rename
                </Button>
            </p>
            <p>
                <Button className="btn btn-danger btn-block ripple"
                    disabled={rights.rightsLevel < 3 || selectedFiles.length > 1}
                    onClick={() => showFileDeletionPrompt(selectedFiles[0].path, Cloud, refetch)}>
                    <em className="ion-ios-trash pull-left" />
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
        <tr>
            <td>
                <div className="card" align="center">
                    <h3 className="text-center">
                        <small>There are no files in current folder</small>
                    </h3>
                </div>
            </td>
        </tr> : null;

    let hasCheckbox = (!!props.selectOrDeselectAllFiles);
    let masterCheckbox = (hasCheckbox) ? (
        <span className={props.masterCheckbox ? "" : "fileData"} style={{ margin: "15px" }}>
            <input
                name="select"
                className="select-box"
                checked={props.masterCheckbox}
                type="checkbox"
                onChange={e => props.selectOrDeselectAllFiles(e.target.checked)}
            />
            <em className="bg-info" />
        </span>
    ) : null;
    let hasFavoriteButton = (!!props.favoriteFile);

    let sortingFunction = (!!props.sortFiles) ? props.sortFiles : () => 0;
    let sortingIconFunction = (!!props.sortingIcon) ? props.sortingIcon : () => "";

    return (
        <Table responsive className="table table-hover mv-lg">
            <thead>
                {noFiles}
                {!noFiles ? (<tr>
                    <th>
                        <span className="text-left">
                            {masterCheckbox}
                            <span onClick={() => sortingFunction("typeAndName", "typeAndName")}>Filename</span>
                            <span className={"pull-right " + sortingIconFunction("typeAndName")} />
                        </span>
                    </th>

                    <th onClick={() => sortingFunction("modifiedAt", "number")}>
                        <span className="text-left">
                            Modified
                                <span className={"pull-right " + sortingIconFunction("modifiedAt")} />
                        </span>
                    </th>

                    <th onClick={() => null}>
                        <span className="text-left">
                            Members
                                <span className={"pull-right " + sortingIconFunction("owner")} />
                        </span>
                    </th>
                </tr>) : null}
            </thead>

            <FilesList
                refetch={props.refetch}
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
            />
        </Table>
    );
};

const CreateFolder = ({ creatingNewFolder, creatingFolderName, updateText, handleKeyDown }) => (
    !creatingNewFolder ? null : (
        <tr>
            <td>
                <FormGroup>
                    <div className="form-inline">
                        <InputGroup>
                            <i
                                className="ion-android-folder"
                                style={{ fontSize: "32px", paddingRight: "8px", paddingLeft: "43px", verticalAlign: "middle", color: "#448aff" }}
                            />
                        </InputGroup>
                        <InputGroup>
                            <input
                                onKeyDown={(e) => handleKeyDown(e.keyCode, true)}
                                className="form-control"
                                type="text"
                                placeholder="Folder name..."
                                value={creatingFolderName ? creatingFolderName : ""}
                                onChange={(e) => updateText(e.target.value)}
                            />
                            <span className="input-group-addon hidden-lg btn-info btn" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</span>
                            <span className="input-group-addon hidden-lg btn" onClick={() => handleKeyDown(KeyCode.ESC, true)}>✗</span>
                        </InputGroup></div>
                </FormGroup>
            </td><td></td><td></td><td></td>
        </tr>
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
            owner={getOwnerFromAcls(file.acl, Cloud)}
            refetch={props.refetch}
            style={file.type === "DIRECTORY" ? ({ cursor: "pointer" }) : {}}
        />)
    );
    return (<tbody>
        <CreateFolder
            creatingNewFolder={props.creatingNewFolder}
            creatingFolderName={props.creatingFolderName}
            updateText={props.updateCreateFolderName}
            handleKeyDown={props.handleKeyDown}
        />
        {filesList}
    </tbody>);
}

const File = ({ file, favoriteFile, beingRenamed, addOrRemoveFile, owner, hasCheckbox, forceInlineButtons, style, ...props }) => (
    <tr className="row-settings clickable-row fileRow" style={style}>
        <td>
            {(hasCheckbox) ? (
                <FileCheckbox className="fileData"
                    isChecked={file.isChecked}
                    onChange={(e) => addOrRemoveFile(e.target.checked, file)}
                    beingRenamed={beingRenamed}
                />
            ) : null}
            <FileType
                type={file.type}
                path={file.path}
                updateEditFileName={props.updateEditFileName}
                handleKeyDown={props.handleKeyDown}
                beingRenamed={beingRenamed}
                renameName={props.renameName}
                update={props.updateName}
            />
            {(!!favoriteFile) ? <Favorited file={file} favoriteFile={favoriteFile} /> : null}
        </td>
        <td>{new Date(file.modifiedAt).toLocaleString()}</td>
        <td>{owner}</td>
        <td>
            <Button className="fileData" onClick={() => shareFile(file.path, Cloud)}>Share</Button>
            <MobileButtons
                file={file}
                forceInlineButtons={forceInlineButtons}
                rename={props.renameFile ? (path) => props.renameFile(props.index, path) : undefined}
                refetch={props.refetch}
            />
        </td>
    </tr>
);

const FileCheckbox = ({ isChecked, onChange }) => (
    <span style={{ margin: "15px" }} className={isChecked ? "" : "fileData"}>
        <input
            name="select"
            className="select-box"
            checked={isChecked}
            type="checkbox"
            onChange={(e) => onChange(e)}
        />
        <em className="bg-info" />
    </span>
);

const FileType = ({ type, path, beingRenamed, update, ...props }) => {
    const fileName = (
        <FileName
            updateEditFileName={props.updateEditFileName}
            name={getFilenameFromPath(path)}
            beingRenamed={beingRenamed}
            handleKeyDown={props.handleKeyDown}
            renameName={props.renameName}
            update={update}
        />);
    if (type === "FILE") {
        return (<React.Fragment>
            <i className={getTypeFromFile(getFilenameFromPath(path))} style={{ fontSize: "32px", paddingRight: "11px", verticalAlign: "middle" }} />
            <span>{fileName}</span>
        </React.Fragment>)
    } else {
        const folderIcon = (
            <i
                className="ion-android-folder"
                style={{ fontSize: "32px", paddingRight: "8px", verticalAlign: "middle", color: "#448aff" }}
            />);
        return beingRenamed ?
            (<React.Fragment>
                {folderIcon}
                <span>{fileName}</span>
            </React.Fragment>) :
            (<Link to={`/files/${path}`}>{folderIcon}
                {fileName}
            </Link>);
    }
}

const FileName = ({ name, beingRenamed, renameName, updateEditFileName, handleKeyDown }) => {
    return beingRenamed ?
        <FormGroup>
            <span className="form-inline">
                <InputGroup>
                    <input
                        value={renameName}
                        onChange={(e) => updateEditFileName(e)}
                        className="form-control"
                        onKeyDown={(e) => handleKeyDown(e.keyCode, false)}
                    />
                    <span className="input-group-addon hidden-lg btn-info btn" onClick={() => handleKeyDown(KeyCode.ENTER, true)}>√</span>
                    <span className="input-group-addon hidden-lg btn" onClick={() => handleKeyDown(KeyCode.ESC, true)}>✗</span>
                </InputGroup>
            </span>
        </FormGroup> :
        <span>{name}</span>
};
const Favorited = ({ file, favoriteFile }) =>
    file.favorited ?
        (<a onClick={() => favoriteFile(file.path)} className="ion-star" style={{ margin: "10px" }} />) :
        (<a className="ion-ios-star-outline fileData" onClick={() => favoriteFile(file.path)} style={{ margin: "10px" }} />);

const MobileButtons = ({ file, forceInlineButtons, rename, refetch }) => {
    return (<span className={(!forceInlineButtons) ? "hidden-lg" : ""}>
        <Dropdown pullRight id="dropdownforfile">
            <Dropdown.Toggle />
            <Dropdown.Menu>
                <MenuItem onClick={() => shareFile(file.path, Cloud)}>
                    Share file
                </MenuItem>
                {file.type === "file" ? <MenuItem onClick={() => downloadFile(file.path, Cloud)}>
                    Download file
                </MenuItem> : null}
                {rename ? <MenuItem onClick={() => rename(file.path)}>
                    Rename file
                </MenuItem> : null}
                <MenuItem onClick={() => copy(file.path)}>
                    Copy file
                </MenuItem>
                <MenuItem onClick={() => showFileDeletionPrompt(file.path, Cloud, refetch)}>
                    Delete file
                </MenuItem>
                <li>
                    <Link to={`/fileInfo/${file.path}/`}>
                        Properties
                    </Link>
                </li>
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

const mapStateToProps = (state, arg2) => {
    const { files, filesPerPage, currentFilesPage, loading, path } = state.files;
    const { uppyFiles, uppyFilesOpen } = state.uppy;
    const favFilesCount = files.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const checkedFilesCount = files.filter(file => file.isChecked).length; // HACK to ensure changes to file checkings are rendered.
    return {
        files, filesPerPage, currentFilesPage, loading, path, uppy: uppyFiles, uppyOpen: uppyFilesOpen, favFilesCount, checkedFilesCount
    }
};

const mapDispatchToProps = (dispatch) => ({
    fetchNewFiles: (path) => {
        dispatch(updatePath(path));
        dispatch(setLoading(true));
        dispatch(fetchFiles(path, sortFilesByTypeAndName, true))
    },
    refetchFiles: (path) =>
        dispatch(fetchFiles(path)),
    updatePath: (path) => dispatch(updatePath(path)),
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    checkFile: (checked, files, newFile) => {
        files.find(file => file.path === newFile.path).isChecked = checked;
        dispatch(updateFiles(files));
    },
    goToPage: (pageNumber, files) => {
        files.forEach(f => f.isChecked = false);
        dispatch(updateFiles(files));
        dispatch(toPage(pageNumber));
    },
    updateFiles: (files) =>
        dispatch(updateFiles(files)),
    updateFilesPerPage: (newSize, files) =>
        dispatch(updateFilesPerPage(newSize, files)),
    openUppy: () => dispatch(changeUppyFilesOpen(true))
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);