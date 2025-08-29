import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {useDispatch, useSelector} from "react-redux";
import {
    bulkRequestOf,
    copyToClipboard,
    displayErrorMessageOrDefault, doNothing,
    isLightThemeStored,
    isLikelyMac,
    joinToString,
    useEffectSkipMount,
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
import {Page, PageV2} from "@/UCloud";
import {sharesLinksInfo} from "@/Files/Shares";
import {ProviderLogo, providerLogoPath} from "@/Providers/ProviderLogo";
import {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import JobsApi, {Job} from "@/UCloud/JobsApi";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import Relative from "./Relative";
import {SafeLogo} from "@/Applications/AppToolLogo";
import {setAppFavorites} from "@/Applications/Redux/Actions";
import {checkCanConsumeResources} from "./ResourceBrowser";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {jobCache} from "@/Applications/Jobs/View";
import {CSSVarCurrentSidebarStickyWidth, CSSVarCurrentSidebarWidth} from "./List";
import {
    LinkInfo,
    SidebarEmpty,
    SidebarEntry,
    SidebarLinkColumn,
    SidebarSectionHeader,
    SidebarTabId
} from "@/ui-components/SidebarComponents";
import {AvatarType} from "@/AvataaarLib";
import {NewsPost} from "@/NewsPost";
import {sidebarFavoriteCache} from "@/Files/FavoriteCache";
import * as AppStore from "@/Applications/AppStoreApi";
import {ApplicationSummaryWithFavorite} from "@/Applications/AppStoreApi";
import {isAdminOrPI} from "@/Project";
import {FileType} from "@/Files";
import metadataDocumentApi from "@/UCloud/MetadataDocumentApi";
import {onProjectUpdated, projectCache, projectTitle} from "@/Project/ProjectSwitcher";
import {HookStore, useGlobal} from "@/Utilities/ReduxHooks";
import {useDiscovery} from "@/Applications/Hooks";
import {Command, CommandPalette, CommandScope, staticProvider, useProvideCommands} from "@/CommandPalette";
import {NavigateFunction, useNavigate} from "react-router";
import {dispatchSetProjectAction} from "@/Project/ReduxState";
import {Dispatch} from "redux";
import {Feature, hasFeature} from "@/Features";

const SecondarySidebarClass = injectStyle("secondary-sidebar", k => `
    ${k} {
        background-color: var(--sidebarSecondaryColor);
        transition: transform 0.25s cubic-bezier(0.5,1,0.5,1);
        display: flex;
        width: var(--secondarySidebarWidth);
        position: absolute;
        flex-direction: column;
        left: 0;
        box-sizing: border-box;
        overflow-y: auto;
        overflow-x: hidden;
        height: 100vh;
        padding: 13px 16px 16px 16px;
        z-index: 10;
        font-size: 14px;
    }

    ${k}[data-open="false"] {
        transform: translateX(-100%);
    }
    
    ${k}[data-open="true"][data-as-pop-over="true"] {
        transform: translateX(var(--sidebarWidth));
    }

    ${k}[data-open="true"][data-as-pop-over="false"] {
        position: static;
    }
    
    @media screen and (max-width: 640px) {
        ${k}[data-open="true"][data-as-pop-over="true"] {
            position: absolute;
            transform: translateX(var(--sidebarWidth));
        }
    }

    ${k}, ${k} a, ${k} a:hover {
        color: white;
    } 

    ${k} header {
        align-items: center;
    }
    
    ${k} header h1 {
        font-size: 20px;
        flex-grow: 1;
        margin: 0;
    }
    
    ${k} header div {
        cursor: pointer;
    }

    ${k} h2, ${k} h3 {
        margin: 0;
    }
    
    ${k} h3 {
        font-size: 16px;
    }

    ${k} .heading, ${k} a.heading, ${k} h3.no-link, ${k} a, ${k} h3.no-link, ${k} h1 {
        user-select: none;
        -webkit-user-select: none;
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
        background-color: rgba(255, 255, 255, 0.25);
    }

`);


const SidebarContainerClass = injectStyleSimple("sidebar-container", `
    color: var(--sidebarColor);
    align-items: center;
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: var(--sidebarWidth);

    /* Note(Jonas): Required by Safari */
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
        background-color: rgba(255, 255, 255, 0.25);
    }
    
    ${k} > * {
        margin: auto;
    }
`);


interface SidebarElement {
    icon: IconName;
}

function SidebarTab({icon}: SidebarElement): React.ReactNode {
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
    predicate: (state: HookStore) => boolean;
}

const sideBarMenuElements: [
    SidebarMenuElements,
    SidebarMenuElements,
    SidebarMenuElements,
] = [
        {
            items: [
                {icon: "heroFolder", label: SidebarTabId.FILES, to: AppRoutes.files.drives()},
                {icon: "heroUserGroup", label: SidebarTabId.PROJECT, to: AppRoutes.project.allocations()},
                {icon: "heroSquaresPlus", label: SidebarTabId.RESOURCES, to: AppRoutes.resources.publicLinks()},
                {icon: "heroShoppingBag", label: SidebarTabId.APPLICATIONS, to: AppRoutes.apps.landing()},
                {icon: "heroServer", label: SidebarTabId.RUNS, to: AppRoutes.jobs.list()}
            ],
            predicate: () => Client.isLoggedIn
        },
        {
            items: [
                {icon: "heroBolt", label: SidebarTabId.ADMIN, to: AppRoutes.admin.userCreation()},
            ],
            predicate: () => Client.userIsAdmin
        },
        {
            items: [
                {icon: "heroBuildingStorefront", label: SidebarTabId.APPLICATION_STUDIO, to: AppRoutes.appStudio.groups()}
            ],
            predicate: (state) => {
                return Client.userIsAdmin;
            }
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

function UserMenuLink(props: {icon: IconName; text: string; to: string; close(): void;}): React.ReactNode {
    return <Link color="textPrimary" onClick={props.close} hoverColor="textPrimary" height="28px" to={props.to}>
        <Flex className={HoverClass}>
            <Icon name={props.icon} mr="0.5em" my="0.2em"
                size="1.3em" />
            <TextSpan color="var(--textPrimary)">{props.text}</TextSpan>
        </Flex>
    </Link>
}

function UserMenuExternalLink(props: {
    icon: IconName;
    href: string;
    text: string;
    close(): void;
}): React.ReactNode {
    if (!props.text) return null;
    return <div className={HoverClass}>
        <ExternalLink hoverColor="textPrimary" onClick={props.close} href={props.href}>
            <Icon name={props.icon} mr="0.5em" my="0.2em" size="1.3em" />
            <TextSpan color="textPrimary">{props.text}</TextSpan>
        </ExternalLink>
    </div>
}

function UserMenu({avatar, dialog, setOpenDialog}: {
    avatar: AvatarType;
} & SidebarDialog) {

    const close = React.useRef(() => undefined);
    React.useEffect(() => {
        function closeOnEscape(e: KeyboardEvent) {
            if (e.key === "Escape") {
                close.current();
            }
        }
        window.addEventListener("keydown", closeOnEscape);
        return () => {
            window.removeEventListener("keydown", closeOnEscape);
        }
    }, []);

    const isOpen = dialog === "UserMenu";

    return <ClickableDropdown
        width="230px"
        paddingControlledByContent
        left="calc(var(--sidebarWidth) + 5px)"
        bottom="0"
        open={isOpen}
        onOpeningTriggerClick={() => setOpenDialog("UserMenu")}
        onClose={() => setOpenDialog("")}
        closeFnRef={close}
        colorOnHover={false}
        trigger={Client.isLoggedIn ?
            <UserAvatar height="42px" width="42px" avatar={avatar} /> : null}
    >
        <Box py="12px">
            {!CONF.STATUS_PAGE ? null : (
                <>
                    <Box className={HoverClass}>
                        <ExternalLink onClick={close.current} href={CONF.STATUS_PAGE}>
                            <Flex>
                                <Icon name="favIcon" mr="0.5em" my="0.2em" size="1.3em" color="textPrimary" />
                                <TextSpan color="textPrimary">Site status</TextSpan>
                            </Flex>
                        </ExternalLink>
                    </Box>
                    <Divider />
                </>
            )}
            <UserMenuLink close={close.current} icon="heroWrenchScrewdriver" text="Settings"
                to={AppRoutes.users.settings()} />
            <UserMenuLink close={close.current} icon="heroUser" text="Edit avatar" to={AppRoutes.users.avatar()} />
            <UserMenuExternalLink close={close.current} href={CONF.SITE_DOCUMENTATION_URL} icon="heroBookOpen"
                text={CONF.PRODUCT_NAME ? CONF.PRODUCT_NAME + " docs" : ""} />
            <UserMenuExternalLink close={close.current} href={CONF.DATA_PROTECTION_LINK} icon="heroShieldCheck"
                text={CONF.DATA_PROTECTION_TEXT} />
            <Divider />
            <Username close={close.current} />
            <ProjectID close={close.current} />
            <CommandPaletteEntry />
            <Divider />
            <Flex className={HoverClass} onClick={() => Client.logout()} data-component={"logout-button"}>
                <Icon name="heroArrowRightOnRectangle" color2="textPrimary" mr="0.5em" my="0.2em" size="1.3em" />
                Logout
            </Flex>
        </Box>
    </ClickableDropdown>;
}

const CTRL_KEY = isLikelyMac ? "⌘" : "ctrl";

function CommandPaletteEntry(): React.ReactNode {
    if (!hasFeature(Feature.COMMAND_PALETTE)) return null;
    return <Flex className={HoverClass} onClick={e => {
        window.dispatchEvent(new KeyboardEvent("keydown", {code: "KeyP", ctrlKey: true, metaKey: true}));
    }}>
        <Icon name="heroCommandLine" color2="textPrimary" mr="0.5em" my="0.2em" size="1.3em" /> Command palette
        <TextSpan ml="0.5em" color="textSecondary">({CTRL_KEY} + P)</TextSpan>
    </Flex>
}

const HoverClass = injectStyle("hover-class", k => `
    ${k} {
        padding-left: 12px;
    }

    ${k}:hover {
        background: var(--rowHover);
    }
`);

function allSidebarCommands(state: HookStore, navigate: NavigateFunction): Command[] {
    const result: Command[] = []

    const projectId = state.projectCache?.project.id;
    const canApply = !projectId || isAdminOrPI(state.projectCache?.project.status.myRole);
    const sidebarSubCommands = sidebarSubEntries(canApply, !projectId, projectId);

    for (const group of sideBarMenuElements) {
        if (group.predicate(state)) {
            for (const it of group.items) {
                if (![SidebarTabId.FILES, SidebarTabId.RESOURCES, SidebarTabId.PROJECT].includes(it.label)) {
                    const to = typeof it.to === "string" ? it.to : it.to();
                    result.push(sidebarCommand(it.label, "", to, it.icon, navigate));
                }
                const sub = sidebarSubCommands[it.label];
                for (const subIt of sub) {
                    result.push(sidebarCommand(subIt.text, "", subIt.to, subIt.icon as IconName, navigate, subIt.defaultHidden));
                }
            }
        }
    };

    return result;
}

function sidebarSubEntries(canApply: boolean, isPersonalWorkspace: boolean, projectId: string | undefined): Record<SidebarTabId, LinkInfo[]> {
    return {
        [SidebarTabId.FILES]: [{to: AppRoutes.files.drives(), text: "Drives", icon: "ftFileSystem", tab: SidebarTabId.FILES}, ...(isPersonalWorkspace ? sharesLinksInfo : [])],
        [SidebarTabId.PROJECT]: projectSidebarSubLinks(canApply, isPersonalWorkspace, projectId),
        [SidebarTabId.RESOURCES]: ResourceSubLinksEntries,
        [SidebarTabId.APPLICATIONS]: [],
        [SidebarTabId.RUNS]: [],
        [SidebarTabId.ADMIN]: [],
        [SidebarTabId.APPLICATION_STUDIO]: ApplicationStudioSubLinksEntries,
        [SidebarTabId.NONE]: [],
    };
}

function sidebarCommand(title: string, description: string, url: string, icon: IconName, navigate: NavigateFunction, defaultHidden?: boolean): Command {
    return {
        title,
        description,
        action() {
            navigate(url);
        },
        icon: {type: "simple", icon},
        defaultHidden,
        scope: CommandScope.GoTo,
    }
}

export function Sidebar(): React.ReactNode {
    const sidebarEntries = sideBarMenuElements;
    const {loggedIn, avatar} = useSidebarReduxProps();

    const [selectedPage, setSelectedPage] = React.useState(SidebarTabId.NONE);
    const [hoveredPage, setHoveredPage] = React.useState(SidebarTabId.NONE);

    const tab = useSelector((it: {status: {tab: SidebarTabId}}) => it.status.tab);

    const dispatch = useDispatch();
    React.useEffect(() => {
        if (Client.isLoggedIn) {
            findAvatar().then(action => {
                if (action !== null) dispatch(action);
            });
        }
    }, []);

    const onLogoClick = useCallback(() => {
        setHoveredPage(SidebarTabId.NONE);
    }, [setHoveredPage]);

    const reduxState = useSelector<ReduxObject, HookStore>(it => it.hookStore);
    const navigate = useNavigate();

    useProvideCommands(staticProvider(allSidebarCommands(reduxState, navigate)));
    const [dialog, setOpenDialog] = React.useState<DialogOptions>("");

    if (useFrameHidden()) return null;
    if (!loggedIn) return null;

    const sidebar: MenuElement[] = sidebarEntries
        .filter(it => it.predicate(reduxState))
        .flatMap(category => category.items.filter((it: MenuElement) => it?.show?.() ?? true));

    return (
        <Flex>
            <div className={classConcat(SidebarContainerClass, SIDEBAR_IDENTIFIER)}>
                <Link data-component={"logo"} title={`Go to dashboard`} aria-label={`Go to dashboard`} to="/" onClick={onLogoClick}>
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
                            <Link hoverColor="fixedWhite" title={`Go to ${label}`} aria-label={`Go to ${label}`} key={label} to={typeof to === "function" ? to() : to}>
                                <div
                                    data-active={tab === label}
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
                                data-active={tab === label}
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
                </>

                <Flex flexDirection={"column"} gap={"18px"} alignItems={"center"}>
                    <Downtimes />
                    <ThemeToggler />
                    <BackgroundTasks dialog={dialog} setOpenDialog={setOpenDialog} />
                    <Notification dialog={dialog} setOpenDialog={setOpenDialog} />
                    <Support dialog={dialog} setOpenDialog={setOpenDialog} />
                    <UserMenu avatar={avatar} dialog={dialog} setOpenDialog={setOpenDialog} />
                    <CommandPalette />
                </Flex>
            </div>

            <SecondarySidebar
                key={(!!selectedPage).toString()} /* Note(Jonas) Needed for Safari to update correctly  */
                data-tag="secondary"
                hovered={hoveredPage}
                clicked={selectedPage}
                setSelectedPage={setSelectedPage}
                setHoveredPage={setHoveredPage}
                clearHover={() => setHoveredPage(SidebarTabId.NONE)}
                clearClicked={() => setSelectedPage(SidebarTabId.NONE)}
            />
        </Flex>
    );
}

type DialogOptions = "BackgroundTask" | "Notifications" | "Support" | "UserMenu" | "";
type SetOpenDialog = React.Dispatch<React.SetStateAction<DialogOptions>>;
export interface SidebarDialog {
    dialog: DialogOptions;
    setOpenDialog: SetOpenDialog;
}

const fileTypeCache: Record<string, FileType | "DELETED"> = {}
function useSidebarFilesPage(): [
    APICallState<PageV2<FileCollection>>,
    FileMetadataAttached[]
] {
    const [drives, fetchDrives] = useCloudAPI<PageV2<FileCollection>>({noop: true}, {items: [], itemsPerPage: 0});

    const favorites = React.useSyncExternalStore(s => sidebarFavoriteCache.subscribe(s), () => sidebarFavoriteCache.getSnapshot());
    const navigate = useNavigate();

    useProvideCommands(staticProvider(drives.data.items.map(d => ({
        title: d.specification.title,
        icon: isShare(d) ?
            {type: "simple", icon: "ftSharesFolder", color: "FtFolderColor", color2: "FtFolderColor2"} :
            {type: "image", imageUrl: providerLogoPath(d.specification.product.provider)},
        action() {
            navigate(AppRoutes.files.drive(d.id));
        },
        description: d.updates[0]?.status ?? "",
        scope: CommandScope.File,
    }))));

    useProvideCommands(staticProvider(favorites.items.map(f => ({
        title: fileName(f.path),
        icon: {type: "simple", icon: "starFilled"},
        action() {
            if (fileTypeCache[f.path] === "DIRECTORY") {
                navigate(AppRoutes.files.path(f.path));
            } else {
                navigate(AppRoutes.files.path(getParentPath(f.path)));
            }
        },
        description: "",
        scope: CommandScope.File,
    }))));

    React.useEffect(() => {
    }, [favorites])

    React.useEffect(() => {
        favorites.items.filter(it => fileTypeCache[it.path] == null).forEach(async file => {
            try {
                const f = await callAPI(FilesApi.retrieve({id: file.path}))
                fileTypeCache[file.path] = f.status.type;
            } catch (e) {
                if (e?.request?.status === 404) {
                    fileTypeCache[file.path] = "DELETED";
                    callAPI(
                        metadataDocumentApi.delete(
                            bulkRequestOf({
                                changeLog: "File no longer exists.",
                                id: file.metadata.id
                            })
                        )
                    );
                }
            }
        });
    }, [favorites]);

    React.useEffect(() => {
        if (!sidebarFavoriteCache.initialized) {
            sidebarFavoriteCache.fetch();
        }
    }, []);

    const projectId = useProjectId();

    React.useEffect(() => {
        fetchDrives(FileCollectionsApi.browse({itemsPerPage: 250/* , filterMemberFiles: "all" */}))
    }, [projectId]);

    return [
        drives,
        favorites.items.slice(0, 10)
    ];
}

function useSidebarRunsPage(): Job[] {
    const projectId = useProjectId();
    const isLight = isLightThemeStored();
    const navigate = useNavigate();

    const cache = React.useSyncExternalStore(s => jobCache.subscribe(s), () => jobCache.getSnapshot());

    useProvideCommands(staticProvider(cache.items.map(j => ({
        title: j.specification.name ?? j.id,
        icon: {type: "image", imageUrl: AppStore.retrieveAppLogo({name: j.specification.application.name, includeText: false, darkMode: !isLight})},
        action() {
            navigate(AppRoutes.jobs.view(j.id));
        },
        description: "",
        scope: CommandScope.Job,
    }))));

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

    setSelectedPage: React.Dispatch<React.SetStateAction<SidebarTabId>>;
    setHoveredPage: React.Dispatch<React.SetStateAction<SidebarTabId>>;
}

function isShare(d: FileCollection) {
    return d.specification.product.id === "share";
}

function myWorkspaceProjectCommand(navigate: NavigateFunction, dispatch: Dispatch): Command {
    return {
        title: "My workspace",
        action() {
            onProjectUpdated(navigate, () => dispatchSetProjectAction(dispatch, undefined), () => void 0, undefined)
        },
        icon: {type: "simple", icon: "user"},
        description: "",
        scope: CommandScope.Project
    }
}

function ResourceSubLinks(): React.ReactNode {
    return ResourceSubLinksEntries.map(it => <SidebarEntry key={it.text} {...it} />);
}

const ResourceSubLinksEntries: LinkInfo[] = [{
    to: AppRoutes.resources.publicLinks(), text: "Links", icon: "heroLink", tab: SidebarTabId.RESOURCES
}, {
    to: AppRoutes.resources.publicIps(), text: "IP addresses", icon: "heroGlobeEuropeAfrica", tab: SidebarTabId.RESOURCES
}, {
    to: AppRoutes.resources.sshKeys(), text: "SSH keys", icon: "heroKey", tab: SidebarTabId.RESOURCES
}, {
    to: AppRoutes.resources.licenses(), text: "Licenses", icon: "heroDocumentCheck", tab: SidebarTabId.RESOURCES
}];

function ProjectSubLinks({canApply, isPersonalWorkspace, projectId}: {canApply: boolean; isPersonalWorkspace: boolean; projectId?: string}) {
    const sublinks = React.useMemo(() =>
        projectSidebarSubLinks(canApply, isPersonalWorkspace, projectId).filter(it => !it.disabled),
        [canApply, isPersonalWorkspace, projectId]);
    return sublinks.map(it => <SidebarEntry key={it.text} {...it} />);
}

function projectSidebarSubLinks(canApply: boolean, isPersonalWorkspace: boolean, projectId?: string): LinkInfo[] {
    const tab = SidebarTabId.PROJECT;
    const {allocations, usage} = AppRoutes.accounting;
    const {members, settings, subprojects} = AppRoutes.project;
    const {outgoing} = AppRoutes.grants;
    return [{
        to: members(), text: "Members", icon: "heroUsers", tab, disabled: isPersonalWorkspace,
    }, {
        to: settings(""), text: "Project settings", icon: "heroWrenchScrewdriver", tab, disabled: isPersonalWorkspace, defaultHidden: true,
    }, {
        to: allocations(), text: "Allocations", icon: "heroBanknotes", tab, defaultHidden: true,
    }, {
        to: subprojects(), icon: "heroUserGroup", text: "Sub-projects", tab, disabled: isPersonalWorkspace, defaultHidden: true,
    }, {
        to: usage(), text: "Usage", icon: "heroPresentationChartLine", tab
    }, {
        to: outgoing(), text: "Grant applications", icon: "heroDocumentText", tab, defaultHidden: true,
    },
    {
        to: !canApply || isPersonalWorkspace ? AppRoutes.grants.editor() : AppRoutes.grants.newApplication({projectId: projectId}),
        text: "Apply for resources",
        icon: "heroPencilSquare",
        disabled: !canApply,
        tab,
    }];
}


const ApplicationStudioSubLinksEntries = [{
    to: AppRoutes.appStudio.groups(), text: "Applications", icon: "heroSquare3Stack3D",
    tab: SidebarTabId.APPLICATION_STUDIO
},
{
    to: AppRoutes.appStudio.categories(), text: "Categories", icon: "heroSquaresPlus",
    tab: SidebarTabId.APPLICATION_STUDIO
},
{
    to: AppRoutes.appStudio.hero(), text: "Carrousel", icon: "heroFilm",
    tab: SidebarTabId.APPLICATION_STUDIO
},
{
    to: AppRoutes.appStudio.topPicks(), text: "Top picks", icon: "heroTrophy",
    tab: SidebarTabId.APPLICATION_STUDIO
},
{
    to: AppRoutes.appStudio.spotlights(), text: "Spotlights", icon: "heroCamera",
    tab: SidebarTabId.APPLICATION_STUDIO
}];

function ApplicationStudioSubLinks() {
    const isAdmin = Client.userIsAdmin;
    if (!isAdmin) return null;

    return ApplicationStudioSubLinksEntries.map(it => <SidebarEntry key={it.text} {...it} />)
}

function SecondarySidebar({
    hovered,
    clicked,
    setHoveredPage,
    clearHover,
    setSelectedPage,
    clearClicked
}: SecondarySidebarProps): React.ReactNode {
    const [drives, favoriteFiles] = useSidebarFilesPage();
    const recentRuns = useSidebarRunsPage();
    const activeProjectId = useProjectId();
    const lastHover = React.useRef(SidebarTabId.NONE);
    const isPersonalWorkspace = !activeProjectId;
    const project = useProject();
    const projectId = useProjectId();
    const canApply = isPersonalWorkspace || isAdminOrPI(project.fetch().status.myRole);

    const onClear = useCallback(() => {
        clearClicked();
    }, [clearClicked]);

    const [favoriteApps, fetchFavoriteApps] = useCloudAPI(
        AppStore.retrieveStars({}),
        {items: []}
    );

    const projects = projectCache.retrieveFromCacheOnly("");
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const projectCommands: Command[] = React.useMemo(() => {
        const projectActivations: Command[] = []
        for (const p of (projects?.items ?? [])) {
            projectActivations.push({
                title: p.specification.title,
                description: "",
                icon: {type: "simple", icon: "heroUserGroup"},
                scope: CommandScope.Project,
                action() {
                    onProjectUpdated(navigate, () => dispatchSetProjectAction(dispatch, p.id), () => void 0, p.id);
                },
            });
        }

        if (activeProjectId) {
            projectActivations.push(myWorkspaceProjectCommand(navigate, dispatch));
        }

        return projectActivations;
    }, [projects?.items, activeProjectId]);

    useProvideCommands(staticProvider(projectCommands));

    const [discoveryMode] = useDiscovery();
    const [landingPage, setLandingPage] = useGlobal("catalogLandingPage", AppStore.emptyLandingPage);
    const oldProjectId = React.useRef<string | null>(null);
    const oldDiscoveryMode = React.useRef<AppStore.CatalogDiscovery | null>(null);

    const canConsume = checkCanConsumeResources(projectId ?? null, {api: FilesApi});
    useEffect(() => {
        (async () => {
            const discoverHasChanged =
                discoveryMode.discovery !== oldDiscoveryMode.current?.discovery ||
                discoveryMode.selected !== oldDiscoveryMode.current?.selected;

            const projectHasChanged = oldProjectId.current !== projectId;

            if (discoverHasChanged || projectHasChanged) {
                const page = await callAPI(AppStore.retrieveLandingPage(discoveryMode));
                setLandingPage(page);
            }

            oldDiscoveryMode.current = discoveryMode;
            oldProjectId.current = projectId ?? null;
        })();
    }, [projectId, discoveryMode]);

    useEffectSkipMount(() => {
        fetchFavoriteApps(AppStore.retrieveStars({})).then(doNothing);
    }, [projectId]);

    React.useEffect(() => {
        if (favoriteApps.loading) return;
        dispatch(setAppFavorites(favoriteApps.data.items));
    }, [favoriteApps]);

    const appFavorites = useSelector<ReduxObject, ApplicationSummaryWithFavorite[]>(it => it.sidebar.favorites);
    const isLight = isLightThemeStored();

    useProvideCommands(staticProvider(appFavorites.map(fav => ({
        title: fav.metadata.title,
        icon: {type: "image", imageUrl: AppStore.retrieveAppLogo({name: fav.metadata.name, includeText: false, darkMode: !isLight})},
        action() {
            navigate(AppRoutes.jobs.create(fav.metadata.name, fav.metadata.version));
        },
        description: "Favorite app",
        scope: CommandScope.Application,
    }))));

    useProvideCommands(staticProvider(landingPage.carrousel.map(horse => ({
        title: horse.title,
        icon: {type: "image", imageUrl: AppStore.retrieveAppLogo({name: horse.resolvedLinkedApp ?? ""})},
        action() {
            if (horse.resolvedLinkedApp) {
                navigate(AppRoutes.jobs.create(horse.resolvedLinkedApp));
            } else if (horse.linkedGroup) {
                navigate(AppRoutes.apps.group((horse.linkedGroup).toString()));
            }
            if (horse.linkedWebPage) navigate(horse.linkedWebPage);
        },
        description: "Highlighted app",
        defaultHidden: true,
        scope: CommandScope.Application,
    }))));

    const carrouselTitles = landingPage.carrousel.map(horse => horse.title);
    useProvideCommands(staticProvider(landingPage.topPicks.filter(it => !carrouselTitles.includes(it.title)).map(pick => ({
        title: pick.title,
        icon: {
            type: "image", imageUrl: AppStore.retrieveGroupLogo({
                id: pick.groupId ?? -1,
                darkMode: !isLight,
                placeTextUnderLogo: false,
            })
        },
        action() {
            if (pick.groupId) {
                let link = AppRoutes.apps.group(pick.groupId.toString());
                if (pick.defaultApplicationToRun) {
                    link = AppRoutes.jobs.create(pick.defaultApplicationToRun);
                }
                navigate(link);
            }
        },
        description: "Top pick",
        defaultHidden: true,
        scope: CommandScope.Application,
    }))));



    const isOpen = clicked !== "" || hovered !== "";
    const active = !isOpen ? lastHover.current : hovered ? hovered : clicked;
    const asPopOver = hovered && !clicked;

    lastHover.current = active;

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
        function findAnchor(elem: HTMLElement): HTMLElement | null {
            if (elem.tagName === "A") return elem;
            if (elem.parentElement === null) return null;
            return findAnchor(elem.parentElement);
        }

        const target = ev.target as HTMLElement;
        const anchor = findAnchor(target);
        if (anchor) {
            const tab = anchor.getAttribute("data-tab") ?? "";
            setSelectedPage(current => {
                if (current) return tab as SidebarTabId;
                else return current;
            });
            clearHover();
        }
    }, [clearHover]);

    return <div
        className={classConcat(SecondarySidebarClass, SIDEBAR_IDENTIFIER)}
        onMouseLeave={e => {
            if (!hasOrParentHasClass(e.relatedTarget, SIDEBAR_IDENTIFIER)) clearHover();
        }}
        onMouseEnter={() => {
            if (active) {
                setHoveredPage(active);
            }
        }}
        data-open={isOpen}
        data-as-pop-over={!!asPopOver}
        onClick={onMenuClick}
    >
        <header>
            <h1>{active}</h1>

            <Relative left="calc(var(--secondarySidebarWidth) - 46px)">
                <Flex style={{position: "fixed", top: "calc(100vh - 68px)"}} alignItems="center" backgroundColor="white" height="38px" width={"30px"}
                    justifyContent={"center"} borderRadius="12px 0 0 12px"
                    onClick={clicked ? onClear : () => setSelectedPage(hovered)}>
                    <Icon name="heroChevronDown" size={18} rotation={clicked ? 90 : -90} color="primaryMain" />
                </Flex>
            </Relative>
        </header>

        <Flex flexDirection={"column"} gap={"5px"}>
            {active !== SidebarTabId.FILES ? null : <>
                <SidebarSectionHeader to={AppRoutes.files.drives()}
                    tab={SidebarTabId.FILES}>Drives</SidebarSectionHeader>
                {(!canConsume || drives.data.items.length === 0) && <>
                    <SidebarEmpty>No drives available</SidebarEmpty>
                </>}

                {canConsume ? drives.data.items.slice(0, 8).map(drive =>
                    <SidebarEntry
                        key={drive.id}
                        text={drive.specification.title}
                        icon={isShare(drive) ? <Icon mt="2px" name="ftSharesFolder" color={"FtFolderColor"} color2={"FtFolderColor2"} /> :
                            <ProviderLogo providerId={drive.specification.product.provider} size={20} />}
                        to={AppRoutes.files.drive(drive.id)}
                        tab={SidebarTabId.FILES}
                    />
                ) : null}

                {canConsume && favoriteFiles.length > 0 ? <>
                    <SidebarSectionHeader tab={SidebarTabId.FILES}>Starred files</SidebarSectionHeader>
                    {favoriteFiles.map(file =>
                        <SidebarEntry
                            key={file.path}
                            to={AppRoutes.files.path(fileTypeCache[file.path] === "DIRECTORY" ? file.path : getParentPath(file.path))}
                            disabled={fileTypeCache[file.path] === "DELETED"}
                            icon={"heroStar"}
                            text={fileName(file.path)}
                            tab={SidebarTabId.FILES}
                        />
                    )}
                </> : null}

                {canConsume && sharesLinksInfo.length > 0 && isPersonalWorkspace ? <>
                    <SidebarSectionHeader tab={SidebarTabId.FILES}>Shared files</SidebarSectionHeader>
                    <SidebarLinkColumn links={sharesLinksInfo} />
                </> : null}
            </>}

            {active !== SidebarTabId.PROJECT ? null : <>
                <SidebarSectionEmptyHeader />
                <ProjectSubLinks canApply={canApply} isPersonalWorkspace={isPersonalWorkspace} projectId={projectId} />
            </>}

            {active !== SidebarTabId.RESOURCES ? null : <>
                <SidebarSectionEmptyHeader />
                <ResourceSubLinks />
            </>}

            {/* Note(Jonas) Do it this way to ensure that the frontend doesn't fetch icons every time this is shown. */}
            <div style={{display: active !== SidebarTabId.APPLICATIONS ? "none" : undefined}}>
                {appFavorites.length > 0 ? <>
                    <SidebarSectionHeader tab={SidebarTabId.APPLICATIONS}>Starred applications</SidebarSectionHeader>
                    {appFavorites.map(fav =>
                        <SidebarEntry
                            key={fav.metadata.name}
                            to={AppRoutes.jobs.create(fav.metadata.name)}
                            text={fav.metadata.title}
                            icon={<AppLogo name={fav.metadata.name} />}
                            tab={SidebarTabId.APPLICATIONS}
                        />
                    )}
                </> : null}

                <SidebarSectionHeader to={AppRoutes.apps.landing()} tab={SidebarTabId.APPLICATIONS}>
                    Store
                </SidebarSectionHeader>

                {landingPage.categories.length === 0 && <>
                    <SidebarEmpty>No applications found</SidebarEmpty>
                </>}

                {landingPage.categories.map(section =>
                    <SidebarEntry
                        key={section.metadata.id}
                        to={AppRoutes.apps.category(section.metadata.id)}
                        text={section.specification.title}
                        icon={"heroCpuChip"}
                        tab={SidebarTabId.APPLICATIONS}
                    />
                )}
            </div>

            {/* Note(Jonas) Do it this way to ensure that the frontend doesn't fetch icons every time this is shown. */}
            <div style={{display: active !== SidebarTabId.RUNS ? "none" : undefined}}>
                <SidebarSectionHeader tab={SidebarTabId.RUNS}>Running jobs</SidebarSectionHeader>
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
                        tab={SidebarTabId.RUNS}
                    />
                })}
            </div>

            {active !== SidebarTabId.ADMIN ? null : <>
                <SidebarSectionHeader tab={SidebarTabId.ADMIN}>Tools</SidebarSectionHeader>
                <SidebarEntry to={AppRoutes.admin.userCreation()} text={"User creation"} icon={"heroUser"}
                    tab={SidebarTabId.ADMIN} />
                <SidebarEntry to={AppRoutes.admin.news()} text={"News"} icon={"heroNewspaper"}
                    tab={SidebarTabId.ADMIN} />
                <SidebarEntry to={AppRoutes.admin.providers()} text={"Providers"} icon={"heroCloud"}
                    tab={SidebarTabId.ADMIN} />
                <SidebarEntry to={AppRoutes.admin.scripts()} text={"Scripts"} icon={"heroPlayPause"}
                    tab={SidebarTabId.ADMIN} />
                <SidebarEntry to={AppRoutes.admin.playground()} text={"Playground"} icon={"heroCake"}
                    tab={SidebarTabId.ADMIN} />
            </>}

            {active !== SidebarTabId.APPLICATION_STUDIO ? null : <>
                <SidebarSectionEmptyHeader />
                <ApplicationStudioSubLinks />
            </>}

        </Flex>
    </div>;
}

function AppLogo({name}: {name: string}): React.ReactNode {
    return <SafeLogo size="16px" name={name} type="APPLICATION" isLightOverride={false} />;
}

function SidebarSectionEmptyHeader(): React.ReactNode {
    return <Box height="11px" />
}

function Username({close}: {close(): void}): React.ReactNode {
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
                <Icon name="heroIdentification" mr="0.5em" my="0.2em" size="1.3em" />{Client.username}
            </EllipsedText>
        )}
    >
        This is your username. <br /> <br />
        Click to copy to clipboard.
    </Tooltip>
}

function ProjectID({close}: {close(): void}): React.ReactNode {
    const projectId = useProjectId();

    const project = useProject();

    const projectPath = joinToString(
        [...(project.fetch().status.path?.split("/")?.filter(it => it.length > 0) ?? []), projectTitle(project.fetch())],
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
                <Icon key={projectId} name={"heroUserGroup"} mr="0.5em" my="0.2em"
                    size="1.3em" />{projectPath}
            </EllipsedText>
        }
    >
        This is your project ID. <br /> <br />
        Click to copy to clipboard.
    </Tooltip>
}

function Downtimes(): React.ReactNode {
    const [downtimes, fetchDowntimes] = useCloudAPI<Page<NewsPost>>({noop: true}, {
        items: [],
        itemsPerPage: 0,
        itemsInTotal: 0,
        pageNumber: 0
    });
    const [intervalId, setIntervalId] = React.useState(-1);

    React.useEffect(() => {
        fetchDowntimes({method: "GET", path: "/news/listDowntimes"});
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
        <Tooltip trigger={<Icon size="24" color="warningMain" name="warning" />}>
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
