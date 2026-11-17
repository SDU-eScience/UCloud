import * as React from "react";
import {useLayoutEffect, useMemo, useRef, useState} from "react";
import ReactModal from "react-modal";
import {format} from "date-fns";
import {allocationInputToRaw, allocationInputUnit, allocationInputValue, productTypeToIcon} from "@/Accounting";
import type {AllocationDisplayTreeRecipient, ProductCategoryV2} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {KeyboardNavigation, SubmitShortcut, useSubmitShortcut} from "@/Applications/KeyboardNavigation";
import * as Grants from "@/Grants";
import {sendInformationNotification, sendSuccessNotification} from "@/Notifications";
import {projectInfoTitle} from "@/Project/InfoCache";
import type {ProjectInfo} from "@/Project/InfoCache";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {UcxStreamedTable, UcxTableStore} from "@/UCX/UcxBrowser";
import type {UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {Button, Icon, Input} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {MarkdownDocument} from "@/ui-components/Markdown";
import {TutorialWizardProgress} from "@/ui-components/TutorialWizard";
import {TooltipV2} from "@/ui-components/Tooltip";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {doNothing, extractErrorMessage, isLikelyMac} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {AllocationAmount} from "./AllocationCells";
import {allocationDateRangeBounds, allocationOverlapsRange} from "./AllocationGrid";
import type {AllocationDateRange} from "./AllocationGrid";
import {allocationCategoryKey} from "./AllocationOverview";
import {allocationCompareProducts} from "./AllocationPresentation";
import {AllocationPeriodFilter} from "./AllocationPeriodFilter";
import {allocationSlices} from "./AllocationSlices";
import type {AllocationSlice} from "./AllocationSlices";
import {AllocationBreakdownDialog} from "./AllocationBreakdownDialog";

const bulkAllocationProductColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "quota", label: "Quota per recipient", jsonPath: "quota", sortType: TableColumnSortType.Number},
    {key: "total", label: "Batch total", jsonPath: "total", sortType: TableColumnSortType.Number},
];

const bulkAllocationAdjustColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "current", label: "Current quota", jsonPath: "current", sortType: TableColumnSortType.Number},
    {key: "base", label: "Base", jsonPath: "base", sortType: TableColumnSortType.Number},
    {key: "adjusted", label: "Adjusted", jsonPath: "adjusted", sortType: TableColumnSortType.Number},
    {key: "total", label: "New total", jsonPath: "total", sortType: TableColumnSortType.Number},
];

const bulkAllocationReviewColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "current", label: "Current quota", jsonPath: "current", sortType: TableColumnSortType.Number},
    {key: "added", label: "Added quota", jsonPath: "added", sortType: TableColumnSortType.Number},
    {key: "total", label: "New total", jsonPath: "total", sortType: TableColumnSortType.Number},
];

const BulkAllocationWizardStyle = injectStyle("bulk-allocation-wizard", k => `
    ${k} { display: flex; flex-direction: column; height: 100%; min-height: 0; min-width: 0; color: var(--textPrimary); }
    ${k} > header { padding: 20px 24px 16px; border-bottom: 1px solid var(--borderColor); }
    ${k} h3, ${k} h4 { margin: 0 0 8px; }
    ${k} p { margin: 0 0 16px; color: var(--textSecondary); }
    ${k} .bulk-content { display: flex; flex-direction: column; flex: 1; gap: 16px; min-height: 0; min-width: 0; padding: 24px; overflow: auto; outline: none; }
    ${k} .bulk-content > p { flex-shrink: 0; margin: 0; }
    ${k} .bulk-content[data-step="1"], ${k} .bulk-content[data-step="2"] { overflow: hidden; }
    ${k} .bulk-content[data-step="4"] { gap: 12px; padding-top: 16px; padding-bottom: 16px; }
    ${k} .bulk-steps { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 16px; color: var(--textSecondary); font-size: 12px; }
    ${k} .bulk-steps [aria-current="step"] { color: var(--primaryMain); font-weight: 600; }
    ${k} .bulk-actions { display: flex; align-items: center; flex: 0 0 auto; flex-wrap: wrap; gap: 8px; padding: 12px 24px; background: var(--dialogToolbar); }
    ${k} .bulk-actions .bulk-progress { flex: 1; min-width: 0; }
    ${k} .bulk-table { display: flex; flex-direction: column; flex: 1; min-height: 0; min-width: 0; }
    ${k} .bulk-content[data-step="4"] > .bulk-table { min-height: 130px; }
    ${k} .bulk-table .streamed-table-frame { min-height: 0; }
    ${k} .bulk-product, ${k} .bulk-provider, ${k} .bulk-quota { display: flex; align-items: center; gap: 8px; min-width: 0; }
    ${k} .bulk-product span { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    ${k} .bulk-product svg, ${k} .bulk-provider svg { flex-shrink: 0; }
    ${k} .bulk-quota input { width: 120px; min-width: 0; height: 32px; }
    ${k} .bulk-quota span { color: var(--textSecondary); font-size: 12px; white-space: normal; }
    ${k} .bulk-reason { display: flex; flex-direction: column; gap: 8px; }
    ${k} textarea { box-sizing: border-box; width: 100%; min-height: 180px; padding: 12px; border: 1px solid var(--borderColor); border-radius: 8px; background: var(--backgroundDefault); color: var(--textPrimary); font: inherit; resize: vertical; }
    ${k} .bulk-review-section { display: flex; flex-direction: column; flex: 0 0 auto; min-width: 0; }
    ${k} .bulk-review-products { flex: 1 1 180px; min-height: 160px; }
    ${k} .bulk-review-totals { flex: 2 1 220px; min-height: 180px; }
    ${k} .bulk-review-label { display: block; margin-bottom: 6px; font-weight: 600; }
    ${k} .bulk-review-dates { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
    ${k} .bulk-review-date { display: flex; align-items: baseline; gap: 6px; min-width: 0; }
    ${k} .bulk-review-date-value { font-variant-numeric: tabular-nums; }
    ${k} .bulk-review-date-time { color: var(--textSecondary); font-size: 12px; font-variant-numeric: tabular-nums; }
    ${k} .bulk-review-reason { max-height: 72px; margin: 0; overflow: auto; overflow-wrap: anywhere; }
    ${k} .bulk-review-reason blockquote { margin: 0; }
    ${k} .bulk-review-reason blockquote p { margin: 0; }
    ${k} .bulk-split-settings { display: flex; flex-direction: column; gap: 12px; }
    ${k} .bulk-split-settings label { display: flex; align-items: center; gap: 8px; }
    ${k} .bulk-split-settings input[type="number"] { width: 80px; }
    ${k} .bulk-split-note { font-size: 12px; color: var(--textSecondary); }
    ${k} .bulk-slice-amount { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
    ${k} .bulk-slice-link { order: -1; flex-shrink: 0; padding: 0; border: 0; background: none; color: var(--textSecondary); font: inherit; font-size: 11px; line-height: 12px; cursor: pointer; }
    ${k} .bulk-slice-link:hover { text-decoration: underline; }
    ${k} .bulk-adjust-group, ${k} .bulk-review-group { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px; min-width: 0; }
    ${k} .bulk-review-group { display: inline-flex; gap: 8px; }
    ${k} .bulk-adjust-workspace { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
    ${k} .bulk-adjust-workspace-title { overflow: hidden; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
    ${k} .bulk-adjust-workspace-meta { color: var(--textSecondary); font-size: 10.5px; }
    ${k} .bulk-adjust-shared { display: flex; align-items: center; gap: 6px; margin-left: auto; color: var(--textSecondary); font-size: 12px; white-space: nowrap; }
    ${k} .bulk-adjust-shared input { width: 70px; height: 28px; }
    ${k} .bulk-adjust-value { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; min-width: 0; }
    ${k} .bulk-adjust-control { display: flex; align-items: center; gap: 6px; min-width: 0; }
    ${k} .bulk-adjust-control input { width: 92px; height: 30px; }
    ${k} .bulk-adjust-control input[readonly] { color: var(--textSecondary); }
    ${k} .bulk-adjust-mode { height: 30px; padding: 0 4px; border: 1px solid var(--borderColor); border-radius: 5px; background: var(--backgroundDefault); color: var(--textPrimary); font: inherit; font-size: 11px; }
    ${k} .bulk-adjust-suffix, ${k} .bulk-adjust-hint { color: var(--textSecondary); font-size: 11px; white-space: nowrap; }
    ${k} .bulk-adjust-result { color: var(--textSecondary); font-size: 10.5px; white-space: nowrap; }
    ${k} .bulk-skip-badge { padding: 1px 6px; border-radius: 999px; background: color-mix(in srgb, var(--warningMain) 20%, transparent); color: var(--textPrimary); font-size: 10px; font-weight: 600; white-space: nowrap; }
    ${k} .bulk-error { margin-top: 16px; color: var(--errorMain); overflow-wrap: anywhere; }
    ${k} .bulk-note { font-size: 12px; }
    ${k} .bulk-review-totals .bulk-note { margin-bottom: 8px; }
    ${k} .bulk-table thead th:not(:first-child) { text-align: right; }
    ${k} .bulk-table[data-table="products"][data-editable="true"] thead th:nth-child(2) { text-align: left; }
    ${k} .bulk-table[data-table="adjust"] thead th:nth-child(4) { text-align: left; }
    @media (max-width: 640px) {
        ${k} > header, ${k} .bulk-content { padding: 16px; }
        ${k} .bulk-actions { padding: 12px 16px; }
        ${k} .bulk-progress { flex-basis: 100%; }
    }
`);

function bulkAllocationUnit(category: ProductCategoryV2): string {
    return allocationInputUnit(category).name.replace(/(?:Core|CPU)-h\b/g, "CPU-hours").replace(/GPU-h\b/g, "GPU-hours");
}

function bulkAllocationWorkspaceTitle(recipient: AllocationDisplayTreeRecipient, projectInfo: Record<string, ProjectInfo | null>): string {
    const owner = recipient.owner.reference;
    return owner.type === "project"
        ? projectInfoTitle(projectInfo[owner.projectId], recipient.owner.title) ?? recipient.owner.title
        : owner.username;
}

function bulkAllocationEditValue(value: number): string {
    return Number.isFinite(value) ? String(Number(value.toFixed(6))) : "";
}

function bulkAllocationReasonBlockquote(reason: string): string {
    return reason.split("\n").map(line => `> ${line}`).join("  \n");
}

type BulkAllocationAdjustMode = "inherit" | "percent" | "absolute";

interface BulkAllocationWorkspaceRow {
    key: string;
    recipientIndex: number;
    category: ProductCategoryV2;
    current: number;
    base: number;
    adjusted: number;
    rawAdjusted: number;
    total: number;
    mode: BulkAllocationAdjustMode;
    overrideValue: string;
    invalid: boolean;
}

function BulkAllocationTable(props: {
    id: string;
    variant: "products" | "adjust" | "review";
    columns: TableColumn[];
    rows: TableRow[];
    renderCell: (context: UcxTableCellRenderContext) => React.ReactNode;
    renderGroupHeader: (group: string) => React.ReactNode;
    editable?: boolean;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    useLayoutEffect(() => {
        store.apply({
            tableId: props.id,
            revision: 0,
            snapshot: true,
            columns: props.columns,
            upserts: props.rows,
            removed: [],
        });
    }, [store, props.id, props.columns, props.rows]);

    return <div className="bulk-table" data-editable={props.editable} data-table={props.variant}>
        <UcxStreamedTable
            tableId={props.id}
            store={store}
            preserveRowOrder
            navigateInputs={props.editable}
            showControls={false}
            density="compact"
            rowHeight={45}
            fitToWidth
            columnWidths={props.variant === "products"
                ? {product: 300, quota: 260, total: 160}
                : props.variant === "adjust"
                    ? {product: 260, current: 110, base: 100, adjusted: 270, total: 140}
                    : {product: 300, current: 150, added: 150, total: 150}}
            renderCell={props.renderCell}
            renderGroupHeader={props.renderGroupHeader}
            onRowActivated={doNothing}
        />
    </div>;
}

export function BulkAllocationWizard(props: {
    projectId: string;
    recipients: AllocationDisplayTreeRecipient[];
    categories: ProductCategoryV2[];
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange: AllocationDateRange;
    demo?: boolean;
    onClose: (changed: boolean) => void;
}): React.ReactNode {
    const [step, setStep] = useState(0);
    const [period, setPeriod] = useState(props.dateRange);
    const [splitEnabled, setSplitEnabled] = useState(false);
    const [splitMonths, setSplitMonths] = useState("1");
    const [breakdown, setBreakdown] = useState<{category: ProductCategoryV2; slices: AllocationSlice[]; workspaceTitle?: string} | null>(null);
    const [quotas, setQuotas] = useState<Record<string, string>>({});
    const [workspacePercents, setWorkspacePercents] = useState<Record<number, string>>({});
    const [rowOverrides, setRowOverrides] = useState<Record<string, {mode: "percent" | "absolute"; value: string}>>({});
    const [reason, setReason] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [completed, setCompleted] = useState(0);
    const submitting = useRef(false);
    const completedRef = useRef(0);
    const contentRef = useRef<HTMLDivElement>(null);
    const focusCurrentStep = () => {
        const content = contentRef.current;
        const target = content?.querySelector<HTMLElement>("input, textarea, [data-navigation-field]") ?? content;
        target?.focus({preventScroll: true});
    };
    useLayoutEffect(() => {
        const frame = requestAnimationFrame(focusCurrentStep);
        return () => cancelAnimationFrame(frame);
    }, [step]);
    const categories = useMemo(() => [...props.categories].sort(allocationCompareProducts), [props.categories]);
    const categoryByKey = useMemo(() => new Map(categories.map(category => [allocationCategoryKey(category), category])), [categories]);
    const rawQuota = (category: ProductCategoryV2) => allocationInputToRaw(category, Number(quotas[allocationCategoryKey(category)] ?? 0));
    const chosen = categories.filter(category => Number(quotas[allocationCategoryKey(category)]) > 0);
    const invalidQuota = categories.some(category => {
        const value = quotas[allocationCategoryKey(category)] ?? "";
        return value !== "" && (!Number.isFinite(Number(value)) || !Number.isFinite(rawQuota(category)) || Number(value) < 0 || (Number(value) > 0 && rawQuota(category) <= 0));
    });
    const workspacePercentFor = (index: number) => workspacePercents[index] ?? "100";
    const workspacePercentNumber = (index: number): number | null => {
        const value = workspacePercentFor(index).trim();
        if (value === "") return null;
        const parsed = Number(value);
        return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
    };
    const bounds = allocationDateRangeBounds(period);
    const periodValid = bounds.start !== null && bounds.end !== null
        && Number.isFinite(bounds.start) && Number.isFinite(bounds.end) && bounds.end >= bounds.start;
    const scheduleValid = !splitEnabled || (Number.isInteger(Number(splitMonths)) && Number(splitMonths) > 0);
    const schedule = periodValid && scheduleValid
        ? allocationSlices(bounds.start!, bounds.end! + 1, splitEnabled ? Number(splitMonths) : 0, 0)
        : [];
    const slicesFor = (category: ProductCategoryV2, quota: number) => allocationSlices(
        bounds.start!,
        bounds.end! + 1,
        splitEnabled && category.accountingModel === "CONSUMPTION" ? Number(splitMonths) : 0,
        quota,
    );
    const hasSplitSchedule = splitEnabled && schedule.length > 1;
    const scheduleText = `Every ${splitMonths} month${Number(splitMonths) === 1 ? "" : "s"} · ${schedule.length} slice${schedule.length === 1 ? "" : "s"}`;
    const workspaceRows: BulkAllocationWorkspaceRow[] = [];
    props.recipients.forEach((recipient, recipientIndex) => {
        for (const category of chosen) {
            const categoryKey = allocationCategoryKey(category);
            const key = `${recipientIndex}/${categoryKey}`;
            const base = allocationInputValue(category, rawQuota(category));
            const currentRaw = recipient.groups
                .filter(group => allocationCategoryKey(group.category) === categoryKey)
                .flatMap(group => group.allocations)
                .filter(allocation => allocationOverlapsRange(
                    allocation.start,
                    allocation.end - (allocation.allocationPackage ? 1 : 0),
                    bounds,
                ))
                .reduce((sum, allocation) => sum + (allocation.allocationPackage && allocation.retired
                    ? allocation.retiredQuota ?? allocation.quota
                    : allocation.quota), 0);
            const current = allocationInputValue(category, currentRaw);
            const override: {mode: "percent" | "absolute"; value: string} | undefined = rowOverrides[key];
            const mode: BulkAllocationAdjustMode = override ? override.mode : "inherit";
            const overrideValue = override ? override.value : "";
            let adjusted = 0;
            let invalid = false;
            if (mode === "inherit") {
                const percent = workspacePercentNumber(recipientIndex);
                invalid = percent === null;
                adjusted = base * (percent ?? 0) / 100;
            } else {
                const value = overrideValue.trim();
                const parsed = value === "" ? NaN : Number(value);
                invalid = !Number.isFinite(parsed) || parsed < 0;
                adjusted = mode === "percent"
                    ? base * (Number.isFinite(parsed) ? parsed : 0) / 100
                    : Number.isFinite(parsed) ? parsed : 0;
            }
            const rawAdjusted = allocationInputToRaw(category, adjusted);
            if (!Number.isFinite(rawAdjusted) || (adjusted > 0 && rawAdjusted <= 0)) invalid = true;
            workspaceRows.push({
                key,
                recipientIndex,
                category,
                current,
                base,
                adjusted: allocationInputValue(category, rawAdjusted),
                rawAdjusted,
                total: allocationInputValue(category, currentRaw + rawAdjusted),
                mode,
                overrideValue,
                invalid,
            });
        }
    });
    const adjustmentInvalid = workspaceRows.some(row => row.invalid);
    const workspaceHasQuota = new Map<number, boolean>();
    const batchRawQuota = new Map<string, number>();
    for (const row of workspaceRows) {
        if (row.rawAdjusted > 0) {
            workspaceHasQuota.set(row.recipientIndex, true);
            const key = allocationCategoryKey(row.category);
            batchRawQuota.set(key, (batchRawQuota.get(key) ?? 0) + row.rawAdjusted);
        }
    }
    const allocationWorkspaces = props.recipients
        .map((recipient, recipientIndex) => ({
            recipient,
            rows: workspaceRows.filter(row => row.recipientIndex === recipientIndex && row.rawAdjusted > 0),
        }))
        .filter(workspace => workspace.rows.length > 0);
    const locked = completed > 0;
    const canGoBack = step > 0 && !busy && !locked;
    const canConfirm = !busy && !breakdown && periodValid && scheduleValid && !invalidQuota && chosen.length > 0
        && !adjustmentInvalid && allocationWorkspaces.length > 0 && !!reason.trim();
    const canGoNext = !busy && (step === 4
        ? canConfirm
        : !locked && (step === 0
            ? periodValid && scheduleValid
            : step === 1
                ? !invalidQuota && chosen.length > 0
                : step === 2
                    ? !adjustmentInvalid && allocationWorkspaces.length > 0
                    : !!reason.trim()));
    const nextDisabledReason = canGoNext ? undefined : (() => {
        if (busy) return "Allocation is in progress.";
        if (!scheduleValid) return "Enter a positive whole number of months per slice.";
        if (step === 0) return "Choose a valid allocation period.";
        if (invalidQuota) return "Enter a valid quota, or clear the invalid value.";
        if (chosen.length === 0) return "Enter a quota for at least one product.";
        if (step >= 2 && adjustmentInvalid) return "Enter a valid percentage or amount for every adjusted value.";
        if (step >= 2 && allocationWorkspaces.length === 0) return "At least one workspace must receive a positive amount.";
        if (!reason.trim()) return "Enter a reason for the allocation.";
        return "Complete the required fields to continue.";
    })();
    const names = ["Period", "Products", "Adjust", "Reason", "Review"];
    const number = (value: number) => formatNumber(value, {precision: 2, removeTrailingZeros: true});
    const productRows: TableRow[] = (step === 4 ? chosen : categories).map(category => {
        const key = allocationCategoryKey(category);
        const quota = allocationInputValue(category, rawQuota(category));
        const total = step === 4
            ? allocationInputValue(category, batchRawQuota.get(key) ?? 0)
            : quota * props.recipients.length;
        return {key, group: category.provider, cells: [category.name, `${quota}`, `${total}`]};
    });
    const adjustTableRows: TableRow[] = workspaceRows.map(row => ({
        key: row.key,
        group: `${row.recipientIndex}`,
        cells: [row.category.name, `${row.current}`, `${row.base}`, `${row.adjusted}`, `${row.total}`],
    }));
    const reviewTableRows: TableRow[] = workspaceRows.map(row => ({
        key: row.key,
        group: `${row.recipientIndex}`,
        cells: [row.category.name, `${row.current}`, `${row.adjusted}`, `${row.total}`],
    }));
    const workspaceRowByKey = new Map(workspaceRows.map(row => [row.key, row]));

    const clearCategoryOverrides = (categoryKey: string) => {
        const suffix = `/${categoryKey}`;
        setRowOverrides(previous => {
            let changed = false;
            const next: typeof previous = {};
            for (const [key, value] of Object.entries(previous)) {
                if (key.endsWith(suffix)) changed = true;
                else next[key] = value;
            }
            return changed ? next : previous;
        });
    };
    const setRowOverride = (key: string, mode: "percent" | "absolute", value: string) => {
        setRowOverrides(previous => ({...previous, [key]: {mode, value}}));
    };
    const clearRowOverride = (key: string) => {
        setRowOverrides(previous => {
            if (!(key in previous)) return previous;
            const next = {...previous};
            delete next[key];
            return next;
        });
    };
    const changeRowMode = (row: BulkAllocationWorkspaceRow, mode: BulkAllocationAdjustMode) => {
        if (mode === "inherit") {
            clearRowOverride(row.key);
            return;
        }
        const key = row.key;
        if (mode === "percent") {
            const percent = row.mode === "inherit"
                ? workspacePercentFor(row.recipientIndex)
                : row.base > 0 ? bulkAllocationEditValue(row.adjusted / row.base * 100) : workspacePercentFor(row.recipientIndex);
            setRowOverride(key, mode, percent);
        } else {
            setRowOverride(key, mode, bulkAllocationEditValue(row.adjusted));
        }
    };

    const close = () => {
        if (!submitting.current) props.onClose(completedRef.current > 0);
    };
    const move = (next: number) => {
        setError("");
        setStep(next);
        contentRef.current?.scrollTo({top: 0});
    };
    const confirm = async () => {
        if (submitting.current || !canConfirm) return;
        if (props.demo) {
            sendInformationNotification("Demo data: no allocations were created.");
            return;
        }
        submitting.current = true;
        setBusy(true);
        setError("");
        try {
            for (let index = completedRef.current; index < allocationWorkspaces.length; index++) {
                const workspace = allocationWorkspaces[index];
                const owner = workspace.recipient.owner.reference;
                const allocationPeriod = {start: bounds.start!, end: bounds.end!};
                const answerField: Grants.AnswerFieldForm = {
                    answer: reason.trim(),
                    field: {
                        name: "Sub-allocation description",
                        title: "Sub-allocation description",
                        description: "Describe the reason for creating this sub-allocation",
                        optional: false,
                        maxLength: 4000,
                        rows: 6,
                    },
                };
                await callAPI({
                    ...Grants.submitRevision({
                        comment: "Submitted the application",
                        revision: {
                            recipient: owner.type === "project"
                                ? {type: "existingProject", id: owner.projectId}
                                : {type: "personalWorkspace", username: owner.username},
                            parentProjectId: props.projectId,
                            allocationPeriod,
                            allocationRequests: workspace.rows.flatMap(row => {
                                const slices = slicesFor(row.category, row.rawAdjusted);
                                const packaged = row.category.accountingModel === "CONSUMPTION" && hasSplitSchedule;
                                return slices.filter(slice => packaged || slice.quota > 0).map(slice => ({
                                    category: row.category.name,
                                    provider: row.category.provider,
                                    grantGiver: props.projectId,
                                    balanceRequested: slice.quota,
                                    period: packaged ? {start: slice.start, end: slice.end} : allocationPeriod,
                                    allocationPackage: packaged,
                                }));
                            }),
                            form: {
                                type: "grant_giver_initiated",
                                subAllocator: false,
                                text: "",
                                answerForms: [{
                                    allocatorId: props.projectId,
                                    templateRevisionNumber: -1,
                                    answerFields: [answerField],
                                }],
                            },
                        },
                    }),
                    projectOverride: props.projectId,
                });
                completedRef.current = index + 1;
                setCompleted(index + 1);
            }
            sendSuccessNotification(`Allocations created for ${number(allocationWorkspaces.length)} workspaces.`);
            props.onClose(true);
        } catch (failure) {
            const recipient = allocationWorkspaces[completedRef.current]?.recipient;
            const title = recipient ? bulkAllocationWorkspaceTitle(recipient, props.projectInfo) : "a workspace";
            setError(`Could not allocate to ${title}: ${extractErrorMessage(failure as any)}. ${number(completedRef.current)} of ${number(allocationWorkspaces.length)} workspaces completed. Confirm retries only the remaining workspaces.`);
        } finally {
            submitting.current = false;
            setBusy(false);
        }
    };
    useSubmitShortcut(() => void confirm(), !(step === 4 && canConfirm), {allowInModal: true});

    const renderProduct = (context: UcxTableCellRenderContext): React.ReactNode => {
        const category = categoryByKey.get(context.row.key)!;
        if (context.column.key === "product") return <div className="bulk-product">
            <Icon name={productTypeToIcon(category.productType)} size={16} /><span>{category.name}</span>
        </div>;
        if (context.column.key === "quota" && step === 1) return <div className="bulk-quota">
            <Input
                type="number"
                min={0}
                step="any"
                value={quotas[context.row.key] ?? ""}
                placeholder="0"
                aria-label={`Quota per recipient for ${category.name} (${getProviderTitle(category.provider)})`}
                onChange={event => {
                    const key = context.row.key;
                    const value = event.target.value;
                    const previous = quotas[key] ?? "";
                    if (allocationInputToRaw(category, Number(previous)) !== allocationInputToRaw(category, Number(value))) {
                        clearCategoryOverrides(key);
                    }
                    setQuotas(previousQuotas => ({...previousQuotas, [key]: value}));
                }}
            />
            <span>{bulkAllocationUnit(category)}</span>
        </div>;
        const amount = <AllocationAmount value={Number(context.cell)} unit={bulkAllocationUnit(category)} />;
        if (step === 4 && context.column.key === "quota" && hasSplitSchedule && category.accountingModel === "CONSUMPTION") {
            return <div className="bulk-slice-amount">{amount}<button
                className="bulk-slice-link"
                onClick={() => setBreakdown({category, slices: slicesFor(category, rawQuota(category))})}
            >{schedule.length} slices</button></div>;
        }
        return amount;
    };

    const workspaceGroupTitle = (index: number) => bulkAllocationWorkspaceTitle(props.recipients[index], props.projectInfo);
    const renderAdjustGroupHeader = (group: string): React.ReactNode => {
        const index = Number(group);
        const title = workspaceGroupTitle(index);
        return <div className="bulk-adjust-group">
            <div className="bulk-adjust-workspace">
                <span className="bulk-adjust-workspace-title">{title}</span>
                <span className="bulk-adjust-workspace-meta">
                    {props.recipients[index].owner.reference.type === "user" ? "Personal" : `PI ${props.recipients[index].owner.primaryUsername}`}
                </span>
            </div>
            {!workspaceHasQuota.get(index) ? <span className="bulk-skip-badge">Will be skipped</span> : null}
            <label className="bulk-adjust-shared">
                <span>All products</span>
                <Input
                    type="number"
                    min={0}
                    step="any"
                    value={workspacePercentFor(index)}
                    aria-label={`Shared percentage for ${title}`}
                    onChange={event => setWorkspacePercents(previous => ({...previous, [index]: event.target.value}))}
                />
                <span>%</span>
            </label>
        </div>;
    };
    const renderAdjust = (context: UcxTableCellRenderContext): React.ReactNode => {
        const row = workspaceRowByKey.get(context.row.key);
        if (!row) return context.cell;
        const unit = bulkAllocationUnit(row.category);
        const title = workspaceGroupTitle(row.recipientIndex);
        if (context.column.key === "product") return <div className="bulk-product">
            <ProviderLogo providerId={row.category.provider} size={16} />
            <span>{row.category.name} · {getProviderTitle(row.category.provider)}</span>
        </div>;
        if (context.column.key === "adjusted") return <div className="bulk-adjust-value">
            <div className="bulk-adjust-control">
                <select
                    className="bulk-adjust-mode"
                    value={row.mode}
                    aria-label={`Amount mode for ${row.category.name} in ${title}`}
                    onChange={event => changeRowMode(row, event.target.value as BulkAllocationAdjustMode)}
                >
                    <option value="inherit">Inherit</option>
                    <option value="percent">%</option>
                    <option value="absolute">Value</option>
                </select>
                {row.mode === "inherit" ? <>
                    <Input
                        type="text"
                        readOnly
                        value={number(row.adjusted)}
                        aria-label={`Inherited amount for ${row.category.name} in ${title}`}
                    />
                    <span className="bulk-adjust-suffix">{unit}</span>
                    <span className="bulk-adjust-hint">inherit</span>
                </> : <>
                    <Input
                        type="number"
                        min={0}
                        step="any"
                        value={row.overrideValue}
                        aria-label={`${row.mode === "percent" ? "Percentage" : "Amount"} for ${row.category.name} in ${title}`}
                        onChange={event => setRowOverride(row.key, row.mode === "percent" ? "percent" : "absolute", event.target.value)}
                    />
                    <span className="bulk-adjust-suffix">{row.mode === "percent" ? "%" : unit}</span>
                </>}
            </div>
            {row.mode === "percent" ? <span className="bulk-adjust-result">= {number(row.adjusted)} {unit}</span> : null}
        </div>;
        return <AllocationAmount value={Number(context.cell)} unit={unit} />;
    };
    const renderReviewGroupHeader = (group: string): React.ReactNode => {
        const index = Number(group);
        return <span className="bulk-review-group">
            {workspaceGroupTitle(index)}
            {!workspaceHasQuota.get(index) ? <span className="bulk-skip-badge">Skipped</span> : null}
        </span>;
    };
    const renderReview = (context: UcxTableCellRenderContext): React.ReactNode => {
        const row = workspaceRowByKey.get(context.row.key);
        if (!row) return context.cell;
        const unit = bulkAllocationUnit(row.category);
        if (context.column.key === "product") return <div className="bulk-product">
            <ProviderLogo providerId={row.category.provider} size={16} />
            <span>{row.category.name} · {getProviderTitle(row.category.provider)}</span>
        </div>;
        if (context.column.key === "added" && hasSplitSchedule && row.category.accountingModel === "CONSUMPTION") {
            return <div className="bulk-slice-amount">
                <AllocationAmount value={Number(context.cell)} unit={unit} />
                <button className="bulk-slice-link" onClick={() => setBreakdown({
                    category: row.category,
                    slices: slicesFor(row.category, row.rawAdjusted),
                    workspaceTitle: workspaceGroupTitle(row.recipientIndex),
                })}>{schedule.length} slices</button>
            </div>;
        }
        return <AllocationAmount value={Number(context.cell)} unit={unit} />;
    };

    return <ReactModal
        isOpen
        ariaHideApp={false}
        contentLabel="Bulk allocation"
        shouldCloseOnEsc={!busy}
        shouldCloseOnOverlayClick={!busy}
        onAfterOpen={focusCurrentStep}
        onRequestClose={close}
        className={CardClass}
        style={{...largeModalStyle, content: {...largeModalStyle.content, width: "960px", padding: 0, overflow: "hidden"}}}
        contentElement={(modalProps, children) => <div {...modalProps} onKeyDownCapture={event => {
            if (breakdown) return;
            if (event.currentTarget.querySelector("[data-tag='dropdown'][aria-expanded='true']")) return;
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
            if (!event.altKey || !primaryPressed || event.shiftKey || event.nativeEvent.isComposing) return;
            if (event.code !== "KeyP" && event.code !== "KeyN") return;
            event.preventDefault();
            event.stopPropagation();
            if (event.repeat || submitting.current) return;
            if (event.code === "KeyP" && canGoBack) move(step - 1);
            if (event.code === "KeyN" && step < 4 && canGoNext) move(step + 1);
        }}>{children}</div>}
    >
        <KeyboardNavigation className={BulkAllocationWizardStyle} navigationSelector="[data-navigation-field]">
            <header>
                <h3>Allocate resources</h3>
                <nav className="bulk-steps" aria-label="Allocation steps">
                    {names.map((name, index) => <span key={name} aria-current={index === step ? "step" : undefined}>{index + 1}. {name}</span>)}
                </nav>
            </header>
            <div className="bulk-content" data-step={step} ref={contentRef} tabIndex={-1}>
                {step === 0 ? <>
                    <p>Choose the period for the new allocations. All selected workspaces receive the same period.</p>
                    <AllocationPeriodFilter value={period} onChange={setPeriod} navigationField />
                    <div className="bulk-split-settings">
                        <label><input
                            type="checkbox"
                            checked={splitEnabled}
                            onChange={event => setSplitEnabled(event.target.checked)}
                        />Split consumption-based allocations into slices</label>
                        {splitEnabled ? <>
                            <label>Every <Input
                                type="number"
                                min={1}
                                step={1}
                                value={splitMonths}
                                aria-label="Months per slice"
                                onChange={event => setSplitMonths(event.target.value)}
                            /> months</label>
                            <p className="bulk-split-note">
                                {scheduleValid ? `${schedule.length} calendar slice${schedule.length === 1 ? "" : "s"}. ` : ""}
                                Divide the total across slices by calendar-month share. Unused quota expires after each slice.
                                {" "}Capacity-based products remain unsplit.
                            </p>
                        </> : null}
                    </div>
                    {!periodValid ? <p className="bulk-error">Choose a valid allocation period.</p> : null}
                </> : null}
                {step === 1 ? <>
                    <p>Enter a base quota for the full allocation period. Leave other products empty.
                        {hasSplitSchedule ? " Consumption-based quotas will be divided across the slices." : ""}
                    </p>
                    <BulkAllocationTable
                        id="bulk-allocation-products"
                        variant="products"
                        columns={bulkAllocationProductColumns}
                        rows={productRows}
                        renderCell={renderProduct}
                        renderGroupHeader={provider => <span className="bulk-provider"><ProviderLogo providerId={provider} size={18} />{getProviderTitle(provider)}</span>}
                        editable
                    />
                    {invalidQuota ? <p className="bulk-error">Enter a valid quota of zero or more. Positive quotas must reach at least one accounting unit.</p> : null}
                    {categories.length === 0 ? <p>No products are available for allocation.</p> : null}
                </> : null}
                {step === 2 ? <>
                    <p>Adjust the amount for each workspace. The shared percentage applies to every product in that workspace. Product rows can inherit it, use their own percentage, or use an absolute amount.</p>
                    <BulkAllocationTable
                        id="bulk-allocation-adjust"
                        variant="adjust"
                        columns={bulkAllocationAdjustColumns}
                        rows={adjustTableRows}
                        renderCell={renderAdjust}
                        renderGroupHeader={renderAdjustGroupHeader}
                        editable
                    />
                    {adjustmentInvalid ? <p className="bulk-error">Enter a valid percentage or amount for every adjusted value.</p> : null}
                    {allocationWorkspaces.length === 0 ? <p className="bulk-error">At least one workspace must receive a positive amount.</p> : null}
                </> : null}
                {step === 3 ? <label className="bulk-reason">
                    <p>Describe the reason for creating these sub-allocations.</p>
                    <textarea
                        aria-label="Reason for allocation"
                        data-navigation-field
                        value={reason}
                        onChange={event => setReason(event.target.value)}
                        maxLength={4000}
                        rows={6}
                        required
                    />
                    <span>{number(reason.length)} / {number(4000)}</span>
                </label> : null}
                {step === 4 ? <>
                    <section className="bulk-review-section">
                        <span className="bulk-review-label">Allocation period</span>
                        <div className="bulk-review-dates">
                            <div className="bulk-review-date">
                                <span className="bulk-review-date-value">{bounds.start !== null ? format(bounds.start, "d MMM yyyy") : "-"}</span>
                                <span className="bulk-review-date-time">{bounds.start !== null ? format(bounds.start, "HH:mm") : ""}</span>
                            </div>
                            <Icon name="heroArrowRight" size={14} />
                            <div className="bulk-review-date">
                                <span className="bulk-review-date-value">{bounds.end !== null ? format(bounds.end, "d MMM yyyy") : "-"}</span>
                                <span className="bulk-review-date-time">{bounds.end !== null ? format(bounds.end, "HH:mm") : ""}</span>
                            </div>
                        </div>
                        {splitEnabled ? <span className="bulk-split-note">{scheduleText}. Consumption-based products only.</span> : null}
                    </section>
                    <section className="bulk-review-section">
                        <span className="bulk-review-label">Reason</span>
                        <div className="bulk-review-reason">
                            <MarkdownDocument text={bulkAllocationReasonBlockquote(reason)} />
                        </div>
                    </section>
                    <section className="bulk-review-section bulk-review-products">
                        <span className="bulk-review-label">Products</span>
                        <BulkAllocationTable
                            id="bulk-allocation-products"
                            variant="products"
                            columns={bulkAllocationProductColumns}
                            rows={productRows}
                            renderCell={renderProduct}
                            renderGroupHeader={provider => <span className="bulk-provider"><ProviderLogo providerId={provider} size={18} />{getProviderTitle(provider)}</span>}
                        />
                    </section>
                    <section className="bulk-review-section bulk-review-totals">
                        <span className="bulk-review-label">Workspace totals</span>
                        <p className="bulk-note">Current quota includes existing allocations that overlap the chosen period. These allocations might not run at the same time.</p>
                        <BulkAllocationTable
                            id="bulk-allocation-review"
                            variant="review"
                            columns={bulkAllocationReviewColumns}
                            rows={reviewTableRows}
                            renderGroupHeader={renderReviewGroupHeader}
                            renderCell={renderReview}
                        />
                    </section>
                    {completed > 0 ? <p>{number(completed)} of {number(allocationWorkspaces.length)} workspaces completed.</p> : null}
                    {error ? <p className="bulk-error" role="alert">{error}</p> : null}
                </> : null}
            </div>
            <footer className="bulk-actions">
                <div className="bulk-progress"><TutorialWizardProgress step={step} stepCount={5} /></div>
                <Button color="errorMain" disabled={busy} onClick={close}>Cancel</Button>
                <Button color="secondaryMain" ariaLabel="Back" disabled={!canGoBack} onClick={() => move(step - 1)}>
                    Back<SubmitShortcut shortcut="P" />
                </Button>
                <TooltipV2 tooltip={nextDisabledReason} contentWidth={240}>
                    <Button
                        color="successMain"
                        ariaLabel={step < 4 ? "Next" : undefined}
                        disabled={!canGoNext}
                        onClick={() => step === 4 ? confirm() : move(step + 1)}
                    >
                        {busy ? `Allocating ${number(completed)} / ${number(allocationWorkspaces.length)}` : step === 4 ? "Confirm" : "Next"}
                        {step < 4 ? <SubmitShortcut shortcut="N" /> : <SubmitShortcut />}
                    </Button>
                </TooltipV2>
            </footer>
            {breakdown ? <AllocationBreakdownDialog {...breakdown} onClose={() => setBreakdown(null)} /> : null}
        </KeyboardNavigation>
    </ReactModal>;
}
