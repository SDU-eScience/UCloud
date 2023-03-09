import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import styled from "styled-components";
import {
    copyToClipboard,
    inDevEnvironment,
    isLightThemeStored,
    joinToString,
    stopPropagationAndPreventDefault,
    useFrameHidden
} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import Box from "./Box";
import ExternalLink from "./ExternalLink";
import Flex, {FlexCProps} from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import RBox from "./RBox";
import Text, {EllipsedText, TextSpan} from "./Text";
import {ThemeColor} from "./theme";
import Tooltip from "./Tooltip";
import {useCallback} from "react";
import {useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import {AutomaticGiftClaim} from "@/Services/Gifts/AutomaticGiftClaim";
import {ResourceInit} from "@/Services/ResourceInit";
import Support from "./SupportBox";
import {VersionManager} from "@/VersionManager/VersionManager";
import Notification from "@/Notifications";
import AppRoutes from "@/Routes";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPage} from "@/DefaultObjects";
import {NewsPost} from "@/Dashboard/Dashboard";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import BackgroundTasks from "@/Services/BackgroundTasks/BackgroundTask";
import {SidebarPages} from "./SidebarPagesEnum";
import ClickableDropdown from "./ClickableDropdown";
import Divider from "./Divider";
import {ThemeToggler} from "./ThemeToggle";
import {AvatarType} from "@/UserSettings/Avataaar";
import {Avatar} from "@/AvataaarLib";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";

const SidebarElementContainer = styled(Flex) <{hover?: boolean; active?: boolean}>`
    justify-content: left;
    flex-flow: row;
    align-items: center;

    & > ${Text} {
        white-space: nowrap;
    }
`;

const SidebarContainer = styled(Flex) <FlexCProps>`
    position: fixed;
    z-index: 80;
    height: 100%;
    background-color: var(--sidebar);
`;

interface TextLabelProps {
    icon: IconName;
    children: | string | number | JSX.Element;
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

interface MenuElement {
    icon: IconName;
    label: string;
    to: string | (() => string);
    show?: () => boolean;
}

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
            {icon: "files", label: "Files", to: "/drives/"},
            {icon: "projects", label: "Projects", to: "/projects/", show: () => Client.hasActiveProject},
            {icon: "shareMenu", label: "Shares", to: "/shares/", show: () => !Client.hasActiveProject},
            {icon: "dashboard", label: "Resources", to: "/public-ips/"},
            {icon: "appStore", label: "Apps", to: "/applications/overview/"},
            {icon: "results", label: "Runs", to: "/jobs/"}
        ], predicate: () => Client.isLoggedIn
    },
    auditing: {items: [], predicate: () => Client.isLoggedIn},
    admin: {items: [{icon: "admin", label: "Admin", to: "/admin"}], predicate: () => Client.userIsAdmin}
};

interface SidebarStateProps {
    page: SidebarPages;
    loggedIn: boolean;
    avatar: AvatarType;
    activeProject?: string;
}

export const Sidebar = (): JSX.Element | null => {
    const sidebarEntries = sideBarMenuElements;
    const {activeProject, loggedIn, page, avatar} = useSidebarReduxProps();
    const dispatch = useDispatch();
    React.useEffect(() => {
        if (Client.isLoggedIn) {
            findAvatar().then(action => {
                if (action !== null) dispatch(action);
            });
        }
    }, []);

    if (useFrameHidden()) return null;
    if (!loggedIn) return null;

    const sidebar = Object.keys(sidebarEntries)
        .map(key => sidebarEntries[key])
        .filter(it => it.predicate());
    return (
        <SidebarContainer color="var(--sidebar)" flexDirection="column" alignItems="center"
            width={"var(--sidebarWidth)"}>
            <Link data-component={"logo"} to="/">
                <Icon name="logoEsc" mt="10px" size="43px" />
            </Link>
            {sidebar.map((category, categoryIdx) => (
                <React.Fragment key={categoryIdx}>
                    {category.items.filter((it: MenuElement) => it?.show?.() ?? true).map(({
                        icon,
                        label,
                        to
                    }: MenuElement) => (
                        <React.Fragment key={label}>
                            <SidebarSpacer />
                            <SidebarElement
                                icon={icon}
                                activePage={page}
                                label=""
                                to={typeof to === "function" ? to() : to}
                            />
                        </React.Fragment>
                    ))}
                </React.Fragment>
            ))}
            <SidebarPushToBottom />
            <AutomaticGiftClaim />
            <ResourceInit />
            {/* Screen size indicator */}
            {inDevEnvironment() ? <Flex mb={"5px"} width={190} ml={19} justifyContent="left"><RBox /> </Flex> : null}
            <Debugger />
            <Box height="28px" />
            <Support />
            <Box height="28px" />
            <Notification />
            <Box height="28px" />
            <VersionManager />
            <BackgroundTasks />
            <Downtimes />
            <ClickableDropdown
                width="230px"
                left="var(--sidebarWidth)"
                top="-223px"
                colorOnHover={false}
                trigger={Client.isLoggedIn ? <UserAvatar height="59px" width="53px" avatar={avatar} /> : null}
            >
                {!CONF.STATUS_PAGE ? null : (
                    <>
                        <Box>
                            <ExternalLink color="black" href={CONF.STATUS_PAGE}>
                                <Flex color="black">
                                    <Icon name="favIcon" mr="0.5em" my="0.2em" size="1.3em" />
                                    <TextSpan>Site status</TextSpan>
                                </Flex>
                            </ExternalLink>
                        </Box>
                        <Divider />
                    </>
                )}
                <Box>
                    <Link color="black" to={AppRoutes.users.settings()}>
                        <Flex color="black">
                            <Icon name="properties" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>Settings</TextSpan>
                        </Flex>
                    </Link>
                </Box>
                <Flex>
                    <Link to={"/users/avatar"}>
                        <Flex color="black">
                            <Icon name="user" color="black" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>Edit Avatar</TextSpan>
                        </Flex>
                    </Link>
                </Flex>
                <Flex onClick={() => Client.logout()} data-component={"logout-button"}>
                    <Icon name="logout" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                    Logout
                </Flex>
                {!CONF.SITE_DOCUMENTATION_URL ? null : (
                    <div>
                        <ExternalLink hoverColor="text" href={CONF.SITE_DOCUMENTATION_URL}>
                            <Icon name="docs" color="black" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>{CONF.PRODUCT_NAME} Docs</TextSpan>
                        </ExternalLink>
                    </div>
                )}
                {!CONF.DATA_PROTECTION_LINK ? null : (
                    <div>
                        <ExternalLink hoverColor="text" href={CONF.DATA_PROTECTION_LINK}>
                            <Icon name="verified" color="black" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>{CONF.DATA_PROTECTION_TEXT}</TextSpan>
                        </ExternalLink>
                    </div>
                )}
                <Divider />
                <Username />
                <ProjectID />
                <Divider />
                <span>
                    <Flex cursor="auto">
                        <ThemeToggler
                            isLightTheme={isLightThemeStored()}
                            onClick={onToggleTheme}
                        />
                    </Flex>
                </span>
            </ClickableDropdown>
            <Box mb="10px" />
        </SidebarContainer>
    );

    function onToggleTheme(e: React.SyntheticEvent<HTMLDivElement, Event>): void {
        stopPropagationAndPreventDefault(e);
        console.log("toggleTheme(); // TODO")
    }
};

function Username(): JSX.Element | null {
    if (!Client.isLoggedIn) return null;
    return <Box style={{zIndex: -1}}>
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
    </Box>
}

function ProjectID(): JSX.Element | null {
    const projectId = useProjectId();

    const project = useProject();

    const projectPath = joinToString(
        [...(project.fetch().status.path?.split("/")?.filter(it => it.length > 0) ?? []), project.fetch().specification.title],
        "/"
    );
    const copyProjectPath = useCallback(() => {
        copyToClipboard({value: projectPath, message: "Project copied to clipboard!"});
    }, [projectPath]);

    if (!projectId) return null;
    return <SidebarTextLabel key={projectId} icon={"projects"} height={"25px"} iconSize={"1em"} textSize={1}
        space={".5em"}>
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
}

function Downtimes(): JSX.Element | null {
    const [downtimes, fetchDowntimes] = useCloudAPI<Page<NewsPost>>({noop: true}, emptyPage);
    const [intervalId, setIntervalId] = React.useState(-1);

    React.useEffect(() => {
        setIntervalId(window.setInterval(() => fetchDowntimes({
            method: "GET", path: "/news/listDowntimes"
        }), 600_000));
        return () => {
            if (intervalId !== -1) clearInterval(intervalId);
        };
    }, []);

    const upcomingDowntime = downtimes.data.items.at(0)?.id ?? -1;

    if (upcomingDowntime === -1) return null;
    return <Link to={AppRoutes.news.detailed(upcomingDowntime)}>
        <Tooltip
            right="0"
            bottom="1"
            tooltipContentWidth="115px"
            wrapperOffsetLeft="10px"
            trigger={<Icon color="yellow" name="warning" />}
        >
            Upcoming downtime.<br />
            Click to view
        </Tooltip>
    </Link>
}

function isLocalHost(): boolean {
    return window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
}

function Debugger(): JSX.Element | null {
    return isLocalHost() ? <>
        <ExternalLink href="/debugger?hide-frame">
            <SidebarTextLabel icon={"bug"} iconSize="1.5em" textSize={1} height={"25px"} hover={false}>
                <div />
            </SidebarTextLabel>
        </ExternalLink>
    </> : null
}

function copyUserName(): void {
    copyToClipboard({
        value: Client.username,
        message: "Username copied to clipboard"
    });
}

function useSidebarReduxProps(): SidebarStateProps {
    return useSelector((it: ReduxObject) => ({
        page: it.status.page,

        /* Used to ensure re-rendering of Sidebar after user logs in. */
        loggedIn: Client.isLoggedIn,

        /* Used to ensure re-rendering of Sidebar after project change. */
        activeProject: it.project.project,

        avatar: it.avatar
    }))
}
