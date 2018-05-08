import React from "react";
import {Jumbotron, Table, ListGroupItem, ListGroup, ProgressBar} from "react-bootstrap";
import { Container } from "semantic-ui-react";
import {BallPulseLoading} from "../LoadingIcon/LoadingIcon";
import {Cloud} from "../../../authentication/SDUCloudObject";
import PromiseKeeper from "../../PromiseKeeper";
import { updatePageTitle } from "../../Actions/Status";
import { connect } from "react-redux";

class ZenodoInfo extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: false,
            publicationID: window.decodeURIComponent(props.match.params.jobID),
            publication: null,
        };
        this.props.dispatch(updatePageTitle("Zenodo Publication Info"));
    }

    componentWillMount() {
        this.setState(() => ({loading: true}));
        this.state.promises.makeCancelable(Cloud.get(`/zenodo/publications/${this.state.publicationID}`)).promise.then((res) => {
            this.setState(() => ({
                publication: res.response.publication,
                uploads: res.response.uploads,
                loading: false,
            }));
        });
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    render() {
        if (this.state.loading) {
            return (<Container><BallPulseLoading loading={this.state.loading}/></Container>)
        }
        return (
            <Container>
                <ZenodoPublishingBody publication={this.state.publication} uploads={this.state.uploads}/>
            </Container>
        );
    }
}

const ZenodoPublishingBody = ({ publication, uploads, isActive }) => {
    let progressBarValue = Math.ceil((uploads.filter(uploads => uploads.hasBeenTransmitted).length / uploads.length) * 100);
    return (
        <div>
            <Jumbotron>
                <h3>Publication name: {publication.name}</h3>
            </Jumbotron>
            <ListGroup>
                <ListGroupItem>
                    <span>
                        <span>Started:</span>
                        <span className="pull-right">{new Date(publication.createdAt).toLocaleString()}</span>
                    </span>
                </ListGroupItem>
                <ListGroupItem>
                    <span>Last update:</span>
                    <span className="pull-right">{new Date(publication.modifiedAt).toLocaleString()}</span>
                </ListGroupItem>
            </ListGroup>
            <ProgressBar active={publication.status === "UPLOADING"} bsStyle={getStatusBarColor(publication.status)} striped={isActive}
                         label={`${progressBarValue}%`}
                         now={progressBarValue}/>
            <FilesList files={uploads}/>
        </div>)
};

const FilesList = ({files}) =>
    files === null ? null :
        (<Table>
            <thead>
            <tr>
                <th>File name</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody>
            {files.map((file, index) =>
                <tr key={index}>
                    <td>{file.dataObject}</td>
                    <td>{file.hasBeenTransmitted ? "✓" : "…"}</td>
                </tr>
            )}
            </tbody>
        </Table>);

const getStatusBarColor = (status) => {
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
};

export default connect()(ZenodoInfo);