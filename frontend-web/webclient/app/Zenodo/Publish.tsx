import * as React from "react";
import { Button, Header, Form, Message } from "semantic-ui-react";
import FileSelector from "Files/FileSelector";
import { Cloud } from "Authentication/SDUCloudObject";
import { NotConnectedToZenodo } from "Utilities/ZenodoPublishingUtilities";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { setZenodoLoading, setErrorMessage } from "./Redux/ZenodoActions";
import { connect } from "react-redux";
import { History } from "history";
import { removeEntry } from "Utilities/CollectionUtilities";
import { failureNotification } from "UtilityFunctions";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { File } from "Files";
import { SET_ZENODO_ERROR } from "Zenodo/Redux/ZenodoReducer";
import { Dispatch } from "redux";

interface ZenodoPublishState {
    files: string[]
    name: string
    requestSent: boolean
}

interface ZenodoPublishProps {
    loading: boolean
    connected: boolean
    history: History
    error?: string
}

interface ZenodoPublishOperations {
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    setErrorMessage: (error?: string) => void
}

class ZenodoPublish extends React.Component<ZenodoPublishProps & ZenodoPublishOperations, ZenodoPublishState> {
    constructor(props) {
        super(props);
        this.state = {
            files: [""],
            name: "",
            requestSent: false,
        };
        props.updatePageTitle();
    }

    componentWillUnmount = () => this.props.setErrorMessage();

    submit = (e) => {
        e.preventDefault();
        const filePaths = this.state.files.filter(filePath => filePath);
        Cloud.post("/zenodo/publish/", { filePaths, name: this.state.name }).then((res) => {
            this.props.history.push(`/zenodo/info/${res.response.publicationId}`);
            this.setState(() => ({ requestSent: true }));
        }).catch(_ => this.props.setErrorMessage("An error occurred publishing. Please try again later."));

    }

    removeFile = (index: number) => {
        const { files } = this.state;
        const remainderFiles = removeEntry<string>(files, index);
        this.setState(() => ({ files: remainderFiles }));
    }

    handleFileSelection = (file: File, index: number) => {
        const files = this.state.files.slice();
        if (files.some(f => getFilenameFromPath(f) === getFilenameFromPath(file.path))) {
            failureNotification("Zenodo does not allow duplicate filenames. Please rename either file and try again.", 8);
            return;
        }
        files[index] = file.path;
        this.setState(() => ({ files }));
    }

    newFile() {
        const files = this.state.files.slice();
        files.push("");
        this.setState(() => ({ files }));
    }

    render() {
        const { name } = this.state;
        if (this.props.loading) {
            return (<DefaultLoading className="" size={undefined} loading={true} />);
        } else if (!this.props.connected) {
            return (<NotConnectedToZenodo />);
        }
        return (
            <>
                <Header as="h3" >
                    <Header.Content className="mobile-padding">
                        File Selection
                    </Header.Content>
                </Header>
                {this.props.error ? <Message error content={this.props.error} onDismiss={() => this.props.setErrorMessage(undefined)} /> : null}
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
                            onChange={(_, { value }) => this.setState(() => ({ name: value }))}
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
            </>
        );
    }
}

const FileSelections = ({ files, handleFileSelection, removeFile }: { files: string[], handleFileSelection: Function, removeFile: Function }) => (
    <>
        {files.map((file, index) =>
            (<Form.Field key={index}>
                <FileSelector
                    isRequired={files.length === 1}
                    path={file}
                    onFileSelect={(chosenFile: File) => handleFileSelection(chosenFile, index)}
                    allowUpload={false}
                    remove={files.length > 1 ? () => removeFile(index) : undefined}
                />
            </Form.Field>))}
    </>
);

const mapStateToProps = ({ zenodo }) => zenodo;
const mapDispatchToProps = (dispatch: Dispatch): ZenodoPublishOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Zenodo Publish")),
    setErrorMessage: (error?: string) => dispatch(setErrorMessage(SET_ZENODO_ERROR, error)),
    setLoading: (loading: boolean) => dispatch(setZenodoLoading(loading))
})
export default connect(mapStateToProps, mapDispatchToProps)(ZenodoPublish);