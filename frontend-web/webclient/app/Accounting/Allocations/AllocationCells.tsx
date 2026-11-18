import * as React from "react";
import {TableColumnSortType} from "@/UCX/protocol";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {injectStyle} from "@/Unstyled";
import {TooltipV2} from "@/ui-components/Tooltip";
import type {AllocationAmounts} from "./AllocationModel";

const AllocationAmountStyle = injectStyle("allocation-amount", k => `
    ${k} { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-variant-numeric: tabular-nums; white-space: nowrap; }
    ${k} .amount-value { color: var(--textPrimary); font-size: 12px; line-height: 14px; font-weight: 400; }
    ${k} .amount-unit { color: var(--textSecondary); font-size: 10.5px; line-height: 12px; }
    ${k}[data-negative="true"] .amount-value { color: var(--errorMain); }
`);

export function AllocationAmount(props: {
    value: number;
    unit: string;
    title?: string;
    negative?: boolean;
}): React.ReactNode {
    return <TooltipV2 tooltip={props.title}>
        <div className={AllocationAmountStyle} data-negative={props.negative}>
            <span className="amount-value">{formatNumber(props.value, {precision: 2, removeTrailingZeros: true})}</span>
            <span className="amount-unit">{props.unit}</span>
        </div>
    </TooltipV2>;
}

export const allocationLifecycleMetrics = [
    {key: "activeBalance", label: "Active balance", width: 110, title: "Active quota minus active usage. Parent funding limits can reduce what you can use."},
    {key: "activeUsed", label: "Active usage", width: 102, title: "Usage that counts against active quota."},
    {key: "activeQuota", label: "Active quota", width: 99, title: "Quota from allocations that are active now."},
    {key: "retiredUsed", label: "Used at expiry", width: 104, title: "Usage recorded when allocations expired. This is not usage by date."},
    {key: "expiredUnused", label: "Expired unused", width: 110, title: "Quota that expired without being used."},
    {key: "future", label: "Upcoming quota", width: 111, title: "Granted quota that has not been released yet."},
] as const;

type AllocationLifecycleMetric = typeof allocationLifecycleMetrics[number];

export const allocationLifecycleColumns = allocationLifecycleMetrics.map(({key, label}) => ({
    key, label, jsonPath: key, sortType: TableColumnSortType.Number,
}));

export const allocationLifecycleWidths = Object.fromEntries(allocationLifecycleMetrics.map(({key, width}) => [key, width]));

export function allocationLifecycleCells(amounts: AllocationAmounts, consumption: boolean): string[] {
    return allocationLifecycleMetrics.map(metric => allocationLifecycleApplies(metric, consumption) ? `${amounts[metric.key]}` : "");
}

function allocationLifecycleApplies(metric: AllocationLifecycleMetric, consumption: boolean): boolean {
    return consumption || (metric.key !== "retiredUsed" && metric.key !== "expiredUnused");
}

export function AllocationLifecycleAmount(props: {
    metric: AllocationLifecycleMetric;
    amounts: AllocationAmounts;
    consumption: boolean;
    unit: string;
}): React.ReactNode {
    if (!allocationLifecycleApplies(props.metric, props.consumption)) return null;
    const value = props.amounts[props.metric.key];
    const balance = props.metric.key === "activeBalance";
    return <AllocationAmount value={value} unit={props.unit} negative={balance && value < 0}
        title={props.metric.title} />;
}

const AllocationUsageStyle = injectStyle("allocation-usage-cell", k => `
    ${k} { display: flex; flex-direction: column; gap: 4px; font-variant-numeric: tabular-nums; white-space: nowrap; }
    ${k} .usage-values { display: flex; align-items: baseline; gap: 4px; line-height: 14px; }
    ${k} .usage-value { font-size: 12px; color: var(--textPrimary); }
    ${k} .usage-quota { font-size: 10.5px; color: var(--textSecondary); }
    ${k} .usage-bottom { display: grid; grid-template-columns: 64px 36px; align-items: center; gap: 8px; }
    ${k} .usage-track { width: 64px; height: 3px; border-radius: 2px; overflow: hidden; background: var(--borderColor); }
    ${k} .usage-track > span { display: block; height: 100%; background: var(--primaryMain); }
    ${k}[data-exhausted="true"] .usage-track > span { background: var(--errorMain); }
    ${k} .usage-percentage { font-size: 10px; line-height: 12px; text-align: right; color: var(--textSecondary); }
    ${k}[data-compact="true"] .usage-bottom { grid-template-columns: 48px 32px; gap: 6px; }
    ${k}[data-compact="true"] .usage-track { width: 48px; }
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
        <div className={AllocationUsageStyle} data-compact={props.compact} data-exhausted={props.quota > 0 && props.usage >= props.quota}>
            <div className="usage-values"><span className="usage-value">{usage}</span><span className="usage-quota">{props.compact ? props.unit : `/ ${quota} ${props.unit}`}</span></div>
            <div className="usage-bottom">
                <span className="usage-track" aria-hidden="true"><span style={{width: `${Math.max(0, Math.min(100, percentage ?? 0))}%`}} /></span>
                <span className="usage-percentage">{label}</span>
            </div>
        </div>
    </TooltipV2>;
}
