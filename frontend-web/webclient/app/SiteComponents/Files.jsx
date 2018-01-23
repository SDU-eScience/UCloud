import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from 'react-router';
import {Button} from 'react-bootstrap';
import {buildBreadCrumbs, sortFiles, createFolder} from '../UtilityFunctions'
import Uppy from "uppy";
import {DashboardModal} from "uppy/lib/react"
import {RightsMap} from "../DefaultObjects";
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
            uppy: Uppy.Core({
                autoProceed: false,
                debug: true,
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

    static sendToAbacus() {
        // console.log("Send to Abacus TODO!")
    }

    static shareFile() {
        // console.log("Share file")
    }

    static renameFile() {
        // console.log("TODO");
    }

    static showFileDeletionPrompt() {

    }

    static getCurrentRights(files) {
        let lowestPrivilegeOptions = RightsMap["OWN"];
        files.forEach((it) => {
            it.acl.forEach((acl) => {
                lowestPrivilegeOptions = Math.min(RightsMap[acl.right], lowestPrivilegeOptions);
            });
        });
        return {
            rightsName: Object.keys(RightsMap)[lowestPrivilegeOptions - 1],
            rightsLevel: lowestPrivilegeOptions
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

    handlePageSizeSelection(event) {
        let value = parseInt(event.target.value);
        this.setState(() => ({filesPerPage: value}));
    }

    getFiles() {
        this.setState({
            loading: true,
        });
        Cloud.get("files?path=/" + this.state.currentPath).then(favourites => {
            favourites.forEach(file => file.isChecked = false);
            this.setState(() => ({
                files: sortFiles(favourites),
                loading: false,
            }));
        });
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

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.state.uppy.use(Uppy.Tus, tusConfig);
        this.state.uppy.run();
        this.getFiles();
    }

    componentWillUnmount() {
        this.setState(() => {
            let result = {uppy: this.state.uppy};
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
                                    masterCheckbox={this.state.masterCheckbox}
                                    getFavourites={() => this.getFavourites} favourite={() => this.favourite}
                                    addOrRemoveFile={this.addOrRemoveFile}
                                    selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}/>
                        <LoadingIcon loading={this.state.loading}/>
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
                                getFavourites={() => this.getFavourites()}
                                onClick={this.handleOpen}/>
                </div>
                <DashboardModal uppy={this.state.uppy} open={this.state.uploadFileOpen} closeModalOnClickOutside
                                onRequestClose={this.handleClose}/>
            </section>)
    }
}

function PaginationButtons(props) {
    const buttons = [...Array(props.totalPages()).keys()].map(i =>
        <span key={i}>
            <button
                className="paginate_button btn btn-default btn-circle btn-info"
                disabled={i === props.currentPage}
                onClick={() => props.toPage(i)}>{i + 1}</button>
        </span>);
    return (
        <div className="text-center">
            <button className="previous btn-default btn btn-circle" onClick={() => props.previousPage()}
                    disabled={props.currentPage === 0}>
                <em className="ion-ios-arrow-left"/></button>
            {buttons}
            <button className="paginate_button next btn-default btn btn-circle ion-ios-arrow-right"
                    onClick={() => props.nextPage()}
                    disabled={props.currentPage === Math.max(props.totalPages() - 1, 0)}/>
        </div>)
}

function FilesPerPageSelector(props) {
    return (
        <select value={props.filesPerPage} onChange={e => props.handlePageSizeSelection(e)}>
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
                    <Button className="btn btn-link btn-lg" onClick={() => props.getFavourites()}><a><i
                        className="icon ion-star"/></a></Button>
                    <Button className="btn btn-link btn-lg"><Link to={`files?path=/home/${Cloud.username}`}><i
                        className="icon ion-ios-home"/></Link></Button>
                </div>
                <hr/>
                <button className="btn btn-primary ripple btn-block ion-android-upload" id="uppy"
                        onClick={props.onClick}> Upload
                    Files
                </button>
                <br/>
                <button className="btn btn-default ripple btn-block ion-folder"
                        onClick={() => createFolder(props.currentPath)}>
                    New folder
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
    let rights = Files.getCurrentRights(props.selectedFiles);
    const fileText = props.selectedFiles.length > 1 ? `${props.selectedFiles.length} files selected.` : props.selectedFiles[0].path.name;
    const rightsLevel = (<RightsLevel rights={rights} fileText={fileText}/>);
    return (
        <div>
            {rightsLevel}
            <p>
                <button type="button" className="btn btn-default ripple btn-block"
                        onClick={Files.shareFile(props.selectedFiles[0].path.name, 'folder')}><span
                    className="ion-share"/> Share selected
                    files
                </button>
            </p>
            <p>
                <Button className="btn btn-default ripple btn-block">
                    <span className="ion-ios-download"/>
                    Download selected files
                </Button>
            </p>
            <p>
                <button type="button" className="btn btn-default btn-block ripple">
                    <span className="ion-android-star"/>
                    Favourite selected files
                </button>
            </p>
            <p>
                <button type="button" className="btn btn-default btn-block ripple"
                        onClick={Files.renameFile(props.selectedFiles[0].path.name, 'folder')}
                        disabled={rights.rightsLevel < 3 || props.selectedFiles.length !== 1}>
                    <span className="ion-ios-compose"/>
                    Rename file
                </button>
            </p>
            <p>
                <button className="btn btn-danger btn-block ripple"
                        disabled={rights.rightsLevel < 3}
                        onClick={Files.showFileDeletionPrompt(props.selectedFiles[0].path, props.selectedFiles.length)}>
                    <em className="ion-ios-trash"/>
                    Delete selected files
                </button>
            </p>
        </div>
    )
}


function FilesTable(props) {
    if (props.loading) {
        return null;
    } else if (!props.files.length) {
        return (<div>
            <h3 className="text-center">
                <small>There are no files in current folder</small>
            </h3>
        </div>);
    }
    return (
        <div className="card">
            <div className="card-body">
                <table className="table-datatable table table-hover mv-lg">
                    <thead>
                    <tr>
                        <th className="select-cell disabled"><label className="mda-checkbox">
                            <input name="select" className="select-box"
                                   checked={props.masterCheckbox}
                                   type="checkbox" onChange={e => props.selectOrDeselectAllFiles(e.target.checked)}/><em
                            className="bg-info"/></label></th>
                        <th><span className="text-left">Filename</span></th>
                        <th><span><em className="ion-star"/></span></th>
                        <th><span className="text-left">Last Modified</span></th>
                        <th><span className="text-left">File Owner</span></th>
                    </tr>
                    </thead>
                    <FilesList files={props.files} favourite={props.favourite}
                               selectedFiles={props.selectedFiles}
                               addOrRemoveFile={props.addOrRemoveFile}/>
                </table>
            </div>
        </div>)
}

function RightsLevel(props) {
    return (
        <h3>
            {`Rights level: ${props.rights.rightsName}`}<br/>
            {props.fileText}
        </h3>);
}

function FilesList(props) {
    let i = 0;
    let directories = props.files.filter(it => it.type === "DIRECTORY");
    let files = props.files.filter(it => it.type !== "DIRECTORY");
    let directoryList = directories.map(file =>
        <Directory key={i++} file={file} addOrRemoveFile={props.addOrRemoveFile} isChecked={file.isChecked}/>
    );
    let filesList = files.map(file =>
        <File key={i++} file={file} isChecked={file.isChecked} addOrRemoveFile={props.addOrRemoveFile}/>
    );
    return (
        <tbody>
        {directoryList}
        {filesList}
        </tbody>
    )
}

function File(props) {
    let file = props.file;
    return (
        <tr className="row-settings clickable-row">
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" checked={props.isChecked}
                       type="checkbox" onChange={(e) => props.addOrRemoveFile(e.target.checked, file)}/>
                <em className="bg-info"/></label></td>
            <FileType type={file.type} path={file.path}/>
            <Favourited file={file} favourite={props.favourite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{file.acl.length > 1 ? file.acl.length + " collaborators" : file.acl[0].right}</td>
            <td>
                <MobileButtons file={file}/>
            </td>
        </tr>)
}

function Directory(props) {
    let file = props.file;
    return (
        <tr className="row-settings clickable-row"
            style={{cursor: "pointer"}}>
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" checked={props.isChecked}
                       type="checkbox" onChange={(e) => props.addOrRemoveFile(e.target.checked, file)}/><em
                className="bg-info"/></label></td>
            <FileType type={file.type} path={file.path}/>
            <Favourited file={file} favourite={props.favourite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{file.acl.length > 1 ? file.acl.length + " collaborators" : file.acl[0].right}</td>
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

function Favourited(props) {
    if (props.file.isStarred) {
        return (<td><a onClick={() => props.favourite(props.file)} className="ion-star"/></td>)
    }
    return (<td><a className="ion-ios-star-outline" onClick={() => props.favourite(props.file.path.uri)}/></td>);
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
                           onClick={Files.sendToAbacus()}> Send to Abacus 2.0</a></li>
                    <li><a className="btn btn-default ripple btn-block ion-share"
                           onClick={Files.shareFile(file.path.name, 'file')}> Share file</a></li>
                    <li><a
                        className="btn btn-default ripple btn-block ion-ios-download"> Download file</a></li>
                    <li><a className="btn btn-default ripple ion-ios-photos"> Move file</a></li>
                    <li><a className="btn btn-default ripple ion-ios-compose"
                           onClick={Files.renameFile(file.path.name, 'file')}> Rename file</a></li>
                    <li><a className="btn btn-danger ripple ion-ios-trash"
                           onClick={Files.showFileDeletionPrompt(file.path)}> Delete file</a></li>
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
