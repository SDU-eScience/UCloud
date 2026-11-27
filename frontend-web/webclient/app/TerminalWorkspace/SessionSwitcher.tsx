import * as React from "react";
import {useEffect, useMemo, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import Icon from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {TerminalSessionDetail, TerminalSessionSummary} from "@/UCloud/TerminalSessionsApi";
import JobsApi, {Job} from "@/UCloud/JobsApi";
import {callAPI} from "@/Authentication/DataHook";
import {shortUUID} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {SafeLogo} from "@/Applications/AppToolLogo";

interface SessionGroup {
    jobId: string;
    jobTitle: string;
    applicationName: string;
    applicationTitle: string;
    sessions: TerminalSessionSummary[];
}

export const TerminalSessionSwitcher = React.memo(function TerminalSessionSwitcher({
    open,
    onOpenChange,
    activeSession,
    sessions,
    onSelectSession,
    onCreateSession,
    onRenameSession,
    onShutdownSession,
    onRefreshSessions,
}: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    activeSession: TerminalSessionDetail | null;
    sessions: TerminalSessionSummary[];
    onSelectSession: (sessionId: string) => void;
    onCreateSession: (jobId: string, rank?: number) => void;
    onRenameSession: (sessionId: string, title: string) => void;
    onShutdownSession: (sessionId: string) => void;
    onRefreshSessions: () => void;
}): React.ReactNode {
    const rootRef = useRef<HTMLDivElement | null>(null);
    const searchRef = useRef<HTMLInputElement | null>(null);
    const [filter, setFilter] = useState("");
    const [jobPickerOpen, setJobPickerOpen] = useState(false);
    const [runningJobs, setRunningJobs] = useState<Job[]>([]);
    const [loadingJobs, setLoadingJobs] = useState(false);
    const [renaming, setRenaming] = useState(false);
    const [renameValue, setRenameValue] = useState("");
    const [confirmShutdown, setConfirmShutdown] = useState(false);

    useEffect(() => {
        if (!open) {
            setFilter("");
            setJobPickerOpen(false);
            setRenaming(false);
            setConfirmShutdown(false);
            return;
        }
        onRefreshSessions();
        window.requestAnimationFrame(() => searchRef.current?.focus());

        const onPointerDown = (event: MouseEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) {
                onOpenChange(false);
            }
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                event.stopPropagation();
                onOpenChange(false);
            }
        };
        window.addEventListener("mousedown", onPointerDown);
        window.addEventListener("keydown", onKeyDown, true);
        return () => {
            window.removeEventListener("mousedown", onPointerDown);
            window.removeEventListener("keydown", onKeyDown, true);
        };
    }, [open, onOpenChange, onRefreshSessions]);

    const openJobPicker = () => {
        setJobPickerOpen(true);
        setLoadingJobs(true);
        void callAPI(JobsApi.browse({
            itemsPerPage: 100,
            filterState: "RUNNING",
            includeApplication: true,
        })).then(page => {
            const items = page?.items ?? [];
            const sorted = [...items].sort((a, b) => {
                if (activeSession && a.id === activeSession.jobId) return -1;
                if (activeSession && b.id === activeSession.jobId) return 1;
                return (b.status.startedAt ?? b.createdAt ?? 0) - (a.status.startedAt ?? a.createdAt ?? 0);
            });
            setRunningJobs(sorted);
        }).catch(() => {
            setRunningJobs([]);
        }).finally(() => {
            setLoadingJobs(false);
        });
    };

    const groupedSessions = useMemo<SessionGroup[]>(() => {
        const needle = filter.trim().toLowerCase();
        const byJob = new Map<string, SessionGroup>();

        for (const item of sessions) {
            if (needle) {
                const hay = `${item.title} ${item.jobTitle} ${item.jobId} ${item.applicationTitle}`.toLowerCase();
                if (!hay.includes(needle)) continue;
            }
            let group = byJob.get(item.jobId);
            if (!group) {
                group = {
                    jobId: item.jobId,
                    jobTitle: item.jobTitle,
                    applicationName: item.applicationName,
                    applicationTitle: item.applicationTitle,
                    sessions: [],
                };
                byJob.set(item.jobId, group);
            }
            group.sessions.push(item);
        }

        const list = [...byJob.values()];
        list.sort((a, b) => {
            if (activeSession && a.jobId === activeSession.jobId) return -1;
            if (activeSession && b.jobId === activeSession.jobId) return 1;
            const aTime = a.sessions[0]?.lastAccessedAt ?? 0;
            const bTime = b.sessions[0]?.lastAccessedAt ?? 0;
            return bTime - aTime;
        });
        return list;
    }, [sessions, filter, activeSession]);

    return <div ref={rootRef} className={SwitcherClass}>
        <TooltipV2 tooltip="Switch or manage terminal sessions (Ctrl+Alt+K)" disabled={open}>
            <button
                type="button"
                className="switcher-trigger"
                onClick={() => onOpenChange(!open)}
                aria-expanded={open}
            >
                <div className="switcher-app-icon">
                    {activeSession && (activeSession.applicationName || activeSession.applicationTitle) ? (
                        <SafeLogo
                            name={activeSession.applicationName || activeSession.applicationTitle}
                            title={activeSession.applicationTitle}
                            type="APPLICATION"
                            size="18px"
                        />
                    ) : (
                        <Icon name="heroCommandLine" size={16} />
                    )}
                </div>
                <div className="switcher-title">
                    {activeSession ? (
                        <>
                            <span>{activeSession.title}</span>
                            <span className="switcher-job-id"> (#{shortUUID(activeSession.jobId)})</span>
                        </>
                    ) : "Terminals"}
                </div>
                <span className="switcher-chevron">
                    <Icon name={open ? "heroChevronUp" : "heroChevronDown"} size={14} />
                </span>
            </button>
        </TooltipV2>

        {!open ? null : <div className="switcher-popover" role="dialog" aria-label="Terminal sessions">
            {jobPickerOpen ? (
                <div className="job-picker">
                    <div className="popover-head">
                        <button type="button" className="back-btn" onClick={() => setJobPickerOpen(false)}>
                            <Icon name="heroChevronLeft" size={14} />
                            <span>Back</span>
                        </button>
                        <strong>New session on job</strong>
                    </div>
                    <div className="popover-list">
                        {loadingJobs ? (
                            <div className="empty-hint">Loading running jobs...</div>
                        ) : runningJobs.length === 0 ? (
                            <div className="empty-hint">No running jobs available.</div>
                        ) : runningJobs.map(job => {
                            const label = job.specification.name?.trim() || `Job ${shortUUID(job.id)}`;
                            const appName = job.status.resolvedApplication?.metadata.title ?? job.specification.application.name;
                            const isCurrent = activeSession?.jobId === job.id;
                            return <button
                                key={job.id}
                                type="button"
                                className={`session-row ${isCurrent ? "current" : ""}`}
                                onClick={() => {
                                    onOpenChange(false);
                                    onCreateSession(job.id, 0);
                                }}
                            >
                                <Icon name="heroServer" size={14} />
                                <div className="row-text">
                                    <div className="row-title">{label}</div>
                                    <div className="row-meta">{appName} · Job {shortUUID(job.id)}</div>
                                </div>
                                {isCurrent ? <span className="tag">Current</span> : null}
                            </button>;
                        })}
                    </div>
                </div>
            ) : (
                <>
                    <div className="search-wrap">
                        <input
                            ref={searchRef}
                            type="text"
                            className="search-input"
                            placeholder="Filter sessions or jobs..."
                            value={filter}
                            onChange={e => setFilter(e.target.value)}
                        />
                    </div>

                    <div className="popover-list">
                        {groupedSessions.length === 0 ? (
                            <div className="empty-hint">No active sessions found.</div>
                        ) : groupedSessions.map(group => <div key={group.jobId} className="job-group">
                            <div className="job-group-header">
                                <span className="job-group-title">
                                    Job {shortUUID(group.jobId)} — {group.applicationTitle || group.jobTitle}
                                </span>
                                <TooltipV2 tooltip={`New session on Job ${shortUUID(group.jobId)}`}>
                                    <button
                                        type="button"
                                        className="group-add-btn"
                                        onClick={e => {
                                            e.stopPropagation();
                                            onOpenChange(false);
                                            onCreateSession(group.jobId, 0);
                                        }}
                                    >
                                        <Icon name="heroPlus" size={13} />
                                    </button>
                                </TooltipV2>
                            </div>
                            {group.sessions.map(item => {
                                const isActive = activeSession?.id === item.id;
                                return <button
                                    key={item.id}
                                    type="button"
                                    className={`session-row ${isActive ? "current" : ""}`}
                                    onClick={() => {
                                        onOpenChange(false);
                                        if (!isActive) onSelectSession(item.id);
                                    }}
                                >
                                    <Icon name="heroCommandLine" size={14} />
                                    <div className="row-text">
                                        <div className="row-title">{item.title}</div>
                                        <div className="row-meta">{item.tabCount} tab{item.tabCount === 1 ? "" : "s"} · {item.paneCount} pane{item.paneCount === 1 ? "" : "s"}</div>
                                    </div>
                                </button>;
                            })}
                        </div>)}
                    </div>

                    <div className="popover-actions">
                        <button type="button" className="action-row" onClick={openJobPicker}>
                            <Icon name="heroPlus" size={14} />
                            <span>New session...</span>
                        </button>

                        {!activeSession ? null : (
                            <>
                                {renaming ? (
                                    <form
                                        className="rename-form"
                                        onSubmit={e => {
                                            e.preventDefault();
                                            const trimmed = renameValue.trim();
                                            if (trimmed) {
                                                onRenameSession(activeSession.id, trimmed);
                                            }
                                            setRenaming(false);
                                            onOpenChange(false);
                                        }}
                                    >
                                        <input
                                            type="text"
                                            className="search-input"
                                            value={renameValue}
                                            autoFocus
                                            onChange={e => setRenameValue(e.target.value)}
                                        />
                                        <button type="submit" className="mini-btn primary">Save</button>
                                    </form>
                                ) : (
                                    <button
                                        type="button"
                                        className="action-row"
                                        onClick={() => {
                                            setRenameValue(activeSession.title);
                                            setRenaming(true);
                                        }}
                                    >
                                        <Icon name="heroPencil" size={14} />
                                        <span>Rename current session</span>
                                    </button>
                                )}

                                <a
                                    className="action-row"
                                    href={`${AppRoutes.prefix}${AppRoutes.jobs.view(activeSession.jobId)}`}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    onClick={() => onOpenChange(false)}
                                >
                                    <Icon name="heroServer" size={14} />
                                    <span>View job details</span>
                                </a>

                                {confirmShutdown ? (
                                    <div className="shutdown-confirm">
                                        <span>Shut down "{activeSession.title}" and kill all its shells?</span>
                                        <div className="confirm-buttons">
                                            <button type="button" className="mini-btn" onClick={() => setConfirmShutdown(false)}>Cancel</button>
                                            <button
                                                type="button"
                                                className="mini-btn danger"
                                                onClick={() => {
                                                    setConfirmShutdown(false);
                                                    onOpenChange(false);
                                                    onShutdownSession(activeSession.id);
                                                }}
                                            >
                                                Shut down
                                            </button>
                                        </div>
                                    </div>
                                ) : (
                                    <button type="button" className="action-row danger" onClick={() => setConfirmShutdown(true)}>
                                        <Icon name="heroTrash" size={14} />
                                        <span>Shut down current session</span>
                                    </button>
                                )}
                            </>
                        )}
                    </div>
                </>
            )}
        </div>}
    </div>;
});

const SwitcherClass = injectStyle("terminal-session-switcher", k => `
    ${k} {
        position: relative;
        flex: none;
        font-family: var(--sansSerif);
    }

    ${k} .switcher-trigger {
        min-width: 196px;
        max-width: 300px;
        height: 32px;
        padding: 0 10px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        border: 1px solid var(--borderColor);
        border-radius: 7px;
        background: var(--backgroundCard);
        color: var(--textPrimary);
        cursor: pointer;
        text-align: left;
    }

    ${k} .switcher-app-icon {
        width: 18px;
        height: 18px;
        flex: none;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        line-height: 0;
        color: var(--textSecondary);
    }

    ${k} .switcher-app-icon > * {
        display: block;
    }

    ${k} .switcher-chevron {
        flex: none;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        line-height: 0;
        color: var(--textSecondary);
    }

    ${k} .switcher-trigger:hover {
        border-color: var(--borderColorHover);
        background: var(--rowHover);
    }

    html.dark ${k} .switcher-trigger {
        background: #0a0a0a;
    }

    ${k} .switcher-title {
        min-width: 0;
        flex: 1;
        font-size: 13px;
        font-weight: 600;
        line-height: 1.2;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .switcher-job-id {
        font-weight: 400;
        color: var(--textSecondary);
    }

    ${k} .switcher-popover {
        position: absolute;
        top: calc(100% + 6px);
        left: 0;
        width: 340px;
        max-height: 75vh;
        z-index: 40;
        display: flex;
        flex-direction: column;
        background: var(--backgroundCard);
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        box-shadow: 0 12px 36px rgba(0, 0, 0, 0.35);
        overflow: hidden;
    }

    ${k} .search-wrap,
    ${k} .popover-head {
        padding: 8px 10px;
        border-bottom: 1px solid var(--borderColor);
        display: flex;
        align-items: center;
        gap: 8px;
    }

    ${k} .search-input {
        width: 100%;
        padding: 6px 8px;
        border-radius: 6px;
        border: 1px solid var(--borderColor);
        background: var(--background);
        color: var(--textPrimary);
        font: inherit;
        font-size: 13px;
        outline: none;
    }

    ${k} .search-input:focus {
        border-color: var(--primaryMain);
    }

    ${k} .popover-list {
        flex: 1;
        overflow-y: auto;
        padding: 6px;
        max-height: 320px;
    }

    ${k} .job-group {
        margin-bottom: 8px;
    }

    ${k} .job-group:last-child {
        margin-bottom: 0;
    }

    ${k} .job-group-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 4px 6px;
        font-size: 11px;
        font-weight: 600;
        color: var(--textSecondary);
        text-transform: uppercase;
        letter-spacing: 0.03em;
    }

    ${k} .job-group-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .group-add-btn,
    ${k} .back-btn {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        border: 0;
        border-radius: 5px;
        padding: 3px 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font: inherit;
        font-size: 12px;
    }

    ${k} .group-add-btn:hover,
    ${k} .back-btn:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k} .session-row,
    ${k} .action-row {
        width: 100%;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 7px 8px;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        font: inherit;
        font-size: 13px;
        text-align: left;
        text-decoration: none;
        box-sizing: border-box;
    }

    ${k} .session-row:hover,
    ${k} .action-row:hover {
        background: var(--rowHover);
    }

    ${k} .session-row.current {
        background: var(--rowActive);
    }

    ${k} .row-text {
        min-width: 0;
        flex: 1;
    }

    ${k} .row-title {
        font-weight: 500;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .row-meta {
        font-size: 11px;
        color: var(--textSecondary);
    }

    ${k} .tag {
        font-size: 11px;
        padding: 1px 6px;
        border-radius: 999px;
        background: var(--primaryMain);
        color: #ffffff;
    }

    ${k} .popover-actions {
        padding: 6px;
        border-top: 1px solid var(--borderColor);
        display: flex;
        flex-direction: column;
        gap: 2px;
    }

    ${k} .action-row.danger {
        color: #e5534b;
    }

    ${k} .rename-form {
        display: flex;
        gap: 6px;
        padding: 4px;
    }

    ${k} .shutdown-confirm {
        padding: 8px;
        border-radius: 6px;
        background: var(--background);
        border: 1px solid #e5534b;
        display: flex;
        flex-direction: column;
        gap: 8px;
        font-size: 12px;
    }

    ${k} .confirm-buttons {
        display: flex;
        justify-content: flex-end;
        gap: 6px;
    }

    ${k} .mini-btn {
        padding: 4px 9px;
        border-radius: 5px;
        border: 1px solid var(--borderColor);
        background: var(--backgroundCard);
        color: var(--textPrimary);
        cursor: pointer;
        font: inherit;
        font-size: 12px;
    }

    ${k} .mini-btn.primary {
        background: var(--primaryMain);
        border-color: var(--primaryMain);
        color: #ffffff;
    }

    ${k} .mini-btn.danger {
        background: #e5534b;
        border-color: #e5534b;
        color: #ffffff;
    }

    ${k} .empty-hint {
        padding: 16px 8px;
        text-align: center;
        color: var(--textSecondary);
        font-size: 12.5px;
    }
`);
