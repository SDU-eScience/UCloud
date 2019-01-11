import * as React from "react";
import styled from "styled-components";

import { Application } from "Applications";
import { Page } from "Types";
import { loadingEvent, LoadableContent } from "LoadableContent";

import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import { connect } from "react-redux";
import * as Actions from "./Redux/ViewActions";
import * as ViewObject from "./Redux/ViewObject";
import { updatePageTitle, UpdatePageTitleAction } from "Navigation/Redux/StatusActions";

import { VerticalButtonGroup, Box, Image, OutlineButton, ActionButton, Link, ExternalLink, Markdown } from "ui-components"
import { TextSpan } from "ui-components/Text";
import * as Heading from "ui-components/Heading"
import ContainerForText from "ui-components/ContainerForText";

import { dateToString } from "Utilities/DateUtilities";
import { toLowerCaseAndCapitalize } from "UtilityFunctions"
import { LoadingMainContainer } from "MainContainer/MainContainer";
import { ApplicationCardContainer, SlimApplicationCard } from "./Card";
import { AppLogo, hashF } from "./Card";

import * as Pages from "./Pages";

interface MainContentProps {
    onFavorite?: () => void
    application: Application
    favorite?: LoadableContent<void>
}

interface OperationProps {
    onInit: (name: string, version: string) => void
    onFavorite: (name: string, version: string) => void
}

type StateProps = ViewObject.Type;

interface OwnProps {
    match: any;
}

type ViewProps = OperationProps & StateProps & OwnProps;

class View extends React.Component<ViewProps> {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        const { appName, appVersion } = this.props.match.params;
        this.props.onInit(appName, appVersion);
    }

    render() {
        const { appName, appVersion } = this.props.match.params;
        const { previous} = this.props;
        const application = this.props.application.content;
        if (previous.content) {
            previous.content.items = previous.content.items.filter(
                ({ description }) => description.info.version !== appVersion
            );
        }
        return (
            <LoadingMainContainer
                loadable={this.props.application}
                main={
                    <ContainerForText>
                        <Box m={16} />
                        <AppHeader application={application!} />
                        <Content
                            application={application!}
                            previousVersions={this.props.previous.content} />
                    </ContainerForText>
                }

                sidebar={
                    <Sidebar
                        application={application!}
                        onFavorite={() => this.props.onFavorite(appName, appVersion)}
                        favorite={this.props.favorite} />
                }
            />
        );
    }
}

const AppHeaderBase = styled.div`
    display: flex;
    flex-direction: row;

    & > ${Image} {
        width: 128px;
        height: 128px;
        border-radius: 8px;
        object-fit: cover;
        margin-right: 16px;
    }
`;

const AppHeaderDetails = styled.div`
    display: flex;
    flex-direction: column;

    & > h1, h2 {
        margin: 0;
    }
`;

export const AppHeader: React.StatelessComponent<MainContentProps> = props => (
    <AppHeaderBase>
        {/* <Image src={props.application.imageUrl} /> */}
        <Box mr={16} >
            <AppLogo size={"128px"} hash={hashF(props.application.description.title)} />
        </Box>
        <AppHeaderDetails>
            <Heading.h2>{props.application.description.title}</Heading.h2>
            <Heading.h3>v{props.application.description.info.version}</Heading.h3>
            <TextSpan>{props.application.description.authors.join(", ")}</TextSpan>
            <Tags tags={props.application.description.tags} />
        </AppHeaderDetails>
    </AppHeaderBase>
);

const Sidebar: React.StatelessComponent<MainContentProps> = props => (
    <>
        <VerticalButtonGroup>
            <ActionButton
                fullWidth
                onClick={() => { if (!!props.onFavorite) props.onFavorite() }}
                loadable={props.favorite as LoadableContent}
                color={"blue"}>
                {props.application.favorite ? "Remove from My Apps" : "Add to My Apps"}
            </ActionButton>

            <Link to={Pages.runApplication(props.application)}>
                <OutlineButton fullWidth color={"blue"}>Run Application</OutlineButton>
            </Link>

            {!props.application.description.website ? null :
                <ExternalLink href={props.application.description.website}>
                    <OutlineButton fullWidth color={"blue"}>Website</OutlineButton>
                </ExternalLink>
            }
        </VerticalButtonGroup>
    </>
);

const AppSection = styled(Box)`
    margin-bottom: 16px;
`;

function Content(props: MainContentProps & { previousVersions?: Page<Application> }): JSX.Element {
    return (
        <>
            <AppSection>
                <Markdown
                    unwrapDisallowed
                    source={props.application.description.description}
                    disallowedTypes={[
                        "image",
                        "heading"
                    ]}
                />
            </AppSection>

            <AppSection>
                <PreviousVersions previousVersions={props.previousVersions} />
            </AppSection>

            <AppSection>
                <Information application={props.application} />
            </AppSection>
        </>
    );
}

const PreviousVersions: React.StatelessComponent<{ previousVersions?: Page<Application> }> = props => {
    return (
        <>
            <Heading.h4>Previous Versions</Heading.h4>
            {!props.previousVersions ? null :
                <ApplicationCardContainer>
                    {props.previousVersions.items.map((it, idx) => (
                        <SlimApplicationCard linkToRun app={it} key={idx} />
                    ))}
                </ApplicationCardContainer>
            }
        </>
    )
};

const ButtonGroup = styled.div`
    display: flex;
    flex-direction: column;

    & > * {
        width: 100%;
        margin-bottom: 8px;
    }
`;

const TagStyle = styled(Link)`
    text-decoration: none;
    padding: 6px;
    margin-right: 3px;
    border: 1px solid ${props => props.theme.colors.gray};
    border-radius: 5px;
`;

const TagBase = styled.div`
    display: flex;
    flex-direction: row;
`;

function Tags({ tags }: { tags: string[] }) {
    if (!tags) return null;

    return <div>
        <TagBase>
            {
                tags.slice(0, 5).map(tag => (
                    <TagStyle to={Pages.browseByTag(tag)}>{tag}</TagStyle>
                ))
            }
        </TagBase>
    </div>;
}

function InfoAttribute(props: {
    name: string,
    value?: string,
    children?: JSX.Element
}) {
    return <Box mb={8} mr={32}>
        <Heading.h5>{props.name}</Heading.h5>
        {props.value}
        {props.children}
    </Box>;
}

const pad = (value, length) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const InfoAttributes = styled.div`
    display: flex;
    flex-direction: row;
`;

function Information({ application }: { application: Application }) {
    const time = application.tool.description.defaultMaxTime;
    const timeString = time ? `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}` : "";

    return <>
        <Heading.h4>Information</Heading.h4>

        <InfoAttributes>
            <InfoAttribute
                name="Release Date"
                value={dateToString(application.createdAt)} />

            <InfoAttribute
                name="Default Time Allocation"
                value={timeString} />

            <InfoAttribute
                name="Default Nodes"
                value={`${application.tool.description.defaultNumberOfNodes}`} />

            <InfoAttribute
                name="Container Type"
                value={toLowerCaseAndCapitalize(application.tool.description.backend)} />
        </InfoAttributes>
    </>;
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | UpdatePageTitleAction>): OperationProps => ({
    onInit: async (name: string, version: string) => {
        dispatch(updatePageTitle(`${name} v${version}`));

        const loadApplications = async () => {
            dispatch({ type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true) });
            dispatch(await Actions.fetchApplication(name, version));
        };

        const loadPrevious = async () => {
            dispatch({ type: Actions.Tag.RECEIVE_PREVIOUS, payload: loadingEvent(true) });
            dispatch(await Actions.fetchPreviousVersions(name));
        };

        await Promise.all([loadApplications(), loadPrevious()]);
    },

    onFavorite: async (name: string, version: string) => {
        dispatch({ type: Actions.Tag.RECEIVE_FAVORITE, payload: loadingEvent(true) });
        dispatch(await Actions.favoriteApplication(name, version));
    }
})

const mapStateToProps = (state: ReduxObject): StateProps => {
    return { ...state.applicationView };
}

export default connect(mapStateToProps, mapDispatchToProps)(View);
