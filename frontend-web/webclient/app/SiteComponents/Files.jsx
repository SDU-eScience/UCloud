import React from "react";
import PropTypes from "prop-types";
import {connect} from 'react-redux'
import {BallPulseLoading} from "./LoadingIcon";
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from "react-router-dom";
import {Button, Table} from "react-bootstrap";
import {PaginationButtons, EntriesPerPageSelector} from "./Pagination";
import {BreadCrumbs} from "./Breadcrumbs";
import {
    sortFilesByTypeAndName,
    createFolder,
    sortFilesByModified,
    sortFilesByFavorite,
    sortFilesByOwner,
    sortFilesBySensitivity,
    shareFile,
    favorite,
    getOwnerFromAcls,
    renameFile,
    showFileDeletionPrompt,
    getCurrentRights,
    sendToAbacus,
    downloadFile,
    toLowerCaseAndCapitalize,
} from "../UtilityFunctions";
import Uppy from "uppy";
import {tusConfig} from "../Configurations";
import pubsub from "pubsub-js";
import { fetchFiles, updateFilesPerPage, updateFiles, setLoading, updatePath, toPage } from "../Actions/Files";
import { changeUppyFilesOpen } from "../Actions/UppyActions";
import { initializeUppy } from "../DefaultObjects";

class Files extends React.Component {
    constructor(props, context) {
        super(props);
        const urlPath = props.match.params[0];
        const {dispatch, history} = props;
        if (urlPath) {
            dispatch(updatePath(urlPath));
        } else {
            history.push(`/files/${Cloud.homeFolder}/`);
        }
        this.props.uppy.run();

        pubsub.publish('setPageTitle', this.constructor.name);
        this.state = {
            lastSorting: {
                name: "typeAndName",
                asc: true,
            },
        };
        this.addOrRemoveFile = this.addOrRemoveFile.bind(this);
        this.selectOrDeselectAllFiles = this.selectOrDeselectAllFiles.bind(this);
        this.sortFilesBy = this.sortFilesBy.bind(this);
        this.getSortingIcon = this.getSortingIcon.bind(this);
    }

    componentWillUnmount() {
    }

    getSortingIcon(name) {
        if (this.state.lastSorting.name === name) {
            return this.state.lastSorting.asc ? "ion-chevron-down" : "ion-chevron-up";
        }
        return "";
    }

    selectOrDeselectAllFiles(checked) {
        const {currentFilesPage, filesPerPage, files, dispatch} = this.props;
        files.forEach(file => file.isChecked = false);
        if (checked) {
            files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage)
                .forEach(file => file.isChecked = true);
        }

        dispatch(updateFiles(files));
    }

    addOrRemoveFile(checked, newFile) {
        const {files, currentPage, filesPerPage, dispatch} = this.props;
        files.find(file => file.path.path === newFile.path.path).isChecked = checked;
        dispatch(updateFiles(files));
    }

    sortFilesBy(fileSorting, sortingFunction) {
        const {files, dispatch, filesPerPage} = this.props;
        const asc = (this.state.lastSorting.name === fileSorting) ? !this.state.lastSorting.asc : true;
        const sortedFiles = sortingFunction(files, asc);
        if (sortedFiles.length > filesPerPage) {
            sortedFiles.forEach((file) => file.isChecked = false);
        }
        dispatch(updateFiles(sortedFiles));
        this.setState(() => ({
            lastSorting: {name: fileSorting, asc: asc}
        }));
    }

    componentWillReceiveProps(nextProps) {
        const {dispatch, path} = this.props;
        let newPath = nextProps.match.params[0];
        if (!newPath) {
            this.props.history.push(`/files/${Cloud.homeFolder}`);
            return
        }
        if (path !== newPath && !nextProps.loading) {
            dispatch(updatePath(newPath));
            dispatch(setLoading(true));
            dispatch(fetchFiles(newPath, sortFilesByTypeAndName, true));
        }
    }

    render() {
        const {dispatch, files, filesPerPage, currentFilesPage, path, loading, history} = this.props;
        const shownFiles = files.slice(currentFilesPage * filesPerPage, currentFilesPage * filesPerPage + filesPerPage);
        const masterCheckboxChecked = shownFiles.length === shownFiles.filter(file => file.isChecked).length;
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <BreadCrumbs currentPath={path} navigate={(newPath) => history.push(`/files/${newPath}`)}/>
                        <FilesTable
                            files={shownFiles}

                            loading={loading}
                            masterCheckbox={masterCheckboxChecked}
                            sortingIcon={this.getSortingIcon}
                            addOrRemoveFile={this.addOrRemoveFile}
                            sortFiles={this.sortFilesBy}
                            favoriteFile={(filePath) => dispatch(updateFiles(favorite(files, filePath, Cloud)))}
                            selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}
                        />
                        <BallPulseLoading loading={loading}/>
                        <PaginationButtons
                            currentPage={currentFilesPage}
                            totalEntries={this.props.files.length}
                            entriesPerPage={filesPerPage}
                            toPage={pageNumber => dispatch(toPage(pageNumber))}/>
                        <EntriesPerPageSelector entriesPerPage={filesPerPage}
                                                handlePageSizeSelection={(newSize) => dispatch(updateFilesPerPage(newSize, files))}/> Files
                        per page
                    </div>
                    <ContextBar selectedFiles={shownFiles.filter(file => file.isChecked)}
                                currentPath={path}
                                getFavorites={this.getFavorites}
                                onClick={open => dispatch(changeUppyFilesOpen(open))}/>
                </div>
            </section>)
    } // TODO: Remove dashboard modal from this and move it to root.
}

const ContextBar = (props) => (
    <div className="col-lg-2 visible-lg">
        <div>
            <div className="center-block text-center">
                <Button className="btn btn-link btn-lg" onClick={() => props.getFavorites()}><i
                    className="icon ion-star"/></Button>
                <Link to={`/files/${Cloud.homeFolder}`}><Button className="btn btn-link btn-lg"><i
                    className="ion-ios-home"/></Button></Link>
            </div>
            <hr/>
            <button className="btn btn-primary btn-block"
                    onClick={() => props.onClick(true)}>
                <span className="ion-android-upload pull-left"/> Upload Files
            </button>
            <br/>
            <button className="btn btn-default btn-block"
                    onClick={() => createFolder(props.currentPath)}>
                <span className="ion-folder pull-left"/> New folder
            </button>
            <br/>
            <hr/>
            <FileOptions selectedFiles={props.selectedFiles}/>
        </div>
    </div>
);

const FileOptions = ({selectedFiles, ...props}) => {
    if (!selectedFiles.length) {
        return null;
    }
    let rights = getCurrentRights(selectedFiles, Cloud);
    let fileText = "";
    if (selectedFiles.length > 1) {
        fileText = `${selectedFiles.length} files selected.`;
    } else {
        let filename = selectedFiles[0].path.name;
        if (filename.length > 10) {
            fileText = filename.slice(0, 17) + "...";
        } else {
            fileText = filename;
        }
    }
    const downloadDisabled = (selectedFiles.length > 1 || selectedFiles[0].sensitivityLevel === "SENSITIVE");
    return (
        <div>
            <h3>{fileText}</h3>
            <p>
                <Link disabled={selectedFiles.length !== 1} className="btn btn-primary ripple btn-block"
                      to={`/fileInfo/${selectedFiles[0].path.path}/`}><span
                    className="ion-ios-settings-strong pull-left"/>Properties</Link>
            </p>
            <p>
                <Button type="button" className="btn btn-default ripple btn-block"
                        disabled={selectedFiles.length > 1}
                        onClick={() => shareFile(selectedFiles[0].path, Cloud)}><span
                    className="ion-share pull-left"/> Share
                </Button>
            </p>
            <p>
                <Button disabled={downloadDisabled} className="btn btn-default ripple btn-block"
                        onClick={() => downloadFile(selectedFiles[0].path.path, Cloud)}>
                    <span className="ion-ios-download pull-left"/>
                    Download
                </Button>
            </p>
            <p>
                <Button type="button" className="btn btn-default btn-block ripple"
                        onClick={() => renameFile(selectedFiles[0].path)}
                        disabled={rights.rightsLevel < 3 || selectedFiles.length !== 1}>
                    <span className="ion-ios-compose pull-left"/>
                    Rename
                </Button>
            </p>
            <p>
                <Button className="btn btn-danger btn-block ripple"
                        disabled={rights.rightsLevel < 3 || selectedFiles.length > 1}
                        onClick={() => showFileDeletionPrompt(selectedFiles[0].path)}>
                    <em className="ion-ios-trash pull-left"/>
                    Delete
                </Button>
            </p>
        </div>
    )
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
        <th className="select-cell disabled">
            <label className="mda-checkbox">
                <input
                    name="select"
                    className="select-box"
                    checked={props.masterCheckbox}
                    type="checkbox"
                    onChange={e => props.selectOrDeselectAllFiles(e.target.checked)}
                />
                <em className="bg-info"/>
            </label>
        </th>
    ) : null;

    let hasFavoriteButton = (!!props.favoriteFile);

    let sortingFunction = (!!props.sortFiles) ? props.sortFiles : () => 0;
    let sortingIconFunction = (!!props.sortingIcon) ? props.sortingIcon : () => "";

    return (
        <div className="card">
            <div className="card-body">
                <Table responsive className="table table-hover mv-lg">
                    <thead>
                    <tr>
                        {masterCheckbox}

                        <th onClick={() => sortingFunction("typeAndName", sortFilesByTypeAndName)}>
                            <span className="text-left">
                                Filename
                                <span className={"pull-right " + sortingIconFunction("typeAndName")}/>
                            </span>
                        </th>

                        {hasFavoriteButton ? (
                            <th onClick={() => sortingFunction("favorite", sortFilesByFavorite)}>
                                <span>
                                    <em className="ion-star"/>
                                    <span className={"pull-right " + sortingIconFunction("favorite")}/>
                                </span>
                            </th>
                        ) : null}

                        <th onClick={() => sortingFunction("modifiedAt", sortFilesByModified)}>
                            <span className="text-left">
                                Last Modified
                                <span className={"pull-right " + sortingIconFunction("modifiedAt")}/>
                            </span>
                        </th>

                        <th onClick={() => sortingFunction("owner", sortFilesByOwner)}>
                            <span className="text-left">
                                File Rights
                                <span className={"pull-right " + sortingIconFunction("owner")}/>
                            </span>
                        </th>

                        <th onClick={() => sortingFunction("sensitivity", sortFilesBySensitivity)}>
                            <span className="text-left">
                                Sensitivity Level
                                <span className={"pull-right " + sortingIconFunction("sensitivity")}/>
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
            </div>
        </div>)
};

const fileTypeToConstructor = (type) => {
    switch (type) {
        case "DIRECTORY":
            return Directory;
        case "FILE":
            return File;
        default:
            console.warn("Unknown file type!");
            return File;
    }
};

const FilesList = ({files, addOrRemoveFile, favoriteFile, hasCheckbox, forceInlineButtons}) => {
    let filesList = files.map((file, index) => {
        let Component = fileTypeToConstructor(file.type);
        return <Component
            key={index}
            file={file}
            addOrRemoveFile={addOrRemoveFile}
            favoriteFile={favoriteFile}
            hasCheckbox={hasCheckbox}
            forceInlineButtons={forceInlineButtons}
            owner={getOwnerFromAcls(file.acl, Cloud)}
        />
    });

    return <tbody>{filesList}</tbody>;
};

const File = ({file, favoriteFile, addOrRemoveFile, owner, hasCheckbox, forceInlineButtons}) => (
    <tr className="row-settings clickable-row">
        {(hasCheckbox) ? (
            <td className="select-cell">
                <label className="mda-checkbox">
                    <input name="select" className="select-box" checked={file.isChecked}
                           type="checkbox" onChange={(e) => addOrRemoveFile(e.target.checked, file)}/>
                    <em className="bg-info"/>
                </label>
            </td>
        ) : null}

        <FileType type={file.type} path={file.path}/>
        {(!!favoriteFile) ? <Favorited file={file} favoriteFile={favoriteFile}/> : null}
        <td>{new Date(file.modifiedAt).toLocaleString()}</td>
        <td>{owner}</td>
        <td>{toLowerCaseAndCapitalize(file.sensitivityLevel)}</td>
        <td>
            <MobileButtons file={file} forceInlineButtons={forceInlineButtons}/>
        </td>
    </tr>
);

const Directory = ({file, favoriteFile, addOrRemoveFile, owner, hasCheckbox, forceInlineButtons}) => (
    <tr className="row-settings clickable-row" style={{cursor: "pointer"}}>
        {(hasCheckbox) ? (
            <td className="select-cell">
                <label className="mda-checkbox">
                    <input
                        name="select"
                        className="select-box"
                        checked={file.isChecked}
                        type="checkbox"
                        onChange={(e) => addOrRemoveFile(e.target.checked, file)}
                    />
                    <em className="bg-info"/>
                </label>
            </td>
        ) : null}
        <FileType type={file.type} path={file.path}/>
        {(!!favoriteFile) ? <Favorited file={file} favoriteFile={favoriteFile}/> : null}
        <td>{new Date(file.modifiedAt).toLocaleString()}</td>
        <td>{owner}</td>
        <td>{toLowerCaseAndCapitalize(file.sensitivityLevel)}</td>
        <td><MobileButtons file={file} forceInlineButtons={forceInlineButtons}/></td>
    </tr>
);

const FileType = ({type, path}) =>
    type === "FILE" ?
        (<td><span className="ion-android-document"/> {path.name}</td>) :
        (<td><Link to={`/files/${path.path}`}><span className="ion-android-folder"/> {path.name}</Link></td>);

const Favorited = ({file, favoriteFile}) =>
    file.favorited ?
        (<td><a onClick={() => favoriteFile(file.path.path)} className="ion-star"/></td>) :
        (<td><a className="ion-ios-star-outline" onClick={() => favoriteFile(file.path.path)}/></td>);

const MobileButtons = ({file, forceInlineButtons}) =>
    (<span className={(!forceInlineButtons) ? "hidden-lg" : ""}>
            <div className="pull-right dropdown">
                <button type="button" data-toggle="dropdown"
                        className="btn btn-flat btn-flat-icon"
                        aria-expanded="false"><em className="ion-android-more-vertical"/></button>
                <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                    <li><a className="btn btn-info ripple btn-block"
                           onClick={() => sendToAbacus()}> Send to Abacus 2.0</a></li>
                    <li><a className="btn btn-default ripple btn-block ion-share"
                           onClick={() => shareFile(file.path, Cloud)}> Share file</a></li>
                    <li><a className="btn btn-default ripple btn-block ion-ios-download"
                           onClick={() => downloadFile(file.path.path, Cloud)}> Download file</a></li>
                    <li><a className="btn btn-default ripple ion-ios-compose"
                           onClick={() => renameFile(file.path)}> Rename file</a></li>
                    <li><a className="btn btn-danger ripple ion-ios-trash"
                           onClick={() => showFileDeletionPrompt(file.path)}> Delete file</a></li>
                    <li><Link className="btn btn-default ripple btn-block ion-ios-settings-strong"
                              to={`/fileInfo/${file.path.path}/`}> Properties</Link></li>
                </ul>
            </div>
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