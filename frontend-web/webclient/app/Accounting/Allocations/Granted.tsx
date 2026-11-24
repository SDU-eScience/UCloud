import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useState} from "react";
import * as Accounting from "@/Accounting";
import {explainUnit, productTypeToIcon} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {
    Button,
    Divider,
    Flex,
    Icon,
    Input,
    Label,
    TextArea,
} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {TooltipV2} from "@/ui-components/Tooltip";
import {DatePickerClass} from "@/ui-components/DatePicker";
import DatePicker from "react-datepicker";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {UcxStreamedTable, UcxTableStore, UcxTableIconButtonClass} from "@/UCX/UcxBrowser";
import type {UcxTableActivationEvent, UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {injectStyle} from "@/Unstyled";
import {ShortcutHint, SubmitShortcut} from "@/Applications/KeyboardNavigation";
import {bulkRequestOf, doNothing} from "@/UtilityFunctions";
import {dateToStringNoTime} from "@/Utilities/DateUtilities";
import {OldProjectRole} from "@/Project";
import {dialogStore} from "@/Dialog/DialogStore";
import {callAPIWithErrorHandler} from "@/Authentication/DataHook";
import {sendFailureNotification, sendInformationNotification, sendSuccessNotification} from "@/Notifications";
import {ExportHeader, exportUsage} from "@/Accounting/Usage";
import type {ProjectInfo} from "@/Project/InfoCache";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {Amount, Table} from "./Table";
import {BulkAllocation} from "./BulkAllocation";
import {focusAction, focusTable} from "./Controls";
import {
    categoryKey,
    compareProducts,
    currentCalendarYear,
    formatAmount,
    recipientsFor,
    selectProducts,
    workspaceInfo,
} from "./Model";
import type {
    DateRange,
    EditRequest,
    Model,
    ProductAllocation,
    Recipient,
} from "./Model";

interface OverviewRow {
    category: ProductCategoryV2;
    activeGranted: number;
    activeUsage: number;
    activeReceived: number;
    granted: number;
    used: number;
    workspaces: number;
}

const overviewColumns: TableColumn[] = [
    {
        key: "overview.product",
        label: "Product",
        jsonPath: "product",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    {
        key: "overview.activeUsage",
        label: "Active usage",
        jsonPath: "activeUsage",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    {
        key: "overview.activeGranted",
        label: "Active granted",
        jsonPath: "activeGranted",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    {
        key: "overview.activeReceived",
        label: "Active received",
        jsonPath: "activeReceived",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    {
        key: "overview.usage",
        label: "Total used",
        jsonPath: "usage",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    {
        key: "overview.granted",
        label: "Total granted",
        jsonPath: "granted",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
    {
        key: "overview.workspaces",
        label: "Workspaces",
        jsonPath: "workspaces",
        sortType: TableColumnSortType.Number,
        alignment: "right",
    },
];

const overviewWidths: Record<string, number> = {
    "overview.product": 230,
    "overview.activeGranted": 110,
    "overview.activeUsage": 104,
    "overview.activeReceived": 115,
    "overview.usage": 100,
    "overview.granted": 108,
    "overview.workspaces": 100,
};

const OverviewStyle = injectStyle("allocation-overview", k => `
    ${k} {
        padding-bottom: 16px;
        margin-bottom: 20px;
        border-bottom: 1px solid var(--borderColor);
        flex: 0 0 auto;
        min-height: 0;
    }

    ${k} .overview-heading {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
        padding: 0;
        border: 0;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        text-align: left;
    }

    ${k} .overview-heading h3 {
        margin: 0;
    }

    ${k} .overview-chevron {
        transition: transform 120ms ease;
    }

    ${k} .overview-heading[aria-expanded="false"] .overview-chevron {
        transform: rotate(-90deg);
    }

    ${k} .overview-heading:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 3px;
        border-radius: 4px;
    }

    ${k} .overview-table {
        display: flex;
        flex-direction: column;
        margin-top: 10px;
        min-height: 0;
    }

    ${k} .overview-table [data-ucx-table="allocation-overview"]:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: -2px;
        border-radius: 8px;
    }

    ${k} .overview-table .streamed-table-frame {
        min-height: 0;
    }

    ${k} .overview-number {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 2px;
        text-align: right;
        font-variant-numeric: tabular-nums;
    }

    ${k} .overview-product {
        display: flex;
        align-items: center;
        gap: 7px;
        min-height: 24px;
        max-width: 100%;
    }

    ${k} .overview-product-name {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .overview-provider {
        display: flex;
        align-items: center;
        gap: 8px;
    }
`);

function Overview(props: {
    model: Model;
    loading: boolean;
    personalWorkspaces: boolean;
    dateRange: DateRange;
    selectedCategories: string[];
    onSelectCategories: (categories: string[]) => void;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    const [collapsed, setCollapsed] = useState(false);
    const rows = useMemo(() => {
        const products = new Map<string, OverviewRow>();
        const ensureProduct = (category: ProductCategoryV2): OverviewRow => {
            const key = categoryKey(category);
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
        for (const product of selectProducts(props.model, {
            dateRange: props.dateRange,
            personalWorkspaces: props.personalWorkspaces,
        })) {
            const row = ensureProduct(product.category);
            row.granted += product.amounts.granted;
            row.used += product.amounts.used;
            row.workspaces++;
        }
        return [...products.values()].sort((a, b) => compareProducts(a.category, b.category));
    }, [props.model, props.dateRange, props.personalWorkspaces]);

    const providerCount = new Set(rows.map(row => row.category.provider)).size;
    const rowByKey = useMemo(() => new Map(rows.map(row => [categoryKey(row.category), row])), [rows]);
    const tableRows = useMemo((): TableRow[] => rows.map(row => ({
        key: categoryKey(row.category),
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
            columns: overviewColumns,
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
            const action = selected ? "Remove" : "Add";
            const preposition = selected ? "from" : "to";
            const label = `${action} ${row.category.name} ${preposition} product filter`;
            return <div className="overview-product">
                <Icon name={productTypeToIcon(row.category.productType)} size={16} />
                <span className="overview-product-name">{row.category.name}</span>
                <TooltipV2 tooltip={label}>
                    <button
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
                    >
                        <Icon name={selected ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} />
                    </button>
                </TooltipV2>
            </div>;
        }
        if (context.column.key === "overview.usage") {
            const unit = explainUnit(row.category).name;
            const title = row.category.accountingModel === "CONSUMPTION"
                ? "Total usage on active and expired allocations granted to workspaces."
                : "Current capacity used by workspaces. Past usage is not included.";
            return <Amount value={row.used} unit={unit} title={title} />;
        }
        if (context.column.key === "overview.granted") {
            const unit = explainUnit(row.category).name;
            return <Amount
                value={row.granted}
                unit={unit}
                title="Quota granted to workspaces, including active, expired and upcoming allocations."
            />;
        }
        const value = Number(context.cell);
        const count = context.column.key === "overview.workspaces";
        const unit = count ? "workspaces" : explainUnit(row.category).name;
        let title = `${formatAmount(value)} ${unit}`;
        if (context.column.key === "overview.activeGranted") title = "Active quota granted to all child workspaces.";
        if (context.column.key === "overview.activeUsage") {
            title = "Usage charged against active quota, including this workspace and its children.";
        }
        if (context.column.key === "overview.activeReceived") {
            title = "Active quota received by this workspace from its funding parents.";
        }
        if (count) {
            return <TooltipV2 tooltip={title}>
                <span className="overview-number">{formatAmount(value)}</span>
            </TooltipV2>;
        }
        return <Amount value={value} unit={unit} title={title} />;
    };

    const naturalHeight = (Math.max(1, rows.length) + providerCount) * 45 + 42;
    const tableHeight = Math.min(4 * 45 + 42, naturalHeight);

    return <section className={OverviewStyle} aria-label="Allocation overview" data-allocation-overview
        onKeyDown={event => {
            if (event.key !== "Escape") return;
            event.preventDefault();
            event.stopPropagation();
            focusTable();
        }}>
        <button type="button" className="overview-heading" data-allocation-overview-toggle
            aria-expanded={!collapsed} aria-keyshortcuts="Control+Alt+O Meta+Alt+O"
            onClick={() => setCollapsed(previous => !previous)}>
            <Icon name="heroChevronDown" size={14} className="overview-chevron" />
            <h3>Allocation overview</h3>
            <ShortcutHint shortcut="O" hideUntilReveal />
        </button>
        {collapsed ? null : <div className="overview-table" style={{height: tableHeight}}>
            <UcxStreamedTable
                tableId="allocation-overview"
                store={store}
                preserveRowOrder
                showControls={false}
                density="compact"
                rowHeight={45}
                activateOnSpace
                renderGroupHeader={provider => <span className="overview-provider">
                    <ProviderLogo providerId={provider} size={18} />
                    {getProviderTitle(provider)}
                </span>}
                columnWidths={overviewWidths}
                renderCell={renderCell}
                onRowActivated={onRowActivated}
                emptyMessage={props.loading ? "Loading allocations…" : "No allocations yet."}
            />
        </div>}
    </section>;
}

const subProjectsStyle = injectStyle("sub-projects", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
    }

    ${k} .sub-projects-heading {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px 12px;
        margin: 24px 0 10px;
    }

    ${k} .sub-projects-heading h3 {
        margin: 0;
    }

    ${k} .sub-projects-container {
        overflow: hidden;
        border: 0;
        border-radius: 5px;
        padding: 0;
        flex: 1 1 0;
        min-height: 320px;
        width: 100%;
    }
`);

function DurationSelector(props: {periodRef: {start: Date | null; end: Date | null}}) {
    const originalStart = useMemo(() => props.periodRef.start, []);
    const originalEnd = useMemo(() => props.periodRef.end, []);
    const [startDate, setStartDate] = useState<Date | null>(props.periodRef.start);
    const [endDate, setEndDate] = useState<Date | null>(props.periodRef.end);
    const currentStart = dateToStringNoTime(originalStart?.getTime() ?? Date.now());
    const currentEnd = dateToStringNoTime(originalEnd?.getTime() ?? Date.now());

    const onChange = React.useCallback((dates: [Date | null, Date | null]) => {
        const [start, end] = dates
        props.periodRef.start = start;
        props.periodRef.end = end;
        setStartDate(start)
        setEndDate(end)
    }, []);

    return <Label>
        Allocation period
        (Current: {currentStart} - {currentEnd})
        <br />
        <DatePicker
            selected={startDate}
            onChange={onChange}
            startDate={startDate}
            endDate={endDate}
            selectsRange
            dateFormat="MM/yyyy"
            minDate={new Date()}
            showMonthYearPicker
            required
            className={DatePickerClass}
        />
    </Label>
}

function RetireDialog(props: {
    allocationIds: number[];
    workspaceTitle: string;
    grantId?: number;
    onRetire: () => Promise<void>;
}): React.ReactNode {
    const [submitting, setSubmitting] = useState(false);
    const count = props.allocationIds.length;
    const title = props.grantId !== undefined ? `Retire grant #${props.grantId} for this wallet?` :
        `Retire allocation ${props.allocationIds[0]}?`;
    return <div onKeyDown={event => event.stopPropagation()}>
        <Heading.h3>{title}</Heading.h3>
        <p>
            {count === 1 ? "This allocation" : `These ${count} allocations`} for {props.workspaceTitle} will stop
            providing quota immediately.
        </p>
        <p>Upcoming allocations will be canceled without changing their start dates.</p>
        <Flex justifyContent="flex-end" gap="8px">
            <Button color="ghost" disabled={submitting} onClick={() => dialogStore.failure()}>Cancel</Button>
            <Button color="errorMain" disabled={submitting} onClick={async () => {
                setSubmitting(true);
                try {
                    await props.onRetire();
                } finally {
                    setSubmitting(false);
                }
            }}>Retire</Button>
        </Flex>
    </div>;
}

function openEditDialog(
    category: ProductCategoryV2,
    allocationId: number,
    originalStart: Date | null,
    originalEnd: Date | null,
    originalQuota: number,
    workspaceTitle: string,
    onReload: () => Promise<void>,
): void {
    const periodRef = {
        start: originalStart,
        end: originalEnd,
    }
    let quota = originalQuota;
    let reason = "";
    const allocationUnitName = Accounting.allocationInputUnit(category).name;
    const currentQuota = Accounting.balanceToString(category, quota);
    dialogStore.addDialog((
        <form onSubmit={async ev => {
            ev.preventDefault();
            ev.stopPropagation();
            if (quota == originalQuota && originalStart == periodRef.start && originalEnd == periodRef.end) {
                sendInformationNotification("No changes made");
                dialogStore.success()
            } else if (reason === "") {
                sendFailureNotification("Missing reason");
            } else {
                const success = (await callAPIWithErrorHandler(
                    Accounting.updateAllocationV2(bulkRequestOf({
                        allocationId: allocationId,
                        newQuota: quota,
                        newStart: periodRef.start?.getTime() ?? new Date().getTime(),
                        newEnd: periodRef.end?.getTime() ?? new Date().getTime(),
                        reason: reason,
                    }))
                )) !== null;

                if (success) {
                    await onReload();
                    sendSuccessNotification("Update Success");
                    dialogStore.success();
                }
            }
        }}>
            <div onKeyDown={e => e.stopPropagation()}>
                <div>
                    <Heading.h3>Update {category.name} ({getProviderTitle(category.provider)}) allocation
                        (ID: {allocationId}) belonging to "{workspaceTitle}"</Heading.h3>
                    <Divider />
                    <Flex mb={"16px"} flexDirection={"row"}>
                        <DurationSelector periodRef={periodRef} />
                        <Label ml={"auto"}>
                            Allocation quota in {allocationUnitName} (Current: {currentQuota})
                            <Input
                                type="number"
                                defaultValue={Accounting.allocationInputValue(category, quota)}
                                min={0}
                                onChange={e => quota = Accounting.allocationInputToRaw(
                                    category,
                                    e.target.valueAsNumber,
                                )}
                            />
                        </Label>
                    </Flex>
                    <Label mb={"16px"}>
                        Reason <Required />
                        <TextArea required rows={4} width={1} height={1} type={"text"} autoFocus
                            placeholder={"Reason for update..."} onChange={e => reason = e.target.value} />
                    </Label>
                    <Button mt={"16px"} type={"submit"} fullWidth>
                        Update allocation
                    </Button>
                </div>
            </div>
        </form>

    ), doNothing, true);
}

function SubProjects(props: {
    projectId?: string;
    projectRole: OldProjectRole;
    canCreateSubAllocator: boolean;
    model: Model;
    loading: boolean;
    personalWorkspaces: boolean;
    onPersonalWorkspacesChange: () => void;
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange?: DateRange;
    selectedCategories?: string[];
    onCategoriesChange?: (categories: string[]) => void;
    periodControl?: React.ReactNode;
    onReloadWallets: () => Promise<void>;
}): React.ReactNode {
    const [exportProducts, setExportProducts] = useState<ProductAllocation[]>([]);
    const [allocationRecipients, setAllocationRecipients] = useState<Recipient[] | null>(null);
    const existingAllocationRecipients = useMemo(() =>
        recipientsFor(props.model, new Set(props.model.products.map(product => product.workspaceKey))),
        [props.model]);

    useEffect(() => {
        setAllocationRecipients(null);
    }, [props.projectId]);

    const allocationCategories = useMemo(() => {
        const categories = new Map<string, ProductCategoryV2>();
        for (const wallet of props.model.received) {
            categories.set(`${wallet.category.provider}/${wallet.category.name}`, wallet.category);
        }
        return [...categories.values()];
    }, [props.model]);

    const onExportData = useCallback(() => {
        interface Allocation {
            id: number;
            grantApplication?: number;
            start: number;
            end: number;
            quota: number;
            used: number | null;
            attribution: string;
        }

        interface Row {
            workspace: string;
            pi: string;
            category: string;
            provider: string;
            usage: number;
            quota: number;
            unit: string;
            activeBalance: number | null;
            expiredUnused: number;
            future: number;
            allocations: Allocation[];
        }

        type AllocationField = keyof Allocation;

        type AllocationFlatColumns = {
            [K in `allocation${number}_${AllocationField}`]?: number | string | null;
        };

        type FlattenedRow = Omit<Row, "allocations"> & AllocationFlatColumns;

        function flattenAllocations(row: Row, maxAllocations?: number): FlattenedRow {
            const {allocations, ...rest} = row;

            const limit = maxAllocations ?? allocations.length;

            const flattenedAllocations = allocations.slice(0, limit).reduce((acc, allocation, index) => {
                const n = index + 1;

                acc[`allocation${n}_id`] = allocation.id;
                acc[`allocation${n}_grantApplication`] = allocation.grantApplication;
                acc[`allocation${n}_start`] = allocation.start;
                acc[`allocation${n}_end`] = allocation.end;
                acc[`allocation${n}_quota`] = allocation.quota;
                acc[`allocation${n}_used`] = allocation.used;
                acc[`allocation${n}_attribution`] = allocation.attribution;

                return acc;
            }, {} as AllocationFlatColumns);

            return {
                ...rest,
                ...flattenedAllocations,
            };
        }

        const rows: Row[] = [];
        let maxAllocations = 0;

        for (const product of exportProducts) {
            const workspace = workspaceInfo(product.owner, props.projectInfo);
            const allocations = product.grants.map(grant => ({
                id: grant.allocationId,
                grantApplication: grant.grantId,
                start: grant.start,
                end: grant.end,
                quota: grant.quota,
                used: grant.used,
                attribution: grant.attribution,
            }));
            maxAllocations = Math.max(maxAllocations, allocations.length);
            rows.push({
                workspace: workspace.title,
                pi: workspace.pi,
                category: product.category.name,
                provider: product.category.provider,
                usage: product.amounts.used,
                quota: product.amounts.granted,
                unit: product.unit,
                activeBalance: product.maxUsable ?? null,
                expiredUnused: product.amounts.expiredUnused,
                future: product.amounts.future,
                allocations,
            });
        }

        function buildExportColumns(maxAllocations: number): ExportHeader<Row>[] {
            const base = [
                {key: "workspace", value: "Workspace", defaultChecked: true},
                {key: "pi", value: "PI", defaultChecked: true},
                {key: "category", value: "Category", defaultChecked: true},
                {key: "provider", value: "Provider", defaultChecked: true},
                {key: "usage", value: "Used", defaultChecked: true},
                {key: "quota", value: "Total granted", defaultChecked: true},
                {key: "unit", value: "Unit", defaultChecked: true},
                {key: "activeBalance", value: "Active balance", defaultChecked: true},
                {key: "expiredUnused", value: "Expired unused", defaultChecked: true},
                {key: "future", value: "Future quota", defaultChecked: true},
            ] as const;

            const allocationFields: readonly AllocationField[] = [
                "id",
                "grantApplication",
                "start",
                "end",
                "quota",
                "used",
                "attribution",
            ] as const;

            const allocationCols = Array.from({length: maxAllocations}).flatMap((_, i) => {
                const n = i + 1;
                return allocationFields.map((field) => ({
                    key: `allocation${n}_${field}` as const,
                    value: `Allocation ${n} ${field}`,
                    defaultChecked: true,
                    hidden: true,
                }));
            });

            return [...base, ...allocationCols] as ExportHeader<Row>[];
        }

        exportUsage(
            rows,
            buildExportColumns(maxAllocations),
            undefined,
            {
                fileName: "sub-projects-export",
                csvData: rows.map((r) => flattenAllocations(r, maxAllocations)),
            }
        );
    }, [exportProducts, props.projectInfo]);

    const clearFilters = useCallback(() => {
        if (props.personalWorkspaces) props.onPersonalWorkspacesChange();
    }, [props.personalWorkspaces, props.onPersonalWorkspacesChange]);

    const editAllocationWithDialog = useCallback((request: EditRequest) => {
        openEditDialog(
            request.category,
            request.allocationId,
            new Date(request.start),
            new Date(request.end),
            request.quota,
            request.workspaceTitle,
            props.onReloadWallets,
        );
    }, [props.onReloadWallets]);

    const retireAllocationsWithDialog = useCallback((
        allocationIds: number[],
        workspaceTitle: string,
        grantId?: number,
    ) => {
        dialogStore.addDialog(<RetireDialog
            allocationIds={allocationIds}
            workspaceTitle={workspaceTitle}
            grantId={grantId}
            onRetire={async () => {
                const response = await callAPIWithErrorHandler(Accounting.updateAllocationV2(
                    bulkRequestOf(...allocationIds.map(allocationId => ({
                        allocationId, retire: true, reason: "Retired from the allocations table.",
                    }))),
                ));
                await props.onReloadWallets();
                dialogStore.success();
                if (response !== null) {
                    sendSuccessNotification(allocationIds.length === 1 ? "Allocation retired" : "Allocations retired");
                }
            }}
        />, doNothing, true);
    }, [props.onReloadWallets]);

    const canManageAllocations = props.projectRole !== OldProjectRole.USER;

    return <>
        {allocationRecipients && props.projectId ? <BulkAllocation
            key={props.projectId}
            projectId={props.projectId}
            recipients={allocationRecipients}
            availableRecipients={existingAllocationRecipients}
            canCreateSubAllocator={props.canCreateSubAllocator}
            categories={allocationCategories}
            projectInfo={props.projectInfo}
            dateRange={props.dateRange ?? currentCalendarYear()}
            onClose={changed => {
                setAllocationRecipients(null);
                if (!changed) {
                    requestAnimationFrame(() => requestAnimationFrame(() => focusAction("allocate")));
                    return;
                }
                void props.onReloadWallets().then(() => {
                    requestAnimationFrame(() => focusTable());
                });
            }}
        /> : null}
        <div className={subProjectsStyle}>
            {props.projectId !== undefined && <>
                <div className="sub-projects-heading">
                    <h3>Sub-projects</h3>
                    <ShortcutHint shortcut="S" hideUntilReveal />
                </div>

                <div className="sub-projects-container">
                    {props.loading ?
                        <div style={{padding: 20}}><HexSpin size={64} /></div> :
                        <Table
                            key={props.projectId}
                            model={props.model}
                            onVisibleProductsChange={setExportProducts}
                            projectInfo={props.projectInfo}
                            dateRange={props.dateRange}
                            selectedCategories={props.selectedCategories}
                            onCategoriesChange={props.onCategoriesChange}
                            periodControl={props.periodControl}
                            personalWorkspaces={props.personalWorkspaces}
                            onPersonalWorkspacesChange={props.onPersonalWorkspacesChange}
                            onClearFilters={clearFilters}
                            onAllocate={canManageAllocations ? setAllocationRecipients : undefined}
                            toolbar={<Flex gap="8px">
                                <Button height={35} data-allocation-shortcut="export"
                                    ariaKeyshortcuts="Control+Alt+E Meta+Alt+E" onClick={onExportData}>
                                    <Icon name="heroArrowDownTray" mr={8} />Export<SubmitShortcut shortcut="E" />
                                </Button>
                            </Flex>}
                            emptyMessage={props.selectedCategories && props.selectedCategories.length > 0
                                ? "No allocations match the current search, filters or period."
                                : undefined}
                            onEdit={canManageAllocations ? editAllocationWithDialog : undefined}
                            onRetire={canManageAllocations ? retireAllocationsWithDialog : undefined}
                        />}
                </div>
            </>}
        </div>
    </>;
}

const req = injectStyle("required", cl => `
    ${cl}::after {
        content: '*';
        color: red;
    }
`)

function Required(): React.ReactNode {
    return <span className={req} />
}

export function Granted(props: {
    projectId?: string;
    projectRole: OldProjectRole;
    canCreateSubAllocator: boolean;
    model: Model;
    loading: boolean;
    personalWorkspaces: boolean;
    onPersonalWorkspacesChange: () => void;
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange: DateRange;
    selectedCategories: string[];
    onCategoriesChange: (categories: string[]) => void;
    periodControl: React.ReactNode;
    onReloadWallets: () => Promise<void>;
}): React.ReactNode {
    return <>
        <Overview
            model={props.model}
            loading={props.loading}
            personalWorkspaces={props.personalWorkspaces}
            dateRange={props.dateRange}
            selectedCategories={props.selectedCategories}
            onSelectCategories={props.onCategoriesChange}
        />
        <SubProjects
            projectId={props.projectId}
            projectRole={props.projectRole}
            canCreateSubAllocator={props.canCreateSubAllocator}
            model={props.model}
            loading={props.loading}
            personalWorkspaces={props.personalWorkspaces}
            onPersonalWorkspacesChange={props.onPersonalWorkspacesChange}
            projectInfo={props.projectInfo}
            dateRange={props.dateRange}
            selectedCategories={props.selectedCategories}
            onCategoriesChange={props.onCategoriesChange}
            periodControl={props.periodControl}
            onReloadWallets={props.onReloadWallets}
        />
    </>;
}
