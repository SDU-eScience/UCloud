import * as React from "react";
import {useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import ReactModal from "react-modal";
import {format} from "date-fns";
import {allocationInputToRaw, allocationInputUnit, allocationInputValue, productCategoryDisplayName, productTypeToIcon} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {KeyboardNavigation, SubmitShortcut, useSubmitShortcut} from "@/Applications/KeyboardNavigation";
import * as Grants from "@/Grants";
import {sendSuccessNotification} from "@/Notifications";
import {RecipientPicker} from "./RecipientPicker";
import type {BulkRecipient} from "./RecipientPicker";
import type {ProjectInfo} from "@/Project/InfoCache";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {UcxStreamedTable, UcxTableStore} from "@/UCX/UcxBrowser";
import type {UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {Button, Icon, Input, Select} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {MarkdownDocument} from "@/ui-components/Markdown";
import {Toggle} from "@/ui-components/Toggle";
import {TutorialWizardProgress} from "@/ui-components/TutorialWizard";
import {TooltipV2} from "@/ui-components/Tooltip";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {doNothing, extractErrorMessage, isLikelyMac, isLikelySafari} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {Amount} from "./Table";
import {PeriodFilter} from "./Controls";
import {
    categoryKey,
    compareProducts,
    formatAmount,
    rangeBounds,
    workspaceInfo,
} from "./Model";
import type {DateRange, Recipient} from "./Model";

const backKey = isLikelySafari ? "J" : "B";
const nextKey = isLikelySafari ? "K" : "N";
const backCode = `Key${backKey}`;
const nextCode = `Key${nextKey}`;

interface Slice {
    allocationId?: number;
    start: number;
    end: number;
    quota: number;
}

function calendarTime(timestamp: number): number {
    const date = new Date(timestamp);
    return Date.UTC(
        date.getFullYear(),
        date.getMonth(),
        date.getDate(),
        date.getHours(),
        date.getMinutes(),
        date.getSeconds(),
        date.getMilliseconds(),
    );
}

function monthShare(start: number, end: number): number {
    let share = 0;
    let cursor = start;
    while (cursor < end) {
        const date = new Date(cursor);
        const monthStart = new Date(date.getFullYear(), date.getMonth(), 1).getTime();
        const monthEnd = new Date(date.getFullYear(), date.getMonth() + 1, 1).getTime();
        const segmentEnd = Math.min(end, monthEnd);
        share += (calendarTime(segmentEnd) - calendarTime(cursor))
            / (calendarTime(monthEnd) - calendarTime(monthStart));
        cursor = segmentEnd;
    }
    return share;
}

function slices(start: number, end: number, months: number, quota: number): Slice[] {
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return [];
    if (!Number.isInteger(months) || months <= 0) return [{start, end, quota}];

    const first = new Date(start);
    const last = new Date(end - 1);
    const coveredMonths = (last.getFullYear() - first.getFullYear()) * 12 + last.getMonth() - first.getMonth() + 1;
    if (months >= coveredMonths) return [{start, end, quota}];
    const periods: {start: number; end: number; share: number}[] = [];
    let cursor = start;
    let offset = months;
    while (cursor < end) {
        const boundary = new Date(first.getFullYear(), first.getMonth() + offset, 1).getTime();
        const sliceEnd = Math.min(end, boundary);
        periods.push({start: cursor, end: sliceEnd, share: monthShare(cursor, sliceEnd)});
        cursor = sliceEnd;
        offset += months;
    }

    const totalShare = periods.reduce((sum, slice) => sum + slice.share, 0);
    let allocated = 0;
    let cumulativeShare = 0;
    return periods.map((period, index) => {
        cumulativeShare += period.share;
        const cumulativeQuota = index === periods.length - 1 ? quota : Math.round(quota * cumulativeShare / totalShare);
        const sliceQuota = cumulativeQuota - allocated;
        allocated = cumulativeQuota;
        return {start: period.start, end: period.end, quota: sliceQuota};
    });
}

const productColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "quota", label: "Quota per recipient", jsonPath: "quota", sortType: TableColumnSortType.Number},
    {key: "total", label: "Batch total", jsonPath: "total", sortType: TableColumnSortType.Number},
];

const baseAdjustColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "current", label: "Current quota", jsonPath: "current", sortType: TableColumnSortType.Number},
    {key: "base", label: "Base", jsonPath: "base", sortType: TableColumnSortType.Number},
    {key: "adjusted", label: "Adjusted", jsonPath: "adjusted", sortType: TableColumnSortType.Number},
    {key: "total", label: "New total", jsonPath: "total", sortType: TableColumnSortType.Number},
];

const baseReviewColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "current", label: "Current quota", jsonPath: "current", sortType: TableColumnSortType.Number},
    {key: "added", label: "Added quota", jsonPath: "added", sortType: TableColumnSortType.Number},
    {key: "total", label: "New total", jsonPath: "total", sortType: TableColumnSortType.Number},
];

const sliceColumn: TableColumn = {
    key: "slices",
    label: "Per slice",
    jsonPath: "slices",
    sortType: TableColumnSortType.Number,
};

const sliceDescription =
    "Provides finer control over how consumption based allocations are split up into several slices. "
    + "Quota not consumed in a given slice is not carried over to the next slice.";

const BulkAllocationStyle = injectStyle("bulk-allocation-wizard", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
        min-width: 0;
        color: var(--textPrimary);
    }

    ${k} > header {
        padding: 20px 24px 16px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} h3, ${k} h4 {
        margin: 0 0 8px;
    }

    ${k} p {
        margin: 0 0 16px;
        color: var(--textSecondary);
    }

    ${k} .bulk-content {
        display: flex;
        flex-direction: column;
        flex: 1;
        gap: 16px;
        min-height: 0;
        min-width: 0;
        padding: 24px;
        overflow: auto;
        outline: none;
    }

    ${k} .bulk-content:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: -2px;
    }

    ${k} .bulk-content > p {
        flex-shrink: 0;
        margin: 0;
    }

    ${k} .bulk-content[data-step="-1"], ${k} .bulk-content[data-step="1"], ${k} .bulk-content[data-step="2"] {
        overflow: hidden;
    }

    ${k} .bulk-content[data-step="4"] {
        gap: 12px;
        padding-top: 16px;
        padding-bottom: 16px;
    }

    ${k} .bulk-steps {
        display: flex;
        flex-wrap: wrap;
        gap: 8px 20px;
        margin-top: 16px;
        color: var(--textSecondary);
        font-size: 12px;
    }

    ${k} .bulk-steps [aria-current="step"] {
        color: var(--primaryMain);
        font-weight: 600;
    }

    ${k} .bulk-actions {
        display: flex;
        align-items: center;
        flex: 0 0 auto;
        flex-wrap: wrap;
        gap: 8px;
        padding: 12px 24px;
        background: var(--dialogToolbar);
    }

    ${k} .bulk-actions .bulk-progress {
        flex: 1;
        min-width: 0;
    }

    ${k} .bulk-table {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
        min-width: 0;
    }

    ${k} .bulk-content[data-step="4"] > .bulk-table {
        min-height: 130px;
    }

    ${k} .bulk-table .streamed-table-frame {
        min-height: 0;
    }

    ${k} .bulk-product, ${k} .bulk-provider, ${k} .bulk-quota {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    ${k} .bulk-product span {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .bulk-product svg, ${k} .bulk-provider svg {
        flex-shrink: 0;
    }

    ${k} .bulk-quota input {
        width: 120px;
        min-width: 0;
        height: 32px;
    }

    ${k} .bulk-quota span {
        color: var(--textSecondary);
        font-size: 12px;
        white-space: normal;
    }

    ${k} .bulk-reason {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    ${k} textarea {
        box-sizing: border-box;
        width: 100%;
        min-height: 180px;
        padding: 12px;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        font: inherit;
        resize: vertical;
    }

    ${k} .bulk-review-section {
        display: flex;
        flex-direction: column;
        flex: 0 0 auto;
        min-width: 0;
    }

    ${k} .bulk-review-products {
        flex: 1 1 180px;
        min-height: 160px;
    }

    ${k} .bulk-review-totals {
        flex: 2 1 220px;
        min-height: 180px;
    }

    ${k} .bulk-review-label {
        display: block;
        margin-bottom: 6px;
        font-weight: 600;
    }

    ${k} .bulk-review-dates {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 10px;
    }

    ${k} .bulk-review-date {
        display: flex;
        align-items: baseline;
        gap: 6px;
        min-width: 0;
    }

    ${k} .bulk-review-date-value {
        font-variant-numeric: tabular-nums;
    }

    ${k} .bulk-review-date-time {
        color: var(--textSecondary);
        font-size: 12px;
        font-variant-numeric: tabular-nums;
    }

    ${k} .bulk-review-reason {
        max-height: 72px;
        margin: 0;
        overflow: auto;
        overflow-wrap: anywhere;
    }

    ${k} .bulk-review-reason blockquote {
        margin: 0;
    }

    ${k} .bulk-review-reason blockquote p {
        margin: 0;
    }

    ${k} .bulk-split {
        display: flex;
        flex-direction: column;
        gap: 10px;
        min-width: 0;
    }

    ${k} .bulk-split-toggle {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        margin-top: 10px;
    }
    
    ${k} .bulk-split-toggle > div:first-child {
        flex-shrink: 0;
    }
    
    ${k} .bulk-split-toggle > div:last-child {
        display: flex;
        flex-direction: column;
        gap: 2px;
    }
    
    ${k} .bulk-split-toggle strong {
        font-size: 14px;
        line-height: 20px;
    }
    
    ${k} .bulk-split-toggle span {
        color: var(--textSecondary);
        font-size: 13px;
        line-height: 18px;
    }

    ${k} .bulk-split-length {
        display: flex;
        flex-direction: column;
        gap: 4px;
        color: var(--textSecondary);
        font-size: 13px;
        height: 30px;
    }

    ${k} .bulk-split-length-control {
        display: flex;
        align-items: center;
        gap: 8px;
        white-space: nowrap;
        margin-top: 12px;
    }

    ${k} .bulk-split-length input {
        width: 76px;
        min-width: 0;
        height: 30px;
    }

    ${k} .bulk-review-slices {
        color: var(--textSecondary);
        font-size: 12px;
        white-space: nowrap;
    }

    ${k} .bulk-slice-unit, ${k} .bulk-slice-empty {
        color: var(--textSecondary);
    }

    ${k} .bulk-slice-unit {
        font-size: 10px;
    }

    ${k} .bulk-slice-empty {
        font-size: 11px;
    }

    ${k} .bulk-adjust-group, ${k} .bulk-review-group {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 6px 16px;
        min-width: 0;
    }

    ${k} .bulk-review-group {
        display: inline-flex;
        gap: 8px;
    }

    ${k} .bulk-adjust-workspace {
        display: flex;
        flex-direction: column;
        gap: 1px;
        min-width: 0;
    }

    ${k} .bulk-adjust-workspace-title {
        overflow: hidden;
        font-weight: 600;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .bulk-adjust-workspace-meta {
        color: var(--textSecondary);
        font-size: 10.5px;
    }

    ${k} .bulk-adjust-shared {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-left: auto;
        color: var(--textSecondary);
        font-size: 12px;
        white-space: nowrap;
    }

    ${k} .bulk-adjust-shared input {
        width: 70px;
        height: 28px;
    }

    ${k} .bulk-adjust-value {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 3px;
        min-width: 0;
    }

    ${k} .bulk-adjust-control {
        display: flex;
        align-items: center;
        gap: 6px;
        min-width: 0;
    }

    ${k} .bulk-adjust-control input {
        flex: 1 1 82px;
        min-width: 54px;
        height: 30px;
    }

    ${k} .bulk-adjust-control select {
        height: 30px;
        padding-top: 0;
        padding-bottom: 0;
        font-size: 12px;
    }

    ${k} .bulk-adjust-suffix {
        color: var(--textSecondary);
        font-size: 11px;
        white-space: nowrap;
    }

    ${k} .bulk-skip-badge {
        padding: 1px 6px;
        border-radius: 999px;
        background: color-mix(in srgb, var(--warningMain) 20%, transparent);
        color: var(--textPrimary);
        font-size: 10px;
        font-weight: 600;
        white-space: nowrap;
    }

    ${k} .bulk-error {
        margin-top: 16px;
        color: var(--errorMain);
        overflow-wrap: anywhere;
    }

    ${k} .bulk-table thead th:not(:first-child) {
        text-align: right;
    }

    ${k} .bulk-table[data-table="products"][data-editable="true"] thead th:nth-child(2) {
        text-align: left;
    }

    ${k} .bulk-table[data-table="adjust"] thead th:nth-child(4) {
        text-align: left;
    }

    ${k} .bulk-table .bulk-slice-values {
        display: flex;
        justify-content: flex-end;
        overflow: hidden;
        white-space: nowrap;
    }

    @media (max-width: 640px) {
        ${k} > header, ${k} .bulk-content {
            padding: 16px;
        }

        ${k} .bulk-actions {
            padding: 12px 16px;
        }

        ${k} .bulk-progress {
            flex-basis: 100%;
        }
    }
`);

function workspaceTitle(
    recipient: Recipient,
    projectInfo: Record<string, ProjectInfo | null>,
): string {
    return workspaceInfo(recipient.owner, projectInfo).title;
}

function workspacePi(
    recipient: Recipient,
    projectInfo: Record<string, ProjectInfo | null>,
): string | null {
    const owner = recipient.owner.reference;
    if (owner.type === "user") return null;
    return workspaceInfo(recipient.owner, projectInfo).pi;
}

function editValue(value: number): string {
    return Number.isFinite(value) ? String(Number(value.toFixed(6))) : "";
}

function reasonBlockquote(reason: string): string {
    return reason.split("\n").map(line => `> ${line}`).join("  \n");
}

type AdjustMode = "inherit" | "percent" | "absolute";

interface WorkspaceRow {
    key: string;
    recipientIndex: number;
    category: ProductCategoryV2;
    current: number;
    base: number;
    adjusted: number;
    rawAdjusted: number;
    total: number;
    mode: AdjustMode;
    overrideValue: string;
    invalid: boolean;
}

function BulkTable(props: {
    id: string;
    variant: "products" | "adjust" | "review";
    columns: TableColumn[];
    rows: TableRow[];
    renderCell: (context: UcxTableCellRenderContext) => React.ReactNode;
    renderGroupHeader: (group: string) => React.ReactNode;
    editable?: boolean;
    hasSlices?: boolean;
    onNavigateEnd?: () => void;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    const tableRef = useRef<HTMLDivElement>(null);
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

    return <div ref={tableRef} className="bulk-table" data-bulk-table-navigation={props.editable ? "" : undefined}
        data-editable={props.editable} data-slices={props.hasSlices} data-table={props.variant}>
        <UcxStreamedTable
            tableId={props.id}
            store={store}
            preserveRowOrder
            navigateInputs={props.editable}
            navigateInputsHorizontal={props.variant === "adjust"}
            onNavigateInputsEnd={props.onNavigateEnd}
            onNavigateInputsEscape={() => tableRef.current
                ?.querySelector<HTMLElement>(`[data-ucx-table="${props.id}"]`)
                ?.focus({preventScroll: true})}
            showControls={false}
            density="compact"
            rowHeight={45}
            fitToWidth
            columnWidths={props.variant === "products"
                ? {product: 300, quota: 260, total: 160}
                : props.variant === "adjust"
                    ? props.hasSlices
                        ? {product: 240, current: 100, base: 90, adjusted: 290, total: 130, slices: 230}
                        : {product: 260, current: 110, base: 100, adjusted: 270, total: 140}
                    : props.hasSlices
                        ? {product: 400, current: 140, added: 140, total: 140, slices: 160}
                        : {product: 400, current: 150, added: 150, total: 150}}
            renderCell={props.renderCell}
            renderGroupHeader={props.renderGroupHeader}
            onRowActivated={doNothing}
        />
    </div>;
}

export function BulkAllocation(props: {
    projectId: string;
    recipients: Recipient[];
    availableRecipients: Recipient[];
    canCreateSubAllocator: boolean;
    categories: ProductCategoryV2[];
    projectInfo: Record<string, ProjectInfo | null>;
    dateRange: DateRange;
    onClose: (changed: boolean) => void;
}): React.ReactNode {
    const showRecipientStep = props.recipients.length !== 1;
    const [recipients, setRecipients] = useState<BulkRecipient[]>(props.recipients);
    const [draftPending, setDraftPending] = useState(false);
    const showAdjust = recipients.length > 1;
    const stepOrder = [...(showRecipientStep ? [-1] : []), 0, 1, ...(showAdjust ? [2] : []), 3, 4];
    const [step, setStep] = useState(showRecipientStep ? -1 : 0);
    const [period, setPeriod] = useState(props.dateRange);
    const [splitEnabled, setSplitEnabled] = useState(false);
    const [splitMonths, setSplitMonths] = useState("1");
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
    const primaryButtonRef = useRef<HTMLButtonElement>(null);
    const cancelButtonRef = useRef<HTMLButtonElement>(null);
    useEffect(() => {
        const page = document.querySelector<HTMLElement>('[data-mode="allocations"]');
        page?.setAttribute("inert", "");
        return () => page?.removeAttribute("inert");
    }, []);
    const focusCurrentStep = () => {
        const content = contentRef.current;
        if (!content) return;
        let target: HTMLElement | null = null;
        if (step === -1) {
            target = content.querySelector<HTMLElement>("[data-recipient-table='available'] [data-ucx-table]") ??
                content.querySelector<HTMLElement>("[data-recipient-search]");
        } else if (step === 0) {
            target = content.querySelector<HTMLElement>(
                "[data-allocation-shortcut='period'] [data-tag='dropdown']"
            );
        } else if (step === 1) {
            target = content.querySelector<HTMLElement>("[data-bulk-quota-input]");
        } else if (step === 2) {
            target = content.querySelector<HTMLElement>(
                "[data-bulk-adjust-control] input, [data-bulk-adjust-control] select"
            );
        } else if (step === 3) {
            target = content.querySelector<HTMLElement>("textarea[data-navigation-field]");
        }
        (target ?? content).focus({preventScroll: true});
    };
    const focusFirstInvalid = () => {
        const content = contentRef.current;
        if (!content) return;
        let target: HTMLElement | null = null;
        if (step === -1) {
            target = content.querySelector<HTMLElement>("[data-recipient-draft] input") ??
                content.querySelector<HTMLElement>("[data-recipient-search]");
        } else if (step === 0) {
            target = !periodValid
                ? content.querySelector<HTMLElement>(
                    "[data-allocation-shortcut='period'] [data-tag='dropdown']"
                )
                : content.querySelector<HTMLElement>("[data-bulk-split-length] input");
            target = target ?? content.querySelector<HTMLElement>(
                "[data-allocation-shortcut='period'] [data-tag='dropdown']"
            );
        } else if (step === 1) {
            target = content.querySelector<HTMLElement>("[data-bulk-quota-input][aria-invalid='true']") ??
                content.querySelector<HTMLElement>("[data-bulk-quota-input]");
        } else if (step === 2) {
            target = content.querySelector<HTMLElement>("[data-bulk-invalid='true'] input") ??
                content.querySelector<HTMLElement>("[data-bulk-invalid='true'] select") ??
                content.querySelector<HTMLElement>(
                    "[data-bulk-adjust-control] input, [data-bulk-adjust-control] select"
                );
        } else if (step === 3) {
            target = content.querySelector<HTMLElement>("textarea[data-navigation-field]");
        }
        (target ?? content).focus({preventScroll: true});
    };
    useLayoutEffect(() => {
        const frame = requestAnimationFrame(focusCurrentStep);
        return () => cancelAnimationFrame(frame);
    }, [step]);
    const categories = useMemo(() => [...props.categories].sort(compareProducts), [props.categories]);
    const categoryByKey = useMemo(
        () => new Map(categories.map(category => [categoryKey(category), category])),
        [categories],
    );
    const rawQuota = (category: ProductCategoryV2) => allocationInputToRaw(
        category,
        Number(quotas[categoryKey(category)] ?? 0),
    );
    const chosen = categories.filter(category => Number(quotas[categoryKey(category)]) > 0);
    const quotaInvalid = (category: ProductCategoryV2): boolean => {
        const value = quotas[categoryKey(category)] ?? "";
        const valueNumber = Number(value);
        const raw = rawQuota(category);
        return value !== ""
            && (!Number.isFinite(valueNumber)
                || !Number.isFinite(raw)
                || valueNumber < 0
                || (valueNumber > 0 && raw <= 0));
    };
    const invalidQuota = categories.some(quotaInvalid);
    const workspacePercentFor = (index: number) => workspacePercents[index] ?? "100";
    const workspacePercentNumber = (index: number): number | null => {
        const value = workspacePercentFor(index).trim();
        if (value === "") return null;
        const parsed = Number(value);
        return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
    };
    const bounds = rangeBounds(period);
    const periodValid = bounds.start !== null && bounds.end !== null
        && Number.isFinite(bounds.start) && Number.isFinite(bounds.end) && bounds.end >= bounds.start;
    const scheduleValid = !splitEnabled || (Number.isInteger(Number(splitMonths)) && Number(splitMonths) > 0);
    const schedule = periodValid && scheduleValid
        ? slices(bounds.start!, bounds.end! + 1, splitEnabled ? Number(splitMonths) : 0, 0)
        : [];
    const slicesFor = (category: ProductCategoryV2, quota: number) => slices(
        bounds.start!,
        bounds.end! + 1,
        splitEnabled && category.accountingModel === "CONSUMPTION" ? Number(splitMonths) : 0,
        quota,
    );
    const hasSplitSchedule = splitEnabled && schedule.length > 1;
    const adjustColumns = useMemo(
        () => hasSplitSchedule
            ? [...baseAdjustColumns, sliceColumn]
            : baseAdjustColumns,
        [hasSplitSchedule],
    );
    const reviewColumns = useMemo(
        () => hasSplitSchedule
            ? [...baseReviewColumns, sliceColumn]
            : baseReviewColumns,
        [hasSplitSchedule],
    );
    const sliceCadence = Number(splitMonths) === 1 ? "one every month" : `one every ${splitMonths} months`;
    const workspaceRows: WorkspaceRow[] = [];
    recipients.forEach((recipient, recipientIndex) => {
        for (const category of chosen) {
            const categoryKeyValue = categoryKey(category);
            const key = `${recipientIndex}/${categoryKeyValue}`;
            const base = allocationInputValue(category, rawQuota(category));
            const currentRaw = recipient.products
                .filter(product => categoryKey(product.category) === categoryKeyValue)
                .reduce((sum, product) => sum + product.raw.activeQuota, 0);
            const current = allocationInputValue(category, currentRaw);
            const override: {mode: "percent" | "absolute"; value: string} | undefined = rowOverrides[key];
            const mode: AdjustMode = override ? override.mode : "inherit";
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
            const key = categoryKey(row.category);
            batchRawQuota.set(key, (batchRawQuota.get(key) ?? 0) + row.rawAdjusted);
        }
    }
    const allocationWorkspaces = recipients
        .map((recipient, recipientIndex) => ({
            recipient,
            rows: workspaceRows.filter(row => row.recipientIndex === recipientIndex && row.rawAdjusted > 0),
        }))
        .filter(workspace => workspace.rows.length > 0);
    const locked = completed > 0;
    const stepIndex = stepOrder.indexOf(step);
    const lastStep = stepOrder[stepOrder.length - 1];
    const nextStep = stepOrder[stepIndex + 1] ?? null;
    const previousStep = stepOrder[stepIndex - 1] ?? null;
    const canGoBack = previousStep !== null && !busy && !locked;
    const canConfirm = !busy && periodValid && scheduleValid && !invalidQuota && chosen.length > 0
        && !adjustmentInvalid && allocationWorkspaces.length > 0 && !!reason.trim();
    const canGoNext = !busy && (step === lastStep
        ? canConfirm
        : !locked && (step === -1 ? recipients.length > 0 && !draftPending : step === 0
            ? periodValid && scheduleValid
            : step === 1
                ? !invalidQuota && chosen.length > 0
                : step === 2
                    ? !adjustmentInvalid && allocationWorkspaces.length > 0
                    : !!reason.trim()));
    const nextDisabledReason = canGoNext ? undefined : (() => {
        if (busy) return "Allocation is in progress.";
        if (step === -1) {
            return draftPending
                ? "Add the draft sub-project or clear the row."
                : "Choose at least one recipient.";
        }
        if (!scheduleValid) return "Enter a positive whole number of months per slice.";
        if (step === 0) return "Choose a valid allocation period.";
        if (invalidQuota) return "Enter a valid quota, or clear the invalid value.";
        if (chosen.length === 0) return "Enter a quota for at least one product.";
        if (step >= 2 && adjustmentInvalid) {
            return "Enter a valid percentage or amount for every adjusted value.";
        }
        if (step >= 2 && allocationWorkspaces.length === 0) {
            return "At least one workspace must receive a positive amount.";
        }
        if (!reason.trim()) return "Enter a reason for the allocation.";
        return "Complete the required fields to continue.";
    })();
    const names = [
        ...(showRecipientStep ? ["Recipients"] : []),
        "Period",
        "Products",
        ...(showAdjust ? ["Adjust"] : []),
        "Reason",
        "Review",
    ];
    const number = formatAmount;
    const productRows: TableRow[] = (step === 4 ? chosen : categories).map(category => {
        const key = categoryKey(category);
        const quota = allocationInputValue(category, rawQuota(category));
        const total = step === 4
            ? allocationInputValue(category, batchRawQuota.get(key) ?? 0)
            : quota * recipients.length;
        return {key, group: category.provider, cells: [productCategoryDisplayName(category), `${quota}`, `${total}`]};
    });
    const perSliceAmount = (row: WorkspaceRow): number | null => {
        if (!hasSplitSchedule || row.category.accountingModel !== "CONSUMPTION" || row.rawAdjusted <= 0) return null;
        return row.adjusted / schedule.length;
    };
    const perSliceCellText = (row: WorkspaceRow): string => {
        const amount = perSliceAmount(row);
        return amount === null ? "" : `${number(amount)} ${allocationInputUnit(row.category).name}`;
    };
    const sliceCells = (row: WorkspaceRow): string[] => hasSplitSchedule ? [perSliceCellText(row)] : [];
    const adjustTableRows: TableRow[] = workspaceRows.map(row => ({
        key: row.key,
        group: `${row.recipientIndex}`,
        cells: [
            productCategoryDisplayName(row.category),
            `${row.current}`,
            `${row.base}`,
            `${row.adjusted}`,
            `${row.total}`,
            ...sliceCells(row),
        ],
    }));
    const reviewTableRows: TableRow[] = workspaceRows.map(row => ({
        key: row.key,
        group: `${row.recipientIndex}`,
        cells: [productCategoryDisplayName(row.category), `${row.current}`, `${row.adjusted}`, `${row.total}`, ...sliceCells(row)],
    }));
    const workspaceRowByKey = new Map(workspaceRows.map(row => [row.key, row]));

    const clearCategoryOverrides = (categoryKeyValue: string) => {
        const suffix = `/${categoryKeyValue}`;
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
    const changeRowMode = (row: WorkspaceRow, mode: AdjustMode) => {
        if (mode === "inherit") {
            clearRowOverride(row.key);
            return;
        }
        const key = row.key;
        if (mode === "percent") {
            const percent = row.mode === "inherit"
                ? workspacePercentFor(row.recipientIndex)
                : row.base > 0
                    ? editValue(row.adjusted / row.base * 100)
                    : workspacePercentFor(row.recipientIndex);
            setRowOverride(key, mode, percent);
        } else {
            setRowOverride(key, mode, editValue(row.adjusted));
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
        submitting.current = true;
        setBusy(true);
        setError("");
        try {
            for (let index = completedRef.current; index < allocationWorkspaces.length; index++) {
                const workspace = allocationWorkspaces[index];
                const owner = workspace.recipient.owner.reference;
                const draft = workspace.recipient.newProject;
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
                            recipient: draft ? {type: "newProject", title: draft.title} : owner.type === "project"
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
                                subAllocator: draft?.subAllocator ?? false,
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
            const title = recipient ? workspaceTitle(recipient, props.projectInfo) : "a workspace";
            const completed = number(completedRef.current);
            const total = number(allocationWorkspaces.length);
            setError(
                `Could not allocate to ${title}: ${extractErrorMessage(failure as any)}. `
                + `${completed} of ${total} workspaces completed. `
                + "Confirm retries only the remaining workspaces."
            );
        } finally {
            submitting.current = false;
            setBusy(false);
        }
    };
    const primaryAction = () => {
        if (busy) return;
        if (step === lastStep) {
            if (!canConfirm) {
                focusFirstInvalid();
                return;
            }
            void confirm();
            return;
        }
        if (!canGoNext) {
            focusFirstInvalid();
            return;
        }
        if (nextStep !== null) move(nextStep);
    };
    useSubmitShortcut(primaryAction, busy, {allowInModal: true});

    const renderProviderGroupHeader = (provider: string): React.ReactNode => (
        <span className="bulk-provider">
            <ProviderLogo providerId={provider} size={18} />
            {getProviderTitle(provider)}
        </span>
    );

    const renderProduct = (context: UcxTableCellRenderContext): React.ReactNode => {
        const category = categoryByKey.get(context.row.key)!;
        if (context.column.key === "product") return <div className="bulk-product">
            <Icon name={productTypeToIcon(category.productType)} size={16} /><span>{productCategoryDisplayName(category)}</span>
        </div>;
        if (context.column.key === "quota" && step === 1) return <div className="bulk-quota">
            <Input
                type="number"
                min={0}
                step="any"
                value={quotas[context.row.key] ?? ""}
                placeholder="0"
                data-bulk-quota-input=""
                aria-invalid={quotaInvalid(category)}
                aria-label={`Quota per recipient for ${productCategoryDisplayName(category)} (${getProviderTitle(category.provider)})`}
                onChange={event => {
                    const key = context.row.key;
                    const value = event.target.value;
                    const previous = quotas[key] ?? "";
                    const previousRaw = allocationInputToRaw(category, Number(previous));
                    const nextRaw = allocationInputToRaw(category, Number(value));
                    if (previousRaw !== nextRaw) {
                        clearCategoryOverrides(key);
                    }
                    setQuotas(previousQuotas => ({...previousQuotas, [key]: value}));
                }}
            />
            <span>{allocationInputUnit(category).name}</span>
        </div>;
        return <Amount value={Number(context.cell)} unit={allocationInputUnit(category).name} />;
    };

    const workspaceGroupTitle = (index: number) => workspaceTitle(recipients[index], props.projectInfo);
    const renderAdjustGroupHeader = (group: string): React.ReactNode => {
        const index = Number(group);
        const title = workspaceGroupTitle(index);
        const pi = workspacePi(recipients[index], props.projectInfo);
        return <div className="bulk-adjust-group">
            <div className="bulk-adjust-workspace">
                <span className="bulk-adjust-workspace-title">{title}</span>
                <span className="bulk-adjust-workspace-meta">{pi === null ? "Personal" : `PI ${pi}`}</span>
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
    const renderSliceValues = (row: WorkspaceRow): React.ReactNode => {
        if (row.category.accountingModel !== "CONSUMPTION") return <div className="bulk-slice-values">
            <span className="bulk-slice-empty">Not split</span>
        </div>;
        const amount = perSliceAmount(row);
        if (amount === null) return <div className="bulk-slice-values">
            <span className="bulk-slice-empty">—</span>
        </div>;
        return <div className="bulk-slice-values">
            <span>{number(amount)} <span className="bulk-slice-unit">
                {allocationInputUnit(row.category).name}
            </span></span>
        </div>;
    };
    const renderAdjust = (context: UcxTableCellRenderContext): React.ReactNode => {
        const row = workspaceRowByKey.get(context.row.key);
        if (!row) return context.cell;
        const unit = allocationInputUnit(row.category).name;
        const title = workspaceGroupTitle(row.recipientIndex);
        const modeLabel = row.mode === "percent" ? "Percentage" : "Amount";
        if (context.column.key === "product") return <div className="bulk-product">
            <ProviderLogo providerId={row.category.provider} size={16} />
            <span>{productCategoryDisplayName(row.category)} ({getProviderTitle(row.category.provider)})</span>
        </div>;
        if (context.column.key === "slices") return renderSliceValues(row);
        if (context.column.key === "adjusted") return <div className="bulk-adjust-value">
            <div
                className="bulk-adjust-control"
                data-bulk-adjust-control=""
                data-bulk-invalid={row.invalid ? "true" : undefined}
            >
                <Select
                    width="88px"
                    flexShrink={0}
                    value={row.mode}
                    aria-label={`Amount mode for ${productCategoryDisplayName(row.category)} in ${title}`}
                    onChange={event => changeRowMode(row, event.target.value as AdjustMode)}
                >
                    <option value="inherit">Inherit</option>
                    <option value="percent">%</option>
                    <option value="absolute">Value</option>
                </Select>
                {row.mode === "inherit" ? <>
                    <Input
                        type="number"
                        min={0}
                        step="any"
                        value={editValue(row.adjusted)}
                        aria-invalid={row.invalid}
                        aria-label={`Inherited amount for ${productCategoryDisplayName(row.category)} in ${title}`}
                        onChange={event => setRowOverride(row.key, "absolute", event.target.value)}
                    />
                    <span className="bulk-adjust-suffix">{unit}</span>
                </> : <>
                    <Input
                        type="number"
                        min={0}
                        step="any"
                        value={row.overrideValue}
                        aria-invalid={row.invalid}
                        aria-label={`${modeLabel} for ${productCategoryDisplayName(row.category)} in ${title}`}
                        onChange={event => setRowOverride(
                            row.key,
                            row.mode === "percent" ? "percent" : "absolute",
                            event.target.value,
                        )}
                    />
                    <span className="bulk-adjust-suffix">{row.mode === "percent" ? "%" : unit}</span>
                </>}
            </div>
        </div>;
        return <Amount value={Number(context.cell)} unit={unit} />;
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
        const unit = allocationInputUnit(row.category).name;
        if (context.column.key === "product") return <div className="bulk-product">
            <ProviderLogo providerId={row.category.provider} size={16} />
            <span>{productCategoryDisplayName(row.category)} ({getProviderTitle(row.category.provider)})</span>
        </div>;
        if (context.column.key === "slices") return renderSliceValues(row);
        return <Amount value={Number(context.cell)} unit={unit} />;
    };

    return <ReactModal
        isOpen
        ariaHideApp={false}
        contentLabel="Bulk allocation"
        shouldCloseOnOverlayClick={!busy}
        onAfterOpen={focusCurrentStep}
        onRequestClose={close}
        shouldCloseOnEsc={false}
        className={CardClass}
        style={{
            ...largeModalStyle,
            content: {
                ...largeModalStyle.content,
                width: "1100px",
                padding: 0,
                overflow: "hidden",
            },
        }}
        contentElement={(modalProps, children) => <div {...modalProps} aria-modal="true" onKeyDownCapture={event => {
            if (event.key === "Escape") {
                const target = event.target as HTMLElement;
                if (target.closest("[data-tag='dropdown'][aria-expanded='true']")) return;
                if (target.closest("[data-recipient-draft]")) return;
                if (target.closest("[data-bulk-table-navigation]") && target.closest("input, select, textarea")) return;
                if (target.closest("[data-recipient-search]")) {
                    event.preventDefault();
                    event.stopPropagation();
                    const available = contentRef.current?.querySelector<HTMLElement>(
                        "[data-recipient-table='available'] [data-ucx-table]"
                    );
                    available?.focus({preventScroll: true});
                    return;
                }
                event.preventDefault();
                event.stopPropagation();
                cancelButtonRef.current?.focus({preventScroll: true});
                return;
            }
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
            if (!event.altKey || !primaryPressed || event.shiftKey || event.nativeEvent.isComposing) return;
            if (event.code !== backCode && event.code !== nextCode) return;
            event.preventDefault();
            event.stopPropagation();
            if (event.repeat || submitting.current) return;
            if (event.code === backCode && canGoBack && previousStep !== null) move(previousStep);
            if (event.code === nextCode && step !== lastStep) primaryAction();
        }}>{children}</div>}
    >
        <KeyboardNavigation className={BulkAllocationStyle}>
            <header>
                <h3>Allocate resources</h3>
                <nav className="bulk-steps" aria-label="Allocation steps">
                    {names.map((name, index) => <span
                        key={name}
                        aria-current={index === stepIndex ? "step" : undefined}
                    >
                        {index + 1}. {name}
                    </span>)}
                </nav>
            </header>
            <div className="bulk-content" data-step={step} ref={contentRef} tabIndex={-1}>
                {step === -1 ? <RecipientPicker
                    projectId={props.projectId}
                    recipients={recipients}
                    existing={props.availableRecipients}
                    projectInfo={props.projectInfo}
                    canCreateSubAllocator={props.canCreateSubAllocator}
                    onDraftPendingChange={setDraftPending}
                    onChange={next => {
                        setRecipients(next);
                        setWorkspacePercents({});
                        setRowOverrides({});
                    }}
                /> : null}
                {step === 0 ? <>
                    <FieldGroup>
                        <FieldRow
                            title="Period"
                            description="All selected workspaces share this period."
                            required
                            control={<PeriodFilter
                                value={period}
                                onChange={setPeriod}
                                navigationField
                                presetButtonColor="secondaryMain"
                            />}
                        />
                        <FieldRow
                            title="Slices"
                            description={sliceDescription}
                            control={<div className="bulk-split">
                                <div className="bulk-split-toggle">
                                    <Toggle
                                        height={20}
                                        checked={splitEnabled}
                                        onChange={() => setSplitEnabled(previous => !previous)}
                                        ariaLabel="Split into slices"
                                    />
                                    <div>
                                        <strong>Divide quota into calendar slices</strong>
                                        <span>
                                            This will cause consumption based allocations (e.g. core-hours) to be
                                            split into multiple allocations ("slices").
                                        </span>
                                    </div>
                                </div>
                                <div className="bulk-split-length" data-bulk-split-length>
                                    {!splitEnabled ? null :
                                        <div className="bulk-split-length-control">
                                            <span>Every</span>
                                            <Input
                                                type="number"
                                                min={1}
                                                step={1}
                                                value={splitMonths}
                                                aria-label="Months per slice"
                                                onChange={event => setSplitMonths(event.target.value)}
                                            />
                                            <span>months</span>
                                        </div>
                                    }
                                </div>
                            </div>}
                        />
                    </FieldGroup>
                    {!periodValid ? <p className="bulk-error" role="alert">Choose a valid allocation period.</p> : null}
                </> : null}
                {step === 1 ? <>
                    <BulkTable
                        id="bulk-allocation-products"
                        variant="products"
                        columns={productColumns}
                        rows={productRows}
                        renderCell={renderProduct}
                        renderGroupHeader={renderProviderGroupHeader}
                        editable
                        onNavigateEnd={() => primaryButtonRef.current?.focus({preventScroll: true})}
                    />
                    {invalidQuota ?
                        <p className="bulk-error" role="alert">
                            Enter a valid quota of zero or more. Positive quotas must reach
                            at least one accounting unit.
                        </p> :
                        null}
                    {categories.length === 0 ? <p>No products are available for allocation.</p> : null}
                </> : null}
                {step === 2 ? <>
                    <p>
                        Adjust each workspace. The shared percentage applies to every product;
                        individual rows can override it.
                    </p>
                    <BulkTable
                        id="bulk-allocation-adjust"
                        variant="adjust"
                        columns={adjustColumns}
                        rows={adjustTableRows}
                        renderCell={renderAdjust}
                        renderGroupHeader={renderAdjustGroupHeader}
                        editable
                        hasSlices={hasSplitSchedule}
                        onNavigateEnd={() => primaryButtonRef.current?.focus({preventScroll: true})}
                    />
                    {adjustmentInvalid ?
                        <p className="bulk-error" role="alert">
                            Enter a valid percentage or amount for every adjusted value.
                        </p> :
                        null}
                    {allocationWorkspaces.length === 0 ?
                        <p className="bulk-error" role="alert">
                            At least one workspace must receive a positive amount.
                        </p> :
                        null}
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
                                <span className="bulk-review-date-value">
                                    {bounds.start !== null ? format(bounds.start, "d MMM yyyy") : "-"}
                                </span>
                                <span className="bulk-review-date-time">
                                    {bounds.start !== null ? format(bounds.start, "HH:mm") : ""}
                                </span>
                            </div>
                            <Icon name="heroArrowRight" size={14} />
                            <div className="bulk-review-date">
                                <span className="bulk-review-date-value">
                                    {bounds.end !== null ? format(bounds.end, "d MMM yyyy") : "-"}
                                </span>
                                <span className="bulk-review-date-time">
                                    {bounds.end !== null ? format(bounds.end, "HH:mm") : ""}
                                </span>
                            </div>
                            {hasSplitSchedule ? <span className="bulk-review-slices">
                                ({number(schedule.length)} slices, {sliceCadence})
                            </span> : null}
                        </div>
                    </section>
                    <section className="bulk-review-section">
                        <span className="bulk-review-label">Reason</span>
                        <div className="bulk-review-reason">
                            <MarkdownDocument text={reasonBlockquote(reason)} />
                        </div>
                    </section>
                    <section className="bulk-review-section bulk-review-products">
                        <span className="bulk-review-label">Products</span>
                        <BulkTable
                            id="bulk-allocation-products"
                            variant="products"
                            columns={productColumns}
                            rows={productRows}
                            renderCell={renderProduct}
                            renderGroupHeader={renderProviderGroupHeader}
                        />
                    </section>
                    <section className="bulk-review-section bulk-review-totals">
                        <span className="bulk-review-label">Workspace totals</span>
                        <BulkTable
                            id="bulk-allocation-review"
                            variant="review"
                            columns={reviewColumns}
                            rows={reviewTableRows}
                            renderGroupHeader={renderReviewGroupHeader}
                            renderCell={renderReview}
                            hasSlices={hasSplitSchedule}
                        />
                    </section>
                    {completed > 0 ?
                        <p>
                            {number(completed)} of {number(allocationWorkspaces.length)} workspaces completed.
                        </p> :
                        null}
                    {error ? <p className="bulk-error" role="alert">{error}</p> : null}
                </> : null}
            </div>
            <footer className="bulk-actions">
                <div className="bulk-progress">
                    <TutorialWizardProgress step={stepIndex} stepCount={stepOrder.length} />
                </div>
                <Button color="errorMain" disabled={busy} btnRef={cancelButtonRef} onClick={close}>Cancel</Button>
                <Button color="secondaryMain" ariaLabel="Back" disabled={!canGoBack}
                    ariaKeyshortcuts={`Control+Alt+${backKey} Meta+Alt+${backKey}`}
                    onClick={() => {
                        if (previousStep !== null) move(previousStep);
                    }}>
                    Back<SubmitShortcut shortcut={backKey} />
                </Button>
                <TooltipV2 tooltip={nextDisabledReason} contentWidth={240}>
                    <Button
                        color="successMain"
                        ariaLabel={step < lastStep ? "Next" : undefined}
                        ariaDisabled={!canGoNext || busy}
                        ariaKeyshortcuts={step < lastStep
                            ? `Control+Alt+${nextKey} Meta+Alt+${nextKey}`
                            : "Control+Alt+Enter Meta+Alt+Enter"}
                        disabled={busy}
                        btnRef={primaryButtonRef}
                        onClick={primaryAction}
                    >
                        {busy
                            ? `Allocating ${number(completed)} / ${number(allocationWorkspaces.length)}`
                            : step === lastStep ? "Confirm" : "Next"}
                        {step < lastStep ? <SubmitShortcut shortcut={nextKey} /> : <SubmitShortcut />}
                    </Button>
                </TooltipV2>
            </footer>
        </KeyboardNavigation>
    </ReactModal>;
}
