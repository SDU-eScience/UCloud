import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from 'react-router';
import {Button} from 'react-bootstrap';

class Files extends React.Component {
    constructor(props) {
        super(props);
        let currentPath = (!props.routeParams.splat) ? `home/${Cloud.username}` : props.routeParams.splat;
        this.state = {
            files: [],
            loading: false,
            currentPage: 0,
            filesPerPage: 10,
            masterCheckbox: false,
            currentPath: currentPath,
            selectedFiles: [],
        };
        this.getFiles = this.getFiles.bind(this);
        this.addOrRemoveFile = this.addOrRemoveFile.bind(this);
        this.selectOrDeselectAllFiles = this.selectOrDeselectAllFiles.bind(this);
    }

    selectOrDeselectAllFiles(event) {
        if (event.target.checked) {
            this.setState(() => ({
                selectedFiles: this.state.files,
             }));
        } else {
            this.setState(() => ({
                selectedFiles: [],
            }))
        }
    }

    static sendToAbacus() {
        console.log("Send to Abacus TODO!")
    }

    static shareFile() {
        console.log("Share file")
    }

    static uploadFile() {
        console.log("Upload file TODO!")
    }
    static getTitle(name) {
        console.log("GET TITLE TODO!")
    }

    static renameFile() {
        console.log("TODO");
    }

    static showFileDeletionPrompt() {

    }

    getFavourites() {
        // TODO
        console.log("GET FAVOURITES TODO")
    }

    favourite(file) {
        // TODO
        console.log("FAVOURITE TODO")
    }

    prevent() {
        // TODO
        console.log("PREVENT TODO")
    }

    static getCurrentRights(files) {
        const rightsMap = {
            'NONE': 1,
            'READ': 2,
            'READ_WRITE': 3,
            'OWN': 4
        }
    
        let lowestPrivilegeOptions = rightsMap['OWN'];
        files.forEach((it) => {
            it.acl.forEach((acl) => {
            lowestPrivilegeOptions = Math.min(rightsMap[acl.right], lowestPrivilegeOptions);
            });
        });
        return {
            rightsName: Object.keys(rightsMap)[lowestPrivilegeOptions - 1],
            rightsLevel: lowestPrivilegeOptions
        }
    }

    addOrRemoveFile(event, newFile) {
        let size = this.state.selectedFiles.length;
        let currentFiles = this.state.selectedFiles.slice().filter(file => file.path.uri !== newFile.path.uri);
        if (event.target.checked && currentFiles.length === size) {
            currentFiles.push(newFile);
        }
        this.setState(() => ({
            selectedFiles: currentFiles,
        }));
    }

    static sortFiles(files) {
        files.sort((a, b) => {
            if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
                return -1;
            else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
                return 1;
            else {
                return a.path.name.localeCompare(b.path.name);
            }
        });
        return files;
    }

    getFiles() {
        this.setState({
            loading: true,
        });
        Cloud.get("files?path=/" + this.state.currentPath).then(favourites => {
            this.setState(() => ({
                files: Files.sortFiles(favourites),
                loading: false,
            }));
        });
    }

    componentWillMount() {
        this.getFiles();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <LoadingIcon loading={this.state.loading}/>
                        <Breadcrumbs currentPath={this.state.currentPath}/>
                        <div className="card">
                            <FilesTable files={this.state.files} loading={this.state.loading} selectedFiles={this.state.selectedFiles}
                                        getFavourites={() => this.getFavourites} favourite={() => this.favourite}
                                        prevent={this.prevent} selectOrDeselectAllFiles={this.selectOrDeselectAllFiles}/>
                        </div>
                    </div>
                    <ContextBar selectedFiles={this.state.selectedFiles} getFavourites={() => this.getFavourites()}/>
                </div>
            </section>)
    }
}

function ContextBar(props) {
    return (
        <div className="col-lg-2 visible-lg">
            <div>
                <div className="center-block text-center">
                    <Button className="btn btn-link btn-lg" onClick={() => props.getFavourites()}><i
                        className="icon ion-star"/></Button>
                    <Link className="btn btn-link btn-lg" to={`files?path=/home/${Cloud.username}`}><i
                        className="icon ion-ios-home"/></Link>
                </div>
                <hr/>
                <button className="btn btn-primary ripple btn-block ion-android-upload" onClick={Files.uploadFile}> Upload
                    Files
                </button>
                <br/>
                <button className="btn btn-default ripple btn-block ion-folder" onClick={Files.createFolder}>
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
    if (!props.selectedFiles.length) { return null; }
    let rights = Files.getCurrentRights(props.selectedFiles);
    const fileText = props.selectedFiles.length > 1 ? `${props.selectedFiles.length} files selected.` : props.selectedFiles[0].path.name
    const rightsLevel = <RightsLevel rights={rights} fileText={fileText}/>
    return (
        <div>
            {rightsLevel}
            <p>
                <button className="btn btn-info rippple btn-block"
                        onClick={Files.sendToAbacus()}> Send to Abacus 2.0
                </button>
            </p>
            <p>
                <button type="button" className="btn btn-default ripple btn-block"
                        title={Files.getTitle('share')}
                        onClick={Files.shareFile(props.selectedFiles[0].path.name, 'folder')}> <span className="ion-share"/> Share selected
                    files
                </button>
            </p>
            <p>
                <button className="btn btn-default ripple btn-block"
                        title={Files.getTitle("download")}>
                        <span className="ion-ios-download"/>
                    Download selected files
                </button>
            </p>
            <p>
                <button type="button" className="btn btn-default btn-block ripple">
                <span className="ion-android-star"/>
                    Favourite selected files
                </button>
            </p>
            <p>
                <button className="btn btn-default btn-block ripple"
                        title={Files.getTitle('move')}>
                        <span className="ion-ios-photos" />
                    Move folder
                </button>
            </p>
            <p>
                <button type="button" className="btn btn-default btn-block ripple"
                        onClick={Files.renameFile(props.selectedFiles[0].path.name, 'folder')}
                        title={Files.getTitle("rename")}
                        disabled={rights.rightsLevel < 3 || props.selectedFiles.length !== 1}>
                        <span className="ion-ios-compose" />
                    Rename file
                </button>
            </p>
            <p>
                <button className="btn btn-danger btn-block ripple"
                        title={Files.getTitle('delete')}
                        disabled={rights.rightsLevel < 3}
                        onClick={Files.showFileDeletionPrompt(props.selectedFiles[0].path)}>
                        <em className="ion-ios-trash"/>
                    Delete selected files
                </button>
            </p>
        </div>
    )
}


function FilesTable(props) {
    let noFiles = props.files.length || props.loading ? '' : (<div>
        <h3 className="text-center">
            <small>There are no files in current folder</small>
        </h3>
    </div>);
    return (
        <div className="card-body">
            <Shortcuts getFavourites={props.getFavourites}/>
            {noFiles}
            <div className="card">
                <div className="card-body">
                    <table className="table-datatable table table-hover mv-lg">
                        <thead>
                        <tr>
                            <th className="select-cell disabled"><label className="mda-checkbox">
                                <input name="select" className="select-box"
                                       defaultChecked={false}
                                       type="checkbox" onChange={e => props.selectOrDeselectAllFiles(e)}/><em
                                className="bg-info"/></label></th>
                            <th><span className="text-left">Filename</span></th>
                            <th><span><em className="ion-star"/></span></th>
                            <th><span className="text-left">Last Modified</span></th>
                            <th><span className="text-left">File Owner</span></th>
                        </tr>
                        </thead>
                        <FilesList files={props.files} favourite={props.favourite} selectedFiles={props.selectedFiles} prevent={props.prevent}/>
                    </table>
                </div>
            </div>
        </div>)
}

function Shortcuts(props) {
    return (
        <div className="center-block text-center hidden-lg">
            <Button onClick={() => props.getFavourites()}><i className="icon ion-ios-home"/></Button>
        </div>)
}

function RightsLevel(props) {
    return (
        <h3>
            {`Rights level: ${props.rights.rightsLevel}`}<br/>
            {props.fileText}
        </h3>);
}

function FilesList(props) {
    let i = 0;
    let directories = props.files.filter(it => it.type === "DIRECTORY");
    let files = props.files.filter(it => it.type !== "DIRECTORY");
    let directoryList = directories.map(file =>
        <Directory key={i++} file={file} isChecked={ -1 !== props.selectedFiles.findIndex(selectedFile => selectedFile.path.uri === file.path.uri)}/>
    );
    let filesList = files.map(file =>
        <File key={i++} file={file} isChecked={ -1 !== props.selectedFiles.findIndex(selectedFile => selectedFile.path.uri === file.path.uri)}/>
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
            <input name="select" className="select-box" checked={props.isChecked }
                type="checkbox"/><em
            className="bg-info"/></label></td>
        <FileType file={file}/>
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
    return(
    <tr className="row-settings clickable-row"
            style={{cursor: "pointer"}}>
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" defaultChecked={false} className="select-box" checked={props.isChecked}
                       type="checkbox" /><em
                className="bg-info"/></label></td>
            <FileType file={file}/>
            <Favourited file={file} favourite={props.favourite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{file.acl.length > 1 ? file.acl.length + " collaborators" : file.acl[0].right}</td>
            <td>
                <MobileButtons file={file}/>
            </td>
    </tr>)
}

function FileType(props) {
    if (props.file.type === "FILE")
        return (
            <td>
                <span className="ion-android-document"/> {props.file.path.name}
            </td>);
    return (
        <td>
            <Link to={`/files/${props.file.path.path}`}>
                <span className="ion-android-folder"/> {props.file.path.name}
            </Link>
        </td>);
}

function Favourited(props) {
    if (props.file.isStarred) {
        return (<td><a onClick={() => props.favourite(props.file)} className="ion-star"/></td>)
    }
    return (<td><a className="ion-star" onClick={() => props.favourite(props.file.path.uri)}/></td>);
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
    let paths = props.currentPath.split("/");
    let pathsMapping = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({actualPath: actualPath, local: paths[i],})
    }
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
