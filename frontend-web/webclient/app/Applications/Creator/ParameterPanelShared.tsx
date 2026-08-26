// Shared panel components
// =====================================================================================================================
// PanelSection and PanelRow are used by both the metadata panel and the parameter panel. Each
// section has a bold header (no background, no uppercase) and stacked rows underneath. The
// sections are separated by a top border and extra vertical spacing. The last section has no
// bottom border.

import * as React from "react";
import {useState} from "react";
import {injectStyle} from "@/Unstyled";
import {Text} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import {Toggle} from "@/ui-components/Toggle";

// PanelSection renders a bold header and stacked rows. When `collapsedByDefault` is true the
// section starts collapsed and the user can toggle it with the chevron button in the header.
export function PanelSection(props: {
    title: string;
    children: React.ReactNode;
    collapsedByDefault?: boolean;
}): React.ReactNode {
    const [collapsed, setCollapsed] = useState(props.collapsedByDefault === true);
    const toggle = () => setCollapsed(c => !c);
    return (
        <div className={PanelSectionClass} data-collapsed={collapsed}>
            <div className="panel-section-header">
                <span className="panel-section-title" onClick={toggle}>{props.title}</span>
                <IconButton
                    icon={collapsed ? "heroChevronRight" : "heroChevronDown"}
                    tooltip={collapsed ? "Expand section" : "Collapse section"}
                    onClick={toggle}
                    compact
                />
            </div>
            {collapsed ? null : <div className="panel-section-body">{props.children}</div>}
        </div>
    );
}

export function PanelRow(props: {label: string; value: string}): React.ReactNode {
    return (
        <div className="panel-row">
            <span className="panel-row-label">{props.label}</span>
            <span className="panel-row-value">{props.value || "—"}</span>
        </div>
    );
}

// ToggleRow pairs a Toggle with a clickable text label. Clicking the label toggles the switch,
// matching the behaviour of a native checkbox label. The optional `id` prop sets a DOM id used
// by the highlight system to scroll to and animate the toggle.
export function ToggleRow(props: {label: string; checked: boolean; onChange: () => void; disabled?: boolean; id?: string}): React.ReactNode {
    return (
        <div className={ToggleRowClass} id={props.id}>
            <Toggle checked={props.checked} onChange={props.onChange} height={20} disabled={props.disabled} />
            <Text fontSize={13} className="toggle-row-label" onClick={props.disabled ? undefined : props.onChange}>{props.label}</Text>
        </div>
    );
}

export const PanelSectionClass = injectStyle("creator-panel-section-shared", k => `
    ${k} {
        padding: 16px 12px;
    }

    ${k} + ${k} {
        border-top: 1px solid var(--borderColor);
    }

    ${k} > .panel-section-header {
        display: flex;
        align-items: center;
        gap: 4px;
        margin-bottom: 12px;
        font-size: 13px;
        font-weight: 700;
        color: var(--textPrimary);
        flex-shrink: 0;
    }

    ${k}[data-collapsed="true"] > .panel-section-header {
        margin-bottom: 0;
    }

    ${k} > .panel-section-header > .panel-section-title {
        flex: 1 1 auto;
        cursor: pointer;
        user-select: none;
    }

    ${k} > .panel-section-header > .panel-section-title:hover {
        color: var(--textSecondary);
    }

    ${k} > .panel-section-body {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    ${k} .panel-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        min-height: 32px;
    }

    ${k} .panel-row > .panel-row-label {
        flex-shrink: 0;
        color: var(--textSecondary);
        font-size: 13px;
    }

    ${k} .panel-row > .panel-row-value {
        text-align: right;
        font-size: 13px;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .panel-field {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    ${k} .panel-field > .panel-field-label {
        font-size: 13px;
        font-weight: 700;
        color: var(--textPrimary);
    }
`);

const ToggleRowClass = injectStyle("creator-toggle-row", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 8px;
        min-height: 32px;
        cursor: pointer;
        user-select: none;
        border-radius: 6px;
        padding: 2px 4px;
        margin: -2px -4px;
        transition: box-shadow 0.2s ease;
    }

    ${k} > .toggle-row-label {
        cursor: pointer;
    }
`);

// Highlight animation. Applied by creatorHighlightTarget to draw the user's attention to a
// specific metadata control. The element glows with the primary color, fading in and out.
// This is a global rule: any element with class `creator-highlight-active` animates. The
// injectStyle call ignores its generated class name so the rule is not scoped to one selector.
injectStyle("creator-highlight-global", () => `
    .creator-highlight-active {
        animation: creator-pulse-glow 2s ease-out 1;
        border-radius: 6px;
    }

    @keyframes creator-pulse-glow {
        0%   { box-shadow: 0 0 0 0   color-mix(in srgb, var(--primaryMain) 0%,   transparent); }
        15%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--primaryMain) 35%,  transparent); }
        30%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--primaryMain) 35%,  transparent); }
        45%  { box-shadow: 0 0 0 2px color-mix(in srgb, var(--primaryMain) 18%,  transparent); }
        60%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--primaryMain) 35%,  transparent); }
        75%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--primaryMain) 35%,  transparent); }
        100% { box-shadow: 0 0 0 0   color-mix(in srgb, var(--primaryMain) 0%,   transparent); }
    }
`);
