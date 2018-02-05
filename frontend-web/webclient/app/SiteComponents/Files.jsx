import React from 'react';
import {BallPulseLoading} from './LoadingIcon';
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from 'react-router';
import {Button, Table} from 'react-bootstrap';
import PaginationButtons from "./Pagination"
import {
    buildBreadCrumbs,
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
} from '../UtilityFunctions'
import Uppy from "uppy";
import {DashboardModal} from "uppy/lib/react"
import {SensitivityLevel} from "../DefaultObjects";
import {tusConfig} from "../Configurations";
import pubsub from "pubsub-js";

class Files extends React.Component {
    constructor(props) {
        super(props);
        let currentPath = (!props.routeParams.splat) ? `home/${Cloud.username}` : props.routeParams.splat;
        this.state = {
            files: [],
            loading: false,
            totalPages: () => Math.ceil(this.state.files.length / this.state.filesPerPage),
            currentPage: 0,
            filesPerPage: 10,
            masterCheckbox: false,
            currentPath: currentPath,
            uploadFileOpen: false,
            sortingFunctions: {
                typeAndName: sortFilesByTypeAndName,
                modifiedAt: sortFilesByModified,
                favorite: sortFilesByFavorite,
                owner: sortFilesByOwner,
                sensitivity: sortFilesBySensitivity,
            },
            lastSorting: {
                name: "typeAndName",
                asc: true,
            },
            uppy: Uppy.Core({
                autoProceed: false,
                debug: false,
                meta: {
                    sensitive: false,
                },
                onBeforeUpload: () => {
                    return Cloud.receiveAccessTokenOrRefreshIt().then((data) => {
                        tusConfig.headers["Authorization"] = "Bearer " + data;
                    }).promise();
                }
            }),
        };
        this.getFavorites = this.getFavorites.bind(this);
        this.getFiles = this.getFiles.bind(this);
        this.addOrRemoveFile = this.addOrRemoveFile.bind(this);
        this.selectOrDeselectAllFiles = this.selectOrDeselectAllFiles.bind(this);
        this.handlePageSizeSelection = this.handlePageSizeSelection.bind(this);
        this.getCurrentFiles = this.getCurrentFiles.bind(this);
        this.toPage = this.toPage.bind(this);
        this.previousPage = this.previousPage.bind(this);
        this.nextPage = this.nextPage.bind(this);
        this.handleClose = this.handleClose.bind(this);
        this.handleOpen = this.handleOpen.bind(this);
        this.sortFilesBy = this.sortFilesBy.bind(this);
        this.getSortingIcon = this.getSortingIcon.bind(this);
        this.favoriteFile = this.favoriteFile.bind(this);
    }

    favoriteFile(fileUri) {
        this.setState(() => ({
            files: favorite(this.state.files.slice(), fileUri),
        }));
    }

    getSortingIcon(name) {
        if (this.state.lastSorting.name === name) {
            return this.state.lastSorting.asc ? "ion-chevron-down" : "ion-chevron-up";
        }
        return "";
    }

    handleOpen() {
        this.setState(() => ({
            uploadFileOpen: true,
        }));
    }

    handleClose() {
        this.setState(() => ({
            uploadFileOpen: false,
        }));
    }

    selectOrDeselectAllFiles(checked) {
        let currentPage = this.state.currentPage;
        let filesPerPage = this.state.filesPerPage;
        let files = this.state.files.slice();
        files.forEach(file => file.isChecked = false);
        if (checked) {
            let selectedFiles = files.slice(currentPage * filesPerPage, currentPage * filesPerPage + filesPerPage);
            selectedFiles.forEach(file => file.isChecked = true);
            this.setState(() => ({
                files: files,
                masterCheckbox: true,
            }));
        } else {
            this.setState(() => ({
                files: files,
                masterCheckbox: false,
            }));
        }
    }

    addOrRemoveFile(checked, newFile) {
        let files = this.state.files.slice();
        files.find(file => file.path.uri === newFile.path.uri).isChecked = checked;
        let currentPage = this.state.currentPage;
        let filesPerPage = this.state.filesPerPage;
        let currentlyShownFiles = this.state.files.slice(currentPage * filesPerPage, currentPage * filesPerPage + filesPerPage);
        let selectedFilesCount = currentlyShownFiles.filter(file => file.isChecked).length;
        this.setState(() => ({
            files: files,
            masterCheckbox: currentlyShownFiles.length === selectedFilesCount,
        }));
    }

    handlePageSizeSelection(newPageSize) {
        const files = this.state.files.slice();
        files.forEach(file => file.isChecked = false);
        this.setState(() => ({
            files: files,
            filesPerPage: newPageSize,
            masterCheckbox: false,
        }));
    }

    getFiles() {
        this.setState({
            loading: true,
        });
        Cloud.get(`files?path=/${this.state.currentPath}`).then(files => {
            files.forEach(file => file.isChecked = false);
            this.setState(() => ({
                files: this.state.sortingFunctions.typeAndName(files, true),
                loading: false,
            }));
        });
    }

    sortFilesBy(fileSorting) {
        let asc = (this.state.lastSorting.name === fileSorting) ? !this.state.lastSorting.asc : true;
        let files = this.state.sortingFunctions[fileSorting](this.state.files, asc);
        this.setState(() => ({
            files: files,
            lastSorting: {
                name: fileSorting,
                asc: asc,
            },
        }));
    }

    toPage(n) {
        let files = this.state.files;
        files.forEach(file => file.isChecked = false);
        this.setState(() => ({
            files: files,
            currentPage: n,
            masterCheckbox: false,
        }));
    }

    getCurrentFiles() {
        let filesPerPage = this.state.filesPerPage;
        let currentPage = this.state.currentPage;
        return this.state.files.slice(currentPage * filesPerPage, currentPage * filesPerPage + filesPerPage);
    }

    nextPage() {
        let files = this.state.files;
        files.forEach(file => file.isChecked = false);
        this.setState(() => ({
            files: files,
            currentPage: this.state.currentPage + 1,
            masterCheckbox: false,
        }));
    }

    previousPage() {
        let files = this.state.files;
        files.forEach(file => file.isChecked = false);
        this.setState(() => ({
            files: files,
            currentPage: this.state.currentPage - 1,
            masterCheckbox: false,
        }));
    }

    getFavorites() {
        this.setState({
            loading: true,
        });
        let currentPath = `/home/${Cloud.username}`;
        Cloud.get(`files?path=${currentPath}`).then(files => {
            files.forEach(file => file.isChecked = false);
            let favorites = files.filter(file => file.favorited);
            this.setState(() => ({
                files: this.state.sortingFunctions.typeAndName(favorites, true),
                loading: false,
                currentPath: currentPath,
            }));
        });
    }

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.state.uppy.use(Uppy.Tus, tusConfig);
        this.state.uppy.run();
        this.getFiles();
    }

    componentWillUnmount() {
        this.setState(() => {
            let result = {
                uppy: this.state.uppy,
            };
            result.uppy.close();
            return result;
        });
    }


    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <Breadcrumbs currentPath={this.state.currentPath}/>
                        <FilesTable files={this.getCurrentFiles()} loading={this.state.loading}
                                    selectedFiles={this.state.selectedFiles}
                                    masterCheckbox={this.state.masterCheckbox} sortingIcon={this.getSortingIcon}
                                    favorite={this.favoriteFile} addOrRemoveFile={this.addOrRemoveFile}
                                    sortFiles={this.sortFilesBy}
                                    selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}/>
                        <BallPulseLoading loading={this.state.loading}/>
                        <PaginationButtons
                            currentPage={this.state.currentPage}
                            totalPages={this.state.totalPages}
                            nextPage={this.nextPage}
                            previousPage={this.previousPage}
                            toPage={this.toPage}/>
                        <FilesPerPageSelector filesPerPage={this.state.filesPerPage}
                                              handlePageSizeSelection={this.handlePageSizeSelection}/> Files per
                        page
                    </div>
                    <ContextBar selectedFiles={this.state.files.filter(file => file.isChecked)}
                                currentPath={this.state.currentPath}
                                getFavorites={this.getFavorites}
                                onClick={this.handleOpen}/>
                </div>
                <DashboardModal uppy={this.state.uppy} open={this.state.uploadFileOpen} closeModalOnClickOutside
                                onRequestClose={this.handleClose}/>
            </section>)
    }
}

function FilesPerPageSelector(props) {
    return (
        <select value={props.filesPerPage} onChange={e => props.handlePageSizeSelection(parseInt(e.target.value))}>
            <option value="10">10</option>
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
        </select>)
}

function ContextBar(props) {
    return (
        <div className="col-lg-2 visible-lg">
            <div>
                <div className="center-block text-center">
                    <Button className="btn btn-link btn-lg" onClick={() => props.getFavorites()}><i
                        className="icon ion-star"/></Button>
                    <Link to={`files?path=/home/${Cloud.username}`}><Button className="btn btn-link btn-lg"><i
                        className="ion-ios-home"/></Button></Link>
                </div>
                <hr/>
                <button className="btn btn-primary ripple btn-block" id="uppy"
                        onClick={props.onClick}>
                    <span className="ion-android-upload pull-left"/> Upload Files
                </button>
                <br/>
                <button className="btn btn-default ripple btn-block"
                        onClick={() => createFolder(props.currentPath)}>
                    <span className="ion-folder pull-left"/> New folder
                </button>
                <br/>
                <hr/>
                <FileOptions selectedFiles={props.selectedFiles}/>
            </div>
        </div>
    )
}

function FileOptions(props) {
    if (!props.selectedFiles.length) {
        return null;
    }
    let rights = getCurrentRights(props.selectedFiles);
    let fileText = "";
    if (props.selectedFiles.length > 1) {
        fileText = `${props.selectedFiles.length} files selected.`;
    } else {
        let filename = props.selectedFiles[0].path.name;
        if (filename.length > 10) {
            fileText = filename.slice(0, 17) + "...";
        } else {
            fileText = filename;
        }
    }
    const downloadDisabled = (props.selectedFiles.length > 1 || props.selectedFiles[0].sensitivityLevel === "SENSITIVE");
    return (
        <div>
            <h3>{fileText}</h3>
            <p>
                <Link disabled={props.selectedFiles.length !== 1} className="btn btn-primary ripple btn-block"
                      to={`/fileInfo/${props.selectedFiles[0].path.path}`}><span
                    className="ion-ios-settings-strong pull-left"/>Properties</Link>
            </p>
            <p>
                <Button type="button" className="btn btn-default ripple btn-block"
                        disabled={props.selectedFiles.length > 1}
                        onClick={() => shareFile(props.selectedFiles[0].path)}><span
                    className="ion-share pull-left"/> Share selected file
                </Button>
            </p>
            <p>
                <Button disabled={downloadDisabled} className="btn btn-default ripple btn-block"
                        onClick={() => downloadFile(props.selectedFiles[0].path.path)}>
                    <span className="ion-ios-download pull-left"/>
                    Download selected files
                </Button>
            </p>
            <p>
                <Button type="button" className="btn btn-default btn-block ripple"
                        onClick={() => renameFile(props.selectedFiles[0].path)}
                        disabled={rights.rightsLevel < 3 || props.selectedFiles.length !== 1}>
                    <span className="ion-ios-compose pull-left"/>
                    Rename file
                </Button>
            </p>
            <p>
                <Button className="btn btn-danger btn-block ripple"
                        disabled={rights.rightsLevel < 3 || props.selectedFiles.length > 1}
                        onClick={() => showFileDeletionPrompt(props.selectedFiles[0].path)}>
                    <em className="ion-ios-trash pull-left"/>
                    Delete selected files
                </Button>
            </p>
        </div>
    )
}


function FilesTable(props) {
    if (props.loading) {
        return null;
    } else if (!props.files.length) {
        return (
            <div>
                <h3 className="text-center">
                    <small>There are no files in current folder</small>
                </h3>
            </div>);
    }
    return (
        <div className="card">
            <div className="card-body">
                <Table responsive className="table table-hover mv-lg">
                    <thead>
                    <tr>
                        <th className="select-cell disabled"><label className="mda-checkbox">
                            <input name="select" className="select-box"
                                   checked={props.masterCheckbox}
                                   type="checkbox" onChange={e => props.selectOrDeselectAllFiles(e.target.checked)}/><em
                            className="bg-info"/></label></th>
                        <th onClick={() => props.sortFiles("typeAndName")}><span className="text-left">Filename<span
                            className={"pull-right " + props.sortingIcon("typeAndName")}/></span>
                        </th>
                        <th onClick={() => props.sortFiles("favorite")}><span><em className="ion-star"/><span
                            className={"pull-right " + props.sortingIcon("favorite")}/></span></th>
                        <th onClick={() => props.sortFiles("modifiedAt")}><span className="text-left">Last Modified<span
                            className={"pull-right " + props.sortingIcon("modifiedAt")}/></span></th>
                        <th onClick={() => props.sortFiles("owner")}><span className="text-left">File Rights<span
                            className={"pull-right " + props.sortingIcon("owner")}/></span>
                        </th>
                        <th onClick={() => props.sortFiles("sensitivity")}><span
                            className="text-left">Sensitivity Level<span
                            className={"pull-right " + props.sortingIcon("sensitivity")}/></span></th>
                    </tr>
                    </thead>
                    <FilesList files={props.files} favorite={props.favorite}
                               selectedFiles={props.selectedFiles}
                               addOrRemoveFile={props.addOrRemoveFile}/>
                </Table>
            </div>
        </div>)
}

function FilesList(props) {
    let i = 0;
    let filesList = props.files.map(file => {
        if (file.type === "DIRECTORY") {
            return <Directory key={i++} file={file} addOrRemoveFile={props.addOrRemoveFile} favorite={props.favorite}
                              isChecked={file.isChecked}/>
        } else {
            return <File key={i++} file={file} isChecked={file.isChecked} addOrRemoveFile={props.addOrRemoveFile}
                         favorite={props.favorite}/>
        }
    });
    return (
        <tbody>
        {filesList}
        </tbody>
    )
}

function File(props) {
    const file = props.file;
    const owner = getOwnerFromAcls(file.acl);
    return (
        <tr className="row-settings clickable-row">
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" checked={props.isChecked}
                       type="checkbox" onChange={(e) => props.addOrRemoveFile(e.target.checked, file)}/>
                <em className="bg-info"/></label></td>
            <FileType type={file.type} path={file.path}/>
            <Favorited file={file} favorite={props.favorite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{owner}</td>
            <td>{SensitivityLevel[file.sensitivityLevel]}</td>
            <td>
                <MobileButtons file={file}/>
            </td>
        </tr>)
}

function Directory(props) {
    const file = props.file;
    const owner = getOwnerFromAcls(file.acl);
    return (
        <tr className="row-settings clickable-row"
            style={{cursor: "pointer"}}>
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" checked={props.isChecked}
                       type="checkbox" onChange={(e) => props.addOrRemoveFile(e.target.checked, file)}/><em
                className="bg-info"/></label></td>
            <FileType type={file.type} path={file.path}/>
            <Favorited file={file} favorite={props.favorite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{owner}</td>
            <td>{SensitivityLevel[file.sensitivityLevel]}</td>
            <td>
                <MobileButtons file={file}/>
            </td>
        </tr>)
}

function FileType(props) {
    if (props.type === "FILE")
        return (
            <td>
                <span className="ion-android-document"/> {props.path.name}
            </td>);
    return (
        <td>
            <Link to={`/files/${props.path.path}`}>
                <span className="ion-android-folder"/> {props.path.name}
            </Link>
        </td>);
}

function Favorited(props) {
    if (props.file.favorited) {
        return (<td><a onClick={() => props.favorite(props.file.path.uri)} className="ion-star"/></td>)
    }
    return (<td><a className="ion-ios-star-outline" onClick={() => props.favorite(props.file.path.uri)}/></td>);
}

function MobileButtons(props) {
    let file = props.file;
    return (
        <span className="hidden-lg">
            <div className="pull-right dropdown">
                <button type="button" data-toggle="dropdown"
                        className="btn btn-flat btn-flat-icon"
                        aria-expanded="false"><em className="ion-android-more-vertical"/></button>
                <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                    <li><a className="btn btn-info ripple btn-block"
                           onClick={() => sendToAbacus()}> Send to Abacus 2.0</a></li>
                    <li><a className="btn btn-default ripple btn-block ion-share"
                           onClick={() => shareFile(file.path)}> Share file</a></li>
                    <li><a
                        className="btn btn-default ripple btn-block ion-ios-download"> Download file</a></li>
                    <li><a className="btn btn-default ripple ion-ios-compose"
                           onClick={() => renameFile(file.path)}> Rename file</a></li>
                    <li><a className="btn btn-danger ripple ion-ios-trash"
                           onClick={() => showFileDeletionPrompt(file.path)}> Delete file</a></li>
                    <li><Link className="btn btn-default ripple btn-block ion-ios-settings-strong"
                              to={`/fileInfo/${file.path.path}`}>Properties</Link></li>
                </ul>
            </div>
        </span>)
}

function Breadcrumbs(props) {
    if (!props.currentPath) {
        return null;
    }
    let pathsMapping = buildBreadCrumbs(props.currentPath);
    let i = 0;
    let breadcrumbs = pathsMapping.map(path =>
        <li key={i++} className="breadcrumb-item">
            <Link to={`files/${path.actualPath}`}>{path.local}</Link>
        </li>
    );
    return (
        <ol className="breadcrumb">
            {breadcrumbs}
        </ol>)
}

export default Files;
