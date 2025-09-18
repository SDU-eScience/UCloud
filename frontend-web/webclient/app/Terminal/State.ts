import {useDispatch, useSelector} from "react-redux";
import {randomUUID} from "@/UtilityFunctions";
import {Action, PayloadAction} from "@reduxjs/toolkit";

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

type TerminalOpenTab = PayloadAction<{tab: TerminalTab}, "TerminalOpenTab">;

type TerminalCloseTab = PayloadAction<{tabIdx: number}, "TerminalCloseTab">;

type TerminalOpen = Action<"TerminalOpen">;

type TerminalClose = Action<"TerminalClose">;

type TerminalSelectTab = PayloadAction<{tabIdx: number}, "TerminalSelectTab">;

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
            const tabWithId = {...action.payload.tab};
            tabWithId.uniqueId = randomUUID();

            const tabs = [...state.tabs, tabWithId];
            return {...state, tabs, activeTab: state.activeTab < 0 ? 0 : state.activeTab};
        }

        case "TerminalCloseTab": {
            const tabs = [...state.tabs];
            tabs.splice(action.payload.tabIdx, 1);
            const newActiveTab = Math.min(state.tabs.length - 2, state.activeTab);
            return {...state, tabs, activeTab: newActiveTab, open: state.open && tabs.length > 0};
        }

        case "TerminalSelectTab": {
            return {...state, activeTab: action.payload.tabIdx, open: true};
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
