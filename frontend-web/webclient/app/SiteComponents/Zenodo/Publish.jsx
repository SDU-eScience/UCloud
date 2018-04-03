import React from "react";
import { Button, FormGroup, ButtonToolbar, ListGroup, ListGroupItem } from "react-bootstrap";
import FileSelector from "../FileSelector";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import { LoadingButton } from "../LoadingIcon/LoadingIcon";
import { updatePageTitle } from "../../Actions/Status";
import { fetchPublications } from "../../Actions/Zenodo";
import { connect } from "react-redux";

class ZenodoPublish extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            files: [""],
            name: "",
            requestSent: false,
        };
        this.handleFileSelection = this.handleFileSelection.bind(this);
        this.submit = this.submit.bind(this);
        this.removeFile = this.removeFile.bind(this);
        this.updateName = this.updateName.bind(this);
        const { dispatch, connected } = props;
        dispatch(updatePageTitle("Zenodo Publication"));
        if (!connected) {
            dispatch(fetchPublications())
        }
    }

    submit(e) {
        e.preventDefault();
        const filePaths = this.state.files.filter(filePath => filePath);
        if (!filePaths.length || !this.state.name) {
            return
        }
        Cloud.post("/zenodo/publish/", { filePaths: filePaths, name: this.state.name }).then((res) => {
            this.props.history.push(`/zenodo/info/${res.response.publicationId}`);
        });
        this.setState(() => ({ requestSent: true }));
    }

    removeFile(index) {
        const files = this.state.files;
        files.splice(index, 1);
        this.setState(() => ({
            files,
        }));
    }

    handleFileSelection(file, index) {
        const files = this.state.files.slice();
        files[index] = file.path.path;
        this.setState(() => ({
            files,
        }));
    }

    newFile() {
        const files = this.state.files.slice();
        files.push("");
        this.setState(() => ({
            files,
        }));
    }

    updateName(name) {
        this.setState(() => ({
            name
        }));
    }

    render() {
        const filesSelected = this.state.files.filter(filePath => filePath).length > 0;
        if (!this.props.connected && !this.props.loading) {
            return (<NotConnectedToZenodo />);
        }
        return (
            <section>
                <div className="container">
                    <h3>File Selection</h3>
                    <CardAndBody>
                        <form onSubmit={e => this.submit(e)} className="form-horizontal">
                            <FileSelections
                                handleFileSelection={this.handleFileSelection}
                                files={this.state.files}
                                newFile={this.newFile} removeFile={this.removeFile}
                            />
                            <fieldset>
                                <div className="form-group">
                                    <label className="col-sm-2 control-label">Publication Name</label>
                                    <div className="col-md-8">
                                        <input required={true}
                                            value={this.state.name}
                                            className="form-control"
                                            type="text" onChange={e => this.updateName(e.target.value)} />
                                        <span className="help-block">The name of the publication</span>
                                    </div>
                                </div>
                            </fieldset>
                            <ButtonToolbar>
                                <Button bsStyle="success" onClick={() => this.newFile()}>Add additional file</Button>
                                <LoadingButton bsStyle={"primary"} disabled={!filesSelected}
                                    loading={this.state.requestSent}
                                    style={"pull-right"} buttonContent={"Upload files for publishing"}
                                    handler={this.submit} />
                            </ButtonToolbar>
                        </form>
                    </CardAndBody>
                </div>
            </section>
        );
    }
}

const CardAndBody = ({ children }) => (
    <div className="card">
        <div className="card-body">
            {children}
        </div>
    </div>
);

const FileSelections = ({ files, handleFileSelection, removeFile }) => (
    <fieldset>
        {files.map((file, index) =>
            <ListGroupItem key={index} className="col-sm-offset-2 col-md-8 input-group">
                <FileSelector
                    path={file}
                    uploadCallback={chosenFile => handleFileSelection(chosenFile, index)}
                    allowUpload={false}
                    remove={files.length > 1 ? () => removeFile(index) : false}
                />
            </ListGroupItem>)}
    </fieldset>
);

const mapStateToProps = (state) => ({ connected, loading } = state.zenodo);
export default connect(mapStateToProps)(ZenodoPublish);