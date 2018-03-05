import React from 'react';
import {Jumbotron} from "react-bootstrap";
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
    };

    componentDidMount() {
        this.state.uppy.use(Uppy.Tus, tusConfig);
        this.state.uppy.run();
        this.getApplication();
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    handleSubmit(event) {
        event.preventDefault();
        let job = {
            application: {
                name: this.state.appName,
                version: this.state.appVersion,
            },
            parameters: Object.assign({}, this.state.parameterValues),
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
                source: file.path.uri,
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
        this.state.promises.makeCancelable(Cloud.get(`/hpc/apps/${this.state.appName}/${this.state.appVersion}`))
            .promise.then(req => {
            const app = req.response;
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
                                        values={this.state.parameterValues}
                            />
                        </div>
                    </div>
                </div>
            </section>)
    }
}

function ApplicationHeader(props) {
    return (
        <Jumbotron>
            <h1>{props.name}</h1>
            <h3>{props.description}</h3>
            <h4>Author: {props.author}</h4>
        </Jumbotron>)
}


function Parameters(props) {
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
            <fieldset><CommentField onCommentChange={props.onCommentChange} comment={props.comment}/></fieldset>
            <input value="Submit" className="btn btn-info" type="submit"/>
        </form>
    )

}

function CommentField(props) {
    return (
        <div className="form-group">
            <label className="col-sm-2 control-label">Comment</label>
            <div className="col-md-4">
            <textarea disabled required style={{resize: "none"}} placeholder="Add a comment about this job..."
                      className="col-md-4 form-control" rows="5" onChange={e => props.onCommentChange(e.target.value)}/>
            </div>
        </div>);
}

// Types: input, output, int, float, string
function Parameter(props) {
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
}

function InputFileParameter(props) {
    return (
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
    )
}

function TextParameter(props) {
    return (
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
    )
}

function IntegerParameter(props) {
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
}

function BooleanParameter(props) {
    return (
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
}

function FloatParameter(props) {
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
}

function OptionalText(props) {
    return props.optional ? (<span className="help-block"><b>Optional</b></span>) : null;
}

export default RunApp;