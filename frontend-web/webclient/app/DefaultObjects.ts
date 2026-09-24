import {DashboardStateProps} from "@/Dashboard";
import * as ProjectRedux from "@/Project/ReduxState";
import {initTerminalState, TerminalState} from "@/Terminal/State";
import {PopInArgs} from "./ui-components/PopIn";
import {SidebarStateProps} from "./Applications/Redux/Reducer";
import {getUserThemePreference} from "./UtilityFunctions";
import {defaultAvatar} from "./AvataaarLib";
import {HookStore} from "./Utilities/ReduxHooks";
import {BrandingResponse} from "./UCloud/BrandingApi";
import {initBranding} from "./Applications/Branding/AutomaticBranding";
import {SidebarTabId} from "./ui-components/SidebarComponents";

export interface StatusReduxObject {
    title: string;
    loading: boolean;
    tab: SidebarTabId;
}

/**
 * Global state created via useGlobal() similar to ReduxObject
 */
export interface LegacyReduxObject {
    hookStore: HookStore;
    status: StatusReduxObject;
    avatar: AvatarReduxObject;
    project: ProjectRedux.State;
    terminal: TerminalState;
    branding: BrandingResponse
    popinChild: PopInArgs;
    sidebar: SidebarStateProps;
}

declare global {
    export type ReduxObject =
        LegacyReduxObject;
}

export function initStatus(): StatusReduxObject {
    return ({
        title: "",
        loading: false,
        tab: SidebarTabId.NONE,
    });
}

export function initDashboard(): DashboardStateProps {
    return {
        loading: false,
    }
}

export function initObject(): ReduxObject {
    return {
        hookStore: {},
        status: initStatus(),
        avatar: initAvatar(),
        project: ProjectRedux.initialState,
        terminal: initTerminalState(),
        branding: initBranding(),
        popinChild: {el: undefined},
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
