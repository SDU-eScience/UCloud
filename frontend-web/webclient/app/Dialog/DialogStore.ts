type DialogStoreSubscriber = (dialogs: JSX.Element[]) => void;

class DialogStore {
    private dialogs: Array<{dialog: JSX.Element, onCancel: () => void}>;
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

    public addDialog(dialog: JSX.Element, onCancel: () => void): void {
        const dialogs = [...this.dialogs, {dialog, onCancel}];
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs.map(el => el.dialog)));
    }

    public success() {
        this.popDialog();
    }

    public failure() {
        const [first] = this.dialogs;
        if (!!first) first.onCancel();
        this.popDialog();
    }

    private popDialog(): void {
        const dialogs = this.dialogs.slice(1);
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs.map(el => el.dialog)));
    }
}

export const dialogStore = new DialogStore();
