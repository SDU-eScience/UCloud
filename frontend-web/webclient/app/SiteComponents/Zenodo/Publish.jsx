import React from "react";
import { Button, Container, List } from "semantic-ui-react";
import FileSelector from "../Files/FileSelector";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import {BallPulseLoading, LoadingButton} from "../LoadingIcon/LoadingIcon";
import { updatePageTitle } from "../../Actions/Status";
import { fetchPublications, setZenodoLoading } from "../../Actions/Zenodo";
import { connect } from "react-redux";
import "./Zenodo.scss";

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
            dispatch(setZenodoLoading(true));
            dispatch(fetchPublications());
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
            files
        }));
    }

    handleFileSelection(file, index) {
        const files = this.state.files.slice();
        files[index] = file.path;
        this.setState(() => ({
            files
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
        const { name } = this.state;
        if (this.props.loading) {
            return (<BallPulseLoading loading={true}/>)
        } else if (!this.props.connected) {
            return (<NotConnectedToZenodo />);
        }
        return (
            <section>
                <Container>
                    <h3>File Selection</h3>
                    <CardAndBody>
                        <form onSubmit={e => this.submit(e)} className="form-horizontal">
                            <FileSelections
                                handleFileSelection={this.handleFileSelection}
                                files={this.state.files}
                                newFile={this.newFile}
                                removeFile={this.removeFile}
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
                            <Button onClick={() => this.newFile()}>Add additional file</Button>
                            <LoadingButton
                                disabled={this.state.requestSent || !filesSelected || !name}
                                loading={this.state.requestSent}
                                buttonContent={"Upload files for publishing"}
                                handler={this.submit} />
                        </form>
                    </CardAndBody>
                </Container>
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
    <List>
        {files.map((file, index) =>
            (<List.Item key={index} className="col-sm-offset-2 col-md-8 input-group zero-padding">
                <FileSelector
                    path={file}
                    uploadCallback={chosenFile => handleFileSelection(chosenFile, index)}
                    allowUpload={false}
                    remove={files.length > 1 ? () => removeFile(index) : false}
                />
            </List.Item>))}
    </List>
);

const mapStateToProps = (state) => ({ connected, loading } = state.zenodo);
export default connect(mapStateToProps)(ZenodoPublish);