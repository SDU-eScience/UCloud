import {ReactNode} from "react";

export interface Dialog {
    element: ReactNode;
    style?: Record<string, any>;
}
type DialogStoreSubscriber = (dialogs: Dialog[]) => void;

class DialogStore {
    private dialogs: {dialog: ReactNode; onCancel: () => void; style?: Record<string, any>}[];
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

    public addDialog(dialog: ReactNode, onCancel: () => void, addToFront = false, style?: Record<string, any>): void {
        const d = {dialog, onCancel, style};
        const dialogs = addToFront ?
            [d, ...this.dialogs] :
            [...this.dialogs, d];
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs.map(el => ({element: el.dialog, style: el.style, onCancel: onCancel}))));
    }

    public success(): void {
        this.popDialog();
    }

    public failure(): void {
        const [first] = this.dialogs;
        if (!!first) first.onCancel();
        this.popDialog();
    }

    private popDialog(): void {
        const dialogs = this.dialogs.slice(1);
        this.dialogs = dialogs;
        this.subscribers.forEach(it => it(dialogs.map(el => ({element: el.dialog, style: el.style}))));
    }
}

export const dialogStore = new DialogStore();
