import equal from "fast-deep-equal";

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
}
