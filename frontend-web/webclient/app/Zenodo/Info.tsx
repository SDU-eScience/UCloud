import * as React from "react";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import { Cloud } from "Authentication/SDUCloudObject";
import PromiseKeeper from "PromiseKeeper";
import { dateToString } from "Utilities/DateUtilities";
import { ZenodoInfoProps, ZenodoInfoState, ZenodoPublicationStatus } from ".";
import { Error, Progress, Box, Flex } from "ui-components";
import * as Table from "ui-components/Table";
import * as Heading from "ui-components/Heading";
import { replaceHomeFolder } from "Utilities/FileUtilities";
import { MainContainer } from "MainContainer/MainContainer";

const isTerminal = (status: ZenodoPublicationStatus): boolean =>
    status === ZenodoPublicationStatus.COMPLETE || status === ZenodoPublicationStatus.FAILURE;

class ZenodoInfo extends React.Component<ZenodoInfoProps, ZenodoInfoState> {
    constructor(props: Readonly<ZenodoInfoProps>) {
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

    onErrorDismiss = (): void => this.setState(() => ({ error: undefined }));


    setErrorMessage = (jobID: string): void =>
        this.setState(() => ({
            error: `An error occured fetching publication ${jobID}`,
            loading: false
        }));

    componentDidMount() {
        this.setState(() => ({ loading: true }));
        const intervalId = window.setInterval(this.reload, 2_000);
        this.setState(() => ({ intervalId: intervalId }));
    }

    reload = () => {
        const { promises } = this.state;
        promises.makeCancelable(Cloud.get(`/zenodo/publications/${encodeURIComponent(this.state.publicationID)}`))
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
            return (<MainContainer main={<LoadingIcon size={18} />} />)
        } else {
            return (
                <MainContainer
                    main={<>
                        <Error error={this.state.error} clearError={this.onErrorDismiss} />
                        <ZenodoPublishingBody publication={this.state.publication} />
                    </>}
                />
            );
        }
    }
}

const enum PublicationStatus {
    UPLOADING = "UPLOADING"
}

const ZenodoPublishingBody = ({ publication }) => {
    if (publication == null) return null;
    const { uploads } = publication;
    let progressBarValue = Math.ceil((uploads.filter(uploads => uploads.hasBeenTransmitted).length / uploads.length) * 100);
    return (
        <>
            <Heading.h2>
                Publication name: {publication.name}
            </Heading.h2>
            <Box pt="1em" pb="1em" />
            <Flex>
                Started:
                <Box ml="auto" />
                {dateToString(publication.createdAt)}
            </Flex>
            <Flex>
                Last update:
                <Box ml="auto" />
                {dateToString(publication.modifiedAt)}
            </Flex>
            <Box pt="1em" pb="1em" />
            <Progress
                active={publication.status === PublicationStatus.UPLOADING}
                color="green"
                label={`${progressBarValue}%`}
                percent={progressBarValue}
            />
            <FilesList files={uploads} />
        </>)
};

const FilesList = ({ files }) =>
    files === null ? null :
        (<Table.Table>
            <Table.TableHeader>
                <Table.TableRow>
                    <Table.TableHeaderCell width="75%" textAlign="left">File name</Table.TableHeaderCell>
                    <Table.TableHeaderCell textAlign="left">Status</Table.TableHeaderCell>
                </Table.TableRow>
            </Table.TableHeader>
            <Table.TableBody>
                {files.map((file, index) =>
                    <Table.TableRow key={index}>
                        <Table.TableCell>{replaceHomeFolder(file.dataObject, Cloud.homeFolder)}</Table.TableCell>
                        <Table.TableCell>{file.hasBeenTransmitted ? "Uploaded" : "Pending"}</Table.TableCell>
                    </Table.TableRow>
                )}
            </Table.TableBody>
        </Table.Table>);

export default ZenodoInfo;