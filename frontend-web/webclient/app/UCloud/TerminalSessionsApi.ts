import {apiBrowse, apiCreate, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";

export type TerminalSessionSplitDirection = "horizontal" | "vertical";

export interface TerminalSessionLayoutNode {
    kind: "pane" | "split";
    paneId?: string;
    direction?: TerminalSessionSplitDirection;
    ratio?: number;
    first?: TerminalSessionLayoutNode;
    second?: TerminalSessionLayoutNode;
}

export interface TerminalSessionPane {
    id: string;
    tabId: string;
    rank: number;
    title: string;
    titlePinned: boolean;
    backendHandle: string;
    createdAt: number;
}

export interface TerminalSessionTab {
    id: string;
    title: string;
    titlePinned: boolean;
    position: number;
    activePaneId?: string;
    layout: TerminalSessionLayoutNode;
    panes: TerminalSessionPane[];
}

export interface TerminalSessionSummary {
    id: string;
    jobId: string;
    jobTitle: string;
    applicationName: string;
    applicationTitle: string;
    providerId: string;
    replicas: number;
    title: string;
    tabCount: number;
    paneCount: number;
    revision: number;
    createdAt: number;
    updatedAt: number;
    lastAccessedAt: number;
}

export interface TerminalSessionDetail {
    id: string;
    jobId: string;
    jobTitle: string;
    applicationName: string;
    applicationTitle: string;
    providerId: string;
    replicas: number;
    title: string;
    activeTabId?: string;
    revision: number;
    createdAt: number;
    updatedAt: number;
    lastAccessedAt: number;
    tabs: TerminalSessionTab[];
}

export interface TerminalSessionsBrowseResponse {
    sessions: TerminalSessionSummary[];
}

export interface TerminalSessionTabUpdate {
    id: string;
    title?: string;
    titlePinned?: boolean;
    position?: number;
    activePaneId?: string;
    layout?: TerminalSessionLayoutNode;
}

export interface TerminalSessionPaneUpdate {
    id: string;
    title?: string;
    titlePinned?: boolean;
}

export interface TerminalSessionUpdateRequest {
    sessionId: string;
    revision: number;
    title?: string;
    activeTabId?: string;
    tabs?: TerminalSessionTabUpdate[];
    panes?: TerminalSessionPaneUpdate[];
}

export interface TerminalSessionCloseResponse {
    sessionDeleted: boolean;
    session?: TerminalSessionDetail;
}

export interface TerminalSessionConnectResponse {
    providerDomain: string;
    providerId: string;
    token: string;
    wsPath: string;
}

const baseContext = "/api/jobs/terminalSessions";

export const terminalSessionsApi = {
    browse(jobId?: string): APICallParameters<unknown, TerminalSessionsBrowseResponse> {
        const request = jobId ? {jobId} : {};
        return apiBrowse(request, baseContext);
    },

    retrieve(sessionId: string): APICallParameters<unknown, TerminalSessionDetail> {
        return apiRetrieve({sessionId}, baseContext);
    },

    openForJob(jobId: string, rank: number): APICallParameters<unknown, TerminalSessionDetail> {
        return apiUpdate({jobId, rank}, baseContext, "openForJob");
    },

    create(jobId: string, rank = 0, title?: string): APICallParameters<unknown, TerminalSessionDetail> {
        return apiCreate({jobId, rank, title}, baseContext);
    },

    update(request: TerminalSessionUpdateRequest): APICallParameters<unknown, TerminalSessionDetail> {
        return apiUpdate(request, baseContext, "update");
    },

    createTab(sessionId: string, rank = 0, title?: string): APICallParameters<unknown, TerminalSessionDetail> {
        return apiUpdate({sessionId, rank, title}, baseContext, "createTab");
    },

    splitPane(request: {
        sessionId: string;
        targetPaneId: string;
        direction: TerminalSessionSplitDirection;
        placeFirst?: boolean;
        rank?: number;
    }): APICallParameters<unknown, TerminalSessionDetail> {
        return apiUpdate(request, baseContext, "splitPane");
    },

    closePane(sessionId: string, paneId: string): APICallParameters<unknown, TerminalSessionCloseResponse> {
        return apiUpdate({sessionId, paneId}, baseContext, "closePane");
    },

    closeTab(sessionId: string, tabId: string): APICallParameters<unknown, TerminalSessionCloseResponse> {
        return apiUpdate({sessionId, tabId}, baseContext, "closeTab");
    },

    shutdown(sessionId: string): APICallParameters<unknown, Record<string, never>> {
        return apiUpdate({sessionId}, baseContext, "shutdown");
    },

    connect(sessionId: string): APICallParameters<unknown, TerminalSessionConnectResponse> {
        return apiUpdate({sessionId}, baseContext, "connect");
    },
};
