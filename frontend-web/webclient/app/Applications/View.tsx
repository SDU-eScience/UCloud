import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { ApplicationInformation } from "Applications";
import { Image, OutlineButton, Button, Box } from "ui-components";
import * as Heading from "ui-components/Heading"
import styled from "styled-components";
import { dateToString } from "Utilities/DateUtilities";
import { toLowerCaseAndCapitalize } from "UtilityFunctions"
import ContainerForText from "ui-components/ContainerForText";
import { Header } from "./Header";

const circuitBoard = require("Assets/Images/circuitboard-bg.png");

type DetailedApplicationProps = any
type DetailedApplicationState = {
    appInformation?: ApplicationInformation
    promises: PromiseKeeper
    loading: boolean
    error?: string
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

    render() {
        const { appInformation } = this.state;
        return (
            !appInformation ?
                <DefaultLoading loading={this.state.loading} /> :
                <ContainerForText><Content application={appInformation} /></ContainerForText>
        );
    }
}

const HeaderSeparator = styled.div`
    margin-bottom: 16px;
`;

interface MainContentProps {
    onFavorite?: () => void
    application: ApplicationInformation
}

const SidebarBase = styled.div`
    & > ${Image} {
        width: 256px;
        height: 256px;
        border-radius: 16px;
        object-fit: cover;
        margin-bottom: 16px;
    }
`;

const ButtonGroup = styled.div`
    display: flex;
    flex-direction: column;

    & button {
        width: 100%;
        margin-bottom: 8px;
    }
`;

const MainContentBase = styled.div`
    flex-grow: 1;
`;

const ContentBase = styled.div`
    display: flex;

    & > ${SidebarBase} {
        flex-shrink: 0;
        width: 256px;
        margin-right: 32px;
    }

    & > ${MainContentBase} {
        flex-grow: 1;
    }
`;

function Content(props: MainContentProps): JSX.Element {
    return (
        <ContentBase>
            <SidebarBase>
                <Image src={circuitBoard} />
                <ButtonGroup>
                    <Button color={"blue"}>Install Application</Button>
                    <Link to={`/applications/${props.application.description.info.name}/${props.application.description.info.version}`}><OutlineButton color={"blue"}>Run Application</OutlineButton></Link>
                    <a target="_blank" href="https://duckduckgo.com" rel="noopener"><OutlineButton color={"blue"}>Website</OutlineButton></a>
                </ButtonGroup>
                <HeaderSeparator />
                <Authors authors={props.application.description.authors} />
                <HeaderSeparator />
                {/* <Tags tags={props.application.description.tags} /> */}
                <Tags tags={["Test Tag 1", "Test Tag 2", "Test Tag 3"]} />
                <HeaderSeparator />
                <Technical application={props.application} />
                <HeaderSeparator />
            </SidebarBase>

            <MainContentBase>
                <Header name={props.application.description.title} version={props.application.description.info.version} />
                <ReactMarkdown source={props.application.description.description} />
            </MainContentBase>
        </ContentBase>
    );
}

function Authors({ authors }: { authors: string[] }) {
    return <div>
        <Heading.h4>Submitted By</Heading.h4>
        <ul>
            {authors.map((it, idx) => <li key={idx}>{it}</li>)}
        </ul>
    </div>;
}

const TagStyle = styled.a`
    text-decoration: none;
    padding: 6px;
    margin: 3px 0 3px 0;
    border: 1px solid ${props => props.theme.colors.gray};
    border-radius: 5px;
`;

const TagBase = styled.div`
    display: flex;
    flex-direction: column;
`;

function Tags({ tags }: { tags: string[] }) {
    if (!tags) return null;

    return <div>
        <Heading.h4>Categories</Heading.h4>
        <TagBase>
            {
                tags.map(tag => (
                    <TagStyle href={`foo/${tag}`}>{tag}</TagStyle>
                ))
            }
        </TagBase>
    </div>;
}

function TechnicalAttribute(props: {
    name: string,
    value?: string,
    children?: JSX.Element
}) {
    return <Box mb={8}>
        <Heading.h5>{props.name}</Heading.h5>
        {props.value}
        {props.children}
    </Box>;
}

const pad = (value, length) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

function Technical({ application }: { application: ApplicationInformation }) {
    const time = application.tool.description.defaultMaxTime;
    const timeString = `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}`;

    return <>
        <Heading.h4>Technical Information</Heading.h4>

        <TechnicalAttribute
            name="Release Date"
            value={dateToString(application.createdAt)} />

        <TechnicalAttribute
            name="Default Time Allocation"
            value={timeString} />

        <TechnicalAttribute
            name="Default Nodes"
            value={`${application.tool.description.defaultNumberOfNodes}`} />

        <TechnicalAttribute
            name="Container Type"
            value={toLowerCaseAndCapitalize(application.tool.description.backend)} />
    </>;
}