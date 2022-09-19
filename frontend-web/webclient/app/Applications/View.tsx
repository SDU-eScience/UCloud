import {AppToolLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import styled from "styled-components";
import {Box, Flex, Link, Tooltip} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {EllipsedText, TextSpan} from "@/ui-components/Text";
import {dateToString} from "@/Utilities/DateUtilities";
import {capitalized} from "@/UtilityFunctions";
import {Tag} from "./Card";
import * as Pages from "./Pages";
import {useHistory} from "react-router";
import * as UCloud from "@/UCloud";
import {FavoriteToggle} from "@/Applications/FavoriteToggle";
import {compute} from "@/UCloud";
import Application = compute.Application;
import ClickableDropdown from "@/ui-components/ClickableDropdown";

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
                                trigger={<TextSpan>{props.application.metadata.version}</TextSpan>}
                                chevron
                            >
                                {props.allVersions.map(it => <div key={it.metadata.version} onClick={() => history.push(Pages.runApplication(it.metadata))}>{it.metadata.version}</div>)}
                            </ClickableDropdown>
                            {newest && newest.metadata.version !== props.application.metadata.version ?
                                <Tooltip trigger={
                                    <TriggerDiv onClick={e => {
                                        e.preventDefault();
                                        history.push(Pages.runApplication(newest.metadata));
                                    }}>
                                        New version available.
                                    </TriggerDiv>
                                }>
                                    <div onClick={e => e.stopPropagation()}>
                                        You are not using the newest version of the app.<br />
                                        Click to use the newest version.
                                    </div>
                                </Tooltip> : null}
                        </Flex>
                    </>
                ) : (
                    <>
                        <Heading.h2>{props.application.metadata.title}<FavoriteToggle application={props.application} /></Heading.h2>
                        <Heading.h3>{props.application.metadata.version}</Heading.h3>
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
    padding-left: 12px;
    padding-right: 12px;
    text-align: center;
    color: var(--white);
    background-color: var(--blue);
    border-radius: 20px;
    cursor: pointer;
`;

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

export const Information: React.FunctionComponent<{application: Application; simple?: boolean;}> = ({application, simple}) => {
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

                {simple ? null : <>
                    <InfoAttribute
                        name="Default Time Allocation"
                        value={timeString}
                    />

                    <InfoAttribute
                        name="Default Nodes"
                        value={`${tool.description.defaultNumberOfNodes}`}
                    />
                </>}

                <InfoAttribute
                    name="Type"
                    value={backendTitle(backend)}
                />

                <InfoAttribute
                    name="License"
                    value={license ? license : "Unknown"}
                />
            </InfoAttributes>
        </>
    );
}

function backendTitle(backend: string): string {
    switch (backend) {
        case "SINGULARITY":
        case "DOCKER":
            return "Container";

        case "VIRTUAL_MACHINE":
            return "Virtual machine";

        case "NATIVE":
            return "Generic";

        default:
            return capitalized(backend);
    }
}
