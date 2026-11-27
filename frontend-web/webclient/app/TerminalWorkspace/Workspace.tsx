import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {injectStyle} from "@/Unstyled";
import {callAPI, noopCall} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {TabStrip} from "@/ui-components/TabStrip";
import {Operation, Operations} from "@/ui-components/Operation";
import Icon from "@/ui-components/Icon";
import {IconButton} from "@/ui-components/IconButton";
import {TooltipV2} from "@/ui-components/Tooltip";
import {sendFailureNotification} from "@/Notifications";
import {
    terminalSessionsApi,
    TerminalSessionDetail,
    TerminalSessionLayoutNode,
    TerminalSessionPane,
    TerminalSessionSplitDirection,
    TerminalSessionSummary,
    TerminalSessionTab,
    TerminalSessionUpdateRequest,
} from "@/UCloud/TerminalSessionsApi";
import {
    createTerminalWsClient,
    TERMINAL_STATUS_ERROR,
    TERMINAL_STATUS_EXITED,
    TERMINAL_STATUS_JOB_ENDED,
    TerminalWsClient,
} from "@/TerminalWorkspace/protocol";
import {
    axisExprToCss,
    axisExprToPx,
    clampSplitRatio,
    computeLayoutGeometry,
    findNeighborPaneId,
    LayoutBoxExpr,
    PaneMoveDirection,
    resizePaneByDirection,
    updateLayoutSplitRatio,
} from "@/TerminalWorkspace/layout";
import {TerminalEngineHandle, TERMINAL_DEFAULT_FONT_SIZE} from "@/TerminalEngine/engine";
import {TerminalSessionSwitcher} from "@/TerminalWorkspace/SessionSwitcher";
import {PaneRuntimeInfo, TerminalPaneView} from "@/TerminalWorkspace/PaneView";
import {TerminalShortcutsModal} from "@/TerminalWorkspace/ShortcutsModal";

function cwdBasename(cwd: string): string {
    const trimmed = cwd.replace(/\/+$/, "");
    if (!trimmed) return "/";
    const idx = trimmed.lastIndexOf("/");
    return idx >= 0 ? trimmed.slice(idx + 1) : trimmed;
}

interface PaneProcMeta {
    command: string;
    cwd: string;
    busy: boolean;
}

function resolvePaneDisplayTitle(
    pane: TerminalSessionPane,
    procMeta: PaneProcMeta | undefined,
    oscTitle: string | undefined,
): string {
    if (pane.titlePinned) return pane.title;
    if (procMeta?.busy && procMeta.command) return procMeta.command;
    if (oscTitle) return oscTitle;
    if (procMeta?.cwd) return cwdBasename(procMeta.cwd);
    return pane.title;
}

function arrowKeyToDirection(code: string): PaneMoveDirection | null {
    switch (code) {
        case "ArrowLeft": return "left";
        case "ArrowRight": return "right";
        case "ArrowUp": return "up";
        case "ArrowDown": return "down";
        default: return null;
    }
}

function vimKeyToDirection(code: string): PaneMoveDirection | null {
    switch (code) {
        case "KeyH": return "left";
        case "KeyJ": return "down";
        case "KeyK": return "up";
        case "KeyL": return "right";
        default: return null;
    }
}

export const TerminalWorkspace: React.FunctionComponent<{
    initialJobId?: string;
    initialRank?: number;
}> = ({initialJobId, initialRank = 0}) => {
    const params = useParams<{sessionId?: string}>();
    const navigate = useNavigate();

    const [sessions, setSessions] = useState<TerminalSessionSummary[]>([]);
    const [session, setSession] = useState<TerminalSessionDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [connectionState, setConnectionState] = useState<"idle" | "connecting" | "connected" | "disconnected" | "replaced">("idle");
    const [connectionNotice, setConnectionNotice] = useState("");
    const [switcherOpen, setSwitcherOpen] = useState(false);
    const [shortcutsOpen, setShortcutsOpen] = useState(false);
    const [maximizedPaneId, setMaximizedPaneId] = useState<string | null>(null);
    const [searchPaneId, setSearchPaneId] = useState<string | null>(null);
    const [runtimeByPane, setRuntimeByPane] = useState<Record<string, PaneRuntimeInfo>>({});
    const [procMetaByPane, setProcMetaByPane] = useState<Record<string, PaneProcMeta>>({});
    const [oscTitleByPane, setOscTitleByPane] = useState<Record<string, string>>({});
    const [activityTabIds, setActivityTabIds] = useState<Set<string>>(() => new Set());
    const [newTabRankMenuOpen, setNewTabRankMenuOpen] = useState(false);
    const [wsReconnectNonce, setWsReconnectNonce] = useState(0);

    const rootRef = useRef<HTMLDivElement | null>(null);
    const newTabMenuRef = useRef<HTMLDivElement | null>(null);
    const sessionRef = useRef<TerminalSessionDetail | null>(null);
    const wsClientRef = useRef<TerminalWsClient | null>(null);
    const paneHandlesRef = useRef<Map<string, TerminalEngineHandle>>(new Map());
    const paneToChannelRef = useRef<Map<string, number>>(new Map());
    const channelToPaneRef = useRef<Map<number, string>>(new Map());
    const attachedChannelsRef = useRef<Set<number>>(new Set());
    const paneOffsetsRef = useRef<Map<string, bigint>>(new Map());
    const paneReplayRemainingRef = useRef<Map<string, number>>(new Map());
    const paneHadDataRef = useRef<Set<string>>(new Set());
    const pendingSourceHandleRef = useRef<Map<string, string>>(new Map());
    const resizeTimersRef = useRef<Map<number, number>>(new Map());
    const lastSentSizeRef = useRef<Map<number, {cols: number; rows: number}>>(new Map());
    const lastSessionIdRef = useRef<string | null>(null);
    const nextChannelIdRef = useRef(1);
    const syncTimerRef = useRef<number | null>(null);
    const pendingSyncRef = useRef<Omit<TerminalSessionUpdateRequest, "sessionId" | "revision"> | null>(null);

    const [tabMenuOps, setTabMenuOps] = useState<Operation<void>[]>([]);
    const openTabMenuRef = useRef<(x: number, y: number) => void>(noopCall);
    const pendingMenuPosRef = useRef<{x: number; y: number} | null>(null);

    sessionRef.current = session;

    usePage(
        session ? `${session.title} — Terminals` : "Terminals",
        SidebarTabId.COMPUTE,
    );

    const refreshSessionsList = useCallback(async (): Promise<TerminalSessionSummary[]> => {
        try {
            const resp = await callAPI(terminalSessionsApi.browse());
            const list = resp?.sessions ?? [];
            setSessions(list);
            return list;
        } catch {
            return [];
        }
    }, []);

    const loadSessionById = useCallback(async (sessionId: string) => {
        setLoading(true);
        try {
            const detail = await callAPI(terminalSessionsApi.retrieve(sessionId));
            setSession(detail);
            setMaximizedPaneId(null);
            setSearchPaneId(null);
            setRuntimeByPane({});
            setProcMetaByPane({});
            setOscTitleByPane({});
            setActivityTabIds(new Set());
            void refreshSessionsList();
        } catch (err) {
            sendFailureNotification("Could not open terminal session");
            setSession(null);
        } finally {
            setLoading(false);
        }
    }, [refreshSessionsList]);

    useEffect(() => {
        let cancelled = false;

        const bootstrap = async () => {
            setLoading(true);
            try {
                if (initialJobId) {
                    const detail = await callAPI(terminalSessionsApi.openForJob(initialJobId, initialRank));
                    if (cancelled) return;
                    setSession(detail);
                    void refreshSessionsList();
                    return;
                }

                if (params.sessionId) {
                    const detail = await callAPI(terminalSessionsApi.retrieve(params.sessionId));
                    if (cancelled) return;
                    setSession(detail);
                    void refreshSessionsList();
                    return;
                }

                const list = await refreshSessionsList();
                if (cancelled) return;
                if (list.length > 0) {
                    const detail = await callAPI(terminalSessionsApi.retrieve(list[0].id));
                    if (cancelled) return;
                    setSession(detail);
                } else {
                    setSession(null);
                }
            } catch {
                if (!cancelled) {
                    sendFailureNotification("Could not load terminal sessions");
                    setSession(null);
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        };

        void bootstrap();
        return () => {
            cancelled = true;
        };
    }, [initialJobId, initialRank, params.sessionId, refreshSessionsList]);

    const flushSync = useCallback(async () => {
        const current = sessionRef.current;
        const patch = pendingSyncRef.current;
        if (!current || !patch) return;
        pendingSyncRef.current = null;
        try {
            const updated = await callAPI(terminalSessionsApi.update({
                sessionId: current.id,
                revision: current.revision,
                ...patch,
            }));
            setSession(prev => {
                if (!prev || prev.id !== updated.id) return prev;
                return {
                    ...prev,
                    revision: updated.revision,
                    updatedAt: updated.updatedAt,
                };
            });
        } catch {
            void loadSessionById(current.id);
        }
    }, [loadSessionById]);

    const scheduleSync = useCallback((patch: Omit<TerminalSessionUpdateRequest, "sessionId" | "revision">) => {
        const existing = pendingSyncRef.current ?? {};
        const mergedTabs = [...(existing.tabs ?? [])];
        for (const nextTab of patch.tabs ?? []) {
            const idx = mergedTabs.findIndex(t => t.id === nextTab.id);
            if (idx >= 0) {
                mergedTabs[idx] = {...mergedTabs[idx], ...nextTab};
            } else {
                mergedTabs.push(nextTab);
            }
        }
        const mergedPanes = [...(existing.panes ?? [])];
        for (const nextPane of patch.panes ?? []) {
            const idx = mergedPanes.findIndex(p => p.id === nextPane.id);
            if (idx >= 0) {
                mergedPanes[idx] = {...mergedPanes[idx], ...nextPane};
            } else {
                mergedPanes.push(nextPane);
            }
        }
        pendingSyncRef.current = {
            title: patch.title ?? existing.title,
            activeTabId: patch.activeTabId ?? existing.activeTabId,
            tabs: mergedTabs.length > 0 ? mergedTabs : undefined,
            panes: mergedPanes.length > 0 ? mergedPanes : undefined,
        };

        if (syncTimerRef.current !== null) {
            window.clearTimeout(syncTimerRef.current);
        }
        syncTimerRef.current = window.setTimeout(() => {
            syncTimerRef.current = null;
            void flushSync();
        }, 300);
    }, [flushSync]);

    useEffect(() => {
        return () => {
            if (syncTimerRef.current !== null) {
                window.clearTimeout(syncTimerRef.current);
            }
        };
    }, []);

    const activeTab = useMemo<TerminalSessionTab | null>(() => {
        if (!session || session.tabs.length === 0) return null;
        return session.tabs.find(t => t.id === session.activeTabId) ?? session.tabs[0];
    }, [session]);

    const activePaneId = useMemo<string | null>(() => {
        if (!activeTab) return null;
        if (activeTab.activePaneId && activeTab.panes.some(p => p.id === activeTab.activePaneId)) {
            return activeTab.activePaneId;
        }
        return activeTab.panes[0]?.id ?? null;
    }, [activeTab]);

    const handleClosePane = useCallback(async (paneId: string) => {
        const current = sessionRef.current;
        if (!current) return;

        const channelId = paneToChannelRef.current.get(paneId);
        if (channelId !== undefined && attachedChannelsRef.current.has(channelId)) {
            wsClientRef.current?.shutdownPane(channelId);
            attachedChannelsRef.current.delete(channelId);
        }

        setMaximizedPaneId(prev => prev === paneId ? null : prev);
        setSearchPaneId(prev => prev === paneId ? null : prev);

        try {
            const resp = await callAPI(terminalSessionsApi.closePane(current.id, paneId));
            if (resp.sessionDeleted) {
                const remaining = await refreshSessionsList();
                if (remaining.length > 0) {
                    await loadSessionById(remaining[0].id);
                } else {
                    setSession(null);
                }
                return;
            }
            if (resp.session) {
                setSession(resp.session);
                void refreshSessionsList();
            }
        } catch {
            sendFailureNotification("Could not close pane");
        }
    }, [loadSessionById, refreshSessionsList]);

    const handleCloseTab = useCallback(async (tabId: string) => {
        const current = sessionRef.current;
        if (!current) return;
        const tab = current.tabs.find(t => t.id === tabId);
        if (tab) {
            for (const pane of tab.panes) {
                const ch = paneToChannelRef.current.get(pane.id);
                if (ch !== undefined && attachedChannelsRef.current.has(ch)) {
                    wsClientRef.current?.shutdownPane(ch);
                    attachedChannelsRef.current.delete(ch);
                }
            }
        }
        setMaximizedPaneId(null);
        setSearchPaneId(null);

        try {
            const resp = await callAPI(terminalSessionsApi.closeTab(current.id, tabId));
            if (resp.sessionDeleted) {
                const remaining = await refreshSessionsList();
                if (remaining.length > 0) {
                    await loadSessionById(remaining[0].id);
                } else {
                    setSession(null);
                }
                return;
            }
            if (resp.session) {
                setSession(resp.session);
                void refreshSessionsList();
            }
        } catch {
            sendFailureNotification("Could not close tab");
        }
    }, [loadSessionById, refreshSessionsList]);

    const ensurePaneChannel = useCallback((paneId: string): number => {
        const existing = paneToChannelRef.current.get(paneId);
        if (existing !== undefined) return existing;
        const assigned = nextChannelIdRef.current++;
        paneToChannelRef.current.set(paneId, assigned);
        channelToPaneRef.current.set(assigned, paneId);
        return assigned;
    }, []);

    const syncAttachedPanes = useCallback(() => {
        const ws = wsClientRef.current;
        const current = sessionRef.current;
        if (!ws || !ws.connected || !current) return;

        const allPanes = current.tabs.flatMap(t => t.panes);
        const desiredChannels = new Set<number>();

        for (const pane of allPanes) {
            const ch = ensurePaneChannel(pane.id);
            desiredChannels.add(ch);
        }

        for (const ch of [...attachedChannelsRef.current]) {
            if (!desiredChannels.has(ch)) {
                ws.detachPane(ch);
                attachedChannelsRef.current.delete(ch);
            }
        }

        for (const pane of allPanes) {
            const ch = ensurePaneChannel(pane.id);
            if (!attachedChannelsRef.current.has(ch)) {
                const handle = paneHandlesRef.current.get(pane.id);
                if (!handle || !handle.measured) continue;
                const lastOffset = paneOffsetsRef.current.get(pane.id) ?? 0n;
                const sourceHandle = pendingSourceHandleRef.current.get(pane.id) ?? "";
                if (sourceHandle) {
                    pendingSourceHandleRef.current.delete(pane.id);
                }
                ws.attachPane(ch, pane.rank, handle.cols, handle.rows, pane.backendHandle, true, lastOffset, sourceHandle);
                lastSentSizeRef.current.set(ch, {cols: handle.cols, rows: handle.rows});
                attachedChannelsRef.current.add(ch);
            }
        }
    }, [ensurePaneChannel]);

    const markPaneTabActivity = useCallback((paneId: string) => {
        const current = sessionRef.current;
        if (!current) return;
        const activeId = current.tabs.find(t => t.id === current.activeTabId)?.id ?? current.tabs[0]?.id;
        const ownerTab = current.tabs.find(t => t.panes.some(p => p.id === paneId));
        if (!ownerTab || ownerTab.id === activeId) return;
        setActivityTabIds(prev => {
            if (prev.has(ownerTab.id)) return prev;
            const next = new Set(prev);
            next.add(ownerTab.id);
            return next;
        });
    }, []);

    const handleClosePaneRef = useRef(handleClosePane);
    handleClosePaneRef.current = handleClosePane;
    const syncAttachedPanesRef = useRef(syncAttachedPanes);
    syncAttachedPanesRef.current = syncAttachedPanes;
    const markPaneTabActivityRef = useRef(markPaneTabActivity);
    markPaneTabActivityRef.current = markPaneTabActivity;

    useEffect(() => {
        const sessionId = session?.id;
        if (!sessionId) {
            wsClientRef.current?.close();
            wsClientRef.current = null;
            paneOffsetsRef.current.clear();
            paneReplayRemainingRef.current.clear();
            paneHadDataRef.current.clear();
            pendingSourceHandleRef.current.clear();
            lastSessionIdRef.current = null;
            setConnectionState("idle");
            return;
        }

        if (lastSessionIdRef.current !== sessionId) {
            paneOffsetsRef.current.clear();
            paneReplayRemainingRef.current.clear();
            paneHadDataRef.current.clear();
            pendingSourceHandleRef.current.clear();
            lastSessionIdRef.current = sessionId;
        }

        let disposed = false;
        paneToChannelRef.current.clear();
        channelToPaneRef.current.clear();
        attachedChannelsRef.current.clear();
        nextChannelIdRef.current = 1;

        setConnectionState("connecting");
        setConnectionNotice("");

        void callAPI(terminalSessionsApi.connect(sessionId)).then(connectInfo => {
            if (disposed) return;

            const usernameHint = Client.activeUsername ? `?usernameHint=${encodeURIComponent(b64EncodeUnicode(Client.activeUsername))}` : "";
            const baseDomain = connectInfo.providerDomain.replace(/^http:/, "ws:").replace(/^https:/, "wss:").replace(/\/$/, "");
            const wsUrl = `${baseDomain}${connectInfo.wsPath}${usernameHint}`;

            const client = createTerminalWsClient(wsUrl, connectInfo.token, {
                onReady: () => {
                    if (disposed) return;
                    setConnectionState("connected");
                    setConnectionNotice("");
                    syncAttachedPanesRef.current();
                },
                onClose: reason => {
                    if (disposed) return;
                    attachedChannelsRef.current.clear();
                    setConnectionState("disconnected");
                    setConnectionNotice(reason || "Terminal connection lost.");
                },
                onReplaced: message => {
                    if (disposed) return;
                    attachedChannelsRef.current.clear();
                    setConnectionState("replaced");
                    setConnectionNotice(message || "Opened in another tab.");
                },
                onPaneReady: ev => {
                    if (disposed) return;
                    const paneId = channelToPaneRef.current.get(ev.channelId);
                    if (!paneId) return;
                    paneOffsetsRef.current.set(paneId, ev.baseOffset);
                    paneReplayRemainingRef.current.set(paneId, ev.replayBytes);
                    if (ev.resetRequired && paneHadDataRef.current.has(paneId)) {
                        paneHandlesRef.current.get(paneId)?.reset();
                    }
                    setRuntimeByPane(prev => ({
                        ...prev,
                        [paneId]: {
                            warning: ev.warning,
                            fallbackShell: ev.fallbackShell,
                            error: "",
                        },
                    }));
                },
                onPaneData: (channelId, data) => {
                    if (disposed) return;
                    const paneId = channelToPaneRef.current.get(channelId);
                    if (!paneId) return;
                    paneHadDataRef.current.add(paneId);
                    paneOffsetsRef.current.set(
                        paneId,
                        (paneOffsetsRef.current.get(paneId) ?? 0n) + BigInt(data.byteLength),
                    );
                    const handle = paneHandlesRef.current.get(paneId);
                    if (!handle) return;
                    const replayRemaining = paneReplayRemainingRef.current.get(paneId) ?? 0;
                    if (replayRemaining <= 0) {
                        handle.write(data);
                        return;
                    }
                    if (data.byteLength <= replayRemaining) {
                        paneReplayRemainingRef.current.set(paneId, replayRemaining - data.byteLength);
                        handle.writeReplay(data);
                        return;
                    }
                    paneReplayRemainingRef.current.set(paneId, 0);
                    handle.writeReplay(data.subarray(0, replayRemaining));
                    handle.write(data.subarray(replayRemaining));
                },
                onPaneMeta: ev => {
                    if (disposed) return;
                    const paneId = channelToPaneRef.current.get(ev.channelId);
                    if (!paneId) return;
                    setProcMetaByPane(prev => {
                        const cur = prev[paneId];
                        if (cur && cur.command === ev.command && cur.cwd === ev.cwd && cur.busy === ev.busy) {
                            return prev;
                        }
                        return {
                            ...prev,
                            [paneId]: {
                                command: ev.command,
                                cwd: ev.cwd,
                                busy: ev.busy,
                            },
                        };
                    });
                    if (ev.commandDone) {
                        markPaneTabActivityRef.current(paneId);
                    }
                },
                onPaneStatus: ev => {
                    if (disposed) return;
                    const paneId = channelToPaneRef.current.get(ev.channelId);
                    if (!paneId) return;
                    if (ev.statusCode === TERMINAL_STATUS_EXITED) {
                        attachedChannelsRef.current.delete(ev.channelId);
                        void handleClosePaneRef.current(paneId);
                        return;
                    }
                    if (ev.statusCode === TERMINAL_STATUS_ERROR || ev.statusCode === TERMINAL_STATUS_JOB_ENDED) {
                        setRuntimeByPane(prev => ({
                            ...prev,
                            [paneId]: {
                                warning: prev[paneId]?.warning ?? "",
                                fallbackShell: prev[paneId]?.fallbackShell ?? false,
                                error: ev.message || "Terminal process disconnected.",
                            },
                        }));
                    }
                },
            });

            wsClientRef.current = client;
        }).catch(() => {
            if (!disposed) {
                setConnectionState("disconnected");
                setConnectionNotice("Failed to establish terminal connection.");
            }
        });

        return () => {
            disposed = true;
            for (const timerId of resizeTimersRef.current.values()) {
                window.clearTimeout(timerId);
            }
            resizeTimersRef.current.clear();
            lastSentSizeRef.current.clear();
            wsClientRef.current?.close();
            wsClientRef.current = null;
            attachedChannelsRef.current.clear();
        };
    }, [session?.id, wsReconnectNonce]);

    const allPanesSignature = (session?.tabs ?? []).flatMap(t => t.panes).map(p => p.id).join(",");

    useEffect(() => {
        if (connectionState !== "connected") return;
        syncAttachedPanes();
    }, [connectionState, activeTab?.id, allPanesSignature, syncAttachedPanes]);

    const handleRegisterHandle = useCallback((paneId: string, handle: TerminalEngineHandle | null) => {
        if (handle) {
            paneHandlesRef.current.set(paneId, handle);
        } else {
            paneHandlesRef.current.delete(paneId);
        }
    }, []);

    const handlePaneMeasured = useCallback(() => {
        syncAttachedPanes();
    }, [syncAttachedPanes]);

    const handleCloseSearch = useCallback(() => {
        setSearchPaneId(null);
    }, []);

    const handleToggleMaximize = useCallback((paneId: string) => {
        setMaximizedPaneId(prev => prev === paneId ? null : paneId);
    }, []);

    const handleClosePaneCallback = useCallback((paneId: string) => {
        void handleClosePane(paneId);
    }, [handleClosePane]);

    const handlePaneData = useCallback((paneId: string, data: string) => {
        const ch = paneToChannelRef.current.get(paneId);
        if (ch === undefined) return;
        wsClientRef.current?.sendData(ch, data);
    }, []);

    const handlePaneResize = useCallback((paneId: string, cols: number, rows: number) => {
        const ch = paneToChannelRef.current.get(paneId);
        if (ch === undefined || !attachedChannelsRef.current.has(ch)) return;

        const existingTimer = resizeTimersRef.current.get(ch);
        if (existingTimer !== undefined) {
            window.clearTimeout(existingTimer);
        }

        const timerId = window.setTimeout(() => {
            resizeTimersRef.current.delete(ch);
            if (!attachedChannelsRef.current.has(ch)) return;
            const prev = lastSentSizeRef.current.get(ch);
            if (prev && prev.cols === cols && prev.rows === rows) return;
            lastSentSizeRef.current.set(ch, {cols, rows});
            wsClientRef.current?.sendResize(ch, cols, rows);
        }, 50);
        resizeTimersRef.current.set(ch, timerId);
    }, []);

    const handlePaneOscTitle = useCallback((paneId: string, rawTitle: string) => {
        const normalized = rawTitle.trim();
        if (!normalized) return;
        setOscTitleByPane(prev => prev[paneId] === normalized ? prev : {...prev, [paneId]: normalized});
    }, []);

    const handlePaneBell = useCallback((paneId: string) => {
        markPaneTabActivity(paneId);
    }, [markPaneTabActivity]);

    const handleRenamePane = useCallback((paneId: string, title: string) => {
        const current = sessionRef.current;
        if (!current) return;
        const nextTabs = current.tabs.map(tab => ({
            ...tab,
            panes: tab.panes.map(pane => pane.id === paneId ? {...pane, title, titlePinned: true} : pane),
        }));
        setSession({...current, tabs: nextTabs});
        scheduleSync({
            panes: [{id: paneId, title, titlePinned: true}],
        });
    }, [scheduleSync]);

    const handleSelectTab = useCallback((tabId: string) => {
        const current = sessionRef.current;
        if (!current || current.activeTabId === tabId) return;
        setMaximizedPaneId(null);
        setSearchPaneId(null);
        setActivityTabIds(prev => {
            if (!prev.has(tabId)) return prev;
            const next = new Set(prev);
            next.delete(tabId);
            return next;
        });
        setSession({...current, activeTabId: tabId});
        scheduleSync({activeTabId: tabId});
    }, [scheduleSync]);

    const handleFocusPane = useCallback((paneId: string) => {
        const current = sessionRef.current;
        if (!current) return;
        const tab = current.tabs.find(t => t.id === current.activeTabId) ?? current.tabs[0];
        if (!tab || tab.activePaneId === paneId) return;

        const nextTabs = current.tabs.map(t => t.id === tab.id ? {...t, activePaneId: paneId} : t);
        setSession({...current, tabs: nextTabs});
        scheduleSync({
            tabs: [{id: tab.id, activePaneId: paneId}],
        });
    }, [scheduleSync]);

    const handleCreateTab = useCallback(async (rank?: number) => {
        const current = sessionRef.current;
        if (!current) return;
        const currentTab = current.tabs.find(t => t.id === current.activeTabId) ?? current.tabs[0];
        const focusedPane = currentTab?.panes.find(p => p.id === currentTab.activePaneId) ?? currentTab?.panes[0];
        const targetRank = rank ?? focusedPane?.rank ?? 0;
        const existingPaneIds = new Set(current.tabs.flatMap(t => t.panes.map(p => p.id)));
        try {
            const updated = await callAPI(terminalSessionsApi.createTab(current.id, targetRank));
            if (focusedPane && focusedPane.rank === targetRank) {
                for (const t of updated.tabs) {
                    for (const p of t.panes) {
                        if (!existingPaneIds.has(p.id)) {
                            pendingSourceHandleRef.current.set(p.id, focusedPane.backendHandle);
                        }
                    }
                }
            }
            setMaximizedPaneId(null);
            setSession(updated);
            void refreshSessionsList();
        } catch {
            sendFailureNotification("Could not create tab");
        }
    }, [refreshSessionsList]);

    const handleSplitPane = useCallback(async (
        targetPaneId: string,
        direction: TerminalSessionSplitDirection,
        rank?: number,
        placeFirst = false,
    ) => {
        const current = sessionRef.current;
        if (!current) return;
        const targetPane = current.tabs.flatMap(t => t.panes).find(p => p.id === targetPaneId);
        const existingPaneIds = new Set(current.tabs.flatMap(t => t.panes.map(p => p.id)));
        setMaximizedPaneId(null);
        try {
            const updated = await callAPI(terminalSessionsApi.splitPane({
                sessionId: current.id,
                targetPaneId,
                direction,
                placeFirst,
                rank,
            }));
            if (targetPane && (rank === undefined || rank === targetPane.rank)) {
                for (const t of updated.tabs) {
                    for (const p of t.panes) {
                        if (!existingPaneIds.has(p.id)) {
                            pendingSourceHandleRef.current.set(p.id, targetPane.backendHandle);
                        }
                    }
                }
            }
            setSession(updated);
            void refreshSessionsList();
        } catch {
            sendFailureNotification("Could not split pane");
        }
    }, [refreshSessionsList]);

    const handleSplitPaneCallback = useCallback((paneId: string, direction: TerminalSessionSplitDirection, rank?: number) => {
        void handleSplitPane(paneId, direction, rank);
    }, [handleSplitPane]);

    const handleReorderTabs = useCallback((tabIds: string[]) => {
        const current = sessionRef.current;
        if (!current) return;
        const reordered = tabIds
            .map((id, position) => {
                const found = current.tabs.find(t => t.id === id);
                return found ? {...found, position} : null;
            })
            .filter((t): t is TerminalSessionTab => t !== null);
        if (reordered.length !== current.tabs.length) return;
        setSession({...current, tabs: reordered});
        scheduleSync({
            tabs: reordered.map((t, position) => ({id: t.id, position})),
        });
    }, [scheduleSync]);

    const handleRenameTab = useCallback((tabId: string) => {
        const current = sessionRef.current;
        if (!current) return;
        const tab = current.tabs.find(t => t.id === tabId);
        if (!tab) return;
        const nextTitle = window.prompt("Rename tab", tab.title);
        if (!nextTitle || !nextTitle.trim()) return;
        const trimmed = nextTitle.trim();
        setSession({
            ...current,
            tabs: current.tabs.map(t => t.id === tabId ? {...t, title: trimmed, titlePinned: true} : t),
        });
        scheduleSync({
            tabs: [{id: tabId, title: trimmed, titlePinned: true}],
        });
    }, [scheduleSync]);

    const handleCreateSession = useCallback(async (jobId: string, rank = 0) => {
        try {
            const created = await callAPI(terminalSessionsApi.create(jobId, rank));
            setSession(created);
            setMaximizedPaneId(null);
            setSearchPaneId(null);
            setRuntimeByPane({});
            void refreshSessionsList();
            if (!initialJobId) {
                navigate(`/terminals/${created.id}`, {replace: true});
            }
        } catch {
            sendFailureNotification("Could not create terminal session");
        }
    }, [initialJobId, navigate, refreshSessionsList]);

    const handleSelectSession = useCallback((sessionId: string) => {
        void loadSessionById(sessionId);
        if (!initialJobId) {
            navigate(`/terminals/${sessionId}`, {replace: true});
        }
    }, [initialJobId, loadSessionById, navigate]);

    const handleRenameSession = useCallback((sessionId: string, title: string) => {
        const current = sessionRef.current;
        if (!current || current.id !== sessionId) return;
        setSession({...current, title});
        scheduleSync({title});
        setSessions(prev => prev.map(s => s.id === sessionId ? {...s, title} : s));
    }, [scheduleSync]);

    const handleShutdownSession = useCallback(async (sessionId: string) => {
        wsClientRef.current?.close();
        wsClientRef.current = null;
        try {
            await callAPI(terminalSessionsApi.shutdown(sessionId));
            const remaining = await refreshSessionsList();
            if (remaining.length > 0) {
                await loadSessionById(remaining[0].id);
            } else {
                setSession(null);
            }
        } catch {
            sendFailureNotification("Could not shut down terminal session");
        }
    }, [loadSessionById, refreshSessionsList]);

    const handleUpdateLayout = useCallback((tabId: string, nextLayout: TerminalSessionLayoutNode) => {
        const current = sessionRef.current;
        if (!current) return;
        setSession({
            ...current,
            tabs: current.tabs.map(t => t.id === tabId ? {...t, layout: nextLayout} : t),
        });
        scheduleSync({
            tabs: [{id: tabId, layout: nextLayout}],
        });
    }, [scheduleSync]);

    const handleChangeRatio = useCallback((tabId: string, splitPath: Array<"first" | "second">, ratio: number) => {
        const current = sessionRef.current;
        if (!current) return;
        const tab = current.tabs.find(t => t.id === tabId);
        if (!tab) return;
        const updated = updateLayoutSplitRatio(tab.layout, splitPath, ratio);
        handleUpdateLayout(tabId, updated);
    }, [handleUpdateLayout]);

    const handleCloseTabCallback = useCallback((tabId: string) => {
        void handleCloseTab(tabId);
    }, [handleCloseTab]);

    const handleCreateSessionCallback = useCallback((jobId: string, rank?: number) => {
        void handleCreateSession(jobId, rank);
    }, [handleCreateSession]);

    const handleShutdownSessionCallback = useCallback((sessionId: string) => {
        void handleShutdownSession(sessionId);
    }, [handleShutdownSession]);

    const handleRefreshSessionsCallback = useCallback(() => {
        void refreshSessionsList();
    }, [refreshSessionsList]);

    const handleShortcutEvent = useCallback((event: KeyboardEvent): boolean => {
        if (event.type !== "keydown") return false;
        if (event.getModifierState("AltGraph")) return false;

        const current = sessionRef.current;
        const tab = current ? (current.tabs.find(t => t.id === current.activeTabId) ?? current.tabs[0] ?? null) : null;
        const focusedId = tab ? (tab.activePaneId && tab.panes.some(p => p.id === tab.activePaneId) ? tab.activePaneId : tab.panes[0]?.id ?? null) : null;

        const ctrlOrMeta = event.ctrlKey || event.metaKey;
        const isCtrlAlt = ctrlOrMeta && event.altKey;
        const isAltShiftOnly = event.altKey && event.shiftKey && !event.ctrlKey && !event.metaKey;

        if (ctrlOrMeta && !event.altKey && !event.shiftKey) {
            const focusedHandle = focusedId ? paneHandlesRef.current.get(focusedId) : undefined;
            if (focusedHandle) {
                if (event.key === "+" || event.key === "=" || event.code === "NumpadAdd") {
                    event.preventDefault();
                    focusedHandle.setFontSize(focusedHandle.getFontSize() + 1);
                    return true;
                }
                if (event.key === "-" || event.code === "NumpadSubtract") {
                    event.preventDefault();
                    focusedHandle.setFontSize(focusedHandle.getFontSize() - 1);
                    return true;
                }
                if (event.key === "0" || event.code === "Numpad0") {
                    event.preventDefault();
                    focusedHandle.setFontSize(TERMINAL_DEFAULT_FONT_SIZE);
                    return true;
                }
            }
        }

        if (isAltShiftOnly) {
            const arrowDir = arrowKeyToDirection(event.code);
            if (arrowDir && focusedId) {
                event.preventDefault();
                event.stopPropagation();
                if (!event.repeat) {
                    const splitDir: TerminalSessionSplitDirection = arrowDir === "left" || arrowDir === "right" ? "horizontal" : "vertical";
                    const placeFirst = arrowDir === "left" || arrowDir === "up";
                    void handleSplitPane(focusedId, splitDir, undefined, placeFirst);
                }
                return true;
            }
            if ((event.code === "PageUp" || event.code === "PageDown") && current && current.tabs.length > 1 && tab) {
                event.preventDefault();
                event.stopPropagation();
                const idx = current.tabs.findIndex(t => t.id === tab.id);
                const nextIdx = (idx + (event.code === "PageUp" ? -1 : 1) + current.tabs.length) % current.tabs.length;
                handleSelectTab(current.tabs[nextIdx].id);
                return true;
            }
        }

        if ((event.ctrlKey && event.shiftKey && !event.altKey && event.code === "KeyF") || (isCtrlAlt && !event.shiftKey && event.code === "KeyF")) {
            if (focusedId) {
                event.preventDefault();
                event.stopPropagation();
                if (!event.repeat) {
                    setSearchPaneId(focusedId);
                }
                return true;
            }
        }

        if (!isCtrlAlt) return false;

        if (event.code === "Slash" || event.key === "?") {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                setShortcutsOpen(prev => !prev);
            }
            return true;
        }

        if (!event.shiftKey && event.code === "KeyK") {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                setSwitcherOpen(prev => !prev);
            }
            return true;
        }

        if (!event.shiftKey && (event.code === "KeyT" || event.code === "Equal" || event.code === "NumpadAdd")) {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                void handleCreateTab();
            }
            return true;
        }

        if (event.code === "KeyW") {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                if (event.shiftKey && tab) {
                    void handleCloseTab(tab.id);
                } else if (focusedId) {
                    void handleClosePane(focusedId);
                }
            }
            return true;
        }

        if (event.code === "KeyD" && focusedId) {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                void handleSplitPane(focusedId, event.shiftKey ? "vertical" : "horizontal");
            }
            return true;
        }

        const isEnterKey = event.code === "Enter" || event.code === "NumpadEnter";
        if (!event.shiftKey && isEnterKey && focusedId) {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                setMaximizedPaneId(prev => prev === focusedId ? null : focusedId);
            }
            return true;
        }

        if (event.code === "KeyR") {
            event.preventDefault();
            event.stopPropagation();
            if (!event.repeat) {
                if (event.shiftKey && focusedId && tab) {
                    const pane = tab.panes.find(p => p.id === focusedId);
                    if (pane) {
                        const next = window.prompt("Rename pane", pane.title);
                        if (next && next.trim()) handleRenamePane(pane.id, next.trim());
                    }
                } else if (tab) {
                    handleRenameTab(tab.id);
                }
            }
            return true;
        }

        const dir = arrowKeyToDirection(event.code) ?? vimKeyToDirection(event.code);
        if (dir && tab && focusedId) {
            event.preventDefault();
            event.stopPropagation();
            if (event.shiftKey) {
                const resized = resizePaneByDirection(tab.layout, focusedId, dir, 0.05);
                if (resized) handleUpdateLayout(tab.id, resized);
            } else {
                const neighbor = findNeighborPaneId(tab.layout, focusedId, dir);
                if (neighbor) {
                    setMaximizedPaneId(null);
                    handleFocusPane(neighbor);
                    paneHandlesRef.current.get(neighbor)?.focus();
                }
            }
            return true;
        }

        return false;
    }, [
        handleClosePane,
        handleCloseTab,
        handleCreateTab,
        handleFocusPane,
        handleRenamePane,
        handleRenameTab,
        handleSelectTab,
        handleSplitPane,
        handleUpdateLayout,
    ]);

    useEffect(() => {
        const listener = (event: KeyboardEvent) => {
            if (event.defaultPrevented) return;
            const target = event.target as HTMLElement | null;
            if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) {
                if (!target.closest(".pane-terminal")) return;
            }
            handleShortcutEvent(event);
        };
        window.addEventListener("keydown", listener, true);
        return () => window.removeEventListener("keydown", listener, true);
    }, [handleShortcutEvent]);

    useEffect(() => {
        if (!newTabRankMenuOpen) return;
        const onPointerDown = (event: MouseEvent) => {
            if (!newTabMenuRef.current?.contains(event.target as Node)) {
                setNewTabRankMenuOpen(false);
            }
        };
        window.addEventListener("mousedown", onPointerDown);
        return () => window.removeEventListener("mousedown", onPointerDown);
    }, [newTabRankMenuOpen]);

    const openTabContextMenu = useCallback((tabId: string, position: {x: number; y: number}) => {
        const current = sessionRef.current;
        if (!current) return;
        const idx = current.tabs.findIndex(t => t.id === tabId);
        if (idx < 0) return;

        const ops: Operation<void>[] = [
            {
                text: "Rename tab",
                enabled: () => true,
                onClick: () => handleRenameTab(tabId),
            },
            {
                text: "Close tab",
                enabled: () => true,
                onClick: () => void handleCloseTab(tabId),
            },
            {
                text: "Close others",
                enabled: () => current.tabs.length > 1,
                onClick: () => {
                    for (const tab of current.tabs) {
                        if (tab.id !== tabId) void handleCloseTab(tab.id);
                    }
                },
            },
            {
                text: "Close to the right",
                enabled: () => idx < current.tabs.length - 1,
                onClick: () => {
                    for (let i = current.tabs.length - 1; i > idx; i--) {
                        void handleCloseTab(current.tabs[i].id);
                    }
                },
            },
        ];

        setTabMenuOps(ops);
        pendingMenuPosRef.current = position;
    }, [handleCloseTab, handleRenameTab]);

    useEffect(() => {
        const pos = pendingMenuPosRef.current;
        if (!pos) return;
        pendingMenuPosRef.current = null;
        openTabMenuRef.current(pos.x, pos.y);
    }, [tabMenuOps]);

    const hasMultiNode = (session?.replicas ?? 1) > 1;

    const tabStripItems = useMemo(() => {
        if (!session) return [];
        return session.tabs.map(tab => ({
            id: tab.id,
            title: tab.title,
            tooltip: tab.title,
            icon: <span className="tab-icon-wrap">
                <Icon name="heroCommandLine" size={14} />
                {activityTabIds.has(tab.id) ? <span className="tab-activity-dot" /> : null}
            </span>,
            closeLabel: `Close ${tab.title}`,
            closeTooltip: "Close tab (Ctrl+Alt+Shift+W)",
        }));
    }, [session?.tabs, activityTabIds]);

    return <div ref={rootRef} className={WorkspaceClass}>
        <div className="top-bar">
            <TerminalSessionSwitcher
                open={switcherOpen}
                onOpenChange={setSwitcherOpen}
                activeSession={session}
                sessions={sessions}
                onSelectSession={handleSelectSession}
                onCreateSession={handleCreateSessionCallback}
                onRenameSession={handleRenameSession}
                onShutdownSession={handleShutdownSessionCallback}
                onRefreshSessions={handleRefreshSessionsCallback}
            />

            <div className="tabs-region">
                {session ? <TabStrip
                    items={tabStripItems}
                    activeId={activeTab?.id}
                    autoSize={false}
                    shortcutScope={rootRef}
                    onActivate={handleSelectTab}
                    onClose={handleCloseTabCallback}
                    onContextMenu={openTabContextMenu}
                    onReorder={handleReorderTabs}
                /> : <div className="tabs-placeholder" />}

                <Operations
                    entityNameSingular=""
                    operations={tabMenuOps}
                    forceEvaluationOnOpen={true}
                    openFnRef={openTabMenuRef}
                    selected={[]}
                    extra={undefined}
                    hidden
                    location="IN_ROW"
                />

                {session ? <div className="new-tab-wrap" ref={newTabMenuRef}>
                    <IconButton
                        tooltip="New tab (Ctrl+Alt+T)"
                        icon="heroPlus"
                        ariaExpanded={hasMultiNode ? newTabRankMenuOpen : undefined}
                        onClick={() => {
                            if (hasMultiNode) {
                                setNewTabRankMenuOpen(prev => !prev);
                            } else {
                                void handleCreateTab(0);
                            }
                        }}
                    />
                    {newTabRankMenuOpen ? <div className="new-tab-rank-menu">
                        <div className="rank-menu-title">New tab on node</div>
                        {Array.from({length: session.replicas}, (_, r) => <button
                            key={r}
                            type="button"
                            className="rank-menu-item"
                            onClick={() => {
                                setNewTabRankMenuOpen(false);
                                void handleCreateTab(r);
                            }}
                        >
                            <Icon name="heroServer" size={13} />
                            <span>Node {r + 1}</span>
                        </button>)}
                    </div> : null}
                </div> : null}

                <IconButton
                    tooltip="Keyboard shortcuts (Ctrl+Alt+/)"
                    icon="heroQuestionMarkCircle"
                    onClick={() => setShortcutsOpen(true)}
                />
            </div>
        </div>

        {connectionState === "replaced" || connectionState === "disconnected" ? <div className="connection-bar">
            <Icon name="heroExclamationTriangle" size={15} />
            <span>{connectionNotice || "Terminal connection disconnected."}</span>
            <button type="button" className="reconnect-btn" onClick={() => setWsReconnectNonce(n => n + 1)}>
                Reconnect here
            </button>
        </div> : null}

        <div className="canvas-area">
            {loading ? (
                <div className="empty-state">Loading terminal workspace...</div>
            ) : !session ? (
                <div className="empty-state">
                    <Icon name="heroCommandLine" size={32} />
                    <h3>No active terminal sessions</h3>
                    <p>Create a persistent terminal session on any running job, or click Open terminal from a job page.</p>
                    <button type="button" className="primary-cta" onClick={() => setSwitcherOpen(true)}>
                        <Icon name="heroPlus" size={14} />
                        <span>New session</span>
                    </button>
                </div>
            ) : (
                session.tabs.map(tab => {
                    const isActiveTab = tab.id === activeTab?.id;
                    const tabActivePaneId = tab.activePaneId && tab.panes.some(p => p.id === tab.activePaneId)
                        ? tab.activePaneId
                        : tab.panes[0]?.id ?? null;
                    const tabMaximizedPaneId = isActiveTab && maximizedPaneId !== null && tab.panes.some(p => p.id === maximizedPaneId)
                        ? maximizedPaneId
                        : null;

                    return <TabPaneCanvas
                        key={tab.id}
                        tab={tab}
                        replicas={session.replicas}
                        activePaneId={tabActivePaneId}
                        maximizedPaneId={tabMaximizedPaneId}
                        isActiveTab={isActiveTab}
                        searchPaneId={isActiveTab ? searchPaneId : null}
                        runtimeByPane={runtimeByPane}
                        procMetaByPane={procMetaByPane}
                        oscTitleByPane={oscTitleByPane}
                        keyInterceptor={handleShortcutEvent}
                        onFocusPane={handleFocusPane}
                        onCloseSearch={handleCloseSearch}
                        onRegisterHandle={handleRegisterHandle}
                        onPaneMeasured={handlePaneMeasured}
                        onData={handlePaneData}
                        onResize={handlePaneResize}
                        onOscTitle={handlePaneOscTitle}
                        onBell={handlePaneBell}
                        onRenamePane={handleRenamePane}
                        onSplitPane={handleSplitPaneCallback}
                        onToggleMaximize={handleToggleMaximize}
                        onClosePane={handleClosePaneCallback}
                        onChangeRatio={handleChangeRatio}
                    />;
                })
            )}
        </div>

        <TerminalShortcutsModal open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} />
    </div>;
};

function boxStyle(box: LayoutBoxExpr): React.CSSProperties {
    return {
        left: axisExprToCss(box.left),
        top: axisExprToCss(box.top),
        width: axisExprToCss(box.width),
        height: axisExprToCss(box.height),
    };
}

const TabPaneCanvas = React.memo(function TabPaneCanvas({
    tab,
    replicas,
    activePaneId,
    maximizedPaneId,
    isActiveTab,
    searchPaneId,
    runtimeByPane,
    procMetaByPane,
    oscTitleByPane,
    keyInterceptor,
    onFocusPane,
    onCloseSearch,
    onRegisterHandle,
    onPaneMeasured,
    onData,
    onResize,
    onOscTitle,
    onBell,
    onRenamePane,
    onSplitPane,
    onToggleMaximize,
    onClosePane,
    onChangeRatio,
}: {
    tab: TerminalSessionTab;
    replicas: number;
    activePaneId: string | null;
    maximizedPaneId: string | null;
    isActiveTab: boolean;
    searchPaneId: string | null;
    runtimeByPane: Record<string, PaneRuntimeInfo>;
    procMetaByPane: Record<string, PaneProcMeta>;
    oscTitleByPane: Record<string, string>;
    keyInterceptor: (event: KeyboardEvent) => boolean;
    onFocusPane: (paneId: string) => void;
    onCloseSearch: () => void;
    onRegisterHandle: (paneId: string, handle: TerminalEngineHandle | null) => void;
    onPaneMeasured: (paneId: string) => void;
    onData: (paneId: string, data: string) => void;
    onResize: (paneId: string, cols: number, rows: number) => void;
    onOscTitle: (paneId: string, title: string) => void;
    onBell: (paneId: string) => void;
    onRenamePane: (paneId: string, title: string) => void;
    onSplitPane: (paneId: string, direction: TerminalSessionSplitDirection, rank?: number) => void;
    onToggleMaximize: (paneId: string) => void;
    onClosePane: (paneId: string) => void;
    onChangeRatio: (tabId: string, path: Array<"first" | "second">, ratio: number) => void;
}): React.ReactNode {
    const canvasRef = useRef<HTMLDivElement | null>(null);
    const geometry = useMemo(() => computeLayoutGeometry(tab.layout, 8), [tab.layout]);

    const startGutterDrag = (
        event: React.PointerEvent<HTMLDivElement>,
        path: Array<"first" | "second">,
        direction: TerminalSessionSplitDirection,
        parentBox: LayoutBoxExpr,
    ) => {
        if (event.button !== 0) return;
        event.preventDefault();
        const canvas = canvasRef.current;
        if (!canvas) return;
        const rect = canvas.getBoundingClientRect();
        const isHorizontal = direction !== "vertical";
        const parentLeft = rect.left + axisExprToPx(parentBox.left, rect.width);
        const parentTop = rect.top + axisExprToPx(parentBox.top, rect.height);
        const parentWidth = Math.max(1, axisExprToPx(parentBox.width, rect.width));
        const parentHeight = Math.max(1, axisExprToPx(parentBox.height, rect.height));

        const onMove = (moveEvent: PointerEvent) => {
            const raw = isHorizontal
                ? (moveEvent.clientX - parentLeft) / parentWidth
                : (moveEvent.clientY - parentTop) / parentHeight;
            onChangeRatio(tab.id, path, clampSplitRatio(raw));
        };
        const onUp = () => {
            window.removeEventListener("pointermove", onMove);
            window.removeEventListener("pointerup", onUp);
            window.removeEventListener("pointercancel", onUp);
        };
        window.addEventListener("pointermove", onMove);
        window.addEventListener("pointerup", onUp);
        window.addEventListener("pointercancel", onUp);
    };

    return <div
        ref={canvasRef}
        className="tab-canvas"
        data-active={isActiveTab}
    >
        {tab.panes.map(pane => {
            const isMaxTarget = maximizedPaneId === pane.id;
            const hiddenByMaximize = maximizedPaneId !== null && !isMaxTarget;
            const box = geometry.paneBoxes.get(pane.id);
            if (!box && !isMaxTarget) return null;

            const slotStyle: React.CSSProperties = isMaxTarget
                ? {left: 0, top: 0, width: "100%", height: "100%", zIndex: 5}
                : hiddenByMaximize
                    ? {...boxStyle(box!), visibility: "hidden", pointerEvents: "none", zIndex: 0}
                    : boxStyle(box!);

            return <div
                key={pane.id}
                className="pane-slot"
                style={slotStyle}
            >
                <TerminalPaneView
                    pane={pane}
                    displayTitle={resolvePaneDisplayTitle(pane, procMetaByPane[pane.id], oscTitleByPane[pane.id])}
                    replicas={replicas}
                    isFocused={isMaxTarget || pane.id === activePaneId}
                    isMaximized={isMaxTarget}
                    isVisible={isActiveTab && !hiddenByMaximize}
                    searchOpen={searchPaneId === pane.id}
                    runtimeInfo={runtimeByPane[pane.id]}
                    keyInterceptor={keyInterceptor}
                    onFocus={onFocusPane}
                    onCloseSearch={onCloseSearch}
                    onRegisterHandle={onRegisterHandle}
                    onMeasured={onPaneMeasured}
                    onData={onData}
                    onResize={onResize}
                    onOscTitle={onOscTitle}
                    onBell={onBell}
                    onRenamePane={onRenamePane}
                    onSplitPane={onSplitPane}
                    onToggleMaximize={onToggleMaximize}
                    onClosePane={onClosePane}
                />
            </div>;
        })}

        {maximizedPaneId === null ? geometry.gutters.map(gutter => <div
            key={gutter.key}
            className="split-gutter-overlay"
            style={boxStyle(gutter.gutterBox)}
        >
            <div
                className="split-gutter"
                data-direction={gutter.direction}
                onPointerDown={e => startGutterDrag(e, gutter.path, gutter.direction, gutter.parentBox)}
                onDoubleClick={() => onChangeRatio(tab.id, gutter.path, 0.5)}
            />
        </div>) : null}
    </div>;
});

const WorkspaceClass = injectStyle("terminal-workspace", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100vh;
        min-height: 0;
        background: var(--background);
        color: var(--textPrimary);
        padding: 8px;
        gap: 8px;
        box-sizing: border-box;
        font-family: var(--sansSerif);
    }

    html.dark ${k} {
        background: #000000;
    }

    ${k} .top-bar {
        display: flex;
        align-items: center;
        gap: 8px;
        flex: none;
        min-width: 0;
    }

    ${k} .tabs-region {
        flex: 1;
        min-width: 0;
        height: 32px;
        padding: 0 4px;
        display: flex;
        align-items: center;
        gap: 6px;
        box-sizing: border-box;
    }

    ${k} .tabs-placeholder {
        flex: 1;
    }

    ${k} .tab-icon-wrap {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
    }

    ${k} .tab-activity-dot {
        position: absolute;
        top: -2px;
        right: -3px;
        width: 6px;
        height: 6px;
        border-radius: 999px;
        background: var(--primaryMain);
        box-shadow: 0 0 0 1.5px var(--background);
    }

    html.dark ${k} .tab-activity-dot {
        box-shadow: 0 0 0 1.5px #000000;
    }

    ${k} .new-tab-wrap {
        position: relative;
        flex: none;
    }

    ${k} .new-tab-rank-menu {
        position: absolute;
        top: calc(100% + 6px);
        right: 0;
        z-index: 40;
        min-width: 150px;
        padding: 4px;
        border-radius: 7px;
        background: var(--backgroundCard);
        border: 1px solid var(--borderColor);
        box-shadow: 0 10px 28px rgba(0, 0, 0, 0.3);
        display: flex;
        flex-direction: column;
        gap: 2px;
    }

    ${k} .rank-menu-title {
        padding: 4px 8px;
        font-size: 11px;
        color: var(--textSecondary);
    }

    ${k} .rank-menu-item {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 8px;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        font: inherit;
        font-size: 12.5px;
        text-align: left;
    }

    ${k} .rank-menu-item:hover {
        background: var(--rowHover);
    }

    ${k} .top-icon-btn {
        width: 28px;
        height: 28px;
        padding: 0;
        flex: none;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 1px solid transparent;
        border-radius: 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
    }

    ${k} .top-icon-btn:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
        border-color: var(--borderColor);
    }

    ${k} .connection-bar {
        flex: none;
        padding: 6px 12px;
        border-radius: 7px;
        border: 1px solid rgba(210, 153, 34, 0.45);
        background: rgba(210, 153, 34, 0.14);
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
    }

    ${k} .connection-bar span {
        flex: 1;
    }

    ${k} .reconnect-btn {
        padding: 4px 10px;
        border-radius: 5px;
        border: 1px solid var(--primaryMain);
        background: var(--primaryMain);
        color: #ffffff;
        cursor: pointer;
        font: inherit;
        font-size: 12px;
    }

    ${k} .canvas-area {
        position: relative;
        flex: 1;
        min-height: 0;
        min-width: 0;
        display: flex;
    }

    ${k} .tab-canvas {
        position: absolute;
        inset: 0;
        min-height: 0;
        min-width: 0;
    }

    ${k} .tab-canvas[data-active="false"] {
        visibility: hidden;
        pointer-events: none;
        z-index: 0;
    }

    ${k} .tab-canvas[data-active="true"] {
        visibility: visible;
        pointer-events: auto;
        z-index: 1;
    }

    ${k} .pane-slot {
        position: absolute;
        display: flex;
        min-width: 0;
        min-height: 0;
        box-sizing: border-box;
    }

    ${k} .split-gutter-overlay {
        position: absolute;
        display: flex;
        z-index: 4;
    }

    ${k} .split-gutter-trigger {
        width: 100%;
        height: 100%;
        display: flex;
    }

    ${k} .split-gutter {
        flex: 1 1 auto;
        position: relative;
        touch-action: none;
    }

    ${k} .split-gutter[data-direction="horizontal"] {
        width: 8px;
        height: 100%;
        cursor: col-resize;
    }

    ${k} .split-gutter[data-direction="vertical"] {
        width: 100%;
        height: 8px;
        cursor: row-resize;
    }

    ${k} .split-gutter::after {
        content: "";
        position: absolute;
        border-radius: 2px;
        background: transparent;
        transition: background-color 120ms ease;
    }

    ${k} .split-gutter[data-direction="horizontal"]::after {
        top: 8px;
        bottom: 8px;
        left: 3px;
        width: 2px;
    }

    ${k} .split-gutter[data-direction="vertical"]::after {
        left: 8px;
        right: 8px;
        top: 3px;
        height: 2px;
    }

    ${k} .split-gutter:hover::after {
        background: var(--primaryMain);
    }

    ${k} .empty-state {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 10px;
        border: 1px dashed var(--borderColor);
        border-radius: 8px;
        color: var(--textSecondary);
        text-align: center;
        padding: 24px;
    }

    ${k} .empty-state h3 {
        margin: 0;
        color: var(--textPrimary);
        font-size: 16px;
    }

    ${k} .empty-state p {
        margin: 0;
        max-width: 420px;
        font-size: 13px;
    }

    ${k} .primary-cta {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 7px 14px;
        border: 0;
        border-radius: 6px;
        background: var(--primaryMain);
        color: #ffffff;
        cursor: pointer;
        font: inherit;
        font-size: 13px;
        font-weight: 500;
    }
`);

export default TerminalWorkspace;
