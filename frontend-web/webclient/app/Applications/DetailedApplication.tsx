import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { ApplicationInformation, ApplicationDescription } from "Applications";
import { Error, Stamp, Button, Box, Flex, Icon, ContainerForText } from "ui-components";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading"
import styled from "styled-components";
import { dateToString } from "Utilities/DateUtilities";
import { toLowerCaseAndCapitalize } from "UtilityFunctions"

const HeaderSeparator = styled.div`
    margin-bottom: 10px;
`;

type DetailedApplicationProps = any
type DetailedApplicationState = {
    appInformation?: ApplicationInformation
    promises: PromiseKeeper
    loading: boolean
    error?: string
}

class DetailedApplication extends React.Component<DetailedApplicationProps, DetailedApplicationState> {
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
        promises.makeCancelable(Cloud.get(`/hpc/apps/${encodeURIComponent(appName)}/${encodeURIComponent(appVersion)}`))
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
            <MainContainer
                header={
                    <Error
                        error={this.state.error}
                        clearError={() => this.setState(() => ({ error: undefined }))}
                    />
                }

                main={
                    <ContainerForText>
                        {
                            !!!appInformation ?
                                <DefaultLoading loading={this.state.loading} /> :
                                <MainContent application={appInformation} />
                        }
                    </ContainerForText>
                }

                sidebar={
                    <>
                        {appInformation !== undefined ?
                            <Link to={`/applications/${appInformation.description.info.name}/${appInformation.description.info.version}/`}>
                                <Button fullWidth color="blue">Run Application</Button>
                            </Link> : null
                        }
                    </>
                }
            />
        );
    }
}

interface MainContentProps {
    onFavorite?: () => void
    application: ApplicationInformation
}

function MainContent(props: MainContentProps) {
    return <>
        <Header
            onFavorite={props.onFavorite}
            application={props.application} />

        <Authors authors={props.application.description.authors} />

        <Tags tags={props.application.description.tags} />

        <HeaderSeparator />

        <ReactMarkdown source={props.application.description.description} />

        <Technical application={props.application} />
    </>;
}

interface HeaderProps {
    application: ApplicationInformation
    onFavorite?: () => void
}

const HeaderStyle = styled(Heading.h1)`
    > small {
        padding-left: 10px;
        font-size: 50%;
    }

    > span {
        float: right;
        padding-right: 15px;
    }
`;

const Header = ({ application, onFavorite }: HeaderProps) => {
    const desc = application.description;
    return (
        <HeaderStyle>
            {application.description.title}
            <small>v. {desc.info.version}</small>

            {onFavorite ?
                <span>
                    <Icon
                        ml="0.5em"
                        style={{ verticalAlign: "center" }}
                        onClick={() => onFavorite()}
                        name={application.favorite ? "starFilled" : "starEmpty"}
                    />
                </span> : null
            }
        </HeaderStyle>
    );
};

function Authors({ authors }: { authors: string[] }) {
    return <div><b>Submitted by:</b> {authors.join(", ")}</div>;
}

const TagStyle = styled.a`
    padding-right: 10px;
`;

function Tags({ tags }: { tags: string[] }) {
    if (!!!tags) return null;

    return <div>
        {
            tags.map(tag => (
                <TagStyle href={`foo/${tag}`}>#{tag}</TagStyle>
            ))
        }
    </div>;
}

function TechnicalAttribute(props: {
    name: string,
    value?: string,
    children?: JSX.Element
}) {
    return <Box width={0.33} mt={10}>
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
        <Heading.h3>Technical Information</Heading.h3>

        <Flex flexDirection="row" flexWrap={"wrap"}>
            <TechnicalAttribute
                name="Release Date"
                value={dateToString(application.createdAt)} />

            <TechnicalAttribute
                name="Default Time Allocation"
                value={timeString} />

            <TechnicalAttribute
                name="Default Nodes"
                value={`${application.tool.description.defaultNumberOfNodes}`} />

            <TechnicalAttribute name="Output Files">
                <ul>
                    {application.description.outputFileGlobs.map(output =>
                        <li><code>{output}</code></li>
                    )}
                </ul>
            </TechnicalAttribute>

            <TechnicalAttribute
                name="Container Type"
                value={toLowerCaseAndCapitalize(application.tool.description.backend)} />
        </Flex>
    </>;
}

export default DetailedApplication;