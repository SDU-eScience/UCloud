export class LocalStorageCache<T> {
    private readonly key: string;
    private value: T | null = null;

    constructor(key: string) {
        this.key = key;
    }

    retrieve(): T | null {
        if (this.value) return this.value;

        const text = localStorage.getItem(this.key);
        if (!text) return null;
        try {
            return JSON.parse(text) as T;
        } catch (e) {
            return null;
        }
    }

    update(item: T) {
        localStorage.setItem(this.key, JSON.stringify(item));
        this.value = item;
    }

    clear() {
        localStorage.removeItem(this.key);
        this.value = null;
    }
}
