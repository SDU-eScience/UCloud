import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {
    copyToClipboard,
    displayErrorMessageOrDefault,
    joinToString,
    useFrameHidden
} from "@/UtilityFunctions";
import CONF from "../../site.config.json";
import Box from "./Box";
import ExternalLink from "./ExternalLink";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import {EllipsedText, TextSpan} from "./Text";
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
import {APICallState, callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {findAvatar} from "@/UserSettings/Redux";
import BackgroundTasks from "@/Services/BackgroundTasks/BackgroundTask";
import ClickableDropdown from "./ClickableDropdown";
import Divider from "./Divider";
import {ThemeToggler} from "./ThemeToggle";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {Page, PageV2, compute} from "@/UCloud";
import {sharesLinksInfo} from "@/Files/Shares";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import {fileName} from "@/Utilities/FileUtilities";
import JobsApi, {Job} from "@/UCloud/JobsApi";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import Relative from "./Relative";
import Absolute from "./Absolute";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {setAppFavorites} from "@/Applications/Redux/Actions";
import {checkCanConsumeResources} from "./ResourceBrowser";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {jobCache} from "@/Applications/Jobs/View";
import {CSSVarCurrentSidebarStickyWidth, CSSVarCurrentSidebarWidth} from "./List";
import {SidebarEmpty, SidebarEntry, SidebarLinkColumn, SidebarSectionHeader} from "@/ui-components/SidebarComponents";
import {AvatarType} from "@/AvataaarLib";
import {NewsPost} from "@/NewsPost";
import {sidebarFavoriteCache} from "@/Files/FavoriteCache";

const SecondarySidebarClass = injectStyle("secondary-sidebar", k => `
    ${k} {
        background-color: var(--sidebarSecondaryColor);
        transition: transform 0.1s;
        width: 0;
        display: flex;
        flex-direction: column;
        transform: translate(-300px, 0);
        box-sizing: border-box;
        overflow-y: auto;
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
    
    ${k} a.heading, ${k} h3.no-link  {
        margin-top: 15px;
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

function SidebarTab({icon}: SidebarElement): JSX.Element {
    return <Icon name={icon} hoverColor="fixedWhite" color="fixedWhite" color2="fixedWhite" size={"24"} />
}

interface MenuElement {
    icon: IconName;
    label: SidebarTabId;
    to: string | (() => string);
    show?: () => boolean;
}

interface SidebarMenuElements {
    items: MenuElement[];
    predicate: () => boolean;
}

enum SidebarTabId {
    NONE = "",
    FILES = "Files",
    WORKSPACE = "Workspace",
    RESOURCES = "Resources",
    APPLICATIONS = "Applications",
    RUNS = "Runs",
    ADMIN = "Admin",
}

const sideBarMenuElements: [
    SidebarMenuElements,
    SidebarMenuElements,
] = [
        {
            items: [
                {icon: "heroFolder", label: SidebarTabId.FILES, to: "/drives/"},
                {icon: "heroUserGroup", label: SidebarTabId.WORKSPACE, to: AppRoutes.project.usage()},
                {icon: "heroSquaresPlus", label: SidebarTabId.RESOURCES, to: AppRoutes.resources.publicIps()},
                {icon: "heroShoppingBag", label: SidebarTabId.APPLICATIONS, to: AppRoutes.apps.landing()},
                {icon: "heroServer", label: SidebarTabId.RUNS, to: "/jobs/"}
            ],
            predicate: () => Client.isLoggedIn
        },
        {
            items: [
                {icon: "heroBolt", label: SidebarTabId.ADMIN, to: AppRoutes.admin.userCreation()}
            ],
            predicate: () => Client.userIsAdmin
        }
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

    const [selectedPage, setSelectedPage] = React.useState<SidebarTabId>(SidebarTabId.NONE);
    const [hoveredPage, setHoveredPage] = React.useState<SidebarTabId>(SidebarTabId.NONE);

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
                            setHoveredPage(SidebarTabId.NONE)
                        }
                    }}
                >
                    {sidebar.map(({label, icon, to}) =>
                        to ? (
                            <Link hoverColor="fixedWhite" key={label} to={typeof to === "function" ? to() : to}>
                                <div
                                    data-active={label === selectedPage}
                                    onMouseEnter={() => setHoveredPage(label)}
                                    onClick={() => {
                                        if (selectedPage) {
                                            setSelectedPage(label);
                                        }
                                    }}
                                    className={SidebarMenuItem}
                                >
                                    <SidebarTab icon={icon} />
                                </div>
                            </Link>) : <div
                                key={label}
                                data-active={label === selectedPage}
                                onClick={() => {
                                    if (selectedPage) {
                                        setSelectedPage(label);
                                    }
                                }}
                                onMouseEnter={() => setHoveredPage(label)}
                                className={SidebarMenuItem}
                            >
                            <SidebarTab icon={icon} />
                        </div>
                    )}
                </div>

                <>
                    {/* (Typically) invisible elements here to run various background tasks */}
                    <AutomaticGiftClaim />
                    <ResourceInit />
                    <VersionManager />
                    <BackgroundTasks />
                </>

                <Flex flexDirection={"column"} gap={"18px"} alignItems={"center"}>
                    <Downtimes />
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
                clearHover={() => setHoveredPage(SidebarTabId.NONE)}
                clearClicked={() => setSelectedPage(SidebarTabId.NONE)}
            />
        </Flex>
    );
}

function useSidebarFilesPage(): [
    APICallState<PageV2<FileCollection>>,
    FileMetadataAttached[]
] {
    const [drives, fetchDrives] = useCloudAPI<PageV2<FileCollection>>({noop: true}, {items: [], itemsPerPage: 0});

    const favorites = React.useSyncExternalStore(s => sidebarFavoriteCache.subscribe(s), () => sidebarFavoriteCache.getSnapshot());

    React.useEffect(() => {
        sidebarFavoriteCache.fetch();
    }, []);

    const projectId = useProjectId();

    React.useEffect(() => {
        fetchDrives(FileCollectionsApi.browse({itemsPerPage: 10/* , filterMemberFiles: "all" */}))
    }, [projectId]);

    return [
        drives,
        favorites.items.slice(0, 10)
    ];
}

function useSidebarRunsPage(): Job[] {
    const projectId = useProjectId();

    const cache = React.useSyncExternalStore(s => jobCache.subscribe(s), () => jobCache.getSnapshot());

    React.useEffect(() => {
        callAPI(JobsApi.browse({itemsPerPage: 100, filterState: "RUNNING"})).then(result => {
            jobCache.updateCache(result, true);
        }).catch(e => displayErrorMessageOrDefault(e, "Failed to fetch running jobs."));
    }, [projectId]);

    return cache.items.slice(0, 10);
}

interface SecondarySidebarProps {
    hovered: SidebarTabId;
    clicked: SidebarTabId;
    clearHover(): void;
    clearClicked(): void;
    setSelectedPage: React.Dispatch<React.SetStateAction<string>>;
}

function isShare(d: FileCollection) {
    return d.specification.product.id === "share";
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
    const activeProjectId = useProjectId();
    const isPersonalWorkspace = !activeProjectId;

    const onClear = useCallback(() => {
        clearHover();
        clearClicked();
    }, [clearHover, clearClicked]);

    const [favoriteApps] = useCloudAPI<Page<compute.ApplicationSummaryWithFavorite>>(
        compute.apps.retrieveFavorites({itemsPerPage: 100, page: 0}),
        {items: [], itemsPerPage: 0, itemsInTotal: 0, pageNumber: 0},
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
        const firstLevel = parseInt(getCssPropertyValue("sidebarWidth").replace("px", ""), 10);
        const secondLevel = parseInt(getCssPropertyValue("secondarySidebarWidth").replace("px", ""), 10);

        let sum = firstLevel;
        if (isOpen) sum += secondLevel;
        if (asPopOver) {
            document.body.style.setProperty("--sidebarBlockWidth", `${firstLevel}px`);
        } else {
            document.body.style.setProperty("--sidebarBlockWidth", `${sum}px`);
        }

        document.body.style.setProperty(CSSVarCurrentSidebarWidth, `${sum}px`);
        document.body.style.setProperty(CSSVarCurrentSidebarStickyWidth, isOpen && !asPopOver ? `${sum}px` : `${firstLevel}px`);
    }, [isOpen, asPopOver]);

    const onMenuClick = useCallback((ev: React.SyntheticEvent) => {
        function isAnchor(elem: HTMLElement): boolean {
            if (elem.tagName === "A") return true;
            if (elem.parentElement === null) return false;
            return isAnchor(elem.parentElement);
        }
        const target = ev.target as HTMLElement;
        if (isAnchor(target)) clearHover();
    }, [clearHover]);

    return <div
        className={classConcat(SecondarySidebarClass, SIDEBAR_IDENTIFIER)}
        onMouseLeave={e => {
            if (!hasOrParentHasClass(e.relatedTarget, SIDEBAR_IDENTIFIER)) clearHover();
        }}
        data-open={isOpen}
        data-as-pop-over={!!asPopOver}
        onClick={onMenuClick}
    >
        <header>
            <h1>{active}</h1>

            <Relative top="16px" right="2px" height={0} width={0}>
                <Absolute>
                    <Flex alignItems="center" backgroundColor="white" height="38px" borderRadius="12px 0 0 12px" onClick={clicked ? onClear : () => setSelectedPage(hovered)}>
                        <Icon name="chevronDownLight" size={18} rotation={clicked ? 90 : -90} color="blue" />
                    </Flex>
                </Absolute>
            </Relative>
        </header>

        <Flex flexDirection={"column"} gap={"5px"}>
            {active !== SidebarTabId.FILES ? null : <>
                <SidebarSectionHeader to={AppRoutes.files.drives()}>Drives</SidebarSectionHeader>
                {(!canConsume || drives.data.items.length === 0) && <>
                    <SidebarEmpty>No drives available</SidebarEmpty>
                </>}

                {canConsume && drives.data.items.slice(0, 8).map(drive =>
                    <SidebarEntry
                        key={drive.id}
                        text={drive.specification.title}
                        icon={isShare(drive) ? "ftSharesFolder" : <ProviderLogo providerId={drive.specification.product.provider} size={20} />}
                        to={AppRoutes.files.drive(drive.id)}
                    />
                )}

                {canConsume && favoriteFiles.length > 0 && <>
                    <SidebarSectionHeader>Starred files</SidebarSectionHeader>
                    {favoriteFiles.map(file =>
                        <SidebarEntry
                            key={file.path}
                            to={AppRoutes.files.path(file.path)}
                            icon={"heroStar"}
                            text={fileName(file.path)}
                        />
                    )}
                </>}

                {canConsume && sharesLinksInfo.length > 0 && <>
                    <SidebarSectionHeader>Shared files</SidebarSectionHeader>
                    <SidebarLinkColumn links={sharesLinksInfo} />
                </>}
            </>}

            {active !== SidebarTabId.WORKSPACE ? null : <>
                {!isPersonalWorkspace && <>
                    <SidebarSectionHeader to={AppRoutes.project.members()}>Management</SidebarSectionHeader>
                    <SidebarEntry
                        to={AppRoutes.project.members()}
                        text={"Members"}
                        icon={"heroUsers"}
                    />

                    <SidebarEntry
                        to={AppRoutes.project.subprojects()}
                        icon={"heroUserGroup"}
                        text={"Sub-projects"}
                    />

                    <SidebarEntry
                        to={AppRoutes.project.settings("")}
                        text={"Settings"}
                        icon={"heroWrenchScrewdriver"}
                    />
                </>}

                <SidebarSectionHeader to={AppRoutes.accounting.allocations()}>Resources</SidebarSectionHeader>
                <SidebarEntry
                    to={AppRoutes.accounting.allocations()}
                    text={"Allocations"}
                    icon={"heroBanknotes"}
                />

                <SidebarEntry
                    to={AppRoutes.accounting.usage()}
                    text={"Usage"}
                    icon={"heroPresentationChartLine"}
                />

                <SidebarEntry
                    to={AppRoutes.grants.outgoing()}
                    text={"Grant applications"}
                    icon={"heroDocumentText"}
                />

                <SidebarEntry
                    to={AppRoutes.grants.editor()}
                    text={"Apply for resources"}
                    icon={"heroPencilSquare"}
                />
            </>}

            {active !== SidebarTabId.RESOURCES ? null : <>
                <SidebarSectionHeader to={AppRoutes.resources.publicLinks()}>Networking</SidebarSectionHeader>
                <SidebarEntry
                    to={AppRoutes.resources.publicLinks()}
                    text={"Links"}
                    icon={"heroLink"}
                />
                <SidebarEntry
                    to={AppRoutes.resources.publicIps()}
                    text={"IP addresses"}
                    icon={"heroGlobeEuropeAfrica"}
                />

                <SidebarSectionHeader to={AppRoutes.resources.sshKeys()}>Security & keys</SidebarSectionHeader>
                <SidebarEntry
                    to={AppRoutes.resources.sshKeys()}
                    text={"SSH keys"}
                    icon={"heroKey"}
                />

                <SidebarSectionHeader to={AppRoutes.resources.licenses()}>Software</SidebarSectionHeader>
                <SidebarEntry
                    to={AppRoutes.resources.licenses()}
                    text={"Licenses"}
                    icon={"heroDocumentCheck"}
                />
            </>}

            {active !== SidebarTabId.APPLICATIONS ? null : <>
                <SidebarSectionHeader to={AppRoutes.apps.landing()}>Categories</SidebarSectionHeader>
                {appStoreSections.data.sections.length === 0 && <>
                    <SidebarEmpty>No applications found</SidebarEmpty>
                </>}

                {appStoreSections.data.sections.map(section =>
                    <SidebarEntry
                        key={section.id}
                        to={AppRoutes.apps.section(section.id)}
                        text={section.name}
                        icon={"heroCpuChip"}
                    />
                )}

                {appFavorites.length > 0 && <>
                    <SidebarSectionHeader>Starred applications</SidebarSectionHeader>
                    {appFavorites.map((fav, i) =>
                        <SidebarEntry
                            key={i}
                            to={AppRoutes.jobs.create(fav.metadata.name, fav.metadata.version)}
                            text={fav.metadata.title}
                            icon={<AppLogo name={fav.metadata.name} />}
                        />
                    )}
                </>}
            </>}

            {active !== SidebarTabId.RUNS ? null : <>
                <SidebarSectionHeader>Running jobs</SidebarSectionHeader>
                {recentRuns.length === 0 && <>
                    <SidebarEmpty>No running jobs</SidebarEmpty>
                </>}
                {recentRuns.map(run => {
                    let name = run.specification.name;
                    if (!name) {
                        const appName = run.status.resolvedApplication?.metadata?.title ?? run.specification.application.name;
                        name = `${run.id} (${appName})`;
                    }

                    return <SidebarEntry
                        key={run.id}
                        to={AppRoutes.jobs.view(run.id)}
                        text={name}
                        icon={<AppLogo name={run.specification.application.name} />}
                    />
                })}
            </>}

            {active !== SidebarTabId.ADMIN ? null : <>
                <SidebarSectionHeader>Tools</SidebarSectionHeader>
                <SidebarEntry to={AppRoutes.admin.userCreation()} text={"User creation"} icon={"heroUser"} />
                <SidebarEntry to={AppRoutes.admin.applicationStudio()} text={"Application studio"}
                    icon={"heroBuildingStorefront"} />
                <SidebarEntry to={AppRoutes.admin.news()} text={"News"} icon={"heroNewspaper"} />
                <SidebarEntry to={AppRoutes.admin.providers()} text={"Providers"} icon={"heroCloud"} />
                <SidebarEntry to={AppRoutes.admin.scripts()} text={"Scripts"} icon={"heroPlayPause"} />
            </>}
        </Flex>
    </div>;
}

function AppLogo({name}: {name: string}): JSX.Element {
    return <Flex alignItems={"center"}>
        <Flex
            p="2px"
            mr="4px"
            justifyContent="center"
            alignItems="center"
            backgroundColor="var(--fixedWhite)"
            width="16px"
            height="16px"
            minHeight="16px"
            minWidth="16px"
            borderRadius="4px"
        >
            <AppToolLogo size="12px" name={name} type="APPLICATION" />
        </Flex>
    </Flex>;
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
    const [downtimes, fetchDowntimes] = useCloudAPI<Page<NewsPost>>({noop: true}, {items: [], itemsPerPage: 0, itemsInTotal: 0, pageNumber: 0});
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
        <Tooltip trigger={<Icon size="24" color="yellow" name="warning" />}>
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
