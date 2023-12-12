import {AppToolLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Box, Flex, Icon, Link, Tooltip} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Text, {EllipsedText, TextSpan} from "@/ui-components/Text";
import {capitalized} from "@/UtilityFunctions";
import {Tag} from "./Card";
import * as Pages from "./Pages";
import {useNavigate} from "react-router";
import * as UCloud from "@/UCloud";
import {FavoriteToggle} from "@/Applications/FavoriteToggle";
import {compute} from "@/UCloud";
import Application = compute.Application;
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

export const AppHeader: React.FunctionComponent<{
    application: UCloud.compute.ApplicationWithFavoriteAndTags;
    slim?: boolean;
    allVersions: UCloud.compute.ApplicationSummaryWithFavorite[];
    flavors: UCloud.compute.ApplicationSummaryWithFavorite[];
    title: string;
}> = props => {
    const isSlim = props.slim === true;
    const size = isSlim ? "64px" : "128px";
    /* Results of `findByName` are ordered by apps `createdAt` field in descending order, so this should be correct. */
    const newest: UCloud.compute.ApplicationSummaryWithFavorite | undefined = props.allVersions[0];
    const navigate = useNavigate();
    const close = React.useRef(() => void 0);

    return (
        <Flex flexDirection={"row"}>
            <Box mr={16} mt="auto">
                <AppToolLogo type={"APPLICATION"} name={props.application.metadata.name} size={size} />
            </Box>
            {/* minWidth=0 is required for the ellipsed text children to work */}
            <Flex flexDirection={"column"} minWidth={0}>
                {isSlim ? (
                    <>
                        <Box>
                            <Flex>
                                <Text verticalAlign="center" alignItems="center" fontSize={30} mr="15px">
                                    {props.title}
                                </Text>
                                <Flex style={{alignSelf: "center"}}>
                                    {props.flavors.length <= 1 ? null :
                                        <ClickableDropdown
                                            closeFnRef={close}
                                            trigger={
                                                <Flex className={FlavorSelectorClass}>
                                                    {props.application.metadata.flavorName ?? props.application.metadata.title} <Icon ml="8px" name="chevronDownLight" size={12} />
                                                </Flex>
                                            }>
                                            {props.flavors.map(f =>
                                                <Box
                                                    cursor="pointer"
                                                    width="auto"
                                                    key={f.metadata.name}
                                                    onClick={() => {
                                                        close.current();
                                                        navigate(Pages.runApplication(f.metadata));
                                                    }}
                                                >
                                                    {f.metadata.flavorName ?? f.metadata.title}
                                                </Box>
                                            )}
                                        </ClickableDropdown>
                                    }
                                </Flex>
                            </Flex>
                        </Box>
                        <Flex>
                            <FavoriteToggle application={props.application} />
                            <ClickableDropdown
                                trigger={<TextSpan ml="8px">{props.application.metadata.version}</TextSpan>}
                                chevron
                            >
                                {props.allVersions.map(it => <div key={it.metadata.version} onClick={() => navigate(Pages.runApplication(it.metadata))}>{it.metadata.version}</div>)}
                            </ClickableDropdown>
                            {newest && newest.metadata.version !== props.application.metadata.version ?
                                <Tooltip tooltipContentWidth={390} trigger={
                                    <div className={TriggerDiv} onClick={e => {
                                        e.preventDefault();
                                        navigate(Pages.runApplication(newest.metadata));
                                    }}>
                                        New version available.
                                    </div>
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

const TriggerDiv = injectStyleSimple("trigger-div", `
    margin-left: 4px;
    padding-left: 12px;
    padding-right: 12px;
    text-align: center;
    color: var(--fixedWhite);
    background-color: var(--primary);
    border-radius: 20px;
    cursor: pointer;
`);

const FlavorSelectorClass = injectStyle("flavor-selector", k => `
    ${k} {
        height: 30px;
        border-radius: 16px;
        padding: 0px 15px;
        font-size: var(--buttonText);
        align-items: center;
        background-color: var(--primary);
        color: white;
        margin: auto 0px;
    }

    ${k}:hover {
        filter: brightness(115%);
    }
`);

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

export function pad(value: string | number, length: number): string | number {
    return (value.toString().length < length) ? pad("0" + value, length) : value;
}

function InfoAttributes({children}: React.PropsWithChildren): JSX.Element {
    return <Flex flexDirection="row">
        {children}
    </Flex>;
}

export const Information: React.FunctionComponent<{application: Application; simple?: boolean;}> = ({application, simple}) => {
    const tool = application?.invocation?.tool?.tool;
    if (!tool) return null;
    const time = tool?.description?.defaultTimeAllocation;
    const timeString = time ? `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}` : "";
    const backend = tool.description.backend;
    const license = tool.description.license;
    return null;
    /*return (
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
    );*/
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
