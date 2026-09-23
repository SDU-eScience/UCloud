import {TableColumn} from "@/UCX/protocol";

export interface UcxStreamedRowData {
    key: string;
    group: string;
    cells: string[];
}

export const UCX_MIN_COLUMN_WIDTH = 60;

const MAX_AUTO_COLUMN_WIDTH = 400;
const CELL_HORIZONTAL_PADDING = 24;
const RESIZE_HANDLE_SPACE = 8;
const HEADER_FONT = "500 14px 'Inter', sans-serif";
const CELL_FONT = "13px 'Inter', sans-serif";

const autoWidthCache = new Map<string, number>();
const userWidthCache = new Map<string, number>();

let measureContext: CanvasRenderingContext2D | null = null;

function measureText(text: string, font: string): number {
    if (measureContext === null) {
        measureContext = document.createElement("canvas").getContext("2d");
    }
    if (measureContext === null) return 0;
    measureContext.font = font;
    return measureContext.measureText(text).width;
}

export function computeColumnWidths(columns: TableColumn[], rows: UcxStreamedRowData[]): Record<string, number> {
    const widths: Record<string, number> = {};
    for (let i = 0; i < columns.length; i++) {
        const column = columns[i];
        const cached = autoWidthCache.get(column.key);
        if (cached !== undefined && cached >= MAX_AUTO_COLUMN_WIDTH) {
            widths[column.key] = cached;
            continue;
        }
        let widest = measureText(column.label, HEADER_FONT);
        for (const row of rows) {
            const cellWidth = measureText(row.cells[i] ?? "", CELL_FONT);
            if (cellWidth > widest) widest = cellWidth;
        }
        let width = Math.ceil(widest) + CELL_HORIZONTAL_PADDING + RESIZE_HANDLE_SPACE;
        if (width < UCX_MIN_COLUMN_WIDTH) width = UCX_MIN_COLUMN_WIDTH;
        if (width > MAX_AUTO_COLUMN_WIDTH) width = MAX_AUTO_COLUMN_WIDTH;
        const resolved = cached !== undefined && cached > width ? cached : width;
        autoWidthCache.set(column.key, resolved);
        widths[column.key] = resolved;
    }
    return widths;
}

export function resolveColumnWidths(columns: TableColumn[], autoWidths: Record<string, number>): Record<string, number> {
    const widths: Record<string, number> = {};
    for (const column of columns) {
        const user = userWidthCache.get(column.key);
        if (user !== undefined) {
            widths[column.key] = user;
            continue;
        }
        const auto = autoWidths[column.key];
        widths[column.key] = auto === undefined ? UCX_MIN_COLUMN_WIDTH : auto;
    }
    return widths;
}

export function setUserColumnWidth(columnKey: string, width: number): void {
    userWidthCache.set(columnKey, Math.max(UCX_MIN_COLUMN_WIDTH, Math.round(width)));
}

export function resetUserColumnWidth(columnKey: string): void {
    userWidthCache.delete(columnKey);
}

export function clearColumnWidthCache(): void {
    autoWidthCache.clear();
    userWidthCache.clear();
}
