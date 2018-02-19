import React from "react";
import {Button, FormGroup, Radio, FormControl, ControlLabel} from "react-bootstrap";
import FileSelector from "./FileSelector";
import { Cloud } from "../../authentication/SDUCloudObject";

class ZenodoPublish extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            files: [""],
        };
        this.handleFileSelection = this.handleFileSelection.bind(this);
        this.submit = this.submit.bind(this);
    }

    submit() {
        const body = null;
        if (body) {
            Cloud.post("/api/ZenodoPublish/", { filePaths: body.files });
        } else {

        }
    }

    handleFileSelection(file, index) {
        const files = this.state.files.slice();
        files[index] = file.path.uri;
        this.setState(() => ({
            files: files,
        }));
    }

    newFile() {
        const files = this.state.files.slice();
        files.push("");
        this.setState(() => ({
            files: files,
        }));
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <CardAndBody>
                        <h3>File Selection</h3>
                        <FileSelections handleFileSelection={this.handleFileSelection} files={this.state.files}
                                        newFile={this.newFile}/>
                        <Button onClick={() => this.newFile()}>Add additional file</Button>
                        <Button onClick={this.submit}>Moment of super</Button>
                    </CardAndBody>
                </div>
            </section>
        );
    }
}

function CardAndBody(props) {
    return (
        <div className="card">
            <div className="card-body">
                {props.children}
            </div>
        </div>
    )
}

function FileSelections(props) {
    const files = props.files.slice();
    const fileSelectors = files.map((file, index) =>
        <FileSelector key={index} onFileSelectionChange={props.handleFileSelection} parameter={index} isSource={false}/>
    );
    return (
        <FormGroup>
            {fileSelectors}
        </FormGroup>);
}

export default ZenodoPublish;