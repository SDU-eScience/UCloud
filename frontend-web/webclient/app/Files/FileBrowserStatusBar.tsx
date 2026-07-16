import {WalletV2} from "@/Accounting";
import {colorNames} from "@/Accounting/Diagrams";
import AppRoutes from "@/Routes";
import {UFile} from "@/UCloud/UFile";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {sizeToString} from "@/Utilities/FileUtilities";
import React from "react";
import {useNavigate} from "react-router-dom";

export interface FileBrowserStatusData {
    path: string;
    directory: UFile;
    drive: UFile;
    wallet?: WalletV2;
    preciseOwnUsageBytes?: number;
    selected: UFile[];
}

const StatusStyle = injectStyle("file-browser-status", k => `
    ${k} {
        min-height: 52px;
        border-top: 1px solid var(--borderColor);
        background: var(--backgroundDefault);
        padding: 8px 12px;
        box-sizing: border-box;
        display: flex;
        align-items: center;
        gap: 18px;
        overflow-x: auto;
        font-size: 13px;
        flex-shrink: 0;
    }

    ${k} .status-value { 
        white-space: nowrap; 
        font-variant-numeric: tabular-nums;
    }
    
    ${k} .status-muted { color: var(--textSecondary); }
    ${k} .status-spacer { flex: 1 1 auto; min-width: 8px; }
    
    ${k} .utilization { 
        display: flex;
        align-items: center;
        gap: 8px;
        border: 0; 
        padding: 0; 
        background: transparent; 
        color: inherit; 
        cursor: pointer; 
        text-align: left; 
    }
    
    ${k} .utilization:hover .utilization-track { 
        outline: 2px solid var(--primaryMain); 
        outline-offset: 1px; 
    }
    
    ${k} .utilization-track { 
        display: flex; 
        width: clamp(120px, 15.5vw, 220px); 
        height: 12px; 
        overflow: hidden; 
        border-radius: 4px; 
        background: var(--borderColor);
    }
        
    ${k} .utilization-track > div { 
        display: block; 
        height: 100%; 
        min-width: 0; 
        flex: 0 0 auto; 
    }

    @media (max-width: 700px) {
        ${k} { gap: 12px; }
        ${k} .utilization-track { width: 110px; }
    }
`);

interface Segment {
    key: string;
    value: number;
    label: string;
    color: string;
}

interface Utilization {
    quota: number;
    used: number;
    segments: Segment[];
}

export function FileBrowserStatusBar({data}: {data: FileBrowserStatusData}): React.ReactNode {
    const navigate = useNavigate();
    const fileCount = data.directory.status.fileCount;
    const directoryCount = data.directory.status.directoryCount;
    const totalCount = fileCount != null && directoryCount != null ? fileCount + directoryCount : null;
    const selectedSize = data.selected.reduce((sum, file) =>
        sum + (file.status.sizeIncludingChildrenInBytes ?? file.status.sizeInBytes ?? 0), 0);
    const counts = data.selected.length > 0 && totalCount != null
        ? `${data.selected.length.toLocaleString()} / ${totalCount.toLocaleString()} items`
        : fileCount != null && directoryCount != null
            ? `${fileCount.toLocaleString()} files, ${directoryCount.toLocaleString()} directories`
            : "Counting files...";

    const utilization = buildUtilization(data);
    return <div className={StatusStyle}>
        <button className="utilization" onClick={() => navigate(AppRoutes.files.visualize(data.path))}>
            {utilization == null ? <span className="status-muted">Storage utilization unavailable</span> : <>
                <span className="utilization-track">
                    {utilization.segments.map(segment => <TooltipV2
                        key={segment.key}
                        tooltip={`${segment.label}: ${formatBytes(segment.value)}`}
                        triggerStyle={{
                            width: `${segment.value / utilization.quota * 100}%`,
                            height: "100%",
                            flex: "0 0 auto",
                        }}
                    >
                        <span
                            style={{
                                display: "block",
                                height: "100%",
                                width: "100%",
                                backgroundColor: segment.color
                            }}
                        />
                    </TooltipV2>)}
                </span>
                <span className="status-value">{sizeToString(utilization.used)} / {sizeToString(utilization.quota)}</span>
            </>}
        </button>
        <span className="status-spacer" />
        {data.selected.length > 0 ? <TooltipV2 tooltip="Cumulative size of the selected items">
            <span className="status-value"><span className="status-muted">Selected</span> {sizeToString(selectedSize)}</span>
        </TooltipV2> : null}
        <TooltipV2 tooltip="Files and folders directly contained in this directory.">
            <span className="status-value">{counts}</span>
        </TooltipV2>
    </div>;
}

function buildUtilization(data: FileBrowserStatusData): Utilization | null {
    const wallet = data.wallet;
    if (wallet == null) return null;
    const unitBytes = accountingUnitInBytes(wallet);
    if (unitBytes == null || wallet.quota <= 0) return null;

    const quota = wallet.quota * unitBytes;
    const accountingOwnUsage = Math.max(0, wallet.localUsage * unitBytes);
    const preciseOwnUsage = data.preciseOwnUsageBytes;
    const preciseUsageIsExpected = preciseOwnUsage != null &&
        preciseOwnUsage >= accountingOwnUsage &&
        preciseOwnUsage < (wallet.localUsage + 1) * unitBytes;
    const ownUsage = Math.min(quota, preciseUsageIsExpected ? preciseOwnUsage : accountingOwnUsage);
    const accountingChildUsage = Math.max(0, (wallet.totalUsage - wallet.localUsage) * unitBytes);
    const childUsage = Math.min(quota - ownUsage, accountingChildUsage);
    const totalUsage = ownUsage + childUsage;
    const folderUsage = Math.min(ownUsage, Math.max(0, data.directory.status.sizeIncludingChildrenInBytes ?? 0));
    const driveUsage = Math.min(ownUsage - folderUsage, Math.max(0, (data.drive.status.sizeIncludingChildrenInBytes ?? 0) - folderUsage));
    const remainingOwnUsage = ownUsage - folderUsage - driveUsage;

    const segments = [
        {key: "folder", value: folderUsage, label: "This folder", color: colorNames[0]},
        {key: "drive", value: driveUsage, label: "This drive (excluding this folder)", color: colorNames[1]},
        {key: "own", value: remainingOwnUsage, label: "Own usage (other drives)", color: colorNames[2]},
        {key: "children", value: childUsage, label: "Descending project use", color: colorNames[3]},
        {key: "free", value: quota - totalUsage, label: "Available quota", color: "textSecondary"},
    ].filter(segment => segment.value > 0);
    return {quota, used: totalUsage, segments};
}

function formatBytes(bytes: number): string {
    return sizeToString(bytes);
}

function accountingUnitInBytes(wallet: WalletV2): number | null {
    const unit = wallet.paysFor.accountingUnit;
    const factor = unit.floatingPoint ? 1 / 1_000_000 : 1;
    const decimal = ["B", "KB", "MB", "GB", "TB", "PB", "EB"];
    const binary = ["B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"];
    const decimalIndex = decimal.indexOf(unit.name);
    if (decimalIndex >= 0) return factor * Math.pow(1000, decimalIndex);
    const binaryIndex = binary.indexOf(unit.name);
    if (binaryIndex >= 0) return factor * Math.pow(1024, binaryIndex);
    return null;
}
