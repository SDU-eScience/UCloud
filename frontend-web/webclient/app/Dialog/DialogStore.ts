type DialogStoreSubscriber = (dialogs: JSX.Element[]) => void;

class DialogStore {
    private dialogs: {dialog: JSX.Element; onCancel: () => void}[];
    private subscribers: DialogStoreSubscriber[] = [];

    constructor() {
        this.dialogs = [];
    }

    public subscribe(subscriber: DialogStoreSubscriber): void {
        this.subscribers.push(subscriber);
    }

    public unsubscribe(subscriber: DialogStoreSubscriber): void {
        this.subscribers = this.subscribers.filter(it => it !== subscriber);
    }

    public addDialog = (dialog: JSX.Element, onCancel: () => void, addToFront = false): void => {
        const dialogs = addToFront ?
            [{dialog, onCancel}, ...this.dialogs] :
            [...this.dialogs, {dialog, onCancel}];
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs.map(el => el.dialog)));
    };

    public success(): void {
        this.popDialog();
    }

    public failure = (): void => {
        const [first] = this.dialogs;
        if (!!first) first.onCancel();
        this.popDialog();
    };

    private popDialog(): void {
        const dialogs = this.dialogs.slice(1);
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs.map(el => el.dialog)));
    }
}

export const dialogStore = new DialogStore();
