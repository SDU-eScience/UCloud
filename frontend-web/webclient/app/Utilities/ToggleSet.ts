import equal from "fast-deep-equal";
import {useCallback, useMemo, useRef, useState} from "react";
import * as React from "react";
import {useMemoWithLogging} from "@/Utilities/ReactUtilities";

export class ToggleSet<T> {
    private privateItems: T[] = [];
    get items(): T[] {
        return this.privateItems;
    }

    public toggle(item: T): void {
        const wasInSet = this.remove(item);
        if (wasInSet) return;

        this.fastAdd(item);
    }

    public has(item: T): boolean {
        return this.privateItems.some(it => equal(it, item))
    }

    /**
     * Removes an item from the set.
     *
     * @param item The item to remove
     * @return true if the set contains the item
     */
    public remove(item: T): boolean {
        const length = this.privateItems.length;
        for (let i = 0; i < length; i++) {
            const current = this.privateItems[i];
            if (equal(current, item)) {
                this.privateItems.splice(i, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an item to the set which is known to not already be in the set. Note that calling this function if
     * has(item) would return true will cause a duplicate entry to appear.
     *
     * Prefer toggle(item) if the current state is not known.
     *
     * @param item The item to add
     */
    public fastAdd(item: T): void {
        this.privateItems.push(item);
    }

    public clear(): void {
        this.privateItems = [];
    }

    public activateAll(items: T[]): void {
        for (const item of items) {
            if (!this.has(item)) {
                this.fastAdd(item);
            }
        }
    }
}

export interface ToggleSetHook<T> {
    checked: ToggleSet<T>;
    allChecked: boolean;
    toggle: (item: T) => void;
    checkAll: () => void;
    uncheckAll: () => void;
    allItems: React.MutableRefObject<T[]>;
}

export function useToggleSet<T>(items: T[]): ToggleSetHook<T> {
    const [checked, setChecked] = useState<{set: ToggleSet<T>}>({set: new ToggleSet()});
    const allItems = useRef<T[]>(items);
    const allChecked = checked.set.items.length > 0 && checked.set.items.length === allItems.current.length;
    const toggle = useCallback((job: T) => {
        checked.set.toggle(job);
        setChecked({...checked});
    }, [setChecked]);

    const checkAll = useCallback(() => {
        if (allChecked) {
            checked.set.clear();
        } else {
            checked.set.activateAll(allItems.current);
        }
        setChecked({...checked});
    }, [setChecked, allChecked]);

    const uncheckAll = useCallback(() => {
        checked.set.clear();
        setChecked({...checked});
    }, [setChecked, allChecked]);


    return useMemo(() => {
        return {checked: checked.set, allChecked, toggle, checkAll, uncheckAll, allItems};
    }, [checked, allChecked, toggle, checkAll, uncheckAll, allItems]);
}
