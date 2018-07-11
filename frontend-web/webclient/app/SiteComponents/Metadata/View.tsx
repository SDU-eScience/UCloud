import * as React from "react";
import { Link } from "react-router-dom";
import { ProjectMetadata } from "./api";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import * as ReactMarkdown from "react-markdown";
import { Creator, getByPath } from "./api";
import { findLicenseByIdentifier } from "./licenses";
import { blankOrNull } from "../../UtilityFunctions";
import {
    Label,
    Icon,
    List,
    Header,
    Popup,
    Grid
} from "semantic-ui-react";
import "./view.scss";

interface ViewProps {
    metadata: ProjectMetadata
    canEdit: boolean
}

export const View = (props: ViewProps) => {
    const { metadata, canEdit } = props;
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
                { canEdit ?
                    <React.Fragment>
                        <Header as="h4">
                            <Icon name="hand pointer" />
                            <Header.Content>Actions</Header.Content>
                        </Header>
                        <List>
                            <List.Item>
                                <Link to={`/metadata/edit/${metadata.sduCloudRoot}`}>
                                    <Label color='blue' className="metadata-detailed-tag">
                                        <Icon name='edit' />
                                        Edit
                                    </Label>
                                </Link>
                            </List.Item>
                        </List>
                    </React.Fragment>
                    : null
                }
                <Header as="h4">
                    <Icon name="info" />
                    <Header.Content>About</Header.Content>
                </Header>
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

                <Header as="h4">
                    <Icon name="hashtag" />
                    <Header.Content>Keywords</Header.Content>
                </Header>
                <List>
                    {
                        metadata.keywords.map((it, idx) => (
                            <List.Item key={idx}>
                                <Label className="metadata-detailed-tag">{it}</Label>
                            </List.Item>
                        ))
                    }
                </List>

                <Header as="h4">
                    <Icon name="bookmark" />
                    <Header.Content>References</Header.Content>
                </Header>
                <List>
                    {
                        metadata.references.map((it, idx) => (
                            <List.Item key={idx}>
                                <PotentialDOIBadge identifier={it} />
                            </List.Item>
                        ))
                    }
                </List>

                <Header as="h4">
                    <Icon name="money" />
                    <Header.Content>Grants</Header.Content>
                </Header>
                <List>
                    {
                        metadata.grants.map((it, idx) => (
                            <List.Item key={idx}>
                                <PotentialDOIBadge identifier={it.id} />
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
        !blankOrNull(contributor.affiliation) ||
        !blankOrNull(contributor.gnd) ||
        !blankOrNull(contributor.orcId)
    ) {
        return <Popup
            trigger={
                <List.Item>
                    <a href="#" onClick={(e) => e.preventDefault()}>
                        <Icon name="user" />
                        {contributor.name}
                    </a>
                </List.Item>
            }
            content={
                <React.Fragment>
                    {!blankOrNull(contributor.affiliation) ?
                        <p><b>Affiliation:</b> {contributor.affiliation}</p>
                        : null
                    }
                    {!blankOrNull(contributor.gnd) ?
                        <p><b>GND:</b> {contributor.gnd}</p>
                        : null
                    }
                    {!blankOrNull(contributor.orcId) ?
                        <p>
                            <b>ORCID:</b>
                            {" "}
                            <a href={`https://orcid.org/${contributor.orcId}`} target="_blank">
                                {contributor.orcId}
                            </a>
                        </p>
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
};

interface ManagedViewState {
    metadata?: ProjectMetadata
    canEdit?: boolean
    errorMessage?: string
}

export class ManagedView extends React.Component<any, ManagedViewState> {
    constructor(props) {
        super(props);
        this.state = {};
    }

    // TODO This is not the correct place to do this!
    componentDidMount() {
        const urlPath = this.props.match.params[0];
        if (!!this.state.metadata) return;
        
        getByPath(urlPath)
            .then(it => this.setState(() => ({ metadata: handleNullArrays(it.metadata), canEdit: it.canEdit })))
            .catch(() => console.warn("TODO something went wrong"));
    }

    render() {
        if (!this.state.metadata) {
            return <DefaultLoading loading />;
        } else {
            return <View canEdit={this.state.canEdit} metadata={this.state.metadata} />;
        }
    }
}

// TODO find more elegant solution
const handleNullArrays = (metadata: ProjectMetadata):ProjectMetadata => {
    const mData = { ...metadata };
    mData.contributors = mData.contributors ? mData.contributors : [];
    mData.keywords = mData.keywords ? mData.keywords : [];
    mData.references = mData.references ? mData.references : [];
    mData.grants = mData.grants ? mData.grants : [];
    return mData;
};

const isIdentifierDOI = (identifier: string): boolean => {
    return /^10\..+\/.+$/.test(identifier);
};

const DOIBadge = (props: { identifier: string }) => {
    const { identifier } = props;
    return <a href={`https://doi.org/${identifier}`} target="_blank">
        <Label className="metadata-detailed-tag" color="blue">
            {identifier}
        </Label>
    </a>;
}

const PotentialDOIBadge = (props: { identifier: string }) => {
    if (isIdentifierDOI(props.identifier)) {
        return <DOIBadge identifier={props.identifier} />;
    }
    return <Label className="metadata-detailed-tag">{props.identifier}</Label>;
};