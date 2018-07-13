import * as React from "react";
import { Container, Header, List, Table, Progress } from "semantic-ui-react";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import { Cloud } from "Authentication/SDUCloudObject";
import PromiseKeeper from "../PromiseKeeper";
import { dateToString } from "../Utilities/DateUtilities";
import { ZenodoInfoProps, ZenodoInfoState } from ".";

class ZenodoInfo extends React.Component<ZenodoInfoProps, ZenodoInfoState> {
    constructor(props: ZenodoInfoProps) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: true,
            publicationID: decodeURIComponent(props.match.params.jobID),
            publication: null,
            intervalId: -1
        };
    }

    componentWillMount() {
        this.setState(() => ({ loading: true }));
        const intervalId = window.setInterval(this.reload, 1000);
        this.setState(() => ({ intervalId: intervalId }));
    }

    reload = () => {
        const { promises } = this.state;
        promises.makeCancelable(Cloud.get(`/zenodo/publications/${this.state.publicationID}`))
            .promise.then(({ response }) => {
                this.setState(() => ({
                    publication: response.publication,
                    uploads: response.uploads,
                    loading: false,
                }));
                if (response.publication.status === "COMPLETE") {
                    window.clearInterval(this.state.intervalId);
                }
            });
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
        window.clearInterval(this.state.intervalId);
    }

    render() {
        if (this.state.loading) {
            return (<Container><DefaultLoading loading={this.state.loading} /></Container>)
        } else {
            return (
                <Container className="container-margin">
                    <ZenodoPublishingBody publication={this.state.publication} uploads={this.state.uploads} />
                </Container>
            );
        }
    }
}

const ZenodoPublishingBody = ({ publication, uploads }) => {
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
                        {dateToString(publication.createdAt)}
                    </List.Content>
                </List.Item>
                <List.Item>
                    Last update:
                    <List.Content floated="right">
                        {dateToString(publication.modifiedAt)}
                    </List.Content>
                </List.Item>
            </List>
            <Progress
                color="green"
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

export default ZenodoInfo;