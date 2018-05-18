import * as React from "react";
import { Link } from "react-router-dom";
import { ProjectMetadata } from "./api";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import * as ReactMarkdown from "react-markdown";
import { FilesTable } from "../Files/Files";
import { getById } from "./api";
import {
    Label,
    Icon,
    Card,
    Message,
    List,
    Input,
    Form,
    Header,
    Dropdown,
    Button,
    Grid
} from "semantic-ui-react";
import "./view.scss";

interface ViewProps {
    metadata: ProjectMetadata
}

export const View = (props: ViewProps) => {
    const { metadata } = props;
    return <div>
        <Header as="h1">{metadata.title}</Header>
        <Grid stackable divided>
            <Grid.Column width={12}>
                <ReactMarkdown source={metadata.description} />
            </Grid.Column>
            <Grid.Column width={4}>
                <Header as="h4">About</Header>
                <List>
                    <List.Item>
                        <Label color='green' className="metadata-detailed-tag">
                            <Icon name='folder open' />
                            Open Access
                        </Label>
                    </List.Item>

                    <List.Item>
                        <Label color='blue' className="metadata-detailed-tag">
                            <Icon name='book' />
                            MIT
                        <Label.Detail>License</Label.Detail>
                        </Label>
                    </List.Item>

                    <List.Item>
                        <Label basic className="metadata-detailed-tag">
                            <Icon name='file' />
                            {metadata.files.length}
                            <Label.Detail>Files</Label.Detail>
                        </Label>
                    </List.Item>
                </List>

                <Header as="h4">Keywords</Header>
                <List>
                    <List.Item>
                        <Label className="metadata-detailed-tag">Tag 1</Label>
                    </List.Item>
                    <List.Item>
                        <Label className="metadata-detailed-tag">Tag 2</Label>
                    </List.Item>
                    <List.Item>
                        <Label className="metadata-detailed-tag">Tag 3</Label>
                    </List.Item>
                    <List.Item>
                        <Label className="metadata-detailed-tag">Tag 4</Label>
                    </List.Item>
                </List>
            </Grid.Column>
        </Grid>
    </div>;
}

interface ManagedViewState {
    metadata?: ProjectMetadata
    errorMessage?: string
}

export class ManagedView extends React.Component<any, ManagedViewState> {
    constructor(props) {
        super(props);
        this.state = {};
    }

    componentWillReceiveProps() {
        const id = this.props.match.params.id;
        if (!!this.state.metadata && this.state.metadata.id == id) return;

        getById(id)
            .then(metadata => this.setState(() => ({ metadata })))
            .catch(() => console.warn("TODO something went wrong"));
    }

    render() {
        if (!this.state.metadata) {
            return <DefaultLoading loading />;
        } else {
            return <View metadata={this.state.metadata} />;
        }
    }
}