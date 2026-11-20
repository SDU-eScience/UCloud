import * as React from "react";
import ReactModal from "react-modal";
import {format} from "date-fns";
import {allocationInputUnit, allocationInputValue} from "@/Accounting";
import type {ProductCategoryV2} from "@/Accounting";
import {Button} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {injectStyle} from "@/Unstyled";
import {AllocationAmount} from "./AllocationCells";
import type {AllocationSlice} from "./AllocationSlices";

const AllocationBreakdownStyle = injectStyle("allocation-breakdown", k => `
    ${k} { display: flex; flex-direction: column; max-height: 75vh; min-height: 0; color: var(--textPrimary); }
    ${k} header { padding: 20px 24px 16px; border-bottom: 1px solid var(--borderColor); }
    ${k} h3 { margin: 0 0 8px; }
    ${k} p { margin: 0; color: var(--textSecondary); }
    ${k} .allocation-breakdown-body { padding: 16px 24px; overflow: auto; min-height: 0; }
    ${k} table { width: 100%; border-collapse: collapse; }
    ${k} th, ${k} td { padding: 10px 8px; border-bottom: 1px solid var(--borderColor); text-align: left; }
    ${k} th { color: var(--textSecondary); font-weight: 400; font-size: 12px; }
    ${k} th:nth-child(2), ${k} td:nth-child(2) { text-align: right; }
    ${k} .allocation-breakdown-date { white-space: nowrap; }
    ${k} .allocation-breakdown-time { margin-left: 6px; font-size: 12px; color: var(--textSecondary); }
    ${k} .allocation-breakdown-active { font-weight: 600; color: var(--primaryMain); }
    ${k} footer { display: flex; justify-content: flex-end; padding: 12px 24px; background: var(--dialogToolbar); }
    ${k} .allocation-breakdown-note { margin-top: 12px; font-size: 12px; }
`);

export function AllocationBreakdownDialog(props: {
    workspaceTitle?: string;
    category: ProductCategoryV2;
    slices: AllocationSlice[];
    onClose: () => void;
    onEdit?: (slice: AllocationSlice) => void;
}): React.ReactNode {
    const now = Date.now();
    const unit = allocationInputUnit(props.category).name;
    const total = props.slices.reduce((sum, slice) => sum + slice.quota, 0);
    const date = (timestamp: number) => <span className="allocation-breakdown-date">
        {format(timestamp, "d MMM yyyy")}<span className="allocation-breakdown-time">{format(timestamp, "HH:mm")}</span>
    </span>;
    return <ReactModal
        isOpen
        ariaHideApp={false}
        contentLabel="Allocation breakdown"
        onRequestClose={props.onClose}
        className={CardClass}
        style={{...largeModalStyle, content: {...largeModalStyle.content, width: "800px", height: "auto", padding: 0}}}
    >
        <div className={AllocationBreakdownStyle}>
            <header>
                <h3>Allocation breakdown</h3>
                <p>{props.workspaceTitle ? `${props.workspaceTitle}, ` : ""}{props.category.name} ({props.category.provider})</p>
            </header>
            <div className="allocation-breakdown-body">
                <table>
                    <thead><tr><th>Period</th><th>Quota</th><th>Status</th>{props.onEdit ? <th>Actions</th> : null}</tr></thead>
                    <tbody>{props.slices.map((slice, index) => {
                        const active = slice.start <= now && now < slice.end;
                        const expired = slice.end <= now;
                        return <tr key={slice.allocationId ?? index}>
                            <td>{date(slice.start)}<br />{date(slice.end - 1)}</td>
                            <td><AllocationAmount value={allocationInputValue(props.category, slice.quota)} unit={unit} /></td>
                            <td className={active ? "allocation-breakdown-active" : undefined}>
                                {slice.quota === 0 ? "Zero quota" : expired ? "Expired" : active ? "Active" : "Upcoming"}
                            </td>
                            {props.onEdit ? <td><Button
                                disabled={expired || slice.allocationId === undefined}
                                onClick={() => props.onEdit?.(slice)}
                            >Edit</Button></td> : null}
                        </tr>;
                    })}</tbody>
                    <tfoot><tr><td>Total</td><td><AllocationAmount value={allocationInputValue(props.category, total)} unit={unit} /></td><td colSpan={props.onEdit ? 2 : 1} /></tr></tfoot>
                </table>
                <p className="allocation-breakdown-note">Unused quota expires after each slice. Future slices are not currently available.</p>
            </div>
            <footer><Button onClick={props.onClose}>Close</Button></footer>
        </div>
    </ReactModal>;
}
