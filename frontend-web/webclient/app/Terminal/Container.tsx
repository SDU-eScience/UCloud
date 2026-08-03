import * as React from "react";
import {terminalClose, terminalCloseTab, terminalOpen, terminalOpenTab, terminalReorderTabs, terminalSelectTab, terminalUpdateTabTitle, TerminalState, TerminalPageContext, TerminalTab, useTerminalState} from "@/Terminal/State";
import {useCallback, useEffect, useRef, useState} from "react";
import {useLocation} from "react-router-dom";
import {IconButton} from "@/ui-components/IconButton";
import {TabStrip} from "@/ui-components/TabStrip";
import {injectStyle} from "@/Unstyled";
import {callAPI, noopCall, useCloudAPI} from "@/Authentication/DataHook";
import {BulkResponse} from "@/UCloud";
import JobsApi, {InteractiveSession} from "@/UCloud/JobsApi";
import {bulkRequestOf, bulkResponseOf, createKeyboardShortcut} from "@/UtilityFunctions";
import {ShellWithSession} from "@/Applications/Jobs/Shell";
import {xtermThemes} from "@/Applications/Jobs/XTermLib";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {Terminal} from "@xterm/xterm";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";
import {Operation, Operations} from "@/ui-components/Operation";
import {useDispatch} from "react-redux";
import {Dispatch} from "@reduxjs/toolkit";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {browseWalletsV2, WalletV2} from "@/Accounting";
import {fetchAll} from "@/Utilities/PageUtilities";
import {pathComponents} from "@/Utilities/FileUtilities";
import {sendFailureNotification} from "@/Notifications";

const MIN_TERMINAL_SIZE = 160;
const TERMINAL_COLLAPSED_SIZE = 53;
const TERMINAL_CHROME_HEIGHT = 90;
function terminalTabId(tab: TerminalTab, index: number): string {
    return tab.uniqueId ?? `terminal-tab-${index}`;
}

const Wrapper = injectStyle("wrapper", k => `
    ${k} {
        --tc-pad: 16px;
        --tc-controls-height: 50px;
        width: calc(100vw - var(--currentSidebarStickyWidth));
        height: var(--termsize, 0px);
        max-height: calc(100vh - 48px);
        background: ${xtermThemes.light.background};
        color: var(--textPrimary);
        position: fixed;
        bottom: 0;
        left: var(--currentSidebarStickyWidth);
        padding-left: var(--tc-pad);
        padding-right: var(--tc-pad);
        user-select: none;
        -webkit-user-select: none;
        font-family: var(--sansSerif);
        z-index: 10;
    }

    html.dark ${k} {
        background: ${xtermThemes.dark.background};
    }

    ${k} .resizer {
        width: calc(100% + var(--tc-pad) * 2);
        height: 8px;
        background: transparent;
        cursor: row-resize;
        position: relative;
        left: calc(var(--tc-pad) * -1);
        touch-action: none;
        z-index: 1;
    }

    ${k} .resizer::before {
        content: "";
        position: absolute;
        top: 3px;
        left: 0;
        right: 0;
        height: 2px;
        background: var(--borderColor);
    }

    ${k} .resizer:hover::before,
    ${k} .resizer:active::before {
        background: var(--primaryMain);
    }

    ${k} .controls {
        width: calc(100% + var(--tc-pad) * 2);
        margin-left: calc(-1 * var(--tc-pad));
        height: var(--tc-controls-height);
        padding: 4px 8px 4px 12px;
        display: flex;
        align-items: center;
        gap: 8px;
        background: var(--backgroundCard);
        font-family: var(--sansSerif);
    }

    ${k} .controls-spacer {
        flex: 0 0 1px;
        height: 24px;
        background: var(--borderColor);
    }

    ${k} .contents {
        width: 100%;
        height: calc(100% - var(--tc-controls-height));
    }
`);

function jobIdFromPath(pathname: string): string | null {
    const components = pathname.split("/").filter(Boolean);
    if (components[0] === "jobs" && components[1] === "properties") return components[2] ?? null;
    if (components[0] === "applications" && (components[1] === "shell" || components[1] === "vnc")) {
        return components[2] ?? null;
    }
    return null;
}

function isNewTerminalShortcut(event: KeyboardEvent): boolean {
    if (!event.altKey) return false;

    const tShortcut = (event.code === "KeyT" || event.key === "t" || event.key === "T") && !event.shiftKey;
    const equalsShortcut = event.code === "Equal" || event.code === "NumpadAdd" || event.key === "=" || event.key === "+";
    return tShortcut || equalsShortcut;
}

function preferredNewTerminalShortcut(): string {
    if (typeof navigator === "undefined") return createKeyboardShortcut("T", ["ctrl", "alt"]);

    const isLinux = /Linux/i.test(navigator.userAgent) || /Linux/i.test(navigator.platform);
    if (!isLinux) return createKeyboardShortcut("T", ["ctrl", "alt"]);

    const languages = [navigator.language, ...(navigator.languages ?? [])];
    const isDanish = languages.some(language => /^da(?:-|$)/i.test(language));
    return createKeyboardShortcut(isDanish ? "+" : "=", ["ctrl", "alt"]);
}

async function homeDriveFolder(providerId: string): Promise<string> {
    const drives = await fetchAll<FileCollection>(next => callAPI({
        ...FileCollectionsApi.browse({
            filterProvider: providerId,
            itemsPerPage: 250,
            next,
        }),
        projectOverride: "",
    }));
    const homeDrive = drives.find(it =>
        (it as FileCollection & {providerGeneratedId?: string}).providerGeneratedId?.startsWith("h-")
    );
    if (!homeDrive) {
        throw new Error(`No personal home drive was found for provider ${providerId}.`);
    }
    return `/${homeDrive.id}`;
}

async function resolveNewTerminalLocation(
    state: TerminalState,
    pathname: string,
    search: string,
): Promise<TerminalPageContext> {
    const activeTab = state.tabs[state.activeTab];
    if (activeTab) {
        return {
            folder: activeTab.folder,
            providerId: activeTab.providerId,
        };
    }

    const isFileBrowserPage = pathname === "/files" || pathname === "/files/";
    if (isFileBrowserPage) {
        if (state.pageContext) return state.pageContext;

        const path = new URLSearchParams(search).get("path");
        const collectionId = path == null || path === "/search" ? null : pathComponents(path)[0];
        if (collectionId) {
            const collection = await callAPI(FileCollectionsApi.retrieve({id: collectionId}));
            return {
                folder: path!,
                providerId: collection.specification.product.provider,
            };
        }
    }

    const jobId = jobIdFromPath(pathname);
    if (jobId) {
        const job = await callAPI(JobsApi.retrieve({id: jobId}));
        const providerId = job.specification.product.provider;
        return {folder: await homeDriveFolder(providerId), providerId};
    }

    const wallets = await fetchAll<WalletV2>(next => callAPI(browseWalletsV2({
        filterType: "STORAGE",
        itemsPerPage: 250,
        next,
    })));
    const wallet = wallets.reduce<WalletV2 | null>((largest, candidate) => {
        const candidateQuota = candidate.activeQuota ?? 0;
        const largestQuota = largest?.activeQuota ?? 0;
        return candidateQuota > largestQuota ? candidate : largest;
    }, null);
    if (!wallet || (wallet.activeQuota ?? 0) <= 0) {
        throw new Error("No active storage quota is available for a terminal.");
    }

    const providerId = wallet.paysFor.provider;
    return {folder: await homeDriveFolder(providerId), providerId};
}

export const TerminalContainer: React.FunctionComponent = () => {
    const state = useTerminalState();
    const dispatch = useDispatch();
    const location = useLocation();

    const termSizeSaved = useRef<number>(400);
    const isResizing = useRef(false);
    const activeTerminalRef = useRef<Terminal | null>(null);
    const terminalRoot = useRef<HTMLDivElement | null>(null);
    const creatingTerminal = useRef(false);
    const focusAfterCreate = useRef(false);
    const activeTabId = state.tabs[state.activeTab]?.uniqueId;
    const previousActiveTabId = useRef(activeTabId);
    const newTerminalShortcut = preferredNewTerminalShortcut();

    const setSize = useCallback((size: number) => {
        if (size > 0) {
            const maxSize = Math.max(MIN_TERMINAL_SIZE, window.innerHeight - 48);
            termSizeSaved.current = Math.min(Math.max(size, MIN_TERMINAL_SIZE), maxSize);
        }
        document.body.style.setProperty("--termsize", `${termSizeSaved.current}px`);
    }, []);

    useEffect(() => {
        if (state.open) {
            setSize(termSizeSaved.current);
        } else {
            if (state.tabs.length === 0) {
                document.body.style.setProperty("--termsize", "0px");
            } else {
                document.body.style.setProperty("--termsize", `${TERMINAL_COLLAPSED_SIZE}px`);
            }
        }
    }, [state.open, state.tabs.length, setSize]);

    const createNewTerminal = useCallback(async () => {
        if (creatingTerminal.current) return;
        creatingTerminal.current = true;
        try {
            const terminalLocation = await resolveNewTerminalLocation(state, location.pathname, location.search);
            focusAfterCreate.current = true;
            dispatch(terminalOpen());
            dispatch(terminalOpenTab({
                select: true,
                tab: {
                    title: "Terminal",
                    folder: terminalLocation.folder,
                    providerId: terminalLocation.providerId,
                },
            }));
        } catch (error) {
            focusAfterCreate.current = false;
            sendFailureNotification(error instanceof Error ? error.message : "Failed to create terminal.");
        } finally {
            creatingTerminal.current = false;
        }
    }, [dispatch, location.pathname, location.search, state.activeTab, state.pageContext, state.tabs]);

    useEffect(() => {
        const listener = (e: KeyboardEvent) => {
            if (!e.ctrlKey && !e.metaKey) return;

            if (isNewTerminalShortcut(e)) {
                e.preventDefault();
                e.stopPropagation();
                void createNewTerminal();
                return;
            }

            if (e.code === "Backquote" && !e.shiftKey && !e.altKey) {
                e.preventDefault();
                e.stopPropagation();
                if (state.tabs.length === 0 || state.activeTab < 0) {
                    void createNewTerminal();
                } else {
                    if (!state.open) dispatch(terminalOpen());
                    window.requestAnimationFrame(() => activeTerminalRef.current?.focus());
                }
                return;
            }

        };

        window.addEventListener("keydown", listener, true);
        return () => window.removeEventListener("keydown", listener, true);
    }, [createNewTerminal, dispatch, state.activeTab, state.open, state.tabs.length]);

    useEffect(() => {
        const tabChanged = previousActiveTabId.current !== activeTabId;
        const shouldFocus = tabChanged && (previousActiveTabId.current !== undefined || focusAfterCreate.current);
        previousActiveTabId.current = activeTabId;
        if (!shouldFocus) return;
        focusAfterCreate.current = false;

        const frame = window.requestAnimationFrame(() => activeTerminalRef.current?.focus());
        return () => window.cancelAnimationFrame(frame);
    }, [activeTabId]);

    const pointerMoveHandler: (e: PointerEvent) => void = useCallback(e => {
        const size = window.innerHeight - e.clientY;
        setSize(size);
    }, []);

    const stopResize = useCallback(() => {
        if (!isResizing.current) return;
        isResizing.current = false;
        window.removeEventListener("pointermove", pointerMoveHandler);
        window.removeEventListener("pointerup", stopResize);
        window.removeEventListener("pointercancel", stopResize);
        window.removeEventListener("blur", stopResize);
    }, [pointerMoveHandler]);

    useEffect(() => stopResize, [stopResize]);

    const onDragStart = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
        if (!state.open || e.button !== 0) return;
        e.preventDefault();
        isResizing.current = true;
        window.addEventListener("pointermove", pointerMoveHandler);
        window.addEventListener("pointerup", stopResize);
        window.addEventListener("pointercancel", stopResize);
        window.addEventListener("blur", stopResize);
    }, [pointerMoveHandler, state.open, stopResize]);

    const toggle = useCallback(() => {
        if (state.open) {
            dispatch(terminalClose());
        } else {
            dispatch(terminalOpen());
        }
    }, [state.open]);

    const closeTerminal = useCallback((idx: number) => {
        if (state.activeTab >= 0) {
            dispatch(terminalCloseTab({tabIdx: idx}))
        }
    }, [state.activeTab]);

    const [operations, setOperations] = useState<Operation<void>[]>([]);
    const openTabOperationWindow = useRef<(x: number, y: number) => void>(noopCall);
    const pendingTabOperationPosition = useRef<{x: number; y: number} | null>(null);
    const openTabOperations = React.useCallback((idx: number, position: {x: number; y: number;}) => {
        const ops = tabOperations(dispatch, idx, state);
        setOperations(ops);
        pendingTabOperationPosition.current = position;
    }, [state]);

    useEffect(() => {
        const position = pendingTabOperationPosition.current;
        if (!position) return;

        pendingTabOperationPosition.current = null;
        openTabOperationWindow.current(position.x, position.y);
    }, [operations]);

    const indexOfTab = useCallback((id: string) => state.tabs.findIndex((tab, index) => terminalTabId(tab, index) === id), [state.tabs]);

    return <div ref={terminalRoot} className={Wrapper}>
        <div className={"resizer"} onPointerDown={state.open ? onDragStart : undefined} />
        <div className="controls">
            <TabStrip
                items={state.tabs.map((tab, idx) => ({
                    id: terminalTabId(tab, idx),
                    title: tab.title,
                    tooltip: tab.title,
                    icon: <ProviderLogo providerId={tab.providerId} size={20} />,
                    closeLabel: `Close ${tab.title}`,
                    closeTooltip: idx === state.activeTab ? `Close tab (${createKeyboardShortcut("W", ["ctrl", "alt"])})` : "Close tab",
                }))}
                activeId={activeTabId}
                shortcutScope={terminalRoot}
                onActivate={id => dispatch(terminalSelectTab({tabIdx: indexOfTab(id)}))}
                onClose={id => closeTerminal(indexOfTab(id))}
                onContextMenu={(id, position) => openTabOperations(indexOfTab(id), position)}
                onReorder={tabIds => dispatch(terminalReorderTabs({tabIds}))}
            />

            <Operations
                entityNameSingular={""}
                operations={operations}
                forceEvaluationOnOpen={true}
                openFnRef={openTabOperationWindow}
                selected={[]}
                extra={undefined}
                hidden
                location={"IN_ROW"}
            />

            <div className="controls-spacer" />

            <IconButton tooltip={`New terminal (${newTerminalShortcut})`} onClick={() => void createNewTerminal()} icon="heroPlus" />
            <IconButton tooltip={state.open ? "Collapse terminal" : "Expand terminal"} onClick={toggle} icon={state.open ? "anglesDownSolid" : "anglesUpSolid"} ariaExpanded={state.open} />
        </div>

        {state.tabs.map((tab, idx) =>
            <IndividualTerminal
                key={tab.uniqueId ?? idx.toString()}
                tab={tab}
                tabIdx={idx}
                hidden={state.activeTab !== idx}
                focusedTerminalRef={state.activeTab === idx ? activeTerminalRef : undefined}
            />
        )}
    </div>;
};

function tabOperations(dispatch: Dispatch, tabIdx: number, state: TerminalState): Operation<void>[] {
    return [
        {
            text: "Close tab",
            enabled: () => true,
            onClick() {
                dispatch(terminalCloseTab({tabIdx: tabIdx}));
            },
        },
        {
            text: "Close others", enabled: () => state.tabs.length > 1,
            onClick() {
                for (let idx = state.tabs.length - 1; idx >= 0; idx--) {
                    if (idx === tabIdx) continue;
                    dispatch(terminalCloseTab({tabIdx: idx}));
                }
            },
        },
        {
            text: "Close to the right", enabled: () => tabIdx < state.tabs.length - 1, onClick() {
                if (tabIdx < state.activeTab) {
                    dispatch(terminalSelectTab({tabIdx}));
                }

                for (let i = state.tabs.length - 1; i > tabIdx; i--) {
                    dispatch(terminalCloseTab({tabIdx: i}))
                }
            },
        },
        {
            text: "Close all", enabled: () => true, onClick() {
                for (let i = state.tabs.length - 1; i >= 0; i--) {
                    dispatch(terminalCloseTab({tabIdx: i}));
                }
                dispatch(terminalClose());
            },
        },
    ];
}

const IndividualTerminal: React.FunctionComponent<{tab: TerminalTab, tabIdx: number, hidden: boolean, focusedTerminalRef?: React.RefObject<Terminal | null>}> = props => {
    const [size, setSize] = useState<[number, number]>([80, 40]);
    const terminal = useRef<Terminal | null>(null);
    const dispatch = useDispatch();
    const [sessionResp, openSession] = useCloudAPI<BulkResponse<InteractiveSession>>(
        {noop: true},
        bulkResponseOf()
    );

    const doReconnect = useCallback(() => {
        openSession(JobsApi.openTerminalInFolder(
            bulkRequestOf({folder: props.tab.folder}))
        );
    }, [props.tab.folder]);

    const updateTitle = useCallback((title: string) => {
        const normalizedTitle = title.trim();
        if (normalizedTitle.length === 0 || normalizedTitle === props.tab.title) return;
        dispatch(terminalUpdateTabTitle({tabIdx: props.tabIdx, title: normalizedTitle}));
    }, [props.tab.title, props.tabIdx]);

    useEffect(() => {
        doReconnect();
    }, [props.tab.folder]);

    useEffect(() => {
        const i = window.setInterval(() => {
            let cssPropertyValue = getCssPropertyValue(CSSVarCurrentSidebarStickyWidth);
            let stickySidebar = parseInt(cssPropertyValue);
            if (isNaN(stickySidebar)) stickySidebar = 0;
            const width = window.innerWidth - stickySidebar;

            let termHeight = parseInt(getCssPropertyValue("--termsize"));
            if (isNaN(termHeight)) termHeight = 0;
            termHeight -= TERMINAL_CHROME_HEIGHT;

            const cols = Math.max(1, Math.floor(width / 10));
            const rows = Math.max(1, Math.floor(termHeight / 20));

            setSize([cols, rows]);
        }, 100);

        return () => {
            window.clearInterval(i);
        }
    }, []);

    useEffect(() => {
        const [cols, rows] = size;
        terminal.current?.resize(cols, rows);
    }, [size[0], size[1]]);

    const sessionWithProvider = sessionResp.data.responses.length > 0 ? sessionResp.data.responses[0] : null;
    return <div style={{display: props.hidden ? "none" : "block"}}>
        <ShellWithSession
            sessionWithProvider={sessionWithProvider}
            xtermRef={terminal}
            focusedTerminalRef={props.focusedTerminalRef}
            autofit={false}
            reconnect={doReconnect}
            onTitleChange={updateTitle}
        />
    </div>;
}
