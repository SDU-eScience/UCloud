import * as React from "react";
import {useEffect, useId, useLayoutEffect, useMemo, useRef, useState} from "react";
import {retrieveAllocationRecipients} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import type {ProjectInfo} from "@/Project/InfoCache";
import {Button, Checkbox, Icon, Input, Label} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {UcxStreamedTable, UcxTableIconButtonClass, UcxTableStore} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {doNothing} from "@/UtilityFunctions";
import {AllocationWorkspaceLabel} from "./AllocationCells";
import {allocationWorkspaceInfo} from "./AllocationModel";
import type {AllocationModelRecipient} from "./AllocationModel";

export type BulkAllocationRecipient = AllocationModelRecipient & {
    newProject?: {title: string; subAllocator: boolean};
};

interface BulkAllocationRecipientSearch {
    items: (ProjectInfo & {type: "project" | "user"})[];
    canSearchCatalog: boolean;
}

const BulkAllocationRecipientsStyle = injectStyle("bulk-allocation-recipients", k => `
    ${k} { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: minmax(0, 1fr); flex: 1 1 0; min-height: 0; min-width: 0; gap: 24px; }
    ${k} > section { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
    ${k} .recipient-heading { display: flex; justify-content: space-between; align-items: center; flex: 0 0 40px; gap: 8px; margin-bottom: 8px; }
    ${k} .recipient-heading h4 { margin: 0; font-size: 16px; line-height: 1.2; }
    ${k} .recipient-heading button { flex-shrink: 0; }
    ${k} > section > input { flex-shrink: 0; min-width: 0; width: 100%; }
    ${k} .recipient-table { display: flex; flex: 1 1 0; min-height: 0; min-width: 0; }
    ${k} .recipient-table .streamed-table-frame { min-height: 0; }
    ${k} .recipient-action { display: flex; justify-content: center; }
    ${k} .recipient-draft { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    ${k} .recipient-draft-title { display: flex; align-items: center; gap: 4px; }
    ${k} .recipient-draft-title > input { flex: 1; }
    ${k} .recipient-draft input { min-width: 0; }
    ${k} .recipient-draft label { display: flex; align-items: center; gap: 4px; font-size: 12px; white-space: normal; }
    ${k} .recipient-draft [role="alert"] { margin: 0; color: var(--errorMain); font-size: 12px; white-space: normal; }
    @media (max-width: 720px) {
        ${k} { grid-template-columns: minmax(0, 1fr); grid-template-rows: repeat(2, minmax(0, 1fr)); gap: 16px; }
    }
`);

function bulkAllocationRecipientKey(recipient: AllocationModelRecipient): string {
    const ref = recipient.owner.reference;
    return ref.type === "project" ? `project:${ref.projectId}` : `user:${ref.username}`;
}

const recipientColumns: TableColumn[] = [
    {key: "recipient.workspace", label: "Workspace", jsonPath: "workspace", sortType: TableColumnSortType.Text, alignment: "left"},
    {key: "recipient.action", label: "Action", jsonPath: "action", alignment: "center"},
];
const recipientColumnWidths = {"recipient.workspace": 260, "recipient.action": 60};
const recipientDraftRowKey = "new-sub-project";

function BulkAllocationRecipientTable(props: {
    available?: boolean;
    recipients: BulkAllocationRecipient[];
    projectInfo: Record<string, ProjectInfo | null>;
    emptyMessage: string;
    onAction: (recipient: BulkAllocationRecipient) => void;
    draft?: {content: React.ReactNode; formId: string; canSubmit: boolean};
}): React.ReactNode {
    const tableId = props.available ? "bulk-allocation-available-recipients" : "bulk-allocation-selected-recipients";
    const store = useMemo(() => new UcxTableStore(), []);
    const entries = useMemo(() => new Map(props.recipients.map(recipient => [
        bulkAllocationRecipientKey(recipient),
        {recipient, workspace: allocationWorkspaceInfo(recipient.owner, props.projectInfo)},
    ])), [props.projectInfo, props.recipients]);
    const hasDraft = props.draft !== undefined;
    useLayoutEffect(() => {
        const rows: TableRow[] = [...entries].map(([key, {workspace}]) => ({key, group: "", cells: [workspace.title, ""]}));
        if (hasDraft) rows.unshift({key: recipientDraftRowKey, group: "", cells: ["", ""]});
        store.apply({
            tableId,
            revision: 0,
            snapshot: true,
            columns: recipientColumns,
            upserts: rows,
            removed: [],
        });
    }, [entries, hasDraft, store, tableId]);

    return <div className="recipient-table">
        <UcxStreamedTable
            tableId={tableId}
            store={store}
            showControls={false}
            showGroupHeaders={false}
            preserveRowOrder
            sorted={!hasDraft}
            density="compact"
            rowHeight={45}
            columnWidths={recipientColumnWidths}
            fitToWidth
            emptyMessage={props.emptyMessage}
            onRowActivated={doNothing}
            renderCell={({row, column}) => {
                if (row.key === recipientDraftRowKey && props.draft) {
                    if (column.key !== "recipient.action") return props.draft.content;
                    return <div className="recipient-action" onClick={event => event.stopPropagation()} onKeyDown={event => {
                        if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                    }}>
                        <TooltipV2 tooltip="Add sub-project"><button type="submit" form={props.draft.formId}
                            className={UcxTableIconButtonClass} aria-label="Add sub-project" disabled={!props.draft.canSubmit}>
                            <Icon name="heroPlus" size={14} />
                        </button></TooltipV2>
                    </div>;
                }
                const entry = entries.get(row.key);
                if (!entry) return null;
                const {recipient, workspace} = entry;
                const action = props.available ? "Add" : "Remove";
                if (column.key === "recipient.action") return <div className="recipient-action" onClick={event => event.stopPropagation()} onKeyDown={event => {
                    if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                }}>
                    <TooltipV2 tooltip={action}><button type="button" className={UcxTableIconButtonClass}
                        aria-label={`${action} ${workspace.title}`} onClick={() => props.onAction(recipient)}>
                        <Icon name={props.available ? "heroPlus" : "heroMinus"} size={14} />
                    </button></TooltipV2>
                </div>;
                const meta = recipient.newProject ? (recipient.newProject.subAllocator ? "Draft sub-allocator" : "Draft sub-project") :
                    workspace.type === "user" ? "Personal" : workspace.pi ? `PI ${workspace.pi}` : undefined;
                const metaTooltip = workspace.type === "user" ? recipient.owner.title : recipient.newProject ? undefined : workspace.pi;
                return <AllocationWorkspaceLabel title={workspace.title} meta={meta} metaTooltip={metaTooltip} />;
            }}
        />
    </div>;
}

export function BulkAllocationRecipients(props: {
    projectId: string;
    recipients: BulkAllocationRecipient[];
    existing: AllocationModelRecipient[];
    projectInfo: Record<string, ProjectInfo | null>;
    canCreateSubAllocator: boolean;
    demo?: boolean;
    onDraftPendingChange: (pending: boolean) => void;
    onChange: (recipients: BulkAllocationRecipient[]) => void;
}): React.ReactNode {
    const [result, setResult] = useState<BulkAllocationRecipientSearch>();
    const [searchStatus, setSearchStatus] = useState<"loading" | "ready" | "failed">("loading");
    const [retry, setRetry] = useState(0);
    const [query, setQuery] = useState("");
    const [showDraftForm, setShowDraftForm] = useState(false);
    const [draftTitle, setDraftTitle] = useState("");
    const [draftSubAllocator, setDraftSubAllocator] = useState(false);
    const draftInput = useRef<HTMLInputElement>(null);
    const newSubProjectButton = useRef<HTMLButtonElement>(null);
    const selectedSection = useRef<HTMLElement>(null);
    const draftFormId = useId();
    const search = query.trim();
    useEffect(() => {
        if (props.demo) return;
        let current = true;
        setSearchStatus("loading");
        const timer = window.setTimeout(() => {
            void callAPI<BulkAllocationRecipientSearch>({...retrieveAllocationRecipients(search), projectOverride: props.projectId}).then(result => {
                if (!current) return;
                setResult(result);
                setSearchStatus("ready");
            }, () => {
                if (current) setSearchStatus("failed");
            });
        }, search === "" ? 0 : 250);
        return () => {
            current = false;
            window.clearTimeout(timer);
        };
    }, [props.demo, props.projectId, retry, search]);
    useLayoutEffect(() => {
        if (!showDraftForm) return;
        selectedSection.current?.querySelector(".streamed-table-scroll")?.scrollTo({top: 0});
        draftInput.current?.focus();
    }, [showDraftForm]);

    const existing = new Map(props.existing.map(recipient => [bulkAllocationRecipientKey(recipient), recipient]));
    const selected = new Set(props.recipients.map(bulkAllocationRecipientKey));
    const available: BulkAllocationRecipient[] = props.demo ? props.existing.filter(recipient => recipient.owner.reference.type === "project") :
        (result?.items ?? []).map(item => ({
            owner: {
                reference: item.type === "project" ? {type: "project", projectId: item.id} : {type: "user", username: item.id},
                title: item.title,
                primaryUsername: item.piUsername,
            },
            products: existing.get(`${item.type}:${item.id}`)?.products ?? [],
        }));
    const addable = available.filter(recipient => {
        if (selected.has(bulkAllocationRecipientKey(recipient))) return false;
        if (!props.demo) return true;
        const info = allocationWorkspaceInfo(recipient.owner, props.projectInfo);
        return `${info.title} ${info.pi}`.toLowerCase().includes(search.toLowerCase());
    });
    const title = draftTitle.trim();
    const validTitle = title.length > 0 && !title.startsWith("%");
    const closeDraft = () => {
        setShowDraftForm(false);
        setDraftTitle("");
        setDraftSubAllocator(false);
        props.onDraftPendingChange(false);
        requestAnimationFrame(() => newSubProjectButton.current?.focus());
    };

    const draftContent = <form id={draftFormId} className="recipient-draft" onClick={event => event.stopPropagation()} onKeyDown={event => {
        event.stopPropagation();
        if (event.key === "Escape") {
            event.preventDefault();
            closeDraft();
        }
    }} onSubmit={event => {
        event.preventDefault();
        event.stopPropagation();
        if (!validTitle) return;
        props.onChange([...props.recipients, {
            owner: {
                reference: {type: "project", projectId: `draft:${crypto.randomUUID()}`},
                title,
                primaryUsername: Client.username ?? "",
            },
            products: [],
            newProject: {title, subAllocator: props.canCreateSubAllocator && draftSubAllocator},
        }]);
        closeDraft();
    }}>
        <div className="recipient-draft-title">
            <Input inputRef={draftInput} height={28} aria-label="New sub-project title" placeholder="Project title" autoFocus
                aria-invalid={title.startsWith("%")} value={draftTitle} onChange={event => {
                    const value = event.currentTarget.value;
                    setDraftTitle(value);
                    props.onDraftPendingChange(value.trim() !== "" || draftSubAllocator);
                }} required />
            <TooltipV2 tooltip="Cancel"><button type="button" className={UcxTableIconButtonClass} aria-label="Cancel new sub-project" onClick={closeDraft}>
                <Icon name="heroXMark" size={14} />
            </button></TooltipV2>
        </div>
        {title.startsWith("%") ? <p role="alert">The title cannot start with %.</p> : null}
        {props.canCreateSubAllocator ? <Label>
            <Checkbox checked={draftSubAllocator} onChange={() => {
                setDraftSubAllocator(!draftSubAllocator);
                props.onDraftPendingChange(title !== "" || !draftSubAllocator);
            }} />
            Sub-allocator
        </Label> : null}
    </form>;

    return <div className={BulkAllocationRecipientsStyle}>
        <section ref={selectedSection}>
            <div className="recipient-heading">
                <h4>Selected recipients ({props.recipients.length})</h4>
                {!showDraftForm ? <Button btnRef={newSubProjectButton} type="button" color="secondaryMain" height={32}
                    onClick={() => setShowDraftForm(true)}>New sub-project</Button> : null}
            </div>
            <BulkAllocationRecipientTable
                recipients={props.recipients}
                projectInfo={props.projectInfo}
                emptyMessage="No recipients selected."
                draft={showDraftForm ? {content: draftContent, formId: draftFormId, canSubmit: validTitle} : undefined}
                onAction={recipient => props.onChange(props.recipients.filter(current => current !== recipient))}
            />
        </section>
        <section>
            <div className="recipient-heading"><h4>Available recipients</h4></div>
            <Input value={query} maxLength={64} onChange={event => setQuery(event.currentTarget.value)} aria-label="Search available recipients" data-recipient-search=""
                placeholder={result?.canSearchCatalog ? "Search projects or people…" : "Search projects…"} mb="8px" />
            {!props.demo && searchStatus === "failed" ? <div role="alert">Could not search for recipients. <Button color="secondaryMain" onClick={() => setRetry(value => value + 1)}>Retry</Button></div> :
                <BulkAllocationRecipientTable
                    available
                    recipients={props.demo || searchStatus === "ready" ? addable : []}
                    projectInfo={props.projectInfo}
                    emptyMessage={!props.demo && searchStatus === "loading" ? "Searching…" :
                        search.length >= 2 ? "No available recipients match." :
                            result?.canSearchCatalog ? "No local results. Search all projects and personal workspaces." : "No local results."}
                    onAction={recipient => props.onChange([...props.recipients, recipient])}
                />}
        </section>
    </div>;
}
