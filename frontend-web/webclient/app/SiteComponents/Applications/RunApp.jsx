import React from 'react';
import {getMockApp} from '../../MockObjects';
import FileSelector from '../FileSelector';
import { Cloud } from "../../../authentication/SDUCloudObject";

class RunApp extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            appName: props.params.appName,
            appVersion: props.params.appVersion,
            appDescription: "",
            appAuthor: "",
            parameters: null,
            parameterValues: {},
        };
        this.handleSubmit = this.handleSubmit.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.handleFileSelectorChange = this.handleFileSelectorChange.bind(this);
    }

    componentDidMount() {
        this.getApplication();
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
        };

        // FIXME HACK
        let name = this.state.parameters.find(par => par.type === "input_file").name;
        let dummyFile = this.state.parameterValues[name];
        this.state.parameters.forEach( par => {
            if (par.type === "output_file") {
                job.parameters[par.name] = { destination: dummyFile.destination, source: dummyFile.destination };
            }
        });
        // FIXME HACK END

        Cloud.post("/hpc/jobs", job);
    }

    handleInputChange(parameterName, event) {
        let value = event.target.value;
        let parameterDescription = this.state.parameters.find((e) => e.name === parameterName);
        let parameterType = parameterDescription.type;
        this.setState(() => {
            let result = {
                parameterValues: Object.assign({}, this.state.parameterValues),
            };

            result.parameterValues[parameterName] = value;

            if (parameterType === "integer") {
                result.parameterValues[parameterName] = parseInt(result.parameterValues[parameterName]);
            } else if (parameterType === "float") {
                result.parameterValues[parameterName] = parseFloat(result.parameterValues[parameterName]);
            }
            // TODO Deal with this correctly FIXME
            return result;
        });
        event.preventDefault();
    }

    handleFileSelectorChange(file, parameter) {
        this.setState(() => {
            let result = {
                parameterValues: Object.assign({}, this.state.parameterValues),
            };
            result.parameterValues[parameter.name] = {
                source: file.path.uri,
                destination: file.path.name // TODO Should allow for custom name at destination
            };
            return result;
        });
    }

    getApplication() {
        this.setState(() => ({
            loading: true
        }));

        this.setState({loading: true});
        Cloud.get(`/hpc/apps/${this.state.appName}/${this.state.appVersion}`).then(app => {
            this.setState(() => ({
                parameters: app.parameters,
                appAuthor: "Dummy",
                appDescription: app.info.description,
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
                            <ApplicationHeader name={this.state.appName} version={this.state.appVersion}
                                               description={this.state.appDescription} author={this.state.appAuthor}/>
                            <hr/>
                            <Parameters parameters={this.state.parameters} handleSubmit={this.handleSubmit}
                                        onChange={this.handleInputChange}
                                        onFileSelectionChange={this.handleFileSelectorChange}/>
                        </div>
                    </div>
                </div>
            </section>)
    }
}

function ApplicationHeader(props) {
    if (!props.description) {
        return null
    }
    return (
        <div>
            <h1>{props.name}</h1>
            <h3>{props.description}</h3>
            <h4>Author: {props.author}</h4>
        </div>)
}


function Parameters(props) {
    if (!props.parameters) {
        return null
    }
    let parameters = props.parameters.slice();
    let i = 0;
    let parametersList = parameters.map(parameter =>
        <Parameter key={i++} parameter={parameter} onChange={props.onChange}
                   onFileSelectionChange={props.onFileSelectionChange}/>
    );
    return (
        <form onSubmit={props.handleSubmit} className="form-horizontal">
            {parametersList}
            <input value="Submit" className="btn btn-info" type="submit"/>
        </form>
    )

}

// Types: input, output, int, float, string
function Parameter(props) {
    let parameter;
    if (props.parameter.type === "input_file") {
        parameter = (
            <InputFileParameter onFileSelectionChange={props.onFileSelectionChange} parameter={props.parameter}/>);
    } else if (props.parameter.type === "output_file") {
        parameter = (<OutputFileParameter parameter={props.parameter}/>);
    } else if (props.parameter.type === "integer") {
        parameter = (<IntegerParameter onChange={props.onChange} parameter={props.parameter}/>);
    } else if (props.parameter.type === "float") {
        parameter = (<FloatParameter onChange={props.onChange} parameter={props.parameter}/>);
    } else if (props.parameter.type === "text") {
        parameter = (<TextParameter onChange={props.onChange} parameter={props.parameter}/>);
    }

    return (
        <fieldset>
            {parameter}
        </fieldset>)
}

function InputFileParameter(props) {
    return (
        <div className="form-group">
            <label className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <FileSelector onFileSelectionChange={props.onFileSelectionChange} parameter={props.parameter}
                              isSource={true}/>
                <span><em/></span>
                <span className="help-block">Source of the file</span>
                <input
                    placeholder={props.parameter.defaultValue ? 'Default value: ' + props.parameter.defaultValue : ''}
                    required={!props.parameter.isOptional}
                    className="form-control"
                    type="text"/>
                <div>Destination of the file</div>
            </div>
        </div>)
}

function OutputFileParameter(props) {
    return (
        <div className="form-group">
            <label className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input required={!props.parameter.isOptional}
                       className="form-control" type="text"/>
                <span className="help-block">Source of the file</span>
                <file-selector select="updateParam(index, 'destination', $event)"/>
                <span className="help-block">Destination of the file.</span>
            </div>
        </div>
    )
}

function TextParameter(props) {
    return (
        <div className="form-group">
            <label className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input id="parameter.name"
                       placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                       required={!props.parameter.isOptional}
                       name="parameter.name"
                       className="form-control"
                       type="text" onChange={e => props.onChange(props.parameter.name, e)}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>
    )
}

function IntegerParameter(props) {
    return (
        <div className="form-group">
            <label
                className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input id="parameter.name"
                       placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                       required={!props.parameter.isOptional} name={props.parameter.name}
                       className="form-control"
                       type="number"
                       step="1" onChange={e => props.onChange(props.parameter.name, e)}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>);
}

function FloatParameter(props) {
    return (
        <div className="form-group">
            <label
                className="col-sm-2 control-label">{props.parameter.prettyName}</label>
            <div className="col-md-4">
                <input id="parameter.name"
                       placeholder={props.parameter.defaultValue ? "Default value: " + props.parameter.defaultValue : ""}
                       required={!props.parameter.isOptional} name={props.parameter.name}
                       className="form-control"
                       type="number"
                       step="any" onChange={e => props.onChange(props.parameter.name, e)}/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>
    )
}

export default RunApp