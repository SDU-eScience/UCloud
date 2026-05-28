import {useSelector} from "react-redux";
import {randomUUID} from "@/UtilityFunctions";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";

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

export function initTerminalState(): TerminalState {
    return {
        activeTab: -1,
        tabs: [],
        open: false
    };
}

const terminalSlice = createSlice({
    name: "terminal",
    initialState: initTerminalState(),
    reducers: {
        terminalOpen(state) {
            state.open = true;
        },
        terminalClose(state) {
            state.open = false;
        },
        terminalOpenTab(state, action: PayloadAction<{tab: TerminalTab}>) {
            const tabWithId = {...action.payload.tab};
            tabWithId.uniqueId = randomUUID();

            state.tabs = [...state.tabs, tabWithId];
            state.activeTab = state.activeTab < 0 ? 0 : state.activeTab;
        },
        terminalCloseTab(state, action: PayloadAction<{tabIdx: number}>) {
            const tabs = [...state.tabs];
            tabs.splice(action.payload.tabIdx, 1);
            const newActiveTab = Math.min(state.tabs.length - 2, state.activeTab);
            state.tabs = tabs;
            state.activeTab = newActiveTab;
            state.open = state.open && tabs.length > 0;
        },
        terminalSelectTab(state, action: PayloadAction<{tabIdx: number}>) {
            state.activeTab = action.payload.tabIdx;
            state.open = true;
        }
    }
});

export const {terminalClose, terminalCloseTab, terminalOpen, terminalOpenTab, terminalSelectTab} = terminalSlice.actions;
export const terminalReducer = terminalSlice.reducer;

export function useTerminalState(): TerminalState {
    return useSelector<ReduxObject, TerminalState>(it => it.terminal);
}
