import * as React from "react";
import {useCallback, useLayoutEffect, useMemo} from "react";
import {explainUnit, productTypeToIcon} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {allocationSelectProducts} from "./AllocationModel";
import type {AllocationDateRange, AllocationModel} from "./AllocationModel";
import {allocationCompareProducts} from "./AllocationPresentation";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {UcxStreamedTable, UcxTableStore, UcxTableIconButtonClass} from "@/UCX/UcxBrowser";
import type {UcxTableActivationEvent, UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {injectStyle} from "@/Unstyled";
import {Icon} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {AllocationAmount} from "./AllocationCells";

interface AllocationOverviewRow {
    category: ProductCategoryV2;
    activeGranted: number;
    activeUsage: number;
    activeReceived: number;
    granted: number;
    used: number;
    workspaces: number;
}

export function allocationCategoryKey(category: ProductCategoryV2): string {
    return `${category.provider}/${category.name}`;
}

const allocationOverviewColumns: TableColumn[] = [
    {key: "overview.product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text, alignment: "left"},
    {key: "overview.activeUsage", label: "Active usage", jsonPath: "activeUsage", sortType: TableColumnSortType.Number, alignment: "right"},
    {key: "overview.activeGranted", label: "Active granted", jsonPath: "activeGranted", sortType: TableColumnSortType.Number, alignment: "right"},
    {key: "overview.activeReceived", label: "Active received", jsonPath: "activeReceived", sortType: TableColumnSortType.Number, alignment: "right"},
    {key: "overview.usage", label: "Total used", jsonPath: "usage", sortType: TableColumnSortType.Number, alignment: "right"},
    {key: "overview.granted", label: "Total granted", jsonPath: "granted", sortType: TableColumnSortType.Number, alignment: "right"},
    {key: "overview.workspaces", label: "Workspaces", jsonPath: "workspaces", sortType: TableColumnSortType.Number, alignment: "right"},
];

const allocationOverviewWidths: Record<string, number> = {
    "overview.product": 230,
    "overview.activeGranted": 110,
    "overview.activeUsage": 104,
    "overview.activeReceived": 115,
    "overview.usage": 100,
    "overview.granted": 108,
    "overview.workspaces": 100,
};

const AllocationOverviewStyle = injectStyle("allocation-overview", k => `
\${k} {
    padding-bottom: 16px;
    margin-bottom: 20px;
    border-bottom: 1px solid var(--borderColor);
}

\${k} p {
    margin: 0;
    color: var(--textSecondary);
    font-size: 12px;
}

\${k} .overview-table {
    display: flex;
    flex-direction: column;
    margin-top: 10px;
    min-height: 200px;
}

\${k} .overview-number {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
    text-align: right;
    font-variant-numeric: tabular-nums;
}

\${k} .overview-product {
    display: flex;
    align-items: center;
    gap: 7px;
    min-height: 24px;
    max-width: 100%;
}

\${k} .overview-product-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
}

\${k} .overview-provider {
    display: flex;
    align-items: center;
    gap: 8px;
}
`);

export function AllocationOverview(props: {
    model: AllocationModel;
    loading: boolean;
    personalWorkspaces: boolean;
    dateRange: AllocationDateRange;
    selectedCategories: string[];
    onSelectCategories: (categories: string[]) => void;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    const rows = useMemo(() => {
        const products = new Map<string, AllocationOverviewRow>();
        const ensureProduct = (category: ProductCategoryV2): AllocationOverviewRow => {
            const key = allocationCategoryKey(category);
            let row = products.get(key);
            if (!row) {
                row = {
                    category,
                    activeGranted: 0,
                    activeUsage: 0,
                    activeReceived: 0,
                    granted: 0,
                    used: 0,
                    workspaces: 0,
                };
                products.set(key, row);
            }
            return row;
        };

        for (const wallet of props.model.received) {
            const row = ensureProduct(wallet.category);
            row.activeUsage += wallet.amounts.activeUsed;
            row.activeReceived += wallet.amounts.activeQuota;
        }
        for (const product of props.model.products) {
            ensureProduct(product.category).activeGranted += product.amounts.activeQuota;
        }
        for (const product of allocationSelectProducts(props.model, {
            dateRange: props.dateRange,
            personalWorkspaces: props.personalWorkspaces,
        })) {
            const row = ensureProduct(product.category);
            row.granted += product.amounts.granted;
            row.used += product.amounts.used;
            row.workspaces++;
        }
        return [...products.values()].sort((a, b) => allocationCompareProducts(a.category, b.category));
    }, [props.model, props.dateRange, props.personalWorkspaces]);

    const number = (value: number) => formatNumber(value, {precision: 2, removeTrailingZeros: true});
    const providerCount = new Set(rows.map(row => row.category.provider)).size;
    const rowByKey = useMemo(() => new Map(rows.map(row => [allocationCategoryKey(row.category), row])), [rows]);
    const tableRows = useMemo((): TableRow[] => rows.map(row => ({
        key: allocationCategoryKey(row.category),
        group: row.category.provider,
        cells: [
            row.category.name,
            `${row.activeUsage}`,
            `${row.activeGranted}`,
            `${row.activeReceived}`,
            `${row.used}`,
            `${row.granted}`,
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
                <TooltipV2 tooltip={label}><button
                    className={UcxTableIconButtonClass}
                    aria-pressed={selected}
                    aria-label={label}
                    onClick={event => {
                        event.stopPropagation();
                        store.setSelected("allocation-overview", context.row.key);
                        toggleCategory(context.row.key);
                    }}
                    onKeyDown={event => {
                        if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                    }}
                ><Icon name={selected ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} /></button></TooltipV2>
            </div>;
        }
        if (context.column.key === "overview.usage") {
            const unit = explainUnit(row.category).name;
            return <AllocationAmount value={row.used} unit={unit} title={row.category.accountingModel === "CONSUMPTION" ?
                "Total usage on active and expired allocations granted to workspaces." :
                "Current capacity used by workspaces. Past usage is not included."} />;
        }
        if (context.column.key === "overview.granted") {
            const unit = explainUnit(row.category).name;
            return <AllocationAmount value={row.granted} unit={unit}
                title="Quota granted to workspaces, including active, expired and upcoming allocations." />;
        }
        const value = Number(context.cell);
        const count = context.column.key === "overview.workspaces";
        const unit = count ? "workspaces" : explainUnit(row.category).name;
        let title = `${number(value)} ${unit}`;
        if (context.column.key === "overview.activeGranted") title = "Active quota granted to all child workspaces.";
        if (context.column.key === "overview.activeUsage") title = "Usage charged against active quota, including this workspace and its children.";
        if (context.column.key === "overview.activeReceived") title = "Active quota received by this workspace from its funding parents.";
        if (count) return <TooltipV2 tooltip={title}><span className="overview-number">{number(value)}</span></TooltipV2>;
        return <AllocationAmount value={value} unit={unit} title={title} />;
    };

    return <section className={AllocationOverviewStyle} aria-label="Allocation overview">
        <h3 style={{margin: 0}}>Allocation overview</h3>
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
                renderCell={renderCell}
                onRowActivated={onRowActivated}
                emptyMessage={props.loading ? "Loading allocations…" : "No allocations yet."}
            />
        </div>
    </section>;
}
