import * as React from "react";
import {useState} from "react";
import {formatDate} from "date-fns";
import type {Cell, SheetData} from "write-excel-file/browser";
import {Button, Checkbox, Flex, Select, Text} from "@/ui-components";
import {Toggle} from "@/ui-components/Toggle";
import * as Heading from "@/ui-components/Heading";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {KeyboardNavigation} from "@/Applications/KeyboardNavigation";
import {dialogStore} from "@/Dialog/DialogStore";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {doNothing} from "@/UtilityFunctions";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import type {ProjectInfo} from "@/Project/InfoCache";
import {sendFailureNotification} from "@/Notifications";
import {injectStyle} from "@/Unstyled";
import * as SiteConfig from "../../../site.config.json";
import {productCategoryDisplayName} from "@/Accounting";
import {productWorkspaceInfo, rangeBounds, selectProducts, workspaceInfo} from "./Model";
import type {DateRange, Model, ProductAllocation, Status} from "./Model";

type ExportKind = "granted" | "received";
type ExportScope = "view" | "all";
type ExportFormat = "xlsx" | "csv" | "json" | "legacy-csv" | "legacy-json";
type ExportTable = "allocations" | "wallets";
type CellValue = string | number | boolean | null;
type Row = Record<string, CellValue>;

interface Column {
    key: string;
    label: string;
}

interface WorkbookSheet {
    sheet: string;
    data: SheetData;
    columns: {width: number}[];
    stickyRowsCount?: number;
}

interface SummaryProduct {
    product: string;
    provider: string;
    unit: string;
    activeQuota: number;
    activeUsage: number;
    total: number;
    totalUsed: number;
    upcoming: number;
    owners: number;
}

const dateKeys: ReadonlySet<string> = new Set(["start", "end", "firstStart", "lastEnd"]);
const statusOrder: Status[] = ["active", "expiring", "upcoming", "exhausted", "expired"];
const legacyReceivedDateFormat = "dd/MM/yyyy HH:mm:ss";

const ExportStyle = injectStyle("allocation-export", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
    }
    ${k} .export-body {
        display: flex;
        flex-direction: column;
        gap: 16px;
        flex: 1 1 auto;
        min-height: 0;
        overflow-y: auto;
        padding: 12px 4px 12px 0;
    }
    ${k} .export-columns {
        display: flex;
        flex-direction: column;
        gap: 10px;
    }
    ${k} .export-column-group {
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        padding: 10px 12px;
    }
    ${k} .export-column-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        margin-bottom: 8px;
    }
    ${k} .export-column-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
        gap: 2px 16px;
    }
    ${k} .export-column {
        display: flex;
        align-items: center;
        gap: 2px;
        cursor: pointer;
    }
    ${k} .export-link {
        border: 0;
        background: transparent;
        color: var(--primaryMain);
        cursor: pointer;
        padding: 0;
        font-size: 12px;
    }
`);

function allocationColumns(kind: ExportKind): Column[] {
    const owner: Column[] = kind === "granted" ? [
        {key: "workspace", label: "Workspace"},
        {key: "workspaceType", label: "Workspace type"},
        {key: "workspaceId", label: "Workspace ID"},
    ] : [
        {key: "workspace", label: "Source"},
        {key: "workspaceType", label: "Source type"},
        {key: "workspaceId", label: "Source ID"},
    ];
    return [
        ...owner,
        {key: "pi", label: "PI"},
        {key: "provider", label: "Provider"},
        {key: "product", label: "Product"},
        {key: "productType", label: "Product type"},
        {key: "accountingModel", label: "Accounting model"},
        {key: "unit", label: "Unit"},
        {key: "allocationId", label: "Allocation ID"},
        ...(kind === "received" ? [{key: "grantApplication", label: "Grant application"}] : []),
        {key: "start", label: "Start"},
        {key: "end", label: "End"},
        {key: "state", label: "State"},
        {key: "retired", label: "Retired"},
        {key: "quota", label: "Quota"},
        {key: "quotaRaw", label: "Quota (raw)"},
        {key: "used", label: "Used"},
    ];
}

function walletColumns(kind: ExportKind): Column[] {
    const owner: Column[] = kind === "granted" ? [
        {key: "workspace", label: "Workspace"},
        {key: "workspaceType", label: "Workspace type"},
        {key: "workspaceId", label: "Workspace ID"},
    ] : [
        {key: "workspace", label: "Source"},
        {key: "workspaceType", label: "Source type"},
        {key: "workspaceId", label: "Source ID"},
    ];
    return [
        ...owner,
        {key: "pi", label: "PI"},
        {key: "provider", label: "Provider"},
        {key: "product", label: "Product"},
        {key: "productType", label: "Product type"},
        {key: "unit", label: "Unit"},
        {key: "activeQuota", label: "Active quota"},
        {key: "activeUsage", label: "Active usage"},
        {key: "activeBalance", label: "Active balance"},
        {key: "totalGranted", label: kind === "granted" ? "Total granted" : "Total received"},
        {key: "totalUsed", label: "Total used"},
        {key: "retiredUsed", label: "Retired used"},
        {key: "expiredUnused", label: "Expired unused"},
        {key: "upcoming", label: "Upcoming quota"},
        {key: "states", label: "States"},
        {key: "allocations", label: "Allocations"},
        {key: "firstStart", label: "First start"},
        {key: "lastEnd", label: "Last end"},
    ];
}

function defaultChecks(columns: Column[]): Record<string, boolean> {
    return Object.fromEntries(columns.map(column => [column.key, true]));
}

function selectedColumns(columns: Column[], checked: Record<string, boolean>): Column[] {
    return columns.filter(column => checked[column.key] !== false);
}

function countAllocations(products: ProductAllocation[]): number {
    let count = 0;
    for (const product of products) {
        for (const grant of product.grants) count += grant.slices.length;
    }
    return count;
}

function workspaceOf(product: ProductAllocation, kind: ExportKind, projectInfo: Record<string, ProjectInfo | null>) {
    return kind === "granted"
        ? workspaceInfo(product.owner, projectInfo)
        : productWorkspaceInfo(product, projectInfo);
}

function buildAllocationRows(
    products: ProductAllocation[],
    kind: ExportKind,
    projectInfo: Record<string, ProjectInfo | null>,
    now: number,
): Row[] {
    const rows: Row[] = [];
    for (const product of products) {
        const workspace = workspaceOf(product, kind, projectInfo);
        for (const grant of product.grants) {
            for (const slice of grant.slices) {
                const state = slice.start > now ? "upcoming" : slice.end > now ? "active" : "expired";
                const used = slice.attribution === "recorded"
                    ? slice.used
                    : slice.estimatedUsed ?? null;
                rows.push({
                    workspace: workspace.title,
                    workspaceType: workspace.type,
                    workspaceId: workspace.id,
                    pi: workspace.pi,
                    provider: product.category.provider,
                    product: productCategoryDisplayName(product.category),
                    productType: product.category.productType,
                    accountingModel: product.category.accountingModel,
                    unit: product.unit,
                    allocationId: slice.allocationId,
                    grantApplication: grant.grantId ?? null,
                    start: slice.start,
                    end: slice.end,
                    state,
                    retired: slice.retired,
                    quota: slice.quota,
                    quotaRaw: slice.rawQuota,
                    used,
                });
            }
        }
    }
    return rows;
}

function buildWalletRows(
    products: ProductAllocation[],
    kind: ExportKind,
    projectInfo: Record<string, ProjectInfo | null>,
): Row[] {
    return products.map(product => {
        const workspace = workspaceOf(product, kind, projectInfo);
        return {
            workspace: workspace.title,
            workspaceType: workspace.type,
            workspaceId: workspace.id,
            pi: workspace.pi,
            provider: product.category.provider,
            product: productCategoryDisplayName(product.category),
            productType: product.category.productType,
            unit: product.unit,
            activeQuota: product.amounts.activeQuota,
            activeUsage: product.amounts.activeUsed,
            activeBalance: product.maxUsable ?? product.amounts.activeBalance,
            totalGranted: product.amounts.granted,
            totalUsed: product.amounts.used,
            retiredUsed: product.amounts.retiredUsed,
            expiredUnused: product.amounts.expiredUnused,
            upcoming: product.amounts.future,
            states: statusOrder.filter(status => product.states.has(status)).join(", "),
            allocations: countAllocations([product]),
            firstStart: Number.isFinite(product.start) ? product.start : null,
            lastEnd: Number.isFinite(product.end) ? product.end : null,
        };
    });
}

function buildSummary(products: ProductAllocation[]): SummaryProduct[] {
    const byProduct = new Map<string, {row: SummaryProduct; owners: Set<string>}>();
    for (const product of products) {
        const key = `${product.category.provider}/${product.category.name}`;
        let entry = byProduct.get(key);
        if (!entry) {
            entry = {
                row: {
                    product: productCategoryDisplayName(product.category),
                    provider: product.category.provider,
                    unit: product.unit,
                    activeQuota: 0,
                    activeUsage: 0,
                    total: 0,
                    totalUsed: 0,
                    upcoming: 0,
                    owners: 0,
                },
                owners: new Set(),
            };
            byProduct.set(key, entry);
        }
        entry.row.activeQuota += product.amounts.activeQuota;
        entry.row.activeUsage += product.amounts.activeUsed;
        entry.row.total += product.amounts.granted;
        entry.row.totalUsed += product.amounts.used;
        entry.row.upcoming += product.amounts.future;
        entry.owners.add(product.workspaceKey);
    }
    return [...byProduct.values()]
        .map(({row, owners}) => ({...row, owners: owners.size}))
        .sort((a, b) => a.provider.localeCompare(b.provider) || a.product.localeCompare(b.product));
}

function periodText(dateRange?: DateRange): string {
    const bounds = rangeBounds(dateRange);
    if (bounds.start === null && bounds.end === null) return "All time";
    if (bounds.start === null) return `Until ${formatDate(new Date(bounds.end!), "yyyy-MM-dd")}`;
    if (bounds.end === null) return `From ${formatDate(new Date(bounds.start), "yyyy-MM-dd")}`;
    return `${formatDate(new Date(bounds.start), "yyyy-MM-dd")} to ${formatDate(new Date(bounds.end), "yyyy-MM-dd")}`;
}

function periodJson(dateRange?: DateRange): {start: string | null; end: string | null} {
    const bounds = rangeBounds(dateRange);
    return {
        start: bounds.start === null ? null : formatDate(new Date(bounds.start), "yyyy-MM-dd"),
        end: bounds.end === null ? null : formatDate(new Date(bounds.end), "yyyy-MM-dd"),
    };
}

function periodFileText(dateRange?: DateRange): string {
    const bounds = rangeBounds(dateRange);
    if (bounds.start === null && bounds.end === null) return "all time";
    if (bounds.start === null) return `until ${formatDate(new Date(bounds.end!), "yyyy-MM-dd")}`;
    if (bounds.end === null) return `from ${formatDate(new Date(bounds.start), "yyyy-MM-dd")}`;
    return `${formatDate(new Date(bounds.start), "yyyy-MM-dd")} to ${formatDate(new Date(bounds.end), "yyyy-MM-dd")}`;
}

function sanitizeFileName(name: string): string {
    return name.replace(/[/\\:*?"<>|]/g, "_");
}

function exportFileName(
    kind: ExportKind,
    projectTitle: string,
    dateRange: DateRange | undefined,
    extension: string,
): string {
    const page = kind === "granted" ? "sub-allocations" : "received";
    return sanitizeFileName(`${projectTitle} - ${page} - ${periodFileText(dateRange)}.${extension}`);
}

function download(text: string, fileName: string, mimeType: string): void {
    const blob = new Blob([text], {type: mimeType});
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function csvString(value: string): string {
    return `"${value.replace(/"/g, '""')}"`;
}

function csvCell(column: Column, value: CellValue): string {
    if (value === null) return "";
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") {
        if (dateKeys.has(column.key)) return formatDate(new Date(value), "yyyy-MM-dd");
        return `${value}`;
    }
    return csvString(value);
}

function buildCsv(columns: Column[], rows: Row[]): string {
    const lines = [columns.map(column => csvString(column.label)).join(";")];
    for (const row of rows) {
        lines.push(columns.map(column => csvCell(column, row[column.key])).join(";"));
    }
    return "\uFEFF" + lines.join("\n") + "\n";
}

function jsonRow(columns: Column[], row: Row): Record<string, CellValue> {
    const result: Record<string, CellValue> = {};
    for (const column of columns) {
        const value = row[column.key];
        result[column.key] = typeof value === "number" && dateKeys.has(column.key)
            ? formatDate(new Date(value), "yyyy-MM-dd")
            : value;
    }
    return result;
}

function headerCell(label: string): Cell {
    return {value: label, fontWeight: "bold"};
}

function numberCell(value: number): Cell {
    return {type: Number, value, format: "#,##0.##"};
}

function toXlsxDate(epoch: number): Date {
    const date = new Date(epoch);
    return new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
}

function toXlsxCell(column: Column, value: CellValue): Cell {
    if (value === null) return null;
    if (dateKeys.has(column.key)) {
        return {type: Date, value: toXlsxDate(value as number), format: "yyyy-mm-dd"};
    }
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") return numberCell(value);
    return value;
}

function columnWidths(columns: Column[], rows: Row[]): {width: number}[] {
    return columns.map(column => {
        let width = column.label.length + 2;
        for (const row of rows.slice(0, 200)) {
            const value = row[column.key];
            const text = value === null ? "" : dateKeys.has(column.key) ? "2026-01-01" : `${value}`;
            width = Math.max(width, text.length + 2);
        }
        return {width: Math.min(48, Math.max(8, width))};
    });
}

function dataSheet(name: string, columns: Column[], rows: Row[]): WorkbookSheet {
    const data: SheetData = [
        columns.map(column => headerCell(column.label)),
        ...rows.map(row => columns.map(column => toXlsxCell(column, row[column.key]))),
    ];
    return {
        sheet: name,
        data,
        columns: columnWidths(columns, rows),
        stickyRowsCount: 1,
    };
}

function summarySheet(
    kind: ExportKind,
    projectTitle: string,
    scope: ExportScope,
    dateRange: DateRange | undefined,
    allocationCount: number,
    walletCount: number,
    summary: SummaryProduct[],
): WorkbookSheet {
    const span = 9;
    const metaRow = (text: string): Cell[] => [
        {value: text, columnSpan: span},
        ...Array<Cell>(span - 1).fill(null),
    ];
    const ownerLabel = kind === "granted" ? "Workspaces" : "Sources";
    const totalLabel = kind === "granted" ? "Total granted" : "Total received";
    const page = kind === "granted" ? "Sub-allocated" : "Received";
    const scopeLabel = scope === "view" ? "Current view" : kind === "granted" ? "All allocations" : "All received allocations";
    const generated = formatDate(new Date(), "yyyy-MM-dd HH:mm");
    const data: SheetData = [
        metaRow(`Project: ${projectTitle}`),
        metaRow(`Page: ${page}`),
        metaRow(`Scope: ${scopeLabel}`),
        metaRow(`Period: ${periodText(dateRange)}`),
        metaRow(`Generated: ${generated}`),
        metaRow(`Allocations: ${allocationCount}`),
        metaRow(`Wallets: ${walletCount}`),
        [],
        [
            headerCell("Product"),
            headerCell("Provider"),
            headerCell("Unit"),
            headerCell("Active quota"),
            headerCell("Active usage"),
            headerCell(totalLabel),
            headerCell("Total used"),
            headerCell("Upcoming"),
            headerCell(ownerLabel),
        ],
        ...summary.map(row => [
            row.product,
            row.provider,
            row.unit,
            numberCell(row.activeQuota),
            numberCell(row.activeUsage),
            numberCell(row.total),
            numberCell(row.totalUsed),
            numberCell(row.upcoming),
            numberCell(row.owners),
        ]),
    ];
    return {
        sheet: "Summary",
        data,
        columns: [
            {width: 24},
            {width: 12},
            {width: 14},
            {width: 13},
            {width: 13},
            {width: 14},
            {width: 11},
            {width: 12},
            {width: 12},
        ],
    };
}

interface LegacySubAllocation {
    id: number;
    grantApplication?: number;
    start: number;
    end: number;
    quota: number;
}

interface LegacySubAllocationRow {
    workspace: string;
    pi: string;
    category: string;
    provider: string;
    usage: number;
    quota: number;
    allocations: LegacySubAllocation[];
}

function legacySubAllocationColumns(maxAllocations: number): Column[] {
    const columns: Column[] = [
        {key: "workspace", label: "Workspace"},
        {key: "pi", label: "PI"},
        {key: "category", label: "Category"},
        {key: "provider", label: "Provider"},
        {key: "usage", label: "Usage"},
        {key: "quota", label: "Quota"},
    ];
    for (let n = 1; n <= maxAllocations; n++) {
        columns.push(
            {key: `allocation${n}_id`, label: `Allocation ${n} id`},
            {key: `allocation${n}_grantApplication`, label: `Allocation ${n} grantApplication`},
            {key: `allocation${n}_start`, label: `Allocation ${n} start`},
            {key: `allocation${n}_end`, label: `Allocation ${n} end`},
            {key: `allocation${n}_quota`, label: `Allocation ${n} quota`},
        );
    }
    return columns;
}

function buildLegacySubAllocationRows(
    products: ProductAllocation[],
    projectInfo: Record<string, ProjectInfo | null>,
): LegacySubAllocationRow[] {
    return products.map(product => {
        const workspace = workspaceInfo(product.owner, projectInfo);
        return {
            workspace: workspace.title,
            pi: workspace.pi,
            category: productCategoryDisplayName(product.category),
            provider: product.category.provider,
            usage: product.amounts.activeUsed,
            quota: product.amounts.activeQuota,
            allocations: product.grants.flatMap(grant => grant.slices.map(slice => ({
                id: slice.allocationId,
                grantApplication: grant.grantId,
                start: slice.start,
                end: slice.end,
                quota: slice.quota,
            }))),
        };
    });
}

function flattenLegacySubAllocationRows(
    rows: LegacySubAllocationRow[],
    maxAllocations: number,
): Record<string, string | number | undefined>[] {
    return rows.map(row => {
        const result: Record<string, string | number | undefined> = {
            workspace: row.workspace,
            pi: row.pi,
            category: row.category,
            provider: row.provider,
            usage: row.usage,
            quota: row.quota,
        };
        row.allocations.slice(0, maxAllocations).forEach((allocation, index) => {
            const n = index + 1;
            result[`allocation${n}_id`] = allocation.id;
            result[`allocation${n}_grantApplication`] = allocation.grantApplication;
            result[`allocation${n}_start`] = allocation.start;
            result[`allocation${n}_end`] = allocation.end;
            result[`allocation${n}_quota`] = allocation.quota;
        });
        return result;
    });
}

function legacyCsv(columns: Column[], rows: Record<string, string | number | undefined>[]): string {
    let text = columns.map(column => column.label).join(";") + "\n";
    for (const row of rows) {
        for (const [index, column] of columns.entries()) {
            let value = `${row[column.key]}`;
            if (value === "undefined" || value === "null") value = "";
            text += `"${value}"`;
            if (index !== columns.length - 1) text += ";";
        }
        text += "\n";
    }
    return text;
}

type LegacyReceivedRow = {
    product: string;
    provider: string;
    usage: number;
    quota: number;
    unit: string;
};

function buildLegacyReceivedRows(products: ProductAllocation[]): LegacyReceivedRow[] {
    const byProduct = new Map<string, {row: LegacyReceivedRow; consumption: boolean}>();
    for (const product of products) {
        const key = `${product.category.provider}/${product.category.name}`;
        let entry = byProduct.get(key);
        if (!entry) {
            entry = {
                row: {
                    product: productCategoryDisplayName(product.category),
                    provider: product.category.provider,
                    usage: 0,
                    quota: 0,
                    unit: product.unit,
                },
                consumption: product.category.accountingModel === "CONSUMPTION",
            };
            byProduct.set(key, entry);
        }
        entry.row.quota += product.amounts.activeQuota;
        entry.row.usage += product.amounts.activeUsed;
        if (entry.consumption) entry.row.usage -= product.amounts.retiredUsed;
    }
    return [...byProduct.values()]
        .map(entry => entry.row)
        .sort((a, b) => a.provider.localeCompare(b.provider) || a.product.localeCompare(b.product));
}

function legacyReceivedColumns(): Column[] {
    return [
        {key: "product", label: "Product"},
        {key: "provider", label: "Provider"},
        {key: "usage", label: "Usage"},
        {key: "quota", label: "Quota"},
        {key: "unit", label: "Unit"},
    ];
}

function legacyExport(
    kind: ExportKind,
    format: "csv" | "json",
    products: ProductAllocation[],
    projectInfo: Record<string, ProjectInfo | null>,
    projectTitle: string,
): void {
    if (kind === "granted") {
        const legacyRows = buildLegacySubAllocationRows(products, projectInfo);
        const maxAllocations = legacyRows.reduce((max, row) => Math.max(max, row.allocations.length), 0);
        if (format === "csv") {
            const columns = legacySubAllocationColumns(maxAllocations);
            const flattened = flattenLegacySubAllocationRows(legacyRows, maxAllocations);
            download(legacyCsv(columns, flattened), "sub-projects-export.csv", "text/csv");
            return;
        }
        const payload = legacyRows.map(row => ({
            workspace: row.workspace,
            pi: row.pi,
            category: row.category,
            provider: row.provider,
            usage: row.usage,
            quota: row.quota,
        }));
        download(JSON.stringify(payload), "sub-projects-export.json", "application/json");
        return;
    }

    const columns = legacyReceivedColumns();
    const rows = buildLegacyReceivedRows(products);
    const fileName = `${SiteConfig.PRODUCT_NAME} - ${projectTitle} - ${formatDate(new Date(), legacyReceivedDateFormat)}`;
    if (format === "csv") {
        download(legacyCsv(columns, rows), `${fileName}.csv`, "text/csv");
        return;
    }
    download(JSON.stringify(rows), `${fileName}.json`, "application/json");
}

interface ExportDialogProps {
    kind: ExportKind;
    visibleProducts: ProductAllocation[];
    allProducts: ProductAllocation[];
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange?: DateRange;
    now: number;
}

function ExportDialog(props: ExportDialogProps): React.ReactNode {
    const projectId = useProjectId();
    const project = useProject().fetch();
    const projectTitle = project.specification.title;
    const kind = props.kind;
    const allocations = allocationColumns(kind);
    const wallets = walletColumns(kind);
    const [scope, setScope] = useState<ExportScope>("view");
    const [format, setFormat] = useState<ExportFormat>("xlsx");
    const [csvTable, setCsvTable] = useState<ExportTable>("allocations");
    const [showColumns, setShowColumns] = useState(false);
    const [busy, setBusy] = useState(false);
    const [allocationChecked, setAllocationChecked] = useState(() => defaultChecks(allocations));
    const [walletChecked, setWalletChecked] = useState(() => defaultChecks(wallets));
    const legacy = format === "legacy-csv" || format === "legacy-json";
    const products = scope === "view" ? props.visibleProducts : props.allProducts;
    const allocationCount = countAllocations(products);
    const walletCount = products.length;
    const title = kind === "granted" ? "Export allocations" : "Export received allocations";
    const scopeDescription = kind === "granted"
        ? "Current view uses the rows visible in the table and the period above it. All allocations ignores the search and filter bar and uses the same period."
        : "Current view uses the rows visible in the table and the period above it. All received allocations ignores the search and filter bar and uses the same period.";
    const formatDescription = legacy
        ? "The original pre-redesign layout with its fixed compatibility columns."
        : format === "xlsx"
            ? "Allocations, wallets and a summary sheet."
            : format === "csv"
                ? "A single table, separated by semicolons."
                : "All data in one document.";
    const showAllocationColumns = !legacy && (format !== "csv" || csvTable === "allocations");
    const showWalletColumns = !legacy && (format !== "csv" || csvTable === "wallets");
    const canExport = products.length > 0 && (legacy || format !== "csv" ||
        selectedColumns(csvTable === "allocations" ? allocations : wallets,
            csvTable === "allocations" ? allocationChecked : walletChecked).length > 0);

    const toggleColumn = (table: ExportTable, key: string) => {
        if (table === "allocations") {
            setAllocationChecked(current => ({...current, [key]: current[key] === false}));
        } else {
            setWalletChecked(current => ({...current, [key]: current[key] === false}));
        }
    };

    const setAllColumns = (table: ExportTable, value: boolean) => {
        if (table === "allocations") {
            setAllocationChecked(Object.fromEntries(allocations.map(column => [column.key, value])));
        } else {
            setWalletChecked(Object.fromEntries(wallets.map(column => [column.key, value])));
        }
    };

    const onExport = async () => {
        setBusy(true);
        try {
            if (format === "legacy-csv" || format === "legacy-json") {
                legacyExport(kind, format === "legacy-json" ? "json" : "csv", products, props.projectInfo,
                    projectTitle);
            } else if (format === "csv") {
                const columns = selectedColumns(csvTable === "allocations" ? allocations : wallets,
                    csvTable === "allocations" ? allocationChecked : walletChecked);
                const rows = csvTable === "allocations"
                    ? buildAllocationRows(products, kind, props.projectInfo, props.now)
                    : buildWalletRows(products, kind, props.projectInfo);
                download(buildCsv(columns, rows), exportFileName(kind, projectTitle, props.dateRange, "csv"),
                    "text/csv;charset=utf-8");
            } else if (format === "json") {
                const allocationSelection = selectedColumns(allocations, allocationChecked);
                const walletSelection = selectedColumns(wallets, walletChecked);
                const summary = buildSummary(products);
                const totalKey = kind === "granted" ? "totalGranted" : "totalReceived";
                const ownerKey = kind === "granted" ? "workspaces" : "sources";
                const payload = {
                    project: {id: projectId ?? null, title: projectTitle},
                    page: kind === "granted" ? "sub-allocated" : "received",
                    scope: scope === "view" ? "current-view" : "all",
                    period: periodJson(props.dateRange),
                    generatedAt: new Date().toISOString(),
                    allocations: allocationSelection.length === 0 ? [] :
                        buildAllocationRows(products, kind, props.projectInfo, props.now)
                            .map(row => jsonRow(allocationSelection, row)),
                    wallets: walletSelection.length === 0 ? [] :
                        buildWalletRows(products, kind, props.projectInfo)
                            .map(row => jsonRow(walletSelection, row)),
                    summary: {
                        allocations: allocationCount,
                        wallets: walletCount,
                        products: summary.map(row => ({
                            product: row.product,
                            provider: row.provider,
                            unit: row.unit,
                            activeQuota: row.activeQuota,
                            activeUsage: row.activeUsage,
                            [totalKey]: row.total,
                            totalUsed: row.totalUsed,
                            upcoming: row.upcoming,
                            [ownerKey]: row.owners,
                        })),
                    },
                };
                download(JSON.stringify(payload), exportFileName(kind, projectTitle, props.dateRange, "json"),
                    "application/json");
            } else {
                const allocationSelection = selectedColumns(allocations, allocationChecked);
                const walletSelection = selectedColumns(wallets, walletChecked);
                const sheets: WorkbookSheet[] = [];
                if (allocationSelection.length > 0) {
                    sheets.push(dataSheet("Allocations", allocationSelection,
                        buildAllocationRows(products, kind, props.projectInfo, props.now)));
                }
                if (walletSelection.length > 0) {
                    sheets.push(dataSheet("Wallets", walletSelection,
                        buildWalletRows(products, kind, props.projectInfo)));
                }
                sheets.push(summarySheet(kind, projectTitle, scope, props.dateRange, allocationCount, walletCount,
                    buildSummary(products)));
                const {default: writeXlsxFile} = await import("write-excel-file/browser");
                await writeXlsxFile(sheets, {}).toFile(
                    exportFileName(kind, projectTitle, props.dateRange, "xlsx"));
            }
            dialogStore.success();
        } catch (error) {
            sendFailureNotification("Could not generate the export.");
        } finally {
            setBusy(false);
        }
    };

    const renderColumnGroup = (
        table: ExportTable,
        label: string,
        columns: Column[],
        checked: Record<string, boolean>,
    ): React.ReactNode =>
        <div className="export-column-group">
            <div className="export-column-head">
                <Text fontWeight="bold">{label}</Text>
                <span>
                    <button type="button" className="export-link" onClick={() => setAllColumns(table, true)}>All</button>
                    <span> / </span>
                    <button type="button" className="export-link" onClick={() => setAllColumns(table, false)}>None</button>
                </span>
            </div>
            <div className="export-column-grid">
                {columns.map(column =>
                    <label key={column.key} className="export-column">
                        <Checkbox checked={checked[column.key] !== false}
                            onChange={() => toggleColumn(table, column.key)} />
                        <Text fontSize="12px">{column.label}</Text>
                    </label>)}
            </div>
        </div>;

    return <div className={ExportStyle}>
        <Heading.h3>{title}</Heading.h3>
        <KeyboardNavigation className="export-body">
            <FieldGroup>
                <FieldRow
                    title="Scope"
                    description={scopeDescription}
                    required
                    control={<Select value={scope} onChange={event => setScope(event.target.value as ExportScope)}>
                        <option value="view">Current view</option>
                        <option value="all">{kind === "granted" ? "All allocations" : "All received allocations"}</option>
                    </Select>}
                />
                <FieldRow
                    title="Format"
                    description={formatDescription}
                    required
                    control={<Select value={format} onChange={event => setFormat(event.target.value as ExportFormat)}>
                        <option value="xlsx">Excel (.xlsx)</option>
                        <option value="csv">CSV</option>
                        <option value="json">JSON</option>
                        <optgroup label="Legacy (pre-redesign)">
                            <option value="legacy-csv">Legacy CSV</option>
                            <option value="legacy-json">Legacy JSON</option>
                        </optgroup>
                    </Select>}
                />
                {format === "csv" ? <FieldRow
                    title="Table"
                    description="Choose which table the CSV contains."
                    required
                    control={<Select value={csvTable}
                        onChange={event => setCsvTable(event.target.value as ExportTable)}>
                        <option value="allocations">Allocations</option>
                        <option value="wallets">Wallets</option>
                    </Select>}
                /> : null}
                {legacy ? null : <FieldRow
                    title="Columns"
                    description="All columns are included by default. Turn this on to choose individual columns."
                    control={<Toggle checked={showColumns}
                        onChange={previous => setShowColumns(!previous)} ariaLabel="Choose columns" />}
                />}
            </FieldGroup>
            {showColumns && !legacy ? <div className="export-columns">
                {showAllocationColumns ? renderColumnGroup("allocations",
                    format === "csv" ? "Allocations table" : format === "json" ? "All allocations" : "Allocations sheet",
                    allocations, allocationChecked) : null}
                {showWalletColumns ? renderColumnGroup("wallets",
                    format === "csv" ? "Wallets table" : format === "json" ? "All wallets" : "Wallets sheet",
                    wallets, walletChecked) : null}
            </div> : null}
        </KeyboardNavigation>
        <Flex justifyContent="end" px="20px" py="12px" margin="auto -20px -20px" background="var(--dialogToolbar)"
            gap="8px" flexShrink={0}>
            <Button color="errorMain" onClick={() => dialogStore.failure()}>Cancel</Button>
            <Button color="successMain" disabled={!canExport || busy} onClick={() => void onExport()}>
                {busy ? "Preparing…" : "Export"}
            </Button>
        </Flex>
    </div>;
}

export function openExportDialog(props: {
    kind: ExportKind;
    model: Model;
    visibleProducts: ProductAllocation[];
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange?: DateRange;
    personalWorkspaces: boolean;
}): void {
    const allProducts = selectProducts(
        props.model,
        {
            dateRange: props.dateRange,
            personalWorkspaces: props.kind === "received" ? true : props.personalWorkspaces,
            projectInfo: props.projectInfo,
        },
        props.kind === "granted" ? props.model.products : props.model.receivedProducts,
    );
    dialogStore.addDialog(
        <ExportDialog
            kind={props.kind}
            visibleProducts={props.visibleProducts}
            allProducts={allProducts}
            projectInfo={props.projectInfo}
            dateRange={props.dateRange}
            now={props.model.now}
        />,
        doNothing,
        true,
        largeModalStyle,
    );
}
