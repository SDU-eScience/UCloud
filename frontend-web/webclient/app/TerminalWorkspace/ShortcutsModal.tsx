import * as React from "react";
import {useEffect} from "react";
import {injectStyle} from "@/Unstyled";
import Icon from "@/ui-components/Icon";

interface ShortcutRow {
    action: string;
    primary: string;
    alternatives: string;
}

interface ShortcutGroup {
    title: string;
    rows: ShortcutRow[];
}

function isMacPlatform(): boolean {
    if (typeof navigator === "undefined") return false;
    return /Mac|iPhone|iPad/i.test(navigator.platform);
}

function buildShortcutGroups(isMac: boolean): ShortcutGroup[] {
    const mod = isMac ? "⌘+⌥" : "Ctrl+Alt";
    const shiftMod = isMac ? "⌘+⌥+Shift" : "Ctrl+Alt+Shift";
    const copyMod = isMac ? "⌘" : "Ctrl+Shift";
    const zoomMod = isMac ? "⌘" : "Ctrl";

    return [
        {
            title: "Panes",
            rows: [
                {action: "Move focus between panes", primary: `${mod}+Arrows`, alternatives: `${mod}+H/J/K/L`},
                {action: "Split pane in arrow direction", primary: "Alt+Shift+Arrows", alternatives: `${mod}+D (right), ${shiftMod}+D (down)`},
                {action: "Resize focused pane", primary: `${shiftMod}+Arrows`, alternatives: `${shiftMod}+H/J/K/L, drag divider`},
                {action: "Reset split ratio to 50/50", primary: "Double-click divider", alternatives: ""},
                {action: "Maximize / restore focused pane", primary: `${mod}+Enter`, alternatives: ""},
                {action: "Close focused pane", primary: `${mod}+W`, alternatives: ""},
                {action: "Rename focused pane", primary: `${shiftMod}+R`, alternatives: "Double-click pane title"},
            ],
        },
        {
            title: "Tabs",
            rows: [
                {action: "New tab", primary: `${mod}+T`, alternatives: `${mod}+= / +`},
                {action: "Previous / next tab", primary: `${mod}+PageUp / PageDown`, alternatives: isMac ? "⌘+⌥+[ / ], Alt+Shift+PageUp / PageDown" : "Alt+Shift+PageUp / PageDown"},
                {action: "Close entire tab", primary: `${shiftMod}+W`, alternatives: "Middle-click tab"},
                {action: "Rename active tab", primary: `${mod}+R`, alternatives: "Tab context menu"},
            ],
        },
        {
            title: "Sessions",
            rows: [
                {action: "Open session switcher", primary: `${mod}+K`, alternatives: "Click top-left session bar"},
                {action: "Show keyboard shortcuts", primary: `${mod}+/`, alternatives: "Click ? in top bar"},
            ],
        },
        {
            title: "Terminal",
            rows: [
                {action: "Search scrollback", primary: `${mod}+F`, alternatives: isMac ? "⌘+F" : "Ctrl+Shift+F"},
                {action: "Copy / paste", primary: `${copyMod}+C / V`, alternatives: ""},
                {action: "Font size zoom in / out / reset", primary: `${zoomMod}++ / - / 0`, alternatives: ""},
            ],
        },
    ];
}

export function TerminalShortcutsModal({
    open,
    onClose,
}: {
    open: boolean;
    onClose: () => void;
}): React.ReactNode {
    useEffect(() => {
        if (!open) return;
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                event.stopPropagation();
                onClose();
            }
        };
        window.addEventListener("keydown", onKeyDown, true);
        return () => window.removeEventListener("keydown", onKeyDown, true);
    }, [open, onClose]);

    if (!open) return null;

    const groups = buildShortcutGroups(isMacPlatform());

    return <div className={ModalBackdropClass} onClick={onClose}>
        <div className="modal-card" onClick={e => e.stopPropagation()} role="dialog" aria-modal="true" aria-label="Keyboard shortcuts">
            <div className="modal-header">
                <h3>Keyboard shortcuts</h3>
                <button type="button" className="close-btn" onClick={onClose} aria-label="Close shortcuts">
                    <Icon name="close" size={14} />
                </button>
            </div>
            <div className="modal-body">
                {groups.map(group => <div key={group.title} className="shortcut-group">
                    <h4>{group.title}</h4>
                    <table>
                        <tbody>
                            {group.rows.map(row => <tr key={row.action}>
                                <td className="action-col">{row.action}</td>
                                <td className="key-col"><kbd>{row.primary}</kbd></td>
                                <td className="alt-col">{row.alternatives ? <span>{row.alternatives}</span> : null}</td>
                            </tr>)}
                        </tbody>
                    </table>
                </div>)}
            </div>
        </div>
    </div>;
}

const ModalBackdropClass = injectStyle("terminal-shortcuts-modal", k => `
    ${k} {
        position: fixed;
        inset: 0;
        z-index: 60;
        background: rgba(0, 0, 0, 0.55);
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 20px;
        font-family: var(--sansSerif);
    }

    ${k} .modal-card {
        width: min(680px, 100%);
        max-height: 82vh;
        display: flex;
        flex-direction: column;
        background: var(--backgroundCard);
        color: var(--textPrimary);
        border: 1px solid var(--borderColor);
        border-radius: 10px;
        box-shadow: 0 18px 48px rgba(0, 0, 0, 0.4);
        overflow: hidden;
    }

    ${k} .modal-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 16px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} .modal-header h3 {
        margin: 0;
        font-size: 15px;
        font-weight: 600;
    }

    ${k} .close-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
    }

    ${k} .close-btn:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k} .modal-body {
        padding: 12px 16px 18px;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    ${k} .shortcut-group h4 {
        margin: 0 0 6px;
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--textSecondary);
    }

    ${k} table {
        width: 100%;
        border-collapse: collapse;
        font-size: 13px;
    }

    ${k} td {
        padding: 6px 8px;
        border-bottom: 1px solid var(--borderColor);
        vertical-align: middle;
    }

    ${k} tr:last-child td {
        border-bottom: 0;
    }

    ${k} .action-col {
        width: 42%;
    }

    ${k} .key-col {
        width: 28%;
        white-space: nowrap;
    }

    ${k} .alt-col {
        width: 30%;
        color: var(--textSecondary);
        font-size: 12px;
    }

    ${k} kbd {
        display: inline-block;
        padding: 2px 7px;
        border-radius: 5px;
        border: 1px solid var(--borderColor);
        background: var(--background);
        font-family: "JetBrains Mono", ui-monospace, monospace;
        font-size: 11.5px;
        color: var(--textPrimary);
    }
`);
