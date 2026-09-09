import * as React from "react";
import {useEffect} from "react";
import {createKeyboardShortcut, isLikelyMac} from "@/UtilityFunctions";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {injectStyle} from "@/Unstyled";

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

export function isDisabledNavigationTarget(element: HTMLElement): boolean {
    return element.matches(":disabled, [aria-disabled='true']");
}

export function closeOpenDropdown(field: HTMLElement): void {
    const dropdown = field.getAttribute("aria-expanded") === "true" ? field :
        field.querySelector<HTMLElement>("[aria-expanded='true']");
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
    const visible = candidates.map(element => ({element, rect: element.getBoundingClientRect()})).filter(candidate => {
        if (candidate.element === current || candidate.element.offsetParent === null ||
            isDisabledNavigationTarget(candidate.element)) return false;
        const currentCenter = vertical ? currentRect.top + currentRect.height / 2 : currentRect.left + currentRect.width / 2;
        const candidateCenter = vertical ? candidate.rect.top + candidate.rect.height / 2 : candidate.rect.left + candidate.rect.width / 2;
        if ((candidateCenter - currentCenter) * (forward ? 1 : -1) <= 4) return false;
        return vertical ?
            overlaps(currentRect.left, currentRect.right, candidate.rect.left, candidate.rect.right) :
            overlaps(currentRect.top, currentRect.bottom, candidate.rect.top, candidate.rect.bottom);
    });
    if (visible.length === 0) return null;

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
    const secondaryDistance = (rect: DOMRect) => vertical ?
        Math.abs(rect.left + rect.width / 2 - (currentRect.left + currentRect.width / 2)) :
        Math.abs(rect.top + rect.height / 2 - (currentRect.top + currentRect.height / 2));
    return sameLane.reduce((best, candidate) =>
        secondaryDistance(candidate.rect) < secondaryDistance(best.rect) ? candidate : best
    ).element;
}

export function KeyboardNavigation({children, className, horizontalSelector}: React.PropsWithChildren<{
    className?: string;
    horizontalSelector?: string;
}>): React.ReactNode {
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
        if (openDropdown?.getAttribute("role") === "button" && !target.closest("[data-dropdown-trigger]")) return;
        if (target instanceof HTMLTextAreaElement) {
            const start = target.selectionStart;
            const end = target.selectionEnd;
            if (start !== null && end !== null) {
                if (event.key === "ArrowUp" && (start !== 0 || end !== 0)) return;
                if (event.key === "ArrowDown" && (start !== target.value.length || end !== target.value.length)) return;
            }
        }

        const current = target.closest<HTMLElement>(FIELD_NAVIGATION_SELECTOR);
        if (!current || !event.currentTarget.contains(current)) return;
        const next = findSpatialNavigationTarget(
            current,
            Array.from(event.currentTarget.querySelectorAll<HTMLElement>(FIELD_NAVIGATION_SELECTOR)),
            event.key,
        );

        if (!next) return;
        event.preventDefault();
        event.stopPropagation();
        closeOpenDropdown(current);
        next.focus();
    };

    return <div className={className} onKeyDown={onKeyDown} onKeyDownCapture={onKeyDownCapture}>{children}</div>;
}

export function useSubmitShortcut(submit: () => void, disabled = false): void {
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (document.querySelector(".ReactModal__Overlay")) return;
            if (event.defaultPrevented) return;
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
            if (!event.altKey || !primaryPressed || event.key !== "Enter") return;
            event.preventDefault();
            if (!disabled) submit();
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [disabled, submit]);
}

const SubmitShortcutClass = injectStyle("submit-shortcut", key => `
    @media (max-width: 600px) {
        ${key} {
            display: none;
        }
    }
`);

export function SubmitShortcut(): React.ReactNode {
    return <span
        className={`${ShortcutClass} ${SubmitShortcutClass}`}
        style={{
            marginLeft: "12px",
            backgroundColor: "var(--successDark)",
            color: "var(--fixedWhite)",
            mixBlendMode: "normal"
        }}
    >
        {createKeyboardShortcut("Enter", ["ctrl", "alt"])}
    </span>;
}
