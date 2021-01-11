import equal from "fast-deep-equal";
import {useCallback, useMemo, useState} from "react";

export class ToggleSet<T> {
    private privateItems: T[] = [];
    get items(): T[] {
        return this.privateItems;
    }

    toggle(item: T): void {
        const wasInSet = this.remove(item);
        if (wasInSet) return;

        this.fastAdd(item);
    }

    has(item: T): boolean {
        return this.privateItems.some(it => equal(it, item))
    }

    /**
     * Removes an item from the set.
     *
     * @param item The item to remove
     * @return true if the set contains the item
     */
    remove(item: T): boolean {
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
    fastAdd(item: T) {
        this.privateItems.push(item);
    }

    clear() {
        this.privateItems = [];
    }

    activateAll(items: T[]) {
        for (const item of items) {
            if (!this.has(item)) {
                this.fastAdd(item);
            }
        }
    }
}

interface ToggleSetHook<T> {
    checked: ToggleSet<T>;
    allChecked: boolean;
    toggle: (item: T) => void;
    checkAll: () => void;
    uncheckAll: () => void;
}

export function useToggleSet<T>(items: T[]): ToggleSetHook<T> {
    const [checked, setChecked] = useState<{ set: ToggleSet<T> }>({set: new ToggleSet()});
    const allChecked = checked.set.items.length > 0 && checked.set.items.length === items.length;
    const toggle = useCallback((job: T) => {
        checked.set.toggle(job);
        setChecked({...checked});
    }, [setChecked]);

    const checkAll = useCallback(() => {
        if (allChecked) {
            checked.set.clear();
        } else {
            checked.set.activateAll(items);
        }
        setChecked({...checked});
    }, [setChecked, items, allChecked]);

    const uncheckAll = useCallback(() => {
        checked.set.clear();
        setChecked({...checked});
    }, [setChecked, items, allChecked]);


    return useMemo(() => {
        return {checked: checked.set, allChecked, toggle, checkAll, uncheckAll};
    }, [checked, allChecked, toggle, checkAll, uncheckAll]);
}