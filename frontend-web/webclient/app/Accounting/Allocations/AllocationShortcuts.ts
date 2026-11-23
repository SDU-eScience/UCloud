import {useEffect} from "react";
import {closeOpenDropdown, isModalOpen} from "@/Applications/KeyboardNavigation";
import {isLikelyMac} from "@/UtilityFunctions";

function allocationShortcutElement(name: string): HTMLElement | null {
    return document.querySelector<HTMLElement>(`[data-allocation-shortcut="${name}"]`);
}

function focusFirstControl(root: HTMLElement | null): void {
    if (!root) return;
    const target = Array.from(root.querySelectorAll<HTMLElement>(
        "[data-allocation-filter-control], button:not(:disabled), [data-tag='dropdown'][tabindex='0'], [role='switch']"
    )).find(element => element.offsetParent !== null && !element.closest("[data-visible='false']") &&
        !element.matches(":disabled, [aria-disabled='true']"));
    target?.focus({preventScroll: true});
}

function focusTableRoot(selector: string): void {
    const table = document.querySelector<HTMLElement>(selector);
    table?.focus({preventScroll: true});
}

export function focusAllocationTable(): void {
    if (isModalOpen()) return;
    focusTableRoot("[data-ucx-table='allocation-grid']");
}

export function focusAllocationOverview(): void {
    if (isModalOpen()) return;
    const toggle = document.querySelector<HTMLElement>("[data-allocation-overview-toggle]");
    const collapsed = toggle?.getAttribute("aria-expanded") === "false";
    if (toggle && collapsed) toggle.click();
    requestAnimationFrame(() => requestAnimationFrame(() => {
        const section = document.querySelector<HTMLElement>("[data-allocation-overview]");
        const table = section?.querySelector<HTMLElement>("[data-ucx-table]");
        (table ?? toggle)?.focus({preventScroll: true});
    }));
}

export function focusAllocationAction(name: "allocate" | "export"): void {
    if (isModalOpen()) return;
    allocationShortcutElement(name)?.focus({preventScroll: true});
}

export function triggerAllocationAction(name: "allocate" | "export"): void {
    if (isModalOpen()) return;
    allocationShortcutElement(name)?.click();
}

function focusAllocationSearch(): void {
    const search = allocationShortcutElement("search");
    if (!search) return;
    search.scrollIntoView({block: "nearest"});
    search.focus({preventScroll: true});
}

export function useAllocationShortcuts(): void {
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.defaultPrevented || event.repeat || isModalOpen()) return;
            const target = event.target as HTMLElement | null;
            const editable = target?.closest("input, textarea, select, [contenteditable='true']") != null;
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;

            if ((event.key === "/" && !event.metaKey && !event.ctrlKey && !event.altKey) ||
                (primaryPressed && !event.altKey && !event.shiftKey && event.key.toLowerCase() === "k")) {
                if (editable && event.key === "/") return;
                event.preventDefault();
                event.stopPropagation();
                focusAllocationSearch();
                return;
            }

            if (!event.altKey || !primaryPressed || event.shiftKey) return;
            if (event.code !== "KeyO" && event.code !== "KeyG" && event.code !== "KeyK" &&
                event.code !== "KeyS" && event.code !== "KeyA" && event.code !== "KeyE") return;

            event.preventDefault();
            event.stopPropagation();
            if (document.activeElement instanceof HTMLElement) closeOpenDropdown(document.activeElement);

            switch (event.code) {
                case "KeyO":
                    focusAllocationOverview();
                    return;
                case "KeyG":
                    focusFirstControl(allocationShortcutElement("filters"));
                    return;
                case "KeyK":
                    focusFirstControl(allocationShortcutElement("period"));
                    return;
                case "KeyS":
                    focusAllocationTable();
                    return;
                case "KeyA":
                    triggerAllocationAction("allocate");
                    return;
                case "KeyE":
                    triggerAllocationAction("export");
                    return;
            }
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, []);
}
