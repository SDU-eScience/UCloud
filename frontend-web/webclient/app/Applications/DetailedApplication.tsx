import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { ApplicationInformation } from "Applications";
import { Error, Stamp, Button, Box, Flex, Icon } from "ui-components";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading"

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
        promises.makeCancelable(Cloud.get(`/hpc/apps/${appName}/${appVersion}`))
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

    favoriteApplication = (): void => {
        const { appInformation } = this.state;
        if (!appInformation) return;
        appInformation.favorite = !appInformation.favorite;
        if (appInformation.favorite) {
            // post
        } else {
            // delete
        }
        this.setState(() => ({ appInformation }));
    }

    render() {
        const { appInformation } = this.state;
        return (
            <MainContainer
                header={<Error error={this.state.error} clearError={() => this.setState(() => ({ error: undefined }))} />}
                main={
                    <>
                        <DefaultLoading loading={this.state.loading} />
                        <ApplicationHeader favoriteApplication={this.favoriteApplication} appInformation={appInformation} />
                        <Heading.h3 mt="0.3em">Tags</Heading.h3>
                        <ApplicationTags tags={[] as string[]} />
                        <Heading.h3 mt="0.3em">Tools</Heading.h3>
                        <ApplicationTools appInformation={appInformation} />
                    </>
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


const ApplicationTags = (props) => {
    const mockedTags = ["nanomachines", "medication", "megamachines", "hyper light simulation", "teleportation research"];
    return (<>{mockedTags.map((tag, i) => <Stamp key={i} color="black" bg="white" borderColor="black">{tag}</Stamp>)}</>);
};

interface ApplicationDetails { appInformation?: ApplicationInformation }
const ApplicationTools = ({ appInformation }: ApplicationDetails) => {
    if (appInformation == null) return null;

    const { tool } = appInformation;
    const { hours, minutes, seconds } = tool.description.defaultMaxTime;
    const padNumber = (val: number): string => val < 10 ? `0${val}` : `${val}`;
    const timeString = `${padNumber(hours)}:${padNumber(minutes)}:${padNumber(seconds)}`;
    return (
        <Flex>
            <Stamp bg="green" color="white" borderColor="green">
                <Box pr="0.2em" pl="0.2em">
                    <i className="fas fa-wrench" />
                </Box>
                Container: {tool.description.backend}
            </Stamp>
            <Stamp bg="blue" color="white" borderColor="blue">
                <Box pr="0.2em" pl="0.2em">
                    <i className="far fa-file" />
                </Box>
                Output files: {appInformation.description.outputFileGlobs.join(", ")}
            </Stamp>
            <Stamp color="black" bg="white" borderColor="black">
                {`${appInformation.description.parameters.length} parameters`}
            </Stamp>
            <Box ml="auto" />
            <Stamp borderColor="black" color="black" bg="white">
                <i className="far fa-clock" />
                Default job time: {timeString}
            </Stamp>
            <Stamp borderColor="black" color="black" bg="white">
                Default number of nodes: {tool.description.defaultNumberOfNodes}
            </Stamp>
            <Stamp borderColor="black" color="black" bg="white">
                {`Default tasks per node: ${tool.description.defaultTasksPerNode}`}
            </Stamp>
        </Flex>
    )
}

interface ApplicationHeaderProps extends ApplicationDetails { favoriteApplication: () => void }
const ApplicationHeader = ({ appInformation, favoriteApplication }: ApplicationHeaderProps) => {
    if (appInformation == null) return null;
    // Not a very good pluralize function.
    const pluralize = (array, text) => (array.length > 1) ? text + "s" : text;
    let authorString = (!!appInformation.description.authors) ? appInformation.description.authors.join(", ") : "";

    return (
        <Heading.h1>
            {appInformation.description.title}
            <span className="app-favorite-padding">
                <Icon
                    ml="0.5em"
                    style={{ verticalAlign: "center" }}
                    onClick={() => favoriteApplication()}
                    name={appInformation.favorite ? "starFilled" : "starEmpty"}
                />
            </span>
            <h4>{appInformation.description.info.version}</h4>
            <h4>{pluralize(appInformation.description.authors, "Author")}: {authorString}</h4>
            <Heading.h5>
                <ReactMarkdown source={appInformation.description.description} />
            </Heading.h5>
        </Heading.h1>
    );
};

export default DetailedApplication;