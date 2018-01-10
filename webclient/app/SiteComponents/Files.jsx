import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from 'react-router'
import {Button} from 'react-bootstrap';


class Files extends React.Component {
    constructor(props) {
        super(props);
        let currentPath = (!props.routeParams.splat) ? `/home/${Cloud.username}` : props.routeParams.splat;
        this.state = {
            files: [],
            loading: false,
            selectedFile: null,
            currentPage: 0,
            filesPerPage: 10,
            masterCheckbox: false,
            currentPath: currentPath,
        };
        this.getFiles = this.getFiles.bind(this);
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

    getFiles() {
        this.setState({
            favouriteLoading: true,
        });
        Cloud.get("files?path=" + this.state.currentPath).then(favourites => {
            favourites.sort((a, b) => {
                if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
                    return -1;
                else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
                    return 1;
                else {
                    return a.path.name.localeCompare(b.path.name);
                }
            });
            this.setState(() => ({
                files: favourites,
                loading: false,
            }));
            console.log(this.state.files.length);
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
                        <Breadcrumbs selectedFile={this.state.selectedFile}/>
                        <LoadingIcon loading={this.state.loading}/>
                        <div className="card">
                            <FilesTable files={this.state.files} loading={this.state.loading}
                                        getFavourites={this.getFavourites} favourite={() => this.favourite}
                                        prevent={this.prevent}/>
                        </div>
                    </div>
                </div>
            </section>)
    }
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
                                       value="all"
                                       type="checkbox"/><em
                                className="bg-info"/></label></th>
                            <th><span className="text-left">Filename</span></th>
                            <th><span><em className="ion-star"/></span></th>
                            <th><span className="text-left">Last Modified</span></th>
                            <th><span className="text-left">File Owner</span></th>
                        </tr>
                        </thead>
                        <FilesList files={props.files} favourite={props.favourite} prevent={props.prevent}/>
                    </table>
                </div>
            </div>
        </div>)
}

function Shortcuts(props) {
    return (
        <div className="center-block text-center hidden-lg">
            <Button onClick={props.getFavourites}><i className="icon ion-ios-home"/></Button>
        </div>)
}

function Breadcrumbs(props) {
    if (!props.selectedFile) {
        return null;
    }
    let paths = props.selectedFile.path.split("/");
    let breadcrumbs = paths.map(path =>
        <li className="breadcrumb-item">
            <router-link to="{ path: breadcrumb.second }" append>{breadcrumb.first}</router-link>
        </li>
    );
    return (
        <ol className="breadcrumb">
            {breadcrumbs}
        </ol>)
}

function FilesList(props) {
    let i = 0;
    let directories = props.files.filter(it => it.type === "DIRECTORY");
    let files = props.files.filter(it => it.type !== "DIRECTORY");
    let filesList = directories.map(file =>
        <tr key={i++} className="row-settings clickable-row"
            style={{cursor: "pointer"}}>
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" value="file"
                       type="checkbox"/><em
                className="bg-info"/></label></td>
            <FileType file={file}/>
            <Favourited file={file} favourite={props.favourite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{file.acl.length > 1 ? file.acl.length + " collaborators" : file.acl[0].right}</td>
            <td>
                <MobileButtons/>
            </td>
        </tr>
    );
    return (
        <tbody>
        {filesList}
        </tbody>
    )
}

function FileType(props) {
    if (props.file.type === "FILE")
        return (
            <td>
                <a className="ion-android-document"/> {props.file.path.name}
            </td>);
    return (
        <td>
            <Link to={`/files/${props.file.path.path}`}>
                <a className="ion-android-folder"/> {props.file.path.name}
            </Link>
        </td>);
}

function Favourited(props) {
    if (props.file.isStarred) {
        return (<td><a onClick={() => props.favourite(props.file)} className="ion-star"/></td>)
    }
    return (<td><a className="ion-star" onClick={() => props.favourite(props.file.path.uri)}/></td>);
}

function MobileButtons() {
    return null;
    /*
    return (
        <span className="hidden-lg">
                      <div className="pull-right dropdown">
                          <button type="button" data-toggle="dropdown"
                                  className="btn btn-flat btn-flat-icon"
                                  aria-expanded="false"><em className="ion-android-more-vertical"/></button>
                          <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                              <li><a className="btn btn-info ripple btn-block"
                                     onClick={sendToAbacus()}> Send to Abacus 2.0</a></li>
                              <li><a className="btn btn-default ripple btn-block ion-share"
                                     onClick="shareFile(file.path.name, 'file')"> Share file</a></li>
                              <li><a
                                  className="btn btn-default ripple btn-block ion-ios-download"> Download file</a></li>
                              <li><a className="btn btn-default ripple ion-ios-photos"> Move file</a></li>
                              <li><a className="btn btn-default ripple ion-ios-compose"
                                     onClick="renameFile(file.path.name, 'file')"> Rename file</a></li>
                              <li><a className="btn btn-danger ripple ion-ios-trash"
                                     onClick="showFileDeletionPrompt(file.path)"> Delete file</a></li>
                          </ul>
                      </div>
                  </span>)*/
}

export default Files;
