import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {NO_EXPIRATION_FALLBACK, explainUnit, productTypeToIcon} from "@/Accounting";
import type {AllocationDisplayTreeRecipient, ProductCategoryV2} from "@/Accounting";
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
import {projectInfoPi, projectInfoTitle} from "@/Project/InfoCache";
import type {ProjectInfo} from "@/Project/InfoCache";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {format} from "date-fns";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {allocationCompareProducts, allocationDisplayUnit} from "./AllocationPresentation";
import {AllocationFilters} from "./AllocationFilters";
import type {AllocationFilterState} from "./AllocationFilters";
import {AllocationAmount, AllocationUsage} from "./AllocationCells";
import {AllocationBreakdownDialog} from "./AllocationBreakdownDialog";
import type {AllocationSlice} from "./AllocationSlices";

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

export interface AllocationDateRange {
    start: Date;
    end: Date;
}

export interface AllocationEditRequest {
    category: ProductCategoryV2;
    allocationId: number;
    start: number;
    end: number;
    quota: number;
    workspaceTitle: string;
}

interface AllocationGridProps {
    recipients: AllocationDisplayTreeRecipient[];
    recipientIndices: number[];
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange?: AllocationDateRange;
    selectedCategories?: string[];
    onCategoriesChange?: (categories: string[]) => void;
    searchRef?: React.RefObject<HTMLInputElement | null>;
    personalWorkspaces: boolean;
    onPersonalWorkspacesChange: () => void;
    onClearFilters?: () => void;
    toolbar?: React.ReactNode;
    onAllocate?: (recipients: AllocationDisplayTreeRecipient[]) => void;
    periodControl?: React.ReactNode;
    emptyMessage?: string;
    onEdit: (request: AllocationEditRequest) => void;
    onOpenGrant?: (grantId: number) => void;
    renderGrantLink?: (grantId: number) => React.ReactNode;
}

type AllocationState = "active" | "expiring" | "upcoming" | "exhausted" | "expired";
type AllocationLifecycleState = "active" | "expiring" | "upcoming" | "expired";

interface AllocationGridRow {
    key: string;
    group: string;
    category: ProductCategoryV2;
    allocationId: number;
    recipientIndex: number;
    start: number;
    end: number;
    grantId?: number;
    workspaceTitle: string;
    workspacePi: string;
    workspaceType: "project" | "user";
    productName: string;
    provider: string;
    unit: string;
    state: AllocationState;
    lifecycleState: AllocationLifecycleState;
    isLastInGroup: boolean;
    allocationQuota: number;
    allocationQuotaRaw: number;
    productQuota: number;
    productUsage: number;
    productAvailable: number;
    slices?: AllocationSlice[];
    activeSliceQuota?: number;
}

export function allocationDateRangeBounds(range?: AllocationDateRange): {start: number | null; end: number | null} {
    if (!range) return {start: null, end: null};
    let start = range.start
        ? new Date(range.start.getFullYear(), range.start.getMonth(), 1, 0, 0, 0, 0).getTime()
        : null;
    let end = range.end
        ? new Date(range.end.getFullYear(), range.end.getMonth() + 1, 0, 23, 59, 59, 999).getTime()
        : null;
    if (start !== null && end !== null && start > end) {
        const swap = start;
        start = end;
        end = swap;
    }
    return {start, end};
}

export function allocationOverlapsRange(start: number, end: number, range: {start: number | null; end: number | null}): boolean {
    if (range.start !== null && end < range.start) return false;
    if (range.end !== null && start > range.end) return false;
    return true;
}

function stateForAllocation(start: number, end: number, usage: number, quota: number): AllocationState {
    const now = Date.now();
    if (end < now) return "expired";
    if (start > now) return "upcoming";
    if (quota > 0 && usage >= quota) return "exhausted";
    if (end - now <= EXPIRING_WINDOW_MS) return "expiring";
    return "active";
}

function lifecycleStateForAllocation(start: number, end: number): AllocationLifecycleState {
    const now = Date.now();
    if (end < now) return "expired";
    if (start > now) return "upcoming";
    if (end - now <= EXPIRING_WINDOW_MS) return "expiring";
    return "active";
}

function periodPercent(start: number, end: number): number {
    if (end <= start) return 100;
    const percentage = ((Date.now() - start) / (end - start)) * 100;
    return Math.max(0, Math.min(100, percentage));
}

function relativeDuration(ms: number): string {
    const days = Math.max(1, Math.round(ms / DAY_MS));
    if (days < 60) return `${formatNumber(days)} day${days === 1 ? "" : "s"}`;
    const months = Math.max(1, Math.round(days / 30));
    return `${formatNumber(months)} month${months === 1 ? "" : "s"}`;
}

function hasNoEnd(end: number): boolean {
    return end >= NO_EXPIRATION_FALLBACK - DAY_MS;
}

function periodNote(start: number, end: number): string {
    const now = Date.now();
    if (end < now) return `Ended ${relativeDuration(now - end)} ago`;
    if (start > now) return `Starts in ${relativeDuration(start - now)}`;
    if (hasNoEnd(end)) return "No end";
    return `${relativeDuration(end - now)} left`;
}

function workspaceInfo(recipient: AllocationDisplayTreeRecipient, projectInfo: Record<string, ProjectInfo | null>): {
    title: string;
    pi: string;
    type: "project" | "user";
    id: string;
} {
    if (recipient.owner.reference.type === "user") {
        const username = recipient.owner.reference.username;
        return {title: username, pi: username, type: "user", id: username};
    }

    const info = projectInfo[recipient.owner.reference.projectId];
    const title = projectInfoTitle(info, recipient.owner.title) ?? "-";
    const pi = projectInfoPi(info, recipient.owner.primaryUsername) ?? "-";
    return {
        title,
        pi,
        type: "project",
        id: recipient.owner.reference.projectId,
    };
}

function openGrantApplication(grantId: number): void {
    window.open(window.origin + "/app" + AppRoutes.grants.editor(grantId), "_blank");
}

function renderWorkspaceCell(row: AllocationGridRow, isFirstInGroup: boolean): React.ReactNode {
    if (!isFirstInGroup) return <span
        className={`allocation-group-rail${row.isLastInGroup ? " allocation-group-rail-last" : ""}`}
        aria-hidden="true"
    />;
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

function renderProductCell(row: AllocationGridRow, onBreakdown: () => void): React.ReactNode {
    return <div className="allocation-product">
        <ProviderLogo providerId={row.provider} size={14} />
        <span className="allocation-product-name">{row.productName}</span>
        <span className="allocation-product-meta">
            {getShortProviderTitle(row.provider)}
            {row.slices ? <> · <button className="allocation-breakdown-link" onClick={event => {
                event.stopPropagation();
                onBreakdown();
            }}>{row.slices.length} slices</button></> : null}
        </span>
    </div>;
}

function renderPeriodCell(row: AllocationGridRow): React.ReactNode {
    const percentage = periodPercent(row.start, row.end);
    const noEnd = hasNoEnd(row.end);
    return <div className="allocation-period" data-state={row.lifecycleState}>
        <div className="allocation-period-dates">
            <span>{format(row.start, "MMM yyyy")}</span>
            <Icon name="heroArrowRight" size={9} />
            <span>{noEnd ? "No end" : format(row.end, "MMM yyyy")}</span>
        </div>
        <div className="allocation-period-bottom">
            <div className="allocation-period-track">
                <span className="allocation-period-elapsed" style={{width: `${percentage}%`}} />
                {row.lifecycleState !== "upcoming" && row.lifecycleState !== "expired" ?
                    <span className="allocation-period-now" style={{left: `${percentage}%`}} /> :
                    null}
            </div>
            <span className="allocation-period-note">{periodNote(row.start, row.end)}</span>
        </div>
    </div>;
}

function renderNumber(value: number, unit: string, title: string, negative = false): React.ReactNode {
    return <AllocationAmount value={value} unit={unit} title={title} negative={negative} />;
}

function renderUsageCell(row: AllocationGridRow): React.ReactNode {
    return <AllocationUsage usage={row.productUsage} quota={row.productQuota} unit={row.unit} title="Usage is reported for the product across all allocations" />;
}

function renderGrantCell(
    row: AllocationGridRow,
    renderLink?: (grantId: number) => React.ReactNode,
): React.ReactNode {
    if (row.grantId === undefined) return <span className="allocation-grant-empty">—</span>;
    if (renderLink) return renderLink(row.grantId);
    return <Link
        to={AppRoutes.grants.editor(row.grantId)}
        target="_blank"
        onClick={event => event.stopPropagation()}
    >
        #{row.grantId}
    </Link>;
}

const AllocationGridClass = injectStyle("allocation-grid", k => `
    ${k} .allocation-breakdown-link { border: 0; padding: 0; background: none; color: inherit; font: inherit; cursor: pointer; }
    ${k} .allocation-breakdown-link:hover { text-decoration: underline; }
    ${k} .allocation-package-quota { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
    ${k} .allocation-package-quota small { color: var(--textSecondary); font-size: 10.5px; }
    ${k} {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
        min-width: 0;
    }

    ${k} [data-density="compact"] .streamed-table-scroll thead th:nth-child(5),
    ${k} [data-density="compact"] .streamed-table-scroll thead th:nth-child(7) {
        text-align: right;
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

    ${k} .allocation-group-rail {
        position: relative;
        display: block;
        width: 16px;
        height: 14px;
        margin: -14px 0 0;
        opacity: 0.65;
    }

    ${k} .allocation-group-rail::before {
        content: "";
        position: absolute;
        top: -100px;
        left: 0;
        width: 1px;
        height: 200px;
        background: var(--textSecondary);
    }

    ${k} .allocation-group-rail::after {
        content: "";
        position: absolute;
        top: 9px;
        left: 0;
        width: 16px;
        height: 5px;
        border-left: 1px solid var(--textSecondary);
        border-bottom: 1px solid var(--textSecondary);
    }

    ${k} .allocation-group-rail-last::before {
        height: 114px;
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

    ${k} .allocation-period-now {
        position: absolute;
        top: -2px;
        width: 1px;
        height: 7px;
        background: var(--textPrimary);
    }

    ${k} .allocation-period[data-state="expired"] .allocation-period-elapsed {
        background: var(--gray-50);
    }

    ${k} .allocation-period[data-state="expiring"] .allocation-period-elapsed {
        background: var(--warningMain);
    }

    ${k} .allocation-period-note {
        flex: 0 0 auto;
        color: var(--textSecondary);
        font-size: 9px;
        white-space: nowrap;
    }

    ${k} .allocation-grant-empty {
        color: var(--textSecondary);
    }
`);

export function AllocationGrid(props: AllocationGridProps): React.ReactNode {
    const [breakdownKey, setBreakdownKey] = useState<string | null>(null);
    const [selected, setSelected] = useState<ReadonlySet<string>>(() => new Set());
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

    const baseRows = useMemo(() => {
        const range = allocationDateRangeBounds(props.dateRange);
        const result: AllocationGridRow[] = [];

        for (const recipientIdx of props.recipientIndices) {
            const recipient = props.recipients[recipientIdx];
            if (!recipient) continue;

            const workspace = workspaceInfo(recipient, props.projectInfo);
            for (const group of recipient.groups) {
                const usage = group.usageAndQuota.raw.usage;
                const quota = group.usageAndQuota.raw.quota;
                const available = quota - usage;
                const unitInfo = explainUnit(group.category);
                const unit = allocationDisplayUnit(group.category);

                const packages = new Map<string, typeof group.allocations>();
                for (const allocation of group.allocations) {
                    const key = allocation.allocationPackage && allocation.grantedIn !== undefined
                        ? `package:${recipientIdx}/${group.category.provider}/${group.category.name}/${allocation.grantedIn}`
                        : `allocation:${allocation.allocationId}`;
                    const entries = packages.get(key) ?? [];
                    entries.push(allocation);
                    packages.set(key, entries);
                }
                for (const [key, allocations] of packages) {
                    if (!allocations.some(allocation => allocationOverlapsRange(
                        allocation.start,
                        allocation.allocationPackage ? allocation.end - 1 : allocation.end,
                        range,
                    ))) continue;
                    const allocation = allocations[0];
                    const packaged = allocation.allocationPackage && allocation.grantedIn !== undefined;
                    const slices = packaged ? allocations.map(entry => ({
                        allocationId: entry.allocationId,
                        start: entry.start,
                        end: entry.end,
                        quota: entry.retired ? entry.retiredQuota ?? entry.quota : entry.quota,
                    })).sort((a, b) => a.start - b.start) : undefined;
                    const start = slices ? Math.min(...slices.map(slice => slice.start)) : allocation.start;
                    const end = slices ? Math.max(...slices.map(slice => slice.end)) - 1 : allocation.end;
                    const packageQuota = slices ? slices.reduce((sum, slice) => sum + slice.quota, 0) : allocation.quota;

                    const normalizedQuota = packageQuota * unitInfo.balanceFactor;
                    const state = stateForAllocation(start, end, usage, quota);
                    const lifecycleState = lifecycleStateForAllocation(start, end);

                    result.push({
                        key,
                        group: `${workspace.title}\u0000${workspace.id}`,
                        category: group.category,
                        allocationId: allocation.allocationId,
                        recipientIndex: recipientIdx,
                        start,
                        end,
                        grantId: allocation.grantedIn,
                        workspaceTitle: workspace.title,
                        workspacePi: workspace.pi,
                        workspaceType: workspace.type,
                        productName: group.category.name,
                        provider: group.category.provider,
                        unit,
                        state,
                        lifecycleState,
                        isLastInGroup: false,
                        allocationQuota: normalizedQuota,
                        allocationQuotaRaw: packageQuota,
                        productQuota: quota,
                        productUsage: usage,
                        productAvailable: available,
                        slices,
                        activeSliceQuota: slices?.filter(slice => slice.start <= Date.now() && Date.now() < slice.end)
                            .reduce((sum, slice) => sum + slice.quota * unitInfo.balanceFactor, 0),
                    });
                }
            }
        }

        result.sort((a, b) => {
            const workspaceComparison = a.workspaceTitle.localeCompare(b.workspaceTitle);
            if (workspaceComparison !== 0) return workspaceComparison;

            const productComparison = allocationCompareProducts(a.category, b.category);
            if (productComparison !== 0) return productComparison;

            return a.start - b.start;
        });

        return result;
    }, [props.recipientIndices, props.recipients, props.projectInfo, props.dateRange]);

    const products = useMemo(() => {
        const options = new Map<string, ProductCategoryV2>();
        for (const recipient of props.recipients) {
            for (const group of recipient.groups) {
                options.set(`${group.category.provider}/${group.category.name}`, group.category);
            }
        }
        return [...options.values()].sort(allocationCompareProducts).map(category => ({
            key: `${category.provider}/${category.name}`,
            label: `${category.name} (${getShortProviderTitle(category.provider)})`,
            icon: <Icon name={productTypeToIcon(category.productType)} size={16} />,
        }));
    }, [props.recipients]);

    const providers = useMemo(() => {
        const keys = new Set(props.recipients.flatMap(recipient => recipient.groups.map(group => group.category.provider)));
        return [...keys].map(key => ({
            key,
            label: getShortProviderTitle(key),
            icon: <ProviderLogo providerId={key} size={18} />,
        })).sort((a, b) => a.label.localeCompare(b.label));
    }, [props.recipients]);

    const matchingRows = useMemo(() => baseRows.filter(row => {
        if (productKeys.length > 0 && !productKeys.includes(`${row.provider}/${row.productName}`)) return false;
        if (selectedProviders.length > 0 && !selectedProviders.includes(row.provider)) return false;
        const needle = query.trim().toLowerCase();
        const text = `${row.workspaceTitle} ${row.workspacePi} ${row.productName} ${getShortProviderTitle(row.provider)}`.toLowerCase();
        return needle === "" || text.includes(needle);
    }), [baseRows, productKeys, selectedProviders, query]);
    const counts: Record<AllocationFilterState, number> = {
        all: matchingRows.length,
        active: 0,
        expiring: 0,
        upcoming: 0,
        exhausted: 0,
        expired: 0,
    };
    for (const row of matchingRows) counts[row.state]++;
    const rows = useMemo(() => {
        const filtered = matchingRows.filter(row => filterState === "all" || row.state === filterState);
        return filtered.map((row, index) => ({
            ...row,
            isLastInGroup: index === filtered.length - 1 || filtered[index + 1].group !== row.group,
        }));
    }, [matchingRows, filterState]);

    const rowByKey = useMemo(() => {
        const result = new Map<string, AllocationGridRow>();
        for (const row of rows) result.set(row.key, row);
        return result;
    }, [rows]);

    const columns = useMemo((): TableColumn[] => [
        {key: COLUMN_KEYS.workspace, label: "Workspace", jsonPath: "workspace", sortType: TableColumnSortType.Text},
        {key: COLUMN_KEYS.product, label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
        {key: COLUMN_KEYS.period, label: "Period", jsonPath: "period", sortType: TableColumnSortType.Text},
        {key: COLUMN_KEYS.quota, label: "Quota", jsonPath: "quota", sortType: TableColumnSortType.Number},
        {key: COLUMN_KEYS.usage, label: "Usage", jsonPath: "usage", sortType: TableColumnSortType.Number},
        {key: COLUMN_KEYS.available, label: "Available", jsonPath: "available", sortType: TableColumnSortType.Number},
        {key: COLUMN_KEYS.grant, label: "Grant", jsonPath: "grant", sortType: TableColumnSortType.Text},
    ], []);

    const tableRows = useMemo((): TableRow[] => rows.map(row => ({
        key: row.key,
        group: row.group,
        cells: [
            `${row.workspaceTitle} ${row.workspacePi}`,
            `${row.productName} ${getShortProviderTitle(row.provider)}`,
            `${format(row.start, "yyyy-MM")} ${format(row.end, "yyyy-MM")}`,
            `${row.allocationQuota}`,
            `${row.productUsage}`,
            `${row.productAvailable}`,
            row.grantId === undefined ? "" : `${row.grantId}`,
        ],
        actions: [
            {
                id: "edit",
                enabled: row.end >= Date.now(),
                disabledReason: "Expired allocations cannot be edited",
            },
            ...(row.grantId === undefined ? [] : [{id: "viewGrant", enabled: true}]),
            ...(row.slices ? [{id: "breakdown", enabled: true}] : []),
        ],
    })), [rows]);

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
            const next = new Set([...previous].filter(key => rowByKey.has(key)));
            return next.size === previous.size ? previous : next;
        });
    }, [rowByKey]);

    const selectedRecipients = useMemo(() => {
        const indices = new Set<number>();
        for (const row of rows) {
            if (selected.has(row.key)) indices.add(row.recipientIndex);
        }
        return [...indices].map(index => props.recipients[index]);
    }, [rows, selected, props.recipients]);

    const selection = useMemo((): UcxTableSelection => ({
        selected,
        onChange: next => {
            const changedGroups = new Map<string, boolean>();
            for (const row of rows) {
                if (next.has(row.key) !== selected.has(row.key)) {
                    changedGroups.set(row.group, next.has(row.key));
                }
            }
            const result = new Set(next);
            for (const row of rows) {
                const checked = changedGroups.get(row.group);
                if (checked === true) result.add(row.key);
                if (checked === false) result.delete(row.key);
            }
            setSelected(result);
        },
    }), [selected, rows]);

    const actionDefs = useMemo((): UcxTableActionDef[] => [
        {id: "edit", label: "Edit allocation", icon: "heroPencil", kind: "custom", shortcut: "e"},
        {id: "viewGrant", label: "View grant application", icon: "heroArrowTopRightOnSquare", kind: "custom"},
        {id: "breakdown", label: "View allocation breakdown", icon: "heroBars3", kind: "custom"},
    ], []);

    const openGrant = props.onOpenGrant ?? openGrantApplication;

    const onRowAction = useCallback((event: UcxTableActionEvent) => {
        const row = rowByKey.get(event.rowKey);
        if (!row) return;

        if (event.actionId === "edit") {
            if (row.slices) {
                setBreakdownKey(row.key);
                return;
            }
            props.onEdit({
                category: row.category,
                allocationId: row.allocationId,
                start: row.start,
                end: row.end,
                quota: row.allocationQuotaRaw,
                workspaceTitle: row.workspaceTitle,
            });
        } else if (event.actionId === "viewGrant" && row.grantId !== undefined) {
            openGrant(row.grantId);
        } else if (event.actionId === "breakdown") {
            setBreakdownKey(row.key);
        }
    }, [openGrant, props.onEdit, rowByKey]);

    const onRowActivated = useCallback((event: UcxTableActivationEvent) => {
        const row = rowByKey.get(event.rowKey);
        if (!row) return;

        if (row.slices) {
            setBreakdownKey(row.key);
        } else if (row.grantId !== undefined) {
            openGrant(row.grantId);
        } else if (row.end >= Date.now()) {
            props.onEdit({
                category: row.category,
                allocationId: row.allocationId,
                start: row.start,
                end: row.end,
                quota: row.allocationQuotaRaw,
                workspaceTitle: row.workspaceTitle,
            });
        }
    }, [openGrant, props.onEdit, rowByKey]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const row = rowByKey.get(context.row.key);
        if (!row) return context.cell;

        switch (context.column.key) {
            case COLUMN_KEYS.workspace:
                return renderWorkspaceCell(row, context.isFirstInGroup);
            case COLUMN_KEYS.product:
                return renderProductCell(row, () => setBreakdownKey(row.key));
            case COLUMN_KEYS.period:
                return renderPeriodCell(row);
            case COLUMN_KEYS.quota:
                if (row.slices) return <TooltipV2 tooltip="Total quota for this package. Active quota belongs to the current slice only.">
                    <div className="allocation-package-quota">
                        <span>{formatNumber(row.allocationQuota, {precision: 2, removeTrailingZeros: true})} <small>{row.unit}</small></span>
                        <small>{formatNumber(row.activeSliceQuota ?? 0, {precision: 2, removeTrailingZeros: true})} active</small>
                    </div>
                </TooltipV2>;
                return renderNumber(row.allocationQuota, row.unit, "Quota for this allocation");
            case COLUMN_KEYS.usage:
                return renderUsageCell(row);
            case COLUMN_KEYS.available:
                return renderNumber(
                    row.productAvailable,
                    row.unit,
                    "Available for the product across all allocations",
                    row.productAvailable < 0,
                );
            case COLUMN_KEYS.grant:
                return renderGrantCell(row, props.renderGrantLink);
            default:
                return context.cell;
        }
    }, [props.renderGrantLink, rowByKey]);

    const breakdownRow = baseRows.find(row => row.key === breakdownKey);
    return <div className={AllocationGridClass}>
        {breakdownRow?.slices ? <AllocationBreakdownDialog
            category={breakdownRow.category}
            workspaceTitle={breakdownRow.workspaceTitle}
            slices={breakdownRow.slices}
            onClose={() => setBreakdownKey(null)}
            onEdit={slice => {
                if (slice.allocationId === undefined) return;
                setBreakdownKey(null);
                props.onEdit({
                    category: breakdownRow.category,
                    allocationId: slice.allocationId,
                    start: slice.start,
                    end: slice.end,
                    quota: slice.quota,
                    workspaceTitle: breakdownRow.workspaceTitle,
                });
            }}
        /> : null}
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
                ><Icon name="heroSquaresPlus" mr={8} />Allocate{selectedRecipients.length > 0 ? ` (${formatNumber(selectedRecipients.length)})` : ""}</Button> : null}
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
            selection={selection}
            actions={actionDefs}
            onRowActivated={onRowActivated}
            onRowAction={onRowAction}
        />
    </div>;
}
