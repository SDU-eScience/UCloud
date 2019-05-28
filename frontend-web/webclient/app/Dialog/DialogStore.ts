type DialogStoreSubscriber = (dialogs: JSX.Element[]) => void;

class DialogStore {
    private dialogs: JSX.Element[];
    private subscribers: DialogStoreSubscriber[] = [];

    constructor() {
        this.dialogs = [];
    }

    public subscribe(subscriber: DialogStoreSubscriber) {
        this.subscribers.push(subscriber);
    }

    public unsubscribe(subscriber: DialogStoreSubscriber) {
        this.subscribers = this.subscribers.filter(it => it !== subscriber);
    }

    public popDialog(): void {
        let dialogs = this.dialogs.slice(1);
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs));
    }

    public addDialog(dialog: JSX.Element): void {
        let dialogs = [...this.dialogs, dialog];
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs));
    }
}

export const dialogStore = new DialogStore();