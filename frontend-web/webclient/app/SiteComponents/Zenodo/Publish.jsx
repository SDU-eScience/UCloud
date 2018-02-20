import React from "react";
import {Button, FormGroup, ButtonGroup, ListGroup, ListGroupItem} from "react-bootstrap";
import FileSelector from "../FileSelector";
import {Cloud} from "../../../authentication/SDUCloudObject";

class ZenodoPublish extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            files: [""],
        };
        this.handleFileSelection = this.handleFileSelection.bind(this);
        this.submit = this.submit.bind(this);
        this.removeFile = this.removeFile.bind(this);
    }

    submit() {
        const body = null;
        if (body) {
            Cloud.post("/api/ZenodoPublish/", {filePaths: this.state.files.filter(filePath => filePath)});
        } else {
            console.log("Body is null.")
        }
    }

    removeFile(index) {
        const files = this.state.files.slice();
        files.splice(index, 1);
        this.setState(() => ({
            files: files,
        }));
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
                                        newFile={this.newFile} removeFile={this.removeFile}/>
                        <ButtonGroup>
                            <Button bsStyle="success" onClick={() => this.newFile()}>Add additional file</Button>
                            <Button disabled={this.state.files.length === 1}
                                    onClick={() => this.removeFile(this.state.files.length - 1)}>Remove file
                                field</Button>
                        </ButtonGroup>{" "}
                        <Button bsStyle="primary" onClick={this.submit}>Moment of super</Button>
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
        <ListGroupItem key={index} className="col-sm-4 col-sm-offset-4 input-group">
            <FileSelector onFileSelectionChange={props.handleFileSelection} parameter={index} isSource={false}/>
        </ListGroupItem>);
    return (
        <FormGroup>
            <ListGroup>
                {fileSelectors}
            </ListGroup>
        </FormGroup>
    );
}

export default ZenodoPublish;