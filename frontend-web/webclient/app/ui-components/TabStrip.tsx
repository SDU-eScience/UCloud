import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import Icon, {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";

const TAB_GAP = 8;
const TAB_DRAG_THRESHOLD = 4;
const TAB_DROP_ANIMATION_MS = 160;

interface TabSlot {
    left: number;
    width: number;
}

interface TabDragState {
    id: string;
    order: string[];
    originalOrder: string[];
    pointerId: number;
    pointerOffset: number;
    pointerStartX: number;
    pointerLeft: number;
    slots: Record<string, TabSlot>;
    offsets: Record<string, number>;
    started: boolean;
    dropping: boolean;
}

export interface TabStripItem {
    id: string;
    title: React.ReactNode;
    tooltip?: React.ReactNode;
    icon?: React.ReactNode;
    closeIcon?: IconName;
    closeIconOnHover?: IconName;
    closeLabel: string;
    closeTooltip?: React.ReactNode;
}

export function TabStrip({
    items,
    activeId,
    onActivate,
    onClose,
    onContextMenu,
    onReorder,
    className,
    shortcutScope,
    allowUnfocusedShortcuts = false,
    slim = false,
    autoSize = true,
}: {
    items: TabStripItem[];
    activeId?: string;
    onActivate(id: string): void;
    onClose(id: string): void;
    onContextMenu?: (id: string, position: {x: number; y: number}) => void;
    onReorder(ids: string[]): void;
    className?: string;
    shortcutScope?: React.RefObject<HTMLElement | null>;
    allowUnfocusedShortcuts?: boolean;
    slim?: boolean;
    autoSize?: boolean;
}): React.ReactNode {
    const tabsRef = useRef<HTMLDivElement | null>(null);
    const tabRefs = useRef<Record<string, HTMLDivElement | null>>({});
    const dragRef = useRef<TabDragState | null>(null);
    const dropFrameRef = useRef<number | null>(null);
    const dropTimerRef = useRef<number | null>(null);
    const onReorderRef = useRef(onReorder);
    const [drag, setDrag] = useState<TabDragState | null>(null);
    const [hoveredClose, setHoveredClose] = useState<string | null>(null);
    const itemIds = items.map(item => item.id);
    const itemOrder = JSON.stringify(itemIds);

    useLayoutEffect(() => {
        onReorderRef.current = onReorder;
    }, [onReorder]);

    useLayoutEffect(() => {
        if (!activeId) return;
        if (drag) return;

        const tabs = tabsRef.current;
        const tab = tabRefs.current[activeId];
        if (!tabs || !tab) return;

        const tabLeft = tab.offsetLeft;
        const tabRight = tabLeft + tab.offsetWidth;
        const visibleLeft = tabs.scrollLeft;
        const visibleRight = visibleLeft + tabs.clientWidth;
        if (tabLeft < visibleLeft) {
            tabs.scrollLeft = tabLeft;
        } else if (tabRight > visibleRight) {
            tabs.scrollLeft = tabRight - tabs.clientWidth;
        }
    }, [activeId, drag !== null, itemOrder]);

    useEffect(() => {
        const listener = (event: KeyboardEvent) => {
            if (event.defaultPrevented) return;
            const activeElement = document.activeElement;
            const tabs = tabsRef.current;
            const inShortcutScope = tabs?.contains(activeElement) || shortcutScope?.current?.contains(activeElement);
            const canHandleWithoutFocus = allowUnfocusedShortcuts && activeElement === document.body;
            if (!activeElement || (!inShortcutScope && !canHandleWithoutFocus)) return;

            const activeIndex = items.findIndex(item => item.id === activeId);
            if (activeIndex < 0 || items.length === 0) return;

            const primaryModifier = event.ctrlKey || event.metaKey;
            if (!event.altKey || !primaryModifier || event.shiftKey) return;
            let nextIndex: number | undefined;
            let close = false;

            if (event.code === "KeyW") {
                close = true;
            } else if (event.code === "PageUp" || event.code === "PageDown") {
                nextIndex = activeIndex + (event.code === "PageUp" ? -1 : 1);
            } else if (event.metaKey && (event.code === "BracketLeft" || event.code === "BracketRight")) {
                nextIndex = activeIndex + (event.code === "BracketLeft" ? -1 : 1);
            }

            if (close) {
                event.preventDefault();
                event.stopPropagation();
                onClose(activeId!);
                return;
            }

            if (nextIndex === undefined || nextIndex === activeIndex) return;
            event.preventDefault();
            event.stopPropagation();
            onActivate(items[(nextIndex + items.length) % items.length].id);
        };

        window.addEventListener("keydown", listener, true);
        return () => window.removeEventListener("keydown", listener, true);
    }, [activeId, allowUnfocusedShortcuts, items, onActivate, onClose, shortcutScope]);

    const handleDragMove = useCallback((e: PointerEvent) => {
        const currentDrag = dragRef.current;
        const tabs = tabsRef.current;
        if (!currentDrag || currentDrag.dropping || !tabs || e.pointerId !== currentDrag.pointerId) return;

        if (!currentDrag.started && Math.abs(e.clientX - currentDrag.pointerStartX) < TAB_DRAG_THRESHOLD) return;

        e.preventDefault();
        const tabsRect = tabs.getBoundingClientRect();
        const edgeSize = 40;
        if (e.clientX < tabsRect.left + edgeSize) {
            tabs.scrollLeft -= Math.max(1, Math.ceil((tabsRect.left + edgeSize - e.clientX) / 4));
        } else if (e.clientX > tabsRect.right - edgeSize) {
            tabs.scrollLeft += Math.max(1, Math.ceil((e.clientX - tabsRect.right + edgeSize) / 4));
        }

        const rawPointerLeft = e.clientX - tabsRect.left + tabs.scrollLeft - currentDrag.pointerOffset;
        const containerLeft = tabs.scrollLeft;
        const containerRight = containerLeft + tabs.clientWidth;
        const draggedWidth = currentDrag.slots[currentDrag.id].width;
        const minPointerLeft = containerLeft;
        const maxPointerLeft = Math.max(minPointerLeft, containerRight - draggedWidth);
        const pointerLeft = Math.min(Math.max(rawPointerLeft, minPointerLeft), maxPointerLeft);
        const atLeftEdge = tabs.scrollLeft <= 0.5 && pointerLeft <= minPointerLeft;
        const atRightEdge = tabs.scrollLeft + tabs.clientWidth >= tabs.scrollWidth - 0.5 && pointerLeft >= maxPointerLeft;
        let order = currentDrag.order;
        let currentIndex = order.indexOf(currentDrag.id);

        if (atLeftEdge) {
            if (currentIndex > 0) {
                order = [currentDrag.id, ...order.filter(id => id !== currentDrag.id)];
                currentIndex = 0;
            }
        } else if (atRightEdge) {
            if (currentIndex < order.length - 1) {
                order = [...order.filter(id => id !== currentDrag.id), currentDrag.id];
                currentIndex = order.length - 1;
            }
        } else if (pointerLeft > currentDrag.pointerLeft) {
            while (currentIndex < order.length - 1) {
                const targetId = order[currentIndex + 1];
                const targetSlot = slotsForOrder(currentDrag, order)[targetId];
                if (pointerLeft + draggedWidth < targetSlot.left + targetSlot.width / 2) break;

                order = [...order];
                order.splice(currentIndex, 0, order.splice(currentIndex + 1, 1)[0]);
                currentIndex++;
            }
        } else if (pointerLeft < currentDrag.pointerLeft) {
            while (currentIndex > 0) {
                const targetId = order[currentIndex - 1];
                const targetSlot = slotsForOrder(currentDrag, order)[targetId];
                if (pointerLeft > targetSlot.left + targetSlot.width / 2) break;

                order = [...order];
                order.splice(currentIndex - 1, 0, order.splice(currentIndex, 1)[0]);
                currentIndex--;
            }
        }

        const nextDrag = {...currentDrag, order, pointerLeft, started: true};
        dragRef.current = nextDrag;
        setDrag(nextDrag);
    }, []);

    const clearDrag = useCallback(() => {
        const currentDrag = dragRef.current;
        const capturedTab = currentDrag ? tabRefs.current[currentDrag.id] : null;
        if (currentDrag && capturedTab?.hasPointerCapture(currentDrag.pointerId)) {
            capturedTab.releasePointerCapture(currentDrag.pointerId);
        }
        if (dropFrameRef.current !== null) window.cancelAnimationFrame(dropFrameRef.current);
        if (dropTimerRef.current !== null) window.clearTimeout(dropTimerRef.current);
        dropFrameRef.current = null;
        dropTimerRef.current = null;
        dragRef.current = null;
        setDrag(null);
    }, []);

    useLayoutEffect(() => {
        const currentDrag = dragRef.current;
        if (currentDrag && (!sameOrder(itemIds, currentDrag.originalOrder) || !sameGeometry(currentDrag, tabRefs.current))) {
            clearDrag();
        }
        if (hoveredClose && !itemIds.includes(hoveredClose)) setHoveredClose(null);
    });

    useLayoutEffect(() => {
        const tabs = tabsRef.current;
        if (!tabs || typeof ResizeObserver === "undefined") return;

        const observer = new ResizeObserver(() => {
            const currentDrag = dragRef.current;
            if (currentDrag && !sameGeometry(currentDrag, tabRefs.current)) clearDrag();
        });
        observer.observe(tabs);
        itemIds.forEach(id => {
            const tab = tabRefs.current[id];
            if (tab) observer.observe(tab);
        });
        return () => observer.disconnect();
    }, [clearDrag, itemOrder]);

    const commitDrop = useCallback(() => {
        const currentDrag = dragRef.current;
        if (!currentDrag?.dropping) return;

        clearDrag();
        if (currentDrag.order.some((id, index) => id !== currentDrag.originalOrder[index])) {
            onReorderRef.current(currentDrag.order);
        }
    }, [clearDrag]);

    const finishDrag = useCallback((commit: boolean) => {
        const currentDrag = dragRef.current;
        if (!currentDrag || currentDrag.dropping) return;

        if (!commit || !currentDrag.started) {
            clearDrag();
            return;
        }

        const dropping = {...currentDrag, dropping: true};
        dragRef.current = dropping;
        setDrag(dropping);
        dropFrameRef.current = window.requestAnimationFrame(() => {
            const pendingDrag = dragRef.current;
            if (!pendingDrag?.dropping) return;

            dropFrameRef.current = null;
            const destination = slotsForOrder(pendingDrag, pendingDrag.order)[pendingDrag.id];
            if (Math.abs(pendingDrag.pointerLeft - destination.left) < 0.5) {
                commitDrop();
                return;
            }

            const animatedDrop = {...pendingDrag, pointerLeft: destination.left};
            dragRef.current = animatedDrop;
            setDrag(animatedDrop);
            dropTimerRef.current = window.setTimeout(commitDrop, TAB_DROP_ANIMATION_MS + 50);
        });
    }, [clearDrag, commitDrop]);

    useLayoutEffect(() => {
        const pointerUp = (e: PointerEvent) => {
            if (dragRef.current?.pointerId === e.pointerId) finishDrag(true);
        };
        const pointerCancel = (e: PointerEvent) => {
            if (dragRef.current?.pointerId === e.pointerId) finishDrag(false);
        };
        const cancel = () => finishDrag(false);

        window.addEventListener("pointermove", handleDragMove, true);
        window.addEventListener("pointerup", pointerUp, true);
        window.addEventListener("pointercancel", pointerCancel, true);
        window.addEventListener("blur", cancel);
        return () => {
            window.removeEventListener("pointermove", handleDragMove, true);
            window.removeEventListener("pointerup", pointerUp, true);
            window.removeEventListener("pointercancel", pointerCancel, true);
            window.removeEventListener("blur", cancel);
            if (dropFrameRef.current !== null) window.cancelAnimationFrame(dropFrameRef.current);
            if (dropTimerRef.current !== null) window.clearTimeout(dropTimerRef.current);
        };
    }, [finishDrag, handleDragMove]);

    const beginDrag = useCallback((e: React.PointerEvent<HTMLDivElement>, id: string) => {
        if (e.button !== 0 || dragRef.current || (e.target as HTMLElement).closest(".tab-strip-close")) return;

        const tabs = tabsRef.current;
        const tab = tabRefs.current[id];
        if (!tabs || !tab) return;

        const originalOrder = items.map(item => item.id);
        if (originalOrder.some(tabId => !tabRefs.current[tabId])) return;

        const tabsRect = tabs.getBoundingClientRect();
        const slots: Record<string, TabSlot> = {};
        const offsets: Record<string, number> = {};
        originalOrder.forEach(tabId => {
            const item = tabRefs.current[tabId]!;
            const itemRect = item.getBoundingClientRect();
            slots[tabId] = {left: itemRect.left - tabsRect.left + tabs.scrollLeft, width: itemRect.width};
            offsets[tabId] = item.offsetLeft;
        });
        const rect = tab.getBoundingClientRect();
        const nextDrag: TabDragState = {
            id,
            order: originalOrder,
            originalOrder,
            pointerId: e.pointerId,
            pointerOffset: e.clientX - rect.left,
            pointerStartX: e.clientX,
            pointerLeft: slots[id].left,
            slots,
            offsets,
            started: false,
            dropping: false,
        };

        dragRef.current = nextDrag;
        e.currentTarget.setPointerCapture(e.pointerId);
    }, [items]);

    const destinationSlots = drag ? slotsForOrder(drag, drag.order) : null;

    return <div className={`${TabStripClass} ${className ?? ""}`} ref={tabsRef} role="tablist" data-reordering={drag !== null} data-slim={slim.toString()} data-auto-size={autoSize.toString()}>
        {items.map(item => {
            const isActive = item.id === activeId;
            const closeIcon = hoveredClose === item.id ? item.closeIconOnHover ?? item.closeIcon ?? "close" : item.closeIcon ?? "close";
            const baseSlot = drag?.slots[item.id];
            const destinationSlot = destinationSlots?.[item.id];
            const left = item.id === drag?.id ? drag.pointerLeft : destinationSlot?.left;
            return <div
                key={item.id}
                ref={node => {
                    tabRefs.current[item.id] = node;
                }}
                className="tab-strip-item"
                role="tab"
                tabIndex={0}
                aria-selected={isActive}
                data-active={isActive}
                data-dragging={drag?.id === item.id}
                data-dropping={drag?.dropping && drag.id === item.id}
                style={baseSlot && left !== undefined ? {transform: `translateX(${left - baseSlot.left}px)`} : undefined}
                onPointerDown={e => beginDrag(e, item.id)}
                onMouseDown={e => {
                    if (e.button === 1 && !(e.target as HTMLElement).closest(".tab-strip-close")) {
                        e.preventDefault();
                        onClose(item.id);
                    }
                }}
                onTransitionEnd={e => {
                    if (e.target === e.currentTarget && e.propertyName === "transform" && drag?.dropping && drag.id === item.id) commitDrop();
                }}
                onClick={() => onActivate(item.id)}
                onKeyDown={e => {
                    if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onActivate(item.id);
                    }
                }}
                onContextMenu={e => {
                    if (!onContextMenu) return;
                    e.preventDefault();
                    e.stopPropagation();
                    onContextMenu(item.id, {x: e.clientX, y: e.clientY});
                }}
            >
                {item.icon ? <span className="tab-strip-icon">{item.icon}</span> : null}
                <TabStripTitle title={item.title} tooltip={item.tooltip} />
                <TooltipV2 tooltip={item.closeTooltip} side="top" contentWidth={150} triggerClassName={`${CloseTooltipTrigger} tab-strip-close-tooltip`}>
                    <button
                        type="button"
                        className="tab-strip-close"
                        aria-label={item.closeLabel}
                        onMouseEnter={() => setHoveredClose(item.id)}
                        onMouseLeave={() => setHoveredClose(null)}
                        onClick={e => {
                            e.stopPropagation();
                            onClose(item.id);
                        }}
                    >
                        <Icon name={closeIcon} size={10} />
                    </button>
                </TooltipV2>
            </div>;
        })}
    </div>;
}

function slotsForOrder(drag: TabDragState, order: string[]): Record<string, TabSlot> {
    const slots: Record<string, TabSlot> = {};
    let left = drag.slots[drag.originalOrder[0]]?.left ?? 0;

    for (const id of order) {
        const width = drag.slots[id]?.width ?? 0;
        slots[id] = {left, width};
        left += width + TAB_GAP;
    }

    return slots;
}

function sameOrder(first: string[], second: string[]): boolean {
    return first.length === second.length && first.every((id, index) => id === second[index]);
}

function sameGeometry(drag: TabDragState, refs: Record<string, HTMLDivElement | null>): boolean {
    return drag.originalOrder.every(id => {
        const tab = refs[id];
        if (!tab) return false;

        return tab.offsetLeft === drag.offsets[id] && Math.abs(tab.getBoundingClientRect().width - drag.slots[id].width) < 0.5;
    });
}

function TabStripTitle({title, tooltip}: {title: React.ReactNode; tooltip?: React.ReactNode}): React.ReactNode {
    const titleRef = useRef<HTMLDivElement | null>(null);
    const [isOverflowing, setIsOverflowing] = useState(false);

    const updateOverflow = useCallback(() => {
        const element = titleRef.current;
        if (!element) return;
        setIsOverflowing(element.scrollWidth > element.clientWidth);
    }, []);

    useLayoutEffect(() => {
        updateOverflow();
        const element = titleRef.current;
        if (!element || typeof ResizeObserver === "undefined") return;

        const observer = new ResizeObserver(updateOverflow);
        observer.observe(element);
        return () => observer.disconnect();
    }, [title, updateOverflow]);

    return <div className={TabTitleSlot}>
        <TooltipV2 tooltip={tooltip} disabled={!isOverflowing} side="top" triggerClassName={TitleTooltipTrigger}>
            <div ref={titleRef} className={TabTitleClass}>{title}</div>
        </TooltipV2>
    </div>;
}

const TabStripClass = injectStyle("tab-strip", k => `
    ${k} {
        min-width: 0;
        height: 100%;
        display: flex;
        align-items: stretch;
        justify-content: flex-start;
        gap: ${TAB_GAP}px;
        flex: 1 1 0;
        width: 0;
        overflow-x: auto;
        overflow-y: hidden;
        box-sizing: border-box;
        user-select: none;
        -webkit-user-select: none;
    }

    ${k} .tab-strip-item {
        min-width: 184px;
        width: auto;
        max-width: none;
        height: 32px;
        align-self: center;
        padding: 0 10px;
        display: flex;
        flex: 1 0 184px;
        align-items: center;
        gap: 6px;
        box-sizing: border-box;
        border: 1px solid var(--borderColor);
        border-radius: 7px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font-family: var(--sansSerif);
        font-size: 13px;
        line-height: 1;
        touch-action: none;
        transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
    }

    ${k}[data-slim="true"] .tab-strip-item {
        min-width: 160px;
        flex-basis: 160px;
        height: 24px;
        padding: 0 7px;
        gap: 4px;
        border-radius: 5px;
        font-size: 12px;
    }

    ${k}[data-auto-size="false"] .tab-strip-item {
        flex-grow: 0;
    }

    ${k}[data-auto-size="false"][data-slim="true"] .tab-strip-item {
        min-width: 0;
        width: fit-content;
        max-width: 160px;
        flex: 0 1 auto;
    }

    ${k}[data-reordering="true"] .tab-strip-item {
        transition: transform ${TAB_DROP_ANIMATION_MS}ms ease, background-color 120ms ease, border-color 120ms ease, color 120ms ease;
    }

    ${k} .tab-strip-item[data-dragging="true"] {
        z-index: 2;
        cursor: grabbing;
        transition: none;
    }

    ${k} .tab-strip-item[data-dragging="true"][data-dropping="true"] {
        transition: transform ${TAB_DROP_ANIMATION_MS}ms ease;
    }

    ${k} .tab-strip-item img {
        -webkit-user-drag: none;
    }

    ${k} .tab-strip-item:hover {
        background: var(--rowHover);
        border-color: var(--borderColorHover);
        color: var(--textPrimary);
    }

    ${k} .tab-strip-item[data-active="true"] {
        background: var(--rowActive);
        border-color: var(--primaryMain);
        color: var(--textPrimary);
    }

    html.dark ${k} .tab-strip-item[data-active="true"] {
        background: #233558;
    }

    ${k} .tab-strip-item:focus-visible,
    ${k} .tab-strip-close:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }

    ${k} .tab-strip-icon {
        display: inline-flex;
        flex: none;
        align-items: center;
    }

    ${k} .tab-strip-close {
        width: 16px;
        height: 16px;
        padding: 0;
        display: inline-flex;
        flex: none;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font: inherit;
        opacity: 0.75;
        transition: background-color 120ms ease, color 120ms ease;
    }

    ${k}[data-slim="true"] .tab-strip-close {
        width: 14px;
        height: 14px;
    }

    ${k}[data-slim="true"] .tab-strip-close-tooltip {
        margin-left: 2px;
    }

    ${k} .tab-strip-close:hover {
        background: var(--borderColorHover);
        color: var(--textPrimary);
    }

    ${k} .tab-strip-item:hover .tab-strip-close,
    ${k} .tab-strip-item[data-active="true"] .tab-strip-close {
        opacity: 1;
    }
`);

const TabTitleSlot = injectStyle("tab-strip-title-slot", k => `
    ${k} {
        min-width: 0;
        flex: 1 1 auto;
        overflow: hidden;
    }
`);

const TitleTooltipTrigger = injectStyle("tab-strip-title-tooltip-trigger", k => `
    ${k} {
        width: 100%;
        overflow: hidden;
    }
`);

const TabTitleClass = injectStyle("tab-strip-title", k => `
    ${k} {
        width: 100%;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        text-align: left;
    }
`);

const CloseTooltipTrigger = injectStyle("tab-strip-close-tooltip-trigger", k => `
    ${k} {
        width: 16px;
        height: 16px;
        display: inline-flex;
        flex: none;
        align-items: center;
        justify-content: center;
    }
`);
