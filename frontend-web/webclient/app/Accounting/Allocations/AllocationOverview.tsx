import * as React from "react";
import {useCallback, useLayoutEffect, useMemo} from "react";
import {explainUnit, productTypeToIcon} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import type {State} from "./State";
import {allocationDateRangeBounds, allocationOverlapsRange} from "./AllocationGrid";
import type {AllocationDateRange} from "./AllocationGrid";
import {allocationCompareProducts, allocationDisplayUnit} from "./AllocationPresentation";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {UcxStreamedTable, UcxTableStore, UcxTableIconButtonClass} from "@/UCX/UcxBrowser";
import type {UcxTableActivationEvent, UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {injectStyle} from "@/Unstyled";
import {Icon} from "@/ui-components";
import {AllocationAmount, AllocationUsage} from "./AllocationCells";

interface AllocationOverviewRow {
    category: ProductCategoryV2;
    received: number;
    granted: number;
    used: number;
    future: number;
    expiring: number;
    exhausted: number;
    workspaces: number;
}

export function allocationCategoryKey(category: ProductCategoryV2): string {
    return `${category.provider}/${category.name}`;
}

const allocationOverviewColumns: TableColumn[] = [
    {key: "overview.product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "overview.received", label: "Received", jsonPath: "received", sortType: TableColumnSortType.Number},
    {key: "overview.granted", label: "Granted", jsonPath: "granted", sortType: TableColumnSortType.Number},
    {key: "overview.used", label: "Usage", jsonPath: "used", sortType: TableColumnSortType.Number},
    {key: "overview.future", label: "Starting soon", jsonPath: "future", sortType: TableColumnSortType.Number},
    {key: "overview.expiring", label: "Expiring soon", jsonPath: "expiring", sortType: TableColumnSortType.Number},
    {key: "overview.exhausted", label: "Exhausted workspaces", jsonPath: "exhausted", sortType: TableColumnSortType.Number},
    {key: "overview.workspaces", label: "Workspaces", jsonPath: "workspaces", sortType: TableColumnSortType.Number},
];

const allocationOverviewWidths: Record<string, number> = {
    "overview.product": 300,
    "overview.received": 100,
    "overview.granted": 130,
    "overview.used": 130,
    "overview.future": 120,
    "overview.expiring": 120,
    "overview.exhausted": 160,
    "overview.workspaces": 100,
};

const AllocationOverviewStyle = injectStyle("allocation-overview", k => `
    ${k} {
        padding-bottom: 16px;
        margin-bottom: 20px;
        border-bottom: 1px solid var(--borderColor);
    }
    ${k} .overview-heading { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px 16px; }
    ${k} h4 { margin: 0 0 4px; }
    ${k} p { margin: 0; color: var(--textSecondary); font-size: 12px; }
    ${k} .overview-table { display: flex; flex-direction: column; margin-top: 10px; min-height: 200px; }
    ${k} [data-density="compact"] .streamed-table-scroll thead th:not(:first-child):not(:nth-child(3)):not(:nth-child(4)) { text-align: right; }
    ${k} .overview-number { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; text-align: right; font-variant-numeric: tabular-nums; }
    ${k} .overview-product { display: flex; align-items: center; gap: 7px; min-height: 24px; max-width: 100%; color: var(--textPrimary); font-size: 12px; }
    ${k} .overview-product-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    ${k} .overview-provider { display: flex; align-items: center; gap: 8px; }
`);

export function AllocationOverview(props: {
    state: State;
    dateRange: AllocationDateRange;
    selectedCategories: string[];
    onSelectCategories: (categories: string[]) => void;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    const rows = useMemo(() => {
        const products = new Map<string, AllocationOverviewRow>();
        const now = Date.now();
        const range = allocationDateRangeBounds(props.dateRange);
        const ensureProduct = (category: ProductCategoryV2): AllocationOverviewRow => {
            const key = allocationCategoryKey(category);
            let row = products.get(key);
            if (!row) {
                row = {
                    category,
                    received: 0,
                    granted: 0,
                    used: 0,
                    future: 0,
                    expiring: 0,
                    exhausted: 0,
                    workspaces: 0,
                };
                products.set(key, row);
            }
            return row;
        };

        for (const allocation of Object.values(props.state.yourAllocations)) {
            for (const wallet of allocation.wallets) {
                const row = ensureProduct(wallet.category);
                const raw = wallet.usageAndQuota.raw;
                row.received += raw.quota;
                row.used += raw.retiredAmountStillCounts ? raw.usage : Math.max(0, raw.usage - raw.retiredAmount);
            }
        }

        for (const wallet of props.state.remoteData.wallets ?? []) {
            if (wallet.paysFor.freeToUse) continue;
            const row = ensureProduct(wallet.paysFor);
            const factor = explainUnit(wallet.paysFor).balanceFactor;
            for (const child of wallet.children ?? []) {
                row.granted += (child.group.activeQuota ?? 0) * factor;
            }
        }

        for (const recipient of props.state.subAllocations.recipients) {
            for (const group of recipient.groups) {
                const row = ensureProduct(group.category);
                const allocations = group.allocations.filter(it => allocationOverlapsRange(it.start, it.end, range));
                if (allocations.length === 0) continue;
                row.workspaces++;
                const raw = group.usageAndQuota.raw;
                const usage = raw.retiredAmountStillCounts ? raw.usage : Math.max(0, raw.usage - raw.retiredAmount);
                const hasActiveAllocation = allocations.some(it => it.start <= now && it.end >= now);
                if (hasActiveAllocation && raw.quota > 0 && usage >= raw.quota) row.exhausted++;
                const factor = explainUnit(group.category).balanceFactor;
                for (const allocation of allocations) {
                    if (allocation.start > now && allocation.start <= now + 30 * 86400000) row.future += allocation.quota * factor;
                    if (allocation.start <= now && allocation.end >= now && allocation.end <= now + 30 * 86400000) {
                        row.expiring += allocation.quota * factor;
                    }
                }
            }
        }
        return [...products.values()].sort((a, b) => allocationCompareProducts(a.category, b.category));
    }, [props.state.yourAllocations, props.state.subAllocations, props.state.remoteData.wallets, props.dateRange]);

    const number = (value: number) => formatNumber(value, {precision: 2, removeTrailingZeros: true});
    const providerCount = new Set(rows.map(row => row.category.provider)).size;
    const rowByKey = useMemo(() => new Map(rows.map(row => [allocationCategoryKey(row.category), row])), [rows]);
    const tableRows = useMemo((): TableRow[] => rows.map(row => ({
        key: allocationCategoryKey(row.category),
        group: row.category.provider,
        cells: [
            row.category.name,
            `${row.received}`,
            `${row.granted}`,
            `${row.used}`,
            `${row.future}`,
            `${row.expiring}`,
            `${row.exhausted}`,
            `${row.workspaces}`,
        ],
    })), [rows]);

    useLayoutEffect(() => {
        store.apply({
            tableId: "allocation-overview",
            revision: 0,
            snapshot: true,
            columns: allocationOverviewColumns,
            upserts: tableRows,
            removed: [],
        });
    }, [store, tableRows]);

    const toggleCategory = useCallback((key: string) => {
        const selected = props.selectedCategories.includes(key);
        props.onSelectCategories(selected
            ? props.selectedCategories.filter(category => category !== key)
            : [...props.selectedCategories, key]);
    }, [props.selectedCategories, props.onSelectCategories]);

    const onRowActivated = useCallback((event: UcxTableActivationEvent) => {
        if (!rowByKey.has(event.rowKey)) return;
        toggleCategory(event.rowKey);
    }, [rowByKey, toggleCategory]);

    const renderCell = (context: UcxTableCellRenderContext): React.ReactNode => {
        const row = rowByKey.get(context.row.key);
        if (!row) return context.cell;
        if (context.column.key === "overview.product") {
            const selected = props.selectedCategories.includes(context.row.key);
            const label = `${selected ? "Remove" : "Add"} ${row.category.name} ${selected ? "from" : "to"} product filter`;
            return <div className="overview-product">
                <Icon name={productTypeToIcon(row.category.productType)} size={16} />
                <span className="overview-product-name">{row.category.name}</span>
                <button
                    className={UcxTableIconButtonClass}
                    aria-pressed={selected}
                    aria-label={label}
                    title={label}
                    onClick={event => {
                        event.stopPropagation();
                        store.setSelected("allocation-overview", context.row.key);
                        toggleCategory(context.row.key);
                    }}
                    onKeyDown={event => {
                        if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                    }}
                ><Icon name={selected ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} /></button>
            </div>;
        }
        if (context.column.key === "overview.used") {
            return <AllocationUsage usage={row.used} quota={row.received} unit={allocationDisplayUnit(row.category)} compact />;
        }
        if (context.column.key === "overview.granted") {
            const unit = allocationDisplayUnit(row.category);
            return <AllocationUsage
                usage={row.granted}
                quota={row.received}
                unit={unit}
                compact
                title={`${number(row.granted)} ${unit} granted to sub-workspaces / ${number(row.received)} ${unit} received`}
            />;
        }
        const value = Number(context.cell);
        const count = context.column.key === "overview.exhausted" || context.column.key === "overview.workspaces";
        const unit = count ? "workspaces" : allocationDisplayUnit(row.category);
        let title = `${number(value)} ${unit}`;
        if (context.column.key === "overview.expiring") title += " of quota ending within 30 days";
        if (context.column.key === "overview.future") title += " of quota starting within 30 days";
        if (count) return <span className="overview-number" title={title}>{number(value)}</span>;
        return <AllocationAmount value={value} unit={unit} title={title} />;
    };

    return <section className={AllocationOverviewStyle} aria-label="Allocation overview">
        <div className="overview-heading">
            <h4>Allocation overview</h4>
        </div>
        <div className="overview-table" style={{height: Math.min(560, Math.max(200, (rows.length + providerCount) * 45 + 42))}}>
            <UcxStreamedTable
                tableId="allocation-overview"
                store={store}
                preserveRowOrder
                showControls={false}
                density="compact"
                rowHeight={45}
                activateOnSpace
                renderGroupHeader={provider => <span className="overview-provider"><ProviderLogo providerId={provider} size={18} />{getProviderTitle(provider)}</span>}
                columnWidths={allocationOverviewWidths}
                fitToWidth
                renderCell={renderCell}
                onRowActivated={onRowActivated}
                emptyMessage={props.state.remoteData.wallets === undefined ? "Loading allocations…" : "No allocations yet."}
            />
        </div>
    </section>;
}
