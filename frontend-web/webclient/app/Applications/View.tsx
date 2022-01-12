import {AppToolLogo} from "@/Applications/AppToolLogo";
import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import styled from "styled-components";
import {
    Box,
    Flex,
    ExternalLink,
    Link,
    Markdown,
    VerticalButtonGroup,
    Button,
    Icon,
    Tooltip
} from "@/ui-components";
import ContainerForText from "@/ui-components/ContainerForText";
import * as Heading from "@/ui-components/Heading";
import {EllipsedText, TextSpan} from "@/ui-components/Text";
import {dateToString} from "@/Utilities/DateUtilities";
import {capitalized} from "@/UtilityFunctions";
import {ApplicationCardContainer, SlimApplicationCard, Tag} from "./Card";
import * as Pages from "./Pages";
import {useHistory, useRouteMatch} from "react-router";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import * as UCloud from "@/UCloud";
import {FavoriteToggle} from "@/Applications/FavoriteToggle";
import {useEffect} from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {compute} from "@/UCloud";
import Application = compute.Application;
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useResourceSearch} from "@/Resource/Search";
import {ApiLike} from "./Overview";
import ClickableDropdown from "@/ui-components/ClickableDropdown";

const View: React.FunctionComponent = () => {
    const {appName, appVersion} = useRouteMatch<{appName: string, appVersion: string}>().params;
    useSidebarPage(SidebarPages.AppStore);
    const [applicationResp, fetchApplication] = useCloudAPI<UCloud.compute.ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );
    const [previousResp, fetchPrevious] = useCloudAPI<UCloud.Page<UCloud.compute.ApplicationSummaryWithFavorite> | null>(
        {noop: true},
        null
    );

    useEffect(() => {
        fetchApplication(UCloud.compute.apps.findByNameAndVersion({appName, appVersion}))
        fetchPrevious(UCloud.compute.apps.findByName({appName}));
    }, [appName, appVersion]);

    useResourceSearch(ApiLike);

    useTitle(applicationResp.data == null ?
        `${appName}, ${appVersion}` :
        `${applicationResp.data.metadata.title}, ${applicationResp.data.metadata.version}`);


    const application = applicationResp.data;
    const previous = previousResp.data;

    if (application === null || previous === null) return <MainContainer main={<HexSpin size={36} />} />;

    return (
        <MainContainer
            header={<AppHeader application={application!} allVersions={previous.items} />}
            headerSize={160}
            main={(
                <ContainerForText left>
                    <Content
                        application={application!}
                        previous={previous.items.filter(it => it.metadata.version !== application.metadata.version)}
                    />
                </ContainerForText>
            )}

            sidebar={(
                <Sidebar
                    application={application!}
                />
            )}
        />
    );
}

export const AppHeader: React.FunctionComponent<{
    application: UCloud.compute.ApplicationWithFavoriteAndTags;
    slim?: boolean;
    allVersions: UCloud.compute.ApplicationSummaryWithFavorite[];
}> = props => {
    const isSlim = props.slim === true;
    const size = isSlim ? "64px" : "128px";
    /* Results of `findByName` are ordered by apps `createdAt` field in descending order, so this should be correct. */
    const newest: UCloud.compute.ApplicationSummaryWithFavorite | undefined = props.allVersions[0];
    const history = useHistory();

    return (
        <Flex flexDirection={"row"} ml={["0px", "0px", "0px", "0px", "0px", "50px"]}  >
            <Box mr={16}>
                <AppToolLogo type={"APPLICATION"} name={props.application.metadata.name} size={size} />
            </Box>
            {/* minWidth=0 is required for the ellipsed text children to work */}
            <Flex flexDirection={"column"} minWidth={0}>
                {isSlim ? (
                    <>
                        <Heading.h3>{props.application.metadata.title}<FavoriteToggle application={props.application} /></Heading.h3>
                        <Flex>
                            <ClickableDropdown
                                trigger={<TextSpan>v{props.application.metadata.version}</TextSpan>}
                                chevron
                            >
                                {props.allVersions.map(it => <div key={it.metadata.version} onClick={() => history.push(Pages.runApplication(it.metadata))}>{it.metadata.version}</div>)}
                            </ClickableDropdown>
                            {newest && newest.metadata.version !== props.application.metadata.version ?
                                <Tooltip trigger={<TriggerDiv onClick={e => {
                                    e.preventDefault();
                                    history.push(Pages.runApplication(newest.metadata));
                                }}>!</TriggerDiv>}>
                                    <div onClick={e => {
                                        e.stopPropagation();
                                    }}>
                                        You are not using the newest version of the app.<br />
                                        Click to use the newest version.
                                    </div>
                                </Tooltip> : null}
                        </Flex>
                    </>
                ) : (
                    <>
                        <Heading.h2>{props.application.metadata.title}<FavoriteToggle application={props.application} /></Heading.h2>
                        <Heading.h3>v{props.application.metadata.version}</Heading.h3>
                        <EllipsedText>by {props.application.metadata.authors.join(", ")}</EllipsedText>
                        <Tags tags={props.application.tags} />
                    </>
                )}
            </Flex>
        </Flex>
    );
};

const TriggerDiv = styled.div`
    margin-left: 4px; 
    text-align: center;
    color: var(--white);
    background-color: var(--blue);
    border-radius: 20px;
    width: 25px;
    cursor: pointer;
`;

const Sidebar: React.FunctionComponent<{application: UCloud.compute.ApplicationWithFavoriteAndTags}> = props => (
    <VerticalButtonGroup>
        {!props.application.metadata.website ? null : (
            <ExternalLink href={props.application.metadata.website}>
                <Button fullWidth color={"blue"}>Documentation</Button>
            </ExternalLink>
        )}

        <Link to={Pages.runApplication(props.application.metadata)}>
            <Button fullWidth color={"blue"}>Run Application</Button>
        </Link>
    </VerticalButtonGroup>
);

const AppSection = styled(Box)`
    margin-bottom: 16px;
`;

const Content: React.FunctionComponent<{
    application: UCloud.compute.ApplicationWithFavoriteAndTags,
    previous: UCloud.compute.ApplicationSummaryWithFavorite[]
}> = props => (
    <>
        <AppSection>
            <Markdown
                unwrapDisallowed
                disallowedElements={[
                    "image",
                    "heading"
                ]}
            >
                {props.application.metadata.description}
            </Markdown>
        </AppSection>

        <AppSection>
            <Information application={props.application} />
        </AppSection>

        <AppSection>
            {!props.previous ? null :
                (!props.previous.length ? null : (
                    <div>
                        <Heading.h4>Other Versions</Heading.h4>
                        <ApplicationCardContainer>
                            {props.previous.map((it, idx) => (
                                <SlimApplicationCard app={it} key={idx} tags={it.tags} />
                            ))}
                        </ApplicationCardContainer>
                    </div>
                ))
            }
        </AppSection>
    </>
);

function Tags({tags}: {tags: string[]}): JSX.Element | null {
    if (!tags) return null;

    return (
        <div>
            <Flex flexDirection="row">
                {
                    tags.map(tag => (
                        <Link key={tag} to={Pages.browseByTag(tag)}><Tag label={tag} /> </Link>
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

const Information: React.FunctionComponent<{application: Application}> = ({application}) => {
    const tool = application?.invocation?.tool?.tool;
    if (!tool) return null;
    const time = tool?.description?.defaultTimeAllocation;
    const timeString = time ? `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}` : "";
    const backend = tool.description.backend;
    const license = tool.description.license;
    return (
        <>
            <InfoAttributes>
                <InfoAttribute
                    name="Release Date"
                    value={dateToString(tool.createdAt)}
                />

                <InfoAttribute
                    name="Default Time Allocation"
                    value={timeString}
                />

                <InfoAttribute
                    name="Default Nodes"
                    value={`${tool.description.defaultNumberOfNodes}`}
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

export default View;
