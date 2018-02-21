import React from "react";
import {Jumbotron, Table, ListGroupItem, ListGroup, ProgressBar} from "react-bootstrap";
import pubsub from "pubsub-js";
import SectionContainerCard from "../SectionContainerCard";
import {BallPulseLoading} from "../LoadingIcon";
import {Cloud} from "../../../authentication/SDUCloudObject";

class ZenodoInfo extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            publicationID: window.decodeURIComponent(props.match.params.jobID),
            publication: null,
        };
    }

    componentWillMount() {
        pubsub.publish('setPageTitle', "Zenodo Publication Info");
        this.setState(() => ({
            loading: true,
        }));
        Cloud.get("/../mock-api/mock_zenodo_publications.json").then((publications) => {
            this.setState(() => ({
                publication: publications.inProgress.find(publications => publications.id === this.state.publicationID),
                loading: false,
            }));
        });
    }

    render() {
        if (this.state.loading) {
            return (<SectionContainerCard><BallPulseLoading loading={this.state.loading}/></SectionContainerCard>)
        }
        return (
            <SectionContainerCard>
                <ZenodoPublishingBody publication={this.state.publication}/>
            </SectionContainerCard>
        );
    }
}

function ZenodoPublishingBody(props) {
    const publication = props.publication;
    // FIXME calculate dynamically
    let progressBarValue = 0;
    if (publication.status === "COMPLETE") {
        progressBarValue = 100;
    } else if (publication.status === "UPLOADING") {
        progressBarValue = 40;
    } else {
        progressBarValue = 60;
    } // FIXME
    const isActive = publication.status === "UPLOADING";
    let style = getStatusBarColor(publication.status);
    return (
        <div>
            <Jumbotron>
                <h3>Publication ID: {props.publication.id}</h3>
            </Jumbotron>
            <ListGroup>
                <ListGroupItem>
                    <span>
                        <span>Started:</span>
                        <span className="pull-right">{new Date(props.publication.createdAt).toLocaleString()}</span>
                    </span>
                </ListGroupItem>
                <ListGroupItem>
                    <span>Last update:</span>
                    <span className="pull-right">{new Date(props.publication.modifiedAt).toLocaleString()}</span>
                </ListGroupItem>
            </ListGroup>
            <ProgressBar active={isActive} bsStyle={style} striped={isActive}
                         label={`${progressBarValue}%`}
                         now={progressBarValue}/>
            <FilesList files={null}/>
        </div>)
}

function FilesList(props) {
    if (props.files === null) {
        return null
    }
    const filesList = props.files.map((file) =>
        <tr>
            <td>{file.name}</td>
            <td>{file.status}</td>
        </tr>
    );
    return (
        <Table>
            <thead>
            <tr>
                <th>File name</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody>
            {filesList}
            </tbody>
        </Table>
    );
}

function getStatusBarColor(status) {
    switch (status) {
        case "UPLOADING": {
            return "info";
        }
        case "COMPLETE": {
            return "success";
        }
        case "FAILURE": {
            return "danger";
        }
    }
}

export default ZenodoInfo;