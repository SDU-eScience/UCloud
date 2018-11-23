import * as React from "react";
import { Container, Header, List, Table, Progress } from "semantic-ui-react";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { Cloud } from "Authentication/SDUCloudObject";
import PromiseKeeper from "PromiseKeeper";
import { dateToString } from "Utilities/DateUtilities";
import { ZenodoInfoProps, ZenodoInfoState, ZenodoPublicationStatus } from ".";
import { Error } from "ui-components";

const isTerminal = (status: ZenodoPublicationStatus): boolean =>
    status === ZenodoPublicationStatus.COMPLETE || status === ZenodoPublicationStatus.FAILURE;

class ZenodoInfo extends React.Component<ZenodoInfoProps, ZenodoInfoState> {
    constructor(props) {
        super(props);
        this.state = {
            error: undefined,
            promises: new PromiseKeeper(),
            loading: true,
            publicationID: decodeURIComponent(props.match.params.jobID),
            publication: undefined,
            intervalId: -1
        };
    }

    onErrorDismiss = (): void => {
        this.setState(() => ({ error: undefined }));
    }

    setErrorMessage = (jobID: string): void => {
        this.setState(() => ({
            error: `An error occured fetching publication ${jobID}`,
            loading: false
        }));
    }

    componentWillMount() {
        this.setState(() => ({ loading: true }));
        const intervalId = window.setInterval(this.reload, 2_000);
        this.setState(() => ({ intervalId: intervalId }));
    }

    reload = () => {
        const { promises } = this.state;
        promises.makeCancelable(Cloud.get(`/zenodo/publications/${encodeURI(this.state.publicationID)}`))
            .promise.then(({ response }) => {
                this.setState(() => ({
                    publication: response,
                    loading: false,
                }));
                if (isTerminal(response.status)) {
                    window.clearInterval(this.state.intervalId);
                }
            }).catch(_ => this.setErrorMessage(this.state.publicationID));
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
        window.clearInterval(this.state.intervalId);
    }

    render() {
        if (this.state.loading) {
            return (<Container as={DefaultLoading} loading={this.state.loading} />)
        } else {
            return (
                <Container className="container-margin">
                    {this.state.error ? <Error error={this.state.error} clearError={this.onErrorDismiss} /> : null}
                    <ZenodoPublishingBody publication={this.state.publication} />
                </Container>
            );
        }
    }
}

const ZenodoPublishingBody = ({ publication }) => {
    if (publication == null) return null;
    const { uploads } = publication;
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
                        <Table.Cell>{file.hasBeenTransmitted ? "Uploaded" : "Pending"}</Table.Cell>
                    </Table.Row>
                )}
            </Table.Body>
        </Table>);

export default ZenodoInfo;