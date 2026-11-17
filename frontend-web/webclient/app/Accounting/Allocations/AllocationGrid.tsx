import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {NO_EXPIRATION_FALLBACK, productTypeToIcon} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {UcxStreamedTable, UcxTableStore} from "@/UCX/UcxBrowser";
import type {
    UcxTableActionDef,
    UcxTableActionEvent,
    UcxTableActivationEvent,
    UcxTableCellRenderContext,
    UcxTableSelection,
} from "@/UCX/UcxBrowser";
import {Button, Icon, Link} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import type {ProjectInfo} from "@/Project/InfoCache";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {addMonths, differenceInCalendarMonths, format} from "date-fns";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {allocationCompareProducts} from "./AllocationPresentation";
import {AllocationFilters} from "./AllocationFilters";
import type {AllocationFilterState} from "./AllocationFilters";
import {AllocationAmount} from "./AllocationCells";
import {allocationModelRecipients, allocationSelectProducts, allocationWorkspaceInfo} from "./AllocationModel";
import type {AllocationDateRange, AllocationModel, AllocationModelGrant, AllocationModelProduct, AllocationModelRecipient, AllocationModelSlice} from "./AllocationModel";
export {allocationDateRangeBounds, allocationOverlapsRange} from "./AllocationModel";
export type {AllocationDateRange} from "./AllocationModel";

const DAY_MS = 24 * 60 * 60 * 1000;
const EXPIRING_WINDOW_MS = 30 * DAY_MS;

const COLUMN_KEYS = {
    workspace: "allocation.workspace",
    product: "allocation.product",
    period: "allocation.period",
    quota: "allocation.quota",
    usage: "allocation.usage",
    available: "allocation.available",
    grant: "allocation.grant",
} as const;

const COLUMN_WIDTHS: Record<string, number> = {
    [COLUMN_KEYS.workspace]: 220,
    [COLUMN_KEYS.product]: 300,
    [COLUMN_KEYS.period]: 170,
    [COLUMN_KEYS.quota]: 130,
    [COLUMN_KEYS.usage]: 170,
    [COLUMN_KEYS.available]: 140,
    [COLUMN_KEYS.grant]: 64,
};

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
    onEdit: (request: AllocationEditRequest) => void;
    onOpenGrant?: (grantId: number) => void;
    renderGrantLink?: (grantId: number) => React.ReactNode;
    onVisibleProductsChange?: (products: AllocationModelProduct[]) => void;
}

interface AllocationGridRow {
    key: string;
    group: string;
    category: ProductCategoryV2;
    allocationId: number;
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
    allocationQuotaRaw: number;
    productUsage: number;
    productAvailable: number;
    accounting: AllocationModelProduct;
    grant: AllocationModelGrant;
    slices?: AllocationModelSlice[];
}

interface AllocationProductRow extends AllocationGridRow {
    allocations: AllocationGridRow[];
    grantIds: number[];
}

type AllocationDisplayRow =
    | {kind: "product"; product: AllocationProductRow}
    | {kind: "allocation"; allocation: AllocationGridRow}
    | {kind: "slice"; allocation: AllocationGridRow; slice: AllocationModelSlice; index: number};

function allocationSliceKey(allocationKey: string, slice: AllocationModelSlice): string {
    return `${allocationKey}/slice:${slice.allocationId}`;
}

function allocationReleaseSchedule(row: AllocationGridRow): string {
    if (!row.slices) return "All at once";
    const first = row.slices[0];
    const months = differenceInCalendarMonths(first.end, first.start);
    const regular = months > 0 && row.slices.every(slice =>
        slice.quota === first.quota && addMonths(slice.start, months).getTime() === slice.end
    );
    if (!regular) return `${row.slices.length} slices`;
    const quota = formatNumber(first.quota, {
        precision: 2,
        removeTrailingZeros: true,
    });
    return `${quota} ${row.unit} / ${months === 1 ? "month" : `${months} months`}`;
}

function allocationPeriodCell(now: number, start: number, end: number, slices?: AllocationModelSlice[], precise = false): React.ReactNode {
    const expired = end < now;
    const upcoming = start > now;
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
                {slices?.slice(1).map(slice => <span
                    key={slice.start}
                    className="allocation-period-slice-tick"
                    style={{left: `${span > 0 ? ((slice.start - start) / span) * 100 : 0}%`}}
                />)}
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

    ${k} [data-density="compact"] .streamed-table-scroll thead th:nth-child(5),
    ${k} [data-density="compact"] .streamed-table-scroll thead th:nth-child(6),
    ${k} [data-density="compact"] .streamed-table-scroll thead th:nth-child(7) {
        text-align: right;
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

    ${k} .streamed-table-scroll td:has(> .allocation-group-rail) {
        position: relative;
        overflow: visible;
    }

    ${k} .allocation-workspace {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        min-width: 0;
    }

    ${k} .allocation-workspace-tooltip {
        display: flex;
        flex-direction: column;
        min-width: 0;
        max-width: 100%;
    }

    ${k} .allocation-workspace-title {
        overflow: hidden;
        color: var(--textPrimary);
        font-size: 12.5px;
        font-weight: 600;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

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

    ${k} .allocation-group-rail {
        position: absolute;
        top: -1px;
        bottom: -1px;
        left: 0;
        z-index: 1;
        width: 16px;
        opacity: 0.65;
    }

    ${k} .allocation-group-rail::before {
        content: "";
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        width: 1px;
        background: var(--textSecondary);
    }

    ${k} .allocation-group-rail-branch::after {
        content: "";
        position: absolute;
        top: 50%;
        left: 0;
        width: 20px;
        border-top: 1px solid var(--textSecondary);
    }

    ${k} .allocation-group-rail-last::before {
        bottom: 50%;
    }

    ${k} .allocation-child-label, ${k} .allocation-slice-label {
        display: flex;
        align-items: center;
        gap: 5px;
    }

    ${k} .allocation-slice-label .allocation-workspace-meta {
        margin-left: 8px;
    }

    ${k} .allocation-detail-empty {
        display: block;
        text-align: right;
        color: var(--textSecondary);
        opacity: 0.5;
    }

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

    ${k} .allocation-period-slice-tick {
        position: absolute;
        top: -2px;
        bottom: -2px;
        width: 1px;
        background: var(--textSecondary);
        opacity: 0.5;
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

    ${k} .allocation-period-note, ${k} .allocation-grant-empty {
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

    const visibleProducts = useMemo(() => matchingProducts.filter(product =>
        filterState === "all" || product.states.has(filterState)
    ), [matchingProducts, filterState]);

    useEffect(() => {
        props.onVisibleProductsChange?.(visibleProducts);
    }, [visibleProducts, props.onVisibleProductsChange]);

    const rows = useMemo((): AllocationProductRow[] => visibleProducts.map(product => {
        const workspace = allocationWorkspaceInfo(product.owner, props.projectInfo);
        const allocations: AllocationGridRow[] = product.grants.map(grant => ({
            key: grant.key,
            group: `${workspace.title}\u0000${product.workspaceKey}`,
            category: product.category,
            allocationId: grant.allocationId,
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
            allocationQuotaRaw: grant.rawQuota,
            productUsage: product.amounts.used,
            productAvailable: product.amounts.activeBalance,
            accounting: product,
            grant,
            slices: grant.sliced ? grant.slices : undefined,
        }));
        return {
            ...allocations[0],
            key: product.key,
            start: product.start,
            end: product.end - 1,
            allocationQuota: product.amounts.granted,
            allocations,
            grantIds: [...new Set(product.grants.flatMap(grant => grant.grantId === undefined ? [] : [grant.grantId]))],
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
                result.set(allocation.key, {kind: "allocation", allocation});
                allocation.slices?.forEach((slice, index) => {
                    result.set(allocationSliceKey(allocation.key, slice), {kind: "slice", allocation, slice, index});
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
                allocation.slices?.forEach(slice => result.set(allocationSliceKey(allocation.key, slice), allocation.key));
            }
        }
        return result;
    }, [rows]);

    const columns = useMemo((): TableColumn[] => [
        {key: COLUMN_KEYS.workspace, label: "Workspace", jsonPath: "workspace", sortType: TableColumnSortType.Text},
        {key: COLUMN_KEYS.product, label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
        {key: COLUMN_KEYS.period, label: "Period", jsonPath: "period", sortType: TableColumnSortType.Text},
        {key: COLUMN_KEYS.quota, label: "Total granted", jsonPath: "quota", sortType: TableColumnSortType.Number},
        {key: COLUMN_KEYS.usage, label: "Used", jsonPath: "usage", sortType: TableColumnSortType.Number},
        {key: COLUMN_KEYS.available, label: "Active balance", jsonPath: "available", sortType: TableColumnSortType.Number},
        {key: COLUMN_KEYS.grant, label: "Grant", jsonPath: "grant", sortType: TableColumnSortType.Text},
    ], []);

    const {tableRows, rowChildren} = useMemo(() => {
        const tableByKey = new Map<string, TableRow>();
        for (const [key, entry] of rowByKey) {
            const row = entry.kind === "product" ? entry.product : entry.allocation;
            const end = entry.kind === "slice" ? entry.slice.end - 1 : row.end;
            const quota = entry.kind === "slice" ? entry.slice.quota : row.allocationQuota;
            tableByKey.set(key, {
                key,
                group: row.group,
                cells: [
                    `${row.workspaceTitle} ${row.workspacePi}`,
                    row.productName,
                    `${row.start} ${end}`,
                    `${quota}`,
                    entry.kind === "product" ? `${row.productUsage}` : "",
                    entry.kind === "product" ? `${row.productAvailable}` : "",
                    row.grantId === undefined ? "" : `${row.grantId}`,
                ],
                actions: entry.kind === "product" ? [{id: "breakdown", enabled: true}] : [
                    {id: "edit", enabled: end >= props.model.now, disabledReason: "Expired allocations cannot be edited"},
                    ...(entry.kind === "allocation" && row.grantId !== undefined ? [{id: "viewGrant", enabled: true}] : []),
                    ...(entry.kind === "allocation" && row.slices ? [{id: "breakdown", enabled: true}] : []),
                ],
            });
        }
        const children = new Map<string, TableRow[]>();
        for (const row of rows) {
            children.set(row.key, row.allocations.map(allocation => tableByKey.get(allocation.key)!));
            for (const allocation of row.allocations) {
                if (!allocation.slices) continue;
                children.set(allocation.key, allocation.slices.map(slice =>
                    tableByKey.get(allocationSliceKey(allocation.key, slice))!
                ));
            }
        }
        return {
            tableRows: rows.map(row => tableByKey.get(row.key)!),
            rowChildren: children,
        };
    }, [rows, rowByKey, props.model.now]);

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
        {id: "edit", label: "Edit allocation", icon: "heroPencil", kind: "custom", shortcut: "e"},
        {id: "viewGrant", label: "View grant application", icon: "heroArrowTopRightOnSquare", kind: "custom"},
        {id: "breakdown", label: "Toggle allocation breakdown", icon: "heroBars3", kind: "custom"},
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
        if (entry.kind === "allocation" && hasChildren) {
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
        if (entry.kind === "product" || (entry.kind === "allocation" && entry.allocation.slices)) {
            setExpanded(previous => new Set([...previous, key]));
            return;
        }
        const row = entry.allocation;
        const slice = entry.kind === "slice" ? entry.slice : undefined;
        if (slice && slice.allocationId === undefined) return;
        const end = slice ? slice.end - 1 : row.end;
        if (end < props.model.now) return;
        props.onEdit({
            category: row.category,
            allocationId: slice?.allocationId ?? row.allocationId,
            start: slice?.start ?? row.start,
            end: slice?.end ?? row.end,
            quota: slice?.rawQuota ?? row.allocationQuotaRaw,
            workspaceTitle: row.workspaceTitle,
        });
    }, [rowByKey, props.onEdit, props.model.now]);

    const onRowAction = useCallback((event: UcxTableActionEvent) => {
        if (event.actionId === "breakdown") toggleExpanded(event.rowKey);
        else if (event.actionId === "edit") editRow(event.rowKey);
        else if (event.actionId === "viewGrant") {
            const entry = rowByKey.get(event.rowKey);
            if (entry?.kind === "allocation" && entry.allocation.grantId !== undefined) {
                openGrant(entry.allocation.grantId);
            }
        }
    }, [toggleExpanded, editRow, rowByKey, openGrant]);

    const onRowActivated = useCallback((event: UcxTableActivationEvent) => {
        const entry = rowByKey.get(event.rowKey);
        if (!entry) return;
        if (entry.kind === "product" || (entry.kind === "allocation" && entry.allocation.slices)) {
            toggleExpanded(event.rowKey);
        } else if (entry.kind === "allocation" && entry.allocation.grantId !== undefined) {
            openGrant(entry.allocation.grantId);
        } else {
            editRow(event.rowKey);
        }
    }, [rowByKey, toggleExpanded, openGrant, editRow]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const entry = rowByKey.get(context.row.key);
        if (!entry) return context.cell;
        const row = entry.kind === "product" ? entry.product : entry.allocation;
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
                return <span
                    className={`allocation-group-rail allocation-group-rail-branch${context.isLastInGroup ? " allocation-group-rail-last" : ""}`}
                    aria-hidden="true"
                />;
            }
            return <div className="allocation-workspace">
                <TooltipV2 tooltip={row.workspaceTitle} triggerClassName="allocation-workspace-tooltip">
                    <span className="allocation-workspace-title">{row.workspaceTitle}</span>
                </TooltipV2>
                <TooltipV2 tooltip={row.workspacePi} triggerClassName="allocation-workspace-tooltip">
                    <span className="allocation-workspace-meta">
                        {row.workspaceType === "user" ? "Personal" : `PI ${row.workspacePi}`}
                    </span>
                </TooltipV2>
            </div>;
        }
        switch (context.column.key) {
            case COLUMN_KEYS.product:
                if (entry.kind === "product") return <div className="allocation-product">
                    {expandButton(`${row.workspaceTitle} ${row.productName} allocations`)}
                    <span className="allocation-icon-slot"><ProviderLogo providerId={row.provider} size={14} /></span>
                    <span className="allocation-product-name">{row.productName}</span>
                    <span className="allocation-product-meta">{getShortProviderTitle(row.provider)}</span>
                </div>;
                if (entry.kind === "slice") return <div className="allocation-slice-label">
                    <span className="allocation-indent" />
                    <span className="allocation-icon-slot" />
                    <span>Slice {entry.index + 1}</span>
                    <span className="allocation-workspace-meta">
                        {entry.slice.retired ? "Expired" : entry.slice.start > props.model.now ? "Upcoming" : "Active"}
                    </span>
                </div>;
                return <div className="allocation-child-label">
                    <span className="allocation-indent" />
                    {row.slices ? expandButton(
                        `${formatNumber(row.allocationQuota)} ${row.unit} allocation release schedule`,
                        `Quota is released in ${row.slices.length} slices. Unused quota expires at the end of each slice.`,
                    ) : <span className="allocation-icon-slot"><Icon name="heroBanknotes" size={14} /></span>}
                    {row.slices ? <span>{allocationReleaseSchedule(row)}</span> :
                        <span>Allocation <code>{row.allocationId.toString().padStart(6, "0")}</code></span>}
                </div>;
            case COLUMN_KEYS.period:
                if (entry.kind === "slice") return allocationPeriodCell(props.model.now, entry.slice.start, entry.slice.end - 1, undefined, true);
                return allocationPeriodCell(props.model.now, row.start, row.end, entry.kind === "product" && entry.product.allocations.length > 1 ? undefined : row.slices);
            case COLUMN_KEYS.quota:
                return <AllocationAmount
                    value={entry.kind === "slice" ? entry.slice.quota : row.allocationQuota}
                    unit={row.unit}
                    title={entry.kind === "slice" ? "Quota released for this slice" : "Total granted, including the full release schedule"}
                />;
            case COLUMN_KEYS.usage: {
                if (entry.kind === "product") return <AllocationAmount
                    value={row.productUsage}
                    unit={row.unit}
                    title={row.category.accountingModel === "CONSUMPTION" ?
                        "All charges attributed to this funding relationship, including expired allocations. The period filter selects relationships, not charges." :
                        "Current capacity charged to this funding relationship. Historical capacity is not added."}
                />;
                const attribution = entry.kind === "slice" ? entry.slice : row.grant;
                if (attribution.used !== null) return <AllocationAmount
                    value={attribution.used}
                    unit={row.unit}
                    title={attribution.attribution === "future" ? "No charges are assigned to this future allocation." :
                        "Charges assigned to this allocation at retirement, not consumption during its dates."}
                />;
                if (attribution.attribution === "not-applicable") return <span className="allocation-detail-empty">—</span>;
                return <TooltipV2 tooltip={`Active charges are pooled across this funding relationship (${formatNumber(row.accounting.amounts.activeUsed)} ${row.unit}). They are not assigned to individual active allocations.`}>
                    <span className="allocation-period-note">Pooled{entry.kind === "allocation" && row.grant.recordedUsed > 0 ? ` · ${formatNumber(row.grant.recordedUsed)} recorded` : ""}</span>
                </TooltipV2>;
            }
            case COLUMN_KEYS.available:
                return entry.kind === "product" ? <AllocationAmount
                    value={row.productAvailable}
                    unit={row.unit}
                    negative={row.productAvailable < 0}
                    title={`Active quota minus active charges on this funding relationship; upstream limits can restrict actual availability. ${formatNumber(row.accounting.amounts.future)} ${row.unit} future; ${formatNumber(row.accounting.amounts.expiredUnused)} ${row.unit} expired unused.`}
                /> : <span className="allocation-detail-empty">—</span>;
            case COLUMN_KEYS.grant: {
                if (entry.kind === "slice") return null;
                const grantIds = entry.kind === "product" ? entry.product.grantIds :
                    row.grantId === undefined ? [] : [row.grantId];
                if (grantIds.length === 0) return <span className="allocation-grant-empty">—</span>;
                if (grantIds.length > 1) return <span className="allocation-period-note">{grantIds.length} grants</span>;
                const grantId = grantIds[0];
                if (props.renderGrantLink) return props.renderGrantLink(grantId);
                return <Link
                    to={AppRoutes.grants.editor(grantId)}
                    target="_blank"
                    onClick={event => {
                        event.stopPropagation();
                        if (!props.onOpenGrant) return;
                        event.preventDefault();
                        props.onOpenGrant(grantId);
                    }}
                >#{grantId}</Link>;
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
                    disabled={selectedRecipients.length === 0}
                    onClick={() => props.onAllocate?.(selectedRecipients)}
                ><Icon name="heroSquaresPlus" mr={8} />Allocate</Button> : null}
                {props.toolbar}
            </div>}
            products={products}
            providers={providers}
            selectedProducts={productKeys}
            selectedProviders={selectedProviders}
            onProductsChange={setProductKeys}
            onProvidersChange={setSelectedProviders}
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
                ? "No allocations match the current search, filters or period."
                : props.emptyMessage ?? (props.dateRange?.start || props.dateRange?.end
                ? "No allocations overlap this period."
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
