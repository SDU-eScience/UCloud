import * as React from "react";
import { Link, Button, Flex, List } from "ui-components";
import { ProjectMetadata } from "./api";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import * as ReactMarkdown from "react-markdown";
import { Contributor, getByPath } from "./api";
import { findLicenseByIdentifier } from "./licenses";
import { blankOrUndefined } from "UtilityFunctions";
import { getQueryParam, RouterLocationProps } from "Utilities/URIUtilities";
import { projectEditPage } from "Utilities/ProjectUtilities";
import * as Heading from "ui-components/Heading";
import { Box, Stamp, Text } from "ui-components";
import { TextSpan } from "ui-components/Text";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { MainContainer } from "MainContainer/MainContainer";

interface ViewProps {
    metadata: ProjectMetadata
    canEdit?: boolean
}

const filePathFromProps = (props: RouterLocationProps): string | null => {
    return getQueryParam(props, "filePath");
}

export const View = (props: ViewProps) => {
    const { canEdit } = props;
    const metadata = handleNullArrays(props.metadata);
    const license = metadata.license ? findLicenseByIdentifier(metadata.license) : null;

    const header = (
        <>
            <Heading.h2>
                {metadata.title}
            </Heading.h2>
            <Flex>
                {metadata.contributors.map((it, idx) => (
                    <Box mr="0.5em">
                        <ContributorItem contributor={it} key={idx} />
                    </Box>
                ))}
            </Flex>
        </>)

    const sidebar = (<Box>
        {canEdit ?
            <>
                <SectionHeader
                    title="Actions"
                    iconClass="fas fa-hand-pointer"
                />
                <Link mb="1em" to={projectEditPage(metadata.sduCloudRoot)}>
                    <Button color="blue" lineHeight="0.3" size="small" fullWidth>
                        <i style={{ paddingRight: "0.4em" }} className="far fa-edit" />
                        Edit
                    </Button>
                </Link>
            </>
            : null
        }
        <SectionHeader
            iconClass="fas fa-info"
            title="About"
        />
        <List bordered={false} pb="1em">
            {license ?
                <a href={license.link} target="_blank" rel="noopener">
                    <Button color="blue" lineHeight="0.3" size="small" fullWidth>
                        <i style={{ paddingRight: "0.4em" }} className="fas fa-book" />
                        <Text mr="0.4em" as="span" bold>{license.identifier}</Text>
                        <TextSpan color="lightGray">License</TextSpan>
                    </Button>
                </a> : null
            }
        </List>
        <SectionHeader
            iconClass="fas fa-hashtag"
            title="Keywords"
        />
        <List mb="1em" bordered={false}>
            {metadata.keywords.map((it, idx) => (
                <Stamp mb="0.4em" fullWidth borderColor="lightGray" key={idx}>{it}</Stamp>
            ))}
        </List>

        <SectionHeader
            iconClass="fas fa-bookmark"
            title="References"
        />
        <List bordered={false} mb="1em">
            {metadata.references.map((it, idx) => (<PotentialDOIBadge key={idx} identifier={it} />))}
        </List>

        <SectionHeader
            iconClass="fas fa-money-bill"
            title="Grants"
        />
        <List mb="1em" bordered={false}>
            {metadata.grants.map((it, idx) => (<PotentialDOIBadge key={idx} identifier={it.id} />))}
        </List>
    </Box>);

    return (
        <MainContainer
            header={header}
            main={<ReactMarkdown source={metadata.description} />}
            sidebar={sidebar}
        />
    );
}

const SectionHeader = ({ iconClass, title }: { iconClass: string, title: string }) => (
    <Heading.h4>
        <Flex>
            <Box width="20%">
                <Text textAlign="center">
                    <i className={iconClass} />
                </Text>
            </Box>
            <Box width="80%">
                {title}
            </Box>
        </Flex>
    </Heading.h4>
)

const ContributorItem = (props: { contributor: Contributor }) => {
    const { contributor } = props;
    if (
        !blankOrUndefined(contributor.affiliation) ||
        !blankOrUndefined(contributor.gnd) ||
        !blankOrUndefined(contributor.orcId)
    ) {
        return (
            <Dropdown>
                <Box width="auto">
                    <a href="#" onClick={e => e.preventDefault()}>
                        <i className="fas fa-user" /><TextSpan ml="0.5em">{contributor.name}</TextSpan>
                    </a>
                </Box>
                <DropdownContent width="180px" colorOnHover={false}>
                    <>
                        {!blankOrUndefined(contributor.affiliation) ?
                            <Box><b>Affiliation:</b> {contributor.affiliation}</Box> : null
                        }
                        {!blankOrUndefined(contributor.gnd) ?
                            <Box><b>GND:</b> {contributor.gnd}</Box> : null
                        }
                        {!blankOrUndefined(contributor.orcId) ?
                            <Box>
                                <b>ORCID:</b>
                                {" "}
                                <a href={`https://orcid.org/${contributor.orcId}`} target="_blank" rel="noopener">
                                    {contributor.orcId}
                                </a>
                            </Box> : null
                        }
                    </>
                </DropdownContent>
            </Dropdown>)
    } else {
        return (<Box><i className="user" />{contributor.name}</Box>);
    }
};

interface ManagedViewState {
    metadata?: ProjectMetadata
    canEdit?: boolean
    errorMessage?: string
}

export class ManagedView extends React.Component<any, ManagedViewState> {
    constructor(props: any) {
        super(props);
        this.state = {}
    }

    // TODO This is not the correct place to do this!
    componentDidMount() {
        const urlPath = filePathFromProps(this.props as RouterLocationProps);
        if (!!this.state.metadata) return;
        if (!urlPath) {
            console.warn("TODO Not found");
            return;
        }

        getByPath(urlPath)
            .then(it => this.setState(() => ({ metadata: handleNullArrays(it.metadata), canEdit: it.canEdit })))
            .catch(() => console.warn("TODO something went wrong"));
    }

    render() {
        if (!this.state.metadata) {
            return <LoadingIcon size={18} />;
        } else {
            return <View canEdit={this.state.canEdit} metadata={this.state.metadata} />;
        }
    }
}

// TODO find more elegant solution
const handleNullArrays = (metadata: ProjectMetadata): ProjectMetadata => {
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
    return <a href={`https://doi.org/${identifier}`} target="_blank" rel="noopener">
        <Stamp mb="0.4em" borderColor="lightGray" fullWidth>
            {identifier}
        </Stamp>
    </a>;
}

const PotentialDOIBadge = (props: { identifier: string }) => {
    if (isIdentifierDOI(props.identifier)) {
        return <DOIBadge identifier={props.identifier} />;
    }
    return <Stamp borderColor="lightGray" mb="0.4em" fullWidth>{props.identifier}</Stamp>;
};