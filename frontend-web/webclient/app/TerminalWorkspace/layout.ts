import {TerminalSessionLayoutNode, TerminalSessionSplitDirection} from "@/UCloud/TerminalSessionsApi";

export type PaneMoveDirection = "left" | "right" | "up" | "down";

interface PaneRect {
    paneId: string;
    x0: number;
    y0: number;
    x1: number;
    y1: number;
}

export interface LayoutAxisExpr {
    percent: number;
    px: number;
}

export interface LayoutBoxExpr {
    left: LayoutAxisExpr;
    top: LayoutAxisExpr;
    width: LayoutAxisExpr;
    height: LayoutAxisExpr;
}

export interface LayoutGutterEntry {
    key: string;
    path: Array<"first" | "second">;
    direction: TerminalSessionSplitDirection;
    parentBox: LayoutBoxExpr;
    gutterBox: LayoutBoxExpr;
}

export interface LayoutGeometry {
    paneBoxes: Map<string, LayoutBoxExpr>;
    gutters: LayoutGutterEntry[];
}

export function axisExprToCss(expr: LayoutAxisExpr): string {
    const pct = Number(expr.percent.toFixed(4));
    const px = Number(expr.px.toFixed(4));
    if (pct === 0) return `${px}px`;
    if (px === 0) return `${pct}%`;
    return `calc(${pct}% ${px >= 0 ? "+" : "-"} ${Math.abs(px)}px)`;
}

export function axisExprToPx(expr: LayoutAxisExpr, totalPx: number): number {
    return (expr.percent / 100) * totalPx + expr.px;
}

function walkLayoutGeometry(
    node: TerminalSessionLayoutNode | undefined,
    path: Array<"first" | "second">,
    box: LayoutBoxExpr,
    gutterPx: number,
    out: LayoutGeometry,
): void {
    if (!node) return;
    if (node.kind === "pane") {
        if (node.paneId) {
            out.paneBoxes.set(node.paneId, box);
        }
        return;
    }

    const ratio = clampSplitRatio(node.ratio ?? 0.5);
    if (node.direction !== "vertical") {
        const availPercent = box.width.percent;
        const availPx = box.width.px - gutterPx;
        const firstWidth: LayoutAxisExpr = {
            percent: availPercent * ratio,
            px: availPx * ratio,
        };
        const secondWidth: LayoutAxisExpr = {
            percent: availPercent * (1 - ratio),
            px: availPx * (1 - ratio),
        };
        const gutterLeft: LayoutAxisExpr = {
            percent: box.left.percent + firstWidth.percent,
            px: box.left.px + firstWidth.px,
        };
        const secondLeft: LayoutAxisExpr = {
            percent: gutterLeft.percent,
            px: gutterLeft.px + gutterPx,
        };
        out.gutters.push({
            key: path.length === 0 ? "root" : path.join("."),
            path,
            direction: "horizontal",
            parentBox: box,
            gutterBox: {
                left: gutterLeft,
                top: box.top,
                width: {percent: 0, px: gutterPx},
                height: box.height,
            },
        });
        walkLayoutGeometry(node.first, [...path, "first"], {
            left: box.left,
            top: box.top,
            width: firstWidth,
            height: box.height,
        }, gutterPx, out);
        walkLayoutGeometry(node.second, [...path, "second"], {
            left: secondLeft,
            top: box.top,
            width: secondWidth,
            height: box.height,
        }, gutterPx, out);
        return;
    }

    const availPercent = box.height.percent;
    const availPx = box.height.px - gutterPx;
    const firstHeight: LayoutAxisExpr = {
        percent: availPercent * ratio,
        px: availPx * ratio,
    };
    const secondHeight: LayoutAxisExpr = {
        percent: availPercent * (1 - ratio),
        px: availPx * (1 - ratio),
    };
    const gutterTop: LayoutAxisExpr = {
        percent: box.top.percent + firstHeight.percent,
        px: box.top.px + firstHeight.px,
    };
    const secondTop: LayoutAxisExpr = {
        percent: gutterTop.percent,
        px: gutterTop.px + gutterPx,
    };
    out.gutters.push({
        key: path.length === 0 ? "root" : path.join("."),
        path,
        direction: "vertical",
        parentBox: box,
        gutterBox: {
            left: box.left,
            top: gutterTop,
            width: box.width,
            height: {percent: 0, px: gutterPx},
        },
    });
    walkLayoutGeometry(node.first, [...path, "first"], {
        left: box.left,
        top: box.top,
        width: box.width,
        height: firstHeight,
    }, gutterPx, out);
    walkLayoutGeometry(node.second, [...path, "second"], {
        left: box.left,
        top: secondTop,
        width: box.width,
        height: secondHeight,
    }, gutterPx, out);
}

export function computeLayoutGeometry(root: TerminalSessionLayoutNode, gutterPx = 8): LayoutGeometry {
    const out: LayoutGeometry = {
        paneBoxes: new Map(),
        gutters: [],
    };
    walkLayoutGeometry(root, [], {
        left: {percent: 0, px: 0},
        top: {percent: 0, px: 0},
        width: {percent: 100, px: 0},
        height: {percent: 100, px: 0},
    }, gutterPx, out);
    return out;
}

export function clampSplitRatio(ratio: number): number {
    if (!Number.isFinite(ratio)) return 0.5;
    return Math.min(0.9, Math.max(0.1, ratio));
}

export function collectLayoutPaneIds(node: TerminalSessionLayoutNode | undefined): string[] {
    if (!node) return [];
    if (node.kind === "pane") {
        return node.paneId ? [node.paneId] : [];
    }
    return [
        ...collectLayoutPaneIds(node.first),
        ...collectLayoutPaneIds(node.second),
    ];
}

export function updateLayoutSplitRatio(
    node: TerminalSessionLayoutNode,
    path: Array<"first" | "second">,
    ratio: number,
): TerminalSessionLayoutNode {
    if (path.length === 0) {
        if (node.kind !== "split") return node;
        return {
            ...node,
            ratio: clampSplitRatio(ratio),
        };
    }

    if (node.kind !== "split") return node;
    const [head, ...rest] = path;
    if (head === "first" && node.first) {
        return {
            ...node,
            first: updateLayoutSplitRatio(node.first, rest, ratio),
        };
    }
    if (head === "second" && node.second) {
        return {
            ...node,
            second: updateLayoutSplitRatio(node.second, rest, ratio),
        };
    }
    return node;
}

function findPanePath(
    node: TerminalSessionLayoutNode | undefined,
    targetPaneId: string,
    prefix: Array<"first" | "second">,
): Array<"first" | "second"> | null {
    if (!node) return null;
    if (node.kind === "pane") {
        return node.paneId === targetPaneId ? prefix : null;
    }
    const left = findPanePath(node.first, targetPaneId, [...prefix, "first"]);
    if (left) return left;
    return findPanePath(node.second, targetPaneId, [...prefix, "second"]);
}

function nodeAtPath(
    root: TerminalSessionLayoutNode,
    path: Array<"first" | "second">,
): TerminalSessionLayoutNode | null {
    let cursor: TerminalSessionLayoutNode | undefined = root;
    for (const step of path) {
        if (!cursor || cursor.kind !== "split") return null;
        cursor = step === "first" ? cursor.first : cursor.second;
    }
    return cursor ?? null;
}

export function resizePaneByDirection(
    root: TerminalSessionLayoutNode,
    targetPaneId: string,
    moveDirection: PaneMoveDirection,
    step = 0.05,
): TerminalSessionLayoutNode | null {
    const panePath = findPanePath(root, targetPaneId, []);
    if (!panePath || panePath.length === 0) return null;

    const targetAxis: TerminalSessionSplitDirection =
        moveDirection === "left" || moveDirection === "right" ? "horizontal" : "vertical";
    const delta = moveDirection === "right" || moveDirection === "down" ? step : -step;

    for (let depth = panePath.length - 1; depth >= 0; depth--) {
        const ancestorPath = panePath.slice(0, depth);
        const ancestor = nodeAtPath(root, ancestorPath);
        if (ancestor && ancestor.kind === "split" && ancestor.direction === targetAxis) {
            const currentRatio = ancestor.ratio ?? 0.5;
            const nextRatio = clampSplitRatio(currentRatio + delta);
            if (Math.abs(nextRatio - currentRatio) < 0.0001) return null;
            return updateLayoutSplitRatio(root, ancestorPath, nextRatio);
        }
    }

    return null;
}

function collectPaneRects(
    node: TerminalSessionLayoutNode | undefined,
    x0: number,
    y0: number,
    x1: number,
    y1: number,
    out: PaneRect[],
): void {
    if (!node) return;
    if (node.kind === "pane") {
        if (node.paneId) {
            out.push({paneId: node.paneId, x0, y0, x1, y1});
        }
        return;
    }

    const ratio = clampSplitRatio(node.ratio ?? 0.5);
    if (node.direction === "horizontal") {
        const splitX = x0 + (x1 - x0) * ratio;
        collectPaneRects(node.first, x0, y0, splitX, y1, out);
        collectPaneRects(node.second, splitX, y0, x1, y1, out);
    } else {
        const splitY = y0 + (y1 - y0) * ratio;
        collectPaneRects(node.first, x0, y0, x1, splitY, out);
        collectPaneRects(node.second, x0, splitY, x1, y1, out);
    }
}

export function findNeighborPaneId(
    root: TerminalSessionLayoutNode,
    currentPaneId: string,
    direction: PaneMoveDirection,
): string | null {
    const rects: PaneRect[] = [];
    collectPaneRects(root, 0, 0, 1, 1, rects);
    const current = rects.find(r => r.paneId === currentPaneId);
    if (!current) return null;

    const cx = (current.x0 + current.x1) / 2;
    const cy = (current.y0 + current.y1) / 2;
    const eps = 0.001;

    let bestId: string | null = null;
    let bestScore = Number.POSITIVE_INFINITY;

    for (const candidate of rects) {
        if (candidate.paneId === currentPaneId) continue;

        let primaryDist = -1;
        let overlap = 0;
        let secondaryDist = 0;

        if (direction === "right") {
            if (candidate.x0 < current.x1 - eps) continue;
            primaryDist = candidate.x0 - current.x1;
            overlap = Math.min(current.y1, candidate.y1) - Math.max(current.y0, candidate.y0);
            secondaryDist = Math.abs((candidate.y0 + candidate.y1) / 2 - cy);
        } else if (direction === "left") {
            if (candidate.x1 > current.x0 + eps) continue;
            primaryDist = current.x0 - candidate.x1;
            overlap = Math.min(current.y1, candidate.y1) - Math.max(current.y0, candidate.y0);
            secondaryDist = Math.abs((candidate.y0 + candidate.y1) / 2 - cy);
        } else if (direction === "down") {
            if (candidate.y0 < current.y1 - eps) continue;
            primaryDist = candidate.y0 - current.y1;
            overlap = Math.min(current.x1, candidate.x1) - Math.max(current.x0, candidate.x0);
            secondaryDist = Math.abs((candidate.x0 + candidate.x1) / 2 - cx);
        } else {
            if (candidate.y1 > current.y0 + eps) continue;
            primaryDist = current.y0 - candidate.y1;
            overlap = Math.min(current.x1, candidate.x1) - Math.max(current.x0, candidate.x0);
            secondaryDist = Math.abs((candidate.x0 + candidate.x1) / 2 - cx);
        }

        const overlapPenalty = overlap > eps ? 0 : 10;
        const score = overlapPenalty + primaryDist * 4 + secondaryDist;
        if (score < bestScore) {
            bestScore = score;
            bestId = candidate.paneId;
        }
    }

    if (bestId !== null) return bestId;

    let wrapBestId: string | null = null;
    let wrapBestScore = Number.POSITIVE_INFINITY;

    for (const candidate of rects) {
        if (candidate.paneId === currentPaneId) continue;

        let wrapDist = 0;
        let overlap = 0;
        let secondaryDist = 0;

        if (direction === "right") {
            overlap = Math.min(current.y1, candidate.y1) - Math.max(current.y0, candidate.y0);
            wrapDist = candidate.x0;
            secondaryDist = Math.abs((candidate.y0 + candidate.y1) / 2 - cy);
        } else if (direction === "left") {
            overlap = Math.min(current.y1, candidate.y1) - Math.max(current.y0, candidate.y0);
            wrapDist = 1 - candidate.x1;
            secondaryDist = Math.abs((candidate.y0 + candidate.y1) / 2 - cy);
        } else if (direction === "down") {
            overlap = Math.min(current.x1, candidate.x1) - Math.max(current.x0, candidate.x0);
            wrapDist = candidate.y0;
            secondaryDist = Math.abs((candidate.x0 + candidate.x1) / 2 - cx);
        } else {
            overlap = Math.min(current.x1, candidate.x1) - Math.max(current.x0, candidate.x0);
            wrapDist = 1 - candidate.y1;
            secondaryDist = Math.abs((candidate.x0 + candidate.x1) / 2 - cx);
        }

        if (overlap <= eps) continue;

        const score = wrapDist * 4 + secondaryDist;
        if (score < wrapBestScore) {
            wrapBestScore = score;
            wrapBestId = candidate.paneId;
        }
    }

    return wrapBestId;
}
