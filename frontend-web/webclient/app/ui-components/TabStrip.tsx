import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import Icon, {IconName} from "@/ui-components/Icon";
import Truncate from "@/ui-components/Truncate";
import {TooltipV2} from "@/ui-components/Tooltip";

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
    pointerLeft: number;
    slots: TabSlot[];
    draggedWidth: number;
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
}: {
    items: TabStripItem[];
    activeId?: string;
    onActivate(id: string): void;
    onClose(id: string): void;
    onContextMenu?: (id: string, position: {x: number; y: number}) => void;
    onReorder(ids: string[]): void;
    className?: string;
}): React.ReactNode {
    const tabsRef = useRef<HTMLDivElement | null>(null);
    const tabRefs = useRef<Record<string, HTMLDivElement | null>>({});
    const dragRef = useRef<TabDragState | null>(null);
    const dropFrameRef = useRef<number | null>(null);
    const [drag, setDrag] = useState<TabDragState | null>(null);
    const [suppressTransitions, setSuppressTransitions] = useState(false);
    const [hoveredClose, setHoveredClose] = useState<string | null>(null);

    useEffect(() => {
        if (!activeId) return;

        const frame = window.requestAnimationFrame(() => {
            const tabs = tabsRef.current;
            const tab = tabRefs.current[activeId];
            if (!tabs || !tab) return;

            const tabLeft = tab.offsetLeft;
            const tabRight = tabLeft + tab.offsetWidth;
            const visibleLeft = tabs.scrollLeft;
            const visibleRight = visibleLeft + tabs.clientWidth;
            if (tabLeft < visibleLeft) {
                tabs.scrollTo({left: tabLeft, behavior: "smooth"});
            } else if (tabRight > visibleRight) {
                tabs.scrollTo({left: tabRight - tabs.clientWidth, behavior: "smooth"});
            }
        });

        return () => window.cancelAnimationFrame(frame);
    }, [activeId]);

    const handleDragMove = useCallback((e: PointerEvent) => {
        const currentDrag = dragRef.current;
        const tabs = tabsRef.current;
        if (!currentDrag || currentDrag.dropping || !tabs || e.pointerId !== currentDrag.pointerId) return;

        e.preventDefault();
        const tabsRect = tabs.getBoundingClientRect();
        const edgeSize = 40;
        if (e.clientX < tabsRect.left + edgeSize) {
            tabs.scrollLeft -= Math.max(1, Math.ceil((tabsRect.left + edgeSize - e.clientX) / 4));
        } else if (e.clientX > tabsRect.right - edgeSize) {
            tabs.scrollLeft += Math.max(1, Math.ceil((e.clientX - tabsRect.right + edgeSize) / 4));
        }

        const rawPointerLeft = e.clientX + tabs.scrollLeft - currentDrag.pointerOffset;
        const containerLeft = tabsRect.left + tabs.scrollLeft;
        const containerRight = tabsRect.right + tabs.scrollLeft;
        const minPointerLeft = containerLeft;
        const maxPointerLeft = Math.max(minPointerLeft, containerRight - currentDrag.draggedWidth);
        const pointerLeft = Math.min(Math.max(rawPointerLeft, minPointerLeft), maxPointerLeft);
        let order = currentDrag.order;
        let currentIndex = order.indexOf(currentDrag.id);

        if (pointerLeft > currentDrag.pointerLeft) {
            while (currentIndex < order.length - 1) {
                const targetId = order[currentIndex + 1];
                const targetSlot = currentDrag.slots[currentDrag.originalOrder.indexOf(targetId)];
                if (pointerLeft + currentDrag.draggedWidth <= targetSlot.left + targetSlot.width / 2) break;

                order = [...order];
                order.splice(currentIndex, 0, order.splice(currentIndex + 1, 1)[0]);
                currentIndex++;
            }
        } else if (pointerLeft < currentDrag.pointerLeft) {
            while (currentIndex > 0) {
                const targetId = order[currentIndex - 1];
                const targetSlot = currentDrag.slots[currentDrag.originalOrder.indexOf(targetId)];
                if (pointerLeft >= targetSlot.left + targetSlot.width / 2) break;

                order = [...order];
                order.splice(currentIndex - 1, 0, order.splice(currentIndex, 1)[0]);
                currentIndex--;
            }
        }

        const nextDrag = {...currentDrag, order, pointerLeft};
        dragRef.current = nextDrag;
        setDrag(nextDrag);
    }, []);

    const commitDrop = useCallback(() => {
        const currentDrag = dragRef.current;
        if (!currentDrag?.dropping) return;

        if (dropFrameRef.current !== null) {
            window.cancelAnimationFrame(dropFrameRef.current);
            dropFrameRef.current = null;
        }

        onReorder(currentDrag.order);
        setSuppressTransitions(true);
        window.requestAnimationFrame(() => setSuppressTransitions(false));
        dragRef.current = null;
        setDrag(null);
    }, [onReorder]);

    const finishDrag = useCallback((commit: boolean) => {
        const currentDrag = dragRef.current;
        if (!currentDrag || currentDrag.dropping) return;

        if (!commit || !currentDrag.order.some((id, index) => id !== currentDrag.originalOrder[index])) {
            dragRef.current = null;
            setDrag(null);
            return;
        }

        const dropping = {...currentDrag, dropping: true};
        dragRef.current = dropping;
        setDrag(dropping);

        dropFrameRef.current = window.requestAnimationFrame(() => {
            const pendingDrag = dragRef.current;
            if (!pendingDrag?.dropping) return;

            const destinationSlot = pendingDrag.slots[pendingDrag.order.indexOf(pendingDrag.id)];
            const animatedDrop = {...pendingDrag, pointerLeft: destinationSlot.left};
            dragRef.current = animatedDrop;
            setDrag(animatedDrop);
            dropFrameRef.current = null;
        });
    }, []);

    useEffect(() => {
        if (drag === null) return;

        const handleDragEnd = (e: PointerEvent) => {
            if (dragRef.current?.pointerId === e.pointerId) finishDrag(true);
        };
        const handleDragCancel = (e: PointerEvent) => {
            if (dragRef.current?.pointerId === e.pointerId) finishDrag(false);
        };

        window.addEventListener("pointermove", handleDragMove);
        window.addEventListener("pointerup", handleDragEnd);
        window.addEventListener("pointercancel", handleDragCancel);
        return () => {
            window.removeEventListener("pointermove", handleDragMove);
            window.removeEventListener("pointerup", handleDragEnd);
            window.removeEventListener("pointercancel", handleDragCancel);
        };
    }, [drag !== null, finishDrag, handleDragMove]);

    useEffect(() => () => {
        if (dropFrameRef.current !== null) window.cancelAnimationFrame(dropFrameRef.current);
    }, []);

    const beginDrag = useCallback((e: React.PointerEvent<HTMLDivElement>, id: string) => {
        if (e.button !== 0 || (e.target as HTMLElement).closest(".tab-strip-close")) return;

        const tabs = tabsRef.current;
        const tab = tabRefs.current[id];
        if (!tabs || !tab) return;

        const originalOrder = items.map(item => item.id);
        const tabIndex = originalOrder.indexOf(id);
        if (tabIndex < 0 || originalOrder.some(tabId => !tabRefs.current[tabId])) return;

        const scrollLeft = tabs.scrollLeft;
        const slots = originalOrder.map(tabId => {
            const rect = tabRefs.current[tabId]!.getBoundingClientRect();
            return {left: rect.left + scrollLeft, width: rect.width};
        });
        const rect = tab.getBoundingClientRect();
        const nextDrag: TabDragState = {
            id,
            order: originalOrder,
            originalOrder,
            pointerId: e.pointerId,
            pointerOffset: e.clientX - rect.left,
            pointerLeft: rect.left + scrollLeft,
            slots,
            draggedWidth: slots[tabIndex].width,
            dropping: false,
        };

        dragRef.current = nextDrag;
        setDrag(nextDrag);
        e.currentTarget.setPointerCapture(e.pointerId);
    }, [items]);

    const dragStyle = useCallback((id: string): React.CSSProperties | undefined => {
        if (!drag) return undefined;

        const baseSlot = drag.slots[drag.originalOrder.indexOf(id)];
        const destinationSlot = drag.slots[drag.order.indexOf(id)];
        if (!baseSlot || !destinationSlot) return undefined;

        const left = id === drag.id ? drag.pointerLeft : destinationSlot.left;
        return {transform: `translateX(${left - baseSlot.left}px)`};
    }, [drag]);

    return <div className={`${TabStripClass} ${className ?? ""}`} ref={tabsRef} role="tablist">
        {items.map(item => {
            const isActive = item.id === activeId;
            const closeIcon = hoveredClose === item.id ? item.closeIconOnHover ?? item.closeIcon ?? "close" : item.closeIcon ?? "close";
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
                data-suppress-transition={suppressTransitions}
                style={dragStyle(item.id)}
                onPointerDown={e => beginDrag(e, item.id)}
                onMouseDown={e => {
                    if (e.button === 1 && !(e.target as HTMLElement).closest(".tab-strip-close")) {
                        e.preventDefault();
                        onClose(item.id);
                    }
                }}
                onTransitionEnd={e => {
                    if (e.propertyName === "transform" && drag?.dropping && drag.id === item.id) commitDrop();
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
                <TooltipV2 tooltip={item.tooltip} side="top" triggerClassName={TitleTooltipTrigger}>
                    <Truncate className="tab-strip-title">{item.title}</Truncate>
                </TooltipV2>
                <TooltipV2 tooltip={item.closeTooltip} side="top" contentWidth={150} triggerClassName={CloseTooltipTrigger}>
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

const TabStripClass = injectStyle("tab-strip", k => `
    ${k} {
        min-width: 0;
        height: 100%;
        display: flex;
        align-items: stretch;
        gap: 4px;
        flex: 1 1 auto;
        overflow-x: auto;
        overflow-y: hidden;
    }

    ${k} .tab-strip-item {
        min-width: 184px;
        width: auto;
        max-width: none;
        height: 36px;
        margin-top: auto;
        padding: 0 4px 0 8px;
        display: flex;
        flex: 1 0 184px;
        align-items: center;
        gap: 6px;
        border: 1px solid var(--borderColor);
        border-radius: 7px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font-family: var(--sansSerif);
        font-size: 13px;
        line-height: 1;
        touch-action: none;
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

    ${k} .tab-strip-item[data-suppress-transition="true"] {
        transition: none !important;
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

    ${k} .tab-strip-title {
        width: 100%;
        min-width: 0;
        text-align: left;
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

    ${k} .tab-strip-close:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k} .tab-strip-item:hover .tab-strip-close,
    ${k} .tab-strip-item[data-active="true"] .tab-strip-close {
        opacity: 1;
    }
`);

const TitleTooltipTrigger = injectStyle("tab-strip-title-tooltip-trigger", k => `
    ${k} {
        min-width: 0;
        flex: 1 1 auto;
        overflow: hidden;
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
