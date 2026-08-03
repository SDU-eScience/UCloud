import {useSelector} from "react-redux";
import {randomUUID} from "@/UtilityFunctions";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";

export interface TerminalTab {
    title: string;
    folder: string;
    providerId: string;
    uniqueId?: string;
}

export interface TerminalPageContext {
    folder: string;
    providerId: string;
}

export interface TerminalState {
    activeTab: number;
    tabs: TerminalTab[];
    open: boolean;
    pageContext: TerminalPageContext | null;
}

export function initTerminalState(): TerminalState {
    return {
        activeTab: -1,
        tabs: [],
        open: false,
        pageContext: null,
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
        terminalOpenTab(state, action: PayloadAction<{tab: TerminalTab; select?: boolean}>) {
            const tabWithId = {...action.payload.tab};
            tabWithId.uniqueId = randomUUID();

            state.tabs = [...state.tabs, tabWithId];
            if (action.payload.select === true) {
                state.activeTab = state.tabs.length - 1;
                state.open = true;
            } else {
                state.activeTab = state.activeTab < 0 ? 0 : state.activeTab;
            }
        },
        terminalSetPageContext(state, action: PayloadAction<TerminalPageContext | null>) {
            state.pageContext = action.payload;
        },
        terminalCloseTab(state, action: PayloadAction<{tabIdx: number}>) {
            if (action.payload.tabIdx < 0 || action.payload.tabIdx >= state.tabs.length) return;

            const activeTab = state.tabs[state.activeTab];
            const tabs = [...state.tabs];
            tabs.splice(action.payload.tabIdx, 1);
            state.tabs = tabs;
            const activeTabIndex = tabs.indexOf(activeTab);
            state.activeTab = activeTabIndex === -1 ? Math.min(action.payload.tabIdx, tabs.length - 1) : activeTabIndex;
            state.open = state.open && tabs.length > 0;
        },
        terminalSelectTab(state, action: PayloadAction<{tabIdx: number}>) {
            state.activeTab = action.payload.tabIdx;
            state.open = true;
        },
        terminalUpdateTabTitle(state, action: PayloadAction<{tabIdx: number; title: string}>) {
            const tab = state.tabs[action.payload.tabIdx];
            if (tab) tab.title = action.payload.title;
        },
        terminalReorderTabs(state, action: PayloadAction<{tabIds: string[]}>) {
            const activeTabId = state.tabs[state.activeTab]?.uniqueId;
            const reorderedTabs = action.payload.tabIds
                .map(tabId => state.tabs.find(tab => tab.uniqueId === tabId))
                .filter((tab): tab is TerminalTab => tab !== undefined);

            if (reorderedTabs.length !== state.tabs.length) return;

            state.tabs = reorderedTabs;
            if (activeTabId !== undefined) {
                state.activeTab = state.tabs.findIndex(tab => tab.uniqueId === activeTabId);
            }
        }
    }
});

export const {terminalClose, terminalCloseTab, terminalOpen, terminalOpenTab, terminalSelectTab, terminalUpdateTabTitle, terminalReorderTabs, terminalSetPageContext} = terminalSlice.actions;
export const terminalReducer = terminalSlice.reducer;

export function useTerminalState(): TerminalState {
    return useSelector<ReduxObject, TerminalState>(it => it.terminal);
}
