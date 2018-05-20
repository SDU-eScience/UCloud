import * as React from "react";
import { Link } from "react-router-dom";
import { ProjectMetadata } from "./api";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import * as ReactMarkdown from "react-markdown";
import { FilesTable } from "../Files/Files";
import { Creator, getById } from "./api";
import { findLicenseByIdentifier } from "./licenses";
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
    Popup,
    Grid
} from "semantic-ui-react";
import "./view.scss";

interface ViewProps {
    metadata: ProjectMetadata
}

export const View = (props: ViewProps) => {
    const { metadata } = props;
    const license = metadata.license ? findLicenseByIdentifier(metadata.license) : null;

    return <div>
        <Header as="h1">
            <Header.Content>
                {metadata.title}
                <Header.Subheader>
                    <List horizontal>
                        {metadata.contributors.map((it, idx) => (
                            <ContributorItem contributor={it} key={idx} />
                        ))}
                    </List>
                </Header.Subheader>
            </Header.Content>
        </Header>
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

                    {license ?
                        <List.Item>
                            <a href={license.link} target="_blank">
                                <Label color='blue' className="metadata-detailed-tag">
                                    <Icon name='book' />
                                    {license.identifier}
                                    <Label.Detail>License</Label.Detail>
                                </Label>
                            </a>
                        </List.Item>
                        : null
                    }

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
                    {
                        metadata.keywords.map((it, idx) => (
                            <List.Item>
                                <Label className="metadata-detailed-tag" key={idx}>{it}</Label>
                            </List.Item>
                        ))
                    }
                </List>

                <Header as="h4">References</Header>
                <List>
                    {
                        metadata.references.map((it, idx) => (
                            <List.Item>
                                {isIdentifierDOI(it) ? 
                                    <DOIBadge identifier={it} key={idx} />
                                    :
                                    <Label className="metadata-detailed-tag" key={idx}>{it}</Label>
                                }
                            </List.Item>
                        ))
                    }
                </List>
            </Grid.Column>
        </Grid>
    </div>;
}

const ContributorItem = (props: { contributor: Creator }) => {
    const { contributor } = props;
    if (
        contributor.affiliation != null ||
        contributor.gnd != null ||
        contributor.orcId != null
    ) {
        return <Popup
            trigger={
                <List.Item>
                    <a href="#">
                        <Icon name="user" />
                        {contributor.name}
                    </a>
                </List.Item>
            }
            content={
                <React.Fragment>
                    {contributor.affiliation ?
                        <p><b>Affiliation:</b> {contributor.affiliation}</p>
                        : null
                    }
                    {contributor.gnd ?
                        <p><b>GND:</b> {contributor.gnd}</p>
                        : null
                    }
                    {contributor.orcId ?
                        <p><b>ORCID:</b> {contributor.orcId}</p>
                        : null
                    }
                </React.Fragment>
            }
            on="click"
            position="bottom left"
            {...props}
        />
    } else {
        return <List.Item icon="user" content={contributor.name} {...props} />
    }
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

const isIdentifierDOI = (identifier: string): boolean => {
    return /^10\..+\/.+$/.test(identifier);
}

const DOIBadge = (props: { identifier: string }) => {
    const { identifier } = props;
    return <a href={`https://doi.org/${identifier}`} target="_blank">
        <Label className="metadata-detailed-tag" color="blue">
            {identifier}
            {/* <Label.Detail>DOI</Label.Detail> */}
        </Label>
    </a>;
}