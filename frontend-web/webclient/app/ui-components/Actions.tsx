import {Button, Icon} from "@/ui-components";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {IconName} from "@/ui-components/Icon";
import type {ShortcutKey} from "@/ui-components/Operation";
import {ThemeColor} from "@/ui-components/theme";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {TooltipV2} from "@/ui-components/Tooltip";
import * as Heading from "@/ui-components/Heading";
import {injectStyle} from "@/Unstyled";
import {isLikelyMac} from "@/UtilityFunctions";
import * as React from "react";
import * as ReactDOM from "react-dom";
import {useCallback, useEffect, useId, useLayoutEffect, useRef, useState} from "react";

export type ActionEntry<T, C> = ActionItem<T, C> | "divider";

export interface CommonActionShortcut {
    code: string;
    key: string;
    modifier?: "primary";
}

export type ActionShortcut = ShortcutKey | CommonActionShortcut;

export interface ActionItem<T, C = undefined> {
    text: string | ((selected: T[], callbacks: C) => string);
    onClick: (selected: T[], callbacks: C) => void;
    enabled: (selected: T[], callbacks: C) => boolean | string;
    icon?: IconName;
    destructive?: boolean;
    confirmationText?: string | ((selected: T[], callbacks: C) => string);
    confirmationButtonText?: string | ((selected: T[], callbacks: C) => string);
    tag?: string;
    children?: ActionEntry<T, C>[];
    shortcut?: ActionShortcut;
}

export interface ResourceBrowserActions<T, C> {
    topbar?: ActionEntry<T, C>[];
    contextMenu?: ActionEntry<T, C>[];
    appearance?: (action: ActionItem<T, C>) => ActionAppearance | undefined;
    topbarMaxVisible?: number;
}

export interface ActionAppearance {
    color?: ThemeColor;
    iconRotation?: number;
    iconSize?: number;
    iconSpacing?: string;
    primary?: boolean;
    groupOnly?: boolean;
}

interface CommonActionProps<T, C> {
    actions: ActionEntry<T, C>[];
    selected: T[];
    callbacks: C;
    appearance?: (action: ActionItem<T, C>) => ActionAppearance | undefined;
}

export interface ActionMenuProps<T, C> extends CommonActionProps<T, C> {
    openFnRef?: React.RefObject<((left: number, top: number) => void) | null>;
    closeFnRef?: React.RefObject<(() => void) | null>;
    trigger?: React.ReactNode | null;
    onOpen?: () => void;
    onClose?: () => void;
    confirmationMode?: "panel" | "hold";
    disabled?: boolean;
}

export interface ActionBarProps<T, C> extends CommonActionProps<T, C> {
    hideShortcuts?: boolean;
    maxVisible?: number;
    compact?: boolean;
    enableShortcuts?: boolean;
}

interface EvaluatedAction<T, C> {
    action: ActionItem<T, C>;
    text: string;
    enabled: true | string;
}

type EvaluatedEntry<T, C> = EvaluatedAction<T, C> | "divider";

interface MenuLevel<T, C> {
    entries: EvaluatedEntry<T, C>[];
    activeIndex: number;
    x: number;
    y: number;
}

interface ConfirmationState<T, C> {
    entry: EvaluatedAction<T, C>;
    rootWidth: number;
    rootHeight: number;
}

const ActionMenuClass = injectStyle("action-menu", k => `
    ${k} {
        position: fixed;
        z-index: 10000;
        box-sizing: border-box;
        width: 240px;
        max-width: calc(100vw - 16px);
        padding: 4px;
        border: 1px solid var(--borderColor);
        border-radius: 7px;
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        box-shadow: var(--defaultShadow);
        overflow-y: auto;
        user-select: none;
    }

    ${k}[data-confirmation="true"] {
        padding: 12px;
        user-select: text;
    }
`);

const ActionMenuItemClass = injectStyle("action-menu-item", k => `
    ${k} {
        display: grid;
        align-items: center;
        box-sizing: border-box;
        height: 30px;
        padding: 0 6px;
        border-radius: 4px;
        gap: 5px;
        font-size: 13px;
        line-height: 1;
        cursor: pointer;
        outline: none;
    }

    ${k}[data-active="true"] {
        background: var(--rowHover);
    }

    ${k}[aria-disabled="true"] {
        color: var(--textSecondary);
        cursor: not-allowed;
        opacity: .65;
    }

    ${k}[data-destructive="true"] {
        color: var(--errorMain);
    }

    ${k} > .action-menu-text {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
         padding-bottom: 1px;
    }

    ${k} > .action-menu-shortcut {
        display: flex;
        align-items: center;
        gap: 3px;
        margin-left: 10px;
        color: var(--textSecondary);
        font-size: 11px;
    }
`);

const ActionDividerClass = injectStyle("action-menu-divider", k => `
    ${k} {
        height: 1px;
        margin: 4px 6px;
        background: var(--borderColor);
    }
`);

const ActionMenuTriggerClass = injectStyle("action-menu-trigger", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        cursor: pointer;
    }
`);

const ActionMenuTooltipTriggerClass = injectStyle("action-menu-tooltip-trigger", k => `
    ${k} {
        height: 30px;
    }
`);

const ActionBarTooltipTriggerClass = injectStyle("action-bar-tooltip-trigger", k => `
    ${k} {
        display: inline-flex;
        align-items: stretch;
    }
`);

const ActionBarClass = injectStyle("action-bar", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    ${k} > div {
        display: inline-flex;
        align-items: stretch;
    }

    ${k} button {
        white-space: nowrap;
    }

    ${k} button[data-keyboard-shortcuts="true"] {
        box-sizing: border-box;
        width: 35px;
        padding-left: 0;
        padding-right: 0;
    }

    ${k} > span[data-destructive-action="true"] {
        display: inline-flex;
    }

    ${k} > span[data-destructive-action="true"] button {
        min-width: 208px;
    }

    ${k}[data-compact="true"] > span[data-destructive-action="true"] button {
        min-width: 0;
        max-width: 150px;
    }
`);

const SplitDropdownTriggerClass = injectStyle("action-split-dropdown-trigger", k => `
    ${k} {
        position: relative;
        box-sizing: border-box;
        width: 35px;
        height: 35px;
        padding: 6px;
        border-radius: 0 8px 8px 0;
        background: var(--secondaryMain);
        color: var(--secondaryContrast);
        cursor: pointer;
        user-select: none;
        margin-left: -10px;
    }

    ${k}:hover {
        background: var(--secondaryDark);
    }

    ${k}[data-disabled="true"] {
        opacity: .25;
        cursor: not-allowed;
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--secondaryMain);
    }

    ${k} > svg {
        position: absolute;
        right: 10px;
        bottom: 9px;
        width: 16px;
        height: 16px;
    }
`);

const ConfirmationPanelClass = injectStyle("action-menu-confirmation", k => `
    ${k} {
        display: flex;
        flex-direction: column;
    }

    ${k} > .action-menu-confirmation-content {
        flex: 1;
    }

    ${k} > .action-menu-confirmation-content > h4 {
        margin: 0;
    }

    ${k} > .action-menu-confirmation-content > p {
        margin: 6px 0 12px;
        line-height: 1.5;
    }

    ${k} > .action-menu-confirmation-toolbar {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin: 0 -12px -12px;
        padding: 8px 12px;
        background: var(--dialogToolbar);
    }
`);

function actionTag(text: string, tag?: string): string {
    return tag ?? `${text.replace(/\./g, "").replace(/ /g, "_")}-action`;
}

function actionText<T, C>(action: ActionItem<T, C>, selected: T[], callbacks: C): string {
    return typeof action.text === "string" ? action.text : action.text(selected, callbacks);
}

function confirmationText<T, C>(
    value: string | ((selected: T[], callbacks: C) => string) | undefined,
    selected: T[],
    callbacks: C,
    fallback: string,
): string {
    if (value == null) return fallback;
    return typeof value === "string" ? value : value(selected, callbacks);
}

function defaultConfirmationText(actionText: string, selectedCount: number): string {
    const verb = actionText.trim().split(/\s+/, 1)[0]?.toLowerCase() || "perform";
    if (selectedCount === 0) return `Are you sure you want to ${actionText.toLowerCase()}?`;
    const target = selectedCount === 1 ? "this item" : `these ${selectedCount} items`;
    return `Are you sure you want to ${verb} ${target}?`;
}

function evaluateActions<T, C>(actions: ActionEntry<T, C>[], selected: T[], callbacks: C): EvaluatedEntry<T, C>[] {
    const result: EvaluatedEntry<T, C>[] = [];
    for (const entry of actions) {
        if (entry === "divider") {
            if (result.length && result[result.length - 1] !== "divider") result.push(entry);
            continue;
        }

        const enabled = entry.enabled(selected, callbacks);
        if (enabled === false) continue;
        result.push({
            action: entry,
            text: actionText(entry, selected, callbacks),
            enabled: typeof enabled === "string" ? enabled : true,
        });
    }
    if (result[result.length - 1] === "divider") result.pop();
    return result;
}

function firstEnabledIndex<T, C>(entries: EvaluatedEntry<T, C>[]): number {
    return entries.findIndex(entry => entry !== "divider" && entry.enabled === true);
}

function nextEnabledIndex<T, C>(entries: EvaluatedEntry<T, C>[], current: number, delta: number): number {
    if (!entries.length) return -1;
    for (let step = 1; step <= entries.length; step++) {
        const idx = (current + delta * step + entries.length) % entries.length;
        const entry = entries[idx];
        if (entry !== "divider" && entry.enabled === true) return idx;
    }
    return -1;
}

function Shortcut({shortcut}: {shortcut: ActionShortcut}): React.ReactNode {
    if (typeof shortcut !== "string") {
        return <>
            {shortcut.modifier === "primary" ?
                <span className={ShortcutClass}>{isLikelyMac ? "⌘" : "Ctrl"}</span> : null}
            <span className={ShortcutClass}>{shortcut.key}</span>
        </>;
    }
    return <>
        <span className={ShortcutClass}>{isLikelyMac ? "⌥" : "alt"}</span>
        <span className={ShortcutClass}>{shortcut.replace("Key", "")}</span>
    </>;
}

function matchesShortcut(shortcut: ActionShortcut | undefined, event: KeyboardEvent): boolean {
    if (!shortcut) return false;
    if (typeof shortcut === "string") {
        return event.altKey && !event.ctrlKey && !event.metaKey && shortcut === event.code;
    }
    const hasPrimaryModifier = isLikelyMac ? event.metaKey : event.ctrlKey;
    return shortcut.code === event.code &&
        (shortcut.modifier === "primary" ? hasPrimaryModifier && !event.altKey :
            !event.altKey && !event.ctrlKey && !event.metaKey);
}

function clampRootPosition(x: number, y: number, entryCount: number): [number, number] {
    const margin = 8;
    const width = Math.min(240, window.innerWidth - margin * 2);
    const height = Math.min(entryCount * 30 + 8, window.innerHeight - margin * 2);
    return [
        Math.max(margin, Math.min(x, window.innerWidth - width - margin)),
        Math.max(margin, Math.min(y, window.innerHeight - height - margin)),
    ];
}

function submenuPosition(anchor: HTMLElement, entryCount: number): [number, number] {
    const margin = 8;
    const rect = anchor.getBoundingClientRect();
    const width = Math.min(240, window.innerWidth - margin * 2);
    const height = Math.min(entryCount * 30 + 8, window.innerHeight - margin * 2);
    const x = rect.right + width <= window.innerWidth - margin ? rect.right + 2 : rect.left - width - 2;
    return [Math.max(margin, x), Math.max(margin, Math.min(rect.top - 4, window.innerHeight - height - margin))];
}

function MenuSurface<T, C>({
    level,
    depth,
    owner,
    setActive,
    openSubmenu,
    scheduleCloseSubmenus,
    cancelScheduledClose,
    activate,
    runAction,
    confirmationMode,
    appearance,
}: {
    level: MenuLevel<T, C>;
    depth: number;
    owner: string;
    setActive: (depth: number, index: number) => void;
    openSubmenu: (depth: number, index: number, anchor: HTMLElement) => void;
    scheduleCloseSubmenus: (depth: number) => void;
    cancelScheduledClose: () => void;
    activate: (entry: EvaluatedAction<T, C>) => void;
    runAction: (entry: EvaluatedAction<T, C>) => void;
    confirmationMode: "panel" | "hold";
    appearance?: (action: ActionItem<T, C>) => ActionAppearance | undefined;
}): React.ReactNode {
    const hasIcons = level.entries.some(entry => entry !== "divider" && !!entry.action.icon);
    const hasSubmenus = level.entries.some(entry => entry !== "divider" && !!entry.action.children?.length);
    const hasShortcuts = level.entries.some(entry => entry !== "divider" && !!entry.action.shortcut);
    const gridTemplateColumns = `${hasIcons ? "24px " : ""}minmax(0, 1fr)${hasShortcuts ? " auto" : ""}${hasSubmenus ? " 14px" : ""}`;
    return <div
        className={ActionMenuClass}
        role="menu"
        tabIndex={-1}
        data-menu-owner={owner}
        style={{left: level.x, top: level.y, maxHeight: "calc(100vh - 16px)"}}
        onMouseEnter={cancelScheduledClose}
        onMouseLeave={() => scheduleCloseSubmenus(depth - 1)}
    >
        {level.entries.map((entry, index) => {
            if (entry === "divider") return <div className={ActionDividerClass} role="separator" key={`divider-${index}`} />;
            const hasChildren = !!entry.action.children?.length;
            if (entry.action.destructive && confirmationMode === "hold") {
                const button = <div data-tag={actionTag(entry.text, entry.action.tag)}>
                    <ConfirmationButton
                        actionText={entry.text}
                        icon={entry.action.icon}
                        color="errorMain"
                        disabled={entry.enabled !== true}
                        fullWidth
                        height={30}
                        onAction={async () => runAction(entry)}
                    />
                </div>;
                if (typeof entry.enabled !== "string") return React.cloneElement(button, {key: `${entry.text}-${index}`});
                return <TooltipV2
                    tooltip={entry.enabled}
                    side="right"
                    contentWidth={260}
                    triggerClassName={ActionMenuTooltipTriggerClass}
                    key={`${entry.text}-${index}`}
                >{button}</TooltipV2>;
            }
            const item = <div
                className={ActionMenuItemClass}
                role="menuitem"
                tabIndex={-1}
                aria-disabled={entry.enabled !== true}
                data-active={level.activeIndex === index}
                data-destructive={entry.action.destructive === true}
                data-menu-index={index}
                data-tag={actionTag(entry.text, entry.action.tag)}
                key={`${entry.text}-${index}`}
                style={{gridTemplateColumns}}
                onMouseEnter={event => {
                    cancelScheduledClose();
                    setActive(depth, index);
                    if (entry.enabled === true && hasChildren) openSubmenu(depth, index, event.currentTarget);
                    else scheduleCloseSubmenus(depth);
                }}
                onClick={event => {
                    event.stopPropagation();
                    if (entry.enabled !== true) return;
                    if (hasChildren) openSubmenu(depth, index, event.currentTarget);
                    else activate(entry);
                }}
            >
                {hasIcons ? <span>{entry.action.icon ? <Icon name={entry.action.icon} size={16} /> : null}</span> : null}
                <span className="action-menu-text">{entry.text}</span>
                {hasShortcuts ? <span className="action-menu-shortcut">
                    {entry.action.shortcut ? <Shortcut shortcut={entry.action.shortcut} /> : null}
                </span> : null}
                {hasSubmenus ? <span>{hasChildren ? <Icon name="heroChevronDown" rotation={-90} size={12} /> : null}</span> : null}
            </div>;
            if (typeof entry.enabled !== "string") return item;
            return <TooltipV2
                tooltip={entry.enabled}
                side="right"
                contentWidth={260}
                triggerClassName={ActionMenuTooltipTriggerClass}
                key={`${entry.text}-${index}`}
            >{item}</TooltipV2>;
        })}
    </div>;
}

export function ActionMenu<T, C>(props: ActionMenuProps<T, C>): React.ReactNode {
    const [levels, setLevels] = useState<MenuLevel<T, C>[]>([]);
    const [confirmation, setConfirmation] = useState<ConfirmationState<T, C> | null>(null);
    const propsRef = useRef(props);
    const triggerRef = useRef<HTMLDivElement>(null);
    const cancelRef = useRef<HTMLButtonElement>(null);
    const confirmRef = useRef<HTMLButtonElement>(null);
    const closeTimer = useRef(-1);
    const owner = useId();
    propsRef.current = props;

    const close = useCallback(() => {
        window.clearTimeout(closeTimer.current);
        setLevels([]);
        setConfirmation(null);
        propsRef.current.onClose?.();
    }, []);

    const open = useCallback((left: number, top: number) => {
        const current = propsRef.current;
        const entries = evaluateActions(current.actions, current.selected, current.callbacks);
        if (!entries.length) return;
        const [x, y] = clampRootPosition(left, top, entries.length);
        setConfirmation(null);
        setLevels([{entries, activeIndex: firstEnabledIndex(entries), x, y}]);
        current.onOpen?.();
    }, []);

    useLayoutEffect(() => {
        if (props.openFnRef) props.openFnRef.current = open;
        if (props.closeFnRef) props.closeFnRef.current = close;
        return () => {
            if (props.openFnRef?.current === open) props.openFnRef.current = null;
            if (props.closeFnRef?.current === close) props.closeFnRef.current = null;
        };
    }, [props.openFnRef, props.closeFnRef, open, close]);

    useEffect(() => () => window.clearTimeout(closeTimer.current), []);

    const setActive = useCallback((depth: number, index: number) => {
        setLevels(current => current.map((level, idx) => idx === depth ? {...level, activeIndex: index} : level));
    }, []);

    const cancelScheduledClose = useCallback(() => window.clearTimeout(closeTimer.current), []);
    const scheduleCloseSubmenus = useCallback((depth: number) => {
        window.clearTimeout(closeTimer.current);
        closeTimer.current = window.setTimeout(() => {
            setLevels(current => current.slice(0, Math.max(1, depth + 1)));
        }, 250);
    }, []);

    const openSubmenu = useCallback((depth: number, index: number, anchor: HTMLElement) => {
        const parent = levels[depth];
        const entry = parent?.entries[index];
        if (!entry || entry === "divider" || entry.enabled !== true || !entry.action.children?.length) return;
        const current = propsRef.current;
        const entries = evaluateActions(entry.action.children, current.selected, current.callbacks);
        if (!entries.length) return;
        const [x, y] = submenuPosition(anchor, entries.length);
        setLevels(existing => [
            ...existing.slice(0, depth + 1).map((level, idx) => idx === depth ? {...level, activeIndex: index} : level),
            {entries, activeIndex: firstEnabledIndex(entries), x, y},
        ]);
    }, [levels]);

    const runAction = useCallback((entry: EvaluatedAction<T, C>) => {
        const current = propsRef.current;
        entry.action.onClick(current.selected, current.callbacks);
        close();
    }, [close]);

    const activate = useCallback((entry: EvaluatedAction<T, C>) => {
        if (entry.action.destructive) {
            if (propsRef.current.confirmationMode !== "hold") {
                const root = document.querySelector<HTMLElement>(`[data-menu-owner="${owner}"]`);
                const bounds = root?.getBoundingClientRect();
                setConfirmation({
                    entry,
                    rootWidth: bounds?.width ?? 240,
                    rootHeight: bounds?.height ?? 0,
                });
            }
            return;
        }
        runAction(entry);
    }, [runAction, owner]);

    useEffect(() => {
        if (!levels.length) return;
        const menus = document.querySelectorAll<HTMLElement>(`[data-menu-owner="${owner}"]`);
        const activeSurface = menus[menus.length - 1];
        if (confirmation) confirmRef.current?.focus();
        else if (activeSurface) {
            const activeLevel = levels[levels.length - 1];
            const activeItem = activeSurface.querySelector<HTMLElement>(`[data-menu-index="${activeLevel.activeIndex}"]`);
            (activeItem ?? activeSurface).focus();
        }
        const onPointerDown = (event: PointerEvent) => {
            const target = event.target as Node | null;
            const menus = document.querySelectorAll<HTMLElement>(`[data-menu-owner="${owner}"]`);
            if (target && Array.from(menus).some(menu => menu.contains(target))) return;
            if (triggerRef.current?.contains(target)) return;
            close();
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (confirmation) {
                if (event.key === "Escape") {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    setConfirmation(null);
                } else if (event.key === "Tab") {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    const target = event.shiftKey ? cancelRef.current : confirmRef.current;
                    if (document.activeElement === target) {
                        (event.shiftKey ? confirmRef.current : cancelRef.current)?.focus();
                    } else {
                        target?.focus();
                    }
                }
                return;
            }

            const depth = levels.length - 1;
            const level = levels[depth];
            const active = level.entries[level.activeIndex];
            let handled = true;
            switch (event.key) {
                case "ArrowDown":
                    setActive(depth, nextEnabledIndex(level.entries, level.activeIndex, 1));
                    break;
                case "ArrowUp":
                    setActive(depth, nextEnabledIndex(level.entries, level.activeIndex, -1));
                    break;
                case "Tab":
                    setActive(depth, nextEnabledIndex(level.entries, level.activeIndex, event.shiftKey ? -1 : 1));
                    break;
                case "ArrowRight": {
                    if (active !== "divider" && active?.action.children?.length) {
                        const element = document.querySelectorAll<HTMLElement>(`[data-menu-owner="${owner}"]`)[depth]
                            ?.querySelector<HTMLElement>(`[data-menu-index="${level.activeIndex}"]`);
                        if (element) openSubmenu(depth, level.activeIndex, element);
                    }
                    break;
                }
                case "ArrowLeft":
                    if (depth > 0) setLevels(current => current.slice(0, -1));
                    else handled = false;
                    break;
                case "Enter":
                case " ":
                    if (active !== "divider" && active?.enabled === true) {
                        if (active.action.children?.length) {
                            const element = document.querySelectorAll<HTMLElement>(`[data-menu-owner="${owner}"]`)[depth]
                                ?.querySelector<HTMLElement>(`[data-menu-index="${level.activeIndex}"]`);
                            if (element) openSubmenu(depth, level.activeIndex, element);
                        } else activate(active);
                    }
                    break;
                case "Escape":
                    if (depth > 0) setLevels(current => current.slice(0, -1));
                    else close();
                    break;
                default: {
                    const shortcut = level.entries.find(entry => entry !== "divider" && entry.enabled === true &&
                        matchesShortcut(entry.action.shortcut, event));
                    if (shortcut && shortcut !== "divider") activate(shortcut);
                    else handled = false;
                    break;
                }
            }
            if (handled) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
        };
        document.addEventListener("pointerdown", onPointerDown, true);
        document.addEventListener("keydown", onKeyDown, true);
        return () => {
            document.removeEventListener("pointerdown", onPointerDown, true);
            document.removeEventListener("keydown", onKeyDown, true);
        };
    }, [levels, confirmation, activate, close, openSubmenu, setActive]);

    const trigger = props.trigger === undefined ?
        <Icon name="ellipsis" rotation={90} size="1em" /> : props.trigger;
    const portal = levels.length ? ReactDOM.createPortal(<>
        {confirmation ? <div
            className={`${ActionMenuClass} ${ConfirmationPanelClass}`}
            data-confirmation="true"
            data-menu-owner={owner}
            role="alertdialog"
            style={{
                left: levels[0].x,
                top: levels[0].y,
                width: confirmation.rootWidth,
                minHeight: confirmation.rootHeight,
            }}
            onClick={event => event.stopPropagation()}
        >
            <div className="action-menu-confirmation-content">
                <Heading.h4>{confirmation.entry.text}</Heading.h4>
                <p>{confirmationText(
                    confirmation.entry.action.confirmationText,
                    props.selected,
                    props.callbacks,
                    defaultConfirmationText(confirmation.entry.text, props.selected.length),
                )}</p>
            </div>
            <div className="action-menu-confirmation-toolbar">
                <Button btnRef={cancelRef} color="secondaryMain" onClick={() => setConfirmation(null)}>Cancel</Button>
                <Button btnRef={confirmRef} color="errorMain" onClick={() => runAction(confirmation.entry)}>
                    {confirmationText(
                        confirmation.entry.action.confirmationButtonText,
                        props.selected,
                        props.callbacks,
                        confirmation.entry.text,
                    )}
                </Button>
            </div>
        </div> : levels.map((level, depth) => <MenuSurface
            key={depth}
            level={level}
            depth={depth}
            owner={owner}
            setActive={setActive}
            openSubmenu={openSubmenu}
            scheduleCloseSubmenus={scheduleCloseSubmenus}
            cancelScheduledClose={cancelScheduledClose}
            activate={activate}
            runAction={runAction}
            confirmationMode={props.confirmationMode ?? "panel"}
            appearance={props.appearance}
        />)}
    </>, document.body) : null;

    return <>
        {trigger === null ? null : <div
            className={ActionMenuTriggerClass}
            ref={triggerRef}
            role="button"
            tabIndex={0}
            onClick={event => {
                event.preventDefault();
                event.stopPropagation();
                if (props.disabled) return;
                if (levels.length) {
                    close();
                    return;
                }
                const rect = event.currentTarget.getBoundingClientRect();
                open(rect.right, rect.bottom + 2);
            }}
            onKeyDown={event => {
                if (event.key !== "Enter" && event.key !== " ") return;
                event.preventDefault();
                if (props.disabled) return;
                const rect = event.currentTarget.getBoundingClientRect();
                open(rect.right, rect.bottom + 2);
            }}
        >{trigger}</div>}
        {portal}
    </>;
}

function ActionBarButton<T, C>({entry, props, split = false}: {
    entry: EvaluatedAction<T, C>;
    props: ActionBarProps<T, C>;
    split?: boolean;
}): React.ReactNode {
    const appearance = props.appearance?.(entry.action);
    const disabled = entry.enabled !== true;
    const content = <>
        {entry.action.icon ? <Icon
            name={entry.action.icon}
            rotation={appearance?.iconRotation}
            size={appearance?.iconSize ?? 18}
            mr={entry.text ? appearance?.iconSpacing ?? "5px" : undefined}
        /> : null}
        {entry.text ? <span>{entry.text}</span> : null}
        {!entry.action.destructive && !props.hideShortcuts && entry.action.shortcut ?
            <span style={{display: "inline-flex", gap: "3px", marginLeft: "8px"}}>
                <Shortcut shortcut={entry.action.shortcut} />
            </span> : null}
    </>;

    let result: React.ReactNode;
    if (entry.action.destructive) {
        result = <span
            data-tag={actionTag(entry.text, entry.action.tag)}
            data-destructive-action="true"
            onPointerDown={event => event.stopPropagation()}
            onClick={event => event.stopPropagation()}
        >
            <ConfirmationButton
                actionText={entry.text}
                icon={entry.action.icon}
                iconSize={appearance?.iconSize}
                color={appearance?.color ?? "errorMain"}
                disabled={disabled}
                onAction={async () => entry.action.onClick(props.selected, props.callbacks)}
            />
        </span>;
    } else {
        result = <Button
            color={appearance?.color ?? "secondaryMain"}
            attachedLeft={split}
            disabled={disabled}
            data-tag={actionTag(entry.text, entry.action.tag)}
            data-keyboard-shortcuts={entry.text === "" && entry.action.icon === "keyboardSolid"}
            onClick={event => {
                event.stopPropagation();
                if (entry.enabled === true) entry.action.onClick(props.selected, props.callbacks);
            }}
        >{content}</Button>;
    }
    if (typeof entry.enabled !== "string") return result;
    return <TooltipV2
        tooltip={entry.enabled}
        contentWidth={260}
        triggerClassName={ActionBarTooltipTriggerClass}
    >{result}</TooltipV2>;
}

export function ActionBar<T, C>(props: ActionBarProps<T, C>): React.ReactNode {
    const entries = evaluateActions(props.actions, props.selected, props.callbacks)
        .filter((entry): entry is EvaluatedAction<T, C> => entry !== "divider");
    let visibleEntries = entries;
    const generatedAppearance = new Map<ActionItem<T, C>, ActionAppearance>();
    if (props.maxVisible != null && entries.length > props.maxVisible) {
        const pinned = entries.filter(entry => props.appearance?.(entry.action)?.groupOnly);
        const primary = entries.filter(entry => !props.appearance?.(entry.action)?.groupOnly &&
            props.appearance?.(entry.action)?.primary);
        const remaining = entries.filter(entry => !props.appearance?.(entry.action)?.groupOnly &&
            !props.appearance?.(entry.action)?.primary);
        const directlyVisible = remaining.slice(0, Math.max(0, props.maxVisible - primary.length));
        const overflow = remaining.slice(directlyVisible.length);
        if (overflow.length) {
            const overflowAction: ActionItem<T, C> = {
                text: "",
                icon: "ellipsis",
                enabled: () => true,
                onClick: () => undefined,
                children: overflow.map(entry => entry.action),
            };
            generatedAppearance.set(overflowAction, {groupOnly: true, iconRotation: 90, iconSize: 16, iconSpacing: "8px"});
            visibleEntries = [...pinned, ...primary, ...directlyVisible, {
                action: overflowAction,
                text: "",
                enabled: true,
            }];
        }
    }
    const originalAppearance = props.appearance;
    const adjustedProps = {
        ...props,
        appearance: (action: ActionItem<T, C>) => generatedAppearance.get(action) ?? originalAppearance?.(action),
    };

    useEffect(() => {
        if (props.enableShortcuts === false) return;
        const onKeyDown = (event: KeyboardEvent) => {
            const entry = entries.find(candidate =>
                candidate.enabled === true && !candidate.action.destructive &&
                matchesShortcut(candidate.action.shortcut, event)
            );
            if (!entry) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            entry.action.onClick(props.selected, props.callbacks);
        };
        document.addEventListener("keydown", onKeyDown, true);
        return () => document.removeEventListener("keydown", onKeyDown, true);
    }, [entries, props.selected, props.callbacks, props.enableShortcuts]);

    return <div className={ActionBarClass} data-compact={props.compact}>
        {visibleEntries.map((entry, index) => {
            const appearance = adjustedProps.appearance(entry.action);
            const children = entry.action.children ?? [];
            if (!children.length) return <ActionBarButton entry={entry} props={adjustedProps} key={`${entry.text}-${index}`} />;

            if (appearance?.groupOnly) {
                const trigger = <Button color={appearance.color ?? "secondaryMain"} disabled={entry.enabled !== true}>
                    {entry.action.icon ? <Icon
                        name={entry.action.icon}
                        rotation={appearance.iconRotation}
                        size={appearance.iconSize ?? 18}
                        mr={entry.text ? appearance.iconSpacing ?? "5px" : undefined}
                    /> : null}
                    {entry.text ? <span>{entry.text}</span> : null}
                </Button>;
                return <ActionMenu
                    actions={children}
                    selected={adjustedProps.selected}
                    callbacks={adjustedProps.callbacks}
                    appearance={adjustedProps.appearance}
                    confirmationMode="panel"
                    disabled={entry.enabled !== true}
                    trigger={typeof entry.enabled === "string" ? <TooltipV2
                        tooltip={entry.enabled}
                        contentWidth={260}
                        triggerClassName={ActionBarTooltipTriggerClass}
                    >{trigger}</TooltipV2> : trigger}
                    key={`${entry.text}-${index}`}
                />;
            }

            return <div key={`${entry.text}-${index}`}>
                <ActionBarButton entry={entry} props={props} split />
                <ActionMenu
                    actions={children}
                    selected={props.selected}
                    callbacks={props.callbacks}
                    appearance={props.appearance}
                    confirmationMode="hold"
                    disabled={entry.enabled !== true}
                    trigger={<span className={SplitDropdownTriggerClass} data-disabled={entry.enabled !== true}>
                        <Icon name="heroChevronDown" size={16} />
                    </span>}
                />
            </div>;
        })}
    </div>;
}
