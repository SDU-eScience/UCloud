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
import {TooltipV2} from "@/ui-components/Tooltip";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import type {ProjectInfo} from "@/Project/InfoCache";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {format} from "date-fns";
import {copyToClipboard, isLikelyMac} from "@/UtilityFunctions";
import {SubmitShortcut} from "@/Applications/KeyboardNavigation";
import {Filters, focusTable} from "./Controls";
import {
    compareProducts,
    filterByRange,
    filterByState,
    formatAmount,
    productWorkspaceInfo,
    recipientsFor,
    selectProducts,
    sumAmounts,
} from "./Model";
import type {
    Amounts,
    DateRange,
    EditRequest,
    FilterState,
    Model,
    ProductAllocation,
    Recipient,
    Slice,
} from "./Model";

const lifecycleMetrics = [
    {key: "activeUsed", label: "Active usage", width: 102, title: "Usage that counts against active quota."},
    {
        key: "activeBalance",
        label: "Active balance",
        width: 110,
        title: "Maximum additional usage this wallet can charge, including limits from parent wallets.",
    },
    {key: "activeQuota", label: "Active quota", width: 99, title: "Quota from allocations that are active now."},
    {key: "used", label: "Total used", width: 104, title: "Total usage across active and expired allocations."},
    {key: "expiredUnused", label: "Expired unused", width: 110, title: "Quota that expired without being used."},
    {key: "future", label: "Upcoming quota", width: 111, title: "Granted quota that has not been released yet."},
] as const;

type LifecycleMetric = typeof lifecycleMetrics[number];

const lifecycleColumns = lifecycleMetrics.map(({key, label}) => ({
    key, label, jsonPath: key, sortType: TableColumnSortType.Number, alignment: "right" as const,
}));

const lifecycleWidths = Object.fromEntries(
    lifecycleMetrics.map(({key, width}) => [key, width]),
);

function lifecycleCells(
    amounts: Amounts,
    consumption: boolean,
    maxUsable: number | undefined,
): Record<string, string> {
    return Object.fromEntries(lifecycleMetrics.map(metric => {
        if (!lifecycleApplies(metric, consumption)) return [metric.key, ""];
        const value = metric.key === "activeBalance" ? maxUsable : amounts[metric.key];
        return [metric.key, value === undefined ? "" : `${value}`];
    }));
}

function lifecycleApplies(metric: LifecycleMetric, consumption: boolean): boolean {
    return consumption || metric.key !== "expiredUnused";
}

const DAY_MS = 24 * 60 * 60 * 1000;
const EXPIRING_WINDOW_MS = 30 * DAY_MS;
const estimatedUsageTitle =
    "Estimate: remaining usage is split evenly among active allocations from the same funding parent. "
    + "Usage is not finalized until each allocation expires.";

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
    ...lifecycleWidths,
    [COLUMN_KEYS.quota]: 102,
};

const RECEIVED_COLUMN_WIDTHS: Record<string, number> = {
    ...COLUMN_WIDTHS,
    [COLUMN_KEYS.workspace]: 300,
    activeQuota: 112,
    activeUsed: 112,
    activeBalance: 112,
    [COLUMN_KEYS.quota]: 112,
};

type Mode = "granted" | "received";

const GRANTED_ALL_COLUMNS: TableColumn[] = [
    {
        key: COLUMN_KEYS.workspace,
        label: "Workspace",
        jsonPath: "workspace",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    {
        key: COLUMN_KEYS.product,
        label: "Product",
        jsonPath: "product",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    {
        key: COLUMN_KEYS.period,
        label: "Period",
        jsonPath: "period",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    ...lifecycleColumns.filter(column => column.key.startsWith("active")),
    ...lifecycleColumns.filter(column => column.key === "used"),
    {
        key: COLUMN_KEYS.quota,
        label: "Total granted",
        jsonPath: "quota",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    ...lifecycleColumns.filter(column => column.key === "expiredUnused" || column.key === "future"),
];

const RECEIVED_ALL_COLUMNS: TableColumn[] = [
    {
        key: COLUMN_KEYS.workspace,
        label: "Product",
        jsonPath: "product",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    {
        key: COLUMN_KEYS.period,
        label: "Period",
        jsonPath: "period",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    ...lifecycleColumns.filter(column => column.key === "activeUsed"),
    ...lifecycleColumns.filter(column => column.key === "activeBalance")
        .map(column => ({...column, label: "Active balance"})),
    ...lifecycleColumns.filter(column => column.key === "activeQuota")
        .map(column => ({...column, label: "Active quota"})),
    ...lifecycleColumns.filter(column => column.key === "used"),
    {
        key: COLUMN_KEYS.quota,
        label: "Total received",
        jsonPath: "quota",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    ...lifecycleColumns.filter(column => column.key === "expiredUnused" || column.key === "future"),
];

const advancedColumns = new Set(["expiredUnused", "future"]);

function buildColumnOptions(columns: TableColumn[]) {
    return columns.filter(column => column.key !== COLUMN_KEYS.workspace)
        .map(({key, label}) => ({key, label, advanced: advancedColumns.has(key)}))
        .sort((a, b) => Number(a.advanced) - Number(b.advanced));
}

function buildDefaultColumns(columns: TableColumn[]): string[] {
    return buildColumnOptions(columns).filter(column => !column.advanced).map(column => column.key);
}

const shortcutGuide = [
    {keys: ["↑", "↓"], action: "Move"},
    {keys: ["←", "→"], action: "Expand or collapse"},
    {keys: ["Enter"], action: "Open"},
    {keys: ["Space"], action: "Select"},
    {keys: ["x"], action: "Actions"},
    {keys: ["e"], action: "Edit"},
    {keys: ["/"], action: "Search"},
    {keys: ["Esc"], action: "Return"},
    {keys: isLikelyMac ? ["⌘", "⌥"] : ["Ctrl", "Alt"], action: "Jump shortcuts"},
];

const receivedShortcutGuide = shortcutGuide.filter(entry =>
    entry.action !== "Edit" && entry.action !== "Select"
);

interface TableProps {
    model: Model;
    mode?: Mode;
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange?: DateRange;
    selectedCategories?: string[];
    onCategoriesChange?: (categories: string[]) => void;
    personalWorkspaces?: boolean;
    onPersonalWorkspacesChange?: () => void;
    onClearFilters?: () => void;
    toolbar?: React.ReactNode;
    onAllocate?: (recipients: Recipient[]) => void;
    onViewUsage?: (product: ProductAllocation) => void;
    periodControl?: React.ReactNode;
    emptyMessage?: string;
    onEdit?: (request: EditRequest) => void;
    onRetire?: (allocationIds: number[], workspaceTitle: string, grantId?: number) => void;
    onVisibleProductsChange?: (products: ProductAllocation[]) => void;
}

interface Row {
    key: string;
    group: string;
    category: ProductCategoryV2;
    start: number;
    end: number;
    grantId?: number;
    workspaceTitle: string;
    workspaceKey: string;
    workspacePi: string;
    workspaceType: "project" | "user" | "root";
    productName: string;
    provider: string;
    unit: string;
    allocationQuota: number;
    accounting: ProductAllocation;
    slices: Slice[];
    usage?: RowUsage;
}

interface RowUsage {
    value: number;
    approximate: boolean;
}

function sliceUsage(slice: Slice): RowUsage | undefined {
    if (slice.estimatedUsed !== undefined) return {value: slice.estimatedUsed, approximate: true};
    if (slice.attribution === "recorded" && slice.used !== null) return {value: slice.used, approximate: false};
    return undefined;
}

type LifecycleKey = typeof lifecycleMetrics[number]["key"];

interface ReceivedMetricValue {
    value: number;
    approximate?: boolean;
}

type ReceivedMetricValues = Partial<Record<LifecycleKey, ReceivedMetricValue>>;

const receivedMetricTitles: Record<LifecycleKey, string> = {
    activeQuota: "Quota received from funding sources that is active now.",
    activeUsed: "Usage charged against active received quota.",
    activeBalance: "Active quota minus active usage.",
    used: "Total usage across received allocations.",
    expiredUnused: "Received quota that expired without being used.",
    future: "Received quota that has not been released yet.",
};

function receivedMetricTitle(key: LifecycleKey, consumption: boolean): string {
    if (key === "used" && !consumption) return "Current capacity in use.";
    return receivedMetricTitles[key];
}

function grantMetricValues(
    grant: {slices: Slice[]; used: number | null},
    now: number,
    consumption: boolean,
): ReceivedMetricValues {
    let activeQuota = 0;
    let activeUsed = 0;
    let activeUsageKnown = true;
    let activeApproximate = false;
    let future = 0;
    for (const slice of grant.slices) {
        if (slice.start > now && !slice.retired) future += slice.quota;
        if (slice.retired || slice.start > now || slice.end <= now) continue;
        activeQuota += slice.quota;
        const usage = sliceUsage(slice);
        if (usage) {
            activeUsed += usage.value;
            activeApproximate ||= usage.approximate;
        } else {
            activeUsageKnown = false;
        }
    }
    const result: ReceivedMetricValues = {
        activeQuota: {value: activeQuota},
        activeUsed: activeUsageKnown ? {value: activeUsed, approximate: activeApproximate} : undefined,
        activeBalance: activeUsageKnown ? {value: activeQuota - activeUsed} : undefined,
        used: grant.used === null ? undefined : {
            value: grant.used,
            approximate: grant.slices.some(slice => slice.estimatedUsed !== undefined),
        },
        future: {value: future},
    };
    if (consumption) {
        let expiredUnused = 0;
        let expiredKnown = true;
        for (const slice of grant.slices) {
            if (!slice.retired) continue;
            const usage = sliceUsage(slice);
            if (!usage) {
                expiredKnown = false;
                break;
            }
            expiredUnused += slice.quota - usage.value;
        }
        result.expiredUnused = expiredKnown ? {value: expiredUnused} : undefined;
    }
    return result;
}

function sliceMetricValues(
    slice: Slice,
    now: number,
    consumption: boolean,
): ReceivedMetricValues {
    const usage = sliceUsage(slice);
    const active = !slice.retired && slice.start <= now && slice.end > now;
    const expiredUnused = !consumption
        ? undefined
        : slice.retired
            ? usage ? {value: slice.quota - usage.value} : undefined
            : {value: 0};
    return {
        activeQuota: {value: active ? slice.quota : 0},
        activeUsed: active
            ? usage ? {value: usage.value, approximate: usage.approximate} : undefined
            : {value: 0},
        activeBalance: active
            ? usage ? {value: slice.quota - usage.value} : undefined
            : {value: 0},
        used: usage ? {value: usage.value, approximate: usage.approximate} : undefined,
        expiredUnused,
        future: {value: slice.start > now ? slice.quota : 0},
    };
}

function receivedMetricCells(values: ReceivedMetricValues): Record<string, string> {
    return Object.fromEntries(lifecycleMetrics.map(metric => {
        const value = values[metric.key];
        return [metric.key, value === undefined ? "" : `${value.value}`];
    }));
}

interface ProductRow extends Row {
    allocations: Row[];
}

interface ReceivedWalletRow {
    key: string;
    group: string;
    category: ProductCategoryV2;
    unit: string;
    start: number;
    end: number;
    amounts: Amounts;
    maxUsable: number;
    sources: ProductRow[];
}

type DisplayRow =
    | {kind: "wallet"; wallet: ReceivedWalletRow}
    | {kind: "product"; product: ProductRow}
    | {kind: "grant"; grant: Row}
    | {kind: "allocation"; grant: Row; allocation: Slice};

function sliceKey(allocationKey: string, slice: Slice): string {
    return `${allocationKey}/slice:${slice.allocationId}`;
}

function grantRowKey(productKey: string, grant: {key: string; grantId?: number}): string {
    return grant.grantId === undefined ? grant.key : `${productKey}/grant:${grant.grantId}`;
}

function canRetire(allocation: Slice, now: number): boolean {
    return !allocation.retired && allocation.end > now;
}

function periodCell(
    now: number,
    start: number,
    end: number,
    precise = false,
    retired = false,
): React.ReactNode {
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
                {!upcoming && !expired ?
                    <span className="allocation-period-now" style={{left: `${percentage}%`}} /> :
                    null}
            </div>
            <span className="allocation-period-note">{note}</span>
        </div>
    </div>;
}

const TableClass = injectStyle("allocation-grid", k => `
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

    ${k} .allocation-group-rail-last::before {
        bottom: 50%;
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

    ${k} .allocation-child-label, ${k} .allocation-slice-label {
        display: flex;
        align-items: center;
        gap: 5px;
    }

    ${k} .allocation-source-label {
        display: flex;
        align-items: center;
        gap: 5px;
        min-width: 0;
    }

    ${k} .allocation-source-name {
        display: flex;
        flex: 1 1 auto;
        min-width: 0;
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
    ${k} .allocation-id-copy:focus-visible {
        opacity: 1;
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

export function Table(props: TableProps): React.ReactNode {
    const mode = props.mode ?? "granted";
    const modeColumns = mode === "received" ? RECEIVED_ALL_COLUMNS : GRANTED_ALL_COLUMNS;
    const allProducts = mode === "received" ? props.model.receivedProducts : props.model.products;
    const columnOptions = buildColumnOptions(modeColumns);
    const defaultColumns = buildDefaultColumns(modeColumns);
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
    const [selectedSources, setSelectedSources] = useState<string[]>([]);
    const [visibleColumns, setVisibleColumns] = useState<string[]>(() => defaultColumns);
    const [filterState, setFilterState] = useState<FilterState>("all");
    const personalWorkspaces = props.personalWorkspaces ?? false;
    const productKeys = props.selectedCategories ?? selectedProducts;
    const setProductKeys = props.onCategoriesChange ?? setSelectedProducts;

    useLayoutEffect(() => {
        const frame = requestAnimationFrame(() => focusTable());
        return () => cancelAnimationFrame(frame);
    }, []);

    const sourceFilteredProducts = useMemo(() => {
        if (selectedSources.length === 0) return allProducts;
        return allProducts.filter(product => selectedSources.includes(product.workspaceKey));
    }, [allProducts, selectedSources]);

    const matchingProducts = useMemo(() => filterByRange(
        selectProducts(props.model, {
            dateRange: props.dateRange,
            personalWorkspaces: mode === "received" ? true : personalWorkspaces,
            categories: productKeys,
            providers: selectedProviders,
            query,
            projectInfo: props.projectInfo,
        }, sourceFilteredProducts),
        props.dateRange,
        props.model.now,
    ), [
        props.model,
        props.model.now,
        props.dateRange,
        personalWorkspaces,
        mode,
        productKeys,
        selectedProviders,
        selectedSources,
        query,
        props.projectInfo,
        sourceFilteredProducts,
    ]);

    const visibleProducts = useMemo(
        () => filterByState(matchingProducts, filterState, props.model.now),
        [matchingProducts, filterState, props.model.now],
    );
    const retireableByGrant = useMemo(() => {
        const result = new Map<string, Slice[]>();
        for (const product of matchingProducts) {
            for (const grant of product.grants) {
                const allocations = grant.slices.filter(slice => canRetire(slice, props.model.now));
                if (!allocations.length) continue;
                const key = grantRowKey(product.key, grant);
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

    const rows = useMemo((): ProductRow[] => visibleProducts.map(product => {
        const workspace = productWorkspaceInfo(product, props.projectInfo);
        const grantsByKey = new Map<string, Row>();
        for (const grant of product.grants) {
            const key = grantRowKey(product.key, grant);
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
                const usage = sliceUsage(slice);
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

    const walletRows = useMemo((): ReceivedWalletRow[] => {
        if (mode !== "received") return [];
        const wallets = new Map<string, ReceivedWalletRow>();
        for (const source of rows) {
            const key = `wallet:${source.category.provider}/${source.category.name}`;
            const existing = wallets.get(key);
            if (existing) {
                existing.sources.push(source);
                existing.start = Math.min(existing.start, source.start);
                existing.end = Math.max(existing.end, source.end);
                continue;
            }
            wallets.set(key, {
                key,
                group: key,
                category: source.category,
                unit: source.unit,
                start: source.start,
                end: source.end,
                amounts: sumAmounts([]),
                maxUsable: 0,
                sources: [source],
            });
        }
        for (const wallet of wallets.values()) {
            wallet.amounts = sumAmounts(wallet.sources.map(source => source.accounting));
            wallet.maxUsable = wallet.amounts.activeBalance;
        }
        return [...wallets.values()];
    }, [mode, rows]);

    const products = useMemo(() => {
        const options = new Map<string, ProductCategoryV2>();
        for (const product of allProducts) {
            options.set(`${product.category.provider}/${product.category.name}`, product.category);
        }
        return [...options.values()].sort(compareProducts).map(category => ({
            key: `${category.provider}/${category.name}`,
            label: `${category.name} (${getShortProviderTitle(category.provider)})`,
            icon: <Icon name={productTypeToIcon(category.productType)} size={16} />,
        }));
    }, [allProducts]);

    const providers = useMemo(() => {
        const keys = new Set(allProducts.map(product => product.category.provider));
        return [...keys].map(key => ({
            key,
            label: getShortProviderTitle(key),
            icon: <ProviderLogo providerId={key} size={18} />,
        })).sort((a, b) => a.label.localeCompare(b.label));
    }, [allProducts]);

    const sources = useMemo(() => {
        if (mode !== "received") return [];
        const options = new Map<string, string>();
        for (const product of allProducts) {
            const source = productWorkspaceInfo(product, props.projectInfo);
            options.set(source.id, source.title);
        }
        return [...options.entries()].map(([key, label]) => ({
            key,
            label,
            icon: <Icon name="heroBuildingOffice2" size={16} />,
        })).sort((a, b) => a.label.localeCompare(b.label));
    }, [allProducts, mode, props.projectInfo]);
    const counts = useMemo(() => {
        const workspaces: Record<FilterState, Set<string>> = {
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
        const result = new Map<string, DisplayRow>();
        for (const wallet of walletRows) {
            result.set(wallet.key, {kind: "wallet", wallet});
        }
        for (const row of rows) {
            result.set(row.key, {kind: "product", product: row});
            for (const allocation of row.allocations) {
                result.set(allocation.key, {kind: "grant", grant: allocation});
                allocation.slices.forEach(slice => {
                    result.set(sliceKey(allocation.key, slice), {
                        kind: "allocation",
                        grant: allocation,
                        allocation: slice,
                    });
                });
            }
        }
        return result;
    }, [rows, walletRows]);

    const treeParents = useMemo(() => {
        const result = new Map<string, string>();
        if (mode === "received") {
            for (const wallet of walletRows) {
                for (const source of wallet.sources) {
                    result.set(source.key, wallet.key);
                    for (const allocation of source.allocations) {
                        result.set(allocation.key, source.key);
                        allocation.slices.forEach(slice => {
                            result.set(sliceKey(allocation.key, slice), allocation.key);
                        });
                    }
                }
            }
            return result;
        }
        const firstProductByGroup = new Map<string, string>();
        for (const row of rows) {
            const firstProduct = firstProductByGroup.get(row.group);
            if (firstProduct === undefined) firstProductByGroup.set(row.group, row.key);
            else result.set(row.key, firstProduct);
            for (const allocation of row.allocations) {
                result.set(allocation.key, row.key);
                allocation.slices.forEach(slice => {
                    result.set(sliceKey(allocation.key, slice), allocation.key);
                });
            }
        }
        return result;
    }, [mode, rows, walletRows]);

    const onColumnsChange = (next: string[]) => {
        setVisibleColumns(next);
        const sort = store.sortFor("allocation-grid");
        if (sort && sort.columnKey !== COLUMN_KEYS.workspace && !next.includes(sort.columnKey)) {
            store.setSort("allocation-grid", {columnKey: COLUMN_KEYS.workspace, ascending: true});
        }
    };

    const visibleColumnIndexes = useMemo(() => modeColumns.flatMap((column, index) =>
        column.key === COLUMN_KEYS.workspace || visibleColumns.includes(column.key) ? [index] : []
    ), [modeColumns, visibleColumns]);
    const columns = useMemo(
        () => visibleColumnIndexes.map(index => modeColumns[index]),
        [modeColumns, visibleColumnIndexes],
    );

    const canAllocate = mode === "granted" && !!props.onAllocate;

    const {tableRows, rowChildren} = useMemo(() => {
        const tableByKey = new Map<string, TableRow>();
        for (const [key, entry] of rowByKey) {
            if (entry.kind === "wallet") {
                const wallet = entry.wallet;
                const cells: Record<string, string> = {
                    [COLUMN_KEYS.workspace]: `${wallet.category.name} ${wallet.category.provider}`,
                    [COLUMN_KEYS.period]: `${wallet.start} ${wallet.end}`,
                    [COLUMN_KEYS.quota]: `${wallet.amounts.granted}`,
                    ...lifecycleCells(
                        wallet.amounts,
                        wallet.category.accountingModel === "CONSUMPTION",
                        wallet.maxUsable,
                    ),
                };
                tableByKey.set(key, {
                    key,
                    group: wallet.group,
                    cells: columns.map(column => cells[column.key] ?? ""),
                    actions: props.onViewUsage ? [{id: "viewUsage", enabled: true}] : [],
                });
                continue;
            }
            const row = entry.kind === "product" ? entry.product : entry.grant;
            const allocation = entry.kind === "allocation" ? entry.allocation : undefined;
            const consumption = row.category.accountingModel === "CONSUMPTION";
            const usage = allocation ? sliceUsage(allocation) : row.usage;
            const end = allocation ? Math.max(allocation.start, allocation.end - 1) : row.end;
            const quota = allocation?.quota ?? row.allocationQuota;
            let metricCells: Record<string, string>;
            if (entry.kind === "product") {
                metricCells = lifecycleCells(
                    row.accounting.amounts,
                    consumption,
                    row.accounting.maxUsable,
                );
            } else if (mode === "received") {
                const values = entry.kind === "grant"
                    ? grantMetricValues(
                        {slices: entry.grant.slices, used: entry.grant.usage?.value ?? null},
                        props.model.now,
                        consumption,
                    )
                    : sliceMetricValues(entry.allocation, props.model.now, consumption);
                metricCells = receivedMetricCells(values);
            } else {
                metricCells = {used: usage ? `${usage.value}` : ""};
            }
            const firstCell = entry.kind === "product"
                ? mode === "received" ? row.workspaceTitle : `${row.workspaceTitle} ${row.workspacePi}`
                : entry.kind === "grant"
                    ? `${row.grantId ?? ""} ${row.slices.length}`
                    : `${entry.allocation.allocationId}`;
            const cells: Record<string, string> = {
                [COLUMN_KEYS.workspace]: firstCell,
                [COLUMN_KEYS.product]: row.productName,
                [COLUMN_KEYS.period]: `${allocation?.start ?? row.start} ${end}`,
                [COLUMN_KEYS.quota]: `${quota}`,
                ...metricCells,
            };
            tableByKey.set(key, {
                key,
                group: row.group,
                cells: columns.map(column => cells[column.key] ?? ""),
                actions: mode === "received" ? (
                    entry.kind === "product" ? [
                        ...(props.onViewUsage ? [{id: "viewUsage", enabled: true}] : []),
                    ] : entry.kind === "grant" ? [
                        ...(row.grantId !== undefined ? [{id: "viewGrant", enabled: true}] : []),
                        ...(props.onViewUsage ? [{id: "viewUsage", enabled: true}] : []),
                    ] : [
                        ...(entry.grant.grantId !== undefined ? [{id: "viewGrant", enabled: true}] : []),
                        ...(props.onViewUsage ? [{id: "viewUsage", enabled: true}] : []),
                    ]
                ) : entry.kind === "product" ? [
                    ...(canAllocate ? [{id: "allocate", enabled: true}] : []),
                ] : entry.kind === "grant" ? [
                    ...(row.grantId !== undefined ? [{id: "viewGrant", enabled: true}] : []),
                    ...(props.onRetire && retireableByGrant.has(row.key) ?
                        [{id: "retire", enabled: true}] : []),
                ] : [
                    ...(canRetire(entry.allocation, props.model.now) ? [
                        ...(props.onRetire ? [{id: "retire", enabled: true}] : []),
                        ...(props.onEdit ? [{id: "edit", enabled: true}] : []),
                    ] : []),
                ],
            });
        }
        const children = new Map<string, TableRow[]>();
        if (mode === "received") {
            for (const wallet of walletRows) {
                children.set(wallet.key, wallet.sources.map(source => tableByKey.get(source.key)!));
                for (const source of wallet.sources) {
                    children.set(source.key, source.allocations.map(allocation => tableByKey.get(allocation.key)!));
                    for (const allocation of source.allocations) {
                        children.set(allocation.key, allocation.slices.map(slice =>
                            tableByKey.get(sliceKey(allocation.key, slice))!
                        ));
                    }
                }
            }
            return {
                tableRows: walletRows.map(wallet => tableByKey.get(wallet.key)!),
                rowChildren: children,
            };
        }
        for (const row of rows) {
            children.set(row.key, row.allocations.map(allocation => tableByKey.get(allocation.key)!));
            for (const allocation of row.allocations) {
                children.set(allocation.key, allocation.slices.map(slice =>
                    tableByKey.get(sliceKey(allocation.key, slice))!
                ));
            }
        }
        return {
            tableRows: rows.map(row => tableByKey.get(row.key)!),
            rowChildren: children,
        };
    }, [
        rows,
        walletRows,
        rowByKey,
        props.model.now,
        columns,
        retireableByGrant,
        mode,
        canAllocate,
        props.onAllocate,
        props.onEdit,
        props.onRetire,
        props.onViewUsage,
    ]);

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
        if (mode === "received") return [];
        const workspaces = new Set<string>();
        for (const row of rows) {
            if (selected.has(row.key)) workspaces.add(row.workspaceKey);
        }
        return recipientsFor(props.model, workspaces);
    }, [mode, rows, selected, props.model]);

    const selection: UcxTableSelection | undefined = mode === "granted"
        ? {selected, onChange: setSelected}
        : undefined;

    const actionDefs = useMemo((): UcxTableActionDef[] => {
        if (mode === "received") {
            return [
                {id: "viewGrant", label: "Open grant application", icon: "heroArrowTopRightOnSquare", kind: "custom"},
                ...(props.onViewUsage ? [{
                    id: "viewUsage",
                    label: "View usage",
                    icon: "heroPresentationChartLine" as const,
                    kind: "custom" as const,
                }] : []),
            ];
        }
        return [
            {id: "allocate", label: "Allocate", icon: "heroSquaresPlus", kind: "custom"},
            {id: "viewGrant", label: "Open grant application", icon: "heroArrowTopRightOnSquare", kind: "custom"},
            {id: "retire", label: "Retire", icon: "heroClock", kind: "custom"},
            {id: "edit", label: "Edit", icon: "heroPencil", kind: "custom", shortcut: "e"},
        ];
    }, [mode, props.onViewUsage]);

    const toggleExpanded = useCallback((key: string) => {
        setExpanded(previous => {
            const next = new Set(previous);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
        });
    }, []);

    const focusRow = useCallback((_key: string) => {
        requestAnimationFrame(() => {
            const view = store.viewFor("allocation-grid");
            if (view) {
                view.focusSelected();
            } else {
                gridRef.current?.querySelector<HTMLElement>("[data-ucx-table]")?.focus({preventScroll: true});
            }
        });
    }, [store]);

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
        const branch = entry.kind === "grant" ||
            (mode === "received" && (entry.kind === "wallet" || entry.kind === "product"));
        if (branch && hasChildren) {
            event.preventDefault();
            event.stopPropagation();
            toggleExpanded(rowKey);
            focusRow(rowKey);
        }
    }, [expanded, focusRow, mode, rowByKey, rowChildren, selectRow, toggleExpanded, treeParents]);

    const openGrant = useCallback((grantId: number) => {
        window.open(window.origin + "/app" + AppRoutes.grants.editor(grantId), "_blank");
    }, []);

    const editRow = useCallback((key: string) => {
        const entry = rowByKey.get(key);
        if (!entry) return;
        if (entry.kind !== "allocation") {
            setExpanded(previous => new Set([...previous, key]));
            return;
        }
        const row = entry.grant;
        const slice = entry.allocation;
        if (!canRetire(slice, props.model.now)) return;
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
        if (event.actionId === "allocate" && entry.kind === "product" && mode === "granted") {
            props.onAllocate?.(selected.has(entry.product.key) ? selectedRecipients :
                recipientsFor(props.model, new Set([entry.product.workspaceKey])));
        } else if (event.actionId === "viewUsage") {
            if (entry.kind === "wallet") {
                const source = entry.wallet.sources[0];
                if (source) props.onViewUsage?.(source.accounting);
            } else if (entry.kind === "product") {
                props.onViewUsage?.(entry.product.accounting);
            } else {
                props.onViewUsage?.(entry.grant.accounting);
            }
        } else if (event.actionId === "edit" && entry.kind === "allocation") {
            editRow(event.rowKey);
        } else if (event.actionId === "viewGrant" && (entry.kind === "grant" || entry.kind === "allocation")) {
            const grantId = entry.grant.grantId;
            if (grantId !== undefined) openGrant(grantId);
        } else if (event.actionId === "retire" && props.onRetire) {
            if (entry.kind === "allocation" && canRetire(entry.allocation, props.model.now)) {
                props.onRetire([entry.allocation.allocationId], entry.grant.workspaceTitle);
            } else if (entry.kind === "grant") {
                const allocations = retireableByGrant.get(entry.grant.key);
                if (!allocations?.length) return;
                const ids = [...allocations].sort((a, b) => a.end - b.end || a.allocationId - b.allocationId)
                    .map(allocation => allocation.allocationId);
                props.onRetire(ids, entry.grant.workspaceTitle, entry.grant.grantId);
            }
        }
    }, [
        editRow,
        rowByKey,
        openGrant,
        mode,
        props.model,
        props.onAllocate,
        props.onViewUsage,
        props.onRetire,
        retireableByGrant,
        selected,
        selectedRecipients,
    ]);

    const onRowActivated = useCallback((event: UcxTableActivationEvent) => {
        const entry = rowByKey.get(event.rowKey);
        if (!entry) return;
        if (entry.kind !== "allocation") {
            toggleExpanded(event.rowKey);
        } else if (mode === "received") {
            if (entry.grant.grantId !== undefined) {
                openGrant(entry.grant.grantId);
            } else {
                copyToClipboard(String(entry.allocation.allocationId));
            }
        } else {
            editRow(event.rowKey);
        }
    }, [rowByKey, toggleExpanded, editRow, mode, openGrant]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const entry = rowByKey.get(context.row.key);
        if (!entry) return context.cell;
        if (mode === "received") {
            const metric = lifecycleMetrics.find(metric => metric.key === context.column.key);
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

            if (entry.kind === "wallet") {
                const wallet = entry.wallet;
                if (context.column.key === COLUMN_KEYS.workspace) {
                    return <div className="allocation-product">
                        {expandButton(`${wallet.category.name} sources`)}
                        <span className="allocation-icon-slot">
                            <ProviderLogo providerId={wallet.category.provider} size={14} />
                        </span>
                        <span className="allocation-product-name">{wallet.category.name}</span>
                        <span className="allocation-product-meta">
                            {getShortProviderTitle(wallet.category.provider)}
                        </span>
                    </div>;
                }
                if (metric) {
                    const value = metric.key === "activeBalance"
                        ? wallet.maxUsable
                        : wallet.amounts[metric.key];
                    return <Amount
                        value={value}
                        unit={wallet.unit}
                        title={receivedMetricTitle(
                            metric.key,
                            wallet.category.accountingModel === "CONSUMPTION",
                        )}
                    />;
                }
                if (context.column.key === COLUMN_KEYS.period) {
                    return periodCell(props.model.now, wallet.start, wallet.end, false, false);
                }
                if (context.column.key === COLUMN_KEYS.quota) {
                    return <Amount
                        value={wallet.amounts.granted}
                        unit={wallet.unit}
                        title="Total received, including active, expired and upcoming allocations."
                    />;
                }
                return context.cell;
            }

            const row = entry.kind === "product" ? entry.product : entry.grant;
            const slice = entry.kind === "allocation" ? entry.allocation : undefined;
            if (context.column.key === COLUMN_KEYS.workspace) {
                if (entry.kind === "product") {
                    return <div className="allocation-source-label">
                        <span className="allocation-indent" />
                        <span className="allocation-indent" />
                        {expandButton(`${row.workspaceTitle} grant applications`)}
                        <span className="allocation-source-name">
                            <WorkspaceLabel
                                title={row.workspaceTitle}
                                meta={row.workspaceType === "user" ? "Personal" : undefined}
                            />
                        </span>
                    </div>;
                }
                if (entry.kind === "grant") {
                    const grantId = entry.grant.grantId;
                    const sliceCount = entry.grant.slices.length;
                    return <div className="allocation-child-label">
                        <span className="allocation-indent" />
                        <span className="allocation-indent" />
                        <span className="allocation-indent" />
                        {expandButton(grantId === undefined ? "unlinked slices" : `grant #${grantId} slices`)}
                        <span>
                            {grantId === undefined ? "No grant" :
                                <Link
                                    to={AppRoutes.grants.editor(grantId)}
                                    target="_blank"
                                    onClick={event => event.stopPropagation()}
                                >{`#${grantId}`}</Link>}
                            {` (${sliceCount} ${sliceCount === 1 ? "slice" : "slices"})`}
                        </span>
                    </div>;
                }
                return <div className="allocation-slice-label">
                    <span className="allocation-indent" />
                    <span className="allocation-indent" />
                    <span className="allocation-indent" />
                    <span className="allocation-indent" />
                    <span className="allocation-icon-slot" />
                    <span>{slice!.allocationId.toString().padStart(6, "0")}</span>
                    <span className="allocation-workspace-meta">
                        {slice!.retired ? "Expired" : slice!.start > props.model.now ? "Upcoming" : "Active"}
                    </span>
                    <button
                        type="button"
                        className={`${UcxTableIconButtonClass} allocation-id-copy`}
                        title="Copy allocation ID"
                        aria-label="Copy allocation ID"
                        onClick={event => {
                            event.stopPropagation();
                            copyToClipboard(String(slice!.allocationId));
                        }}
                        onDoubleClick={event => event.stopPropagation()}
                        onKeyDown={event => {
                            if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                        }}
                    ><Icon name="heroDocumentDuplicate" size={14} /></button>
                </div>;
            }
            if (metric) {
                if (entry.kind === "product") {
                    const value = metric.key === "activeBalance"
                        ? row.accounting.maxUsable ?? row.accounting.amounts.activeBalance
                        : row.accounting.amounts[metric.key];
                    return <Amount
                        value={value}
                        unit={row.unit}
                        title={receivedMetricTitle(
                            metric.key,
                            row.category.accountingModel === "CONSUMPTION",
                        )}
                    />;
                }
                const consumption = row.category.accountingModel === "CONSUMPTION";
                const values = entry.kind === "grant"
                    ? grantMetricValues(
                        {slices: entry.grant.slices, used: entry.grant.usage?.value ?? null},
                        props.model.now,
                        consumption,
                    )
                    : sliceMetricValues(entry.allocation, props.model.now, consumption);
                const value = values[metric.key];
                if (!value) return null;
                return <Amount
                    value={value.value}
                    unit={row.unit}
                    approximate={value.approximate}
                    title={receivedMetricTitle(metric.key, consumption)}
                />;
            }
            if (context.column.key === COLUMN_KEYS.period) {
                if (slice) {
                    return periodCell(
                        props.model.now,
                        slice.start,
                        Math.max(slice.start, slice.end - 1),
                        true,
                        slice.retired,
                    );
                }
                return periodCell(
                    props.model.now,
                    row.start,
                    Math.max(row.start, row.end),
                    false,
                    row.slices.every(allocation => allocation.retired),
                );
            }
            if (context.column.key === COLUMN_KEYS.quota) {
                const value = entry.kind === "product" ? row.accounting.amounts.granted :
                    entry.kind === "grant" ? row.allocationQuota : entry.allocation.quota;
                return <Amount
                    value={value}
                    unit={row.unit}
                    title={entry.kind === "allocation"
                        ? "Quota received on this allocation."
                        : "Total received, including the full release schedule."}
                />;
            }
            return context.cell;
        }
        if (entry.kind === "wallet") return context.cell;
        const row = entry.kind === "product" ? entry.product : entry.grant;
        const allocation = entry.kind === "allocation" ? entry.allocation : undefined;
        const metric = lifecycleMetrics.find(metric => metric.key === context.column.key);
        const consumption = row.category.accountingModel === "CONSUMPTION";
        if (metric) {
            if (entry.kind === "product") return <LifecycleAmount
                metric={metric}
                amounts={row.accounting.amounts}
                consumption={consumption}
                unit={row.unit}
                maxUsable={row.accounting.maxUsable}
            />;
            if (metric.key === "used") {
                const usage = allocation ? sliceUsage(allocation) : row.usage;
                if (!usage) return null;
                const usageTitle = usage.approximate
                    ? estimatedUsageTitle
                    : allocation
                        ? "Usage recorded when this allocation expired."
                        : "Total usage recorded on the allocations in this grant.";
                return <Amount
                    value={usage.value}
                    unit={row.unit}
                    approximate={usage.approximate}
                    title={usageTitle}
                />;
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
                const railClass = context.isLastInGroup
                    ? "allocation-group-rail allocation-group-rail-last"
                    : "allocation-group-rail";
                return <span className={railClass} aria-hidden="true" />;
            }
            return <WorkspaceLabel
                title={row.workspaceTitle}
                meta={row.workspaceType === "root" ? undefined :
                    row.workspaceType === "user" ? "Personal" :
                        row.workspacePi ? `PI ${row.workspacePi}` : undefined}
                metaTooltip={row.workspaceType === "root" ? undefined : row.workspacePi}
            />;
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
                            <Link
                                to={AppRoutes.grants.editor(grantId)}
                                target="_blank"
                                onClick={event => event.stopPropagation()}
                            >#{grantId}</Link>}
                        {` (${sliceCount} ${sliceCount === 1 ? "slice" : "slices"})`}
                    </span>
                </div>;
            }
            case COLUMN_KEYS.period:
                if (allocation) {
                    return periodCell(
                        props.model.now,
                        allocation.start,
                        Math.max(allocation.start, allocation.end - 1),
                        true,
                        allocation.retired,
                    );
                }
                return periodCell(
                    props.model.now,
                    row.start,
                    Math.max(row.start, row.end),
                    false,
                    entry.kind === "grant" && row.slices.every(slice => slice.retired),
                );
            case COLUMN_KEYS.quota: {
                let title = allocation
                    ? "Quota granted to this allocation."
                    : "Total granted, including the full release schedule.";
                if (entry.kind === "product" && consumption) {
                    const used = formatAmount(row.accounting.amounts.used);
                    title += ` Total used across active and expired allocations: ${used} ${row.unit}.`;
                }
                return <Amount
                    value={allocation?.quota ?? row.allocationQuota}
                    unit={row.unit}
                    title={title}
                />;
            }
            default:
                return context.cell;
        }
    }, [expanded, rowByKey, store, toggleExpanded, props.model.now, mode]);

    return <div ref={gridRef} className={TableClass} onKeyDown={onGridKeyDown}>
        <Filters
            query={query}
            onQueryChange={setQuery}
            onSearchExit={() => focusTable()}
            periodControl={props.periodControl}
            actions={<div style={{display: "flex", flexWrap: "wrap", gap: 8}}>
                {canAllocate ? <Button
                    height={35}
                    data-allocation-shortcut="allocate"
                    ariaKeyshortcuts="Control+Alt+A Meta+Alt+A"
                    onClick={() => props.onAllocate?.(selectedRecipients)}
                ><Icon name="heroSquaresPlus" mr={8} />
                    Allocate
                    <SubmitShortcut shortcut="A" />
                </Button> : null}
                {props.toolbar}
            </div>}
            products={products}
            providers={providers}
            sources={mode === "received" ? sources : undefined}
            columns={columnOptions}
            selectedProducts={productKeys}
            selectedProviders={selectedProviders}
            selectedSources={selectedSources}
            visibleColumns={visibleColumns}
            onProductsChange={setProductKeys}
            onProvidersChange={setSelectedProviders}
            onSourcesChange={setSelectedSources}
            onColumnsChange={onColumnsChange}
            state={filterState}
            onStateChange={setFilterState}
            counts={counts}
            workspaceCount={mode === "received" ? walletRows.length : new Set(rows.map(row => row.group)).size}
            groupLabel={mode === "received" ? "product" : "workspace"}
            personalWorkspaces={personalWorkspaces}
            showPersonalWorkspaces={mode !== "received"}
            onPersonalWorkspacesChange={props.onPersonalWorkspacesChange ?? (() => void 0)}
            searchPlaceholder={mode === "received" ? "Search source, PI or product…" : undefined}
            onClear={() => {
                setQuery("");
                setProductKeys([]);
                setSelectedProviders([]);
                setSelectedSources([]);
                setFilterState("all");
                props.onClearFilters?.();
            }}
        />
        <UcxStreamedTable
            tableId="allocation-grid"
            store={store}
            stateKey="allocation-grid"
            viewId="allocation-grid"
            shortcutGuide={mode === "received" ? receivedShortcutGuide : shortcutGuide}
            emptyMessage={query || productKeys.length > 0 || selectedProviders.length > 0 ||
                selectedSources.length > 0 || filterState !== "all"
                ? "No allocations match the current search, filters or dates."
                : props.emptyMessage ?? (props.dateRange?.start || props.dateRange?.end
                    ? "No allocations overlap these dates."
                    : mode === "received" ? "No received allocations yet." : "No sub-allocations yet.")}
            showGroupHeaders={false}
            inlineGroups={mode === "granted"}
            inlineGroupColumn={mode === "granted" ? COLUMN_KEYS.workspace : undefined}
            density="compact"
            rowHeight={45}
            columnWidths={mode === "received" ? RECEIVED_COLUMN_WIDTHS : COLUMN_WIDTHS}
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

const WorkspaceLabelStyle = injectStyle("allocation-workspace-label", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        min-width: 0;
        text-align: left;
    }
    ${k} .workspace-tooltip {
        display: flex;
        flex-direction: column;
        min-width: 0;
        max-width: 100%;
    }
    ${k} .workspace-title, ${k} .workspace-meta {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    ${k} .workspace-title {
        color: var(--textPrimary);
        font-size: 12.5px;
        font-weight: 600;
    }
    ${k} .workspace-meta {
        color: var(--textSecondary);
        font-size: 9.5px;
    }
`);

export function WorkspaceLabel(props: {title: string; meta?: string; metaTooltip?: string}): React.ReactNode {
    return <div className={WorkspaceLabelStyle}>
        <TooltipV2 tooltip={props.title} triggerClassName="workspace-tooltip">
            <span className="workspace-title">{props.title}</span>
        </TooltipV2>
        {props.meta ? <TooltipV2 tooltip={props.metaTooltip ?? props.meta} triggerClassName="workspace-tooltip">
            <span className="workspace-meta">{props.meta}</span>
        </TooltipV2> : null}
    </div>;
}

const AmountStyle = injectStyle("allocation-amount", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 2px;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
    }
    ${k} .amount-value {
        color: var(--textPrimary);
        font-size: 12px;
        line-height: 14px;
        font-weight: 400;
    }
    ${k} .amount-unit {
        color: var(--textSecondary);
        font-size: 10.5px;
        line-height: 12px;
    }
    ${k}[data-approximate="true"] {
        font-style: italic;
    }
    ${k}[data-negative="true"] .amount-value {
        color: var(--errorMain);
    }
`);

export function Amount(props: {
    value: number;
    unit: string;
    title?: string;
    negative?: boolean;
    approximate?: boolean;
}): React.ReactNode {
    return <TooltipV2 tooltip={props.title}>
        <div className={AmountStyle} data-negative={props.negative} data-approximate={props.approximate}>
            <span className="amount-value">
                {props.approximate ? "~" : ""}
                {formatAmount(props.value)}
            </span>
            <span className="amount-unit">{props.unit}</span>
        </div>
    </TooltipV2>;
}

function LifecycleAmount(props: {
    metric: LifecycleMetric;
    amounts: Amounts;
    consumption: boolean;
    unit: string;
    maxUsable?: number;
}): React.ReactNode {
    if (!lifecycleApplies(props.metric, props.consumption)) return null;
    const value = props.metric.key === "activeBalance" ? props.maxUsable : props.amounts[props.metric.key];
    if (value === undefined) return null;
    return <Amount value={value} unit={props.unit}
        title={props.metric.key === "used" && !props.consumption ? "Current capacity in use." : props.metric.title} />;
}
