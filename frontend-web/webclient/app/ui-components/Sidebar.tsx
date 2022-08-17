import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {connect, useDispatch} from "react-redux";
import styled, {css} from "styled-components";
import {copyToClipboard, inDevEnvironment, joinToString, onDevSite, useFrameHidden} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import Box from "./Box";
import ExternalLink from "./ExternalLink";
import Flex, {FlexCProps} from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import RatingBadge from "./RatingBadge";
import RBox from "./RBox";
import Text, {EllipsedText} from "./Text";
import {ThemeColor} from "./theme";
import Tooltip from "./Tooltip";
import {useCallback, useEffect} from "react";
import {setActivePage} from "@/Navigation/Redux/StatusActions";
import {ProjectRole, useProjectId, UserInProject, viewProject} from "@/Project";
import {useGlobalCloudAPI} from "@/Authentication/DataHook";

const SidebarElementContainer = styled(Flex) <{hover?: boolean; active?: boolean}>`
    justify-content: left;
    flex-flow: row;
    align-items: center;

    & > ${Text} {
        white-space: nowrap;
    }
`;

// This is applied to SidebarContainer on small screens
const HideText = css`
${({theme}) => theme.mediaQueryLT.xl} {

    will-change: transform;
    transition: transform ${({theme}) => theme.timingFunctions.easeOut} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
    transform: translate(-122px,0); //122 = 190-68 (original - final width)

    & ${Icon},${RatingBadge} {
        will-change: transform;
        transition: transform ${({theme}) => theme.timingFunctions.easeOut} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
        transform: translate(122px,0); //inverse transformation; same transition function!
    }

    & ${SidebarElementContainer} > ${Text} {
        // transition: opacity ${({theme}) => theme.timingFunctions.easeOutQuit} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
        transition: opacity ${({theme}) => theme.timingFunctions.stepStart} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
        opacity: 0;
        will-change: opacity;
    }


    &:hover {
            transition: transform ${({theme}) => theme.timingFunctions.easeIn} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
            transform: translate(0,0);

            & ${Icon},${RatingBadge} {
                transition: transform ${({theme}) => theme.timingFunctions.easeIn} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
                transform: translate(0,0); //inverter transformation
            }

            ${SidebarElementContainer} > ${Text} {
                // transition: opacity ${({theme}) => theme.timingFunctions.easeInQuint} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
                transition: opacity ${({theme}) => theme.timingFunctions.stepEnd} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
                opacity: 1;
        }
    }
}
`;

const SidebarContainer = styled(Flex) <FlexCProps>`
    position: fixed;
    z-index: 80;
    top: 0;
    left: 0;
    padding-top: 48px;
    height: 100%;
    background-color: ${props => props.theme.colors.lightGray};
    // border-right: 1px solid ${props => props.theme.colors.borderGray};
    //background: linear-gradient(135deg, rgba(246,248,249,1) 0%,rgba(229,235,238,1) 69%,rgba(215,222,227,1) 71%,rgba(245,247,249,1) 100%);
    ${HideText}
`;

interface TextLabelProps {
    icon: IconName;
    children: React.ReactText | JSX.Element;
    ml?: string;
    height?: string;
    color?: ThemeColor;
    color2?: ThemeColor;
    iconSize?: string;
    textSize?: number;
    space?: string;
    hover?: boolean;
    title?: string;
}

export const SidebarTextLabel = ({
    icon, children, title, height = "30px", color = "iconColor", color2 = "iconColor2",
    iconSize = "24", space = "22px", textSize = 3, hover = true
}: TextLabelProps): JSX.Element => (
    <SidebarElementContainer title={title} height={height} ml="22px" hover={hover}>
        <Icon name={icon} color={color} color2={color2} size={iconSize} mr={space} />
        <Text fontSize={textSize}> {children} </Text>
    </SidebarElementContainer>
);

const SidebarLink = styled(Link) <{active?: boolean}>`
    ${props => props.active ?
        `&:not(:hover) > * > ${Text} {
            color: ${props.theme.colors.blue};
        }
        &:not(:hover) > * > ${Icon} {
            filter: saturate(500%);
        }
    ` : null}

    text-decoration: none;

    &:hover > ${Text}, &:hover > * > ${Icon} {
        filter: saturate(500%);
    }
`;

interface SidebarElement {
    icon: IconName;
    label: string;
    to: string;
    external?: boolean;
    activePage: SidebarPages;
}

const SidebarElement = ({icon, label, to, activePage}: SidebarElement): JSX.Element => (
    <SidebarLink to={to} active={enumToLabel(activePage) === label}>
        <SidebarTextLabel icon={icon}>{label}</SidebarTextLabel>
    </SidebarLink>
);

function enumToLabel(value: SidebarPages): string {
    switch (value) {
        case SidebarPages.Files:
            return "Files";
        case SidebarPages.Shares:
            return "Shares";
        case SidebarPages.Projects:
            return "Projects";
        case SidebarPages.AppStore:
            return "Apps";
        case SidebarPages.Runs:
            return "Runs";
        case SidebarPages.Publish:
            return "Publish";
        case SidebarPages.Activity:
            return "Activity";
        case SidebarPages.Admin:
            return "Admin";
        case SidebarPages.Resources:
            return "Resources";
        default:
            return "";
    }
}

const SidebarSpacer = (): JSX.Element => (<Box mt="12px" />);

const SidebarPushToBottom = styled.div`
    flex-grow: 1;
`;

interface MenuElement {icon: IconName; label: string; to: string | (() => string); show?: () => boolean}
interface SidebarMenuElements {
    items: MenuElement[];
    predicate: () => boolean;
}

export const sideBarMenuElements: {
    guest: SidebarMenuElements;
    general: SidebarMenuElements;
    auditing: SidebarMenuElements;
    admin: SidebarMenuElements;
} = {
    guest: {
        items: [
            {icon: "files", label: "Files", to: "/login"},
            {icon: "projects", label: "Projects", to: "/login"},
            {icon: "apps", label: "Apps", to: "/login"}
        ], predicate: () => !Client.isLoggedIn
    },
    general: {
        items: [
            {icon: "files", label: "Files", to: "/drives"},
            {icon: "projects", label: "Projects", to: "/projects", show: () => Client.hasActiveProject},
            {icon: "shareMenu", label: "Shares", to: "/shares/", show: () => !Client.hasActiveProject},
            {icon: "dashboard", label: "Resources", to: "/public-ips"},
            {icon: "appStore", label: "Apps", to: "/applications/overview"},
            {icon: "results", label: "Runs", to: "/jobs"}
        ], predicate: () => Client.isLoggedIn
    },
    auditing: {items: [], predicate: () => Client.isLoggedIn},
    admin: {items: [{icon: "admin", label: "Admin", to: "/admin"}], predicate: () => Client.userIsAdmin}
};

interface SidebarStateProps {
    page: SidebarPages;
    loggedIn: boolean;
    activeProject?: string;
}

interface SidebarProps extends SidebarStateProps {
    sideBarEntries?: typeof sideBarMenuElements;
}

const Sidebar = ({sideBarEntries = sideBarMenuElements, page, loggedIn}: SidebarProps): JSX.Element | null => {
    if (!loggedIn) return null;

    if (useFrameHidden()) return null;

    const projectId = useProjectId();
    const [projectDetails, fetchProjectDetails] = useGlobalCloudAPI<UserInProject>(
        "projectManagementDetails",
        {noop: true},
        {
            projectId: projectId ?? "",
            favorite: false,
            needsVerification: false,
            title: "",
            whoami: {username: Client.username ?? "", role: ProjectRole.USER},
            archived: false
        }
    );

    useEffect(() => {
        if (projectId) fetchProjectDetails(viewProject({id: projectId}));
    }, [projectId]);

    const projectPath = joinToString(
        [...(projectDetails.data.ancestorPath?.split("/")?.filter(it => it.length > 0) ?? []), projectDetails.data.title],
        "/"
    );
    const copyProjectPath = useCallback(() => {
        copyToClipboard({value: projectPath, message: "Project copied to clipboard!"});
    }, [projectPath]);

    const sidebar = Object.keys(sideBarEntries)
        .map(key => sideBarEntries[key])
        .filter(it => it.predicate());
    return (
        <SidebarContainer color="sidebar" flexDirection="column" width={190}>
            {sidebar.map((category, categoryIdx) => (
                <React.Fragment key={categoryIdx}>
                    {category.items.filter((it: MenuElement) => it?.show?.() ?? true).map(({icon, label, to}: MenuElement) => (
                        <React.Fragment key={label}>
                            <SidebarSpacer />
                            <SidebarElement
                                icon={icon}
                                activePage={page}
                                label={label}
                                to={typeof to === "function" ? to() : to}
                            />
                        </React.Fragment>
                    ))}
                </React.Fragment>
            ))}
            <SidebarPushToBottom />
            {/* Screen size indicator */}
            {inDevEnvironment() ? <Flex mb={"5px"} width={190} ml={19} justifyContent="left"><RBox /> </Flex> : null}
            {window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1" ? <>
                <SidebarTextLabel icon={"bug"} iconSize="1em" textSize={1} height={"25px"} hover={false} space={".5em"}>
                    <ExternalLink href="/debugger?hide-frame">
                        Open debugger
                    </ExternalLink>
                </SidebarTextLabel>
            </> : null}
            {!projectId ? null : <>
                <SidebarTextLabel icon={"projects"} height={"25px"} iconSize={"1em"} textSize={1} space={".5em"}>
                    <Tooltip
                        left="-50%"
                        top="1"
                        mb="35px"
                        trigger={(
                            <EllipsedText
                                cursor="pointer"
                                onClick={copyProjectPath}
                                width="140px"
                            >
                                {projectPath}
                            </EllipsedText>
                        )}
                    >
                        Click to copy to clipboard
                    </Tooltip>
                </SidebarTextLabel>
            </>}
            {!Client.isLoggedIn ? null : (<Box style={{zIndex: -1}}>
                <SidebarTextLabel
                    height="25px"
                    hover={false}
                    icon="id"
                    iconSize="1em"
                    textSize={1}
                    space=".5em"
                >
                    <Tooltip
                        left="-50%"
                        top="1"
                        mb="35px"
                        trigger={(
                            <EllipsedText
                                cursor="pointer"
                                onClick={copyUserName}
                                width="140px"
                            >
                                {Client.username}
                            </EllipsedText>
                        )}
                    >
                        Click to copy {Client.username} to clipboard
                    </Tooltip>
                </SidebarTextLabel>
            </Box>)}
            {!CONF.SITE_DOCUMENTATION_URL ? null : (
                <ExternalLink style={{zIndex: -2}} href={CONF.SITE_DOCUMENTATION_URL}>
                    <SidebarTextLabel height="25px" icon="docs" iconSize="1em" textSize={1} space=".5em">
                        {`${CONF.PRODUCT_NAME} Docs`}
                    </SidebarTextLabel>
                </ExternalLink>
            )}
            {!CONF.DATA_PROTECTION_LINK ? null : (
                <ExternalLink style={{zIndex: -2}} href={CONF.DATA_PROTECTION_LINK}>
                    <SidebarTextLabel height="25px" icon="verified" iconSize="1em" textSize={1} space=".5em">
                        {CONF.DATA_PROTECTION_TEXT}
                    </SidebarTextLabel>
                </ExternalLink>
            )}
            <Box mb="10px" />
        </SidebarContainer>
    );
};

function copyUserName(): void {
    copyToClipboard({
        value: Client.username,
        message: "Username copied to clipboard"
    });
}

const mapStateToProps = ({status, project}: ReduxObject): SidebarStateProps => ({
    page: status.page,

    /* Used to ensure re-rendering of Sidebar after user logs in. */
    loggedIn: Client.isLoggedIn,

    /* Used to ensure re-rendering of Sidebar after project change. */
    activeProject: project.project
});

export const enum SidebarPages {
    Files,
    Shares,
    Projects,
    AppStore,
    Resources,
    Runs,
    Publish,
    Activity,
    Admin,
    None
}

export function useSidebarPage(page: SidebarPages): void {
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(setActivePage(page));
        return () => {
            dispatch(setActivePage(SidebarPages.None));
        };
    }, [page]);
}

export default connect(mapStateToProps)(Sidebar);
