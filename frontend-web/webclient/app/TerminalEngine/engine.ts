import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {Feature, hasFeature} from "@/Features";
import {useBackendFeatures} from "@/Features/backend";
import {xtermThemes} from "@/Applications/Jobs/XTermLib";
import {FitAddon as XtermFitAddon} from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";
import {Terminal as XtermTerminal} from "@xterm/xterm";
import type {GhosttyCell, Terminal as GhosttyTerminalType} from "ghostty-web";
import {
    getActiveTerminalTheme,
    isTerminalLightTheme,
    subscribeTerminalThemeChange,
    TerminalPalette,
} from "@/TerminalEngine/themes";

export const TERMINAL_DEFAULT_FONT_SIZE = 15;
export const TERMINAL_MIN_FONT_SIZE = 10;
export const TERMINAL_MAX_FONT_SIZE = 28;
const TERMINAL_FONT_FAMILY = `"Jetbrains Mono", "JetBrains Mono", "Ubuntu Mono", courier-new, courier, monospace`;

export interface TerminalSearchMatch {
    lineIndex: number;
    col: number;
    length: number;
}

export interface TerminalFrameColors {
    top: string;
    bottom: string;
    leftBackground: string;
    rightBackground: string;
    canvasLeft: number;
    canvasWidth: number;
    canvasHeight: number;
}

export interface TerminalEngineHandle {
    readonly cols: number;
    readonly rows: number;
    readonly measured: boolean;
    readonly engine: "ghostty" | "xterm";
    write(data: string | Uint8Array): void;
    writeReplay(data: string | Uint8Array): void;
    appendText(text: string): void;
    reset(): void;
    clear(): void;
    focus(): void;
    blur(): void;
    resize(cols: number, rows: number): void;
    fit(): void;
    paste(text: string): void;
    getSelection(): string;
    hasSelection(): boolean;
    setVisible(visible: boolean): void;
    setCursorVisible(visible: boolean): void;
    setCursorBlink(enabled: boolean): void;
    setFontSize(size: number): void;
    getFontSize(): number;
    search(query: string, caseSensitive: boolean): TerminalSearchMatch[];
    jumpToMatch(match: TerminalSearchMatch): void;
    clearSelection(): void;
    onData(listener: (data: string) => void): () => void;
    onResize(listener: (dims: {cols: number; rows: number}) => void): () => void;
    onMeasured(listener: () => void): () => void;
    onTitleChange(listener: (title: string) => void): () => void;
    onBell(listener: () => void): () => void;
    onOsc777(listener: (data: string) => void): () => void;
    onFrameColorsChange(listener: (colors: TerminalFrameColors) => void): () => void;
}

export interface UseTerminalEngineOptions {
    autofit?: boolean;
    readOnly?: boolean;
    visible?: boolean;
    cursorVisible?: boolean;
    cursorBlink?: boolean;
    fontSize?: number;
    keyInterceptor?: (event: KeyboardEvent) => boolean;
}

export interface UseTerminalEngineResult {
    termRef: React.RefObject<HTMLDivElement | null>;
    handle: TerminalEngineHandle;
    ready: boolean;
    engine: "ghostty" | "xterm";
}

type GhosttyModule = typeof import("ghostty-web");

let ghosttyModulePromise: Promise<GhosttyModule> | null = null;

export function loadGhosttyModule(): Promise<GhosttyModule> {
    if (ghosttyModulePromise === null) {
        ghosttyModulePromise = import("ghostty-web");
    }
    return ghosttyModulePromise;
}

function scanOsc777(
    data: string | Uint8Array,
    carryRef: {current: string},
    listeners: Set<(payload: string) => void>,
): void {
    if (listeners.size === 0) return;
    const text = typeof data === "string" ? data : new TextDecoder().decode(data);
    const combined = carryRef.current + text;
    const prefix = "\x1b]777;";
    let searchFrom = 0;

    while (true) {
        const start = combined.indexOf(prefix, searchFrom);
        if (start === -1) {
            carryRef.current = combined.slice(Math.max(0, combined.length - prefix.length));
            return;
        }
        const payloadStart = start + prefix.length;
        const belEnd = combined.indexOf("\x07", payloadStart);
        const stEnd = combined.indexOf("\x1b\\", payloadStart);
        let end = -1;
        let advance = 1;
        if (belEnd !== -1 && (stEnd === -1 || belEnd < stEnd)) {
            end = belEnd;
            advance = 1;
        } else if (stEnd !== -1) {
            end = stEnd;
            advance = 2;
        }
        if (end === -1) {
            carryRef.current = combined.slice(start);
            return;
        }
        const payload = combined.slice(payloadStart, end);
        for (const listener of listeners) {
            listener(payload);
        }
        searchFrom = end + advance;
    }
}

function cellsToLineString(cells: GhosttyCell[] | null): string {
    if (!cells) return "";
    let out = "";
    for (let i = 0; i < cells.length; i++) {
        const cp = cells[i].codepoint;
        out += cp === 0 ? " " : String.fromCodePoint(cp);
    }
    return out;
}

function searchGhosttyBuffer(
    term: GhosttyTerminalType,
    query: string,
    caseSensitive: boolean,
): TerminalSearchMatch[] {
    if (!query) return [];
    const needle = caseSensitive ? query : query.toLowerCase();
    const matches: TerminalSearchMatch[] = [];
    const scrollbackLen = term.getScrollbackLength();
    const totalLines = scrollbackLen + term.rows;

    for (let lineIndex = 0; lineIndex < totalLines; lineIndex++) {
        let lineText = "";
        if (lineIndex < scrollbackLen) {
            lineText = cellsToLineString(term.getScrollbackLine(lineIndex));
        } else {
            const bufferLine = term.buffer.active.getLine(lineIndex);
            lineText = bufferLine ? bufferLine.translateToString(false) : "";
        }
        if (lineText.length === 0) continue;

        const haystack = caseSensitive ? lineText : lineText.toLowerCase();
        let pos = 0;
        while (pos <= haystack.length - needle.length) {
            const found = haystack.indexOf(needle, pos);
            if (found === -1) break;
            matches.push({
                lineIndex,
                col: found,
                length: query.length,
            });
            pos = found + Math.max(1, needle.length);
            if (matches.length >= 2000) return matches;
        }
    }

    return matches;
}

function searchXtermBuffer(
    term: XtermTerminal,
    query: string,
    caseSensitive: boolean,
): TerminalSearchMatch[] {
    if (!query) return [];
    const needle = caseSensitive ? query : query.toLowerCase();
    const matches: TerminalSearchMatch[] = [];
    const active = term.buffer.active;
    const totalLines = active.length;

    for (let lineIndex = 0; lineIndex < totalLines; lineIndex++) {
        const line = active.getLine(lineIndex);
        if (!line) continue;
        const lineText = line.translateToString(false);
        const haystack = caseSensitive ? lineText : lineText.toLowerCase();
        let pos = 0;
        while (pos <= haystack.length - needle.length) {
            const found = haystack.indexOf(needle, pos);
            if (found === -1) break;
            matches.push({
                lineIndex,
                col: found,
                length: query.length,
            });
            pos = found + Math.max(1, needle.length);
            if (matches.length >= 2000) return matches;
        }
    }

    return matches;
}

function isCopyShortcut(event: KeyboardEvent): boolean {
    if (event.type !== "keydown") return false;
    if (event.getModifierState("AltGraph")) return false;
    const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/i.test(navigator.platform);
    if (isMac) {
        return event.metaKey && !event.ctrlKey && !event.altKey && event.code === "KeyC";
    }
    return event.ctrlKey && event.shiftKey && !event.altKey && !event.metaKey && event.code === "KeyC";
}

function isPasteShortcut(event: KeyboardEvent): boolean {
    if (event.type !== "keydown") return false;
    if (event.getModifierState("AltGraph")) return false;
    const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/i.test(navigator.platform);
    if (isMac) {
        return event.metaKey && !event.ctrlKey && !event.altKey && event.code === "KeyV";
    }
    return event.ctrlKey && event.shiftKey && !event.altKey && !event.metaKey && event.code === "KeyV";
}

function isReadOnlyAllowedKey(event: KeyboardEvent): boolean {
    if (isCopyShortcut(event)) return true;
    if (event.ctrlKey || event.metaKey || event.altKey) return false;
    return event.shiftKey && (event.key === "PageUp" || event.key === "PageDown");
}

function clampFontSize(size: number): number {
    return Math.min(TERMINAL_MAX_FONT_SIZE, Math.max(TERMINAL_MIN_FONT_SIZE, size));
}

type RgbTuple = [number, number, number];
type GhosttyColorRemap = Map<number, RgbTuple>;

function parseHexRgb(hex: string): RgbTuple {
    const clean = hex.startsWith("#") ? hex.slice(1) : hex;
    const r = Number.parseInt(clean.slice(0, 2), 16) || 0;
    const g = Number.parseInt(clean.slice(2, 4), 16) || 0;
    const b = Number.parseInt(clean.slice(4, 6), 16) || 0;
    return [r, g, b];
}

function rgbKey(r: number, g: number, b: number): number {
    return (r << 16) | (g << 8) | b;
}

function paletteColorList(palette: TerminalPalette): string[] {
    return [
        palette.background,
        palette.foreground,
        palette.black,
        palette.red,
        palette.green,
        palette.yellow,
        palette.blue,
        palette.magenta,
        palette.cyan,
        palette.white,
        palette.brightBlack,
        palette.brightRed,
        palette.brightGreen,
        palette.brightYellow,
        palette.brightBlue,
        palette.brightMagenta,
        palette.brightCyan,
        palette.brightWhite,
    ];
}

function buildGhosttyColorRemap(from: TerminalPalette, to: TerminalPalette): GhosttyColorRemap | null {
    if (from === to) return null;
    const src = paletteColorList(from);
    const dst = paletteColorList(to);
    const map: GhosttyColorRemap = new Map();
    for (let i = 0; i < src.length; i++) {
        const [sr, sg, sb] = parseHexRgb(src[i]);
        const target = parseHexRgb(dst[i]);
        map.set(rgbKey(sr, sg, sb), target);
    }
    return map;
}

function installGhosttyRendererPatches(
    term: GhosttyTerminalType,
    getColorRemap: () => GhosttyColorRemap | null,
    getVisible: () => boolean,
): void {
    const renderer = term.renderer as unknown as {
        ctx?: CanvasRenderingContext2D;
        canvas?: HTMLCanvasElement;
        devicePixelRatio?: number;
        metrics?: {width: number; height: number; baseline: number};
        fontSize?: number;
        fontFamily?: string;
        theme?: {
            background: string;
            selectionBackground: string;
            selectionForeground: string;
            cursor: string;
        };
        currentBuffer?: {getGraphemeString?(row: number, col: number): string} | null;
        currentSelectionCoords?: {startRow: number; startCol: number; endRow: number; endCol: number} | null;
        isInSelection?: (col: number, row: number) => boolean;
        renderLine?: (cells: GhosttyCell[], row: number, cols: number) => void;
        renderScrollbar?: (viewportY: number, scrollbackLength: number, rows: number, opacity?: number) => void;
        render?: (
            buffer: unknown,
            forceAll?: boolean,
            viewportY?: number,
            scrollbackProvider?: unknown,
            scrollbarOpacity?: number,
        ) => void;
        resize?: (cols: number, rows: number) => void;
        cursorVisible?: boolean;
        hoveredHyperlinkId?: number;
        previousHoveredHyperlinkId?: number;
        hoveredLinkRange?: {startX: number; startY: number; endX: number; endY: number} | null;
        previousHoveredLinkRange?: unknown;
        selectionManager?: {
            hasSelection(): boolean;
            getDirtySelectionRows(): Set<number>;
        };
    } | undefined;
    if (!renderer) return;

    if (typeof renderer.renderLine === "function" && renderer.ctx && renderer.metrics && renderer.theme) {
        renderer.renderLine = (cells: GhosttyCell[], row: number, cols: number) => {
            const ctx = renderer.ctx!;
            const metrics = renderer.metrics!;
            const theme = renderer.theme!;
            const cw = metrics.width;
            const ch = metrics.height;
            const baseline = metrics.baseline;
            const yPx = row * ch;
            const remap = getColorRemap();
            const hasSel = renderer.currentSelectionCoords !== null && renderer.currentSelectionCoords !== undefined;
            const defaultBg = theme.background;

            ctx.fillStyle = defaultBg;
            ctx.fillRect(0, yPx, cols * cw, ch);

            let runColor = "";
            let runStartCol = -1;
            let runWidthCols = 0;

            const flushBgRun = () => {
                if (runStartCol === -1) return;
                ctx.fillStyle = runColor;
                ctx.fillRect(runStartCol * cw, yPx, runWidthCols * cw, ch);
                runStartCol = -1;
                runWidthCols = 0;
            };

            for (let col = 0; col < cells.length; col++) {
                const cell = cells[col];
                if (cell.width === 0) continue;

                let bgCss = "";
                if (hasSel && renderer.isInSelection?.(col, row)) {
                    bgCss = theme.selectionBackground;
                } else {
                    const inv = (cell.flags & 16) !== 0;
                    let r = inv ? cell.fg_r : cell.bg_r;
                    let g = inv ? cell.fg_g : cell.bg_g;
                    let b = inv ? cell.fg_b : cell.bg_b;
                    if (remap) {
                        const mapped = remap.get(rgbKey(r, g, b));
                        if (mapped) {
                            r = mapped[0];
                            g = mapped[1];
                            b = mapped[2];
                        }
                    }
                    if (r !== 0 || g !== 0 || b !== 0) {
                        const css = `rgb(${r},${g},${b})`;
                        if (css !== defaultBg) {
                            bgCss = css;
                        }
                    }
                }

                if (bgCss) {
                    if (runStartCol !== -1 && bgCss === runColor && runStartCol + runWidthCols === col) {
                        runWidthCols += cell.width;
                    } else {
                        flushBgRun();
                        runColor = bgCss;
                        runStartCol = col;
                        runWidthCols = cell.width;
                    }
                } else {
                    flushBgRun();
                }
            }
            flushBgRun();

            const normalFont = `${renderer.fontSize ?? 15}px ${renderer.fontFamily ?? "monospace"}`;
            let currentFont = "";
            let currentFill = "";
            let currentAlpha = 1;
            const hoveredId = renderer.hoveredHyperlinkId || 0;
            const hoveredRange = renderer.hoveredLinkRange;
            const buf = renderer.currentBuffer;

            for (let col = 0; col < cells.length; col++) {
                const cell = cells[col];
                if (cell.width === 0) continue;
                const flags = cell.flags;
                if ((flags & 32) !== 0) continue;

                const cp = cell.codepoint;
                const hasDecor = (flags & 12) !== 0;
                const hasHyperlink = hoveredId > 0 && cell.hyperlink_id === hoveredId;
                const inHoverRange = Boolean(
                    hoveredRange && (
                        (row === hoveredRange.startY && col >= hoveredRange.startX && (row < hoveredRange.endY || col <= hoveredRange.endX)) ||
                        (row > hoveredRange.startY && row < hoveredRange.endY) ||
                        (row === hoveredRange.endY && col <= hoveredRange.endX && (row > hoveredRange.startY || col >= hoveredRange.startX))
                    ),
                );
                const isBlank = cell.grapheme_len === 0 && (cp === 0 || cp === 32);
                if (isBlank && !hasDecor && !hasHyperlink && !inHoverRange) continue;

                const xPx = col * cw;
                const cellW = cw * cell.width;

                let fillCss: string;
                if (hasSel && renderer.isInSelection?.(col, row)) {
                    fillCss = theme.selectionForeground;
                } else {
                    const inv = (flags & 16) !== 0;
                    let r = inv ? cell.bg_r : cell.fg_r;
                    let g = inv ? cell.bg_g : cell.fg_g;
                    let b = inv ? cell.bg_b : cell.fg_b;
                    if (remap) {
                        const mapped = remap.get(rgbKey(r, g, b));
                        if (mapped) {
                            r = mapped[0];
                            g = mapped[1];
                            b = mapped[2];
                        }
                    }
                    fillCss = `rgb(${r},${g},${b})`;
                }

                if (!isBlank) {
                    const styleKey = flags & 3;
                    const font = styleKey === 0
                        ? normalFont
                        : styleKey === 1
                            ? `bold ${normalFont}`
                            : styleKey === 2
                                ? `italic ${normalFont}`
                                : `italic bold ${normalFont}`;
                    if (font !== currentFont) {
                        ctx.font = font;
                        currentFont = font;
                    }
                    if (fillCss !== currentFill) {
                        ctx.fillStyle = fillCss;
                        currentFill = fillCss;
                    }
                    const alpha = (flags & 128) !== 0 ? 0.5 : 1;
                    if (alpha !== currentAlpha) {
                        ctx.globalAlpha = alpha;
                        currentAlpha = alpha;
                    }
                    const chStr = cell.grapheme_len > 0 && buf && typeof buf.getGraphemeString === "function"
                        ? buf.getGraphemeString(row, col)
                        : String.fromCodePoint(cp);
                    ctx.fillText(chStr, xPx, yPx + baseline);
                }

                if ((flags & 4) !== 0) {
                    if (currentAlpha !== 1) {
                        ctx.globalAlpha = 1;
                        currentAlpha = 1;
                    }
                    ctx.strokeStyle = fillCss;
                    ctx.lineWidth = 1;
                    ctx.beginPath();
                    ctx.moveTo(xPx, yPx + baseline + 2);
                    ctx.lineTo(xPx + cellW, yPx + baseline + 2);
                    ctx.stroke();
                }
                if ((flags & 8) !== 0) {
                    if (currentAlpha !== 1) {
                        ctx.globalAlpha = 1;
                        currentAlpha = 1;
                    }
                    ctx.strokeStyle = fillCss;
                    ctx.lineWidth = 1;
                    ctx.beginPath();
                    ctx.moveTo(xPx, yPx + ch / 2);
                    ctx.lineTo(xPx + cellW, yPx + ch / 2);
                    ctx.stroke();
                }
                if (hasHyperlink || inHoverRange) {
                    if (currentAlpha !== 1) {
                        ctx.globalAlpha = 1;
                        currentAlpha = 1;
                    }
                    ctx.strokeStyle = "#4A90E2";
                    ctx.lineWidth = 1;
                    ctx.beginPath();
                    ctx.moveTo(xPx, yPx + baseline + 2);
                    ctx.lineTo(xPx + cellW, yPx + baseline + 2);
                    ctx.stroke();
                }
            }
            if (currentAlpha !== 1) {
                ctx.globalAlpha = 1;
            }
        };
    }

    if (typeof renderer.renderScrollbar === "function" && renderer.ctx && renderer.canvas) {
        renderer.renderScrollbar = (viewportY: number, scrollbackLength: number, rows: number, opacity = 1) => {
            if (opacity <= 0 || scrollbackLength <= 0) return;
            const ctx = renderer.ctx!;
            const canvas = renderer.canvas!;
            const dpr = renderer.devicePixelRatio || window.devicePixelRatio || 1;
            const canvasHeight = canvas.height / dpr;
            const canvasWidth = canvas.width / dpr;
            const barWidth = 8;
            const barX = canvasWidth - barWidth - 4;
            const padY = 4;
            const trackHeight = canvasHeight - padY * 2;
            if (trackHeight <= 0) return;
            const totalLines = scrollbackLength + rows;
            const thumbHeight = Math.max(20, (rows / totalLines) * trackHeight);
            const scrollRatio = viewportY / scrollbackLength;
            const thumbY = padY + (trackHeight - thumbHeight) * (1 - scrollRatio);
            ctx.fillStyle = `rgba(128, 128, 128, ${0.1 * opacity})`;
            ctx.fillRect(barX, padY, barWidth, trackHeight);
            const thumbAlpha = viewportY > 0 ? 0.5 : 0.3;
            ctx.fillStyle = `rgba(128, 128, 128, ${thumbAlpha * opacity})`;
            ctx.fillRect(barX, thumbY, barWidth, thumbHeight);
        };
    }

    if (typeof renderer.render === "function") {
        const originalRender = renderer.render.bind(renderer);
        let lastViewportY = Number.NaN;
        let lastScrollbackLen = -1;
        let lastScrollbarOpacity = -1;
        let lastCursorX = -1;
        let lastCursorY = -1;
        let lastCursorVisible = false;
        let lastBlinkVisible = true;
        let lastCols = -1;
        let lastRows = -1;
        let lastHadSelection = false;
        const rowCache: GhosttyCell[][] = [];

        renderer.render = (
            buffer: unknown,
            forceAll = false,
            viewportY = 0,
            scrollbackProvider?: unknown,
            scrollbarOpacity = 0,
        ) => {
            if (!getVisible()) return;

            const wasmBuf = buffer as {
                update(): number;
                getCursor(): {x: number; y: number; visible: boolean};
                getDimensions(): {cols: number; rows: number};
                getViewport(): GhosttyCell[];
                getLine(row: number): GhosttyCell[] | null;
            };

            const dirtyState = wasmBuf.update();
            const dims = wasmBuf.getDimensions();
            const cursor = wasmBuf.getCursor();
            const sbProvider = scrollbackProvider as {getScrollbackLength(): number} | undefined;
            const scrollbackLen = sbProvider ? sbProvider.getScrollbackLength() : 0;
            const blinkVisible = Boolean(renderer.cursorVisible);
            const selMgr = renderer.selectionManager;
            const hasSelection = Boolean(selMgr && selMgr.hasSelection());
            const dirtySelection = Boolean(selMgr && selMgr.getDirtySelectionRows().size > 0);
            const hoverChanged =
                renderer.hoveredHyperlinkId !== renderer.previousHoveredHyperlinkId ||
                renderer.hoveredLinkRange !== renderer.previousHoveredLinkRange;

            const idle =
                !forceAll &&
                dirtyState === 0 &&
                viewportY === lastViewportY &&
                scrollbackLen === lastScrollbackLen &&
                scrollbarOpacity === lastScrollbarOpacity &&
                dims.cols === lastCols &&
                dims.rows === lastRows &&
                cursor.x === lastCursorX &&
                cursor.y === lastCursorY &&
                cursor.visible === lastCursorVisible &&
                blinkVisible === lastBlinkVisible &&
                !dirtySelection &&
                !hoverChanged &&
                hasSelection === lastHadSelection &&
                !hasSelection;

            if (idle) return;

            const scrollbarChanged =
                scrollbarOpacity !== lastScrollbarOpacity &&
                (scrollbarOpacity > 0 || lastScrollbarOpacity > 0);
            const effectiveForceAll = forceAll || scrollbarChanged;

            lastViewportY = viewportY;
            lastScrollbackLen = scrollbackLen;
            lastScrollbarOpacity = scrollbarOpacity;
            lastCols = dims.cols;
            lastRows = dims.rows;
            lastCursorX = cursor.x;
            lastCursorY = cursor.y;
            lastCursorVisible = cursor.visible;
            lastBlinkVisible = blinkVisible;
            lastHadSelection = hasSelection;

            const origGetLine = wasmBuf.getLine;
            let cachedViewport: GhosttyCell[] | null = null;
            wasmBuf.getLine = (row: number): GhosttyCell[] | null => {
                if (row < 0 || row >= dims.rows) return null;
                if (cachedViewport === null) {
                    cachedViewport = wasmBuf.getViewport();
                }
                const start = row * dims.cols;
                if (start + dims.cols > cachedViewport.length) return null;
                let slice = rowCache[row];
                if (!slice) {
                    slice = new Array<GhosttyCell>(dims.cols);
                    rowCache[row] = slice;
                }
                slice.length = dims.cols;
                for (let c = 0; c < dims.cols; c++) {
                    slice[c] = cachedViewport[start + c];
                }
                return slice;
            };

            try {
                originalRender(buffer, effectiveForceAll, viewportY, scrollbackProvider, scrollbarOpacity);
            } finally {
                wasmBuf.getLine = origGetLine;
            }
        };
    }

    const internalTerm = term as unknown as {
        cols: number;
        rows: number;
        isDisposed: boolean;
        isOpen: boolean;
        viewportY: number;
        resizeEmitter?: {fire(dims: {cols: number; rows: number}): void};
    };
    term.resize = (nextCols: number, nextRows: number) => {
        if (internalTerm.isDisposed || !internalTerm.isOpen) return;
        if (nextCols === internalTerm.cols && nextRows === internalTerm.rows) return;
        internalTerm.cols = nextCols;
        internalTerm.rows = nextRows;
        term.wasmTerm?.resize(nextCols, nextRows);
        renderer.resize?.(nextCols, nextRows);
        internalTerm.resizeEmitter?.fire({cols: nextCols, rows: nextRows});
        if (term.wasmTerm && getVisible()) {
            renderer.render?.(term.wasmTerm, true, internalTerm.viewportY, term);
        }
    };
}

function cellBackgroundColor(cell: GhosttyCell, defaultBg: string, colorRemap: GhosttyColorRemap | null): string {
    const inverseFlag = 16;
    let r = cell.bg_r;
    let g = cell.bg_g;
    let b = cell.bg_b;
    if ((cell.flags & inverseFlag) !== 0) {
        r = cell.fg_r;
        g = cell.fg_g;
        b = cell.fg_b;
    }
    if (colorRemap) {
        const mapped = colorRemap.get(rgbKey(r, g, b));
        if (mapped) {
            r = mapped[0];
            g = mapped[1];
            b = mapped[2];
        }
    }
    return r === 0 && g === 0 && b === 0 ? defaultBg : `rgb(${r}, ${g}, ${b})`;
}

function dominantRowBackground(
    rowCells: GhosttyCell[] | null,
    startIdx: number,
    length: number,
    defaultBg: string,
    colorRemap: GhosttyColorRemap | null,
): string {
    if (!rowCells || length <= 0) return defaultBg;

    const counts = new Map<string, number>();
    let firstColColor = defaultBg;
    let firstColSet = false;

    for (let i = 0; i < length; i++) {
        const cell = rowCells[startIdx + i];
        if (!cell || cell.width === 0) continue;

        const color = cellBackgroundColor(cell, defaultBg, colorRemap);
        if (!firstColSet) {
            firstColColor = color;
            firstColSet = true;
        }
        const weight = Math.max(1, cell.width);
        counts.set(color, (counts.get(color) ?? 0) + weight);
    }

    let bestColor = firstColColor;
    let bestCount = -1;
    let tied = false;

    for (const [color, count] of counts.entries()) {
        if (count > bestCount) {
            bestCount = count;
            bestColor = color;
            tied = false;
        } else if (count === bestCount) {
            tied = true;
        }
    }

    return tied ? firstColColor : bestColor;
}

function buildVerticalBandBackground(rowColors: string[], rowHeight: number, fallback: string): string {
    if (rowColors.length === 0) return fallback;

    const stops: Array<{color: string; startRow: number; endRow: number}> = [];
    for (let y = 0; y < rowColors.length; y++) {
        const color = rowColors[y];
        const prev = stops[stops.length - 1];
        if (prev && prev.color === color) {
            prev.endRow = y + 1;
        } else {
            stops.push({color, startRow: y, endRow: y + 1});
        }
    }

    if (stops.length === 1) {
        return stops[0].color;
    }

    const parts: string[] = [];
    for (let i = 0; i < stops.length; i++) {
        const stop = stops[i];
        const startPx = (stop.startRow * rowHeight).toFixed(2);
        const endPos = i === stops.length - 1 ? "100%" : `${(stop.endRow * rowHeight).toFixed(2)}px`;
        parts.push(`${stop.color} ${startPx}px`, `${stop.color} ${endPos}`);
    }
    return `linear-gradient(to bottom, ${parts.join(", ")})`;
}

function getGhosttyFrameColors(
    term: GhosttyTerminalType,
    containerEl: HTMLDivElement | null,
    defaultBg: string,
    colorRemap: GhosttyColorRemap | null,
): TerminalFrameColors {
    const cols = term.cols;
    const rows = term.rows;
    const charWidth = term.renderer?.charWidth ?? 0;
    const charHeight = term.renderer?.charHeight ?? 0;
    const canvasWidth = cols * charWidth;
    const canvasHeight = rows * charHeight;
    const extraWidth = containerEl ? Math.max(0, containerEl.clientWidth - canvasWidth) : 0;
    const canvasLeft = Math.floor(extraWidth / 2);

    if (!term.wasmTerm || cols <= 0 || rows <= 0) {
        return {
            top: defaultBg,
            bottom: defaultBg,
            leftBackground: defaultBg,
            rightBackground: defaultBg,
            canvasLeft,
            canvasWidth,
            canvasHeight,
        };
    }

    term.wasmTerm.update();
    const viewport = term.wasmTerm.getViewport();
    const scrollbackLen = term.getScrollbackLength();
    const viewportOffset = Math.floor(term.getViewportY());

    const resolveRow = (y: number): {cells: GhosttyCell[] | null; start: number; length: number} => {
        if (viewportOffset > 0 && y < viewportOffset) {
            const sbIndex = scrollbackLen - viewportOffset + y;
            const line = sbIndex >= 0 && sbIndex < scrollbackLen ? term.getScrollbackLine(sbIndex) : null;
            return {cells: line, start: 0, length: line ? line.length : 0};
        }
        const screenRow = y - viewportOffset;
        if (screenRow < 0 || screenRow >= rows) {
            return {cells: null, start: 0, length: 0};
        }
        const start = screenRow * cols;
        if (start + cols > viewport.length) {
            return {cells: null, start: 0, length: 0};
        }
        return {cells: viewport, start, length: cols};
    };

    const topRow = resolveRow(0);
    const bottomRow = resolveRow(rows - 1);
    const top = dominantRowBackground(topRow.cells, topRow.start, topRow.length, defaultBg, colorRemap);
    const bottom = dominantRowBackground(bottomRow.cells, bottomRow.start, bottomRow.length, defaultBg, colorRemap);

    const leftRowColors: string[] = new Array(rows);
    const rightRowColors: string[] = new Array(rows);

    for (let y = 0; y < rows; y++) {
        const row = resolveRow(y);
        let leftColor = defaultBg;
        let rightColor = defaultBg;
        if (row.cells && row.length > 0) {
            for (let i = 0; i < row.length; i++) {
                const cell = row.cells[row.start + i];
                if (cell && cell.width !== 0) {
                    leftColor = cellBackgroundColor(cell, defaultBg, colorRemap);
                    break;
                }
            }
            for (let i = row.length - 1; i >= 0; i--) {
                const cell = row.cells[row.start + i];
                if (cell && cell.width !== 0) {
                    rightColor = cellBackgroundColor(cell, defaultBg, colorRemap);
                    break;
                }
            }
        }
        leftRowColors[y] = leftColor;
        rightRowColors[y] = rightColor;
    }

    return {
        top,
        bottom,
        leftBackground: buildVerticalBandBackground(leftRowColors, charHeight, defaultBg),
        rightBackground: buildVerticalBandBackground(rightRowColors, charHeight, defaultBg),
        canvasLeft,
        canvasWidth,
        canvasHeight,
    };
}

function applyGhosttyThemeAndRender(
    term: GhosttyTerminalType,
    cursorVisible: boolean,
    onUpdateRemap?: (activePalette: TerminalPalette) => void,
): void {
    const base = getActiveTerminalTheme();
    onUpdateRemap?.(base);
    const theme = {
        ...base,
        cursor: cursorVisible ? base.cursor : "transparent",
    };
    term.renderer?.setTheme(theme);
    if (term.renderer && term.wasmTerm) {
        term.renderer.render(term.wasmTerm, true, term.getViewportY(), term);
    }
}

export function useTerminalEngine(options: UseTerminalEngineOptions = {}): UseTerminalEngineResult {
    useBackendFeatures();
    const useGhostty = hasFeature(Feature.TERMINAL_V2);
    const engineKind: "ghostty" | "xterm" = useGhostty ? "ghostty" : "xterm";

    const termRef = useRef<HTMLDivElement | null>(null);
    const [ready, setReady] = useState(false);

    const dataListenersRef = useRef(new Set<(data: string) => void>());
    const resizeListenersRef = useRef(new Set<(dims: {cols: number; rows: number}) => void>());
    const measuredListenersRef = useRef(new Set<() => void>());
    const titleListenersRef = useRef(new Set<(title: string) => void>());
    const bellListenersRef = useRef(new Set<() => void>());
    const osc777ListenersRef = useRef(new Set<(payload: string) => void>());
    const frameColorsListenersRef = useRef(new Set<(colors: TerminalFrameColors) => void>());
    const measuredRef = useRef(false);
    const lastFrameKeyRef = useRef("");
    const lastFrameColorsRef = useRef<TerminalFrameColors | null>(null);
    const oscCarryRef = useRef("");
    const pendingWritesRef = useRef<Array<{data: string | Uint8Array; replay: boolean}>>([]);
    const suppressReplaySideEffectsRef = useRef(false);
    const xtermReplayDepthRef = useRef(0);
    const pendingSizeRef = useRef<{cols: number; rows: number} | null>(null);
    const pendingFocusRef = useRef(false);
    const fontSizeRef = useRef(clampFontSize(options.fontSize ?? TERMINAL_DEFAULT_FONT_SIZE));
    const visibleRef = useRef(options.visible !== false);
    const cursorVisibleRef = useRef(options.cursorVisible ?? !options.readOnly);
    const cursorBlinkRef = useRef(options.cursorBlink ?? !options.readOnly);
    const keyInterceptorRef = useRef(options.keyInterceptor);
    const readOnlyRef = useRef(Boolean(options.readOnly));
    const autofitRef = useRef(options.autofit !== false);

    keyInterceptorRef.current = options.keyInterceptor;
    readOnlyRef.current = Boolean(options.readOnly);
    autofitRef.current = options.autofit !== false;

    const activeGhosttyRef = useRef<{
        term: GhosttyTerminalType;
        fitNow: (force?: boolean) => void;
        setVisible: (visible: boolean) => void;
        markDirtyIfHidden: () => void;
        scheduleTopBgCheck: () => void;
        emitFrameColors: () => void;
        updateThemeRemap: (activePalette: TerminalPalette) => void;
    } | null>(null);

    const activeXtermRef = useRef<{
        term: XtermTerminal;
        fitAddon: XtermFitAddon;
    } | null>(null);

    const handleRef = useRef<TerminalEngineHandle | null>(null);
    if (handleRef.current === null) {
        handleRef.current = {
            get cols(): number {
                if (activeGhosttyRef.current) return activeGhosttyRef.current.term.cols;
                if (activeXtermRef.current) return activeXtermRef.current.term.cols;
                return pendingSizeRef.current?.cols ?? 80;
            },
            get rows(): number {
                if (activeGhosttyRef.current) return activeGhosttyRef.current.term.rows;
                if (activeXtermRef.current) return activeXtermRef.current.term.rows;
                return pendingSizeRef.current?.rows ?? 24;
            },
            get measured(): boolean {
                return measuredRef.current;
            },
            get engine(): "ghostty" | "xterm" {
                return activeGhosttyRef.current ? "ghostty" : "xterm";
            },
            write(data: string | Uint8Array): void {
                scanOsc777(data, oscCarryRef, osc777ListenersRef.current);
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.write(data);
                    activeGhosttyRef.current.markDirtyIfHidden();
                    activeGhosttyRef.current.scheduleTopBgCheck();
                    return;
                }
                if (activeXtermRef.current) {
                    activeXtermRef.current.term.write(data);
                    return;
                }
                pendingWritesRef.current.push({data, replay: false});
            },
            writeReplay(data: string | Uint8Array): void {
                if (activeGhosttyRef.current) {
                    suppressReplaySideEffectsRef.current = true;
                    try {
                        activeGhosttyRef.current.term.write(data);
                    } finally {
                        suppressReplaySideEffectsRef.current = false;
                    }
                    activeGhosttyRef.current.markDirtyIfHidden();
                    activeGhosttyRef.current.scheduleTopBgCheck();
                    return;
                }
                if (activeXtermRef.current) {
                    xtermReplayDepthRef.current += 1;
                    activeXtermRef.current.term.write(data, () => {
                        xtermReplayDepthRef.current = Math.max(0, xtermReplayDepthRef.current - 1);
                    });
                    return;
                }
                pendingWritesRef.current.push({data, replay: true});
            },
            appendText(text: string): void {
                const normalized = text.replace(/\n/g, "\r\n");
                this.write(normalized);
            },
            reset(): void {
                oscCarryRef.current = "";
                if (activeGhosttyRef.current) {
                    const term = activeGhosttyRef.current.term;
                    term.clearSelection();
                    if (term.wasmTerm) {
                        term.wasmTerm.write("\x1bc\x1b[2J\x1b[3J\x1b[H");
                        term.wasmTerm.update();
                    }
                    activeGhosttyRef.current.markDirtyIfHidden();
                    if (visibleRef.current && term.renderer && term.wasmTerm) {
                        term.renderer.render(term.wasmTerm, true, 0, term);
                    }
                    activeGhosttyRef.current.emitFrameColors();
                    return;
                }
                if (activeXtermRef.current) {
                    activeXtermRef.current.term.reset();
                    return;
                }
                pendingWritesRef.current = [];
            },
            clear(): void {
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.clear();
                    activeGhosttyRef.current.markDirtyIfHidden();
                    activeGhosttyRef.current.scheduleTopBgCheck();
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.term.clear();
                }
            },
            focus(): void {
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.focus();
                    return;
                }
                if (activeXtermRef.current) {
                    activeXtermRef.current.term.focus();
                    return;
                }
                pendingFocusRef.current = true;
            },
            blur(): void {
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.blur();
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.term.blur();
                }
            },
            resize(cols: number, rows: number): void {
                const safeCols = Math.max(1, Math.floor(cols));
                const safeRows = Math.max(1, Math.floor(rows));
                pendingSizeRef.current = {cols: safeCols, rows: safeRows};
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.resize(safeCols, safeRows);
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.term.resize(safeCols, safeRows);
                }
            },
            fit(): void {
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.fitNow();
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.fitAddon.fit();
                }
            },
            paste(text: string): void {
                if (readOnlyRef.current) return;
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.paste(text);
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.term.paste(text);
                }
            },
            getSelection(): string {
                if (activeGhosttyRef.current) return activeGhosttyRef.current.term.getSelection();
                if (activeXtermRef.current) return activeXtermRef.current.term.getSelection();
                return "";
            },
            hasSelection(): boolean {
                if (activeGhosttyRef.current) return activeGhosttyRef.current.term.hasSelection();
                if (activeXtermRef.current) return activeXtermRef.current.term.hasSelection();
                return false;
            },
            setVisible(visible: boolean): void {
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.setVisible(visible);
                } else {
                    visibleRef.current = visible;
                }
            },
            setCursorVisible(visible: boolean): void {
                if (cursorVisibleRef.current === visible) return;
                cursorVisibleRef.current = visible;
                if (activeGhosttyRef.current) {
                    const term = activeGhosttyRef.current.term;
                    const blink = visible && cursorBlinkRef.current && !readOnlyRef.current;
                    term.options.cursorBlink = blink;
                    term.renderer?.setCursorBlink(blink);
                    if (visibleRef.current) {
                        applyGhosttyThemeAndRender(term, visible, activeGhosttyRef.current.updateThemeRemap);
                    } else {
                        const base = getActiveTerminalTheme();
                        activeGhosttyRef.current.updateThemeRemap(base);
                        term.renderer?.setTheme({
                            ...base,
                            cursor: visible ? base.cursor : "transparent",
                        });
                        activeGhosttyRef.current.markDirtyIfHidden();
                    }
                } else if (activeXtermRef.current) {
                    const base = isTerminalLightTheme() ? xtermThemes.light : xtermThemes.dark;
                    activeXtermRef.current.term.options.theme = {
                        ...base,
                        cursor: visible ? base.cursor : "transparent",
                    };
                    activeXtermRef.current.term.options.cursorBlink = visible && cursorBlinkRef.current && !readOnlyRef.current;
                }
            },
            setCursorBlink(enabled: boolean): void {
                cursorBlinkRef.current = enabled;
                const effective = enabled && cursorVisibleRef.current && !readOnlyRef.current;
                if (activeGhosttyRef.current) {
                    if (activeGhosttyRef.current.term.options.cursorBlink === effective) return;
                    activeGhosttyRef.current.term.options.cursorBlink = effective;
                    activeGhosttyRef.current.term.renderer?.setCursorBlink(effective);
                } else if (activeXtermRef.current) {
                    if (activeXtermRef.current.term.options.cursorBlink === effective) return;
                    activeXtermRef.current.term.options.cursorBlink = effective;
                }
            },
            setFontSize(size: number): void {
                const clamped = clampFontSize(size);
                if (fontSizeRef.current === clamped) return;
                fontSizeRef.current = clamped;
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.options.fontSize = clamped;
                    activeGhosttyRef.current.term.renderer?.remeasureFont();
                    if (autofitRef.current) {
                        activeGhosttyRef.current.fitNow(true);
                    }
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.term.options.fontSize = clamped;
                    if (autofitRef.current) {
                        activeXtermRef.current.fitAddon.fit();
                    }
                }
            },
            getFontSize(): number {
                return fontSizeRef.current;
            },
            search(query: string, caseSensitive: boolean): TerminalSearchMatch[] {
                if (activeGhosttyRef.current) {
                    return searchGhosttyBuffer(activeGhosttyRef.current.term, query, caseSensitive);
                }
                if (activeXtermRef.current) {
                    return searchXtermBuffer(activeXtermRef.current.term, query, caseSensitive);
                }
                return [];
            },
            jumpToMatch(match: TerminalSearchMatch): void {
                if (activeGhosttyRef.current) {
                    const term = activeGhosttyRef.current.term;
                    const scrollbackLen = term.getScrollbackLength();
                    const targetLine = Math.max(0, Math.min(scrollbackLen, match.lineIndex - Math.floor(term.rows / 3)));
                    term.scrollToLine(targetLine);
                    const viewportRow = match.lineIndex - targetLine;
                    if (viewportRow >= 0 && viewportRow < term.rows) {
                        term.select(match.col, viewportRow, match.length);
                    }
                } else if (activeXtermRef.current) {
                    const term = activeXtermRef.current.term;
                    term.scrollToLine(Math.max(0, match.lineIndex - Math.floor(term.rows / 3)));
                    term.select(match.col, match.lineIndex, match.length);
                }
            },
            clearSelection(): void {
                if (activeGhosttyRef.current) {
                    activeGhosttyRef.current.term.clearSelection();
                } else if (activeXtermRef.current) {
                    activeXtermRef.current.term.clearSelection();
                }
            },
            onData(listener: (data: string) => void): () => void {
                dataListenersRef.current.add(listener);
                return () => {
                    dataListenersRef.current.delete(listener);
                };
            },
            onResize(listener: (dims: {cols: number; rows: number}) => void): () => void {
                resizeListenersRef.current.add(listener);
                return () => {
                    resizeListenersRef.current.delete(listener);
                };
            },
            onMeasured(listener: () => void): () => void {
                measuredListenersRef.current.add(listener);
                if (measuredRef.current) {
                    listener();
                }
                return () => {
                    measuredListenersRef.current.delete(listener);
                };
            },
            onTitleChange(listener: (title: string) => void): () => void {
                titleListenersRef.current.add(listener);
                return () => {
                    titleListenersRef.current.delete(listener);
                };
            },
            onBell(listener: () => void): () => void {
                bellListenersRef.current.add(listener);
                return () => {
                    bellListenersRef.current.delete(listener);
                };
            },
            onOsc777(listener: (data: string) => void): () => void {
                osc777ListenersRef.current.add(listener);
                return () => {
                    osc777ListenersRef.current.delete(listener);
                };
            },
            onFrameColorsChange(listener: (colors: TerminalFrameColors) => void): () => void {
                frameColorsListenersRef.current.add(listener);
                if (lastFrameColorsRef.current) {
                    listener(lastFrameColorsRef.current);
                }
                return () => {
                    frameColorsListenersRef.current.delete(listener);
                };
            },
        };
    }

    useEffect(() => {
        const container = termRef.current;
        if (!container) return;

        let disposed = false;
        let cleanupFn: (() => void) | null = null;
        measuredRef.current = false;
        setReady(false);

        const markMeasured = () => {
            if (disposed || measuredRef.current) return;
            measuredRef.current = true;
            for (const listener of measuredListenersRef.current) {
                listener();
            }
        };

        if (useGhostty) {
            void loadGhosttyModule().then(async mod => {
                if (disposed || !termRef.current) return;

                const ghostty = await mod.Ghostty.load();
                if (disposed || !termRef.current) return;

                const initialPalette = getActiveTerminalTheme();
                const term = new mod.Terminal({
                    ghostty,
                    theme: {
                        ...initialPalette,
                        cursor: cursorVisibleRef.current ? initialPalette.cursor : "transparent",
                    },
                    fontFamily: TERMINAL_FONT_FAMILY,
                    fontSize: fontSizeRef.current,
                    scrollback: 10000,
                    cursorBlink: !readOnlyRef.current && cursorVisibleRef.current && cursorBlinkRef.current,
                });

                term.attachCustomKeyEventHandler(event => {
                    if (keyInterceptorRef.current?.(event)) {
                        return true;
                    }
                    if (isCopyShortcut(event) && term.hasSelection()) {
                        const selected = term.getSelection();
                        if (selected) {
                            void navigator.clipboard?.writeText(selected);
                        }
                        event.preventDefault();
                        return true;
                    }
                    if (isPasteShortcut(event) && !readOnlyRef.current) {
                        if (navigator.clipboard?.readText) {
                            event.preventDefault();
                            void navigator.clipboard.readText().then(text => {
                                if (text) term.paste(text);
                            }).catch(() => undefined);
                            return true;
                        }
                        return false;
                    }
                    if (readOnlyRef.current) {
                        return !isReadOnlyAllowedKey(event);
                    }
                    return false;
                });

                term.open(termRef.current);
                let activeColorRemap: GhosttyColorRemap | null = null;
                const updateThemeRemap = (activePalette: TerminalPalette) => {
                    activeColorRemap = buildGhosttyColorRemap(initialPalette, activePalette);
                };
                installGhosttyRendererPatches(term, () => activeColorRemap, () => visibleRef.current);

                termRef.current.removeAttribute("contenteditable");
                termRef.current.style.caretColor = "transparent";
                termRef.current.style.outline = "none";
                if (term.textarea) {
                    term.textarea.style.caretColor = "transparent";
                }

                const dataDisp = term.onData(data => {
                    if (readOnlyRef.current || suppressReplaySideEffectsRef.current) return;
                    for (const listener of dataListenersRef.current) {
                        listener(data);
                    }
                });
                const resizeDisp = term.onResize(dims => {
                    for (const listener of resizeListenersRef.current) {
                        listener(dims);
                    }
                });
                const titleDisp = term.onTitleChange(title => {
                    for (const listener of titleListenersRef.current) {
                        listener(title);
                    }
                });
                const bellDisp = term.onBell(() => {
                    if (suppressReplaySideEffectsRef.current) return;
                    for (const listener of bellListenersRef.current) {
                        listener();
                    }
                });

                let topBgTimerId: number | null = null;
                const emitFrameColors = () => {
                    if (disposed || !visibleRef.current || frameColorsListenersRef.current.size === 0) return;
                    const colors = getGhosttyFrameColors(
                        term,
                        termRef.current,
                        getActiveTerminalTheme().background,
                        activeColorRemap,
                    );
                    const key = `${colors.top}|${colors.bottom}|${colors.leftBackground}|${colors.rightBackground}|${colors.canvasLeft}|${colors.canvasWidth.toFixed(1)}|${colors.canvasHeight.toFixed(1)}`;
                    if (key !== lastFrameKeyRef.current) {
                        lastFrameKeyRef.current = key;
                        lastFrameColorsRef.current = colors;
                        for (const listener of frameColorsListenersRef.current) {
                            listener(colors);
                        }
                    }
                };
                const scheduleTopBgCheck = () => {
                    if (disposed || !visibleRef.current || topBgTimerId !== null) return;
                    topBgTimerId = window.setTimeout(() => {
                        topBgTimerId = null;
                        emitFrameColors();
                    }, 80);
                };

                const resizeBgDisp = term.onResize(emitFrameColors);
                const scrollDisp = term.onScroll(scheduleTopBgCheck);

                let rafId: number | null = null;
                let resizeObserver: ResizeObserver | null = null;
                let dirtyWhileHidden = !visibleRef.current;
                let needsFitOnShow = false;
                let lastMeasuredW = -1;
                let lastMeasuredH = -1;
                let lastMeasuredFont = -1;

                const internalLoopTerm = term as unknown as {
                    animationFrameId?: number;
                    startRenderLoop?: () => void;
                };
                const pauseRenderLoop = () => {
                    if (internalLoopTerm.animationFrameId !== undefined) {
                        window.cancelAnimationFrame(internalLoopTerm.animationFrameId);
                        internalLoopTerm.animationFrameId = undefined;
                    }
                };
                const resumeRenderLoop = () => {
                    if (internalLoopTerm.animationFrameId === undefined && typeof internalLoopTerm.startRenderLoop === "function") {
                        internalLoopTerm.startRenderLoop();
                    }
                };

                if (!visibleRef.current) {
                    pauseRenderLoop();
                }

                const markDirtyIfHidden = () => {
                    if (!visibleRef.current) {
                        dirtyWhileHidden = true;
                    }
                };

                const fitNow = (force = false) => {
                    if (disposed || !activeGhosttyRef.current) return;
                    const el = termRef.current;
                    if (!el || el.clientWidth === 0 || el.clientHeight === 0) return;
                    const w = el.clientWidth;
                    const h = el.clientHeight;
                    const f = fontSizeRef.current;
                    if (!force && measuredRef.current && w === lastMeasuredW && h === lastMeasuredH && f === lastMeasuredFont) {
                        return;
                    }
                    const metrics = term.renderer?.getMetrics();
                    if (!metrics || metrics.width <= 0 || metrics.height <= 0) return;
                    const style = window.getComputedStyle(el);
                    const padH = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0);
                    const padV = (Number.parseFloat(style.paddingTop) || 0) + (Number.parseFloat(style.paddingBottom) || 0);
                    const availW = Math.max(0, w - padH);
                    const availH = Math.max(0, h - padV);
                    const cols = Math.max(2, Math.floor(availW / metrics.width));
                    const rows = Math.max(1, Math.floor(availH / metrics.height));
                    lastMeasuredW = w;
                    lastMeasuredH = h;
                    lastMeasuredFont = f;
                    const sizeChanged = cols !== term.cols || rows !== term.rows;
                    if (sizeChanged) {
                        term.resize(cols, rows);
                        if (!visibleRef.current) {
                            dirtyWhileHidden = true;
                        }
                    }
                    if (visibleRef.current) {
                        emitFrameColors();
                    } else {
                        dirtyWhileHidden = true;
                    }
                    markMeasured();
                };

                const setVisible = (nextVisible: boolean) => {
                    const changed = visibleRef.current !== nextVisible;
                    visibleRef.current = nextVisible;
                    if (!changed) return;
                    if (!nextVisible) {
                        pauseRenderLoop();
                        return;
                    }
                    if (needsFitOnShow) {
                        needsFitOnShow = false;
                        fitNow(false);
                    }
                    if (dirtyWhileHidden) {
                        dirtyWhileHidden = false;
                        if (term.renderer && term.wasmTerm) {
                            term.renderer.render(term.wasmTerm, true, term.getViewportY(), term);
                        }
                        emitFrameColors();
                    }
                    resumeRenderLoop();
                };

                activeGhosttyRef.current = {
                    term,
                    fitNow,
                    setVisible,
                    markDirtyIfHidden,
                    scheduleTopBgCheck,
                    emitFrameColors,
                    updateThemeRemap,
                };

                const scheduleFit = () => {
                    if (!autofitRef.current || disposed) return;
                    if (!visibleRef.current) {
                        needsFitOnShow = true;
                        return;
                    }
                    if (rafId !== null) return;
                    rafId = window.requestAnimationFrame(() => {
                        rafId = null;
                        fitNow(false);
                    });
                };

                if (autofitRef.current) {
                    fitNow(false);
                    if (typeof ResizeObserver !== "undefined") {
                        resizeObserver = new ResizeObserver(scheduleFit);
                        resizeObserver.observe(termRef.current);
                    }
                } else {
                    if (pendingSizeRef.current) {
                        term.resize(pendingSizeRef.current.cols, pendingSizeRef.current.rows);
                    }
                    markMeasured();
                }

                if (typeof document !== "undefined" && document.fonts?.ready) {
                    void document.fonts.ready.then(() => {
                        if (disposed || !activeGhosttyRef.current) return;
                        activeGhosttyRef.current.term.renderer?.remeasureFont();
                        if (visibleRef.current) {
                            fitNow(true);
                        } else {
                            needsFitOnShow = true;
                            dirtyWhileHidden = true;
                        }
                    });
                }

                const unsubTheme = subscribeTerminalThemeChange(() => {
                    if (disposed || !activeGhosttyRef.current) return;
                    if (visibleRef.current) {
                        applyGhosttyThemeAndRender(
                            activeGhosttyRef.current.term,
                            cursorVisibleRef.current,
                            activeGhosttyRef.current.updateThemeRemap,
                        );
                        emitFrameColors();
                    } else {
                        const base = getActiveTerminalTheme();
                        activeGhosttyRef.current.updateThemeRemap(base);
                        activeGhosttyRef.current.term.renderer?.setTheme({
                            ...base,
                            cursor: cursorVisibleRef.current ? base.cursor : "transparent",
                        });
                        dirtyWhileHidden = true;
                    }
                });

                emitFrameColors();

                if (pendingWritesRef.current.length > 0) {
                    const queued = pendingWritesRef.current;
                    pendingWritesRef.current = [];
                    for (const item of queued) {
                        if (item.replay) {
                            suppressReplaySideEffectsRef.current = true;
                            try {
                                term.write(item.data);
                            } finally {
                                suppressReplaySideEffectsRef.current = false;
                            }
                        } else {
                            term.write(item.data);
                        }
                    }
                    markDirtyIfHidden();
                    scheduleTopBgCheck();
                }

                if (pendingFocusRef.current) {
                    pendingFocusRef.current = false;
                    term.focus();
                }

                setReady(true);

                cleanupFn = () => {
                    measuredRef.current = false;
                    pauseRenderLoop();
                    if (rafId !== null) window.cancelAnimationFrame(rafId);
                    if (topBgTimerId !== null) window.clearTimeout(topBgTimerId);
                    resizeObserver?.disconnect();
                    unsubTheme();
                    dataDisp.dispose();
                    resizeDisp.dispose();
                    titleDisp.dispose();
                    bellDisp.dispose();
                    resizeBgDisp.dispose();
                    scrollDisp.dispose();
                    term.dispose();
                    activeGhosttyRef.current = null;
                };
            });
        } else {
            const fitAddon = new XtermFitAddon();
            const term = new XtermTerminal({
                theme: isTerminalLightTheme() ? xtermThemes.light : xtermThemes.dark,
                fontFamily: "Jetbrains Mono, Ubuntu Mono, courier-new, courier, monospace",
                fontSize: 16,
            });
            term.loadAddon(fitAddon);

            term.attachCustomKeyEventHandler(event => {
                if (keyInterceptorRef.current?.(event)) {
                    return false;
                }
                if (readOnlyRef.current) {
                    return isReadOnlyAllowedKey(event);
                }
                return true;
            });

            term.open(container);
            if (readOnlyRef.current && term.textarea) {
                term.textarea.readOnly = true;
            }

            const dataDisp = term.onData(data => {
                if (readOnlyRef.current || xtermReplayDepthRef.current > 0) return;
                for (const listener of dataListenersRef.current) {
                    listener(data);
                }
            });
            const resizeDisp = term.onResize(dims => {
                for (const listener of resizeListenersRef.current) {
                    listener(dims);
                }
            });
            const titleDisp = term.onTitleChange(title => {
                for (const listener of titleListenersRef.current) {
                    listener(title);
                }
            });
            const bellDisp = term.onBell(() => {
                if (xtermReplayDepthRef.current > 0) return;
                for (const listener of bellListenersRef.current) {
                    listener();
                }
            });
            const osc1Disp = term.parser.registerOscHandler(1, title => {
                for (const listener of titleListenersRef.current) {
                    listener(title);
                }
                return true;
            });

            activeXtermRef.current = {term, fitAddon};

            const onWindowResize = () => {
                if (autofitRef.current && container.clientWidth > 0 && container.clientHeight > 0) {
                    fitAddon.fit();
                    markMeasured();
                }
            };
            window.addEventListener("resize", onWindowResize);
            const intervals: number[] = [];
            if (autofitRef.current) {
                onWindowResize();
                intervals.push(window.setInterval(onWindowResize, 100));
                intervals.push(window.setInterval(onWindowResize, 500));
                intervals.push(window.setInterval(onWindowResize, 1000));
                intervals.push(window.setInterval(onWindowResize, 2000));
                intervals.push(window.setInterval(onWindowResize, 5000));
            } else {
                if (pendingSizeRef.current) {
                    term.resize(pendingSizeRef.current.cols, pendingSizeRef.current.rows);
                }
                markMeasured();
            }

            const unsubTheme = subscribeTerminalThemeChange(() => {
                if (disposed || !activeXtermRef.current) return;
                activeXtermRef.current.term.options.theme = isTerminalLightTheme() ? xtermThemes.light : xtermThemes.dark;
            });

            if (pendingWritesRef.current.length > 0) {
                const queued = pendingWritesRef.current;
                pendingWritesRef.current = [];
                for (const item of queued) {
                    if (item.replay) {
                        xtermReplayDepthRef.current += 1;
                        term.write(item.data, () => {
                            xtermReplayDepthRef.current = Math.max(0, xtermReplayDepthRef.current - 1);
                        });
                    } else {
                        term.write(item.data);
                    }
                }
            }

            if (pendingFocusRef.current) {
                pendingFocusRef.current = false;
                term.focus();
            }

            setReady(true);

            cleanupFn = () => {
                measuredRef.current = false;
                window.removeEventListener("resize", onWindowResize);
                for (const id of intervals) window.clearInterval(id);
                unsubTheme();
                dataDisp.dispose();
                resizeDisp.dispose();
                titleDisp.dispose();
                bellDisp.dispose();
                osc1Disp.dispose();
                term.dispose();
                activeXtermRef.current = null;
            };
        }

        return () => {
            disposed = true;
            cleanupFn?.();
        };
    }, [useGhostty]);

    return {
        termRef,
        handle: handleRef.current,
        ready,
        engine: engineKind,
    };
}
