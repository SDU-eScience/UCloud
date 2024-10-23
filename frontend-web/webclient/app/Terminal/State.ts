import {useDispatch, useSelector} from "react-redux";
import {randomUUID} from "@/UtilityFunctions";

export interface TerminalTab {
    title: string;
    folder: string;
    uniqueId?: string;
}

export interface TerminalState {
    activeTab: number;
    tabs: TerminalTab[];
    open: boolean;
}

interface TerminalOpenTab {
    type: "TerminalOpenTab";
    tab: TerminalTab;
}

interface TerminalCloseTab {
    type: "TerminalCloseTab";
    tabIdx: number;
}

interface TerminalOpen {
    type: "TerminalOpen";
}

interface TerminalClose {
    type: "TerminalClose";
}

interface TerminalSelectTab {
    type: "TerminalSelectTab";
    tabIdx: number;
}

export type TerminalAction = TerminalOpenTab | TerminalCloseTab | TerminalOpen | TerminalClose | TerminalSelectTab;

export function initTerminalState(): TerminalState {
    return {
        activeTab: -1,
        tabs: [],
        open: false
    };
}

export function terminalReducer(state: TerminalState = initTerminalState(), action: TerminalAction): TerminalState {
    switch (action.type) {
        case "TerminalOpen": {
            return {...state, open: true};
        }

        case "TerminalClose": {
            return {...state, open: false};
        }

        case "TerminalOpenTab": {
            const tabWithId = {...action.tab};
            tabWithId.uniqueId = randomUUID();

            const tabs = [...state.tabs, tabWithId];
            return {...state, tabs, activeTab: state.activeTab < 0 ? 0 : state.activeTab};
        }

        case "TerminalCloseTab": {
            const tabs = [...state.tabs];
            tabs.splice(action.tabIdx, 1);
            const newActiveTab = Math.min(state.tabs.length - 2, state.activeTab);
            return {...state, tabs, activeTab: newActiveTab, open: state.open && tabs.length > 0};
        }

        case "TerminalSelectTab": {
            return {...state, activeTab: action.tabIdx, open: true};
        }

        default:
            return state;
    }
}

export function useTerminalDispatcher(): (action: TerminalAction) => void {
    return useDispatch();
}

export function useTerminalState(): TerminalState {
    return useSelector<ReduxObject, TerminalState>(it => it.terminal);
}
