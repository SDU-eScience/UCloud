import React from "react";
import { Container, Header, List, Table, Progress } from "semantic-ui-react";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import { Cloud } from "../../../authentication/SDUCloudObject";
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
        this.setState(() => ({ loading: true }));
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
            return (<Container><DefaultLoading loading={this.state.loading} /></Container>)
        }
        return (
            <Container className="container-margin">
                <ZenodoPublishingBody publication={this.state.publication} uploads={this.state.uploads} />
            </Container>
        );
    }
}

const ZenodoPublishingBody = ({ publication, uploads, isActive }) => {
    let progressBarValue = Math.ceil((uploads.filter(uploads => uploads.hasBeenTransmitted).length / uploads.length) * 100);
    return (
        <div>
            <Header as="h2">
                Publication name: {publication.name}
            </Header>
            <List>
                <List.Item>
                    Started:
                    <List.Content floated="right">
                        {new Date(publication.createdAt).toLocaleString()}
                    </List.Content>
                </List.Item>
                <List.Item>
                    Last update:
                    <List.Content floated="right">
                        {new Date(publication.modifiedAt).toLocaleString()}
                    </List.Content>
                </List.Item>
            </List>
            <Progress
                active={publication.status === "UPLOADING"}
                label={`${progressBarValue}%`}
                percent={progressBarValue} />
            <FilesList files={uploads} />
        </div>)
};

const FilesList = ({ files }) =>
    files === null ? null :
        (<Table>
            <Table.Header>
                <Table.Row>
                    <th>File name</th>
                    <th>Status</th>
                </Table.Row>
            </Table.Header>
            <Table.Body>
                {files.map((file, index) =>
                    <Table.Row key={index}>
                        <Table.Cell>{file.dataObject}</Table.Cell>
                        <Table.Cell>{file.hasBeenTransmitted ? "✓" : "…"}</Table.Cell>
                    </Table.Row>
                )}
            </Table.Body>
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