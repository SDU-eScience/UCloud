import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Grid, Header, Table, List } from "../../node_modules/semantic-ui-react";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";

type DetailedApplicationProps = any
type DetailedApplicationState = {
    appInformation?: ApplicationInformation
    promises: PromiseKeeper
    loading: boolean
}


class DetailedApplication extends React.Component<DetailedApplicationProps, DetailedApplicationState> {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper,
            loading: false
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
            .promise.then(({ response }: { response: ApplicationInformation }) => {
                this.setState(() => ({
                    appInformation: response,
                    loading: false,
                }))
            });
    }

    render() {
        const { appInformation } = this.state;
        return (
            <Grid container columns={16}>
                <Grid.Column width={16}>
                    <DefaultLoading loading={this.state.loading} />
                    <ApplicationDetails appInformation={appInformation} />
                </Grid.Column>
            </Grid>
        );
    }
}

interface ApplicationDetails { appInformation: ApplicationInformation }
const ApplicationDetails = ({ appInformation }: ApplicationDetails) => {
    if (appInformation == null) return null;
    return (
        <React.Fragment>
            <ApplicationHeader appInformation={appInformation} />
            <Header as="h1" content="Tools" />
            <ApplicationTools appInformation={appInformation} />
            <Header as="h1" content="Parameters" />
            <ApplicationParameters appInformation={appInformation} />
        </React.Fragment>
    );
}

const ApplicationTools = ({ appInformation }: ApplicationDetails) => {
    const { tool } = appInformation;
    const { hours, minutes, seconds } = tool.description.defaultMaxTime;
    const padNumber = (val: number): string => val < 10 ? `0${val}` : `${val}`; 
    const timeString = `${padNumber(hours)}:${padNumber(minutes)}:${padNumber(seconds)}`;
    return (
        <Table basic="very">
            <Table.Header>
                <Table.Row>
                    <Table.HeaderCell content="Container name" />
                    <Table.HeaderCell content="Default job time (HH:MM:SS)" />
                    <Table.HeaderCell content="Output files" />
                </Table.Row>
            </Table.Header>
            <Table.Body>
                <Table.Row>
                    <Table.Cell content={tool.description.backend} />
                    <Table.Cell content={timeString} />
                    <Table.Cell content={appInformation.description.outputFileGlobs.map((f, i, a) =>
                        i !== a.length -1 ? `${f}, ` : f
                    )} />
                </Table.Row>
            </Table.Body>
        </Table>
    )
}

const ApplicationParameters = (props: ApplicationDetails) => (
    <Table basic="very">
        <Table.Header>
            <Table.Row>
                <Table.HeaderCell content={"Parameter name"} />
                <Table.HeaderCell content={"Default value"} />
                <Table.HeaderCell content={"Optional"} />
                <Table.HeaderCell content={"Parameter name"} />
            </Table.Row>
        </Table.Header>
        <Table.Body>
            {props.appInformation.description.parameters.map((p, i) =>
                <Table.Row key={i}>
                    <Table.Cell content={p.name} />
                    <Table.Cell content={p.defaultValue == null ? "No default value" : p.defaultValue} />
                    <Table.Cell icon={p.optional ? "check" : "close"} />
                    <Table.Cell content={p.type} />
                </Table.Row>
            )}
        </Table.Body>
    </Table>
)



const ApplicationHeader = ({ appInformation }: ApplicationDetails) => {
    if (appInformation == null) return null;
    // Not a very good pluralize function.
    const pluralize = (array, text) => (array.length > 1) ? text + "s" : text;
    let authorString = (!!appInformation.description.authors) ? appInformation.description.authors.join(", ") : "";

    return (
        <Header as="h1">
            <Header.Content>
                {appInformation.description.title}
                <h4>{appInformation.description.info.version}</h4>
                <h4>{pluralize(appInformation.description.authors, "Author")}: {authorString}</h4>
            </Header.Content>
            <Header.Subheader>
                <ReactMarkdown source={appInformation.description.description} />
            </Header.Subheader>
        </Header>
    );
};

export default DetailedApplication;

interface ApplicationInformation {
    owner: string
    createdAt, modifiedAt: number
    description: {
        info: {
            name: string
            version: string
        }
        tool: {
            name: string
            version: string
        }
        authors: string[]
        title: string
        description: string
        invocation: any
        parameters: {
            name: string
            optional: boolean
            defaultValue: any
            title: string
            description: string
            trueValue?: boolean
            falseValue?: boolean
            type: string
        }[]
        outputFileGlobs: [string, string]
    }
    tool: {
        owner: string
        createdAt: number
        modifiedAt: number
        description: {
            info: {
                name: string
                version: string
            }
            container: string
            defaultNumberOfNodes: number,
            defaultTasksPerNode: number,
            defaultMaxTime: {
                hours: number
                minutes: number
                seconds: number
            }
            requiredModules: any[],
            authors: string[]
            title: string,
            description: string
            backend: string
        }
    }
}