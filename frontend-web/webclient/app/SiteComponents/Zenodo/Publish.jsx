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
            name: "",
        };
        this.handleFileSelection = this.handleFileSelection.bind(this);
        this.submit = this.submit.bind(this);
        this.removeFile = this.removeFile.bind(this);
        this.updateName = this.updateName.bind(this);
    }

    componentWillMount() {
        pubsub.publish('setPageTitle', "Zenodo Publication");
    }

    submit(e) {
        e.preventDefault();
        const filePaths = this.state.files.filter(filePath => filePath);
        if (!filePaths.length || !this.state.name) { return }
        Cloud.post("/zenodo/publish/", {filePaths: filePaths, name: this.state.name}).then((response) => {
                console.log(response.publicationID)
        });
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
        files[index] = file.path.path;
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

    updateName(newName) {
        this.setState(() => ({
            name: newName
        }));
        console.log(newName);
    }

    render() {
        const noFilesSelected = this.state.files.filter(filePath => filePath).length > 0;
        return (
            <section>
                <div className="container-fluid">
                    <CardAndBody>
                        <h3>File Selection</h3>
                        <form onSubmit={e => this.submit(e)} className="form-horizontal">
                            <FileSelections handleFileSelection={this.handleFileSelection} files={this.state.files}
                                            newFile={this.newFile} removeFile={this.removeFile}/>
                            <fieldset>
                                <div className="form-group">
                                    <label className="col-sm-2 control-label">Publication Name</label>
                                    <div className="col-md-4">
                                        <input required={true}
                                               value={this.state.name}
                                               className="form-control"
                                               type="text" onChange={e => this.updateName(e.target.value)}/>
                                        <span className="help-block">The name of the publication</span>
                                    </div>
                                </div>
                            </fieldset>
                            <ButtonToolbar>
                                <Button bsStyle="success" onClick={() => this.newFile()}>Add additional file</Button>
                                <Button disabled={this.state.files.length === 1}
                                        onClick={() => this.removeFile(this.state.files.length - 1)}>Remove file
                                    field</Button>
                                <Button bsStyle="primary" disabled={!noFilesSelected} className="pull-right"
                                        onClick={this.submit}>Upload files for publishing</Button>
                            </ButtonToolbar>
                        </form>
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
        <ListGroupItem key={index} className="col-sm-offset-2 col-sm-4 input-group">
            <FileSelector onFileSelectionChange={props.handleFileSelection} parameter={index} isSource={false}/>
        </ListGroupItem>);
    return (
        <fieldset>
            <FormGroup>
                <ListGroup>
                    {fileSelectors}
                </ListGroup>
            </FormGroup>
        </fieldset>
    );
}

export default ZenodoPublish;