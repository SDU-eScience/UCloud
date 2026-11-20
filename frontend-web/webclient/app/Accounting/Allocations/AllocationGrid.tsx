import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {NO_EXPIRATION_FALLBACK, productTypeToIcon} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {UcxStreamedTable, UcxTableIconButtonClass, UcxTableStore} from "@/UCX/UcxBrowser";
import type {
    UcxTableActionDef,
    UcxTableActionEvent,
    UcxTableActivationEvent,
    UcxTableCellRenderContext,
    UcxTableSelection,
} from "@/UCX/UcxBrowser";
import {Button, Icon, Link} from "@/ui-components";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import type {ProjectInfo} from "@/Project/InfoCache";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {format} from "date-fns";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {copyToClipboard} from "@/UtilityFunctions";
import {allocationCompareProducts} from "./AllocationPresentation";
import {AllocationFilters} from "./AllocationFilters";
import type {AllocationFilterState} from "./AllocationFilters";
import {AllocationAmount, AllocationLifecycleAmount, AllocationWorkspaceLabel, allocationLifecycleCells, allocationLifecycleColumns, allocationLifecycleMetrics, allocationLifecycleWidths} from "./AllocationCells";
import {allocationFilterProductsByState, allocationModelRecipients, allocationSelectProducts, allocationWorkspaceInfo} from "./AllocationModel";
import type {AllocationDateRange, AllocationModel, AllocationModelProduct, AllocationModelRecipient, AllocationModelSlice} from "./AllocationModel";
export {allocationDateRangeBounds, allocationOverlapsRange} from "./AllocationModel";
export type {AllocationDateRange} from "./AllocationModel";

const DAY_MS = 24 * 60 * 60 * 1000;
const EXPIRING_WINDOW_MS = 30 * DAY_MS;

const COLUMN_KEYS = {
    workspace: "allocation.workspace",
    product: "allocation.product",
    period: "allocation.period",
    quota: "allocation.quota",
} as const;

const COLUMN_WIDTHS: Record<string, number> = {
    [COLUMN_KEYS.workspace]: 150,
    [COLUMN_KEYS.product]: 175,
    [COLUMN_KEYS.period]: 164,
    ...allocationLifecycleWidths,
    [COLUMN_KEYS.quota]: 102,
};

const ALL_COLUMNS: TableColumn[] = [
    {key: COLUMN_KEYS.workspace, label: "Workspace", jsonPath: "workspace", sortType: TableColumnSortType.Text, alignment: "left"},
    {key: COLUMN_KEYS.product, label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text, alignment: "left"},
    {key: COLUMN_KEYS.period, label: "Dates", jsonPath: "period", sortType: TableColumnSortType.Text, alignment: "left"},
    ...allocationLifecycleColumns.filter(column => column.key.startsWith("active")),
    ...allocationLifecycleColumns.filter(column => column.key === "used"),
    {key: COLUMN_KEYS.quota, label: "Total granted", jsonPath: "quota", sortType: TableColumnSortType.Number, alignment: "right"},
    ...allocationLifecycleColumns.filter(column => column.key === "expiredUnused" || column.key === "future"),
];

const advancedColumns = new Set(["expiredUnused", "future"]);
const columnOptions = ALL_COLUMNS.filter(column => column.key !== COLUMN_KEYS.workspace)
    .map(({key, label}) => ({key, label, advanced: advancedColumns.has(key)}))
    .sort((a, b) => Number(a.advanced) - Number(b.advanced));
const defaultColumns = columnOptions.filter(column => !column.advanced).map(column => column.key);

export interface AllocationEditRequest {
    category: ProductCategoryV2;
    allocationId: number;
    start: number;
    end: number;
    quota: number;
    workspaceTitle: string;
}

interface AllocationGridProps {
    model: AllocationModel;
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange?: AllocationDateRange;
    selectedCategories?: string[];
    onCategoriesChange?: (categories: string[]) => void;
    searchRef?: React.RefObject<HTMLInputElement | null>;
    personalWorkspaces: boolean;
    onPersonalWorkspacesChange: () => void;
    onClearFilters?: () => void;
    toolbar?: React.ReactNode;
    onAllocate?: (recipients: AllocationModelRecipient[]) => void;
    periodControl?: React.ReactNode;
    emptyMessage?: string;
    onEdit?: (request: AllocationEditRequest) => void;
    onRetire?: (allocationIds: number[], workspaceTitle: string, grantId?: number) => void;
    onOpenGrant?: (grantId: number) => void;
    renderGrantLink?: (grantId: number) => React.ReactNode;
    onVisibleProductsChange?: (products: AllocationModelProduct[]) => void;
}

interface AllocationGridRow {
    key: string;
    group: string;
    category: ProductCategoryV2;
    start: number;
    end: number;
    grantId?: number;
    workspaceTitle: string;
    workspaceKey: string;
    workspacePi: string;
    workspaceType: "project" | "user";
    productName: string;
    provider: string;
    unit: string;
    allocationQuota: number;
    accounting: AllocationModelProduct;
    slices: AllocationModelSlice[];
    usage?: AllocationRowUsage;
}

interface AllocationRowUsage {
    value: number;
    approximate: boolean;
}

function allocationSliceUsage(slice: AllocationModelSlice): AllocationRowUsage | undefined {
    if (slice.estimatedUsed !== undefined) return {value: slice.estimatedUsed, approximate: true};
    if (slice.attribution === "recorded" && slice.used !== null) return {value: slice.used, approximate: false};
    return undefined;
}

interface AllocationProductRow extends AllocationGridRow {
    allocations: AllocationGridRow[];
}

type AllocationDisplayRow =
    | {kind: "product"; product: AllocationProductRow}
    | {kind: "grant"; grant: AllocationGridRow}
    | {kind: "allocation"; grant: AllocationGridRow; allocation: AllocationModelSlice};

function allocationSliceKey(allocationKey: string, slice: AllocationModelSlice): string {
    return `${allocationKey}/slice:${slice.allocationId}`;
}

function allocationGrantRowKey(productKey: string, grant: {key: string; grantId?: number}): string {
    return grant.grantId === undefined ? grant.key : `${productKey}/grant:${grant.grantId}`;
}

function allocationCanRetire(allocation: AllocationModelSlice, now: number): boolean {
    return !allocation.retired && allocation.end > now;
}

function allocationPeriodCell(now: number, start: number, end: number, precise = false, retired = false): React.ReactNode {
    const expired = retired || end < now;
    const upcoming = !expired && start > now;
    const noEnd = end >= NO_EXPIRATION_FALLBACK - DAY_MS;
    const span = end - start;
    const percentage = span <= 0 ? 100 : Math.max(0, Math.min(100, ((now - start) / span) * 100));
    const days = Math.max(1, Math.round(Math.abs(end - now) / DAY_MS));
    const months = Math.max(1, Math.round(days / 30));
    const duration = days < 60 ? `${days} day${days === 1 ? "" : "s"}` : `${months} month${months === 1 ? "" : "s"}`;
    let note = `${duration} left`;
    if (expired) note = "Expired";
    else if (upcoming) note = "Upcoming";
    else if (noEnd) note = "No end";
    const expiring = !expired && !upcoming && end - now <= EXPIRING_WINDOW_MS;
    return <div className="allocation-period" data-state={expired ? "expired" : expiring ? "expiring" : "active"}>
        <div className="allocation-period-dates">
            <span>{format(start, precise ? "d MMM yyyy" : "MMM yyyy")}</span>
            <Icon name="heroArrowRight" size={9} />
            <span>{noEnd ? "No end" : format(end, precise ? "d MMM yyyy" : "MMM yyyy")}</span>
        </div>
        <div className="allocation-period-bottom">
            <div className="allocation-period-track">
                <span className="allocation-period-elapsed" style={{width: `${percentage}%`}} />
                {!upcoming && !expired ? <span className="allocation-period-now" style={{left: `${percentage}%`}} /> : null}
            </div>
            <span className="allocation-period-note">{note}</span>
        </div>
    </div>;
}

const AllocationGridClass = injectStyle("allocation-grid", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
        min-width: 0;
    }

    ${k} .allocation-expand {
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        width: 14px;
        height: 20px;
        padding: 0;
        border: 0;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
    }

    ${k} .allocation-indent {
        flex-shrink: 0;
        width: 14px;
    }

    ${k} .allocation-icon-slot {
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        width: 14px;
    }

    ${k} [data-has-selection="true"] .streamed-table-scroll tr > :first-child {
        border-right: 0;
    }

    ${k} [data-has-selection="true"] .streamed-table-scroll tr > :nth-child(2) {
        border-left: 0;
    }

    ${k} .streamed-table-scroll td:has(> .allocation-group-rail) {
        position: relative;
        overflow: visible;
    }

    ${k} .allocation-group-rail {
        position: absolute;
        inset: -1px auto -1px 8px;
        width: 20px;
        pointer-events: none;
    }

    ${k} .allocation-group-rail::before,
    ${k} .allocation-group-rail::after {
        content: "";
        position: absolute;
        left: 0;
        border-color: var(--textPrimary);
        border-style: solid;
        border-width: 0;
    }

    ${k} .allocation-group-rail::before {
        top: 0;
        bottom: 0;
        border-left-width: 1px;
    }

    ${k} .allocation-group-rail::after {
        width: 100%;
        top: 50%;
        border-top-width: 1px;
    }

    ${k} .allocation-group-rail-last::before { bottom: 50%; }

    ${k} .allocation-workspace-meta {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        color: var(--textSecondary);
        font-size: 9.5px;
        white-space: nowrap;
    }

    ${k} .allocation-product {
        display: flex;
        align-items: center;
        justify-content: flex-start;
        text-align: left;
        gap: 5px;
        min-width: 0;
        white-space: nowrap;
    }

    ${k} .allocation-product-name {
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .allocation-product-meta {
        overflow: hidden;
        color: var(--textSecondary);
        text-overflow: ellipsis;
        margin-left: 8px;
    }

    ${k} .allocation-child-label, ${k} .allocation-slice-label {
        display: flex;
        align-items: center;
        gap: 5px;
    }

    ${k} .allocation-slice-label .allocation-workspace-meta {
        margin-left: 8px;
    }

    ${k} .allocation-slice-label .allocation-id-copy {
        flex: 0 0 24px;
        width: 24px;
        height: 24px;
        opacity: 0;
    }
    ${k} tr:hover .allocation-id-copy,
    ${k} tr[data-selected="true"] .allocation-id-copy,
    ${k} .allocation-id-copy:focus-visible { opacity: 1; }

    ${k} .allocation-period {
        display: flex;
        flex-direction: column;
        gap: 3px;
        min-width: 145px;
    }

    ${k} .allocation-period-dates {
        display: flex;
        align-items: center;
        gap: 5px;
        color: var(--textPrimary);
        font-size: 10.5px;
        white-space: nowrap;
    }

    ${k} .allocation-period-bottom {
        display: flex;
        align-items: center;
        gap: 6px;
    }

    ${k} .allocation-period-track {
        position: relative;
        flex: 1 1 auto;
        height: 3px;
        border-radius: 2px;
        background: var(--gray-20);
    }

    ${k} .allocation-period-elapsed {
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        border-radius: 2px;
        background: var(--primaryMain);
    }

    ${k} .allocation-period[data-state="expired"] .allocation-period-elapsed {
        background: var(--gray-50);
    }

    ${k} .allocation-period[data-state="expiring"] .allocation-period-elapsed {
        background: var(--warningMain);
    }

    ${k} .allocation-period-now {
        position: absolute;
        top: -2px;
        width: 1px;
        height: 7px;
        background: var(--textPrimary);
    }

    ${k} .allocation-period-note {
        color: var(--textSecondary);
        font-size: 9px;
        white-space: nowrap;
    }

`);

export function AllocationGrid(props: AllocationGridProps): React.ReactNode {
    const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set());
    const [selected, setSelected] = useState<ReadonlySet<string>>(() => new Set());
    const gridRef = useRef<HTMLDivElement>(null);
    const store = useMemo(() => new UcxTableStore(), []);
    useLayoutEffect(() => {
        store.setSort("allocation-grid", {columnKey: COLUMN_KEYS.workspace, ascending: true});
    }, [store]);
    const [query, setQuery] = useState("");
    const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
    const [selectedProviders, setSelectedProviders] = useState<string[]>([]);
    const [visibleColumns, setVisibleColumns] = useState<string[]>(defaultColumns);
    const [filterState, setFilterState] = useState<AllocationFilterState>("all");
    const localSearchRef = useRef<HTMLInputElement>(null);
    const searchRef = props.searchRef ?? localSearchRef;
    const productKeys = props.selectedCategories ?? selectedProducts;
    const setProductKeys = props.onCategoriesChange ?? setSelectedProducts;

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            const target = event.target as HTMLElement | null;
            if (target?.closest("input, textarea, select, [contenteditable='true']")) return;
            if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
            event.preventDefault();
            searchRef.current?.focus();
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [searchRef]);

    const matchingProducts = useMemo(() => allocationSelectProducts(props.model, {
        dateRange: props.dateRange,
        personalWorkspaces: props.personalWorkspaces,
        categories: productKeys,
        providers: selectedProviders,
        query,
        projectInfo: props.projectInfo,
    }), [props.model, props.dateRange, props.personalWorkspaces, productKeys, selectedProviders, query, props.projectInfo]);

    const visibleProducts = useMemo(() => allocationFilterProductsByState(matchingProducts, filterState, props.model.now),
        [matchingProducts, filterState, props.model.now]);
    const retireableByGrant = useMemo(() => {
        const result = new Map<string, AllocationModelSlice[]>();
        for (const product of matchingProducts) {
            for (const grant of product.grants) {
                const allocations = grant.slices.filter(slice => allocationCanRetire(slice, props.model.now));
                if (!allocations.length) continue;
                const key = allocationGrantRowKey(product.key, grant);
                const existing = result.get(key) ?? [];
                existing.push(...allocations);
                result.set(key, existing);
            }
        }
        return result;
    }, [matchingProducts, props.model.now]);

    useEffect(() => {
        props.onVisibleProductsChange?.(visibleProducts);
    }, [visibleProducts, props.onVisibleProductsChange]);

    const rows = useMemo((): AllocationProductRow[] => visibleProducts.map(product => {
        const workspace = allocationWorkspaceInfo(product.owner, props.projectInfo);
        const grantsByKey = new Map<string, AllocationGridRow>();
        for (const grant of product.grants) {
            const key = allocationGrantRowKey(product.key, grant);
            const existing = grantsByKey.get(key);
            if (existing) {
                existing.start = Math.min(existing.start, grant.start);
                existing.end = Math.max(existing.end, grant.end - 1);
                existing.allocationQuota += grant.quota;
                existing.slices.push(...grant.slices);
                continue;
            }
            grantsByKey.set(key, {
                key,
                group: `${workspace.title}\u0000${product.workspaceKey}`,
                category: product.category,
                start: grant.start,
                end: grant.end - 1,
                grantId: grant.grantId,
                workspaceTitle: workspace.title,
                workspaceKey: product.workspaceKey,
                workspacePi: workspace.pi,
                workspaceType: workspace.type,
                productName: product.category.name,
                provider: product.category.provider,
                unit: product.unit,
                allocationQuota: grant.quota,
                accounting: product,
                slices: [...grant.slices],
            });
        }
        const allocations = [...grantsByKey.values()];
        for (const grant of allocations) {
            grant.slices.sort((a, b) => a.start - b.start || a.allocationId - b.allocationId);
            for (const slice of grant.slices) {
                const usage = allocationSliceUsage(slice);
                if (!usage) continue;
                if (grant.usage) {
                    grant.usage.value += usage.value;
                    grant.usage.approximate ||= usage.approximate;
                } else {
                    grant.usage = usage;
                }
            }
        }
        return {
            ...allocations[0],
            key: product.key,
            start: product.start,
            end: product.end - 1,
            allocationQuota: product.amounts.granted,
            allocations,
        };
    }), [visibleProducts, props.projectInfo]);

    const products = useMemo(() => {
        const options = new Map<string, ProductCategoryV2>();
        for (const product of props.model.products) {
            options.set(`${product.category.provider}/${product.category.name}`, product.category);
        }
        return [...options.values()].sort(allocationCompareProducts).map(category => ({
            key: `${category.provider}/${category.name}`,
            label: `${category.name} (${getShortProviderTitle(category.provider)})`,
            icon: <Icon name={productTypeToIcon(category.productType)} size={16} />,
        }));
    }, [props.model]);

    const providers = useMemo(() => {
        const keys = new Set(props.model.products.map(product => product.category.provider));
        return [...keys].map(key => ({
            key,
            label: getShortProviderTitle(key),
            icon: <ProviderLogo providerId={key} size={18} />,
        })).sort((a, b) => a.label.localeCompare(b.label));
    }, [props.model]);
    const counts = useMemo(() => {
        const workspaces: Record<AllocationFilterState, Set<string>> = {
            all: new Set(),
            active: new Set(),
            expiring: new Set(),
            upcoming: new Set(),
            exhausted: new Set(),
            expired: new Set(),
        };
        for (const product of matchingProducts) {
            workspaces.all.add(product.workspaceKey);
            for (const state of product.states) workspaces[state].add(product.workspaceKey);
        }
        return {
            all: workspaces.all.size,
            active: workspaces.active.size,
            expiring: workspaces.expiring.size,
            upcoming: workspaces.upcoming.size,
            exhausted: workspaces.exhausted.size,
            expired: workspaces.expired.size,
        };
    }, [matchingProducts]);

    const rowByKey = useMemo(() => {
        const result = new Map<string, AllocationDisplayRow>();
        for (const row of rows) {
            result.set(row.key, {kind: "product", product: row});
            for (const allocation of row.allocations) {
                result.set(allocation.key, {kind: "grant", grant: allocation});
                allocation.slices.forEach(slice => {
                    result.set(allocationSliceKey(allocation.key, slice), {kind: "allocation", grant: allocation, allocation: slice});
                });
            }
        }
        return result;
    }, [rows]);

    const treeParents = useMemo(() => {
        const result = new Map<string, string>();
        const firstProductByGroup = new Map<string, string>();
        for (const row of rows) {
            const firstProduct = firstProductByGroup.get(row.group);
            if (firstProduct === undefined) firstProductByGroup.set(row.group, row.key);
            else result.set(row.key, firstProduct);
            for (const allocation of row.allocations) {
                result.set(allocation.key, row.key);
                allocation.slices.forEach(slice => result.set(allocationSliceKey(allocation.key, slice), allocation.key));
            }
        }
        return result;
    }, [rows]);

    const onColumnsChange = (next: string[]) => {
        setVisibleColumns(next);
        const sort = store.sortFor("allocation-grid");
        if (sort && sort.columnKey !== COLUMN_KEYS.workspace && !next.includes(sort.columnKey)) {
            store.setSort("allocation-grid", {columnKey: COLUMN_KEYS.workspace, ascending: true});
        }
    };

    const visibleColumnIndexes = useMemo(() => ALL_COLUMNS.flatMap((column, index) =>
        column.key === COLUMN_KEYS.workspace || visibleColumns.includes(column.key) ? [index] : []
    ), [visibleColumns]);
    const columns = useMemo(() => visibleColumnIndexes.map(index => ALL_COLUMNS[index]), [visibleColumnIndexes]);

    const {tableRows, rowChildren} = useMemo(() => {
        const tableByKey = new Map<string, TableRow>();
        for (const [key, entry] of rowByKey) {
            const row = entry.kind === "product" ? entry.product : entry.grant;
            const allocation = entry.kind === "allocation" ? entry.allocation : undefined;
            const usage = allocation ? allocationSliceUsage(allocation) : row.usage;
            const end = allocation ? Math.max(allocation.start, allocation.end - 1) : row.end;
            const quota = allocation?.quota ?? row.allocationQuota;
            const cells: Record<string, string> = {
                [COLUMN_KEYS.workspace]: `${row.workspaceTitle} ${row.workspacePi}`,
                [COLUMN_KEYS.product]: row.productName,
                [COLUMN_KEYS.period]: `${allocation?.start ?? row.start} ${end}`,
                [COLUMN_KEYS.quota]: `${quota}`,
                ...(entry.kind === "product" ? allocationLifecycleCells(row.accounting.amounts, row.category.accountingModel === "CONSUMPTION", row.accounting.maxUsable) :
                    {used: usage ? `${usage.value}` : ""}),
            };
            tableByKey.set(key, {
                key,
                group: row.group,
                cells: columns.map(column => cells[column.key] ?? ""),
                actions: entry.kind === "product" ? (props.onAllocate ? [{id: "allocate", enabled: true}] : []) :
                    entry.kind === "grant" ? [
                        ...(row.grantId !== undefined ? [{id: "viewGrant", enabled: true}] : []),
                        ...(props.onRetire && retireableByGrant.has(row.key) ? [{id: "retire", enabled: true}] : []),
                    ] : allocationCanRetire(entry.allocation, props.model.now) ? [
                        ...(props.onRetire ? [{id: "retire", enabled: true}] : []),
                        ...(props.onEdit ? [{id: "edit", enabled: true}] : []),
                    ] : [],
            });
        }
        const children = new Map<string, TableRow[]>();
        for (const row of rows) {
            children.set(row.key, row.allocations.map(allocation => tableByKey.get(allocation.key)!));
            for (const allocation of row.allocations) {
                children.set(allocation.key, allocation.slices.map(slice =>
                    tableByKey.get(allocationSliceKey(allocation.key, slice))!
                ));
            }
        }
        return {
            tableRows: rows.map(row => tableByKey.get(row.key)!),
            rowChildren: children,
        };
    }, [rows, rowByKey, props.model.now, columns, retireableByGrant, props.onAllocate, props.onEdit, props.onRetire]);

    useLayoutEffect(() => {
        store.apply({
            tableId: "allocation-grid",
            revision: 0,
            snapshot: true,
            columns,
            upserts: tableRows,
            removed: [],
        });
    }, [store, columns, tableRows]);

    useEffect(() => {
        setSelected(previous => {
            const next = new Set([...previous].filter(key => rowByKey.get(key)?.kind === "product"));
            return next.size === previous.size ? previous : next;
        });
    }, [rowByKey]);

    const selectedRecipients = useMemo(() => {
        const workspaces = new Set<string>();
        for (const row of rows) {
            if (selected.has(row.key)) workspaces.add(row.workspaceKey);
        }
        return allocationModelRecipients(props.model, workspaces);
    }, [rows, selected, props.model]);

    const selection: UcxTableSelection = {selected, onChange: setSelected};

    const actionDefs = useMemo((): UcxTableActionDef[] => [
        {id: "allocate", label: "Allocate", icon: "heroSquaresPlus", kind: "custom"},
        {id: "viewGrant", label: "Open grant application", icon: "heroArrowTopRightOnSquare", kind: "custom"},
        {id: "retire", label: "Retire", icon: "heroClock", kind: "custom"},
        {id: "edit", label: "Edit", icon: "heroPencil", kind: "custom", shortcut: "e"},
    ], []);

    const toggleExpanded = useCallback((key: string) => {
        setExpanded(previous => {
            const next = new Set(previous);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
        });
    }, []);

    const focusRow = useCallback((key: string) => {
        requestAnimationFrame(() => {
            const row = gridRef.current?.querySelector<HTMLElement>(`tr[data-row-key="${CSS.escape(key)}"]`);
            row?.scrollIntoView({block: "nearest"});
            row?.focus();
        });
    }, []);

    const selectRow = useCallback((key: string) => {
        store.setSelected("allocation-grid", key);
        focusRow(key);
    }, [focusRow, store]);

    const onGridKeyDown = useCallback((event: React.KeyboardEvent<HTMLDivElement>) => {
        if (event.key !== "ArrowLeft" && event.key !== "ArrowRight" && event.key !== " ") return;
        if (event.ctrlKey || event.metaKey || event.altKey) return;
        const target = event.target as HTMLElement | null;
        if (target?.closest("input, textarea, select, [contenteditable='true']")) return;

        const selectedRow = gridRef.current?.querySelector<HTMLElement>('tr[data-selected="true"][data-row-key]');
        const rowKey = selectedRow?.getAttribute("data-row-key");
        if (!rowKey) return;
        const entry = rowByKey.get(rowKey);
        if (!entry) return;

        const hasChildren = (rowChildren.get(rowKey)?.length ?? 0) > 0;
        const isExpanded = expanded.has(rowKey);
        if (event.key === "ArrowRight") {
            if (!hasChildren) return;
            event.preventDefault();
            event.stopPropagation();
            if (!isExpanded) {
                toggleExpanded(rowKey);
                focusRow(rowKey);
            } else {
                const firstChild = rowChildren.get(rowKey)?.[0];
                if (firstChild) selectRow(firstChild.key);
            }
            return;
        }
        if (event.key === "ArrowLeft") {
            if (hasChildren && isExpanded) {
                event.preventDefault();
                event.stopPropagation();
                toggleExpanded(rowKey);
                focusRow(rowKey);
                return;
            }
            const parent = treeParents.get(rowKey);
            if (parent) {
                event.preventDefault();
                event.stopPropagation();
                selectRow(parent);
            }
            return;
        }
        if (entry.kind === "grant" && hasChildren) {
            event.preventDefault();
            event.stopPropagation();
            toggleExpanded(rowKey);
            focusRow(rowKey);
        }
    }, [expanded, focusRow, rowByKey, rowChildren, selectRow, toggleExpanded, treeParents]);

    const openGrant = useCallback((grantId: number) => {
        if (props.onOpenGrant) props.onOpenGrant(grantId);
        else window.open(window.origin + "/app" + AppRoutes.grants.editor(grantId), "_blank");
    }, [props.onOpenGrant]);

    const editRow = useCallback((key: string) => {
        const entry = rowByKey.get(key);
        if (!entry) return;
        if (entry.kind !== "allocation") {
            setExpanded(previous => new Set([...previous, key]));
            return;
        }
        const row = entry.grant;
        const slice = entry.allocation;
        const end = slice.end - 1;
        if (!allocationCanRetire(slice, props.model.now)) return;
        props.onEdit?.({
            category: row.category,
            allocationId: slice.allocationId,
            start: slice.start,
            end: slice.end,
            quota: slice.rawQuota,
            workspaceTitle: row.workspaceTitle,
        });
    }, [rowByKey, props.onEdit, props.model.now]);

    const onRowAction = useCallback((event: UcxTableActionEvent) => {
        const entry = rowByKey.get(event.rowKey);
        if (!entry) return;
        if (event.actionId === "allocate" && entry.kind === "product") {
            props.onAllocate?.(selected.has(entry.product.key) ? selectedRecipients :
                allocationModelRecipients(props.model, new Set([entry.product.workspaceKey])));
        } else if (event.actionId === "edit" && entry.kind === "allocation") {
            editRow(event.rowKey);
        } else if (event.actionId === "viewGrant" && entry.kind === "grant" && entry.grant.grantId !== undefined) {
            openGrant(entry.grant.grantId);
        } else if (event.actionId === "retire" && props.onRetire) {
            if (entry.kind === "allocation" && allocationCanRetire(entry.allocation, props.model.now)) {
                props.onRetire([entry.allocation.allocationId], entry.grant.workspaceTitle);
            } else if (entry.kind === "grant") {
                const allocations = retireableByGrant.get(entry.grant.key);
                if (!allocations?.length) return;
                const ids = [...allocations].sort((a, b) => a.end - b.end || a.allocationId - b.allocationId)
                    .map(allocation => allocation.allocationId);
                props.onRetire(ids, entry.grant.workspaceTitle, entry.grant.grantId);
            }
        }
    }, [editRow, rowByKey, openGrant, props.model, props.onAllocate, props.onRetire, retireableByGrant, selected, selectedRecipients]);

    const onRowActivated = useCallback((event: UcxTableActivationEvent) => {
        const entry = rowByKey.get(event.rowKey);
        if (!entry) return;
        if (entry.kind !== "allocation") {
            toggleExpanded(event.rowKey);
        } else {
            editRow(event.rowKey);
        }
    }, [rowByKey, toggleExpanded, editRow]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const entry = rowByKey.get(context.row.key);
        if (!entry) return context.cell;
        const row = entry.kind === "product" ? entry.product : entry.grant;
        const allocation = entry.kind === "allocation" ? entry.allocation : undefined;
        const metric = allocationLifecycleMetrics.find(metric => metric.key === context.column.key);
        const consumption = row.category.accountingModel === "CONSUMPTION";
        if (metric) {
            if (entry.kind === "product") return <AllocationLifecycleAmount
                metric={metric} amounts={row.accounting.amounts} consumption={consumption} unit={row.unit} maxUsable={row.accounting.maxUsable}
            />;
            if (metric.key === "used") {
                const usage = allocation ? allocationSliceUsage(allocation) : row.usage;
                if (!usage) return null;
                return <AllocationAmount value={usage.value} unit={row.unit} approximate={usage.approximate}
                    title={usage.approximate ?
                        "Estimate: remaining usage is split evenly among active allocations from the same funding parent. Usage is not finalized until each allocation expires." :
                        allocation ? "Usage recorded when this allocation expired." : "Total usage recorded on the allocations in this grant."} />;
            }
            return null;
        }
        const expandButton = (label: string, title?: string) => <button
            className="allocation-expand"
            aria-label={`${expanded.has(context.row.key) ? "Collapse" : "Expand"} ${label}`}
            title={title}
            aria-expanded={expanded.has(context.row.key)}
            onClick={event => {
                event.stopPropagation();
                store.setSelected("allocation-grid", context.row.key);
                toggleExpanded(context.row.key);
            }}
            onDoubleClick={event => event.stopPropagation()}
        ><Icon name={expanded.has(context.row.key) ? "heroChevronDown" : "heroChevronRight"} size={12} /></button>;

        if (context.column.key === COLUMN_KEYS.workspace) {
            if (entry.kind !== "product" || !context.isFirstInGroup) {
                return <span className={`allocation-group-rail${context.isLastInGroup ? " allocation-group-rail-last" : ""}`} aria-hidden="true" />;
            }
            return <AllocationWorkspaceLabel title={row.workspaceTitle} meta={row.workspaceType === "user" ? "Personal" : `PI ${row.workspacePi}`}
                metaTooltip={row.workspacePi} />;
        }
        switch (context.column.key) {
            case COLUMN_KEYS.product: {
                if (entry.kind === "product") return <div className="allocation-product">
                    {expandButton(`${row.workspaceTitle} ${row.productName} allocations`)}
                    <span className="allocation-icon-slot"><ProviderLogo providerId={row.provider} size={14} /></span>
                    <span className="allocation-product-name">{row.productName}</span>
                    <span className="allocation-product-meta">{getShortProviderTitle(row.provider)}</span>
                </div>;
                if (allocation) return <div className="allocation-slice-label">
                    <span className="allocation-indent" />
                    <span className="allocation-icon-slot" />
                    <span>{allocation.allocationId.toString().padStart(6, "0")}</span>
                    <span className="allocation-workspace-meta">
                        {allocation.retired ? "Expired" : allocation.start > props.model.now ? "Upcoming" : "Active"}
                    </span>
                    <button
                        type="button"
                        className={`${UcxTableIconButtonClass} allocation-id-copy`}
                        title="Copy allocation ID"
                        aria-label="Copy allocation ID"
                        onClick={event => {
                            event.stopPropagation();
                            copyToClipboard(String(allocation.allocationId));
                        }}
                        onDoubleClick={event => event.stopPropagation()}
                        onKeyDown={event => {
                            if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                        }}
                    ><Icon name="heroDocumentDuplicate" size={14} /></button>
                </div>;
                const grantId = row.grantId;
                const sliceCount = row.slices.length;
                return <div className="allocation-child-label">
                    <span className="allocation-indent" />
                    {expandButton(grantId === undefined ? "unlinked allocations" : `grant #${grantId} allocations`)}
                    <span>
                        {grantId === undefined ? "No grant" :
                            props.renderGrantLink?.(grantId) ?? <Link
                                to={AppRoutes.grants.editor(grantId)}
                                target="_blank"
                                onClick={event => {
                                    event.stopPropagation();
                                    if (!props.onOpenGrant) return;
                                    event.preventDefault();
                                    props.onOpenGrant(grantId);
                                }}
                            >#{grantId}</Link>}
                        {` (${sliceCount} ${sliceCount === 1 ? "slice" : "slices"})`}
                    </span>
                </div>;
            }
            case COLUMN_KEYS.period:
                if (allocation) return allocationPeriodCell(props.model.now, allocation.start, Math.max(allocation.start, allocation.end - 1), true, allocation.retired);
                return allocationPeriodCell(props.model.now, row.start, Math.max(row.start, row.end), false,
                    entry.kind === "grant" && row.slices.every(slice => slice.retired));
            case COLUMN_KEYS.quota: {
                let title = allocation ? "Quota granted to this allocation." : "Total granted, including the full release schedule.";
                if (entry.kind === "product" && consumption) {
                    title += ` Total used across active and expired allocations: ${formatNumber(row.accounting.amounts.used, {precision: 2, removeTrailingZeros: true})} ${row.unit}.`;
                }
                return <AllocationAmount
                    value={allocation?.quota ?? row.allocationQuota}
                    unit={row.unit}
                    title={title}
                />;
            }
            default:
                return context.cell;
        }
    }, [expanded, rowByKey, store, toggleExpanded, props.renderGrantLink, props.onOpenGrant, props.model.now]);

    return <div ref={gridRef} className={AllocationGridClass} onKeyDown={onGridKeyDown}>
        <AllocationFilters
            query={query}
            onQueryChange={setQuery}
            searchRef={searchRef}
            onSearchExit={() => store.viewFor("allocation-grid")?.focus()}
            periodControl={props.periodControl}
            actions={<div style={{display: "flex", flexWrap: "wrap", gap: 8}}>
                {props.onAllocate ? <Button
                    height={35}
                    onClick={() => props.onAllocate?.(selectedRecipients)}
                ><Icon name="heroSquaresPlus" mr={8} />Allocate</Button> : null}
                {props.toolbar}
            </div>}
            products={products}
            providers={providers}
            columns={columnOptions}
            selectedProducts={productKeys}
            selectedProviders={selectedProviders}
            visibleColumns={visibleColumns}
            onProductsChange={setProductKeys}
            onProvidersChange={setSelectedProviders}
            onColumnsChange={onColumnsChange}
            state={filterState}
            onStateChange={setFilterState}
            counts={counts}
            workspaceCount={new Set(rows.map(row => row.group)).size}
            personalWorkspaces={props.personalWorkspaces}
            onPersonalWorkspacesChange={props.onPersonalWorkspacesChange}
            onClear={() => {
                setQuery("");
                setProductKeys([]);
                setSelectedProviders([]);
                setFilterState("all");
                props.onClearFilters?.();
            }}
        />
        <UcxStreamedTable
            tableId="allocation-grid"
            store={store}
            stateKey="allocation-grid"
            viewId="allocation-grid"
            emptyMessage={query || productKeys.length > 0 || selectedProviders.length > 0 || filterState !== "all"
                ? "No allocations match the current search, filters or dates."
                : props.emptyMessage ?? (props.dateRange?.start || props.dateRange?.end
                ? "No allocations overlap these dates."
                : "No sub-allocations yet.")}
            showGroupHeaders={false}
            inlineGroups
            inlineGroupColumn={COLUMN_KEYS.workspace}
            density="compact"
            rowHeight={45}
            columnWidths={COLUMN_WIDTHS}
            renderCell={renderCell}
            expandedRowKeys={expanded}
            rowChildren={rowChildren}
            selection={selection}
            actions={actionDefs}
            onRowActivated={onRowActivated}
            onRowAction={onRowAction}
        />
    </div>;
}
