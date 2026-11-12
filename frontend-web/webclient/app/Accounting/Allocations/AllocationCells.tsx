import * as React from "react";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {injectStyle} from "@/Unstyled";

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
    return <div className={AllocationAmountStyle} title={props.title} data-negative={props.negative}>
        <span className="amount-value">{formatNumber(props.value, {precision: 2, removeTrailingZeros: true})}</span>
        <span className="amount-unit">{props.unit}</span>
    </div>;
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
    compact?: boolean;
}): React.ReactNode {
    const usage = formatNumber(props.usage, {precision: 2, removeTrailingZeros: true});
    const quota = formatNumber(props.quota, {precision: 2, removeTrailingZeros: true});
    const percentage = props.quota > 0 ? (props.usage / props.quota) * 100 : null;
    const label = percentage === null ? "—" : `${Math.round(percentage)}%`;
    const title = props.title ?? `${usage} ${props.unit} used of ${quota} ${props.unit} (${label})`;
    return <div className={AllocationUsageStyle} title={title} data-compact={props.compact} data-exhausted={props.quota > 0 && props.usage >= props.quota}>
        <div className="usage-values"><span className="usage-value">{usage}</span><span className="usage-quota">{props.compact ? props.unit : `/ ${quota} ${props.unit}`}</span></div>
        <div className="usage-bottom">
            <span className="usage-track" aria-hidden="true"><span style={{width: `${Math.max(0, Math.min(100, percentage ?? 0))}%`}} /></span>
            <span className="usage-percentage">{label}</span>
        </div>
    </div>;
}
