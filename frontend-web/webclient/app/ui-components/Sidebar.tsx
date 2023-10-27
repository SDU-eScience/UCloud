import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {
    copyToClipboard,
    joinToString,
    useFrameHidden
} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import Box from "./Box";
import ExternalLink from "./ExternalLink";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import Text, {EllipsedText, TextSpan} from "./Text";
import Tooltip from "./Tooltip";
import {useCallback, useEffect} from "react";
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
import ClickableDropdown from "./ClickableDropdown";
import Divider from "./Divider";
import {ThemeToggler} from "./ThemeToggle";
import {AvatarType} from "@/UserSettings/Avataaar";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {Page, PageV2, compute} from "@/UCloud";
import AdminLinks from "@/Admin/Links";
import {sharesLinksInfo} from "@/Files/Shares";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import Truncate from "./Truncate";
import metadataApi from "@/UCloud/MetadataDocumentApi";
import {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import {fileName} from "@/Utilities/FileUtilities";
import {useNavigate} from "react-router";
import JobsApi, {Job} from "@/UCloud/JobsApi";
import {ProjectLinks} from "@/Project/ProjectLinks";
import {ResourceLinks} from "@/Resource/ResourceOptions";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import Relative from "./Relative";
import Absolute from "./Absolute";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {setAppFavorites} from "@/Applications/Redux/Actions";
import {checkCanConsumeResources} from "./ResourceBrowser";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";

const SecondarySidebarClass = injectStyle("secondary-sidebar", k => `
    ${k} {
        background-color: var(--sidebarSecondaryColor);
        transition: transform 0.1s;
        width: 0;
        display: flex;
        flex-direction: column;
        transform: translate(-300px, 0);
        box-sizing: border-box;
        overflow-y: scroll;
        overflow-x: hidden;
        height: 100vh;
        
        font-size: 14px;
    }
    
    ${k}, ${k} a, ${k} a:hover {
        color: white;
    }
    
    ${k}[data-open="true"] {
        transform: translate(0, 0);
        padding: 13px 16px 16px 16px;
        width: var(--secondarySidebarWidth);
    }

    @media screen and (max-width: 640px) {
        ${k}[data-open="true"] {
            position: absolute;
            left: var(--sidebarWidth);
            z-index: 1;
        }
    }
    

    ${k}[data-as-pop-over="true"] {
        position: absolute;
        left: var(--sidebarWidth);
        z-index: 10;
    }
    
    ${k} header {
        display: flex;
        align-items: center;
        margin-bottom: 15px;
    }
    
    ${k} header h1 {
        font-weight: bold;
        font-size: 20px;
        flex-grow: 1;
        margin: 0;
    }
    
    ${k} header div {
        cursor: pointer;
    }
    
    ${k} h2, ${k} h3 {
        margin: 0;
        font-weight: bold;
    }
    
    ${k} h3 {
        font-size: 16px;
    }
    
    ${k} a, ${k} h3.no-link  {
        border-radius: 10px;
        padding: 5px;
        display: block;
        margin-left: -5px;
    }
    
    ${k} a:hover {
        background-color: rgba(255, 255, 255, 0.35);
    }

`);


const SidebarContainerClass = injectStyleSimple("sidebar-container", `
    color: var(--sidebarColor);
    align-items: center;
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: var(--sidebarWidth);

    /* Required by Safari */
    min-width: var(--sidebarWidth);
    
    background-color: var(--sidebarColor);
    z-index: 100;
    padding-bottom: 12px;
`);

const SidebarMenuItem = injectStyle("sidebar-item", k => `
    ${k} {
        cursor: pointer;
        display: flex;
        border-radius: 5px;
        width: 32px;
        height: 32px;
        margin-top: 8px;
    }
    
    ${k}:hover, ${k}[data-active="true"] {
        background-color: rgba(255, 255, 255, 0.35);
    }
    
    ${k} > * {
        margin: auto;
    }
`);


interface SidebarElement {
    icon: IconName;
}

function SidebarElement({icon}: SidebarElement): JSX.Element {
    return <Icon name={icon} hoverColor="fixedWhite" color="fixedWhite" color2="fixedWhite" size={"24"} />
}

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

export const sideBarMenuElements: [
    SidebarMenuElements,
    SidebarMenuElements,
] = [
        {
            items: [
                {icon: "heroFolder", label: "Files", to: "/drives/"},
                {icon: "heroUserGroup", label: "Workspace", to: AppRoutes.project.usage()},
                {icon: "heroSquaresPlus", label: "Resources", to: AppRoutes.resources.publicIps()},
                {icon: "heroShoppingBag", label: "Applications", to: AppRoutes.apps.landing()},
                {icon: "heroServer", label: "Runs", to: "/jobs/"}
            ], predicate: () => Client.isLoggedIn
        },
        {items: [{icon: "heroBolt", label: "Admin", to: AppRoutes.admin.userCreation()}], predicate: () => Client.userIsAdmin}
    ];

interface SidebarStateProps {
    loggedIn: boolean;
    avatar: AvatarType;
}

function hasOrParentHasClass(t: EventTarget | null, classname: string): boolean {
    let target = t;
    while (target && "classList" in target) {
        let classList = target.classList as DOMTokenList;
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

const SidebarItemsClass = injectStyle("sidebar-items", k => `
    ${k} {
        padding-top: 7px;
        flex-grow: 1;
    }
`);

function UserMenuLink(props: {icon: IconName; text: string; to: string; close(): void;}): JSX.Element {
    return <Link color="black" onClick={props.close} hoverColor="black" height="28px" to={props.to}>
        <Flex className={HoverClass}>
            <Icon name={props.icon} color="var(--black)" color2="var(--black)" mr="0.5em" my="0.2em"
                size="1.3em" />
            <TextSpan color="var(--black)">{props.text}</TextSpan>
        </Flex>
    </Link>
}

function UserMenuExternalLink(props: {icon: IconName; href: string; text: string; close(): void;}): JSX.Element | null {
    if (!props.text) return null;
    return <div className={HoverClass}>
        <ExternalLink hoverColor="text" onClick={props.close} href={props.href}>
            <Icon name={props.icon} color="black" color2="gray" mr="0.5em" my="0.2em" size="1.3em" />
            <TextSpan color="black">{props.text}</TextSpan>
        </ExternalLink>
    </div>
}

const UserMenu: React.FunctionComponent<{
    avatar: AvatarType;
}> = ({avatar}) => {
    const close = React.useRef(() => undefined);
    return <ClickableDropdown
        width="230px"
        paddingControlledByContent
        left="calc(var(--sidebarWidth) + 5px)"
        bottom="0"
        closeFnRef={close}
        colorOnHover={false}
        trigger={Client.isLoggedIn ?
            <UserAvatar height="42px" width="42px" avatar={avatar} /> : null}
    >
        <Box py="12px">
            {!CONF.STATUS_PAGE ? null : (
                <>
                    <Box className={HoverClass} >
                        <ExternalLink onClick={close.current} href={CONF.STATUS_PAGE}>
                            <Flex>
                                <Icon name="favIcon" mr="0.5em" my="0.2em" size="1.3em" color="var(--black)" />
                                <TextSpan color="black">Site status</TextSpan>
                            </Flex>
                        </ExternalLink>
                    </Box>
                    <Divider />
                </>
            )}
            <UserMenuLink close={close.current} icon="properties" text="Settings" to={AppRoutes.users.settings()} />
            <UserMenuLink close={close.current} icon="user" text="Edit avatar" to={AppRoutes.users.avatar()} />
            <UserMenuExternalLink close={close.current} href={CONF.SITE_DOCUMENTATION_URL} icon="docs" text={CONF.PRODUCT_NAME ? CONF.PRODUCT_NAME + " docs" : ""} />
            <UserMenuExternalLink close={close.current} href={CONF.DATA_PROTECTION_LINK} icon="verified" text={CONF.DATA_PROTECTION_TEXT} />
            <Divider />
            <Username close={close.current} />
            <ProjectID close={close.current} />
            <Divider />
            <Flex className={HoverClass} onClick={() => Client.logout()} data-component={"logout-button"}>
                <Icon name="logout" color2="var(--black)" mr="0.5em" my="0.2em" size="1.3em" />
                Logout
            </Flex>
            <Divider />
            <span>
                <Flex cursor="auto">
                    <ThemeToggler />
                </Flex>
            </span>
        </Box>
    </ClickableDropdown>;
}

const HoverClass = injectStyle("hover-class", k => `
    ${k} {
        padding-left: 12px;
    }

    ${k}:hover {
        background: var(--lightBlue);
    }
`);

export function Sidebar(): JSX.Element | null {
    const sidebarEntries = sideBarMenuElements;
    const {loggedIn, avatar} = useSidebarReduxProps();

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

    const sidebar: MenuElement[] = sidebarEntries.filter(it => it.predicate())
        .flatMap(category => category.items.filter((it: MenuElement) => it?.show?.() ?? true));

    return (
        <Flex>
            <div className={classConcat(SidebarContainerClass, SIDEBAR_IDENTIFIER)}>
                <Link data-component={"logo"} to="/">
                    <Icon name="logoEsc" mt="10px" size="34px" />
                </Link>

                <div
                    className={SidebarItemsClass}
                    onMouseLeave={e => {
                        if (!hasOrParentHasClass(e.relatedTarget, SIDEBAR_IDENTIFIER)) {
                            setHoveredPage("")
                        }
                    }}
                >
                    {sidebar.map(({label, icon, to}) =>
                        to ? (
                            <Link hoverColor="fixedWhite" key={label} to={typeof to === "function" ? to() : to}>
                                <div
                                    data-active={label === selectedPage}
                                    onMouseEnter={() => setHoveredPage(label)}
                                    className={SidebarMenuItem}
                                >
                                    <SidebarElement icon={icon} />
                                </div>
                            </Link>) : <div
                                key={label}
                                data-active={label === selectedPage}
                                onMouseEnter={() => setHoveredPage(label)}
                                className={SidebarMenuItem}
                            >
                            <SidebarElement icon={icon} />
                        </div>
                    )}
                </div>

                <>
                    {/* (Typically) invisible elements here to run various background tasks */}
                    <AutomaticGiftClaim />
                    <ResourceInit />
                    <VersionManager />
                    <BackgroundTasks />
                    <Downtimes />
                </>

                <Flex flexDirection={"column"} gap={"18px"} alignItems={"center"}>
                    <Notification />
                    <Support />
                    <UserMenu avatar={avatar} />
                </Flex>
            </div>

            <SecondarySidebar
                key={(!!selectedPage).toString()} /* Note(Jonas) Needed for Safari to update correctly  */
                data-tag="secondary"
                hovered={hoveredPage}
                clicked={selectedPage}
                setSelectedPage={setSelectedPage}
                clearHover={() => setHoveredPage("")}
                clearClicked={() => setSelectedPage("")}
            />
        </Flex>
    );
};

function useSidebarFilesPage(): [
    APICallState<PageV2<FileCollection>>,
    APICallState<PageV2<FileMetadataAttached>>
] {
    const [drives, fetchDrives] = useCloudAPI<PageV2<FileCollection>>({noop: true}, emptyPageV2);

    const [favorites] = useCloudAPI<PageV2<FileMetadataAttached>>(
        metadataApi.browse({
            filterActive: true,
            filterTemplate: "Favorite",
            itemsPerPage: 10
        }),
        emptyPageV2
    );

    const projectId = useProjectId();

    React.useEffect(() => {
        fetchDrives(FileCollectionsApi.browse({itemsPerPage: 10/* , filterMemberFiles: "all" */}))
    }, [projectId]);

    return [
        drives,
        favorites
    ];
}

function useSidebarRunsPage(): APICallState<PageV2<Job>> {
    /* TODO(Jonas): This should be fetched from the same source as the runs page. */
    const [runs, fetchRuns] = useCloudAPI<PageV2<Job>>({noop: true}, emptyPageV2);
    const projectId = useProjectId();

    React.useEffect(() => {
        fetchRuns(JobsApi.browse({itemsPerPage: 10, filterState: "RUNNING"}));
    }, [projectId]);

    return runs;
}

interface SecondarySidebarProps {
    hovered: string;
    clicked: string;
    clearHover(): void;
    clearClicked(): void;
    setSelectedPage: React.Dispatch<React.SetStateAction<string>>;
}

function SecondarySidebar({
    hovered,
    clicked,
    clearHover,
    setSelectedPage,
    clearClicked
}: SecondarySidebarProps): React.JSX.Element {
    const [drives, favoriteFiles] = useSidebarFilesPage();
    const recentRuns = useSidebarRunsPage();

    const navigate = useNavigate();
    const [, invokeCommand] = useCloudCommand();

    const [favoriteApps] = useCloudAPI<Page<compute.ApplicationSummaryWithFavorite>>(
        compute.apps.retrieveFavorites({itemsPerPage: 100, page: 0}),
        emptyPage,
    );

    const [appStoreSections] = useCloudAPI<compute.AppStoreSections>(
        compute.apps.appStoreSections({page: "FULL"}),
        {sections: []}
    );

    const canConsume = checkCanConsumeResources(Client.projectId ?? null, {api: FilesApi});

    const dispatch = useDispatch();
    React.useEffect(() => {
        if (favoriteApps.loading) return;
        dispatch(setAppFavorites(favoriteApps.data.items));
    }, [favoriteApps]);

    const appFavorites = useSelector<ReduxObject, compute.ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);
    const isOpen = clicked !== "" || hovered !== "";
    const active = hovered ? hovered : clicked;
    const asPopOver = hovered && !clicked;

    useEffect(() => {
        const firstLevel = parseInt(getCssPropertyValue("sidebarWidth").replace("px", ""));
        const secondLevel = parseInt(getCssPropertyValue("secondarySidebarWidth").replace("px", ""));

        let sum = firstLevel;
        if (isOpen) sum += secondLevel;

        document.body.style.setProperty("--currentSidebarWidth", `${sum}px`);
    }, [isOpen]);

    return <div
        className={classConcat(SecondarySidebarClass, SIDEBAR_IDENTIFIER)}
        onMouseLeave={e => {
            if (!hasOrParentHasClass(e.relatedTarget, SIDEBAR_IDENTIFIER)) clearHover();
        }}
        data-open={isOpen}
        data-as-pop-over={!!asPopOver}
    >
        <header>
            <h1>{active}</h1>

            <Relative top="16px" right="2px" height={0} width={0}>
                <Absolute>
                    <Flex alignItems="center" backgroundColor="white" height="38px" borderRadius="12px 0 0 12px" onClick={clicked ? clearClicked : () => setSelectedPage(hovered)}>
                        <Icon name="chevronDownLight" size={18} rotation={clicked ? 90 : -90} color="blue" />
                    </Flex>
                </Absolute>
            </Relative>
        </header>

        {active !== "Files" || !canConsume ? null : (
            <Flex flexDirection={"column"} gap={"16px"}>
                <div>
                    <h3><Link to={"/drives/"}>Drives</Link></h3>
                    {drives.data.items.map(it =>
                        <Link key={it.id} to={`/files?path=${it.id}`}>
                            <Flex>
                                <Box mt="1px" mr="4px"><ProviderLogo providerId={it.specification.product.provider} size={20} /></Box>
                                <Truncate fontSize="14px" title={it.specification.title} maxWidth={"150px"} color="var(--fixedWhite)">
                                    {it.specification.title}
                                </Truncate>
                            </Flex>
                        </Link>
                    )}
                </div>

                <div>
                    <h3 className={"no-link"}>Favorite files</h3>
                    {favoriteFiles.data.items.length === 0 ? <div>No favorite files</div> : null}
                    {favoriteFiles.data.items.map(it =>
                        <a href={"#"} key={it.path} onClick={() => navigateByFileType(it, invokeCommand, navigate)}>
                            <Flex alignItems={"center"}>
                                <Icon name="heroStar" size={16} mr="4px" color="#fff" color2="#fff" />
                                <Truncate fontSize={"14px"} color="#fff">{fileName(it.path)}</Truncate>
                            </Flex>
                        </a>
                    )}
                </div>

                {Client.hasActiveProject ? null : <div>
                    <h3 className={"no-link"}>Shared files</h3>
                    {sharesLinksInfo.map(it => <Link key={it.text} to={it.to}>{it.text}</Link>)}
                </div>}
            </Flex>
        )}

        {active !== "Workspace" ? null : (<ProjectLinks />)}

        {active !== "Applications" ? null :
            <Flex flexDirection={"column"} gap="16px">
                <div>
                    {appStoreSections.data.sections.map(section =>
                        <Link key={section.id} to={`/applications/full#section${section.id}`}>{section.name}</Link>
                    )}
                </div>

                <div>
                    <h3 className={"no-link"}>Favorite apps</h3>

                    {appFavorites.map(it =>
                        <AppTitleAndLogo
                            key={it.metadata.name + it.metadata.version}
                            to={AppRoutes.jobs.create(it.metadata.name, it.metadata.version)}
                            name={it.metadata.name}
                            title={it.metadata.title}
                        />
                    )}

                    {appFavorites.length !== 0 ? null : <Text fontSize="var(--secondaryText)">No app favorites.</Text>}
                </div>
            </Flex>
        }

        {active !== "Runs" ? null : (
            <Flex flexDirection={"column"}>
                <h3 className={"no-link"}>Running jobs</h3>
                {recentRuns.data.items.map(it =>
                    <AppTitleAndLogo
                        key={it.id}
                        to={AppRoutes.jobs.view(it.id)}
                        name={it.specification.application.name}
                        title={`${it.specification.name ?? it.id} (${it.specification.application.name})`}
                    />
                )}
                {recentRuns.data.items.length !== 0 ? null : <div>No jobs running.</div>}
            </Flex>
        )}

        {active !== "Resources" ? null : (<ResourceLinks />)}

        {active !== "Admin" ? null : (<AdminLinks />)}
    </div >;
}

function AppTitleAndLogo({name, title, to}): JSX.Element {
    return <Link to={to}>
        <Flex alignItems={"center"}>
            <Flex
                p="2px"
                mr="4px"
                justifyContent="center"
                alignItems="center"
                backgroundColor="var(--fixedWhite)"
                width="16px"
                height="16px"
                borderRadius="4px"
            >
                <AppToolLogo size="12px" name={name} type="APPLICATION" />
            </Flex>
            <Truncate color="#fff">
                {title}
            </Truncate>
        </Flex>
    </Link>
}

function Username({close}: {close(): void}): JSX.Element | null {
    if (!Client.isLoggedIn) return null;
    return <Tooltip
        trigger={(
            <EllipsedText
                className={HoverClass}
                cursor="pointer"
                onClick={() => {
                    copyUserName();
                    close();
                }}
                width={"100%"}
            >
                <Icon name="id" color="black" color2="gray" mr="0.5em" my="0.2em" size="1.3em" /> {Client.username}
            </EllipsedText>
        )}
    >
        Click to copy {Client.username} to clipboard
    </Tooltip>
}

function ProjectID({close}: {close(): void}): JSX.Element | null {
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
        trigger={
            <EllipsedText
                className={HoverClass}
                cursor="pointer"
                onClick={() => {
                    copyProjectPath();
                    close();
                }}
                width={"100%"}
            >
                <Icon key={projectId} name={"projects"} color2="white" color="black" mr="0.5em" my="0.2em"
                    size="1.3em" />{projectPath}
            </EllipsedText>
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
        <Tooltip trigger={<Icon color="yellow" name="warning" />}>
            Upcoming downtime.<br />
            Click to view
        </Tooltip>
    </Link>
}

function copyUserName(): void {
    copyToClipboard({
        value: Client.username,
        message: "Username copied to clipboard"
    });
}

function useSidebarReduxProps(): SidebarStateProps {
    const loggedIn = Client.isLoggedIn;
    const avatar = useSelector((it: ReduxObject) => it.avatar);
    return {
        loggedIn,
        avatar
    }
}
