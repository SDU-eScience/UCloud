import {DashboardStateProps} from "@/Dashboard";
import * as ProjectRedux from "@/Project/ReduxState";
import {Upload} from "@/Files/Upload";
import {ProjectCache} from "@/Project/cache";
import * as UCloud from "@/UCloud";
import {BulkRequest, BulkResponse, PageV2} from "@/UCloud";
import {UCLOUD_CORE} from "@/UCloud/ResourceApi";
import {initTerminalState, TerminalState} from "@/Terminal/State";
import {PopInArgs} from "./ui-components/PopIn";
import {SidebarStateProps} from "./Applications/Redux/Reducer";
import {getUserThemePreference} from "./UtilityFunctions";
import {defaultAvatar} from "./AvataaarLib";

export enum KeyCode {
    ENTER = 13,
    ESC = 27
}

export function placeholderProduct(): {"id": "", "category": "", "provider": string} {
    return {"id": "", "category": "", "provider": UCLOUD_CORE};
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
    {items: [], itemsPerPage: 100};

export function pageV2Of<T>(...items: T[]): PageV2<T> {
    return {items, itemsPerPage: items.length, next: undefined};
}

export enum SensitivityLevel {
    "INHERIT" = "Inherit",
    "PRIVATE" = "Private",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
}

export enum SensitivityLevelMap {
    INHERIT = "INHERIT",
    PRIVATE = "PRIVATE",
    CONFIDENTIAL = "CONFIDENTIAL",
    SENSITIVE = "SENSITIVE"
}

export interface StatusReduxObject {
    title: string;
    loading: boolean;
}

/**
 * Global state created via useGlobal() similar to ReduxObject
 */
export interface HookStore {
    uploaderVisible?: boolean;
    uploads?: Upload[];
    uploadPath?: string;

    projectCache?: ProjectCache;
    frameHidden?: boolean;

    mainContainerHeaderSize?: number;
}

interface LegacyReduxObject {
    hookStore: HookStore;
    dashboard: DashboardStateProps;
    status: StatusReduxObject;
    avatar: AvatarReduxObject;
    project: ProjectRedux.State;
    terminal: TerminalState;
    popinChild: PopInArgs | null;
    loading?: boolean;
    sidebar: SidebarStateProps;
}

declare global {
    export type ReduxObject =
        LegacyReduxObject;
}

export function initStatus(): StatusReduxObject {
    return ({
        title: "",
        loading: false
    });
}

export const initDashboard = (): DashboardStateProps => ({
    loading: false,
});

export function initObject(): ReduxObject {
    return {
        hookStore: {},
        dashboard: initDashboard(),
        status: initStatus(),
        avatar: initAvatar(),
        project: ProjectRedux.initialState,
        terminal: initTerminalState(),
        popinChild: null,
        sidebar: {favorites: [], theme: getThemeOrDefaultValue()}
    };
}

function getThemeOrDefaultValue(): "light" | "dark" {
    return (window.localStorage.getItem("theme") ?? getUserThemePreference()) as "light" | "dark"
}

export type AvatarReduxObject = typeof defaultAvatar & {error?: string};
export function initAvatar(): AvatarReduxObject {
    return {...defaultAvatar, error: undefined};
}
