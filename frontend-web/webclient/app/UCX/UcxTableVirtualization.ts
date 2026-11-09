import {RefObject, useCallback, useLayoutEffect, useMemo, useRef, useState} from "react";

export type UcxTableItem =
    | {key: string; kind: "row"; rowKey: string}
    | {key: string; kind: "group"; group: string}
    | {key: string; kind: "empty"}
    | {key: string; kind: "trailing"};

function ucxTableItemIndex(offsets: number[], position: number): number {
    let low = 0;
    let high = offsets.length - 2;
    while (low < high) {
        const middle = Math.floor((low + high) / 2);
        if (offsets[middle + 1] <= position) low = middle + 1;
        else high = middle;
    }
    return low;
}

export function useUcxTableVirtualization(
    items: UcxTableItem[],
    scrollRef: RefObject<HTMLDivElement | null>,
    bodyRef: RefObject<HTMLTableSectionElement | null>,
) {
    const heights = useRef(new Map<string, number>());
    const pendingRow = useRef<string | null>(null);
    const scrollAdjustment = useRef(0);
    const [measurementRevision, setMeasurementRevision] = useState(0);
    const [viewport, setViewport] = useState({top: 0, height: 0, headerHeight: 0});
    const updateViewport = useCallback(() => {
        const scroll = scrollRef.current;
        if (!scroll) return;
        const headerHeight = scroll.querySelector("thead")?.getBoundingClientRect().height ?? 0;
        const next = {top: scroll.scrollTop, height: scroll.clientHeight, headerHeight};
        setViewport(previous => previous.top === next.top && previous.height === next.height && previous.headerHeight === next.headerHeight
            ? previous : next);
    }, [scrollRef]);

    useLayoutEffect(() => {
        const scroll = scrollRef.current;
        if (!scroll) return;
        const observer = new ResizeObserver(updateViewport);
        observer.observe(scroll);
        const header = scroll.querySelector("thead");
        if (header) observer.observe(header);
        updateViewport();
        return () => observer.disconnect();
    }, [scrollRef, updateViewport]);

    const offsets = useMemo(() => {
        const result = [0];
        for (const item of items) {
            const estimate = item.kind === "trailing" ? 68 : item.kind === "group" ? 53 : 44;
            result.push(result[result.length - 1] + (heights.current.get(item.key) ?? estimate));
        }
        return result;
    }, [items, measurementRevision]);

    const itemPositions = useMemo(() => {
        const rows = new Map<string, number>();
        const keys = new Map<string, number>();
        const groups: number[] = [];
        let groupIndex = -1;
        for (let index = 0; index < items.length; index++) {
            const item = items[index];
            keys.set(item.key, index);
            if (item.kind === "group") groupIndex = index;
            if (item.kind === "row") rows.set(item.rowKey, index);
            groups.push(groupIndex);
        }
        return {rows, keys, groups};
    }, [items]);

    const visible = useMemo(() => {
        if (items.length === 0) return [];
        const first = ucxTableItemIndex(offsets, viewport.top);
        const last = ucxTableItemIndex(offsets, viewport.top + Math.max(0, viewport.height - viewport.headerHeight));
        const start = Math.max(0, first - 8);
        const end = Math.min(items.length, last + 9);
        const indices: number[] = [];
        const groupIndex = itemPositions.groups[first];
        if (groupIndex >= 0 && groupIndex < start) indices.push(groupIndex);
        for (let index = start; index < end; index++) indices.push(index);
        return indices;
    }, [items, itemPositions, offsets, viewport]);

    useLayoutEffect(() => {
        const scroll = scrollRef.current;
        if (!scroll || scrollAdjustment.current === 0) return;
        if (pendingRow.current === null) scroll.scrollTop += scrollAdjustment.current;
        scrollAdjustment.current = 0;
        updateViewport();
    }, [offsets, scrollRef, updateViewport]);

    useLayoutEffect(() => {
        const body = bodyRef.current;
        if (!body) return;
        const measure = () => {
            let changed = false;
            const first = ucxTableItemIndex(offsets, scrollRef.current?.scrollTop ?? 0);
            for (const element of Array.from(body.children)) {
                if (!(element instanceof HTMLTableRowElement)) continue;
                const key = element.dataset.ucxVirtualKey;
                if (key === undefined) continue;
                const height = element.getBoundingClientRect().height;
                if (height <= 0 || heights.current.get(key) === height) continue;
                const index = itemPositions.keys.get(key)!;
                if (index < first) {
                    scrollAdjustment.current += height - (heights.current.get(key) ?? offsets[index + 1] - offsets[index]);
                }
                heights.current.set(key, height);
                changed = true;
            }
            if (changed) setMeasurementRevision(value => value + 1);
            updateViewport();
        };
        const observer = new ResizeObserver(measure);
        for (const element of Array.from(body.children)) {
            if (element instanceof HTMLTableRowElement && element.dataset.ucxVirtualKey !== undefined) observer.observe(element);
        }
        measure();
        return () => observer.disconnect();
    }, [bodyRef, itemPositions, items, offsets, scrollRef, visible, updateViewport]);

    useLayoutEffect(() => {
        const keys = new Set(items.map(item => item.key));
        for (const key of heights.current.keys()) {
            if (!keys.has(key)) heights.current.delete(key);
        }
    }, [items]);

    const scrollToRow = useCallback((rowKey: string) => {
        const scroll = scrollRef.current;
        const index = itemPositions.rows.get(rowKey);
        if (!scroll || index === undefined) return;
        const groupIndex = itemPositions.groups[index];
        const groupHeight = groupIndex >= 0 ? offsets[groupIndex + 1] - offsets[groupIndex] : 0;
        const top = offsets[index] - groupHeight;
        const bottom = offsets[index + 1];
        if (top < scroll.scrollTop) scroll.scrollTop = Math.max(0, top);
        else if (bottom > scroll.scrollTop + scroll.clientHeight - viewport.headerHeight) {
            scroll.scrollTop = bottom - scroll.clientHeight + viewport.headerHeight;
        }
        updateViewport();
    }, [itemPositions, offsets, scrollRef, updateViewport, viewport.headerHeight]);

    useLayoutEffect(() => {
        const rowKey = pendingRow.current;
        if (rowKey === null) return;
        const index = itemPositions.rows.get(rowKey);
        if (index === undefined) {
            pendingRow.current = null;
            return;
        }
        scrollToRow(rowKey);
        if (visible.includes(index) && visible.every(itemIndex =>
            heights.current.get(items[itemIndex].key) === offsets[itemIndex + 1] - offsets[itemIndex])) {
            pendingRow.current = null;
        }
    }, [itemPositions, items, offsets, scrollToRow, visible]);

    const revealRow = useCallback((rowKey: string) => {
        pendingRow.current = rowKey;
        scrollToRow(rowKey);
    }, [scrollToRow]);

    return {offsets, visible, updateViewport, scrollToRow: revealRow, headerHeight: viewport.headerHeight};
}
