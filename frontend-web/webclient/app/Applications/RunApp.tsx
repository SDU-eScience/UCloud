import * as React from "react";
import { Grid, Header, Form, Input, Button, Rating, Message } from "semantic-ui-react";
import FileSelector from "Files/FileSelector";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import swal from "sweetalert2";
import * as PropTypes from "prop-types";
import { DefaultLoading } from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import * as ReactMarkdown from "react-markdown";
import { connect } from "react-redux";
import { getFilenameFromPath, favoriteApplication, infoNotification, failureNotification } from "UtilityFunctions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import "Styling/Shared.scss";
import { RunAppProps, RunAppState } from "."
import { Application, ParameterTypes } from ".";
import { extractParametersVersion1 } from "Utilities/ApplicationUtilities";

class RunApp extends React.Component<RunAppProps, RunAppState> {
    private siteVersion = 1;
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: false,
            favorite: null,
            error: null,
            appName: props.match.params.appName,
            displayAppName: props.match.params.appName,
            appVersion: props.match.params.appVersion,
            appDescription: "",
            appAuthor: [],
            parameters: null,
            parameterValues: {},
            jobInfo: {
                maxTime: {
                    hours: null,
                    minutes: null,
                    seconds: null
                },
                numberOfNodes: null,
                tasksPerNode: null,
            },
            tool: {},
            comment: "",
            jobSubmitted: false
        };
        this.props.uppy.run();
        this.onSubmit = this.onSubmit.bind(this);
        this.onInputChange = this.onInputChange.bind(this);
        this.onCommentChange = this.onCommentChange.bind(this);
        this.onJobSchedulingParamsChange = this.onJobSchedulingParamsChange.bind(this);
        this.props.updatePageTitle();
    };

    static propTypes = {
        uppy: PropTypes.object.isRequired,
        uppyOpen: PropTypes.bool.isRequired
    };

    componentDidMount() {
        this.retrieveApplication();
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    favoriteApp = () => {
        this.setState(() => ({
            favorite: favoriteApplication({ favorite: this.state.favorite }).favorite
        }));
    }

    onJobSchedulingParamsChange(field, value, timeField) {
        let { jobInfo } = this.state;
        if (timeField) {
            jobInfo[field][timeField] = !isNaN(value) ? value : null;
        } else {
            jobInfo[field] = value;
        }
        this.setState(() => ({
            jobInfo
        }));
    }

    importParameters = (file: File) => {
        const fileReader = new FileReader();
        fileReader.onload = () => {
            const result = fileReader.result as string;
            try {
                const { application, parameters, numberOfNodes, tasksPerNode, maxTime, siteVersion } = JSON.parse(result);
                if (application.name !== this.state.appName) {
                    failureNotification("Application name does not match");
                    return;
                } else if (application.version !== this.state.appVersion) {
                    infoNotification("Application version does not match. Some parameters may not be filled out correctly.")
                }
                const extractedParameters = extractParametersVersion1(parameters, this.state.parameters.map(it => ({
                    name: it.name, type: it.type
                })), siteVersion);
                this.setState(() => ({
                    parameterValues: { ...this.state.parameterValues, ...extractedParameters },
                    jobInfo: this.extractJobInfo({ maxTime, numberOfNodes, tasksPerNode })
                }));
            } catch (e) {
                console.log(e);
            }
        };
        fileReader.readAsText(file);
    }

    extractJobInfo(jobInfo) {
        let extractedJobInfo = { maxTime: { hours: null, minutes: null, seconds: null }, numberOfNodes: null, tasksPerNode: null };
        const { maxTime, numberOfNodes, tasksPerNode } = jobInfo;
        if (maxTime !== null && (maxTime.hours !== null || maxTime.minutes !== null || maxTime.seconds !== null)) {
            extractedJobInfo.maxTime.hours = maxTime.hours ? maxTime.hours : null;
            extractedJobInfo.maxTime.minutes = maxTime.minutes ? maxTime.minutes : null;
            extractedJobInfo.maxTime.seconds = maxTime.seconds ? maxTime.seconds : null;
        }
        extractedJobInfo.numberOfNodes = numberOfNodes;
        extractedJobInfo.tasksPerNode = tasksPerNode;
        return extractedJobInfo;
    }

    exportParameters() {
        const jobInfo = this.extractJobInfo(this.state.jobInfo);
        const element = document.createElement("a");
        element.setAttribute("href", "data:application/json;charset=utf-8," + encodeURIComponent(JSON.stringify({
            siteVersion: this.siteVersion,
            application: {
                name: this.state.appName,
                version: this.state.appVersion,
            },
            parameters: { ...this.state.parameterValues },
            numberOfNodes: jobInfo.numberOfNodes,
            tasksPerNode: jobInfo.tasksPerNode,
            maxTime: jobInfo.maxTime,
        })));

        element.setAttribute("download", `${this.state.appName}-${this.state.appVersion}-params.json`);
        element.style.display = "none";
        document.body.appendChild(element);
        element.click();
        document.body.removeChild(element);
    }

    onSubmit(event) {
        event.preventDefault();
        let maxTime = this.extractJobInfo(this.state.jobInfo).maxTime;
        if (maxTime.hours === null && maxTime.minutes === null && maxTime.seconds === null) maxTime = null;
        let job = {
            application: {
                name: this.state.appName,
                version: this.state.appVersion,
            },
            parameters: { ...this.state.parameterValues },
            numberOfNodes: this.state.jobInfo.numberOfNodes,
            tasksPerNode: this.state.jobInfo.tasksPerNode,
            maxTime: maxTime,
            type: "start",
            //comment: this.state.comment.slice(),
        };
        Cloud.post("/hpc/jobs", job).then((req) => {
            if (req.request.status === 200) {
                this.props.history.push(`/analyses/${req.response.jobId}`);
            } else {
                swal("And error occurred. Please try again later.");
            }
        });
        this.setState(() => ({ jobSubmitted: true }));
    }

    onInputChange(parameterName, value) {
        this.setState(() => {
            let result = {
                parameterValues: { ...this.state.parameterValues },
            };

            result.parameterValues[parameterName] = value;
            return result;
        });
    }

    onCommentChange(comment) {
        this.setState(() => ({
            comment: comment,
        }));
    }

    retrieveApplication() {
        this.setState(() => ({
            loading: true
        }));

        this.state.promises.makeCancelable(
            Cloud.get(`/hpc/apps/${this.state.appName}/${this.state.appVersion}`)
        ).promise.then((req: { response: Application }) => {
            const app = req.response.description;
            const tool = req.response.tool;

            this.setState(() => ({
                favorite: req.response.favorite,
                appName: app.info.name,
                displayAppName: app.title,
                parameters: app.parameters,
                appAuthor: app.authors,
                appDescription: app.description,
                loading: false,
                tool,
            }));
        }).catch(_ => this.setState(() => ({
            loading: false,
            error: `An error occurred fetching ${this.state.appName}`
        })));
    }

    render() {
        const error = this.state.error ? <Message color="red" onDismiss={() => this.setState(() => ({ error: null }))} content={this.state.error} /> : null;
        return (
            <Grid container columns={16}>
                <Grid.Column width={16}>
                    <DefaultLoading loading={this.state.loading} />
                    {error}
                    <ApplicationHeader
                        importParameters={this.importParameters}
                        exportParameters={() => this.exportParameters()}
                        appName={this.state.appName}
                        displayName={this.state.displayAppName}
                        version={this.state.appVersion}
                        favorite={this.state.favorite}
                        favoriteApp={this.favoriteApp}
                        authors={this.state.appAuthor}
                    />

                    <Parameters
                        values={this.state.parameterValues}
                        parameters={this.state.parameters}
                        onSubmit={this.onSubmit}
                        onChange={this.onInputChange}
                        comment={this.state.comment}
                        onCommentChange={this.onCommentChange}
                        uppy={this.props.uppy}
                        jobInfo={this.state.jobInfo}
                        onJobSchedulingParamsChange={this.onJobSchedulingParamsChange}
                        tool={this.state.tool}
                        jobSubmitted={this.state.jobSubmitted}
                    />
                </Grid.Column>
            </Grid>
        );
    }
}

const ApplicationHeader = ({ authors, displayName, appName, favorite, version, favoriteApp, exportParameters, importParameters }) => {
    // Not a very good pluralize function.
    const pluralize = (array, text) => (array.length > 1) ? text + "s" : text;
    let authorString = (!!authors) ? authors.join(", ") : "";

    return (
        <Header as="h1">
            <Header.Content className="float-right">
                <Button onClick={() => exportParameters()} content="Export parameters" />
                <Button as="label">
                    Import parameters
                    <input className="import-parameters" type="file" onChange={(e) => importParameters(e.target.files[0])} />
                </Button>
                <Button as={Link} basic color="blue" content="More information" to={`/appDetails/${appName}/${version}/`} />
            </Header.Content>
            <Header.Content>
                {displayName}
                <span className="app-favorite-padding">
                    <Rating
                        icon="star"
                        size="huge"
                        rating={favorite ? 1 : 0}
                        maxRating={1}
                        onClick={() => favoriteApp()}
                    />
                </span>
                <h4>{version}</h4>
                <h4>{pluralize(authors, "Author")}: {authorString}</h4>

            </Header.Content>
        </Header >
    );
};

const Parameters = (props) => {
    if (!props.parameters) {
        return null
    }

    let parametersList = props.parameters.map((parameter, index) => {
        let value = props.values[parameter.name];
        return (
            <Parameter
                key={index}
                parameter={parameter}
                onChange={props.onChange}
                value={value}

                // TODO These should be removed from the parameter interface
                uppyOpen={props.openUppy}
                uppy={props.uppy}
            />
        );
    });

    return (
        <Form onSubmit={props.onSubmit}>
            {parametersList}
            <JobSchedulingParams
                onJobSchedulingParamsChange={props.onJobSchedulingParamsChange}
                jobInfo={props.jobInfo}
                tool={props.tool.description}
            />
            <JobMetaParams
                onJobSchedulingParamsChange={props.onJobSchedulingParamsChange}
            />

            <Button
                color="blue"
                loading={props.jobSubmitted}
                content={"Submit"}
            />
        </Form>
    )
};

const JobMetaParams = (props) => {
    return (
        <React.Fragment>
            <Form.Input
                label="Jobname"
                type="text"
                placeholder="Jobname will be assigned if field left empty"
                disabled
                onChange={(_, { value }) => console.log(value)} // onJobSchedulingParamsChange
            />
            <Form.Input
                label="Tags (Separated by space)"
                type="text"
                placeholder="Assign tags to jobs"
                disabled
                onChange={(_, { value }) => console.log(value)} // onJobSchedulingParamsChange
            />
            <Form.TextArea
                label="Comment"
                placeholder="Comment..."
                disabled
                onChange={(_, { value }) => console.log(value)} // onJobSchedulingParamsChange
            />
        </React.Fragment>
    );
}

const JobSchedulingParams = (props) => {
    // TODO refactor fields, very not DRY compliant
    const { maxTime, numberOfNodes, tasksPerNode } = props.jobInfo;
    return (
        <React.Fragment>
            <Form.Group widths="equal">
                <Form.Input
                    label="Number of nodes"
                    type="number" step="1"
                    value={numberOfNodes === null || isNaN(numberOfNodes) ? "" : numberOfNodes}
                    placeholder={`Default value: ${props.tool.defaultNumberOfNodes}`}
                    onChange={(_, { value }) => props.onJobSchedulingParamsChange("numberOfNodes", parseInt(value), null)}
                />
                <Form.Input
                    label="Tasks per node"
                    type="number" step="1"
                    value={tasksPerNode === null || isNaN(tasksPerNode) ? "" : tasksPerNode}
                    placeholder={`Default value: ${props.tool.defaultTasksPerNode}`}
                    onChange={(_, { value }) => props.onJobSchedulingParamsChange("tasksPerNode", parseInt(value), null)}
                />
            </Form.Group>
            <label>Maximum time allowed</label>
            <Form.Group widths="equal">
                <Form.Input
                    fluid
                    label="Hours"
                    placeholder={props.tool.defaultMaxTime.hours}
                    type="number" step="1" min="0"
                    value={maxTime.hours === null || isNaN(maxTime.hours) ? "" : maxTime.hours}
                    onChange={(_, { value }) => props.onJobSchedulingParamsChange("maxTime", parseInt(value), "hours")}
                />
                <Form.Input
                    fluid
                    label="Minutes"
                    placeholder={props.tool.defaultMaxTime.minutes}
                    type="number" step="1" min="0" max="59"
                    value={maxTime.minutes === null || isNaN(maxTime.minutes) ? "" : maxTime.minutes}
                    onChange={(_, { value }) => props.onJobSchedulingParamsChange("maxTime", parseInt(value), "minutes")}
                />
                <Form.Input
                    fluid
                    label="Seconds"
                    placeholder={props.tool.defaultMaxTime.seconds}
                    type="number" step="1" min="0" max="59"
                    value={maxTime.seconds === null || isNaN(maxTime.seconds) ? "" : maxTime.seconds}
                    onChange={(_, { value }) => props.onJobSchedulingParamsChange("maxTime", parseInt(value), "seconds")}
                />
            </Form.Group>
        </React.Fragment>)
};

const parameterTypeToComponent = (type) => {
    switch (type) {
        case ParameterTypes.InputFile:
            return InputFileParameter;
        case ParameterTypes.InputDirectory:
            return InputDirectoryParameter;
        case ParameterTypes.Integer:
            return IntegerParameter;
        case ParameterTypes.FloatingPoint:
            return FloatingParameter;
        case ParameterTypes.Text:
            return TextParameter;
        case ParameterTypes.Boolean:
            return BooleanParameter;
        default:
            console.warn(`Unknown parameter type: ${type}`);
            return null;
    }
};

const Parameter = (props) => {
    let Component = parameterTypeToComponent(props.parameter.type);
    return (<Component {...props} />);
};

const InputFileParameter = (props) => {
    const internalOnChange = (file) => {
        props.onChange(props.parameter.name, {
            source: file.path,
            destination: getFilenameFromPath(file.path) // TODO Should allow for custom name at destination
        });
    };
    const path = props.value ? props.value.source : "";
    return (
        <GenericParameter parameter={props.parameter}>
            <FileSelector
                onFileSelect={(file) => internalOnChange(file)}
                uppy={props.uppy}
                path={path}
                isRequired={!props.parameter.optional}
                allowUpload
            />
        </GenericParameter>
    );
};

const InputDirectoryParameter = (props) => {
    const internalOnChange = (file) => {
        props.onChange(props.parameter.name, {
            source: file.path,
            destination: getFilenameFromPath(file.path) // TODO Should allow for custom name at destination
        });
    };
    const path = props.value ? props.value.source : "";
    return (
        <GenericParameter parameter={props.parameter}>
            <FileSelector
                onFileSelect={(file) => internalOnChange(file)}
                path={path}
                canSelectFolders
                onlyAllowFolders
                isRequired={!props.parameter.optional}
            />
        </GenericParameter>
    )
}

const TextParameter = (props) => {
    const internalOnChange = (event) => {
        event.preventDefault();
        props.onChange(props.parameter.name, event.target.value);
    };

    return (
        <GenericParameter parameter={props.parameter}>
            <input
                placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                required={!props.parameter.optional}
                type="text" onChange={e => internalOnChange(e)}
            />
        </GenericParameter>
    );
};

const BooleanParameter = (props) => {
    let options = [{ value: true, display: "Yes" }, { value: false, display: "No" }];
    if (props.parameter.optional) {
        options.unshift({ value: null, display: "" });
    }

    const internalOnChange = (event, data) => {
        let index = parseInt(data.value);
        let actualValue = options[index];
        props.onChange(props.parameter.name, actualValue.value);
        event.preventDefault();
    };

    let selected = options.findIndex(it => it.value === props.value);

    return (
        <GenericParameter parameter={props.parameter}>
            <Form.Select id={props.parameter} onChange={(e, d) => internalOnChange(e, d)} value={selected}
                options={
                    options.map((it, idx) =>
                        ({ key: idx, text: it.display, value: idx }))
                }
            />
        </GenericParameter>
    );
};

const GenericNumberParameter = (props) => {
    const internalOnChange = (event) => {
        event.preventDefault();

        if (event.target.value === "") {
            props.onChange(props.parameter.name, undefined);
        } else {
            let value = props.parseValue(event.target.value);
            if (!isNaN(value)) {
                props.onChange(props.parameter.name, value);
            }
        }
    };

    let value = (props.value != null) ? props.value : "";

    const hasLabel = !!props.parameter.unitName;
    
    let baseField = (
        <Input
            labelPosition="right"
            label={{ basic: true, content: hasLabel ? props.parameter.unitName : "Number" }}
            placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
            required={!props.parameter.optional} name={props.parameter.name}
            type="number"
            step="any"
            value={value}
            id={props.parameter.name}
            onChange={e => internalOnChange(e)} />
    );

    let slider = null;
    if (props.parameter.min !== null && props.parameter.max !== null) {
        slider = (
            <Input
                min={props.parameter.min}
                max={props.parameter.max}
                step={props.parameter.step}
                type="range"
                value={value}
                onChange={e => internalOnChange(e)}
            />
        );
    }

    return (
        <GenericParameter parameter={props.parameter}>
            {baseField}
            {slider}
        </GenericParameter>
    );
};

const IntegerParameter = (props) => {
    let childProps = { ...props };
    childProps.parseValue = (it) => parseInt(it);
    return <GenericNumberParameter {...childProps} />;
};

const FloatingParameter = (props) => {
    let childProps = { ...props };
    childProps.parseValue = (it) => parseFloat(it);
    return <GenericNumberParameter {...childProps} />;
};

const GenericParameter = ({ parameter, children }) => (
    <Form.Field required={!parameter.optional}>
        <label htmlFor={parameter.name}>
            {parameter.title}
        </label>
        {children}
        <OptionalText optional={parameter.optional} />
        <ReactMarkdown className="help-block" source={parameter.description} />
    </Form.Field >
);

const OptionalText = ({ optional }) =>
    optional ? (<span className="help-block"><b>Optional</b></span>) : null;


const mapDispatchToProps = (dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("Run Application"))
});

const mapStateToProps = ({ uppy }) => {
    const { uppyRunApp, uppyRunAppOpen } = uppy;
    return { uppy: uppyRunApp, uppyOpen: uppyRunAppOpen };
}

export default connect(mapStateToProps, mapDispatchToProps)(RunApp);
