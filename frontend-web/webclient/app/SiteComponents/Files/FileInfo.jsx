import React from "react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { getParentPath, updateSharingOfFile, shareFile, favorite, fileSizeToString } from "../../UtilityFunctions";
import { fetchFiles, updatePath, updateFiles, setLoading } from "../../Actions/Files";
import SectionContainerCard from "../SectionContainerCard";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { SensitivityLevel, RightsNameMap } from "../../DefaultObjects"
import { ListGroup, ListGroupItem, Jumbotron, Button, ButtonGroup } from "react-bootstrap";
import swal from "sweetalert2";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { updatePageTitle } from "../../Actions/Status";


const FileInfo = ({ dispatch, files, loading, ...props }) => {
    dispatch(updatePageTitle("File Info"));
    let file;
    const path = props.match.params[0];
    const parentPath = getParentPath(path);
    if (parentPath === props.filesPath) {
        const filePath = path.endsWith("/") ? path.slice(0, path.length - 1) : path;
        file = files.find(file => file.path.path === filePath);
    } else {
        dispatch(setLoading(true));
        dispatch(fetchFiles(parentPath));
        dispatch(updatePath(parentPath));
    }

    if (!file) { return (<BallPulseLoading loading={true} />) }

    const retrieveFilesCallback = () => {
        dispatch(setLoading(true));
        dispatch(fetchFiles(props.filesPath));
    }

    let button = (<div />);
    if (file) {
        const currentRights = file.acl.find(acl => acl.entity.displayName === Cloud.username);
        if (currentRights) {
            if (currentRights.right === "OWN") {
                button = (
                    <Button
                        onClick={() => shareFile(file.path, Cloud, retrieveFilesCallback)}
                        className="btn btn-primary"
                    >
                        Share file
                    </Button>);
            }
        }
    }
    return (
        <SectionContainerCard>
            <FileHeader file={file} />
            <FileView file={file} favorite={() => dispatch(updateFiles(favorite(files, file.path.path, Cloud)))} />
            <FileSharing
                file={file}
                revokeRights={(acl) => revokeRights(file, acl, () => dispatch(updateFiles(files)))}
                updateSharing={(acl) => updateSharingOfFile(file.path, acl.entity.displayName, acl.right, Cloud, retrieveFilesCallback)}
            />
            <BallPulseLoading loading={loading} />
            {button}
        </SectionContainerCard>
    );
}

const revokeRights = (file, acl, callback) => {
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
            removeAcl(file, acl);
            swal("Success!", `Rights have been revoked`, "success").then(() => callback ? callback() : null);
        }).catch((failure) => {
            swal("Error", `An error occurred revoking the rights. Please try again later`, "error");
        });
    });
}

const removeAcl = (file, toRemoveAcl) => {
    let index = file.acl.findIndex(acl => acl.entity.name === toRemoveAcl.entity.name);
    file.acl = file.acl.slice(0, index).concat(file.acl.slice(index + 1));
}

const FileHeader = ({ file }) => {
    if (!file) {
        return null;
    }
    let type = file.type === "DIRECTORY" ? "Directory" : "File";
    return (
        <Jumbotron>
            <h3>{file.path.path}</h3>
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

const FileSharing = ({ file, updateSharing, revokeRights }) => {
    if (!file) {
        return null;
    }
    const currentRights = file.acl.find(acl => acl.entity.displayName === Cloud.username);
    if (!currentRights || currentRights.right !== "OWN") {
        return null;
    }
    const sharedWith = file.acl.filter(acl => acl.entity.displayName !== Cloud.username);
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
                            <Button onClick={() => updateSharing(acl)}
                                className="btn btn-primary">Change</Button>
                            <Button onClick={() => revokeRights(acl)}
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
    favoriteCount: PropTypes.number.isRequired,
    aclCount: PropTypes.number.isRequired
}

const mapStateToProps = (state) => {
    const { loading, files, path } = state.files;
    let aclCount = 0;
    files.forEach((file) => {
        aclCount += file.acl.length;
    });
    return {
        loading,
        files,
        filesPath: path,
        aclCount,
        favoriteCount: files.filter(file => file.favorited).length // Hack to ensure rerender
    }
}

export default connect(mapStateToProps)(FileInfo);