import React from "react";
import {Cloud} from "../../authentication/SDUCloudObject";
import {getParentPath} from "../UtilityFunctions";
import SectionContainerCard from "./SectionContainerCard";
import {BallPulseLoading} from "./LoadingIcon";
import {SensitivityLevel} from "../DefaultObjects"
import {ListGroup, ListGroupItem, Jumbotron} from "react-bootstrap";

class FileInfo extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            filePath: props.params.splat,
            file: null,
            loading: false,
        };
        this.getFile = this.getFile.bind(this);
    }

    componentWillMount() {
        this.getFile();
    }

    getFile() {
        this.setState({
            loading: true,
        });
        let path = getParentPath(this.state.filePath);
        Cloud.get(`files?path=${path}`).then(files => {
            let file = files.find(file => file.path.path === this.state.filePath);
            this.setState(() => ({
                file: file,
                loading: false,
            }));
            console.log(file);
        });
    }

    render() {
        return (
            <SectionContainerCard>
                <BallPulseLoading loading={this.state.loading}/>
                <FileHeader file={this.state.file}/>
                <FileView file={this.state.file}/>
                <FileSharing file={this.state.file}/>
            </SectionContainerCard>
        );
    }
}

function FileHeader(props) {
    if (!props.file) {
        return null;
    }
    let type = props.file.type === "DIRECTORY" ? "Directory" : "File";
    return (
        <Jumbotron>
            <h1>{props.file.path.path}</h1>
            <h3>
                <small>{type}</small>
            </h3>
        </Jumbotron>)
}

function FileView(props) {
    if (!props.file) {
        return null;
    }
    const sharedWithCount = props.file.acl.filter(acl => acl.entity.displayName !== Cloud.username).length;
    return (
        <div className="container-fluid">
            <ListGroup className="col-sm-4">
                <ListGroupItem>Created at: {new Date(props.file.createdAt).toLocaleString()}</ListGroupItem>
                <ListGroupItem>Modified at: {new Date(props.file.createdAt).toLocaleString()}</ListGroupItem>
                <ListGroupItem>Favorite file: {props.file.favorited ? <em className="ion-star"/> :
                    <em className="ion-ios-star-outline"/>}</ListGroupItem>
            </ListGroup>
            <ListGroup className="col-sm-4">
                <ListGroupItem>Sensitivity: {SensitivityLevel[props.file.sensitivityLevel]}</ListGroupItem>
                <ListGroupItem>Size: {props.file.size}</ListGroupItem>
                <ListGroupItem>Shared with {sharedWithCount} {sharedWithCount === 1 ? "person" : "people"}.</ListGroupItem>
            </ListGroup>
        </div>
    );
}

function FileSharing(props) {
    if (!props.file) { return null; }
    const sharedWith = props.file.acl.filter(acl => acl.entity.displayName !== Cloud.username);
    if (!sharedWith.length) {
        return (<h3 className="text-center"><small>This file has not been shared with anyone.</small></h3>);
    }
    return (null);
}

export default FileInfo;