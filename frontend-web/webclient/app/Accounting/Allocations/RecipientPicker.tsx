import * as React from "react";
import {useEffect, useId, useLayoutEffect, useMemo, useRef, useState} from "react";
import {retrieveAllocationRecipients} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {SubmitShortcut} from "@/Applications/KeyboardNavigation";
import type {ProjectInfo} from "@/Project/InfoCache";
import {Button, Checkbox, Icon, Input, Label} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {UcxStreamedTable, UcxTableIconButtonClass, UcxTableStore} from "@/UCX/UcxBrowser";
import type {UcxTableShortcutGuideItem} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {isLikelyMac} from "@/UtilityFunctions";
import {Search} from "./Controls";
import {WorkspaceLabel} from "./Table";
import {workspaceInfo} from "./Model";
import type {Recipient} from "./Model";

export type BulkRecipient = Recipient & {
    newProject?: {title: string; subAllocator: boolean};
};

interface RecipientSearch {
    items: (ProjectInfo & {type: "project" | "user"})[];
    canSearchCatalog: boolean;
}

const RecipientPickerStyle = injectStyle("bulk-allocation-recipients", k => `
    ${k} {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        grid-template-rows: minmax(0, 1fr);
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
        gap: 24px;
    }
    ${k} > section {
        display: flex;
        flex-direction: column;
        min-width: 0;
        min-height: 0;
    }
    ${k} .recipient-heading {
        display: flex;
        justify-content: space-between;
        align-items: center;
        flex: 0 0 40px;
        gap: 8px;
        margin-bottom: 8px;
    }
    ${k} .recipient-heading h4 {
        margin: 0;
        font-size: 16px;
        line-height: 1.2;
    }
    ${k} .recipient-heading button {
        flex-shrink: 0;
    }
    ${k} .recipient-search {
        flex-shrink: 0;
        width: 100%;
        margin-bottom: 8px;
    }
    ${k} .recipient-search .table-filter {
        width: 100%;
    }
    ${k} .recipient-table {
        display: flex;
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
    }
    ${k} .recipient-table .streamed-table-frame {
        min-height: 0;
    }
    ${k} .recipient-action {
        display: flex;
        justify-content: center;
    }
    ${k} .recipient-draft {
        display: flex;
        flex-direction: column;
        gap: 4px;
        min-width: 0;
    }
    ${k} .recipient-draft-title {
        display: flex;
        align-items: center;
        gap: 4px;
    }
    ${k} .recipient-draft-title > input {
        flex: 1;
    }
    ${k} .recipient-draft input {
        min-width: 0;
    }
    ${k} .recipient-draft label {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        white-space: normal;
    }
    ${k} .recipient-draft [role="alert"] {
        margin: 0;
        color: var(--errorMain);
        font-size: 12px;
        white-space: normal;
    }
    @media (max-width: 720px) {
        ${k} {
            grid-template-columns: minmax(0, 1fr);
            grid-template-rows: repeat(2, minmax(0, 1fr));
            gap: 16px;
        }
    }
`);

function recipientKey(recipient: Recipient): string {
    const ref = recipient.owner.reference;
    return ref.type === "project" ? `project:${ref.projectId}` : `user:${ref.username}`;
}

const recipientColumns: TableColumn[] = [
    {
        key: "recipient.workspace",
        label: "Workspace",
        jsonPath: "workspace",
        sortType: TableColumnSortType.Text,
        alignment: "left",
    },
    {key: "recipient.action", label: "Action", jsonPath: "action", alignment: "center"},
];
const recipientColumnWidths = {"recipient.workspace": 260, "recipient.action": 60};
const recipientDraftRowKey = "new-sub-project";
const recipientShortcutGuide: UcxTableShortcutGuideItem[] = [
    {keys: ["←", "→"], action: "Switch list"},
    {keys: ["↑", "↓"], action: "Move"},
    {keys: ["Enter"], action: "Add"},
    {keys: [isLikelyMac ? "⌫" : "Del"], action: "Remove"},
    {keys: ["/"], action: "Search"},
];

function RecipientTable(props: {
    pane: "selected" | "available";
    recipients: BulkRecipient[];
    projectInfo: Record<string, ProjectInfo | null>;
    emptyMessage: string;
    onAction: (recipient: BulkRecipient) => void;
    draft?: {content: React.ReactNode; formId: string; canSubmit: boolean};
}): React.ReactNode {
    const tableId = props.pane === "available"
        ? "bulk-allocation-available-recipients"
        : "bulk-allocation-selected-recipients";
    const store = useMemo(() => new UcxTableStore(), []);
    const entries = useMemo(() => new Map(props.recipients.map(recipient => [
        recipientKey(recipient),
        {recipient, workspace: workspaceInfo(recipient.owner, props.projectInfo)},
    ])), [props.projectInfo, props.recipients]);
    const hasDraft = props.draft !== undefined;
    useLayoutEffect(() => {
        const rows: TableRow[] = [...entries].map(([key, {workspace}]) => ({
            key,
            group: "",
            cells: [workspace.title, ""],
        }));
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

    return <div className="recipient-table" data-recipient-table={props.pane}>
        <UcxStreamedTable
            tableId={tableId}
            store={store}
            showControls={props.pane === "available"}
            shortcutGuide={props.pane === "available" ? recipientShortcutGuide : undefined}
            showGroupHeaders={false}
            preserveRowOrder
            sorted={!hasDraft}
            density="compact"
            rowHeight={45}
            columnWidths={recipientColumnWidths}
            fitToWidth
            emptyMessage={props.emptyMessage}
            onRowActivated={({rowKey}) => {
                if (props.pane !== "available") return;
                if (rowKey === recipientDraftRowKey) return;
                const entry = entries.get(rowKey);
                if (entry) props.onAction(entry.recipient);
            }}
            onRowDelete={props.pane === "selected" ? ({rowKey}) => {
                if (rowKey === recipientDraftRowKey) return;
                const entry = entries.get(rowKey);
                if (entry) props.onAction(entry.recipient);
            } : undefined}
            renderCell={({row, column}) => {
                if (row.key === recipientDraftRowKey && props.draft) {
                    if (column.key !== "recipient.action") return props.draft.content;
                    return <div
                        className="recipient-action"
                        onClick={event => event.stopPropagation()}
                        onKeyDown={event => {
                            if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                        }}
                    >
                        <TooltipV2 tooltip="Add sub-project">
                            <button
                                type="submit"
                                form={props.draft.formId}
                                className={UcxTableIconButtonClass}
                                aria-label="Add sub-project"
                                disabled={!props.draft.canSubmit}
                            >
                                <Icon name="heroPlus" size={14} />
                            </button>
                        </TooltipV2>
                    </div>;
                }
                const entry = entries.get(row.key);
                if (!entry) return null;
                const {recipient, workspace} = entry;
                const action = props.pane === "available" ? "Add" : "Remove";
                if (column.key === "recipient.action") {
                    return <div
                        className="recipient-action"
                        onClick={event => event.stopPropagation()}
                        onKeyDown={event => {
                            if (event.key === "Enter" || event.key === " ") event.stopPropagation();
                        }}
                    >
                        <TooltipV2 tooltip={action}>
                            <button
                                type="button"
                                className={UcxTableIconButtonClass}
                                aria-label={`${action} ${workspace.title}`}
                                onClick={() => props.onAction(recipient)}
                            >
                                <Icon name={props.pane === "available" ? "heroPlus" : "heroMinus"} size={14} />
                            </button>
                        </TooltipV2>
                    </div>;
                }
                let meta: string | undefined;
                if (recipient.newProject) {
                    meta = recipient.newProject.subAllocator ? "Draft sub-allocator" : "Draft sub-project";
                } else if (workspace.type === "user") {
                    meta = "Personal";
                } else if (workspace.pi) {
                    meta = `PI ${workspace.pi}`;
                }
                const metaTooltip = workspace.type === "user"
                    ? recipient.owner.title
                    : recipient.newProject ? undefined : workspace.pi;
                return <WorkspaceLabel title={workspace.title} meta={meta} metaTooltip={metaTooltip} />;
            }}
        />
    </div>;
}

export function RecipientPicker(props: {
    projectId: string;
    recipients: BulkRecipient[];
    existing: Recipient[];
    projectInfo: Record<string, ProjectInfo | null>;
    canCreateSubAllocator: boolean;
    onDraftPendingChange: (pending: boolean) => void;
    onChange: (recipients: BulkRecipient[]) => void;
}): React.ReactNode {
    const [result, setResult] = useState<RecipientSearch>();
    const [searchStatus, setSearchStatus] = useState<"loading" | "ready" | "failed">("loading");
    const [retry, setRetry] = useState(0);
    const [query, setQuery] = useState("");
    const [showDraftForm, setShowDraftForm] = useState(false);
    const [draftTitle, setDraftTitle] = useState("");
    const [draftSubAllocator, setDraftSubAllocator] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    const searchRef = useRef<HTMLInputElement>(null);
    const pendingFocusRef = useRef<{pane: "selected" | "available"; key?: string} | null>(null);
    const draftInput = useRef<HTMLInputElement>(null);
    const newSubProjectButton = useRef<HTMLButtonElement>(null);
    const selectedSection = useRef<HTMLElement>(null);
    const draftFormId = useId();
    const search = query.trim();
    useEffect(() => {
        let current = true;
        setSearchStatus("loading");
        const timer = window.setTimeout(() => {
            const request = {...retrieveAllocationRecipients(search), projectOverride: props.projectId};
            void callAPI<RecipientSearch>(request).then(result => {
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
    }, [props.projectId, retry, search]);
    useLayoutEffect(() => {
        if (!showDraftForm) return;
        selectedSection.current?.querySelector(".streamed-table-scroll")?.scrollTo({top: 0});
        draftInput.current?.focus();
    }, [showDraftForm]);

    const existing = new Map(props.existing.map(recipient => [recipientKey(recipient), recipient]));
    const selected = new Set(props.recipients.map(recipientKey));
    const available: BulkRecipient[] = (result?.items ?? []).map(item => ({
        owner: {
            reference: item.type === "project"
                ? {type: "project", projectId: item.id}
                : {type: "user", username: item.id},
            title: item.title,
            primaryUsername: item.piUsername,
        },
        products: existing.get(`${item.type}:${item.id}`)?.products ?? [],
    }));
    const addable = available.filter(recipient => !selected.has(recipientKey(recipient)));
    const availableEmptyMessage = searchStatus === "loading"
        ? "Searching…"
        : search.length >= 2
            ? "No available recipients match."
            : result?.canSearchCatalog
                ? "No local results. Search all projects and personal workspaces."
                : "No local results.";
    const title = draftTitle.trim();
    const validTitle = title.length > 0 && !title.startsWith("%");
    const focusPane = (pane: "selected" | "available") => {
        const root = rootRef.current;
        if (!root) return;
        if (pane === "selected" && showDraftForm) {
            draftInput.current?.focus({preventScroll: true});
            return;
        }
        const table = root.querySelector<HTMLElement>(`[data-recipient-table="${pane}"]`);
        const grid = table?.querySelector<HTMLElement>("[data-ucx-table]");
        if (grid) {
            grid.focus({preventScroll: true});
            return;
        }
        if (pane === "available") {
            searchRef.current?.focus({preventScroll: true});
        }
    };
    const addRecipient = (recipient: BulkRecipient) => {
        const index = addable.findIndex(current =>
            recipientKey(current) === recipientKey(recipient)
        );
        const next = addable[index + 1] ?? addable[index - 1];
        pendingFocusRef.current = {pane: "available", key: next ? recipientKey(next) : undefined};
        props.onChange([...props.recipients, recipient]);
    };
    const removeRecipient = (recipient: BulkRecipient) => {
        const key = recipientKey(recipient);
        const index = props.recipients.findIndex(current => recipientKey(current) === key);
        const next = props.recipients[index + 1] ?? props.recipients[index - 1];
        pendingFocusRef.current = {pane: "selected", key: next ? recipientKey(next) : undefined};
        props.onChange(props.recipients.filter(current => current !== recipient));
    };
    const closeDraft = (restoreFocus = true) => {
        setShowDraftForm(false);
        setDraftTitle("");
        setDraftSubAllocator(false);
        props.onDraftPendingChange(false);
        if (restoreFocus) requestAnimationFrame(() => newSubProjectButton.current?.focus());
    };
    useLayoutEffect(() => {
        const pending = pendingFocusRef.current;
        if (!pending) return;
        pendingFocusRef.current = null;
        const frame = requestAnimationFrame(() => {
            if (pending.key) {
                const table = rootRef.current?.querySelector<HTMLElement>(`[data-recipient-table="${pending.pane}"]`);
                const row = table?.querySelector<HTMLElement>(`tr[data-row-key="${CSS.escape(pending.key)}"]`);
                if (row) row.click();
            }
            focusPane(pending.pane);
        });
        return () => cancelAnimationFrame(frame);
    }, [props.recipients, showDraftForm]);

    const draftContent = <form
        id={draftFormId}
        className="recipient-draft"
        data-recipient-draft
        onClick={event => event.stopPropagation()}
        onKeyDown={event => {
            event.stopPropagation();
            if (event.key === "Escape") {
                event.preventDefault();
                closeDraft();
            }
        }}
        onSubmit={event => {
            event.preventDefault();
            event.stopPropagation();
            if (!validTitle) return;
            const draftRecipient: BulkRecipient = {
                owner: {
                    reference: {type: "project", projectId: `draft:${crypto.randomUUID()}`},
                    title,
                    primaryUsername: Client.username ?? "",
                },
                products: [],
                newProject: {title, subAllocator: props.canCreateSubAllocator && draftSubAllocator},
            };
            pendingFocusRef.current = {pane: "selected", key: recipientKey(draftRecipient)};
            props.onChange([...props.recipients, draftRecipient]);
            closeDraft(false);
        }}
    >
        <div className="recipient-draft-title">
            <Input
                inputRef={draftInput}
                height={28}
                aria-label="New sub-project title"
                placeholder="Project title"
                autoFocus
                aria-invalid={title.startsWith("%")}
                value={draftTitle}
                onChange={event => {
                    const value = event.currentTarget.value;
                    setDraftTitle(value);
                    props.onDraftPendingChange(value.trim() !== "" || draftSubAllocator);
                }}
                required
            />
            <TooltipV2 tooltip="Cancel">
                <button
                    type="button"
                    className={UcxTableIconButtonClass}
                    aria-label="Cancel new sub-project"
                    onClick={() => closeDraft()}
                >
                    <Icon name="heroXMark" size={14} />
                </button>
            </TooltipV2>
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

    return <div ref={rootRef} className={RecipientPickerStyle}
        onKeyDown={event => {
            const target = event.target as HTMLElement;
            const editable = target.closest("input, textarea, select, [contenteditable='true']") != null;
            if (!editable && event.key === "/" && !event.metaKey && !event.ctrlKey && !event.altKey) {
                event.preventDefault();
                event.stopPropagation();
                searchRef.current?.focus({preventScroll: true});
                return;
            }
            if (editable) return;
            if (event.code === "KeyN" && !event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
                event.preventDefault();
                event.stopPropagation();
                if (showDraftForm) {
                    draftInput.current?.focus({preventScroll: true});
                } else {
                    setShowDraftForm(true);
                }
                return;
            }
            const horizontalArrow = event.key === "ArrowLeft" || event.key === "ArrowRight";
            if (horizontalArrow && !event.altKey && !event.ctrlKey && !event.metaKey) {
                const table = target.closest<HTMLElement>("[data-recipient-table]");
                if (!table) return;
                const pane = table.getAttribute("data-recipient-table");
                const nextPane = event.key === "ArrowLeft"
                    ? (pane === "available" ? "selected" : null)
                    : (pane === "selected" ? "available" : null);
                if (nextPane === null) return;
                event.preventDefault();
                event.stopPropagation();
                focusPane(nextPane);
                return;
            }
        }}>
        <section ref={selectedSection}>
            <div className="recipient-heading">
                <h4>Selected recipients ({props.recipients.length})</h4>
                {!showDraftForm ?
                    <Button
                        btnRef={newSubProjectButton}
                        type="button"
                        color="secondaryMain"
                        height={32}
                        ariaKeyshortcuts="N"
                        onClick={() => setShowDraftForm(true)}
                    >
                        New sub-project<SubmitShortcut shortcut="N" modifiers={[]} />
                    </Button> :
                    null}
            </div>
            <RecipientTable
                pane="selected"
                recipients={props.recipients}
                projectInfo={props.projectInfo}
                emptyMessage="No recipients selected."
                draft={showDraftForm ? {content: draftContent, formId: draftFormId, canSubmit: validTitle} : undefined}
                onAction={removeRecipient}
            />
        </section>
        <section>
            <div className="recipient-heading"><h4>Available recipients</h4></div>
            <Search
                className="recipient-search"
                inputRef={searchRef}
                query={query}
                onQueryChange={setQuery}
                maxLength={64}
                ariaLabel="Search available recipients"
                ariaKeyshortcuts="/"
                dataAttributes={{"data-recipient-search": ""}}
                onKeyDown={event => {
                    const moveToTable = event.key === "ArrowUp" || event.key === "ArrowDown";
                    if (!moveToTable && event.key !== "Escape" && event.key !== "Enter") return;
                    const modifierPressed = event.altKey || event.ctrlKey || event.metaKey || event.shiftKey;
                    if (moveToTable && modifierPressed) return;
                    event.preventDefault();
                    event.stopPropagation();
                    focusPane("available");
                }}
                placeholder={result?.canSearchCatalog ? "Search projects or people…" : "Search projects…"}
            />
            {searchStatus === "failed" ?
                <div role="alert">
                    Could not search for recipients.{" "}
                    <Button color="secondaryMain" onClick={() => setRetry(value => value + 1)}>Retry</Button>
                </div> :
                <RecipientTable
                    pane="available"
                    recipients={searchStatus === "ready" ? addable : []}
                    projectInfo={props.projectInfo}
                    emptyMessage={availableEmptyMessage}
                    onAction={addRecipient}
                />}
        </section>
    </div>;
}
