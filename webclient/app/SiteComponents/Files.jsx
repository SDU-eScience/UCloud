import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Cloud} from "../../authentication/SDUCloudObject";

class Files extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            files: [],
            loading: false,
            selectedFile: null,
            currentPage: 0,
            filesPerPage: 10,
            masterCheckbox: false,
        }
    }

    getFiles() {
        this.setState({
            favouriteLoading: true,
        });
        Cloud.get(`/files?path=/home/${Cloud.username}/`).then(favourites => {
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
                            <FilesTable files={this.state.files} loading={this.state.loading}/>
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
            <Shortcuts/>
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
                        <FilesList files={props.files}/>
                    </table>
                </div>
            </div>
        </div>)
}

function Shortcuts(props) {
    return (
        <div className="center-block text-center hidden-lg">
            <router-link class="btn btn-link btn-lg" to="/retrieveFavourites"><i className="icon ion-star"/>
            </router-link>
            <a className="btn btn-link btn-lg" href="#/"><i className="icon ion-ios-home"/></a>
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
    let files = props.files.slice(); // If they are readonly, is this necessary?
    let filesList = files.map(file =>
        <tr className="row-settings clickable-row"
            style={{cursor: file.type === "DIRECTORY" ? "pointer" : ""}} click="openFile(file)">
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" value="file" click="prevent"
                       type="checkbox"/><em
                className="bg-info"/></label></td>
            <FileType file={file}/>
            <Favourited file={file}/>
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
            <a className="ion-android-folder"/> {props.file.path.name}
        </td>);
}

function Favourited(props) {
    if (props.file.isStarred) {
        return (<td><a click="favourite(file)" className="ion-star"/></td>)
    }
    return (<td><a className="ion-star" click="favourite(file.path.uri, $event)"/></td>);
}

function MobileButtons() {
    return (
        <span className="hidden-lg">
                      <div className="pull-right dropdown">
                          <button type="button" data-toggle="dropdown"
                                  className="btn btn-flat btn-flat-icon"
                                  aria-expanded="false"><em className="ion-android-more-vertical"/></button>
                          <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                              <li><a className="btn btn-info ripple btn-block"
                                     click="sendToAbacus()"> Send to Abacus 2.0</a></li>
                              <li><a className="btn btn-default ripple btn-block ion-share"
                                     click="shareFile(file.path.name, 'file')"> Share file</a></li>
                              <li><a
                                  className="btn btn-default ripple btn-block ion-ios-download"> Download file</a></li>
                              <li><a className="btn btn-default ripple ion-ios-photos"> Move file</a></li>
                              <li><a className="btn btn-default ripple ion-ios-compose"
                                     click="renameFile(file.path.name, 'file')"> Rename file</a></li>
                              <li><a className="btn btn-danger ripple ion-ios-trash"
                                     click="showFileDeletionPrompt(file.path)"> Delete file</a></li>
                          </ul>
                      </div>
                  </span>)
}

export default Files;