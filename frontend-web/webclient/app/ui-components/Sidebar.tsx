import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import styled from "styled-components";
import {
    copyToClipboard,
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
import {APICallState, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {emptyPage, emptyPageV2} from "@/DefaultObjects";
import {navigateByFileType, NewsPost} from "@/Dashboard/Dashboard";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import BackgroundTasks from "@/Services/BackgroundTasks/BackgroundTask";
import {SidebarPages} from "./SidebarPagesEnum";
import ClickableDropdown from "./ClickableDropdown";
import Divider from "./Divider";
import {ThemeToggler} from "./ThemeToggle";
import {AvatarType} from "@/UserSettings/Avataaar";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {PageV2} from "@/UCloud";
import AdminLinks from "@/Admin/Links";
import {SharesLinks} from "@/Files/Shares";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import Truncate from "./Truncate";
import metadataApi from "@/UCloud/MetadataDocumentApi";
import {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import {fileName} from "@/Utilities/FileUtilities";
import {useNavigate} from "react-router";
import JobsApi, {Job} from "@/UCloud/JobsApi";
import {ProjectLinks} from "@/Project/ProjectLinks";

const SidebarElementContainer = styled(Flex) <{hover?: boolean; active?: boolean}>`
    justify-content: left;
    flex-flow: row;
    align-items: center;

    & > ${Text} {
        white-space: nowrap;
    }
`;

const SidebarAdditionalStyle = styled(Flex) <{forceOpen: boolean}>`
    background-color: #5C89F4;
    transition: width 0.2s;
    width: ${p => p.forceOpen ? "var(--sidebarAdditionalWidth)" : "0px"};
`;

const SidebarContainer = styled(Flex) <FlexCProps>`
    height: 100vh;
    background-color: var(--sidebar);
`;

const SidebarItemWrapper = styled.div<{active: boolean}>`
    cursor: pointer;
    display: flex;
    border-radius: 5px;
    ${p => p.active ? "background-color: rgba(255, 255, 255, 0.35);" : null}
    &:hover {
        background-color: rgba(255, 255, 255, 0.35);
    }
    width: 32px;
    height: 32px;
    margin-top: 8px;
    & > a, & > ${Icon} {
        margin: auto auto auto auto;
    }
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
    iconSize = "18", space = "22px", textSize = 3, hover = true
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
    to?: string;
    external?: boolean;
    activePage: SidebarPages;
}

function SidebarElement({icon, label, to, activePage}: SidebarElement): JSX.Element {
    if (to) {
        return (
            <SidebarLink to={to} active={enumToLabel(activePage) === label}>
                <Icon name={icon} color={"white"} color2={"white"} size={"20"} />
            </SidebarLink>
        );
    } else return <Icon name={icon} color={"white"} color2={"white"} size={"20"} />;
}

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

const SidebarPushToBottom = styled.div`
    flex-grow: 1;
`;

interface MenuElement {
    icon: IconName;
    label: string;
    to?: string | (() => string);
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
    admin: {items: [{icon: "admin", label: "Admin"}], predicate: () => Client.userIsAdmin}
};

interface SidebarStateProps {
    page: SidebarPages;
    loggedIn: boolean;
    avatar: AvatarType;
    activeProject?: string;
}

function hasOrParentHasClass(t: EventTarget | null, classname: string): boolean {
    var target = t;
    while (target && "classList" in target) {
        var classList = target.classList as DOMTokenList;
        if (classList.contains(classname)) return true;
        if ("parentNode" in target) {
            target = target.parentNode as EventTarget | null;
        } else {
            return false;
        }
        if (!target) return false;
    }
    return false;
}

const SIDEBAR_IDENTIFIER = "SIDEBAR_IDENTIFIER";

const SidebarItemsColumn = styled.div`
    margin-left: auto;
    width: calc(var(--sidebarWidth) - 32px / 2);
    padding-top: 6px;
    padding-bottom: 8px;
`

export const Sidebar = ({toggleTheme}: {toggleTheme(): void;}): JSX.Element | null => {
    const sidebarEntries = sideBarMenuElements;
    const {activeProject, loggedIn, page, avatar} = useSidebarReduxProps();

    const [selectedPage, setSelectedPage] = React.useState("");
    const [hoveredPage, setHoveredPage] = React.useState("");

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
        <Flex>
            <SidebarContainer color="var(--sidebar)" flexDirection="column" alignItems="center"
                width={"var(--sidebarWidth)"}>
                <Link data-component={"logo"} to="/">
                    <Icon name="logoEsc" mt="10px" size="34px" />
                </Link>
                <SidebarItemsColumn className={SIDEBAR_IDENTIFIER} onMouseLeave={e => {
                    if (!hasOrParentHasClass(e.relatedTarget, SIDEBAR_IDENTIFIER)) setHoveredPage("")
                }}>
                    {sidebar.map((category, categoryIdx) => (
                        <React.Fragment key={categoryIdx}>
                            {category.items.filter((it: MenuElement) => it?.show?.() ?? true).map(({
                                icon,
                                label,
                                to
                            }: MenuElement) => (
                                <SidebarItemWrapper
                                    key={label}
                                    active={label === selectedPage}
                                    onClick={() => setSelectedPage(label)}
                                    onMouseEnter={() => setHoveredPage(label)}
                                >
                                    <SidebarElement
                                        icon={icon}
                                        activePage={page}
                                        label=""
                                        to={typeof to === "function" ? to() : to}
                                    />
                                </SidebarItemWrapper>
                            ))}
                        </React.Fragment>
                    ))}
                </SidebarItemsColumn>
                <SidebarPushToBottom />
                <AutomaticGiftClaim />
                <ResourceInit />
                <Debugger />
                {/* TODO(Jonas): These should be inside the above node. Only render if above node is rendered. */}
                <Box height="18px" />
                <Support />
                {/* TODO(Jonas): These should be inside the above node. Only render if above node is rendered. */}
                <Box height="18px" />
                <Notification />
                {/* TODO(Jonas): These should be inside the above node. Only render if above node is rendered. */}
                <Box height="18px" />
                <VersionManager />
                <BackgroundTasks />
                <Downtimes />
                <ClickableDropdown
                    width="230px"
                    left="var(--sidebarWidth)"
                    bottom="0"
                    colorOnHover={false}
                    trigger={Client.isLoggedIn ? <UserAvatar avatarStyle={""} height="42px" width="42px" avatar={avatar} /> : null}
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

            <SidebarAdditional data-tag="additional" hovered={hoveredPage} clicked={selectedPage} clearHover={() => setHoveredPage("")} />
        </Flex>
    );

    function onToggleTheme(e: React.SyntheticEvent<HTMLDivElement, Event>): void {
        stopPropagationAndPreventDefault(e);
        toggleTheme();
    }
};

function useSidebarFilesPage(): {
    drives: APICallState<PageV2<FileCollection>>,
    favorites: APICallState<PageV2<FileMetadataAttached>>,
} {
    const [drives] = useCloudAPI<PageV2<FileCollection>>(FileCollectionsApi.browse({itemsPerPage: 10/* , filterMemberFiles: "all" */}), emptyPageV2);

    const [favorites] = useCloudAPI<PageV2<FileMetadataAttached>>(
        metadataApi.browse({
            filterActive: true,
            filterTemplate: "Favorite",
            itemsPerPage: 10
        }),
        emptyPageV2
    );

    return {
        drives,
        favorites
    }
}

function useSidebarRunsPage(): APICallState<PageV2<Job>> {
    /* TODO(Jonas): This should be fetched from the same source as the runs page. */
    const [runs] = useCloudAPI<PageV2<Job>>(JobsApi.browse({itemsPerPage: 10}), emptyPageV2);

    return runs;
}


function SidebarAdditional({hovered, clicked, clearHover}: {hovered: string; clicked: string; clearHover(): void}): JSX.Element {
    const [forceOpen, setForceOpen] = React.useState(false);
    React.useEffect(() => {
        if (clicked) {
            setForceOpen(true);
        }
    }, [clicked]);

    const {drives, favorites} = useSidebarFilesPage();
    const recentRuns = useSidebarRunsPage();

    const navigate = useNavigate();
    const [, invokeCommand] = useCloudCommand();

    const active = hovered ? hovered : clicked;
    /* TODO(Jonas): hovering should slide over, while clicking should push */
    return (<SidebarAdditionalStyle onMouseLeave={e => {
        if (!hasOrParentHasClass(e.relatedTarget, SIDEBAR_IDENTIFIER)) clearHover()
    }} className={SIDEBAR_IDENTIFIER} flexDirection="column" forceOpen={forceOpen || hovered !== ""}>
        <Box ml="6px">
            <Flex>
                <TextSpan bold color="var(--white)">{active}</TextSpan>
                {forceOpen ? <TextSpan ml="auto" mr="4px" onClick={() => setForceOpen(false)}>Unlock</TextSpan> : null}
            </Flex>
            {active !== "Files" ? null : (
                <Flex flexDirection="column">
                    <Flex ml="auto" mr="4px" mb="4px">
                        <Link hoverColor="white" style={{fontWeight: "bold", cursor: "pointer"}} color="white" to="/drives/">Manage drives</Link>
                    </Flex>
                    <TextSpan color="white" bold>Drives</TextSpan>
                    {drives.data.items.map(it =>
                        <Flex key={it.id} ml="4px">
                            <Link hoverColor="white" to={`/files?path=${it.id}`}>
                                <Truncate color="var(--white)">
                                    <Icon size={12} mr="4px" name="hdd" color="white" color2="white" />
                                    {it.specification.title}
                                </Truncate>
                            </Link>
                            <Flex ml="auto" mr="5px" my="auto"><ProviderLogo providerId={it.specification.product.provider} size={20} /></Flex>
                        </Flex>
                    )}
                    <TextSpan bold color="white">Favorite Files</TextSpan>
                    {favorites.data.items.map(it => <Flex key={it.path} ml="4px" cursor="pointer" onClick={() => navigateByFileType(it, invokeCommand, navigate)}>
                        <Flex mx="auto" my="auto"><Icon name="starFilled" size={12} mr="4px" color="white" color2="white" /></Flex><Truncate color="white">{fileName(it.path)}</Truncate>
                    </Flex>)}
                </Flex>
            )}
            {active !== "Projects" ? null : (<ProjectLinks />)}
            {active !== "Shares" ? null : (<SharesLinks />)}
            {active !== "Runs" ? null : (
                <Flex flexDirection="column">
                    <TextSpan bold color="white">Most recent</TextSpan>
                    {recentRuns.data.items.map(it =>
                        <Truncate key={it.id} color="white">{it.specification.name ?? it.id} ({it.specification.application.name})</Truncate>
                    )}
                </Flex>
            )}
            {active !== "Resources" ? null : ("TODO")}
            {active !== "Admin" ? null : (<AdminLinks />)}
        </Box>
    </SidebarAdditionalStyle >)
}

function Username(): JSX.Element | null {
    if (!Client.isLoggedIn) return null;
    return <Tooltip
        left="-50%"
        top="1"
        mb="35px"
        trigger={(
            <SidebarTextLabel
                height="25px"
                hover={false}
                icon="id"
                iconSize="1em"
                textSize={1}
                space=".5em"
            >
                <EllipsedText
                    cursor="pointer"
                    onClick={copyUserName}
                    width="140px"
                >
                    {Client.username}
                </EllipsedText>
            </SidebarTextLabel>
        )}
    >
        Click to copy {Client.username} to clipboard
    </Tooltip>
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
    return <Tooltip
        left="-50%"
        top="1"
        mb="35px"
        trigger={<SidebarTextLabel key={projectId} icon={"projects"} height={"25px"} iconSize={"1em"} textSize={1} space={".5em"}>
            <EllipsedText
                cursor="pointer"
                onClick={copyProjectPath}
                width="140px"
            >
                {projectPath}
            </EllipsedText>
        </SidebarTextLabel>
        }
    >
        Click to copy to clipboard
    </Tooltip>
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
            <SidebarTextLabel icon={"bug"} iconSize="18px" textSize={1} height={"25px"} hover={false}>
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
