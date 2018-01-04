import React from 'react';
import { getMockApp } from '../../MockObjects'
import FileSelector from '../FileSelector'

class RunApp extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            appName: props.params.appName,
            appVersion: props.params.appVersion,
            app: null,
        };
    }

    componentDidMount() {
        this.getApplication();
    }

    handleSubmit(event) {
        console.log("Submitted");
        event.preventDefault();
    }

    handleInputChange(parameterName, event) {
        let app = this.state.app;
        console.log(app.parameters[parameterName]);
        console.log(event.target.value);
        app.parameters[parameterName].value = event.target.value;
        this.setState({app: app});
        event.preventDefault();
    }

    handleFileSelectorChange() {

    }

    getApplication() {
        this.setState({
            loading: true
        });
        // FIXME ACTUAL APPLICATION
        {
            let app = getMockApp(this.state.appName, this.state.appVersion);
            this.setState({app: app});
            this.setState({loading: false});
        }
        // FIXME END
    }


    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="card">
                        <div className="card-body">
                            <ApplicationHeader app={this.state.app}/>
                            <hr/>
                            <Parameters app={this.state.app} handleSubmit={this.handleSubmit}
                                        onChange={this.handleInputChange}/>
                        </div>
                    </div>
                </div>
            </section>)
    }
}

function ApplicationHeader(props) {
    if (!props.app) {
        return null
    }
    return (
        <div>
            <h1>{props.app.info.name}</h1>
            <h3>{props.app.info.description}</h3>
            <h4>Author: {props.app.info.author}</h4>
        </div>)
}


function Parameters(props) {
    if (!props.app) { return null }
    let parameters = props.app.parameters.slice();
    let i = 0;
    let parametersList = parameters.map(parameter =>
        <Parameter key={i++} parameter={parameter} onChange={props.onChange}/>
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
        parameter = (<InputFileParameter parameter={props.parameter}/>);
    } else if (props.parameter.type === "output_file") {
        parameter = (<OutputFileParameter parameter={props.parameter}/>);
    } else if (props.parameter.type === "integer") {
        parameter = (<IntegerParameter parameter={props.parameter}/>);
    } else if (props.parameter.type === "float") {
        parameter = (<FloatParameter parameter={props.parameter}/>);
    } else if (props.parameter.type === "text") {
        parameter = (<TextParameter parameter={props.parameter}/>);
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
                <FileSelector/>
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
            <label  className="col-sm-2 control-label">{props.parameter.prettyName}</label>
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
                       type="text"/>
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
                       step="1"/>
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
                       step="any"/>
                <span className="help-block">{props.parameter.description}</span>
            </div>
        </div>
    )
}

export default RunApp