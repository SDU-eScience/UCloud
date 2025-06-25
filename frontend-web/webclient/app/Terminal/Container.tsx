import * as React from "react";
import {TerminalAction, TerminalState, TerminalTab, useTerminalDispatcher, useTerminalState} from "@/Terminal/State";
import {useCallback, useEffect, useRef, useMemo, useState} from "react";
import {Icon, Truncate} from "@/ui-components";
import {Feature, hasFeature} from "@/Features";
import {injectStyle} from "@/Unstyled";
import {noopCall, useCloudAPI} from "@/Authentication/DataHook";
import {BulkResponse} from "@/UCloud";
import JobsApi, {InteractiveSession} from "@/UCloud/JobsApi";
import {bulkRequestOf, bulkResponseOf} from "@/UtilityFunctions";
import {ShellWithSession} from "@/Applications/Jobs/Shell";
import {Terminal} from "xterm";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";
import {Tab} from "@/Editor/Editor";
import {Operation, Operations, ShortcutKey} from "@/ui-components/Operation";

const Wrapper = injectStyle("wrapper", k => `
    ${k} {
        --tc-pad: 16px;
        width: calc(100vw - var(--currentSidebarStickyWidth));
        height: var(--termsize, 0px);
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        position: fixed;
        bottom: 0;
        left: var(--currentSidebarStickyWidth);
        padding-left: var(--tc-pad);
        padding-right: var(--tc-pad);
        user-select: none;
        -webkit-user-select: none;
        font-family: 'Jetbrains Mono', 'Ubuntu Mono', courier-new, courier, monospace;
    }

    ${k} .resizer {
        width: calc(100% + var(--tc-pad) * 2);
        height: 2px;
        background: black;
        cursor: row-resize;
        position: relative;
        left: calc(var(--tc-pad) * -1);
    }

    ${k} .controls {
        width: 100%;
        margin-left: calc(-1 * var(--tc-pad));

        height: 32px;
        display: flex;
        align-items: center;
    }

    ${k} .control {
        cursor: pointer;
    }

    ${k} .tab:first-child {
        border-left: 1px solid black;
    }

    ${k} .tab {
        width: 150px;
        text-overflow: ellipsis;
        text-align: center;
        border-right: 1px solid black;
        cursor: pointer;
    }

    ${k} .tab:hover {
        background: var(--textPrimary);
        color: var(--backgroundDefault);
    }

    ${k} .tab.active {
        background: var(--textPrimary);
        color: var(--backgroundDefault);
    }

    ${k} .contents {
        width: 100%;
        height: calc(100% - 32px);
    }
`);

export const TerminalContainer: React.FunctionComponent = () => {
    if (!hasFeature(Feature.INLINE_TERMINAL)) return null;

    const state = useTerminalState();
    const dispatch = useTerminalDispatcher();

    const termSizeSaved = useRef<number>(400);

    const setSize = useCallback((size: number) => {
        if (size > 0) termSizeSaved.current = size;
        document.body.style.setProperty("--termsize", `${termSizeSaved.current}px`);
    }, []);

    useEffect(() => {
        if (state.open) {
            document.body.style.setProperty("--termsize", `${termSizeSaved.current}px`);
        } else {
            if (state.tabs.length === 0) {
                document.body.style.setProperty("--termsize", "0px");
            } else {
                document.body.style.setProperty("--termsize", "32px");
            }
        }
    }, [state.open, state.tabs.length]);

    const mouseMoveHandler: (e: MouseEvent) => void = useCallback(e => {
        const size = window.innerHeight - e.clientY;
        setSize(size);
    }, []);

    const mouseUpHandler: (e: MouseEvent) => void = useCallback(() => {
        document.body.removeEventListener("mousemove", mouseMoveHandler);
        document.body.removeEventListener("mouseup", mouseUpHandler);
    }, []);

    const onDragStart = useCallback(() => {
        document.body.addEventListener("mousemove", mouseMoveHandler);
        document.body.addEventListener("mouseup", mouseUpHandler);
    }, []);

    const toggle = useCallback(() => {
        if (state.open) {
            dispatch({type: "TerminalClose"});
        } else {
            dispatch({type: "TerminalOpen"});
        }
    }, [state.open]);

    const closeTerminal = useCallback((idx: number) => {
        if (state.activeTab >= 0) {
            dispatch({type: "TerminalCloseTab", payload: {tabIdx: idx}});
        }
    }, [state.activeTab]);

    const [operations, setOperations] = useState<Operation<any, undefined>[]>([]);
    const openTabOperations = React.useCallback((idx: number, position: {x: number; y: number;}) => {
        const ops = tabOperations(dispatch, idx, state);
        setOperations(ops);
        openTabOperationWindow.current(position.x, position.y);
    }, [state]);

    const tabComponents = useMemo(() => state.tabs.map((tab, idx) => (
        <Tab
            key={idx}
            title={<Truncate>{tab.title}</Truncate>}
            onRowClick={() => dispatch({type: "TerminalSelectTab", payload: {tabIdx: idx}})}
            isActive={idx === state.activeTab}
            icon={<div />}
            onClose={() => closeTerminal(idx)}
            cursor="close"
            onContextMenu={e => {
                e.preventDefault();
                e.stopPropagation();
                openTabOperations(idx, {x: e.clientX, y: e.clientY});
            }}
        />
    )), [state.tabs, state.activeTab, closeTerminal]);

    const openTabOperationWindow = useRef<(x: number, y: number) => void>(noopCall)

    return <div className={Wrapper}>
        <div className={"resizer"} onMouseDown={onDragStart} />
        <div className="controls">
            {tabComponents}

            <Operations
                entityNameSingular={""}
                operations={operations}
                forceEvaluationOnOpen={true}
                openFnRef={openTabOperationWindow}
                selected={[]}
                extra={null}
                row={42}
                hidden
                location={"IN_ROW"}
            />

            <div style={{flexGrow: 1}} />

            <div className="control" onClick={toggle}>
                <Icon name={state.open ? "anglesDownSolid" : "anglesUpSolid"} size={16} />
            </div>
        </div>

        {state.tabs.map((tab, idx) =>
            <IndividualTerminal key={tab.uniqueId ?? idx.toString()} tab={tab} hidden={state.activeTab !== idx} />
        )}
    </div>;
};

function tabOperations(dispatch: (action: TerminalAction) => void, tabIdx: number, state: TerminalState): Operation<any>[] {
    return [
        {
            text: "Close tab",
            enabled: () => true,
            onClick() {
                dispatch({type: "TerminalCloseTab", payload: {tabIdx: tabIdx}});
            },
            "shortcut": ShortcutKey.A,
        },
        {
            text: "Close others", enabled: () => state.tabs.length > 1,
            onClick() {
                for (let idx = state.tabs.length - 1; idx >= 0; idx--) {
                    if (idx === tabIdx) continue;
                    dispatch({type: "TerminalCloseTab", payload: {tabIdx: idx}});
                }
            },
            "shortcut": ShortcutKey.B,
        },
        {
            text: "Close to the right", enabled: () => true /* todo */, onClick() {
                for (let i = state.tabs.length - 1; i > tabIdx; i--) {
                    dispatch({type: "TerminalCloseTab", payload: {tabIdx: i}});
                }

                if (tabIdx < state.activeTab) {
                    dispatch({type: "TerminalSelectTab", payload: {tabIdx: tabIdx}})
                }
            },
            "shortcut": ShortcutKey.C,
        },
        {
            text: "Close all", enabled: () => true, onClick() {
                for (let i = state.tabs.length - 1; i >= 0; i--) {
                    dispatch({type: "TerminalCloseTab", payload: {tabIdx: i}});
                }
                dispatch({type: "TerminalClose"});
            },
            "shortcut": ShortcutKey.D,
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
            termHeight -= 64;

            const cols = Math.floor(width / 10);
            const rows = Math.floor(termHeight / 20);

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
        <ShellWithSession sessionWithProvider={sessionWithProvider} xtermRef={terminal} autofit={false} reconnect={doReconnect} />;
    </div>;
}
