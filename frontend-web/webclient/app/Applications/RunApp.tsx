import * as React from "react";
import { Button as SButton, Rating as SRating } from "semantic-ui-react";
import FileSelector from "Files/FileSelector";
import { Cloud } from "Authentication/SDUCloudObject";
import Link from "ui-components/Link";
import swal from "sweetalert2";
import { DefaultLoading } from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import * as ReactMarkdown from "react-markdown";
import { connect } from "react-redux";
import { infoNotification, failureNotification } from "UtilityFunctions";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { RunAppProps, RunAppState, JobInfo, MaxTime } from "."
import { Application, ParameterTypes } from ".";
import { extractParameters, hpcFavoriteApp } from "Utilities/ApplicationUtilities";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import { Box, Flex, Button, OutlineButton, Label, FormField, Text, Error, Select } from "ui-components";
import Input, { HiddenInputField } from "ui-components/Input";

class RunApp extends React.Component<RunAppProps, RunAppState> {
    private siteVersion = 1;

    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: false,
            favorite: false,
            error: undefined,
            appName: props.match.params.appName,
            displayAppName: props.match.params.appName,
            appVersion: props.match.params.appVersion,
            appDescription: "",
            appAuthor: [],
            parameters: [],
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
        this.props.updatePageTitle();
    };

    componentDidMount() {
        this.retrieveApplication();
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    onJobSchedulingParamsChange = (field, value, timeField) => {
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
                const extractedParameters = extractParameters(parameters, this.state.parameters.map(it => ({
                    name: it.name, type: it.type as ParameterTypes
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

    extractJobInfo(jobInfo): JobInfo {
        let extractedJobInfo = { maxTime: { hours: null, minutes: null, seconds: null }, numberOfNodes: null, tasksPerNode: null };
        const { maxTime, numberOfNodes, tasksPerNode } = jobInfo;
        if (maxTime != null && (maxTime.hours != null || maxTime.minutes != null || maxTime.seconds != null)) {
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

    onSubmit = event => {
        event.preventDefault();
        let maxTime: MaxTime = this.extractJobInfo(this.state.jobInfo).maxTime;
        if (maxTime)
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
            if (req.request.status === 200) { // FIXME Guaranteed to be 200?
                this.props.history.push(`/analyses/${req.response.jobId}`);
            } else {
                swal("And error occurred. Please try again later.");
            }
        });
        this.setState(() => ({ jobSubmitted: true }));
    }

    onInputChange = (parameterName, value) => {
        this.setState(() => {
            let result = {
                parameterValues: { ...this.state.parameterValues },
            };

            result.parameterValues[parameterName] = value;
            return result;
        });
    }

    onCommentChange = comment => this.setState(() => ({ comment }));

    retrieveApplication() {
        this.setState(() => ({ loading: true }));

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

    favoriteApplication = () => {
        this.state.promises.makeCancelable(Cloud.post(hpcFavoriteApp(this.state.appName, this.state.appVersion)))
            .promise.then(it => this.setState(() => ({ favorite: !this.state.favorite })))
            .catch(it => { !it.isCanceled ? failureNotification("An error occurred favoriting the app") : undefined })

    }

    render() {
        return (
            <Flex alignItems="center" flexDirection="column">
                <Box width={0.7}>
                    <DefaultLoading loading={this.state.loading} />
                    <Error clearError={() => this.setState(() => ({ error: undefined }))} error={this.state.error} />
                    <ApplicationHeader
                        importParameters={this.importParameters}
                        exportParameters={() => this.exportParameters()}
                        appName={this.state.appName}
                        displayName={this.state.displayAppName}
                        version={this.state.appVersion}
                        favorite={this.state.favorite}
                        favoriteApp={this.favoriteApplication}
                        authors={this.state.appAuthor}
                    />

                    <Parameters
                        values={this.state.parameterValues}
                        parameters={this.state.parameters}
                        onSubmit={this.onSubmit}
                        onChange={this.onInputChange}
                        comment={this.state.comment}
                        onCommentChange={this.onCommentChange}
                        jobInfo={this.state.jobInfo}
                        onJobSchedulingParamsChange={this.onJobSchedulingParamsChange}
                        tool={this.state.tool}
                        jobSubmitted={this.state.jobSubmitted}
                    />
                </Box>
            </Flex>
        );
    }
}

interface ApplicationHeaderProps {
    authors: string[]
    displayName: string
    appName: string
    favorite: boolean
    version: string
    favoriteApp: () => void
    exportParameters: () => void
    importParameters: (f: File) => void
}
const ApplicationHeader = ({ authors, displayName, appName, favorite, version, favoriteApp, exportParameters, importParameters }: ApplicationHeaderProps) => {
    // Not a very good pluralize function.
    const pluralize = (array, text) => (array.length > 1) ? text + "s" : text;
    let authorString = (!!authors) ? authors.join(", ") : "";

    return (
        <Heading.h1 mb={"1em"}>
            <Box className="float-right">
                <SButton.Group>
                    <SButton basic color="green" onClick={() => exportParameters()}>Export parameters</SButton>
                    <SButton basic color="green" as={"label"}>
                        <label>
                            Import parameters
                        <HiddenInputField type="file" onChange={(e) => { if (e.target.files) importParameters(e.target.files[0]) }} />
                        </label>
                    </SButton>
                    <SButton as={Link} to={`/appDetails/${appName}/${version}/`} color="blue">More information</SButton>
                </SButton.Group>
            </Box>
            <Box>
                {displayName}
                <span className="app-favorite-padding">
                    <SRating
                        icon="star"
                        size="huge"
                        rating={favorite ? 1 : 0}
                        maxRating={1}
                        onClick={() => favoriteApp()}
                    />
                </span>
                <h4>{version}</h4>
                <h4>{pluralize(authors, "Author")}: {authorString}</h4>
            </Box>
        </Heading.h1>
    );
};

const Parameters = (props) => {
    if (!props.parameters) return null

    let parametersList = props.parameters.map((parameter, index) => {
        let value = props.values[parameter.name];
        return (
            <Parameter
                key={index}
                parameter={parameter}
                onChange={props.onChange}
                value={value}
            />
        );
    });

    return (
        <form onSubmit={props.onSubmit}>
            {parametersList}
            <JobSchedulingParams
                onJobSchedulingParamsChange={props.onJobSchedulingParamsChange}
                jobInfo={props.jobInfo}
                tool={props.tool.description}
            />
            <Box mb="1em">
                <JobMetaParams
                    onJobSchedulingParamsChange={props.onJobSchedulingParamsChange}
                />
            </Box>

            <Button color="blue" disabled={props.jobSubmitted}>Submit</Button>
        </form>
    )
};

const JobMetaParams = (props) => {
    return (
        <>
            <Label color="black" fontSize={2}>
                Jobname
                <Input
                    type="text"
                    placeholder="Jobname will be assigned if field left empty"
                    disabled
                    onChange={({ target: { value } }) => console.log(value)} // onJobSchedulingParamsChange

                />
            </Label>
            <Label color="black" fontSize={2}>
                Tags (Separated by space)
            <Input
                    type="text"
                    placeholder="Assign tags to jobs"
                    disabled
                    onChange={({ target: { value } }) => console.log(value)} // onJobSchedulingParamsChange
                />
            </Label>
            {/* <Label color="black" fontSize={2}>
                Comment
                <textarea
                    placeholder="Comment..."
                    disabled
                    onChange={({ target: { value } }) => console.log(value)} // onJobSchedulingParamsChange
                />
            </Label> */}
        </>
    );
}

const JobSchedulingParams = (props) => {
    if (!props.tool) return null;

    // TODO refactor fields, very not DRY compliant
    const { maxTime, numberOfNodes, tasksPerNode } = props.jobInfo;

    return (
        <>
            <Flex mb="1em">
                <Label>Number of nodes
                <Input
                        type="number" step="1" min="1"
                        value={numberOfNodes === null || isNaN(numberOfNodes) ? "" : numberOfNodes}
                        placeholder={`Default value: ${props.tool.defaultNumberOfNodes}`}
                        onChange={({ target: { value } }) => props.onJobSchedulingParamsChange("numberOfNodes", parseInt(value), null)}
                    />
                </Label>
                <Box ml="5px" />
                <Label>Tasks per node
                <Input
                        type="number" step="1" min="1"
                        value={tasksPerNode === null || isNaN(tasksPerNode) ? "" : tasksPerNode}
                        placeholder={`Default value: ${props.tool.defaultTasksPerNode}`}
                        onChange={({ target: { value } }) => props.onJobSchedulingParamsChange("tasksPerNode", parseInt(value), null)}
                    />
                </Label>
            </Flex>
            <Label>Maximum time allowed</Label>
            <Flex mb="1em">
                <Label>
                    Hours
                    <Input
                        placeholder={props.tool.defaultMaxTime.hours}
                        type="number" step="1" min="0"
                        value={maxTime.hours === null || isNaN(maxTime.hours) ? "" : maxTime.hours}
                        onChange={({ target: { value } }) => props.onJobSchedulingParamsChange("maxTime", parseInt(value), "hours")}
                    />
                </Label>
                <Box ml="4px" />
                <Label>
                    Minutes
                    <Input
                        placeholder={props.tool.defaultMaxTime.minutes}
                        type="number" step="1" min="0" max="59"
                        value={maxTime.minutes === null || isNaN(maxTime.minutes) ? "" : maxTime.minutes}
                        onChange={({ target: { value } }) => props.onJobSchedulingParamsChange("maxTime", parseInt(value), "minutes")}
                    />
                </Label>
                <Box ml="4px" />
                <Label>
                    Seconds
                    <Input
                        placeholder={props.tool.defaultMaxTime.seconds}
                        type="number" step="1" min="0" max="59"
                        value={maxTime.seconds === null || isNaN(maxTime.seconds) ? "" : maxTime.seconds}
                        onChange={({ target: { value } }) => props.onJobSchedulingParamsChange("maxTime", parseInt(value), "seconds")}
                    />
                </Label>
            </Flex>
        </>)
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
            return GenericNumberParameter; // Must be a constructor or have call signatures
    }
};

const Parameter = (props) => {
    let Component = parameterTypeToComponent(props.parameter.type);
    return (<><Component {...props} /><Box pb="1em" /></>);
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
                onFileSelect={file => internalOnChange(file)}
                path={path}
                isRequired={!props.parameter.optional}
            /* allowUpload */
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

    let placeholder = !!props.parameter.defaultValue ? props.parameter.defaultValue.value : undefined;

    return (
        <GenericParameter parameter={props.parameter}>
            <Input
                placeholder={placeholder}
                required={!props.parameter.optional}
                type="text" onChange={e => internalOnChange(e)}
            />
        </GenericParameter>
    );
};


type BooleanParameterOption = { value?: boolean, display: string }
const BooleanParameter = (props) => {
    let options: BooleanParameterOption[] = [{ value: true, display: "Yes" }, { value: false, display: "No" }];
    if (props.parameter.optional) {
        options.unshift({ value: undefined, display: "" });
    }

    const internalOnChange = (event) => {
        let value;
        switch (event.target.value) {
            case "Yes": value = true; break;
            case "No": value = false; break;
            case "": value = undefined; break;
        }
        props.onChange(props.parameter.name, value);
        event.preventDefault();
    };

    return (
        <GenericParameter parameter={props.parameter}>
            <Select pb="9.5px" pt="9.5px" id="select" onChange={e => internalOnChange(e)} defaultValue="">
                <option></option>
                <option>Yes</option>
                <option>No</option>
            </Select>
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

    let placeholder = typeof props.parameter.defaultValue === "number" ? props.parameter.defaultValue.value : undefined;

    let baseField = (
        <Input
            placeholder={placeholder}
            required={!props.parameter.optional} name={props.parameter.name}
            type="number"
            step="any"
            value={value}
            id={props.parameter.name}
            /* label={hasLabel ? props.parameter.unitName : "Number"} */
            onChange={e => internalOnChange(e)} />
    );

    let slider: React.ReactNode = null;
    if (props.parameter.min !== null && props.parameter.max !== null) {
        slider = (
            <Input
                mt="2px"
                noBorder
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
    childProps.parseValue = it => parseInt(it);
    return <GenericNumberParameter {...childProps} />;
};

const FloatingParameter = (props) => {
    let childProps = { ...props };
    childProps.parseValue = (it) => parseFloat(it);
    return <GenericNumberParameter {...childProps} />;
};

const GenericParameter = ({ parameter, children }) => (
    <>
        <Label fontSize={2} htmlFor={parameter.name}>
            <Flex>{parameter.title}{parameter.optional ? "" : <Text bold color="red"> *</Text>}</Flex>
        </Label>
        {children}
        <OptionalText optional={parameter.optional} />
        <ReactMarkdown className="help-block" source={parameter.description} />
    </>
);

const OptionalText = ({ optional }) =>
    optional ? (<span className="help-block"><b>Optional</b></span>) : null;


const mapDispatchToProps = (dispatch: Dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("Run Application"))
});

const mapStateToProps = ({ }: ReduxObject) => ({});

export default connect(mapStateToProps, mapDispatchToProps)(RunApp);
