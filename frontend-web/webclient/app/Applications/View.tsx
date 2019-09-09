import {FullAppInfo, WithAppInvocation, WithAppMetadata} from "Applications";
import {AppToolLogo} from "Applications/AppToolLogo";
import {ReduxObject} from "DefaultObjects";
import {LoadableContent, loadingEvent} from "LoadableContent";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {updatePageTitle, UpdatePageTitleAction} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {Page} from "Types";
import {
    ActionButton,
    Box,
    ExternalLink,
    Image,
    Link,
    Markdown,
    OutlineButton,
    VerticalButtonGroup
} from "ui-components";
import ContainerForText from "ui-components/ContainerForText";
import * as Heading from "ui-components/Heading";
import {TextSpan} from "ui-components/Text";
import {dateToString} from "Utilities/DateUtilities";
import {capitalized} from "UtilityFunctions";
import {ApplicationCardContainer, SlimApplicationCard} from "./Card";
import * as Pages from "./Pages";
import * as Actions from "./Redux/ViewActions";
import * as ViewObject from "./Redux/ViewObject";

interface MainContentProps {
    onFavorite?: () => void;
    application: FullAppInfo;
    favorite?: LoadableContent<void>;
}

interface OperationProps {
    fetchApp: (name: string, version: string) => void;
    onFavorite: (name: string, version: string) => void;
}

type StateProps = ViewObject.Type;

interface OwnProps {
    match: any;
}

type ViewProps = OperationProps & StateProps & OwnProps;

function View(props: ViewProps) {

    React.useEffect(() => {
        fetchApp();
    }, []);

    const {appName, appVersion} = props.match.params;

    React.useEffect(() => {
        if (!props.application.loading && props.application.content) {
            const {name, version} = props.application.content.metadata;
            if (appName !== name || appVersion !== version)
                fetchApp();
        }
    });

    function fetchApp() {
        const {params} = props.match;
        props.fetchApp(params.appName, params.appVersion);
    }

    const {previous} = props;
    const application = props.application.content;
    if (previous.content) {
        previous.content.items = previous.content.items.filter(
            ({metadata}) => metadata.version !== appVersion
        );
    }
    return (
        <LoadableMainContainer
            loadable={props.application}
            main={
                <ContainerForText>
                    <Box m={16} />
                    <AppHeader application={application!} />
                    <Content
                        application={application!}
                        previousVersions={props.previous.content} />
                </ContainerForText>
            }

            sidebar={
                <Sidebar
                    application={application!}
                    onFavorite={() => props.onFavorite(appName, appVersion)}
                    favorite={props.favorite} />
            }
        />
    );
}

const AppHeaderBase = styled.div`
    display: flex;
    flex-direction: row;

    & > ${Image} {
        //width: 128px;
        //height: 128px;
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

export const AppHeader: React.FunctionComponent<MainContentProps & {slim?: boolean}> = props => {
    const isSlim = props.slim === true;
    const size = isSlim ? "32px" : "128px";
    return (
        <AppHeaderBase>
            <Box mr={16} >
                <AppToolLogo type={"APPLICATION"} name={props.application.metadata.name} size={size} />
            </Box>
            <AppHeaderDetails>
                {isSlim ?
                    <Heading.h3>
                        {props.application.metadata.title} <small>({props.application.metadata.version})</small>
                    </Heading.h3> :
                    <>
                        <Heading.h2>{props.application.metadata.title}</Heading.h2>
                        <Heading.h3>v{props.application.metadata.version}</Heading.h3>
                        <TextSpan>{props.application.metadata.authors.join(", ")}</TextSpan>
                        <Heading.h6>
                            <Tags tags={props.application.tags} />
                        </Heading.h6>
                    </>
                }
            </AppHeaderDetails>
        </AppHeaderBase>
    );
};

const Sidebar: React.FunctionComponent<MainContentProps> = props => (
    <VerticalButtonGroup>
        <ActionButton
            fullWidth
            onClick={() => {if (!!props.onFavorite) props.onFavorite();}}
            loadable={props.favorite as LoadableContent}
            color="blue">
            {props.application.favorite ? "Remove from favorites" : "Add to favorites"}
        </ActionButton>

        <Link to={Pages.runApplication(props.application.metadata)}>
            <OutlineButton fullWidth color={"blue"}>Run Application</OutlineButton>
        </Link>
        {!props.application.metadata.website ? null :
            <ExternalLink href={props.application.metadata.website}>
                <OutlineButton fullWidth color={"blue"}>Website</OutlineButton>
            </ExternalLink>
        }
    </VerticalButtonGroup>
);

const AppSection = styled(Box)`
    margin-bottom: 16px;
`;

function Content(props: MainContentProps & {previousVersions?: Page<FullAppInfo>}): JSX.Element {
    return (
        <>
            <AppSection>
                <Markdown
                    unwrapDisallowed
                    source={props.application.metadata.description}
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

const PreviousVersions: React.FunctionComponent<{previousVersions?: Page<FullAppInfo>}> = props => (
    <>
        {!props.previousVersions ? null :
            (!props.previousVersions.items.length ? null :
                <div>
                    <Heading.h4>Other Versions</Heading.h4>
                    <ApplicationCardContainer>
                        {props.previousVersions.items.map((it, idx) => (
                            <SlimApplicationCard app={it} key={idx} tags={it.tags} />
                        ))}
                    </ApplicationCardContainer>
                </div>
            )
        }
    </>
);

export const TagStyle = styled(Link)`
    background-color: ${({theme}) => theme.colors.darkGray};
    color: #ebeff3;
    &:hover { color: #ebeff3;}
    text-decoration: none;
    text-transform: Uppercase;
    padding: 0px 10px 0px 10px;
    margin-right: 4px;
    border-radius: 3px;
`;


const TagBase = styled.div`
    display: flex;
    flex-direction: row;
`;

function Tags({tags}: {tags: string[]}) {
    if (!tags) return null;

    return <div>
        <TagBase>
            {
                tags.map(tag => (
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

export const pad = (value: string | number, length: number) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const InfoAttributes = styled.div`
    display: flex;
    flex-direction: row;
`;

function Information({application}: {application: WithAppMetadata & WithAppInvocation}) {
    const time = application.invocation.tool.tool.description.defaultTimeAllocation;
    const timeString = time ? `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}` : "";
    const backend = application.invocation.tool.tool.description.backend;
    const license = application.invocation.tool.tool.description.license;
    return <>
        <Heading.h4>Information</Heading.h4>

        <InfoAttributes>
            <InfoAttribute
                name="Release Date"
                value={dateToString(application.invocation.tool.tool.createdAt)} />

            <InfoAttribute
                name="Default Time Allocation"
                value={timeString} />

            <InfoAttribute
                name="Default Nodes"
                value={`${application.invocation.tool.tool.description.defaultNumberOfNodes}`} />

            <InfoAttribute
                name="Container Type"
                value={capitalized(backend)} />

            <InfoAttribute
                name="License"
                value={license} />
        </InfoAttributes>
    </>;
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | UpdatePageTitleAction>): OperationProps => ({
    fetchApp: async (name: string, version: string) => {
        dispatch(updatePageTitle(`${name} v${version}`));

        const loadApplications = async () => {
            dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
            dispatch(await Actions.fetchApplication(name, version));
        };

        const loadPrevious = async () => {
            dispatch({type: Actions.Tag.RECEIVE_PREVIOUS, payload: loadingEvent(true)});
            dispatch(await Actions.fetchPreviousVersions(name));
        };

        await Promise.all([loadApplications(), loadPrevious()]);
    },

    onFavorite: async (name: string, version: string) => {
        dispatch({type: Actions.Tag.RECEIVE_FAVORITE, payload: loadingEvent(true)});
        dispatch(await Actions.favoriteApplication(name, version));
    }
});

const mapStateToProps = (state: ReduxObject): StateProps => ({
    ...state.applicationView
});

export default connect(mapStateToProps, mapDispatchToProps)(View);
