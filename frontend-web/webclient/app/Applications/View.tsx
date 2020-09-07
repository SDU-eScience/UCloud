import {FullAppInfo, WithAppInvocation, WithAppMetadata} from "Applications";
import {AppToolLogo} from "Applications/AppToolLogo";
import {LoadableContent, loadingEvent} from "LoadableContent";
import {LoadableMainContainer} from "MainContainer/MainContainer";
import {updatePageTitle, UpdatePageTitleAction} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import styled from "styled-components";
import {
    ActionButton,
    Box,
    Flex,
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
import {ApplicationCardContainer, SlimApplicationCard, Tag} from "./Card";
import * as Pages from "./Pages";
import * as Actions from "./Redux/ViewActions";
import * as ViewObject from "./Redux/ViewObject";
import {match} from "react-router";

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
    match: match<{appName: string; appVersion: string}>;
}

type ViewProps = OperationProps & StateProps & OwnProps;

function View(props: ViewProps): JSX.Element {

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

    function fetchApp(): void {
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
            main={(
                <ContainerForText left>
                    <Box m={16} />
                    <AppHeader application={application!} />
                    <Content
                        application={application!}
                        previousVersions={props.previous.content}
                    />
                </ContainerForText>
            )}

            sidebar={(
                <Sidebar
                    application={application!}
                    onFavorite={() => props.onFavorite(appName, appVersion)}
                    favorite={props.favorite}
                />
            )}
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
                {isSlim ? (
                    <Heading.h3>
                        {props.application.metadata.title} <small>({props.application.metadata.version})</small>
                    </Heading.h3>
                ) : (
                        <>
                            <Heading.h2>{props.application.metadata.title}</Heading.h2>
                            <Heading.h3>v{props.application.metadata.version}</Heading.h3>
                            <TextSpan>{props.application.metadata.authors.join(", ")}</TextSpan>
                            <Tags tags={props.application.tags} />
                        </>
                    )}
            </AppHeaderDetails>
        </AppHeaderBase>
    );
};

const Sidebar: React.FunctionComponent<MainContentProps> = props => (
    <VerticalButtonGroup>
        <ActionButton
            fullWidth
            onClick={() => props.onFavorite?.()}
            loadable={props.favorite as LoadableContent}
            color="blue"
        >
            {props.application.favorite ? "Remove from favorites" : "Add to favorites"}
        </ActionButton>

        {!props.application.metadata.website ? null : (
            <ExternalLink href={props.application.metadata.website}>
                <OutlineButton fullWidth color={"blue"}>Documentation</OutlineButton>
            </ExternalLink>
        )}

        <Link to={Pages.runApplication(props.application.metadata)}>
            <OutlineButton fullWidth color={"blue"}>Run Application</OutlineButton>
        </Link>
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
            (!props.previousVersions.items.length ? null : (
                <div>
                    <Heading.h4>Other Versions</Heading.h4>
                    <ApplicationCardContainer>
                        {props.previousVersions.items.map((it, idx) => (
                            <SlimApplicationCard app={it} key={idx} tags={it.tags} />
                        ))}
                    </ApplicationCardContainer>
                </div>
            ))
        }
    </>
);

function Tags({tags}: {tags: string[]}): JSX.Element | null {
    if (!tags) return null;

    return (
        <div>
            <Flex flexDirection="row" >
                {
                    tags.map(tag => (
                        <Link key={tag} to={Pages.browseByTag(tag)}><Tag label={tag}/> </Link>
                    ))
                }
            </Flex>
        </div>
    );
}

function InfoAttribute(props: {
    name: string;
    value?: string;
    children?: JSX.Element;
}): JSX.Element {
    return (
        <Box mb={8} mr={32}>
            <Heading.h5>{props.name}</Heading.h5>
            {props.value}
            {props.children}
        </Box>
    );
}

export const pad = (value: string | number, length: number): string | number =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const InfoAttributes = styled.div`
    display: flex;
    flex-direction: row;
`;

function Information({application}: {application: WithAppMetadata & WithAppInvocation}): JSX.Element {
    const time = application.invocation.tool.tool.description.defaultTimeAllocation;
    const timeString = time ? `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}` : "";
    const backend = application.invocation.tool.tool.description.backend;
    const license = application.invocation.tool.tool.description.license;
    return (
        <>
            <Heading.h4>Information</Heading.h4>

            <InfoAttributes>
                <InfoAttribute
                    name="Release Date"
                    value={dateToString(application.invocation.tool.tool.createdAt)}
                />

                <InfoAttribute
                    name="Default Time Allocation"
                    value={timeString}
                />

                <InfoAttribute
                    name="Default Nodes"
                    value={`${application.invocation.tool.tool.description.defaultNumberOfNodes}`}
                />

                <InfoAttribute
                    name="Container Type"
                    value={capitalized(backend)}
                />

                <InfoAttribute
                    name="License"
                    value={license}
                />
            </InfoAttributes>
        </>
    );
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | UpdatePageTitleAction>): OperationProps => ({
    fetchApp: async (name: string, version: string) => {
        dispatch(updatePageTitle(`${name} v${version}`));

        const loadApplications = async (): Promise<void> => {
            dispatch({type: Actions.Tag.RECEIVE_APP, payload: loadingEvent(true)});
            dispatch(await Actions.fetchApplication(name, version));
        };

        const loadPrevious = async (): Promise<void> => {
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

const mapStateToProps = (state: ReduxObject): StateProps => state.applicationView;

export default connect(mapStateToProps, mapDispatchToProps)(View);
