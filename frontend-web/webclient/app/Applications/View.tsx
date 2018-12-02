import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { ApplicationInformation, Application } from "Applications";
import { Image, OutlineButton, Button, Box, VerticalButtonGroup} from "ui-components";
import * as Heading from "ui-components/Heading"
import styled from "styled-components";
import { dateToString } from "Utilities/DateUtilities";
import { toLowerCaseAndCapitalize } from "UtilityFunctions"
import ContainerForText from "ui-components/ContainerForText";
import { MainContainer } from "MainContainer/MainContainer";
import { ApplicationCardContainer, SlimApplicationCard } from "./Card";
import { Page } from "Types";
import { promises } from "fs";

const circuitBoard = require("Assets/Images/circuitboard-bg.png");

type DetailedApplicationProps = any
type DetailedApplicationState = {
    appInformation?: ApplicationInformation
    previousVersions?: Page<Application>
    promises: PromiseKeeper
    loading: boolean
    error?: string
}

interface MainContentProps {
    onFavorite?: () => void
    application: ApplicationInformation
}

export class View extends React.Component<DetailedApplicationProps, DetailedApplicationState> {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper,
            loading: false,
            appInformation: undefined,
            error: undefined
        }
    }

    componentDidMount() {
        this.retrieveApplication();
        this.retrievePreviousVersions();
    }

    retrieveApplication() {
        this.setState(() => ({ loading: true }));
        const { appName, appVersion } = this.props.match.params;
        const { promises } = this.state;
        promises.makeCancelable(Cloud.get(`/hpc/apps/${encodeURI(appName)}/${encodeURI(appVersion)}`))
            .promise.then(({ response }: { response: ApplicationInformation }) =>
                this.setState(() => ({
                    appInformation: response,
                    loading: false,
                }))
            ).catch(_ => this.setState({
                error: `An error occurred fetching ${appName}`,
                loading: false
            }));
    }

    retrievePreviousVersions() {
        const { appName } = this.props.match.params;
        const { promises } = this.state;
        promises.makeCancelable(Cloud.get(`/hpc/apps/${encodeURI(appName)}`))
            .promise.then(({ response }: { response: Page<Application> }) =>
                this.setState(() => ({
                    previousVersions: response,
                }))
            ).catch(_ => this.setState({
                error: `An error occurred fetching versions of ${appName}`,
            }));
    }

    render() {
        const { appInformation } = this.state;
        return (
            <MainContainer
                header={
                    !appInformation ? null :
                        <AppHeader application={appInformation} />
                }

                main={!appInformation ?
                    <DefaultLoading loading={this.state.loading} /> :
                    <ContainerForText>
                        <Content
                            application={appInformation}
                            previousVersions={this.state.previousVersions} />
                    </ContainerForText>
                }

                sidebar={
                    !appInformation ? null : <Sidebar application={appInformation} />
                }
            />
        );
    }
}

const AppHeaderBase = styled.div`
    display: flex;
    flex-direction: row;

    & > ${Image} {
        width: 128px;
        height: 128px;
        border-radius: 8px;
        object-fit: cover;
        margin-right: 16px;
    }
`;

const AppHeaderDetails = styled.div`
    display: flex;
    flex-direction: column;

    & > h1, h2 {
        margin: 0;
    }
`;

const AppSection = styled(Box)`
    margin-bottom: 16px;
`;

const AppHeader: React.StatelessComponent<MainContentProps> = props => (
    <AppHeaderBase>
        <Image src={circuitBoard} />
        <AppHeaderDetails>
            <h1>{props.application.description.title}</h1>
            <h2>v{props.application.description.info.version}</h2>
            <span>{props.application.description.authors.join(", ")}</span>
            <Tags tags={["Test Tag 1", "Test Tag 2", "Test Tag 3"]} />
        </AppHeaderDetails>
    </AppHeaderBase>
);

const Sidebar: React.StatelessComponent<MainContentProps> = props => (
    <>
        <VerticalButtonGroup>
            <Button color={"blue"}>Add to My Applications</Button>
            <Link to={`/applications/${props.application.description.info.name}/${props.application.description.info.version}`}><OutlineButton color={"blue"}>Run Application</OutlineButton></Link>
            <a target="_blank" href="https://duckduckgo.com" rel="noopener"><OutlineButton color={"blue"}>Website</OutlineButton></a>
        </VerticalButtonGroup>
    </>
);

function Content(props: MainContentProps & { previousVersions?: Page<Application> }): JSX.Element {
    return (
        <>
            <AppSection>
                <ReactMarkdown
                    unwrapDisallowed
                    source={props.application.description.description}
                    disallowedTypes={[
                        "image",
                        "heading"
                    ]}
                />
            </AppSection>

            <AppSection>
                <PreviousVersions previousVersions={props.previousVersions} />
            </AppSection>

            <AppSection>
                <Information application={props.application} />
            </AppSection>
        </>
    );
}

const PreviousVersions: React.StatelessComponent<{ previousVersions?: Page<Application> }> = props => (
    <>
        <Heading.h4>Previous Versions</Heading.h4>
        {!props.previousVersions ? null :
            <ApplicationCardContainer>
                {props.previousVersions.items.map((it, idx) => (
                    <SlimApplicationCard linkToRun app={it} key={idx} />
                ))}
            </ApplicationCardContainer>
        }
    </>
);


const TagStyle = styled.a`
    text-decoration: none;
    padding: 6px;
    margin-right: 3px;
    border: 1px solid ${props => props.theme.colors.gray};
    border-radius: 5px;
`;

const TagBase = styled.div`
    display: flex;
    flex-direction: row;
`;

function Tags({ tags }: { tags: string[] }) {
    if (!tags) return null;

    return <div>
        <TagBase>
            {
                tags.slice(0, 5).map(tag => (
                    <TagStyle href={`foo/${tag}`}>{tag}</TagStyle>
                ))
            }
        </TagBase>
    </div>;
}

function InfoAttribute(props: {
    name: string,
    value?: string,
    children?: JSX.Element
}) {
    return <Box mb={8} mr={32}>
        <Heading.h5>{props.name}</Heading.h5>
        {props.value}
        {props.children}
    </Box>;
}

const pad = (value, length) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const InfoAttributes = styled.div`
    display: flex;
    flex-direction: row;
`;

function Information({ application }: { application: ApplicationInformation }) {
    const time = application.tool.description.defaultMaxTime;
    const timeString = `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}`;

    return <>
        <Heading.h4>Information</Heading.h4>

        <InfoAttributes>
            <InfoAttribute
                name="Release Date"
                value={dateToString(application.createdAt)} />

            <InfoAttribute
                name="Default Time Allocation"
                value={timeString} />

            <InfoAttribute
                name="Default Nodes"
                value={`${application.tool.description.defaultNumberOfNodes}`} />

            <InfoAttribute
                name="Container Type"
                value={toLowerCaseAndCapitalize(application.tool.description.backend)} />
        </InfoAttributes>
    </>;
}