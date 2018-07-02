import * as React from "react";
import { Button, Header, Form } from "semantic-ui-react";
import FileSelector from "../Files/FileSelector";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import { updatePageTitle } from "../../Actions/Status";
import { fetchPublications, setZenodoLoading } from "../../Actions/Zenodo";
import { connect } from "react-redux";
import "./Zenodo.scss";
import { History } from "history";

interface ZenodoPublishState {
    files: string[]
    name: string
    requestSent: boolean
}

interface ZenodoPublishProps {
    loading: boolean
    connected: boolean
    history: History
}

class ZenodoPublish extends React.Component<ZenodoPublishProps, ZenodoPublishState> {
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
        dispatch(updatePageTitle("Zenodo Publish"));
        if (!connected) {
            dispatch(setZenodoLoading(true));
            dispatch(fetchPublications(0, 10));
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
        const { files } = this.state;
        const remainderFiles = files.slice(0, index).concat(files.slice(index + 1));
        this.setState(() => ({
            files: remainderFiles
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
        const { name } = this.state;
        if (this.props.loading) {
            return (<DefaultLoading loading={true} />);
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
                    <FileSelections
                        handleFileSelection={this.handleFileSelection}
                        files={this.state.files}
                        removeFile={this.removeFile}
                    />
                    <Form.Field className="mobile-padding">
                        <Form.Input
                            fluid
                            label="Publication Name"
                            required={true}
                            value={name}
                            type="text"
                            onChange={(_, {value}) => this.updateName(value)}
                        />
                    </Form.Field>
                    <Form.Field>
                        <Button className="bottom-padding"
                            floated="left"
                            color="green"
                            content="Add file"
                            type="button"
                            onClick={() => this.newFile()}
                        />
                        <Button className="bottom-padding"
                            disabled={!name || this.state.files.filter(p => p).length === 0}
                            floated="right"
                            color="blue"
                            loading={this.state.requestSent}
                            content="Upload files"
                            onClick={this.submit}
                        />
                    </Form.Field>
                </Form>
            </React.Fragment>
        );
    }
}

const FileSelections = ({ files, handleFileSelection, removeFile }) => (
    <React.Fragment>
        {files.map((file, index) =>
            (<Form.Field key={index}>
                <FileSelector
                    isRequired={files.length === 1}
                    path={file}
                    onFileSelect={chosenFile => handleFileSelection(chosenFile, index)}
                    allowUpload={false}
                    remove={files.length > 1 ? () => removeFile(index) : null}
                />
            </Form.Field>))}
    </React.Fragment>
);

const mapStateToProps = ({ zenodo }) => ({ connected: zenodo.connected, loading: zenodo.loading });
export default connect(mapStateToProps)(ZenodoPublish);