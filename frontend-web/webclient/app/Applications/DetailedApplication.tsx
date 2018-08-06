import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { Grid, Header, Table, Label, Icon, List, Rating, Button } from "semantic-ui-react";
import { Link } from "react-router-dom";
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

    favoriteApplication = (): void => {
        const { appInformation } = this.state;
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
        let body;
        if (appInformation != null) {
            body = (
                <React.Fragment>
                    <ApplicationHeader favoriteApplication={this.favoriteApplication} appInformation={appInformation} />
                    <Header as="h3" content="Tags" />
                    <ApplicationTags tags={[] as string[]} />
                    <Header as="h1" content="Tools" />
                    <ApplicationTools appInformation={appInformation} />
                </React.Fragment>)
        } else {
            body = (<DefaultLoading loading={this.state.loading} />);
        }
        return (
            <Grid container columns={16}>
                <Grid.Column width={16}>
                    {body}
                </Grid.Column>
            </Grid>
        );
    }
}

interface ApplicationDetails { appInformation: ApplicationInformation }

const ApplicationTags = (props: { tags: string[] }) => {
    const mockedTags = ["nanomachines", "medication", "megamachines", "hyper light simulation", "teleportation research"];
    return (
        <React.Fragment>
            {mockedTags.map((tag, i) => <Label key={i} basic content={tag} />)}
        </React.Fragment>
    )
};

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
                        Output files: {appInformation.description.outputFileGlobs.map((f, i, { length }) =>
                            i !== length - 1 ? `${f}, ` : f
                        )}
                    </Label>
                    <Label content={`${appInformation.description.parameters.length} parameters`} />
                </List.Content>
                <List.Content floated="right">
                    <Label basic>
                        <Icon name="clock" />
                        Default job time: {timeString}
                    </Label>
                    <Label basic>
                        Default number of nodes: {tool.description.defaultNumberOfNodes}
                    </Label>
                    <Label basic content={`Default tasks per node: ${tool.description.defaultTasksPerNode}`} />
                </List.Content>
            </List.Item>
        </List >
    )
}

// FIXME, remove?
const ApplicationParameters = (props: ApplicationDetails) => (
    <Table basic="very">
        <Table.Header>
            <Table.Row>
                <Table.HeaderCell content="Parameter name" />
                <Table.HeaderCell content="Default value" />
                <Table.HeaderCell content="Optional" />
                <Table.HeaderCell content="Parameter name" />
            </Table.Row>
        </Table.Header>
        <Table.Body>
            {props.appInformation.description.parameters.map((p, i) =>
                <Table.Row key={i}>
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

interface ApplicationHeaderProps extends ApplicationDetails { favoriteApplication: () => void }
const ApplicationHeader = ({ appInformation, favoriteApplication }: ApplicationHeaderProps) => {
    if (appInformation == null) return null;
    const { info } = appInformation.description;
    // Not a very good pluralize function.
    const pluralize = (array, text) => (array.length > 1) ? text + "s" : text;
    let authorString = (!!appInformation.description.authors) ? appInformation.description.authors.join(", ") : "";

    return (
        <Header as="h1">
            <Header.Content className="float-right">
                <Button as={Link} basic color="blue" content="Run Application" to={`/applications/${info.name}/${info.version}`} />
            </Header.Content>
            <Header.Content>
                {appInformation.description.title}
                <span className="app-favorite-padding">
                    <Rating
                        icon="star"
                        size="huge"
                        rating={appInformation.favorite ? 1 : 0}
                        maxRating={1}
                        onClick={() => favoriteApplication()}
                    />
                </span>
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