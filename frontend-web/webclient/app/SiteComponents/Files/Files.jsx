import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import { Button, Table, Dropdown, MenuItem, Glyphicon } from "react-bootstrap";
import { PaginationButtons, EntriesPerPageSelector } from "../Pagination";
import { BreadCrumbs } from "../Breadcrumbs";
import {
    sortFilesByTypeAndName,
    createFolder,
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
    getTypeFromFile
} from "../../UtilityFunctions";
import Uppy from "uppy";
import { fetchFiles, updateFilesPerPage, updateFiles, setLoading, updatePath, toPage } from "../../Actions/Files";
import { updatePageTitle } from "../../Actions/Status";
import { changeUppyFilesOpen } from "../../Actions/UppyActions";

class Files extends React.Component {
    constructor(props) {
        super(props);
        const urlPath = props.match.params[0];
        const { dispatch, history } = props;
        if (urlPath) {
            dispatch(updatePath(urlPath));
        } else {
            history.push(`/files/${Cloud.homeFolder}/`);
        }
        this.props.uppy.run();
        dispatch(updatePageTitle(this.constructor.name));
        this.state = {
            searchText: "",
            lastSorting: {
                name: "typeAndName",
                asc: true
            }
        };
        this.updateSearchText = this.updateSearchText.bind(this);
        this.selectOrDeselectAllFiles = this.selectOrDeselectAllFiles.bind(this);
        this.sortFilesBy = this.sortFilesBy.bind(this);
    }

    updateSearchText(e) {
        e.preventDefault();
        const value = e.target.value;
        this.setState(() => ({
            searchText: value
        }));
        const files = this.props.files;
        files.forEach(file => file.isChecked = false);
        this.props.dispatch(updateFiles(files));
    }

    selectOrDeselectAllFiles(checked) {
        const { currentFilesPage, filesPerPage, files, dispatch } = this.props;
        files.forEach(file => file.isChecked = false);
        if (checked) {
            files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
                .forEach(file => file.isChecked = true);
        }
        dispatch(updateFiles(files));
    }

    sortFilesBy(name, type) {
        const { files, dispatch, filesPerPage } = this.props;
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
        dispatch(updateFiles(sortedFiles));
        this.setState(() => ({
            lastSorting: { name, asc }
        }));
    }

    componentWillReceiveProps(nextProps) {
        const { dispatch, path } = this.props;
        let newPath = nextProps.match.params[0];
        if (!newPath) {
            this.props.history.push(`/files/${Cloud.homeFolder}`);
            return
        }
        if (path !== newPath) {
            dispatch(updatePath(newPath));
            dispatch(setLoading(true));
            dispatch(fetchFiles(newPath, sortFilesByTypeAndName, true));
        }
    }

    render() {
        const { dispatch, files, filesPerPage, currentFilesPage, path, loading, history } = this.props;
        const totalPages = Math.ceil(this.props.files.length / filesPerPage);
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
            .filter(f => f.path.name.toLowerCase().includes(this.state.searchText.toLowerCase()));
        const masterCheckboxChecked = shownFiles.length === shownFiles.filter(file => file.isChecked).length;
        const checkFile = (checked, newFile) => {
            files.find(file => file.path.path === newFile.path.path).isChecked = checked;
            dispatch(updateFiles(files));
        }
        const goToPage = (pageNumber) => {
            files.forEach(f => f.isChecked = false);
            dispatch(updateFiles(files));
            dispatch(toPage(pageNumber));
        }
        const openUppy = () => dispatch(changeUppyFilesOpen(true));
        return (
            <section>
                <div className="col-lg-10">
                    <BreadCrumbs currentPath={path} navigate={(newPath) => history.push(`/files/${newPath}`)} />
                    <ContextButtons upload={openUppy} createFolder={() => createFolder(path)} mobileOnly={true} />
                    <FilesTable
                        files={shownFiles}
                        loading={loading}
                        masterCheckbox={masterCheckboxChecked}
                        sortingIcon={(name) => getSortingIcon(this.state.lastSorting, name)}
                        addOrRemoveFile={(checked, newFile) => checkFile(checked, newFile)}
                        sortFiles={this.sortFilesBy}
                        favoriteFile={(filePath) => dispatch(updateFiles(favorite(files, filePath, Cloud)))}
                        selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}
                        forceInlineButtons={true}
                    />
                    <BallPulseLoading loading={loading} />
                    <PaginationButtons
                        currentPage={currentFilesPage}
                        totalPages={totalPages}
                        toPage={(pageNumber) => goToPage(pageNumber)}
                    />
                    <EntriesPerPageSelector
                        entriesPerPage={filesPerPage}
                        totalPages={totalPages}
                        handlePageSizeSelection={(newSize) => dispatch(updateFilesPerPage(newSize, files))}
                    >
                        Files per page
                        </EntriesPerPageSelector>
                </div>
                <ContextBar
                    selectedFiles={shownFiles.filter(file => file.isChecked)}
                    currentPath={path}
                    getFavorites={this.getFavorites}
                    onClick={openUppy}
                    searchText={this.state.searchText}
                    updateText={this.updateSearchText}
                />
            </section>);
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

const ContextBar = ({ getFavorites, onClick, currentPath, selectedFiles, searchText, updateText }) => (
    <div className="col-lg-2 visible-lg">
        <SearchBar searchText={searchText} updateText={updateText} />
        <ContextButtons upload={onClick} createFolder={() => createFolder(currentPath)} mobileOnly={false} />
        <br />
        <FileOptions selectedFiles={selectedFiles} />
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


const FileOptions = ({ selectedFiles }) => {
    if (!selectedFiles.length) {
        return null;
    }
    const toFileText = (files) => {
        if (selectedFiles.length > 1) {
            return `${selectedFiles.length} files selected.`;
        } else {
            let filename = selectedFiles[0].path.name;
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
                    to={`/fileInfo/${selectedFiles[0].path.path}/`}>
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
                <Button disabled={downloadDisabled} className="btn btn-default ripple btn-block"
                    onClick={() => downloadFile(selectedFiles[0].path.path, Cloud)}>
                    <span className="ion-ios-download pull-left" />
                    Download
                </Button>
            </p>
            <p>
                <Button type="button" className="btn btn-default btn-block ripple"
                    onClick={() => renameFile(selectedFiles[0].path)}
                    disabled={rights.rightsLevel < 3 || selectedFiles.length !== 1}>
                    <span className="ion-ios-compose pull-left" />
                    Rename
                </Button>
            </p>
            <p>
                <Button className="btn btn-danger btn-block ripple"
                    disabled={rights.rightsLevel < 3 || selectedFiles.length > 1}
                    onClick={() => showFileDeletionPrompt(selectedFiles[0].path)}>
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
    } else if (!props.files.length) {
        return (
            <div className="card">
                <h3 className="text-center">
                    <small>There are no files in current folder</small>
                </h3>
            </div>);
    }

    let hasCheckbox = (!!props.selectOrDeselectAllFiles);
    let masterCheckbox = (hasCheckbox) ? (
        <span style={{ margin: "15px" }}>
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
                <tr>
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
                </tr>
            </thead>

            <FilesList
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

const FilesList = ({ files, addOrRemoveFile, favoriteFile, hasCheckbox, forceInlineButtons }) => (
    <tbody>{files.map((file, index) =>
        (<File
            key={index}
            file={file}
            addOrRemoveFile={addOrRemoveFile}
            favoriteFile={favoriteFile}
            hasCheckbox={hasCheckbox}
            forceInlineButtons={forceInlineButtons}
            owner={getOwnerFromAcls(file.acl, Cloud)}
            style={file.type === "DIRECTORY" ? ({ cursor: "pointer" }) : {}}
        />)
    )}</tbody>);

const File = ({ file, favoriteFile, addOrRemoveFile, owner, hasCheckbox, forceInlineButtons, style }) => (
    <tr className="row-settings clickable-row fileRow" style={style}>
        <td>
            {(hasCheckbox) ? (
                <FileCheckbox className="fileData"
                    isChecked={file.isChecked}
                    onChange={(e) => addOrRemoveFile(e.target.checked, file)}
                />
            ) : null}
            <FileType type={file.type} path={file.path} />
            {(!!favoriteFile) ? <Favorited file={file} favoriteFile={favoriteFile} /> : null}
        </td>
        <td>{new Date(file.modifiedAt).toLocaleString()}</td>
        <td>{owner}</td>
        <td>
            <Button className="fileData" onClick={() => shareFile(file.path, Cloud)}>Share</Button>
            <MobileButtons file={file} forceInlineButtons={forceInlineButtons} />
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

const FileType = ({ type, path }) =>
    type === "FILE" ?
        (<React.Fragment>
            <i className={getTypeFromFile(path.name)} style={{ fontSize: "32px", paddingRight: "11px", verticalAlign: "middle" }} />
            <span>{path.name}</span>
        </React.Fragment>) :
        (<Link to={`/files/${path.path}`}><i className="ion-android-folder" style={{ fontSize: "32px", paddingRight: "8px", verticalAlign: "middle" }} /> {path.name}</Link>);

const Favorited = ({ file, favoriteFile }) =>
    file.favorited ?
        (<a onClick={() => favoriteFile(file.path.path)} className="ion-star" style={{ margin: "10px" }} />) :
        (<a className="ion-ios-star-outline fileData" onClick={() => favoriteFile(file.path.path)} style={{ margin: "10px" }} />);

const MobileButtons = ({ file, forceInlineButtons }) => (
    <span className={(!forceInlineButtons) ? "hidden-lg" : ""}>
        <Dropdown pullRight id="dropdownforfile">
            <Dropdown.Toggle />
            <Dropdown.Menu>
                <MenuItem onClick={() => shareFile(file.path, Cloud)}>
                    Share file
                </MenuItem>
                <MenuItem onClick={() => downloadFile(file.path.path, Cloud)}>
                    Download file
                </MenuItem>
                <MenuItem onClick={() => renameFile(file.path)}>
                    Rename file
                </MenuItem>
                <MenuItem onClick={() => showFileDeletionPrompt(file.path)}>
                    Delete file
                </MenuItem>
                <li>
                    <Link to={`/fileInfo/${file.path.path}/`}>
                        <em className="ion-ios-settings-strong" /> Properties
                    </Link>
                </li>
            </Dropdown.Menu>
        </Dropdown>
    </span>
);


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
    const { files, filesPerPage, currentFilesPage, loading, path } = state.files;
    const { uppyFiles, uppyFilesOpen } = state.uppy;
    const favFilesCount = files.filter(file => file.favorited).length; // Hack to ensure changes to favorites are rendered.
    const checkedFilesCount = files.filter(file => file.isChecked).length; // Hack to ensure changes to file checkings are rendered.
    return {
        files, filesPerPage, currentFilesPage, loading, path, uppy: uppyFiles, uppyOpen: uppyFilesOpen, favFilesCount, checkedFilesCount
    }
};

export default connect(mapStateToProps)(Files);