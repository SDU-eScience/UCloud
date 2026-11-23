import * as React from "react";
import {TableColumnSortType} from "@/UCX/protocol";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {injectStyle} from "@/Unstyled";
import {TooltipV2} from "@/ui-components/Tooltip";
import type {AllocationAmounts} from "./AllocationModel";

const AllocationWorkspaceLabelStyle = injectStyle("allocation-workspace-label", k => `
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

export function AllocationWorkspaceLabel(props: {title: string; meta?: string; metaTooltip?: string}): React.ReactNode {
    return <div className={AllocationWorkspaceLabelStyle}>
        <TooltipV2 tooltip={props.title} triggerClassName="workspace-tooltip">
            <span className="workspace-title">{props.title}</span>
        </TooltipV2>
        {props.meta ? <TooltipV2 tooltip={props.metaTooltip ?? props.meta} triggerClassName="workspace-tooltip">
            <span className="workspace-meta">{props.meta}</span>
        </TooltipV2> : null}
    </div>;
}

const AllocationAmountStyle = injectStyle("allocation-amount", k => `
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

export function AllocationAmount(props: {
    value: number;
    unit: string;
    title?: string;
    negative?: boolean;
    approximate?: boolean;
}): React.ReactNode {
    return <TooltipV2 tooltip={props.title}>
        <div className={AllocationAmountStyle} data-negative={props.negative} data-approximate={props.approximate}>
            <span className="amount-value">
                {props.approximate ? "~" : ""}
                {formatNumber(props.value, {precision: 2, removeTrailingZeros: true})}
            </span>
            <span className="amount-unit">{props.unit}</span>
        </div>
    </TooltipV2>;
}

export const allocationLifecycleMetrics = [
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

type AllocationLifecycleMetric = typeof allocationLifecycleMetrics[number];

export const allocationLifecycleColumns = allocationLifecycleMetrics.map(({key, label}) => ({
    key, label, jsonPath: key, sortType: TableColumnSortType.Number, alignment: "right" as const,
}));

export const allocationLifecycleWidths = Object.fromEntries(
    allocationLifecycleMetrics.map(({key, width}) => [key, width]),
);

export function allocationLifecycleCells(
    amounts: AllocationAmounts,
    consumption: boolean,
    maxUsable: number | undefined,
): Record<string, string> {
    return Object.fromEntries(allocationLifecycleMetrics.map(metric => {
        if (!allocationLifecycleApplies(metric, consumption)) return [metric.key, ""];
        const value = metric.key === "activeBalance" ? maxUsable : amounts[metric.key];
        return [metric.key, value === undefined ? "" : `${value}`];
    }));
}

function allocationLifecycleApplies(metric: AllocationLifecycleMetric, consumption: boolean): boolean {
    return consumption || metric.key !== "expiredUnused";
}

export function AllocationLifecycleAmount(props: {
    metric: AllocationLifecycleMetric;
    amounts: AllocationAmounts;
    consumption: boolean;
    unit: string;
    maxUsable?: number;
}): React.ReactNode {
    if (!allocationLifecycleApplies(props.metric, props.consumption)) return null;
    const value = props.metric.key === "activeBalance" ? props.maxUsable : props.amounts[props.metric.key];
    if (value === undefined) return null;
    return <AllocationAmount value={value} unit={props.unit}
        title={props.metric.key === "used" && !props.consumption ? "Current capacity in use." : props.metric.title} />;
}

const AllocationUsageStyle = injectStyle("allocation-usage-cell", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
    }
    ${k} .usage-values {
        display: flex;
        align-items: baseline;
        gap: 4px;
        line-height: 14px;
    }
    ${k} .usage-value {
        font-size: 12px;
        color: var(--textPrimary);
    }
    ${k} .usage-quota {
        font-size: 10.5px;
        color: var(--textSecondary);
    }
    ${k} .usage-bottom {
        display: grid;
        grid-template-columns: 64px 36px;
        align-items: center;
        gap: 8px;
    }
    ${k} .usage-track {
        width: 64px;
        height: 3px;
        border-radius: 2px;
        overflow: hidden;
        background: var(--borderColor);
    }
    ${k} .usage-track > span {
        display: block;
        height: 100%;
        background: var(--primaryMain);
    }
    ${k}[data-exhausted="true"] .usage-track > span {
        background: var(--errorMain);
    }
    ${k} .usage-percentage {
        font-size: 10px;
        line-height: 12px;
        text-align: right;
        color: var(--textSecondary);
    }
    ${k}[data-compact="true"] .usage-bottom {
        grid-template-columns: 48px 32px;
        gap: 6px;
    }
    ${k}[data-compact="true"] .usage-track {
        width: 48px;
    }
`);

export function AllocationUsage(props: {
    usage: number;
    quota: number;
    unit: string;
    title?: string;
    details?: string;
    compact?: boolean;
}): React.ReactNode {
    const usage = formatNumber(props.usage, {precision: 2, removeTrailingZeros: true});
    const quota = formatNumber(props.quota, {precision: 2, removeTrailingZeros: true});
    const percentage = props.quota > 0 ? (props.usage / props.quota) * 100 : null;
    const label = percentage === null ? "—" : `${formatNumber(Math.round(percentage))}%`;
    const title = props.title ?? `${usage} ${props.unit} used of ${quota} ${props.unit} (${label})`;
    return <TooltipV2 tooltip={props.details === undefined ? title : `${title}. ${props.details}`}>
        <div
            className={AllocationUsageStyle}
            data-compact={props.compact}
            data-exhausted={props.quota > 0 && props.usage >= props.quota}
        >
            <div className="usage-values">
                <span className="usage-value">{usage}</span>
                <span className="usage-quota">
                    {props.compact ? props.unit : `/ ${quota} ${props.unit}`}
                </span>
            </div>
            <div className="usage-bottom">
                <span className="usage-track" aria-hidden="true">
                    <span style={{width: `${Math.max(0, Math.min(100, percentage ?? 0))}%`}} />
                </span>
                <span className="usage-percentage">{label}</span>
            </div>
        </div>
    </TooltipV2>;
}
