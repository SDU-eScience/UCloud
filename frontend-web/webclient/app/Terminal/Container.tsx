import * as React from "react";
import {terminalClose, terminalCloseTab, terminalOpen, terminalOpenTab, terminalReorderTabs, terminalSelectTab, terminalUpdateTabTitle, TerminalState, TerminalPageContext, TerminalTab, useTerminalState} from "@/Terminal/State";
import {useCallback, useEffect, useRef, useMemo, useState} from "react";
import {useLocation} from "react-router-dom";
import {Icon, Truncate} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {callAPI, noopCall, useCloudAPI} from "@/Authentication/DataHook";
import {BulkResponse} from "@/UCloud";
import JobsApi, {InteractiveSession} from "@/UCloud/JobsApi";
import {bulkRequestOf, bulkResponseOf} from "@/UtilityFunctions";
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
const TERMINAL_CHROME_HEIGHT = 85;
const TAB_DROP_ANIMATION_MS = 160;

interface TabSlot {
    left: number;
    width: number;
}

interface TabDragState {
    id: string;
    order: string[];
    originalOrder: string[];
    pointerId: number;
    pointerOffset: number;
    pointerLeft: number;
    slots: TabSlot[];
    draggedWidth: number;
    dropping: boolean;
}

function terminalTabId(tab: TerminalTab, index: number): string {
    return tab.uniqueId ?? `terminal-tab-${index}`;
}

const Wrapper = injectStyle("wrapper", k => `
    ${k} {
        --tc-pad: 16px;
        --tc-controls-height: 45px;
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

    ${k} .tabs {
        min-width: 0;
        height: 100%;
        display: flex;
        align-items: stretch;
        gap: 4px;
        flex: 1 1 auto;
        overflow-x: auto;
        overflow-y: hidden;
    }

    ${k} .terminal-tab {
        min-width: 184px;
        width: auto;
        max-width: none;
        height: 36px;
        margin-top: auto;
        padding: 0 4px 0 8px;
        display: flex;
        flex: 1 0 184px;
        align-items: center;
        gap: 6px;
        border: 1px solid var(--borderColor);
        border-radius: 7px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font-family: var(--sansSerif);
        font-size: 13px;
        line-height: 1;
        touch-action: none;
        transition: transform ${TAB_DROP_ANIMATION_MS}ms ease, background-color 120ms ease, border-color 120ms ease, color 120ms ease;
    }

    ${k} .terminal-tab[data-dragging="true"] {
        z-index: 2;
        cursor: grabbing;
        transition: none;
    }

    ${k} .terminal-tab[data-dragging="true"][data-dropping="true"] {
        transition: transform ${TAB_DROP_ANIMATION_MS}ms ease;
    }

    ${k} .terminal-tab[data-suppress-transition="true"] {
        transition: none !important;
    }

    ${k} .terminal-tab img {
        -webkit-user-drag: none;
    }

    ${k} .terminal-tab:hover {
        background: var(--rowHover);
        border-color: var(--borderColorHover);
        color: var(--textPrimary);
    }

    ${k} .terminal-tab[data-active="true"] {
        background: var(--rowActive);
        border-color: var(--primaryMain);
        color: var(--textPrimary);
    }

    ${k} .terminal-tab:focus-visible,
    ${k} .control:focus-visible,
    ${k} .tab-close:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }

    ${k} .tab-icon,
    ${k} .tab-title {
        flex: none;
    }

    ${k} .tab-title {
        min-width: 0;
        flex: 1 1 auto;
        text-align: left;
    }

    ${k} .tab-close,
    ${k} .control {
        width: 16px;
        height: 16px;
        padding: 0;
        display: inline-flex;
        flex: none;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font: inherit;
        transition: background-color 120ms ease, color 120ms ease;
    }

    ${k} .tab-close:hover,
    ${k} .control:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k} .tab-close {
        opacity: 0.75;
    }

    ${k} .terminal-tab:hover .tab-close,
    ${k} .terminal-tab[data-active="true"] .tab-close {
        opacity: 1;
    }

    ${k} .controls-spacer {
        flex: 0 0 1px;
        height: 24px;
        background: var(--borderColor);
    }

    ${k} .control {
        width: 32px;
        height: 32px;
    }

    ${k} .contents {
        width: 100%;
        height: calc(100% - var(--tc-controls-height));
    }
`);

const TabTitleTooltipTrigger = injectStyle("terminal-tab-title-tooltip-trigger", k => `
    ${k} {
        min-width: 0;
        flex: 1 1 auto;
        overflow: hidden;
    }

    ${k} > .tab-title {
        width: 100%;
    }
`);

const TabCloseTooltipTrigger = injectStyle("terminal-tab-close-tooltip-trigger", k => `
    ${k} {
        width: 16px;
        height: 16px;
        display: inline-flex;
        flex: none;
        align-items: center;
        justify-content: center;
    }
`);

const TerminalControlTooltipTrigger = injectStyle("terminal-control-tooltip-trigger", k => `
    ${k} {
        width: 32px;
        height: 32px;
        display: inline-flex;
        flex: none;
        align-items: center;
        justify-content: center;
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
    if (typeof navigator === "undefined") return "Ctrl + Alt + T";

    const isLinux = /Linux/i.test(navigator.userAgent) || /Linux/i.test(navigator.platform);
    if (!isLinux) return "Ctrl + Alt + T";

    const languages = [navigator.language, ...(navigator.languages ?? [])];
    const isDanish = languages.some(language => /^da(?:-|$)/i.test(language));
    return isDanish ? "Ctrl + Alt + +" : "Ctrl + Alt + =";
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

            if (e.code === "KeyW" && e.altKey && !e.shiftKey && state.activeTab >= 0 && state.activeTab < state.tabs.length) {
                e.preventDefault();
                e.stopPropagation();
                dispatch(terminalCloseTab({tabIdx: state.activeTab}));
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

            if (state.tabs.length < 2 || !e.altKey || e.shiftKey) return;
            if (e.code !== "PageUp" && e.code !== "PageDown") return;

            e.preventDefault();
            e.stopPropagation();

            const direction = e.code === "PageUp" ? -1 : 1;
            const currentTab = Math.max(0, state.activeTab);
            const nextTab = (currentTab + direction + state.tabs.length) % state.tabs.length;
            dispatch(terminalSelectTab({tabIdx: nextTab}));
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

    const tabsRef = useRef<HTMLDivElement | null>(null);
    const tabRefs = useRef<Record<string, HTMLDivElement | null>>({});
    const tabDragRef = useRef<TabDragState | null>(null);
    const tabDropFrameRef = useRef<number | null>(null);
    const [tabDrag, setTabDrag] = useState<TabDragState | null>(null);
    const [suppressTabTransitions, setSuppressTabTransitions] = useState(false);

    useEffect(() => {
        if (!activeTabId) return;

        const frame = window.requestAnimationFrame(() => {
            const tabs = tabsRef.current;
            const tab = tabRefs.current[activeTabId];
            if (!tabs || !tab) return;

            const tabLeft = tab.offsetLeft;
            const tabRight = tabLeft + tab.offsetWidth;
            const visibleLeft = tabs.scrollLeft;
            const visibleRight = visibleLeft + tabs.clientWidth;
            if (tabLeft < visibleLeft) {
                tabs.scrollTo({left: tabLeft, behavior: "smooth"});
            } else if (tabRight > visibleRight) {
                tabs.scrollTo({left: tabRight - tabs.clientWidth, behavior: "smooth"});
            }
        });

        return () => window.cancelAnimationFrame(frame);
    }, [activeTabId]);

    const handleTabDragMove = useCallback((e: PointerEvent) => {
        const drag = tabDragRef.current;
        const tabs = tabsRef.current;
        if (!drag || drag.dropping || !tabs || e.pointerId !== drag.pointerId) return;

        e.preventDefault();
        const tabsRect = tabs.getBoundingClientRect();
        const edgeSize = 40;
        if (e.clientX < tabsRect.left + edgeSize) {
            tabs.scrollLeft -= Math.max(1, Math.ceil((tabsRect.left + edgeSize - e.clientX) / 4));
        } else if (e.clientX > tabsRect.right - edgeSize) {
            tabs.scrollLeft += Math.max(1, Math.ceil((e.clientX - tabsRect.right + edgeSize) / 4));
        }

        const rawPointerLeft = e.clientX + tabs.scrollLeft - drag.pointerOffset;
        const containerLeft = tabsRect.left + tabs.scrollLeft;
        const containerRight = tabsRect.right + tabs.scrollLeft;
        const minPointerLeft = containerLeft;
        const maxPointerLeft = Math.max(minPointerLeft, containerRight - drag.draggedWidth);
        const pointerLeft = Math.min(Math.max(rawPointerLeft, minPointerLeft), maxPointerLeft);
        let order = drag.order;
        let currentIndex = order.indexOf(drag.id);

        if (pointerLeft > drag.pointerLeft) {
            while (currentIndex < order.length - 1) {
                const targetId = order[currentIndex + 1];
                const targetSlot = drag.slots[drag.originalOrder.indexOf(targetId)];
                if (pointerLeft + drag.draggedWidth <= targetSlot.left + targetSlot.width / 2) break;

                order = [...order];
                order.splice(currentIndex, 0, order.splice(currentIndex + 1, 1)[0]);
                currentIndex++;
            }
        } else if (pointerLeft < drag.pointerLeft) {
            while (currentIndex > 0) {
                const targetId = order[currentIndex - 1];
                const targetSlot = drag.slots[drag.originalOrder.indexOf(targetId)];
                if (pointerLeft >= targetSlot.left + targetSlot.width / 2) break;

                order = [...order];
                order.splice(currentIndex - 1, 0, order.splice(currentIndex, 1)[0]);
                currentIndex--;
            }
        }

        const nextDrag = {...drag, order, pointerLeft};
        tabDragRef.current = nextDrag;
        setTabDrag(nextDrag);
    }, []);

    const commitTabDrop = useCallback(() => {
        const drag = tabDragRef.current;
        if (!drag?.dropping) return;

        if (tabDropFrameRef.current !== null) {
            window.cancelAnimationFrame(tabDropFrameRef.current);
            tabDropFrameRef.current = null;
        }

        dispatch(terminalReorderTabs({tabIds: drag.order}));
        setSuppressTabTransitions(true);
        window.requestAnimationFrame(() => setSuppressTabTransitions(false));
        tabDragRef.current = null;
        setTabDrag(null);
    }, [dispatch]);

    const finishTabDrag = useCallback((commit: boolean) => {
        const drag = tabDragRef.current;
        if (!drag || drag.dropping) return;

        if (!commit || !drag.order.some((id, index) => id !== drag.originalOrder[index])) {
            tabDragRef.current = null;
            setTabDrag(null);
            return;
        }

        const dropping = {...drag, dropping: true};
        tabDragRef.current = dropping;
        setTabDrag(dropping);

        tabDropFrameRef.current = window.requestAnimationFrame(() => {
            const currentDrag = tabDragRef.current;
            if (!currentDrag?.dropping) return;

            const destinationSlot = currentDrag.slots[currentDrag.order.indexOf(currentDrag.id)];
            const animatedDrop = {...currentDrag, pointerLeft: destinationSlot.left};
            tabDragRef.current = animatedDrop;
            setTabDrag(animatedDrop);
            tabDropFrameRef.current = null;
        });
    }, [commitTabDrop]);

    const handleTabDragEnd = useCallback((e: PointerEvent) => {
        if (tabDragRef.current?.pointerId === e.pointerId) finishTabDrag(true);
    }, [finishTabDrag]);

    const handleTabDragCancel = useCallback((e: PointerEvent) => {
        if (tabDragRef.current?.pointerId === e.pointerId) finishTabDrag(false);
    }, [finishTabDrag]);

    useEffect(() => {
        if (tabDrag === null) return;

        window.addEventListener("pointermove", handleTabDragMove);
        window.addEventListener("pointerup", handleTabDragEnd);
        window.addEventListener("pointercancel", handleTabDragCancel);
        return () => {
            window.removeEventListener("pointermove", handleTabDragMove);
            window.removeEventListener("pointerup", handleTabDragEnd);
            window.removeEventListener("pointercancel", handleTabDragCancel);
        };
    }, [tabDrag !== null, handleTabDragMove, handleTabDragEnd, handleTabDragCancel]);

    useEffect(() => () => {
        if (tabDropFrameRef.current !== null) window.cancelAnimationFrame(tabDropFrameRef.current);
    }, []);

    const beginTabDrag = useCallback((e: React.PointerEvent<HTMLDivElement>, id: string) => {
        if (e.button !== 0 || (e.target as HTMLElement).closest(".tab-close")) return;

        const tabs = tabsRef.current;
        const tab = tabRefs.current[id];
        if (!tabs || !tab) return;

        const originalOrder = state.tabs.map(terminalTabId);
        const tabIndex = originalOrder.indexOf(id);
        if (tabIndex < 0 || originalOrder.some(tabId => !tabRefs.current[tabId])) return;

        const scrollLeft = tabs.scrollLeft;
        const slots = originalOrder.map(tabId => {
            const rect = tabRefs.current[tabId]!.getBoundingClientRect();
            return {left: rect.left + scrollLeft, width: rect.width};
        });
        const rect = tab.getBoundingClientRect();
        const drag: TabDragState = {
            id,
            order: originalOrder,
            originalOrder,
            pointerId: e.pointerId,
            pointerOffset: e.clientX - rect.left,
            pointerLeft: e.clientX + scrollLeft - (e.clientX - rect.left),
            slots,
            draggedWidth: slots[tabIndex].width,
            dropping: false,
        };

        tabDragRef.current = drag;
        setTabDrag(drag);
        e.currentTarget.setPointerCapture(e.pointerId);
    }, [state.tabs]);

    const tabDragStyle = useCallback((id: string): React.CSSProperties | undefined => {
        if (!tabDrag) return undefined;

        const baseIndex = tabDrag.originalOrder.indexOf(id);
        const destinationIndex = tabDrag.order.indexOf(id);
        const baseSlot = tabDrag.slots[baseIndex];
        const destinationSlot = tabDrag.slots[destinationIndex];
        if (!baseSlot || !destinationSlot) return undefined;

        const left = id === tabDrag.id ? tabDrag.pointerLeft : destinationSlot.left;
        return {transform: `translateX(${left - baseSlot.left}px)`};
    }, [tabDrag]);

    const tabComponents = useMemo(() => state.tabs.map((tab, idx) => (
        <div
            key={terminalTabId(tab, idx)}
            ref={node => {
                tabRefs.current[terminalTabId(tab, idx)] = node;
            }}
            className="terminal-tab"
            role="tab"
            tabIndex={0}
            aria-selected={idx === state.activeTab}
            data-active={idx === state.activeTab}
            data-dragging={tabDrag?.id === terminalTabId(tab, idx)}
            data-dropping={tabDrag?.dropping && tabDrag.id === terminalTabId(tab, idx)}
            data-suppress-transition={suppressTabTransitions}
            style={tabDragStyle(terminalTabId(tab, idx))}
            onPointerDown={e => beginTabDrag(e, terminalTabId(tab, idx))}
            onMouseDown={e => {
                if (e.button === 1 && !(e.target as HTMLElement).closest(".tab-close")) {
                    e.preventDefault();
                    closeTerminal(idx);
                }
            }}
            onTransitionEnd={e => {
                if (e.propertyName === "transform" && tabDrag?.dropping && tabDrag.id === terminalTabId(tab, idx)) {
                    commitTabDrop();
                }
            }}
            onClick={e => {
                if (e.button === 0) {
                    dispatch(terminalSelectTab({tabIdx: idx}));
                }
            }}
            onKeyDown={e => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    dispatch(terminalSelectTab({tabIdx: idx}));
                }
            }}
            onContextMenu={e => {
                e.preventDefault();
                e.stopPropagation();
                openTabOperations(idx, {x: e.clientX, y: e.clientY});
            }}
        >
            <ProviderLogo className="tab-icon" providerId={tab.providerId} size={20} />
            <TooltipV2 tooltip={tab.title} side="top" triggerClassName={TabTitleTooltipTrigger}>
                <Truncate className="tab-title">{tab.title}</Truncate>
            </TooltipV2>
            <TooltipV2 tooltip={idx === state.activeTab ? "Close tab (Ctrl + Alt + W)" : "Close tab"} side="top" contentWidth={150} triggerClassName={TabCloseTooltipTrigger}>
                <button
                    type="button"
                    className="tab-close"
                    aria-label={`Close ${tab.title}`}
                    onClick={e => {
                        e.stopPropagation();
                        closeTerminal(idx);
                    }}
                >
                    <Icon name="close" size={10} />
                </button>
            </TooltipV2>
        </div>
    )), [state.tabs, state.activeTab, closeTerminal, tabDrag, tabDragStyle, beginTabDrag, commitTabDrop]);

    return <div className={Wrapper}>
        <div className={"resizer"} onPointerDown={state.open ? onDragStart : undefined} />
        <div className="controls">
            <div className="tabs" ref={tabsRef}>{tabComponents}</div>

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

            <TooltipV2 tooltip={`New terminal (${newTerminalShortcut})`} side="top" contentWidth={170} triggerClassName={TerminalControlTooltipTrigger}>
                <button
                    type="button"
                    className="control"
                    onClick={() => void createNewTerminal()}
                    aria-label="New terminal"
                >
                    <Icon name="heroPlus" size={15} />
                </button>
            </TooltipV2>

            <TooltipV2 tooltip={state.open ? "Collapse terminal" : "Expand terminal"} side="top" contentWidth={130} triggerClassName={TerminalControlTooltipTrigger}>
                <button
                    type="button"
                    className="control"
                    onClick={toggle}
                    aria-expanded={state.open}
                    aria-label={state.open ? "Collapse terminal" : "Expand terminal"}
                >
                    <Icon name={state.open ? "anglesDownSolid" : "anglesUpSolid"} size={15} />
                </button>
            </TooltipV2>
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
