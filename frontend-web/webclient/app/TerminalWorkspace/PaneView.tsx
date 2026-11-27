import * as React from "react";
import {useEffect, useLayoutEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import Icon from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {TerminalEngineHandle, TerminalFrameColors, useTerminalEngine} from "@/TerminalEngine/engine";
import {TerminalSearchBar} from "@/TerminalEngine/SearchBar";
import {terminalThemes} from "@/TerminalEngine/themes";
import {TerminalSessionPane, TerminalSessionSplitDirection} from "@/UCloud/TerminalSessionsApi";

export interface PaneRuntimeInfo {
    warning: string;
    fallbackShell: boolean;
    error: string;
}

function isColorLight(color: string): boolean | null {
    const rgbMatch = color.match(/^rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)$/i);
    let r = 0;
    let g = 0;
    let b = 0;
    if (rgbMatch) {
        r = Number.parseInt(rgbMatch[1], 10);
        g = Number.parseInt(rgbMatch[2], 10);
        b = Number.parseInt(rgbMatch[3], 10);
    } else if (/^#[0-9a-f]{6}$/i.test(color)) {
        r = Number.parseInt(color.slice(1, 3), 16);
        g = Number.parseInt(color.slice(3, 5), 16);
        b = Number.parseInt(color.slice(5, 7), 16);
    } else if (/^#[0-9a-f]{3}$/i.test(color)) {
        r = Number.parseInt(color[1] + color[1], 16);
        g = Number.parseInt(color[2] + color[2], 16);
        b = Number.parseInt(color[3] + color[3], 16);
    } else {
        return null;
    }
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return luminance > 0.58;
}

export const TerminalPaneView = React.memo(function TerminalPaneView({
    pane,
    displayTitle,
    replicas,
    isFocused,
    isMaximized,
    isVisible,
    searchOpen,
    runtimeInfo,
    keyInterceptor,
    onFocus,
    onCloseSearch,
    onRegisterHandle,
    onMeasured,
    onData,
    onResize,
    onOscTitle,
    onBell,
    onRenamePane,
    onSplitPane,
    onToggleMaximize,
    onClosePane,
}: {
    pane: TerminalSessionPane;
    displayTitle?: string;
    replicas: number;
    isFocused: boolean;
    isMaximized: boolean;
    isVisible: boolean;
    searchOpen: boolean;
    runtimeInfo: PaneRuntimeInfo | undefined;
    keyInterceptor: (event: KeyboardEvent) => boolean;
    onFocus: (paneId: string) => void;
    onCloseSearch: () => void;
    onRegisterHandle: (paneId: string, handle: TerminalEngineHandle | null) => void;
    onMeasured?: (paneId: string) => void;
    onData: (paneId: string, data: string) => void;
    onResize: (paneId: string, cols: number, rows: number) => void;
    onOscTitle: (paneId: string, title: string) => void;
    onBell?: (paneId: string) => void;
    onRenamePane: (paneId: string, title: string) => void;
    onSplitPane: (paneId: string, direction: TerminalSessionSplitDirection, rank?: number) => void;
    onToggleMaximize: (paneId: string) => void;
    onClosePane: (paneId: string) => void;
}): React.ReactNode {
    const {termRef, handle, ready} = useTerminalEngine({
        autofit: true,
        visible: isVisible,
        cursorVisible: isFocused,
        cursorBlink: isFocused && isVisible,
        keyInterceptor,
    });

    const [editingTitle, setEditingTitle] = useState(false);
    const [titleDraft, setTitleDraft] = useState(pane.title);
    const [dismissedWarning, setDismissedWarning] = useState(false);
    const [frameColors, setFrameColors] = useState<TerminalFrameColors | null>(null);
    const [rankPickerDir, setRankPickerDir] = useState<TerminalSessionSplitDirection | null>(null);
    const controlsRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        if (!rankPickerDir) return;
        const onMouseDown = (event: MouseEvent) => {
            if (!controlsRef.current?.contains(event.target as Node)) {
                setRankPickerDir(null);
            }
        };
        window.addEventListener("mousedown", onMouseDown);
        return () => window.removeEventListener("mousedown", onMouseDown);
    }, [rankPickerDir]);

    useEffect(() => {
        onRegisterHandle(pane.id, handle);
        return () => onRegisterHandle(pane.id, null);
    }, [pane.id, handle, onRegisterHandle]);

    useEffect(() => {
        const unsubData = handle.onData(data => onData(pane.id, data));
        const unsubResize = handle.onResize(dims => onResize(pane.id, dims.cols, dims.rows));
        const unsubMeasured = handle.onMeasured(() => onMeasured?.(pane.id));
        const unsubTitle = handle.onTitleChange(title => onOscTitle(pane.id, title));
        const unsubBell = handle.onBell(() => onBell?.(pane.id));
        const unsubFrame = handle.onFrameColorsChange(colors => setFrameColors(colors));
        return () => {
            unsubData();
            unsubResize();
            unsubMeasured();
            unsubTitle();
            unsubBell();
            unsubFrame();
        };
    }, [handle, pane.id, onData, onResize, onMeasured, onOscTitle, onBell]);

    useEffect(() => {
        handle.setCursorVisible(isFocused);
        handle.setCursorBlink(isFocused && isVisible);
        if (!isFocused) {
            handle.blur();
        }
    }, [handle, isFocused, isVisible, ready]);

    useLayoutEffect(() => {
        handle.setVisible(isVisible);
    }, [isVisible, handle]);

    useLayoutEffect(() => {
        if (isVisible) {
            handle.fit();
        }
    }, [isMaximized, handle]);

    useEffect(() => {
        if (isVisible && isFocused) {
            handle.focus();
        }
    }, [isVisible, isFocused, handle]);

    const hasWarning = Boolean(runtimeInfo?.fallbackShell && runtimeInfo?.warning);
    const hasMultiNode = replicas > 1;

    const headerBg = frameColors?.top ?? null;
    const headerLight = headerBg ? isColorLight(headerBg) : null;
    const headerStyle: React.CSSProperties | undefined = headerBg
        ? {
            backgroundColor: headerBg,
            color: headerLight === null ? undefined : headerLight ? "#1f2328" : "#e6edf3",
        }
        : undefined;

    return <div
        className={PaneCardClass}
        data-focused={isFocused}
        onMouseDown={() => {
            if (!isFocused) onFocus(pane.id);
        }}
    >
        <div className="pane-header" style={headerStyle}>
            <div className="pane-header-left">
                <TooltipV2 tooltip={hasWarning ? runtimeInfo?.warning : "Terminal pane"} triggerClassName="pane-icon-trigger">
                    <span className={`pane-icon ${hasWarning ? "warning" : ""}`}>
                        <Icon name={hasWarning ? "heroExclamationTriangle" : "heroCommandLine"} size={14} />
                    </span>
                </TooltipV2>
                {editingTitle ? (
                    <form
                        className="title-edit-form"
                        onSubmit={e => {
                            e.preventDefault();
                            const trimmed = titleDraft.trim();
                            if (trimmed) onRenamePane(pane.id, trimmed);
                            setEditingTitle(false);
                            handle.focus();
                        }}
                    >
                        <input
                            type="text"
                            className="title-input"
                            value={titleDraft}
                            autoFocus
                            onChange={e => setTitleDraft(e.target.value)}
                            onBlur={() => {
                                const trimmed = titleDraft.trim();
                                if (trimmed && trimmed !== pane.title) {
                                    onRenamePane(pane.id, trimmed);
                                }
                                setEditingTitle(false);
                            }}
                            onKeyDown={e => {
                                if (e.key === "Escape") {
                                    e.stopPropagation();
                                    setEditingTitle(false);
                                    handle.focus();
                                }
                            }}
                        />
                    </form>
                ) : (
                    <TooltipV2 tooltip="Double-click to rename pane" triggerClassName="pane-title-trigger">
                        <span
                            className="pane-title"
                            onDoubleClick={() => {
                                setTitleDraft(displayTitle || pane.title);
                                setEditingTitle(true);
                            }}
                        >
                            {displayTitle || pane.title}
                        </span>
                    </TooltipV2>
                )}
                {hasMultiNode ? <span className="node-badge">Node {pane.rank + 1}</span> : null}
            </div>

            <div className="pane-controls" ref={controlsRef}>
                <TooltipV2 tooltip="Split right (Ctrl+Alt+D)" triggerClassName="pane-icon-trigger">
                    <button
                        type="button"
                        className="pane-btn"
                        onClick={e => {
                            e.stopPropagation();
                            if (hasMultiNode) {
                                setRankPickerDir(prev => prev === "horizontal" ? null : "horizontal");
                            } else {
                                onSplitPane(pane.id, "horizontal");
                            }
                        }}
                    >
                        <Icon name="heroViewColumns" size={13} />
                    </button>
                </TooltipV2>
                <TooltipV2 tooltip="Split down (Ctrl+Alt+Shift+D)" triggerClassName="pane-icon-trigger">
                    <button
                        type="button"
                        className="pane-btn"
                        onClick={e => {
                            e.stopPropagation();
                            if (hasMultiNode) {
                                setRankPickerDir(prev => prev === "vertical" ? null : "vertical");
                            } else {
                                onSplitPane(pane.id, "vertical");
                            }
                        }}
                    >
                        <span className="split-down-icon">
                            <Icon name="heroViewColumns" size={13} />
                        </span>
                    </button>
                </TooltipV2>
                <TooltipV2 tooltip={isMaximized ? "Restore split view (Ctrl+Alt+Enter)" : "Maximize pane (Ctrl+Alt+Enter)"} triggerClassName="pane-icon-trigger">
                    <button
                        type="button"
                        className="pane-btn"
                        onClick={e => {
                            e.stopPropagation();
                            onToggleMaximize(pane.id);
                        }}
                    >
                        <Icon name={isMaximized ? "heroArrowsPointingIn" : "heroArrowsPointingOut"} size={13} />
                    </button>
                </TooltipV2>
                <TooltipV2 tooltip="Close pane (Ctrl+Alt+W)" triggerClassName="pane-icon-trigger">
                    <button
                        type="button"
                        className="pane-btn close"
                        onClick={e => {
                            e.stopPropagation();
                            onClosePane(pane.id);
                        }}
                    >
                        <Icon name="close" size={11} />
                    </button>
                </TooltipV2>

                {rankPickerDir ? <div className="rank-popover" onClick={e => e.stopPropagation()}>
                    <div className="rank-popover-title">Split on node</div>
                    {Array.from({length: replicas}, (_, r) => <button
                        key={r}
                        type="button"
                        className="rank-option"
                        onClick={() => {
                            const dir = rankPickerDir;
                            setRankPickerDir(null);
                            onSplitPane(pane.id, dir, r);
                        }}
                    >
                        <Icon name="heroServer" size={12} />
                        <span>Node {r + 1}</span>
                    </button>)}
                </div> : null}
            </div>
        </div>

        {hasWarning && !dismissedWarning ? <div className="pane-banner warning">
            <Icon name="heroExclamationTriangle" size={14} />
            <span>{runtimeInfo?.warning}</span>
            <button type="button" className="banner-dismiss" onClick={() => setDismissedWarning(true)}>
                <Icon name="close" size={10} />
            </button>
        </div> : null}

        {runtimeInfo?.error ? <div className="pane-banner error">
            <Icon name="heroExclamationTriangle" size={14} />
            <span>{runtimeInfo.error}</span>
        </div> : null}

        <div className="pane-body">
            {frameColors && frameColors.canvasHeight > 0 ? (
                <>
                    <div
                        className="pane-edge pane-edge-bottom"
                        style={{
                            top: `${Math.max(0, Math.floor(frameColors.canvasHeight) - 1)}px`,
                            background: frameColors.bottom,
                        }}
                    />
                    <div
                        className="pane-edge pane-edge-left"
                        style={{
                            width: `${8 + frameColors.canvasLeft + 2}px`,
                            height: `${Math.ceil(frameColors.canvasHeight)}px`,
                            background: frameColors.leftBackground,
                        }}
                    />
                    <div
                        className="pane-edge pane-edge-right"
                        style={{
                            left: `${Math.max(0, 8 + frameColors.canvasLeft + frameColors.canvasWidth - 2)}px`,
                            height: `${Math.ceil(frameColors.canvasHeight)}px`,
                            background: frameColors.rightBackground,
                        }}
                    />
                </>
            ) : null}
            <TerminalSearchBar
                terminal={handle}
                open={searchOpen}
                onClose={onCloseSearch}
            />
            <div ref={termRef} className="pane-terminal" />
        </div>
    </div>;
});

const PaneCardClass = injectStyle("terminal-pane-card", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100%;
        min-width: 0;
        min-height: 0;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: ${terminalThemes.light.background};
        overflow: hidden;
        box-sizing: border-box;
        transition: border-color 120ms ease, opacity 140ms ease;
    }

    html.dark ${k} {
        background: ${terminalThemes.dark.background};
    }

    ${k}[data-focused="false"] {
        opacity: 0.50;
    }

    ${k}[data-focused="false"]:hover {
        opacity: 0.7;
    }

    ${k}[data-focused="true"] {
        opacity: 1;
    }

    ${k} .pane-header {
        height: 28px;
        flex: none;
        padding: 0 8px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        background: ${terminalThemes.light.background};
        font-family: var(--sansSerif);
        font-size: 12px;
        color: var(--textPrimary);
        user-select: none;
        transition: background-color 120ms ease, color 120ms ease;
    }

    html.dark ${k} .pane-header {
        background: ${terminalThemes.dark.background};
    }

    ${k} .pane-header-left {
        min-width: 0;
        flex: 1;
        display: flex;
        align-items: center;
        gap: 6px;
    }

    ${k} .pane-icon-trigger {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex: none;
    }

    ${k} .pane-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        line-height: 0;
        color: inherit;
        opacity: 0.78;
        flex: none;
    }

    ${k} .pane-icon.warning {
        color: #d29922;
    }

    ${k} .pane-title-trigger {
        min-width: 0;
        overflow: hidden;
        display: flex;
    }

    ${k} .pane-title {
        display: block;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        font-weight: 500;
        cursor: text;
    }

    ${k} .title-edit-form {
        flex: 1;
        min-width: 0;
    }

    ${k} .title-input {
        width: 100%;
        padding: 1px 5px;
        border-radius: 4px;
        border: 1px solid var(--primaryMain);
        background: var(--background);
        color: var(--textPrimary);
        font: inherit;
        outline: none;
    }

    ${k} .node-badge {
        flex: none;
        padding: 1px 6px;
        border-radius: 999px;
        border: 1px solid var(--borderColor);
        font-size: 10.5px;
        color: var(--textSecondary);
    }

    ${k} .pane-controls {
        position: relative;
        display: flex;
        align-items: center;
        gap: 2px;
        flex: none;
    }

    ${k} .pane-btn {
        width: 22px;
        height: 22px;
        padding: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: inherit;
        opacity: 0.72;
        cursor: pointer;
    }

    ${k} .pane-btn:hover {
        background: var(--rowHover);
        opacity: 1;
    }

    ${k} .pane-btn.close:hover {
        background: rgba(229, 83, 75, 0.16);
        color: #e5534b;
    }

    ${k} .split-down-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        transform: rotate(90deg);
    }

    ${k} .rank-popover {
        position: absolute;
        top: calc(100% + 4px);
        right: 0;
        z-index: 30;
        min-width: 150px;
        padding: 4px;
        border-radius: 6px;
        background: var(--backgroundCard);
        border: 1px solid var(--borderColor);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
        display: flex;
        flex-direction: column;
        gap: 2px;
    }

    ${k} .rank-popover-title {
        padding: 4px 6px;
        font-size: 11px;
        color: var(--textSecondary);
    }

    ${k} .rank-option {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 5px 8px;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        font: inherit;
        text-align: left;
    }

    ${k} .rank-option:hover {
        background: var(--rowHover);
    }

    ${k} .pane-banner {
        flex: none;
        padding: 5px 10px;
        display: flex;
        align-items: center;
        gap: 6px;
        font-family: var(--sansSerif);
        font-size: 12px;
    }

    ${k} .pane-banner.warning {
        background: rgba(210, 153, 34, 0.14);
        border-bottom: 1px solid rgba(210, 153, 34, 0.35);
        color: var(--textPrimary);
    }

    ${k} .pane-banner.error {
        background: rgba(229, 83, 75, 0.14);
        border-bottom: 1px solid rgba(229, 83, 75, 0.35);
        color: var(--textPrimary);
    }

    ${k} .pane-banner span {
        flex: 1;
    }

    ${k} .banner-dismiss {
        border: 0;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        padding: 2px;
        display: inline-flex;
    }

    ${k} .pane-body {
        position: relative;
        flex: 1;
        min-height: 0;
        min-width: 0;
        padding: 0 8px 6px;
        box-sizing: border-box;
        overflow: hidden;
    }

    ${k} .pane-edge {
        position: absolute;
        pointer-events: none;
        z-index: 0;
    }

    ${k} .pane-edge-left {
        top: 0;
        left: 0;
    }

    ${k} .pane-edge-right {
        top: 0;
        right: 0;
    }

    ${k} .pane-edge-bottom {
        left: 0;
        right: 0;
        bottom: 0;
    }

    ${k} .pane-terminal {
        position: relative;
        z-index: 1;
        width: 100%;
        height: 100%;
        min-width: 0;
        min-height: 0;
        display: flex;
        justify-content: center;
        align-items: flex-start;
        caret-color: transparent;
        outline: none;
    }

    ${k} .pane-terminal * {
        caret-color: transparent;
    }
`);
