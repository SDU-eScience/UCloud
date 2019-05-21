class DialogStore {
    private dialogs: JSX.Element[]
    private modal: React.PureComponent;

    constructor() {
        this.dialogs = [];
    }


    public attach(modal: React.PureComponent) {
        this.modal = modal;
    }

    public popDialog(): void {
        this.dialogs.pop();
        this.modal.forceUpdate();
    }

    public get current(): JSX.Element | undefined {
        return this.dialogs[0];
    }

    public get dialogCount() {
        return this.dialogs.length;
    }

    public addDialog(dialog: JSX.Element): void {
        this.dialogs.push(dialog);
        this.modal.forceUpdate();
    }
}


export const dialogStore = new DialogStore();