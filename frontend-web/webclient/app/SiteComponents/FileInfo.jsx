import React from "react";
import { Cloud } from "../../authentication/SDUCloudObject";
import { getParentPath, updateSharingOfFile, shareFile, favorite, fileSizeToString } from "../UtilityFunctions";
import {fetchFiles, updatePath} from "../Actions/Files";
import SectionContainerCard from "./SectionContainerCard";
import { BallPulseLoading } from "./LoadingIcon/LoadingIcon";
import { SensitivityLevel, RightsNameMap } from "../DefaultObjects"
import { ListGroup, ListGroupItem, Jumbotron, Button, ButtonGroup } from "react-bootstrap";
import swal from "sweetalert2";
import pubsub from "pubsub-js";
import PropTypes from "prop-types";
import PromiseKeeper from "../PromiseKeeper";
import { connect } from "react-redux";

class FileInfo extends React.Component {
    constructor(props) {
        super(props);
        this.revokeRights = this.revokeRights.bind(this);
        this.removeAcl = this.removeAcl.bind(this);
        this.favoriteFile = this.favoriteFile.bind(this);
        pubsub.publish("setPageTitle", "File Info");
    }

    revokeRights(file, acl) {
        swal({
            title: "Revoke access",
            text: `Revoke ${RightsNameMap[acl.right]} access for ${acl.entity.displayName}.`,
            showCancelButton: true,
            showCloseButton: true,
        }).then(input => {
            if (input.dismiss) {
                return;
            }
            const body = {
                onFile: file.path.path,
                entity: acl.entity.displayName,
                type: "revoke",
            };

            Cloud.delete("/acl", body).then(res => {
                this.removeAcl(acl);
                swal("Success!", `Rights have been revoked`, "success");
            }).catch((failure) => {
                swal("Error", `An error occurred revoking the rights. Please try again later`, "error");
            });
        });
    }

    removeAcl(toRemoveAcl) {
        const file = Object.assign({}, this.state.file);
        let index = file.acl.findIndex(acl => acl.name === toRemoveAcl.entity.name);
        file.acl.splice(index, 1);
        // TODO: rewrite as functional.
        // TODO: E.g. file. file.acl = file.acl.slice(0, index).concat(file.acl.slice(index + 1))
        this.setState(() => ({
            file: file,
        }));
    }

    favoriteFile() {
        this.setState(() => ({
            file: favorite([this.state.file], this.state.file.path.path, Cloud)[0],
        }));
    }

    render() {
        let file;
        const path = this.props.match.params[0];
        console.log("path from props", path);
        const parentPath = getParentPath(path);
        if (parentPath === this.props.filesPath) {
            const filePath = path.endsWith("/") ? path.slice(0, path.length - 1) : path;
            file = this.props.files.find(file => file.path.path === filePath);
        } else {
            const { dispatch } = this.props;
            dispatch(fetchFiles(parentPath));
            dispatch(updatePath(parentPath));
        }

        if (!file) { return (<BallPulseLoading loading={true} />)}

        let button = (<div />);
        if (file) {
            const currentRights = file.acl.find(acl => acl.entity.displayName === Cloud.username);
            if (currentRights) {
                if (currentRights.right === "OWN") {
                    button = (
                        <Button onClick={() => shareFile(file.path, Cloud)} className="btn btn-primary">Share
                            file</Button>);
                }
            }
        }
        return (
            <SectionContainerCard>
                <FileHeader file={file} />
                <FileView file={file} favorite={this.favoriteFile} />
                <FileSharing file={file} revokeRights={this.revokeRights} />
                {button}
            </SectionContainerCard>
        );
    }
}

const FileHeader = (props) => {
    if (!props.file) {
        return null;
    }
    let type = props.file.type === "DIRECTORY" ? "Directory" : "File";
    return (
        <Jumbotron>
            <h3>{props.file.path.path}</h3>
            <h5>
                <small>{type}</small>
            </h5>
        </Jumbotron>)
}

const FileView = (props) => {
    if (!props.file) {
        return null;
    }
    const sharedWithCount = props.file.acl.filter(acl => acl.entity.displayName !== Cloud.username).length;
    const currentRights = props.file.acl.find(acl => acl.entity.displayName === Cloud.username);
    if (!currentRights) {
        return (
            <h3 className="text-center">
                <small>You do not have rights for this file.</small>
            </h3>);
    }
    return (
        <div className="container-fluid">
            <ListGroup className="col-sm-4">
                <ListGroupItem>Created at: {new Date(props.file.createdAt).toLocaleString()}</ListGroupItem>
                <ListGroupItem>Modified at: {new Date(props.file.createdAt).toLocaleString()}</ListGroupItem>
                <ListGroupItem>Favorite file: {props.file.favorited ?
                    <em onClick={() => props.favorite()} className="ion-star" /> :
                    <em onClick={() => props.favorite()} className="ion-ios-star-outline" />}</ListGroupItem>
            </ListGroup>
            <ListGroup className="col-sm-4">
                <ListGroupItem>Sensitivity: {SensitivityLevel[props.file.sensitivityLevel]}</ListGroupItem>
                <ListGroupItem>Size: {fileSizeToString(props.file.size)}</ListGroupItem>
                <ListGroupItem>Shared
                    with {sharedWithCount} {sharedWithCount === 1 ? "person" : "people"}.</ListGroupItem>
            </ListGroup>
            <ListGroup className="col-sm-4">
                <ListGroupItem>Type: {currentRights.entity.type}</ListGroupItem>
                <ListGroupItem>Name: {currentRights.entity.displayName}</ListGroupItem>
                <ListGroupItem>Rights Level: {RightsNameMap[currentRights.right]}</ListGroupItem>
            </ListGroup>
        </div>
    );
}

const FileSharing = (props) => {
    if (!props.file) {
        return null;
    }
    const currentRights = props.file.acl.find(acl => acl.entity.displayName === Cloud.username);
    if (!currentRights || currentRights.right !== "OWN") {
        return null;
    }
    const sharedWith = props.file.acl.filter(acl => acl.entity.displayName !== Cloud.username);
    if (!sharedWith.length) {
        return (
            <h3 className="text-center">
                <small>This file has not been shared with anyone.</small>
            </h3>);
    }
    return (
        <div className="container-fluid">
            <ListGroup className="col-sm-4 col-sm-offset-4">
                {sharedWith.map((acl, index) =>
                    (<ListGroupItem key={index}>
                        <span
                            className="text-left"><b>{acl.entity.displayName}</b> has <b>{RightsNameMap[acl.right]}</b> access.</span>
                        <ButtonGroup bsSize="xsmall" className="pull-right">
                            <Button onClick={() => updateSharingOfFile(props.file.path, acl.entity.displayName, acl.right, Cloud)}
                                className="btn btn-primary">Change</Button>
                            <Button onClick={() => props.revokeRights(props.file, acl)}
                                className="btn btn-danger">Revoke</Button>
                        </ButtonGroup>
                    </ListGroupItem>))}
            </ListGroup>
        </div>
    );
}

FileInfo.propTypes = {
    loading: PropTypes.bool.isRequired,
    files: PropTypes.array.isRequired,
    filesPath: PropTypes.string.isRequired,
}

const mapStateToProps = (state) => {
    const { loading, files, path } = state.files;
    return {
        loading,
        files,
        filesPath: path,
    }
}

export default connect(mapStateToProps)(FileInfo);