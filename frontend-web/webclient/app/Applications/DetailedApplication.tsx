import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Grid, Header, Table, Label, Icon, List } from "../../node_modules/semantic-ui-react";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { ApplicationInformation, ParameterTypes } from "Applications";

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
        <List>
            <List.Item>
                <List.Content floated="left">
                    <Label color="green">
                        <Icon name="wrench" />
                        Container: {tool.description.backend}
                    </Label>
                    <Label color="blue">
                        <Icon name="file" />
                        Output files: {appInformation.description.outputFileGlobs.map((f, i, a) =>
                            i !== a.length - 1 ? `${f}, ` : f
                        )}
                    </Label>
                </List.Content>
                <List.Content floated="right">
                    <Label basic>
                        <Icon name="clock" />
                        Default job time: {timeString}
                    </Label>
                    <Label basic>
                        <Icon name="address book" />
                        Default number of nodes: {tool.description.defaultNumberOfNodes}
                    </Label>
                    <Label basic>
                        <Icon name="file" />
                        Default tasks per node: {tool.description.defaultTasksPerNode}
                    </Label>
                </List.Content>
            </List.Item>
        </List >
    )
}

const ApplicationParameters = (props: ApplicationDetails) => (
    <Table basic="very">
        <Table.Header>
            <Table.Row>
                <Table.HeaderCell content={"#"} />
                <Table.HeaderCell content={"Parameter name"} />
                <Table.HeaderCell content={"Default value"} />
                <Table.HeaderCell content={"Optional"} />
                <Table.HeaderCell content={"Parameter name"} />
            </Table.Row>
        </Table.Header>
        <Table.Body>
            {props.appInformation.description.parameters.map((p, i) =>
                <Table.Row key={i}>
                    <Table.Cell content={i + 1} />
                    <Table.Cell content={p.name} />
                    <Table.Cell content={p.defaultValue == null ? "No default value" : p.defaultValue} />
                    <Table.Cell icon={p.optional ? "check" : "close"} />
                    <Table.Cell content={typeToString(p.type as ParameterTypes)} />
                </Table.Row>
            )}
        </Table.Body>
    </Table>
)

const typeToString = (parameterType: ParameterTypes): string => {
    switch (parameterType) {
        case ParameterTypes.Integer:
            return "Integer";
        case ParameterTypes.FloatingPoint:
            return "Floating point";
        case ParameterTypes.Text:
            return "Text";
        case ParameterTypes.Boolean:
            return "Boolean";
        case ParameterTypes.InputFile:
            return "Input file";
        case ParameterTypes.InputDirectory:
            return "Input directory";
        default:
            console.warn(`Unhandled parameter type: ${parameterType}`);
            return "";
    }
}


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