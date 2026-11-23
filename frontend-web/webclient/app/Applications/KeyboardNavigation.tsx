import * as React from "react";
import {useEffect, useState} from "react";
import {createKeyboardShortcut, isLikelyMac} from "@/UtilityFunctions";
import type {KeyboardShortcutModifier} from "@/UtilityFunctions";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {injectStyle} from "@/Unstyled";

export function isModalOpen(): boolean {
    return document.querySelector(".ReactModal__Overlay") !== null;
}

export function useShortcutReveal(): boolean {
    const [visible, setVisible] = useState(false);
    useEffect(() => {
        const update = (event: KeyboardEvent) => {
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
            setVisible(!isModalOpen() && event.altKey && primaryPressed);
        };
        const hide = () => setVisible(false);
        const onKeyDown = (event: KeyboardEvent) => update(event);
        const onKeyUp = (event: KeyboardEvent) => update(event);
        document.addEventListener("keydown", onKeyDown);
        document.addEventListener("keyup", onKeyUp);
        window.addEventListener("blur", hide);
        return () => {
            document.removeEventListener("keydown", onKeyDown);
            document.removeEventListener("keyup", onKeyUp);
            window.removeEventListener("blur", hide);
        };
    }, []);
    return visible;
}

export function ShortcutHint(props: {
    shortcut: string;
    modifiers?: KeyboardShortcutModifier[];
    compact?: boolean;
    hideUntilReveal?: boolean;
}): React.ReactNode {
    return <span
        className={ShortcutClass}
        style={{marginLeft: 8, marginRight: 4}}
        data-allocation-shortcut-hint={props.hideUntilReveal ? "" : undefined}
    >
        {createKeyboardShortcut(props.shortcut, props.modifiers ?? ["ctrl", "alt"], props.compact)}
    </span>;
}

export const FIELD_NAVIGATION_SELECTOR = [
    "[data-job-info-field]",
    "[data-navigation-field]",
    "[data-field-row] input:not([type='hidden'])",
    "[data-field-row] select",
    "[data-field-row] textarea",
    "[data-field-row] [role='switch']",
    "[data-field-row] [role='button']",
    "[data-field-row] [data-field-activator]",
].join(", ");

export const FORM_NAVIGATION_SELECTOR = [
    "input:not([type='hidden'])",
    "select",
    "textarea",
    "[role='switch']",
    "[data-navigation-field]",
].join(", ");

export function isDisabledNavigationTarget(element: HTMLElement): boolean {
    return element.matches(":disabled, [aria-disabled='true']");
}

export function focusFirstNavigationTarget(root: HTMLElement, selector = FIELD_NAVIGATION_SELECTOR): HTMLElement | null {
    const target = Array.from(root.querySelectorAll<HTMLElement>(selector))
        .find(element => element.offsetParent !== null && !isDisabledNavigationTarget(element)) ?? null;
    target?.focus();
    return target;
}

export function closeOpenDropdown(field: HTMLElement): void {
    const dropdown = field.closest<HTMLElement>("[aria-expanded='true']");
    dropdown?.querySelector<HTMLElement>("[data-dropdown-trigger]")?.click();
}

function findSpatialNavigationTarget(
    current: HTMLElement,
    candidates: HTMLElement[],
    key: "ArrowLeft" | "ArrowRight" | "ArrowUp" | "ArrowDown",
): HTMLElement | null {
    const currentRect = current.getBoundingClientRect();
    const vertical = key === "ArrowUp" || key === "ArrowDown";
    const forward = key === "ArrowRight" || key === "ArrowDown";
    const overlaps = (aStart: number, aEnd: number, bStart: number, bEnd: number) =>
        Math.min(aEnd, bEnd) > Math.max(aStart, bStart);
    const currentCenter = vertical ? currentRect.top + currentRect.height / 2 : currentRect.left + currentRect.width / 2;
    const centerDistance = (rect: DOMRect) => Math.abs((vertical ?
        rect.top + rect.height / 2 : rect.left + rect.width / 2) - currentCenter);
    const isAhead = (rect: DOMRect) => {
        const candidateCenter = vertical ? rect.top + rect.height / 2 : rect.left + rect.width / 2;
        return (candidateCenter - currentCenter) * (forward ? 1 : -1) > 4;
    };
    const available = candidates.map(element => ({element, rect: element.getBoundingClientRect()})).filter(candidate => {
        if (candidate.element === current || candidate.element.offsetParent === null ||
            isDisabledNavigationTarget(candidate.element)) return false;
        return isAhead(candidate.rect);
    });
    const visible = available.filter(candidate => vertical ?
        overlaps(currentRect.left, currentRect.right, candidate.rect.left, candidate.rect.right) :
        overlaps(currentRect.top, currentRect.bottom, candidate.rect.top, candidate.rect.bottom)
    );
    if (visible.length === 0) {
        if (available.length === 0) return null;
        const nearest = available.reduce((best, candidate) =>
            centerDistance(candidate.rect) < centerDistance(best.rect) ? candidate : best
        );
        return nearest.element;
    }

    const primaryDistance = (rect: DOMRect) => Math.max(0, vertical ?
        (forward ? rect.top - currentRect.bottom : currentRect.top - rect.bottom) :
        (forward ? rect.left - currentRect.right : currentRect.left - rect.right));
    const nearest = visible.reduce((best, candidate) =>
        primaryDistance(candidate.rect) < primaryDistance(best.rect) ? candidate : best
    );
    const sameLane = visible.filter(candidate => vertical ?
        overlaps(nearest.rect.top, nearest.rect.bottom, candidate.rect.top, candidate.rect.bottom) :
        overlaps(nearest.rect.left, nearest.rect.right, candidate.rect.left, candidate.rect.right)
    );
    return sameLane.reduce((best, candidate) =>
        centerDistance(candidate.rect) < centerDistance(best.rect) ? candidate : best
    ).element;
}

export function KeyboardNavigation({children, className, style, horizontalSelector, navigationSelector}: React.PropsWithChildren<{
    className?: string;
    style?: React.CSSProperties;
    horizontalSelector?: string;
    navigationSelector?: string;
}>): React.ReactNode {
    const verticalSelector = navigationSelector ?? FIELD_NAVIGATION_SELECTOR;
    const onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (!horizontalSelector || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
        const target = event.target as HTMLElement;
        const current = target.closest<HTMLElement>(horizontalSelector);
        if (!current || !event.currentTarget.contains(current)) return;

        if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
            const start = target.selectionStart;
            const end = target.selectionEnd;
            if (start !== null && end !== null) {
                if (event.key === "ArrowLeft" && (start !== 0 || end !== 0)) return;
                if (event.key === "ArrowRight" && (start !== target.value.length || end !== target.value.length)) return;
            }
        }

        const next = findSpatialNavigationTarget(
            current,
            Array.from(event.currentTarget.querySelectorAll<HTMLElement>(horizontalSelector)),
            event.key,
        );
        if (!next) return;
        event.preventDefault();
        closeOpenDropdown(current);
        next.focus();
    };

    const onKeyDownCapture = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if ((event.key !== "ArrowUp" && event.key !== "ArrowDown") || event.metaKey || event.ctrlKey || event.altKey) return;
        const target = event.target as HTMLElement;
        const openDropdown = target.closest<HTMLElement>("[aria-expanded='true']");
        if (openDropdown && !target.closest("[data-dropdown-trigger]")) return;
        if (target instanceof HTMLTextAreaElement) {
            const start = target.selectionStart;
            const end = target.selectionEnd;
            if (start !== null && end !== null) {
                if (event.key === "ArrowUp" && (start !== 0 || end !== 0)) return;
                if (event.key === "ArrowDown" && (start !== target.value.length || end !== target.value.length)) return;
            }
        }

        const current = target.closest<HTMLElement>(verticalSelector);
        if (!current || !event.currentTarget.contains(current)) return;
        const next = findSpatialNavigationTarget(
            current,
            Array.from(event.currentTarget.querySelectorAll<HTMLElement>(verticalSelector)),
            event.key,
        );

        if (!next) return;
        event.preventDefault();
        event.stopPropagation();
        closeOpenDropdown(current);
        next.focus();
    };

    return <div className={className} style={style} onKeyDown={onKeyDown} onKeyDownCapture={onKeyDownCapture}>{children}</div>;
}

export function useSubmitShortcut(submit: () => void, disabled = false, opts?: {allowInModal?: boolean}): void {
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (!opts?.allowInModal && document.querySelector(".ReactModal__Overlay")) return;
            if (event.defaultPrevented) return;
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
            if (!event.altKey || !primaryPressed || event.key !== "Enter") return;
            event.preventDefault();
            if (!disabled) submit();
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [disabled, opts?.allowInModal, submit]);
}

const SubmitShortcutClass = injectStyle("submit-shortcut", key => `
    @media (max-width: 600px) {
        ${key} {
            display: none;
        }
    }
`);

export function SubmitShortcut(props: {
    shortcut?: string;
    modifiers?: KeyboardShortcutModifier[];
    compact?: boolean;
}): React.ReactNode {
    return <span
        className={`${ShortcutClass} ${SubmitShortcutClass}`}
        style={{
            marginLeft: "12px",
            backgroundColor: "var(--hoverColor, var(--successDark))",
            color: "inherit",
            mixBlendMode: "normal"
        }}
    >
        {createKeyboardShortcut(props.shortcut ?? "Enter", props.modifiers ?? ["ctrl", "alt"], props.compact)}
    </span>;
}
