import React from "react";
import { Jumbotron, InputGroup, FormGroup } from "react-bootstrap";
import { Container, Header, Form, Input } from "semantic-ui-react";
import FileSelector from "../Files/FileSelector";
import { Cloud } from "../../../authentication/SDUCloudObject";
import swal from "sweetalert2";
import PropTypes from "prop-types";
import { BallPulseLoading, LoadingButton } from "../LoadingIcon/LoadingIcon"
import PromiseKeeper from "../../PromiseKeeper";
import ReactMarkdown from "react-markdown";
import { connect } from "react-redux";
import { getFilenameFromPath } from "../../UtilityFunctions";
import { initializeUppy } from "../../DefaultObjects";
import { updateUppy } from "../../Actions/UppyActions";
import { updatePageTitle } from "../../Actions/Status";
import "../Styling/Shared.scss";

class RunApp extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: false,
            appName: props.match.params.appName,
            displayAppName: props.match.params.appName,
            appVersion: props.match.params.appVersion,
            appDescription: "",
            appAuthor: "",
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
        this.handleSubmit = this.handleSubmit.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.onCommentChange = this.onCommentChange.bind(this);
        this.onJobSchedulingParamsChange = this.onJobSchedulingParamsChange.bind(this);
        this.props.dispatch(updatePageTitle("Run App"));
    };

    componentDidMount() {
        this.retrieveApplication();
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
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


    handleSubmit(event) {
        event.preventDefault();

        let maxTime = this.state.jobInfo.maxTime;
        if (maxTime.hours !== null || maxTime.minutes !== null || maxTime.seconds !== null) {
            maxTime.hours = maxTime.hours ? maxTime.hours : 0;
            maxTime.minutes = maxTime.minutes ? maxTime.minutes : 0;
            maxTime.seconds = maxTime.seconds ? maxTime.seconds : 0;
        } else if (maxTime.hours === null && maxTime.minutes === null && maxTime.seconds === null) {
            maxTime = null;
        }

        let job = {
            application: {
                name: this.state.appName,
                version: this.state.appVersion,
            },
            parameters: Object.assign({}, this.state.parameterValues),
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

    handleInputChange(parameterName, value) {
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
            Cloud.get(`/hpc/apps/${this.state.appName}/${this.state.appVersion}/?resolve=true`)
        ).promise.then(req => {
            const app = req.response.application;
            const tool = req.response.tool;

            this.setState(() => ({
                appName: app.info.name,
                displayAppName: app.prettyName,
                parameters: app.parameters,
                appAuthor: app.authors,
                appDescription: app.description,
                loading: false,
                tool,
            }));
        });
    }


    render() {
        return (
            <section>
                <Container className="container-margin">
                    <BallPulseLoading loading={this.state.loading} />

                    <ApplicationHeader
                        name={this.state.displayAppName}
                        version={this.state.appVersion}
                        description={this.state.appDescription}
                        authors={this.state.appAuthor}
                    />

                    <Parameters
                        values={this.state.parameterValues}
                        parameters={this.state.parameters}
                        handleSubmit={this.handleSubmit}
                        onChange={this.handleInputChange}
                        comment={this.state.comment}
                        onCommentChange={this.onCommentChange}
                        uppy={this.props.uppy}
                        jobInfo={this.state.jobInfo}
                        onJobSchedulingParamsChange={this.onJobSchedulingParamsChange}
                        tool={this.state.tool}
                        jobSubmitted={this.state.jobSubmitted}
                    />
                </Container>
            </section>)
    }
}

const ApplicationHeader = ({ authors, name, description }) => {
    // Not a very good pluralize function.
    const pluralize = (array, text) => (array.length > 1) ? text + "s" : text;
    let authorString = (!!authors) ? authors.join(", ") : "";

    return (
        <Header as="h2">
            <Header.Content>
                {name}
                <h4>{pluralize(authors, "Author")}: {authorString}</h4>
            </Header.Content>
            <Header.Subheader>
                <ReactMarkdown source={description} />
            </Header.Subheader>

        </Header>
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
        <Form>
            {parametersList}
            <JobSchedulingParams
                onJobSchedulingParamsChange={props.onJobSchedulingParamsChange}
                jobInfo={props.jobInfo}
                tool={props.tool}
            />
            <LoadingButton bsStyle={"primary"} disabled={props.jobSubmitted}
                loading={props.jobSubmitted}
                style={""} buttonContent={"Submit"}
                handler={props.handleSubmit} />
        </Form>
    )
};

const JobSchedulingParams = (props) => {
    // TODO refactor fields, very not DRY compliant
    const { maxTime } = props.jobInfo;
    return (
        <span>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Number of nodes</label>
                    <div className={"col-md-8"}>
                        <input type="number" step="1" placeholder={"Default value: " + props.tool.defaultNumberOfNodes}
                            className="col-md-4 form-control"
                            onChange={e => props.onJobSchedulingParamsChange("numberOfNodes", parseInt(e.target.value), null)} />
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Tasks per node</label>
                    <div className="col-md-8">
                        <input type="number" step="1" placeholder={"Default value: " + props.tool.defaultTasksPerNode}
                            className="col-md-4 form-control"
                            onChange={e => props.onJobSchedulingParamsChange("tasksPerNode", parseInt(e.target.value), null)} />
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <FormGroup>
                    <label className="col-sm-2 control-label">Maximum time allowed</label>
                    <div className="col-xs-8">
                        <div className="form-inline">
                            <InputGroup>
                                <input type="number" step="1" min="0" className="form-control time-selection"
                                    placeholder={props.tool.defaultMaxTime.hours}
                                    value={maxTime.hours === null || isNaN(maxTime.hours) ? "" : maxTime.hours}
                                    onChange={e => props.onJobSchedulingParamsChange("maxTime", parseInt(e.target.value), "hours")} />
                                <span className="input-group-addon">Hours</span>
                            </InputGroup>{" "}
                            <InputGroup>
                                <input type="number" step="1" min="0" max="59" className="form-control time-selection"
                                    placeholder={props.tool.defaultMaxTime.minutes}
                                    value={maxTime.minutes === null || isNaN(maxTime.minutes) ? "" : maxTime.minutes}
                                    onChange={e => props.onJobSchedulingParamsChange("maxTime", parseInt(e.target.value), "minutes")} />
                                <span className="input-group-addon">Minutes</span>
                            </InputGroup>{"  "}
                            <InputGroup>
                                <input type="number" step="1" min="0" max="59" className="form-control time-selection"
                                    placeholder={props.tool.defaultMaxTime.seconds}
                                    value={maxTime.seconds === null || isNaN(maxTime.seconds) ? "" : maxTime.seconds}
                                    onChange={e => props.onJobSchedulingParamsChange("maxTime", parseInt(e.target.value), "seconds")} />
                                <span className="input-group-addon">Seconds</span>
                            </InputGroup>
                        </div>
                    </div>
                </FormGroup>
            </fieldset>
            <fieldset><CommentField onCommentChange={props.onCommentChange} comment={props.comment} /></fieldset>
        </span>
    )
};

const CommentField = (props) => (
    <div className="form-group">
        <label className="col-sm-2 control-label">Comment</label>
        <div className="col-md-8 col-lg-6">
            <textarea
                disabled
                required
                placeholder="Add a comment about this job..."
                className="col-md-4 form-control text-area-no-resize"
                rows="5"
                onChange={e => props.onCommentChange(e.target.value)}
            />
        </div>
    </div>
);

const parameterTypeToComponent = (type) => {
    switch (type) {
        case "input_file":
            return InputFileParameter;
        case "integer":
            return IntegerParameter;
        case "floating_point":
            return FloatingParameter;
        case "text":
            return TextParameter;
        case "boolean":
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
                onFileSelectionChange={(file) => internalOnChange(file)}
                uppyOpen={props.uppyOpen}
                uploadCallback={(file) => internalOnChange(file)}
                uppy={props.uppy}
                path={path}
                isRequired={!props.parameter.optional}
                allowUpload
            />
        </GenericParameter>
    );
};

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
                className="form-control"
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
            labelPosition='right'
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
            {parameter.prettyName}
        </label>
        {children}
        <OptionalText optional={parameter.optional} />
        <ReactMarkdown className="help-block" source={parameter.description} />
    </Form.Field >
);

const OptionalText = ({ optional }) =>
    optional ? (<span className="help-block"><b>Optional</b></span>) : null;

const mapStateToProps = ({ uppy }) => {
    const { uppyRunApp, uppyRunAppOpen } = uppy;
    return { uppy: uppyRunApp, uppyOpen: uppyRunAppOpen };
}

RunApp.propTypes = {
    uppy: PropTypes.object.isRequired,
    uppyOpen: PropTypes.bool.isRequired
};

export default connect(mapStateToProps)(RunApp);
