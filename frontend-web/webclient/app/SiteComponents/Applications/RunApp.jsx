import React from 'react';
import {Jumbotron, InputGroup, FormGroup} from "react-bootstrap";
import FileSelector from '../FileSelector';
import {Cloud} from "../../../authentication/SDUCloudObject";
import swal from "sweetalert2";
import {BallPulseLoading} from "../LoadingIcon"
import PromiseKeeper from "../../PromiseKeeper";
import {tusConfig} from "../../Configurations";
import Uppy from "uppy";
import IndeterminateCheckbox from "../IndeterminateCheckbox";
import {castValueTo} from "../../UtilityFunctions";

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
            uppy: Uppy.Core({
                autoProceed: false,
                debug: false,
                restrictions: {
                    maxNumberOfFiles: 1,
                },
                meta: {
                    sensitive: false,
                },
                onBeforeUpload: () => {
                    return Cloud.receiveAccessTokenOrRefreshIt().then((data) => {
                        tusConfig.headers["Authorization"] = "Bearer " + data;
                    });
                }
            }),
        };
        this.handleSubmit = this.handleSubmit.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.handleFileSelectorChange = this.handleFileSelectorChange.bind(this);
        this.onCommentChange = this.onCommentChange.bind(this);
        this.onJobInfoChange = this.onJobInfoChange.bind(this);
    };

    componentDidMount() {
        this.state.uppy.use(Uppy.Tus, tusConfig);
        this.state.uppy.run();
        this.getApplication();
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    onJobInfoChange(field, value, timeField) {
        let {jobInfo} = this.state;
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
        }
        if (maxTime.hours === null && maxTime.minutes === null && maxTime.seconds === null) {
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

        // FIXME HACK
        let dummyParameter = this.state.parameters.find(par => par.type === "input_file");
        if (!!dummyParameter) {
            let name = dummyParameter.name;
            let dummyFile = this.state.parameterValues[name];
            this.state.parameters.forEach(par => {
                if (par.type === "output_file") {
                    job.parameters[par.name] = {destination: dummyFile.destination, source: dummyFile.destination};
                }
            });
        } else {
            this.state.parameters.forEach(par => {
                if (par.type === "output_file") {
                    job.parameters[par.name] = {destination: "ignored", source: "output.txt"};
                }
            });
        }
        // FIXME HACK END
        Cloud.post("/hpc/jobs", job).then(req => {
            if (req.request.status === 200) {
                this.props.history.push("/analyses");
            } else {
                swal("And error occurred. Please try again later.");
            }
        });
    }

    handleInputChange(parameterName, event) {
        let value = event.target.value;
        let parameterDescription = this.state.parameters.find((e) => e.name === parameterName);
        let parameterType = parameterDescription.type;
        this.setState(() => {
            let result = {
                parameterValues: Object.assign({}, this.state.parameterValues),
            };
            result.parameterValues[parameterName] = castValueTo(parameterType, value);
            return result;
        });
        if (event.preventDefault) {
            event.preventDefault();
        }
    }

    handleFileSelectorChange(file, returnObject) {
        this.setState(() => {
            let result = {
                parameterValues: Object.assign({}, this.state.parameterValues),
            };
            result.parameterValues[returnObject.parameter.name] = {
                source: file.path.path,
                destination: file.path.name // TODO Should allow for custom name at destination
            };
            return result;
        });
    }

    onCommentChange(comment) {
        this.setState(() => ({
            comment: comment,
        }));
    }

    getApplication() {
        this.setState(() => ({
            loading: true
        }));

        this.setState({loading: true});
        this.state.promises.makeCancelable(Cloud.get(`/hpc/apps/${this.state.appName}/${this.state.appVersion}/?resolve=true`))
            .promise.then(req => {
            const app = req.response.application;
            const tool = req.response.tool;
            let authors;
            if (app.authors.length > 1) {
                authors = app.authors.join(", ");
            } else {
                authors = app.authors[0];
            }

            this.setState(() => ({
                appName: app.info.name,
                displayAppName: app.prettyName,
                parameters: app.parameters,
                appAuthor: authors,
                appDescription: app.description,
                loading: false,
                tool,
            }));
        });
    }


    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="card">
                        <div className="card-body">
                            <BallPulseLoading loading={this.state.loading}/>
                            <ApplicationHeader name={this.state.displayAppName} version={this.state.appVersion}
                                               description={this.state.appDescription} author={this.state.appAuthor}/>
                            <Parameters parameters={this.state.parameters} handleSubmit={this.handleSubmit}
                                        onChange={this.handleInputChange} comment={this.state.comment}
                                        onFileSelectionChange={this.handleFileSelectorChange}
                                        onCommentChange={this.onCommentChange} uppy={this.state.uppy}
                                        values={this.state.parameterValues} jobInfo={this.state.jobInfo}
                                        onJobInfoChange={this.onJobInfoChange} tool={this.state.tool}
                            />
                        </div>
                    </div>
                </div>
            </section>)
    }
}

const ApplicationHeader = (props) => (
    <Jumbotron>
        <h1>{props.name}</h1>
        <h3>{props.description}</h3>
        <h4>Author: {props.author}</h4>
    </Jumbotron>
);

const Parameters = (props) => {
    if (!props.parameters) {
        return null
    }
    let i = 0;
    let parametersList = props.parameters.map(parameter =>
        <Parameter key={i++} parameter={parameter} onChange={props.onChange} uppyOpen={props.openUppy}
                   onFileSelectionChange={props.onFileSelectionChange} uppy={props.uppy} values={props.values}/>
    );
    return (
        <form onSubmit={props.handleSubmit} className="form-horizontal">
            {parametersList}
            <JobInfo onJobInfoChange={props.onJobInfoChange} jobInfo={props.jobInfo} tool={props.tool}/>
            <input value="Submit" className="btn btn-info" type="submit"/>
        </form>
    )
};

const JobInfo = (props) => {
    // TODO refactor fields, very not DRY compliant
    const {maxTime} = props.jobInfo;
    return (
        <span>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Number of nodes</label>
                    <div className="col-md-4">
                    <input type="number" step="1" placeholder={"Default value: " + props.tool.defaultNumberOfNodes}
                           className="col-md-4 form-control"
                           onChange={e => props.onJobInfoChange("numberOfNodes", parseInt(e.target.value), null)}/>
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <div className="form-group">
                    <label className="col-sm-2 control-label">Tasks per node</label>
                    <div className="col-md-4">
                    <input type="number" step="1" placeholder={"Default value: " + props.tool.defaultTasksPerNode}
                           className="col-md-4 form-control"
                           onChange={e => props.onJobInfoChange("tasksPerNode", parseInt(e.target.value), null)}/>
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <FormGroup>
                    <label className="col-sm-2 control-label">Maximum time allowed</label>
                    <div className="col-xs-10">
                        <div className="form-inline">
                            <InputGroup>
                                <input type="number" step="1" min="0" className="form-control" style={{width: 150}}
                                       placeholder={props.tool.defaultMaxTime.hours}
                                       value={maxTime.hours === null || isNaN(maxTime.hours) ? "" : maxTime.hours}
                                       onChange={e => props.onJobInfoChange("maxTime", parseInt(e.target.value), "hours")}/>
                                <span className="input-group-addon">Hours</span>
                            </InputGroup>{" "}
                            <InputGroup>
                                <input type="number" step="1" min="0" max="59" className="form-control"
                                       style={{width: 150}}
                                       placeholder={props.tool.defaultMaxTime.minutes}
                                       value={maxTime.minutes === null || isNaN(maxTime.minutes) ? "" : maxTime.minutes}
                                       onChange={e => props.onJobInfoChange("maxTime", parseInt(e.target.value), "minutes")}/>
                                <span className="input-group-addon">Minutes</span>
                            </InputGroup>{"  "}
                            <InputGroup>
                                <input type="number" step="1" min="0" max="59" className="form-control"
                                       style={{width: 150}}
                                       placeholder={props.tool.defaultMaxTime.seconds}
                                       value={maxTime.seconds === null || isNaN(maxTime.seconds) ? "" : maxTime.seconds}
                                       onChange={e => props.onJobInfoChange("maxTime", parseInt(e.target.value), "seconds")}/>
                                <span className="input-group-addon">Seconds</span>
                            </InputGroup>
                        </div>
                    </div>
                </FormGroup>
            </fieldset>
            <fieldset><CommentField onCommentChange={props.onCommentChange} comment={props.comment}/></fieldset>
        </span>
    )
};

const CommentField = (props) => (
    <div className="form-group">
        <label className="col-sm-2 control-label">Comment</label>
        <div className="col-md-4">
            <textarea disabled required style={{resize: "none"}} placeholder="Add a comment about this job..."
                      className="col-md-4 form-control" rows="5" onChange={e => props.onCommentChange(e.target.value)}/>
        </div>
    </div>
);

// Types: input, integer, floating_point, text
const Parameter = (props) => {
    if (props.parameter.type === "input_file") {
        return (<fieldset><InputFileParameter onFileSelectionChange={props.onFileSelectionChange} uppy={props.uppy}
                                              parameter={props.parameter} uppyOpen={props.uppyOpen}/></fieldset>);
    } else if (props.parameter.type === "output_file") {
        return null; // parameter = (<OutputFileParameter parameter={props.parameter}/>);
    } else if (props.parameter.type === "integer") {
        return (
            <fieldset><IntegerParameter onChange={props.onChange} parameter={props.parameter} values={props.values}/>
            </fieldset>);
    } else if (props.parameter.type === "floating_point") {
        return (<fieldset><FloatParameter onChange={props.onChange} parameter={props.parameter} values={props.values}/>
        </fieldset>);
    } else if (props.parameter.type === "text") {
        return (<fieldset><TextParameter onChange={props.onChange} parameter={props.parameter}/></fieldset>);
    } else if (props.parameter.type === "boolean") {
        return (<fieldset><BooleanParameter parameter={props.parameter} onChange={props.onChange}/></fieldset>)
    } else {
        return null;
    }
};

const InputFileParameter = (props) => (
    <div className="form-group">
        <label className="col-sm-2 control-label">{props.parameter.prettyName}</label>
        <div className="col-md-4">
            <FileSelector onFileSelectionChange={props.onFileSelectionChange} uppyOpen={props.uppyOpen}
                          uploadCallback={props.onFileSelectionChange} uppy={props.uppy}
                          isRequired={!props.parameter.optional} allowUpload={true}
                          returnObject={{parameter: props.parameter, isSource: true}}/>
            <span className="help-block">Source of the file</span>
            <OptionalText optional={props.parameter.optional}/>
        </div>
    </div>
);

const TextParameter = (props) => (
        <div className="form-group">
            <label className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input
                    placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                    required={!props.parameter.optional}
                    className="form-control"
                    type="text" onChange={e => props.onChange(props.parameter.name, e)}/>
                <OptionalText optional={props.parameter.optional}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>
);

const IntegerParameter = (props) => {
    let value = props.values[props.parameter.name];
    value = value !== undefined && !isNaN(value) ? value : NaN.toString();
    let slider = null;
    if (props.parameter.min !== null && props.parameters.max !== null) {
        slider = (
            <input
                min={props.parameter.min}
                max={props.parameter.max}
                value={value}
                step={1}
                type="range"
                onChange={e => props.onChange(props.parameter.name, e)}
            />
        );
    }
    return (
        <div className="form-group">
            <label
                className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input
                    placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                    required={!props.parameter.optional} name={props.parameter.name}
                    className="form-control"
                    type="number"
                    value={value}
                    step="1" onChange={e => props.onChange(props.parameter.name, e)}/>
                {slider}
                <OptionalText optional={props.parameter.optional}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>);
};

const BooleanParameter = (props) => (
        <div className="form-group">
            <label className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <IndeterminateCheckbox parameter={props.parameter} defaultValue={props.parameter.defaultValue}
                                       onChange={props.onChange}
                                       isIndeterminate={props.parameter.optional}/><span> {}</span>
                <OptionalText optional={props.parameter.optional}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>
);

const FloatParameter = (props) => {
    let value = props.values[props.parameter.name];
    value = value !== undefined && !isNaN(value) ? value : NaN.toString();
    let slider = null;
    if (props.parameter.min !== null && props.parameters.max !== null) {
        slider = (
            <input
                min={props.parameter.min}
                max={props.parameter.max}
                value={value}
                step={props.parameter.step}
                type="range"
                onChange={e => {
                    props.onChange(props.parameter.name, e)
                }}
            />
        );
    }
    return (
        <div className="form-group">
            <label
                className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input
                    placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                    required={!props.parameter.optional} name={props.parameter.name}
                    className="form-control"
                    type="number"
                    step="any"
                    value={value}
                    onChange={e => props.onChange(props.parameter.name, e)}/>
                {slider}
                <OptionalText optional={props.parameter.optional}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>
    )
};

const OptionalText = (props) => {
    return props.optional ? (<span className="help-block"><b>Optional</b></span>) : null;
};

export default RunApp;