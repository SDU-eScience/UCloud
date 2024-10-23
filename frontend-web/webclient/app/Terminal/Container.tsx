import * as React from "react";
import {TerminalTab, useTerminalDispatcher, useTerminalState} from "@/Terminal/State";
import {useCallback, useEffect, useRef, useMemo, useState} from "react";
import {Icon} from "@/ui-components";
import {Feature, hasFeature} from "@/Features";
import {injectStyle} from "@/Unstyled";
import {useCloudAPI} from "@/Authentication/DataHook";
import {BulkResponse} from "@/UCloud";
import JobsApi, {InteractiveSession} from "@/UCloud/JobsApi";
import {bulkRequestOf, bulkResponseOf} from "@/UtilityFunctions";
import {ShellWithSession} from "@/Applications/Jobs/Shell";
import {Terminal} from "xterm";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";

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
        height: 32px;
        display: flex;
        align-items: center;
    }

    ${k} .controls, ${k} .control {
        margin-left: 16px;
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

    const closeTerminal = useCallback(() => {
        if (state.activeTab >= 0) {
            dispatch({type: "TerminalCloseTab", tabIdx: state.activeTab});
        }
    }, [state.activeTab]);

    const tabComponents = useMemo(() => (
        <>
            {state.tabs.map((tab, idx) => (
                <div
                    className={`tab ${state.open && idx === state.activeTab ? "active" : ""}`}
                    key={idx}
                    onClick={() => dispatch({type: "TerminalSelectTab", tabIdx: idx})}
                >
                    {tab.title}
                </div>
            ))}
        </>
    ), [state.tabs, state.activeTab, state.open]);

    return <div className={Wrapper}>
        <div className={"resizer"} onMouseDown={onDragStart} />
        <div className="controls">
            {tabComponents}

            <div style={{flexGrow: 1}} />

            {state.open && state.activeTab >= 0 ?
                <div className="control" title={"Close terminal"} onClick={closeTerminal}>
                    <Icon name={"trash"} size={16} />
                </div> :
                null
            }
            <div className="control" onClick={toggle}>
                <Icon name={state.open ? "anglesDownSolid" : "anglesUpSolid"} size={16} />
            </div>
        </div>

        {state.tabs.map((tab, idx) =>
            <IndividualTerminal key={tab.uniqueId ?? idx.toString()} tab={tab} hidden={state.activeTab !== idx} />
        )}
    </div>;
};

const IndividualTerminal: React.FunctionComponent<{ tab: TerminalTab, hidden: boolean }> = props => {
    const [size, setSize] = useState<[number, number]>([80, 40]);
    const terminal = useRef<Terminal | null>(null);
    const [sessionResp, openSession] = useCloudAPI<BulkResponse<InteractiveSession>>(
        {noop: true},
        bulkResponseOf()
    );

    useEffect(() => {
        openSession(JobsApi.openTerminalInFolder(
            bulkRequestOf({folder: props.tab.folder}))
        );
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
        <ShellWithSession sessionWithProvider={sessionWithProvider} xtermRef={terminal} autofit={false} />;
    </div>;
}
