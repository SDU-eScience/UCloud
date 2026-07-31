import * as React from "react";
import {terminalClose, terminalCloseTab, terminalOpen, terminalSelectTab, TerminalState, TerminalTab, useTerminalState} from "@/Terminal/State";
import {useCallback, useEffect, useRef, useMemo, useState} from "react";
import {Icon, Truncate} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {noopCall, useCloudAPI} from "@/Authentication/DataHook";
import {BulkResponse} from "@/UCloud";
import JobsApi, {InteractiveSession} from "@/UCloud/JobsApi";
import {bulkRequestOf, bulkResponseOf} from "@/UtilityFunctions";
import {ShellWithSession} from "@/Applications/Jobs/Shell";
import {xtermThemes} from "@/Applications/Jobs/XTermLib";
import {Terminal} from "@xterm/xterm";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";
import {Operation, Operations, ShortcutKey} from "@/ui-components/Operation";
import {useDispatch} from "react-redux";
import {Dispatch} from "@reduxjs/toolkit";

const MIN_TERMINAL_SIZE = 160;
const TERMINAL_COLLAPSED_SIZE = 53;
const TERMINAL_CHROME_HEIGHT = 85;

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
        scrollbar-width: none;
    }

    ${k} .tabs::-webkit-scrollbar {
        display: none;
    }

    ${k} .terminal-tab {
        min-width: 132px;
        width: 184px;
        max-width: 220px;
        height: 36px;
        margin-top: auto;
        padding: 0 4px 0 8px;
        display: flex;
        flex: 0 1 184px;
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
        transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
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

export const TerminalContainer: React.FunctionComponent = () => {
    const state = useTerminalState();
    const dispatch = useDispatch();

    const termSizeSaved = useRef<number>(400);
    const isResizing = useRef(false);

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
    const openTabOperations = React.useCallback((idx: number, position: {x: number; y: number;}) => {
        const ops = tabOperations(dispatch, idx, state);
        setOperations(ops);
        openTabOperationWindow.current(position.x, position.y);
    }, [state]);

    const tabComponents = useMemo(() => state.tabs.map((tab, idx) => (
        <div
            key={tab.uniqueId ?? idx}
            className="terminal-tab"
            role="tab"
            tabIndex={0}
            aria-selected={idx === state.activeTab}
            data-active={idx === state.activeTab}
            onClick={e => {
                if (e.button === 1) {
                    e.preventDefault();
                    closeTerminal(idx);
                } else if (e.button === 0) {
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
            <Icon className="tab-icon" name="terminalSolid" size={15} color="textPrimary" />
            <Truncate className="tab-title" title={tab.title}>{tab.title}</Truncate>
            <button
                type="button"
                className="tab-close"
                aria-label={`Close ${tab.title}`}
                title="Close tab"
                onClick={e => {
                    e.stopPropagation();
                    closeTerminal(idx);
                }}
            >
                <Icon name="close" size={10} />
            </button>
        </div>
    )), [state.tabs, state.activeTab, closeTerminal]);

    const openTabOperationWindow = useRef<(x: number, y: number) => void>(noopCall)

    return <div className={Wrapper}>
        <div className={"resizer"} onPointerDown={state.open ? onDragStart : undefined} />
        <div className="controls">
            <div className="tabs">{tabComponents}</div>

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

            <button
                type="button"
                className="control"
                onClick={toggle}
                aria-expanded={state.open}
                aria-label={state.open ? "Collapse terminal" : "Expand terminal"}
                title={state.open ? "Collapse terminal" : "Expand terminal"}
            >
                <Icon name={state.open ? "anglesDownSolid" : "anglesUpSolid"} size={15} />
            </button>
        </div>

        {state.tabs.map((tab, idx) =>
            <IndividualTerminal key={tab.uniqueId ?? idx.toString()} tab={tab} hidden={state.activeTab !== idx} />
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
            text: "Close to the right", enabled: () => true /* todo */, onClick() {
                for (let i = state.tabs.length - 1; i > tabIdx; i--) {
                    dispatch(terminalCloseTab({tabIdx: i}))
                }

                if (tabIdx < state.activeTab) {
                    terminalSelectTab({tabIdx})
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

const IndividualTerminal: React.FunctionComponent<{tab: TerminalTab, hidden: boolean}> = props => {
    const [size, setSize] = useState<[number, number]>([80, 40]);
    const terminal = useRef<Terminal | null>(null);
    const [sessionResp, openSession] = useCloudAPI<BulkResponse<InteractiveSession>>(
        {noop: true},
        bulkResponseOf()
    );

    const doReconnect = useCallback(() => {
        openSession(JobsApi.openTerminalInFolder(
            bulkRequestOf({folder: props.tab.folder}))
        );
    }, [props.tab.folder]);

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
        <ShellWithSession sessionWithProvider={sessionWithProvider} xtermRef={terminal} autofit={false} reconnect={doReconnect} />
    </div>;
}
