import {DashboardStateProps} from "@/Dashboard";
import {Notification} from "@/Notifications";
import * as ProjectRedux from "@/Project/Redux";
import {SidebarOption} from "@/Types";
import {SidebarPages} from "@/ui-components/Sidebar";
import {Upload} from "@/Files/Upload";
import {defaultAvatar} from "@/UserSettings/Avataaar";
import {ProjectCache} from "@/Project/cache";
import {APICallStateWithParams} from "@/Authentication/DataHook";
import {
    ListGroupMembersRequestProps,
    ListOutgoingInvitesRequest,
    OutgoingInvite,
    ProjectMember,
    UserInProject
} from "@/Project";
import {GroupWithSummary} from "@/Project/GroupList";
import {Product} from "@/Accounting";
import * as UCloud from "@/UCloud";
import {BulkRequest, BulkResponse, PageV2} from "@/UCloud";
import {useEffect} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {doNothing} from "@/UtilityFunctions";
import {UCLOUD_CORE} from "@/UCloud/ResourceApi";
import {useHistory} from "react-router";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {History} from "history";

export enum KeyCode {
    ENTER = 13,
    ESC = 27
}

export function placeholderProduct(): { "id": "", "category": "", "provider": string } {
    return { "id": "", "category": "", "provider": UCLOUD_CORE };
}

export function bulkRequestOf<T>(...items: T[]): BulkRequest<T> {
    return {"type": "bulk", items};
}

export function bulkResponseOf<T>(...items: T[]): BulkResponse<T> {
    return {responses: items};
}

export const emptyPage: Readonly<Page<any>> =
    {items: [], itemsInTotal: 0, itemsPerPage: 25, pageNumber: 0};

export const emptyPageV2: Readonly<UCloud.PageV2<any>> =
    {items: [], itemsPerPage: 25};

export function pageV2Of<T>(...items: T[]): PageV2<T> {
    return {items, itemsPerPage: items.length, next: undefined};
}

export enum SensitivityLevel {
    "INHERIT" = "Inherit",
    "PRIVATE" = "Private",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
}

export type Sensitivity = keyof typeof SensitivityLevel;

export enum SensitivityLevelMap {
    INHERIT = "INHERIT",
    PRIVATE = "PRIVATE",
    CONFIDENTIAL = "CONFIDENTIAL",
    SENSITIVE = "SENSITIVE"
}

export interface ComponentWithLoadingState {
    loading: boolean;
    error?: string;
}

export interface ComponentWithPage<T> extends ComponentWithLoadingState {
    page: Page<T>;
}

export interface ResponsiveReduxObject {
    mediaType: string;
    orientation: string;
    lessThan: Record<string, boolean>;
    greaterThan: Record<string, boolean>;
    is: Record<string, boolean>;
}

export interface NotificationsReduxObject {
    redirectTo: string;
    items: Notification[];
    loading: boolean;
    error?: string;
}

export interface StatusReduxObject {
    title: string;
    page: SidebarPages;
    loading: boolean;
}

export interface SidebarReduxObject {
    pp: boolean;
    options: SidebarOption[];
    kcCount: number;
}

export interface HeaderSearchReduxObject {
    prioritizedSearch: HeaderSearchType;
    refresh?: () => void;
}

export type HeaderSearchType = "files" | "applications" | "projects";

export interface UploaderReduxObject {
    visible: boolean;
    path: string;
    allowMultiple: boolean;
    onFilesUploaded: () => void;
    error?: string;
    loading: boolean;
}

/**
 * Global state created via useGlobal() similar to ReduxObject
 */
export interface HookStore {
    uploaderVisible?: boolean;
    uploads?: Upload[];
    uploadPath?: string;

    searchPlaceholder?: string;
    onSearch?: (query: string, history: History) => void;

    projectCache?: ProjectCache;
    projectManagementDetails?: APICallStateWithParams<UserInProject>;
    projectManagement?: APICallStateWithParams<Page<ProjectMember>>;
    projectManagementGroupMembers?: APICallStateWithParams<Page<string>, ListGroupMembersRequestProps>;
    projectManagementGroupSummary?: APICallStateWithParams<Page<GroupWithSummary>, PaginationRequest>;
    projectManagementQuery?: string;
    projectManagementOutgoingInvites?: APICallStateWithParams<Page<OutgoingInvite>, ListOutgoingInvitesRequest>;
    computeProducts?: APICallStateWithParams<Page<Product>>;
    storageProducts?: APICallStateWithParams<Page<Product>>;
    frameHidden?: boolean;
    cloudApiCache?: Record<string, { expiresAt: number, cached: any }>;
}

interface LegacyReduxObject {
    hookStore: HookStore;
    dashboard: DashboardStateProps;
    status: StatusReduxObject;
    notifications: NotificationsReduxObject;
    header: HeaderSearchReduxObject;
    sidebar: SidebarReduxObject;
    avatar: AvatarReduxObject;
    responsive?: ResponsiveReduxObject;
    project: ProjectRedux.State;
    loading?: boolean;
}

declare global {
    export type ReduxObject =
        LegacyReduxObject;
}

export const initNotifications = (): NotificationsReduxObject => ({
    items: [],
    loading: false,
    redirectTo: "",
    error: undefined
});

export const initHeader = (): HeaderSearchReduxObject => ({
    prioritizedSearch: "files"
});

export const initStatus = (): StatusReduxObject => ({
    title: "",
    page: SidebarPages.None,
    loading: false
});

export const initDashboard = (): DashboardStateProps => ({
    notifications: {items: []},
});

export function initObject(): ReduxObject {
    return {
        hookStore: {},
        dashboard: initDashboard(),
        status: initStatus(),
        header: initHeader(),
        notifications: initNotifications(),
        sidebar: initSidebar(),
        avatar: initAvatar(),
        project: ProjectRedux.initialState,
        responsive: undefined,
    };
}

export type AvatarReduxObject = typeof defaultAvatar & { error?: string };
export const initAvatar = (): AvatarReduxObject => ({...defaultAvatar, error: undefined});

export const initSidebar = (): SidebarReduxObject => ({
    pp: false,
    kcCount: 0,
    options: []
});

export const defaultSearchPlaceholder = "Search files and applications..."

export function defaultSearch(query: string, history: History) {
    history.push(buildQueryString("/files/search", {q: query}));
}

export function useSearch(onSearch: (query: string, history: History) => void): void {
    const [, setOnSearch] = useGlobal("onSearch", defaultSearch);
    useEffect(() => {
        setOnSearch(() => onSearch);
        return () => {
            setOnSearch(() => defaultSearch);
        };
    }, [setOnSearch, onSearch]);
}

export function useSearchPlaceholder(searchPlaceholder: string): void {
    const [, setSearchPlaceholder] = useGlobal("searchPlaceholder", defaultSearchPlaceholder);
    useEffect(() => {
        setSearchPlaceholder(searchPlaceholder);
        return () => {
            setSearchPlaceholder(defaultSearchPlaceholder);
        };
    }, [setSearchPlaceholder, searchPlaceholder]);
}
