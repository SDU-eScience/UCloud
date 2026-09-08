// Keyboard shortcuts for the creator editor
// =====================================================================================================================
// All creator shortcuts use the Primary+Alt chord (Primary is Cmd on macOS, Ctrl elsewhere) except
// save, which uses Primary+S. The letters are read from event.code so they are layout independent.
//
// The view shortcuts are toggles: pressing the shortcut for the active view returns to the editor
// view. This lets a full-screen view such as YAML exit with the same key that opened it. The editor
// return key E works from every non-editor view.
//
// The properties island ends with a single discovery hint that tells the user which chord reveals
// the rest. While the chord is held, every section title shows its keycap.

import * as React from "react";
import {createContext, useContext, useEffect, useState} from "react";
import {createKeyboardShortcut, isLikelyMac} from "@/UtilityFunctions";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {injectStyle} from "@/Unstyled";
import {focusFirstNavigationTarget, FORM_NAVIGATION_SELECTOR} from "@/Applications/KeyboardNavigation";

export type CreatorSectionKey = "M" | "C" | "F" | "N" | "A";

export interface CreatorShortcutHandlers {
    onToggleYaml: () => void;
    onToggleInvocation: () => void;
    onTogglePreview: () => void;
    onReturnToEditor: () => void;
    onSave: () => void;
    onFocusSection: (key: CreatorSectionKey) => void;
}

export const CREATOR_SECTION_TARGETS: Record<CreatorSectionKey, string> = {
    M: "creator-section-metadata",
    C: "creator-section-software",
    F: "creator-section-features",
    N: "creator-section-connectivity",
    A: "creator-section-add-parameter",
};

const CREATOR_SECTION_KEYS: Record<string, CreatorSectionKey> = {
    KeyM: "M",
    KeyC: "C",
    KeyF: "F",
    KeyN: "N",
    KeyA: "A",
};

export function creatorFocusSection(key: CreatorSectionKey): void {
    const target = document.getElementById(CREATOR_SECTION_TARGETS[key]);
    if (!target) return;
    const collapsed = target.getAttribute("data-collapsed") === "true";
    window.requestAnimationFrame(() => {
        target.scrollIntoView({block: "center", behavior: "smooth"});
        if (collapsed) {
            target.querySelector<HTMLElement>("[data-panel-section-toggle]")?.focus();
            return;
        }
        focusFirstNavigationTarget(target, FORM_NAVIGATION_SELECTOR);
    });
}

const CreatorShortcutHintsContext = createContext(false);

export function useCreatorShortcutsHints(): boolean {
    return useContext(CreatorShortcutHintsContext);
}

export function CreatorShortcutHintsProvider(props: {visible: boolean; children: React.ReactNode}): React.ReactNode {
    return (
        <CreatorShortcutHintsContext.Provider value={props.visible}>
            {props.children}
        </CreatorShortcutHintsContext.Provider>
    );
}

export function useCreatorShortcuts(
    view: string | null,
    enabled: boolean,
    handlers: CreatorShortcutHandlers,
): boolean {
    const [hintsVisible, setHintsVisible] = useState(false);
    const handlersRef = React.useRef(handlers);
    handlersRef.current = handlers;

    useEffect(() => {
        if (!enabled || view == null) return;
        const primaryPressed = (event: KeyboardEvent) => isLikelyMac ? event.metaKey : event.ctrlKey;
        const onKeyDown = (event: KeyboardEvent) => {
            if (document.querySelector(".ReactModal__Overlay")) return;
            if (event.defaultPrevented) return;
            if (primaryPressed(event) && !event.altKey && !event.shiftKey && event.code === "KeyS") {
                event.preventDefault();
                handlersRef.current.onSave();
                return;
            }
            if (!event.altKey || !primaryPressed(event) || event.shiftKey) return;
            setHintsVisible(true);
            if (event.code === "KeyE") {
                if (view !== "editor") {
                    event.preventDefault();
                    event.stopPropagation();
                    handlersRef.current.onReturnToEditor();
                }
                return;
            }
            if (event.code === "KeyY") {
                event.preventDefault();
                event.stopPropagation();
                handlersRef.current.onToggleYaml();
            } else if (event.code === "KeyI") {
                event.preventDefault();
                event.stopPropagation();
                handlersRef.current.onToggleInvocation();
            } else if (event.code === "KeyP") {
                event.preventDefault();
                event.stopPropagation();
                handlersRef.current.onTogglePreview();
            } else if (view === "editor") {
                const section = CREATOR_SECTION_KEYS[event.code];
                if (section) {
                    event.preventDefault();
                    event.stopPropagation();
                    handlersRef.current.onFocusSection(section);
                }
            }
        };
        const onModifierChange = (event: KeyboardEvent) => {
            setHintsVisible(event.altKey && primaryPressed(event));
        };
        const hideHints = () => setHintsVisible(false);

        window.addEventListener("keydown", onKeyDown, true);
        window.addEventListener("keyup", onModifierChange, true);
        window.addEventListener("blur", hideHints);
        return () => {
            window.removeEventListener("keydown", onKeyDown, true);
            window.removeEventListener("keyup", onModifierChange, true);
            window.removeEventListener("blur", hideHints);
        };
    }, [enabled, view]);

    useEffect(() => {
        if (!enabled) setHintsVisible(false);
    }, [enabled]);

    return hintsVisible;
}

// Shortcut hint keycaps
// -------------------------------------------------------------------------------------------------------------------
// Shown while the Primary+Alt chord is held. The hint reads the context from
// useCreatorShortcutsHints, which the Create page provides through CreatorShortcutHintsProvider.

export function CreatorShortcutHint(props: {shortcut: string}): React.ReactNode {
    const visible = useCreatorShortcutsHints();
    return (
        <span className={CreatorShortcutHintClass} data-visible={visible ? "true" : undefined}>
            <span className={ShortcutClass}>{createKeyboardShortcut(props.shortcut, ["ctrl", "alt"])}</span>
        </span>
    );
}

export function CreatorShortcutControl(props: React.PropsWithChildren<{shortcut: string}>): React.ReactNode {
    return (
        <span className={CreatorShortcutControlClass}>
            {props.children}
            <CreatorShortcutHint shortcut={props.shortcut} />
        </span>
    );
}

const CreatorShortcutHintClass = injectStyle("creator-shortcut-hint", k => `
    ${k} {
        display: none;
        flex: 0 0 auto;
    }

    ${k}[data-visible="true"] {
        display: inline-flex;
    }
`);

const CreatorShortcutControlClass = injectStyle("creator-shortcut-control", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        gap: 4px;
    }
`);

// Shortcut guide
// -------------------------------------------------------------------------------------------------------------------
// A single discovery row at the bottom of the properties island. It names the chord that reveals
// the full list. While the chord is held, the guide expands to every shortcut and the section
// titles show their keycaps.

export function CreatorShortcutGuide(): React.ReactNode {
    const visible = useCreatorShortcutsHints();
    const entries: {shortcut: string; modifiers?: ("ctrl" | "alt")[]; label: string}[] = [
        {shortcut: "↑ ↓", modifiers: [], label: "Move parameter selection"},
        {shortcut: "alt + ↑ ↓", modifiers: [], label: "Reorder selected parameter"},
        {shortcut: "esc", modifiers: [], label: "Deselect parameter"},
    ];
    if (visible) {
        entries.push(
            {shortcut: "Y", label: "YAML view"},
            {shortcut: "I", label: "Invocation view"},
            {shortcut: "P", label: "Preview"},
            {shortcut: "E", label: "Back to editor"},
            {shortcut: "S", modifiers: ["ctrl"], label: "Save"},
            {shortcut: "M", label: "Jump to metadata"},
            {shortcut: "C", label: "Jump to software"},
            {shortcut: "F", label: "Jump to features"},
            {shortcut: "N", label: "Jump to connectivity"},
            {shortcut: "A", label: "Jump to add parameter"},
        );
    }
    return (
        <div className={CreatorShortcutGuideClass}>
            {entries.map(entry => (
                <div key={entry.label} className="keyboard-navigation-row">
                    <span className={ShortcutClass}>
                        {createKeyboardShortcut(entry.shortcut, entry.modifiers ?? ["ctrl", "alt"])}
                    </span>
                    <span className="keyboard-navigation-action">{entry.label}</span>
                </div>
            ))}
            {visible ? null : (
                <div className="keyboard-navigation-row">
                    <span className={ShortcutClass}>{isLikelyMac ? "⌘⌥" : "Ctrl + Alt"}</span>
                    <span className="keyboard-navigation-action">Hold to show more shortcuts</span>
                </div>
            )}
        </div>
    );
}

const CreatorShortcutGuideClass = injectStyle("creator-shortcut-guide", k => `
    ${k} {
        color: var(--textSecondary);
        font-size: 12px;
        display: flex;
        flex-direction: column;
        gap: 8px;
        padding: 16px 12px;
        border-top: 1px solid var(--borderColor);
        flex-shrink: 0;
    }

    ${k} .keyboard-navigation-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: center;
        gap: 12px;
    }

    ${k} .keyboard-navigation-action {
        text-align: right;
    }

    @media (max-width: 1000px) {
        ${k} {
            display: none;
        }
    }
`);
