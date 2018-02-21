import React from "react";
import {Button, FormGroup, ButtonToolbar, ListGroup, ListGroupItem} from "react-bootstrap";
import FileSelector from "../FileSelector";
import {Cloud} from "../../../authentication/SDUCloudObject";
import pubsub from "pubsub-js";

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

    componentWillMount() {
        pubsub.publish('setPageTitle', "Zenodo File Selection");
    }

    submit() {
        const filePaths = this.state.files.filter(filePath => filePath);
        if (body) {
            Cloud.post("/api/zenodo/publish/", {filePaths: filePaths});
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
        const noFilesSelected = this.state.files.filter(filePath => filePath).length > 0;
        return (
            <section>
                <div className="container-fluid">
                    <CardAndBody>
                        <h3>File Selection</h3>
                        <FileSelections handleFileSelection={this.handleFileSelection} files={this.state.files}
                                        newFile={this.newFile} removeFile={this.removeFile}/>
                        <ButtonToolbar>
                            <Button bsStyle="success" onClick={() => this.newFile()}>Add additional file</Button>
                            <Button disabled={this.state.files.length === 1}
                                    onClick={() => this.removeFile(this.state.files.length - 1)}>Remove file
                                field</Button>
                            <Button bsStyle="primary" disabled={!noFilesSelected} className="pull-right"
                                    onClick={this.submit}>Upload files for publishing</Button>
                        </ButtonToolbar>
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