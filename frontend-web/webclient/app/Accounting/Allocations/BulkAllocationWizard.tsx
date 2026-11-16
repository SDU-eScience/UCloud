import * as React from "react";
import {useLayoutEffect, useMemo, useRef, useState} from "react";
import ReactModal from "react-modal";
import {format} from "date-fns";
import {allocationInputToRaw, allocationInputUnit, allocationInputValue, productTypeToIcon} from "@/Accounting";
import type {AllocationDisplayTreeRecipient, ProductCategoryV2} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
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
import {TutorialWizardProgress} from "@/ui-components/TutorialWizard";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {doNothing, extractErrorMessage} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {AllocationAmount} from "./AllocationCells";
import {allocationDateRangeBounds, allocationOverlapsRange} from "./AllocationGrid";
import type {AllocationDateRange} from "./AllocationGrid";
import {allocationCategoryKey} from "./AllocationOverview";
import {allocationCompareProducts} from "./AllocationPresentation";
import {AllocationPeriodFilter} from "./AllocationPeriodFilter";

const bulkAllocationProductColumns: TableColumn[] = [
    {key: "product", label: "Product", jsonPath: "product", sortType: TableColumnSortType.Text},
    {key: "quota", label: "Quota per recipient", jsonPath: "quota", sortType: TableColumnSortType.Number},
    {key: "total", label: "Batch total", jsonPath: "total", sortType: TableColumnSortType.Number},
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
    ${k} header p { margin-bottom: 0; }
    ${k} .bulk-content { flex: 1; min-height: 0; min-width: 0; padding: 24px; overflow: auto; }
    ${k} .bulk-steps { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 16px; color: var(--textSecondary); font-size: 12px; }
    ${k} .bulk-steps [aria-current="step"] { color: var(--primaryMain); font-weight: 600; }
    ${k} .bulk-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 12px 24px; background: var(--dialogToolbar); }
    ${k} .bulk-actions .bulk-progress { flex: 1; min-width: 0; }
    ${k} .bulk-table { display: flex; flex-direction: column; min-width: 0; margin: 12px 0 20px; }
    ${k} .bulk-table .streamed-table-frame { min-height: 0; }
    ${k} .bulk-product, ${k} .bulk-provider, ${k} .bulk-quota { display: flex; align-items: center; gap: 8px; min-width: 0; }
    ${k} .bulk-product span { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    ${k} .bulk-product svg, ${k} .bulk-provider svg { flex-shrink: 0; }
    ${k} .bulk-quota input { width: 120px; min-width: 0; height: 32px; }
    ${k} .bulk-quota span { color: var(--textSecondary); font-size: 12px; white-space: normal; }
    ${k} .bulk-reason { display: flex; flex-direction: column; gap: 8px; }
    ${k} textarea { box-sizing: border-box; width: 100%; min-height: 180px; padding: 12px; border: 1px solid var(--borderColor); border-radius: 8px; background: var(--backgroundDefault); color: var(--textPrimary); font: inherit; resize: vertical; }
    ${k} .bulk-review-section { margin-bottom: 24px; }
    ${k} .bulk-review-reason { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--textPrimary); }
    ${k} .bulk-error { margin-top: 16px; color: var(--errorMain); overflow-wrap: anywhere; }
    ${k} .bulk-note { font-size: 12px; }
    ${k} .bulk-table thead th:not(:first-child) { text-align: right; }
    ${k} .bulk-table[data-editable="true"] thead th:nth-child(2) { text-align: left; }
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

function BulkAllocationTable(props: {
    id: string;
    columns: TableColumn[];
    rows: TableRow[];
    renderCell: (context: UcxTableCellRenderContext) => React.ReactNode;
    renderGroupHeader: (group: string) => React.ReactNode;
    editable?: boolean;
    height: number;
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

    return <div className="bulk-table" data-editable={props.editable} style={{height: props.height}}>
        <UcxStreamedTable
            tableId={props.id}
            store={store}
            preserveRowOrder
            showControls={false}
            density="compact"
            rowHeight={45}
            fitToWidth
            columnWidths={props.columns === bulkAllocationProductColumns
                ? {product: 300, quota: 260, total: 160}
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
    const [quotas, setQuotas] = useState<Record<string, string>>({});
    const [reason, setReason] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [completed, setCompleted] = useState(0);
    const submitting = useRef(false);
    const completedRef = useRef(0);
    const contentRef = useRef<HTMLDivElement>(null);
    const categories = useMemo(() => [...props.categories].sort(allocationCompareProducts), [props.categories]);
    const categoryByKey = useMemo(() => new Map(categories.map(category => [allocationCategoryKey(category), category])), [categories]);
    const rawQuota = (category: ProductCategoryV2) => allocationInputToRaw(category, Number(quotas[allocationCategoryKey(category)] ?? 0));
    const chosen = categories.filter(category => Number(quotas[allocationCategoryKey(category)]) > 0);
    const invalidQuota = categories.some(category => {
        const value = quotas[allocationCategoryKey(category)] ?? "";
        return value !== "" && (!Number.isFinite(Number(value)) || Number(value) < 0 || (Number(value) > 0 && rawQuota(category) <= 0));
    });
    const bounds = allocationDateRangeBounds(period);
    const periodValid = bounds.start !== null && bounds.end !== null
        && Number.isFinite(bounds.start) && Number.isFinite(bounds.end) && bounds.end >= bounds.start;
    const locked = completed > 0;
    const names = ["Period", "Products", "Reason", "Review"];
    const number = (value: number) => formatNumber(value, {precision: 2, removeTrailingZeros: true});
    const productRows: TableRow[] = (step === 3 ? chosen : categories).map(category => {
        const key = allocationCategoryKey(category);
        const quota = allocationInputValue(category, rawQuota(category));
        return {key, group: category.provider, cells: [category.name, `${quota}`, `${quota * props.recipients.length}`]};
    });

    const reviewRows: TableRow[] = [];
    const reviewByKey = new Map<string, {category: ProductCategoryV2; current: number; added: number}>();
    props.recipients.forEach((recipient, index) => {
        for (const category of chosen) {
            const key = `${index}/${allocationCategoryKey(category)}`;
            const current = recipient.groups
                .filter(group => allocationCategoryKey(group.category) === allocationCategoryKey(category))
                .flatMap(group => group.allocations)
                .filter(allocation => allocationOverlapsRange(allocation.start, allocation.end, bounds))
                .reduce((sum, allocation) => sum + allocation.quota, 0);
            const added = rawQuota(category);
            reviewByKey.set(key, {category, current, added});
            reviewRows.push({key, group: `${index}`, cells: [
                category.name,
                `${current}`,
                `${added}`,
                `${current + added}`,
            ]});
        }
    });

    const close = () => {
        if (!submitting.current) props.onClose(completedRef.current > 0);
    };
    const move = (next: number) => {
        setError("");
        setStep(next);
        contentRef.current?.scrollTo({top: 0});
    };
    const confirm = async () => {
        if (submitting.current || !periodValid || invalidQuota || chosen.length === 0 || !reason.trim()) return;
        if (props.demo) {
            sendInformationNotification("Demo data: no allocations were created.");
            return;
        }
        submitting.current = true;
        setBusy(true);
        setError("");
        try {
            for (let index = completedRef.current; index < props.recipients.length; index++) {
                const owner = props.recipients[index].owner.reference;
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
                            allocationRequests: chosen.map(category => ({
                                category: category.name,
                                provider: category.provider,
                                grantGiver: props.projectId,
                                balanceRequested: rawQuota(category),
                                period: allocationPeriod,
                            })),
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
            sendSuccessNotification(`Allocations created for ${number(props.recipients.length)} workspaces.`);
            props.onClose(true);
        } catch (failure) {
            const recipient = props.recipients[completedRef.current];
            const title = bulkAllocationWorkspaceTitle(recipient, props.projectInfo);
            setError(`Could not allocate to ${title}: ${extractErrorMessage(failure)}. ${number(completedRef.current)} of ${number(props.recipients.length)} workspaces completed. Confirm retries only the remaining workspaces.`);
        } finally {
            submitting.current = false;
            setBusy(false);
        }
    };

    const renderProduct = (context: UcxTableCellRenderContext): React.ReactNode => {
        const category = categoryByKey.get(context.row.key)!;
        if (context.column.key === "product") return <div className="bulk-product">
            <Icon name={productTypeToIcon(category.productType)} size={16} /><span>{category.name}</span>
        </div>;
        if (context.column.key === "quota" && step === 1) return <div className="bulk-quota" onKeyDown={event => event.stopPropagation()}>
            <Input
                type="number"
                min={0}
                step="any"
                value={quotas[context.row.key] ?? ""}
                placeholder="0"
                aria-label={`Quota per recipient for ${category.name} (${getProviderTitle(category.provider)})`}
                onChange={event => setQuotas(previous => ({...previous, [context.row.key]: event.target.value}))}
            />
            <span>{bulkAllocationUnit(category)}</span>
        </div>;
        return <AllocationAmount value={Number(context.cell)} unit={bulkAllocationUnit(category)} />;
    };

    return <ReactModal
        isOpen
        ariaHideApp={false}
        contentLabel="Bulk allocation"
        shouldCloseOnEsc={!busy}
        shouldCloseOnOverlayClick={!busy}
        onRequestClose={close}
        className={CardClass}
        style={{...largeModalStyle, content: {...largeModalStyle.content, width: "960px", padding: 0, overflow: "hidden"}}}
    >
        <div className={BulkAllocationWizardStyle}>
            <header>
                <h3>Allocate resources</h3>
                <p>{number(props.recipients.length)} workspace{props.recipients.length === 1 ? "" : "s"} selected</p>
                <nav className="bulk-steps" aria-label="Allocation steps">
                    {names.map((name, index) => <span key={name} aria-current={index === step ? "step" : undefined}>{index + 1}. {name}</span>)}
                </nav>
            </header>
            <div className="bulk-content" ref={contentRef}>
                {step === 0 ? <>
                    <h4>Allocation period</h4>
                    <p>Choose the period for the new allocations. All selected workspaces receive the same period.</p>
                    <AllocationPeriodFilter value={period} onChange={setPeriod} />
                    <p style={{marginTop: 20}}>{format(period.start, "MMMM yyyy")} through {format(period.end, "MMMM yyyy")}</p>
                    {!periodValid ? <p className="bulk-error">Choose a valid allocation period.</p> : null}
                </> : null}
                {step === 1 ? <>
                    <h4>Products and quota</h4>
                    <p>Enter a quota for each product you want to allocate. Leave other products empty.</p>
                </> : null}
                {step === 1 || step === 3 ? <>
                    {step === 3 ? <>
                        <h4>Review allocations</h4>
                        <p>{format(period.start, "MMMM yyyy")} through {format(period.end, "MMMM yyyy")} · {number(props.recipients.length)} workspace{props.recipients.length === 1 ? "" : "s"}</p>
                    </> : null}
                    <BulkAllocationTable
                        id="bulk-allocation-products"
                        columns={bulkAllocationProductColumns}
                        rows={productRows}
                        renderCell={renderProduct}
                        renderGroupHeader={provider => <span className="bulk-provider"><ProviderLogo providerId={provider} size={18} />{getProviderTitle(provider)}</span>}
                        editable={step === 1}
                        height={Math.min(410, Math.max(160, (productRows.length + new Set(productRows.map(row => row.group)).size) * 45 + 42))}
                    />
                    {invalidQuota ? <p className="bulk-error">Enter a valid quota of zero or more. Positive quotas must reach at least one accounting unit.</p> : null}
                    {categories.length === 0 ? <p>No products are available for allocation.</p> : null}
                </> : null}
                {step === 2 ? <label className="bulk-reason">
                    <h4>Sub-allocation description</h4>
                    <p>Describe the reason for creating these sub-allocations.</p>
                    <textarea
                        aria-label="Reason for allocation"
                        value={reason}
                        onChange={event => setReason(event.target.value)}
                        maxLength={4000}
                        rows={6}
                        required
                        autoFocus
                    />
                    <span>{number(reason.length)} / {number(4000)}</span>
                </label> : null}
                {step === 3 ? <>
                    <section className="bulk-review-section" style={{marginTop: 24}}>
                        <h4>Reason</h4>
                        <p className="bulk-review-reason">{reason}</p>
                    </section>
                    <section className="bulk-review-section">
                        <h4>Workspace totals</h4>
                        <p className="bulk-note">Current quota includes existing allocations that overlap the chosen period. These allocations might not run at the same time.</p>
                        <BulkAllocationTable
                            id="bulk-allocation-review"
                            columns={bulkAllocationReviewColumns}
                            rows={reviewRows}
                            height={Math.min(410, Math.max(180, (reviewRows.length + props.recipients.length) * 45 + 42))}
                            renderGroupHeader={index => bulkAllocationWorkspaceTitle(props.recipients[Number(index)], props.projectInfo)}
                            renderCell={context => {
                                const row = reviewByKey.get(context.row.key)!;
                                if (context.column.key === "product") return <div className="bulk-product">
                                    <ProviderLogo providerId={row.category.provider} size={16} />
                                    <span>{row.category.name} · {getProviderTitle(row.category.provider)}</span>
                                </div>;
                                return <AllocationAmount value={allocationInputValue(row.category, Number(context.cell))} unit={bulkAllocationUnit(row.category)} />;
                            }}
                        />
                    </section>
                    {completed > 0 ? <p>{number(completed)} of {number(props.recipients.length)} workspaces completed.</p> : null}
                    {error ? <p className="bulk-error" role="alert">{error}</p> : null}
                </> : null}
            </div>
            <footer className="bulk-actions">
                <div className="bulk-progress"><TutorialWizardProgress step={step} stepCount={4} /></div>
                <Button color="errorMain" disabled={busy} onClick={close}>Cancel</Button>
                <Button color="secondaryMain" disabled={step === 0 || busy || locked} onClick={() => move(step - 1)}>Back</Button>
                <Button
                    color="successMain"
                    disabled={busy || (step === 0 && !periodValid) || (step === 1 && (invalidQuota || chosen.length === 0)) || (step === 2 && !reason.trim())}
                    onClick={() => step === 3 ? confirm() : move(step + 1)}
                >{busy ? `Allocating ${number(completed)} / ${number(props.recipients.length)}` : step === 3 ? props.demo ? "Confirm (demo)" : "Confirm" : "Next"}</Button>
            </footer>
        </div>
    </ReactModal>;
}
