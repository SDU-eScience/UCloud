import React from "react";
import { Button, Container, Header, Form } from "semantic-ui-react";
import FileSelector from "../Files/FileSelector";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
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
            return (<BallPulseLoading loading={true} />)
        } else if (!this.props.connected) {
            return (<NotConnectedToZenodo />);
        }
        return (
            <React.Fragment>
                <Header as="h3" >
                    <Header.Content className="mobile-padding">
                        File Selection
                    </Header.Content>
                </Header>
                <Form onSubmit={(e) => this.submit(e)}>
                    <FileSelections className="mobile-padding"
                        handleFileSelection={this.handleFileSelection}
                        files={this.state.files}
                        newFile={this.newFile}
                        removeFile={this.removeFile}
                    />
                    <Form.Field className="mobile-padding">
                        <Form.Input
                            fluid
                            label="Publication Name"
                            required={true}
                            value={this.state.name}
                            type="text"
                            onChange={e => this.updateName(e.target.value)}
                        />
                    </Form.Field>
                    <Button
                        className="mobile-right-margin"
                        disabled={!this.state.name || this.state.files.filter(p => p).length === 0 }
                        floated="right"
                        color="blue"
                        loading={this.state.requestSent}
                        content="Upload files"
                        onClick={this.submit}
                    />
                </Form>
                <Button className="mobile-left-margin" floated="left" content="Add file" onClick={() => this.newFile()}/>
            </React.Fragment>
        );
    }
}

const FileSelections = ({ files, handleFileSelection, removeFile }) => (
    <React.Fragment>
        {files.map((file, index) =>
            (<Form.Field key={index}>
                <FileSelector
                    path={file}
                    uploadCallback={chosenFile => handleFileSelection(chosenFile, index)}
                    allowUpload={false}
                    remove={files.length > 1 ? () => removeFile(index) : false}
                />
            </Form.Field>))}
    </React.Fragment>
);

const mapStateToProps = ({ zenodo }) => ({ connected, loading } = zenodo);
export default connect(mapStateToProps)(ZenodoPublish);