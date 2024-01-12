import * as React from "react";
import {useTerminalDispatcher, useTerminalState} from "@/Terminal/State";
import {useCallback, useEffect, useRef, useMemo} from "react";
import {Icon} from "@/ui-components";
import {appendToXterm, useXTerm} from "@/Applications/Jobs/XTermLib";
import {Feature, hasFeature} from "@/Features";
import {injectStyle} from "@/Unstyled";

const Wrapper = injectStyle("wrapper", k => `
    ${k} {
        --tc-pad: 16px;
        width: calc(100vw - 190px);
        height: var(--termsize, 0px);
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        position: fixed;
        left: 190px;
        padding-left: var(--tc-pad);
        padding-right: var(--tc-pad);
        user-select: none;
        z-index: 9999999999;
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
    const {termRef, terminal, fitAddon} = useXTerm();

    const termSizeSaved = useRef<number>(400);

    const setSize = useCallback((size: number) => {
        if (size > 0) termSizeSaved.current = size;
        document.body.style.setProperty("--termsize", `${termSizeSaved.current}px`);
    }, []);

    useEffect(() => {
        setTimeout(() => {
            appendToXterm(terminal, "┌── Welcome ───────────────────────────────────────────────────────────────────┐\n");
            appendToXterm(terminal, "│                 Welcome to the DeiC Large Memory HPC system                  │\n");
            appendToXterm(terminal, "├── Information ───────────────────────────────────────────────────────────────┤\n");
            appendToXterm(terminal, "│ eScience Center: https://escience.sdu.dk                                     │\n");
            appendToXterm(terminal, "│ Service desk: https://servicedesk.cloud.sdu.dk                               │\n");
            appendToXterm(terminal, "│ Documentation: https://docs.hpc-type3.sdu.dk                                 │\n");
            appendToXterm(terminal, "│                                                                              │\n");
            appendToXterm(terminal, "├── Software ──────────────────────────────────────────────────────────────────┤\n");
            appendToXterm(terminal, "│ Use 'module spider' to see the installed software                            │\n");
            appendToXterm(terminal, "│ Use 'myquota' to see your available resources                                │\n");
            appendToXterm(terminal, "│                                                                              │\n");
            appendToXterm(terminal, "├── System Update ─────────────────────────────────────────────────────────────┤\n");
            appendToXterm(terminal, "│ Welcome back to the new and improved Hippo cluster!                          │\n");
            appendToXterm(terminal, "│                                                                              │\n");
            appendToXterm(terminal, "│ Please check the link below for an overview of changes.                      │\n");
            appendToXterm(terminal, "│ https://docs.hpc-type3.sdu.dk/help/updates.html                              │\n");
            appendToXterm(terminal, "│                                                                              │\n");
            appendToXterm(terminal, "│ If you experience any issues, please contact our service desk.               │\n");
            appendToXterm(terminal, "│                                                                              │\n");
            appendToXterm(terminal, "└──────────────────────────────────────────────────────────────────────────────┘\n");
            appendToXterm(terminal, "Last login: Wed Oct  5 09:03:35 2022 from 0.0.0.0\n");
            appendToXterm(terminal, "[dthrane@hippo-fe ~]$ ")
        }, 3000);
    }, []);

    useEffect(() => {
        if (state.open) {
            document.body.style.setProperty("--termsize", `${termSizeSaved.current}px`);
            fitAddon.fit();
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
        fitAddon.fit();
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

        <div className={"contents"} ref={termRef} />
    </div>;
};
