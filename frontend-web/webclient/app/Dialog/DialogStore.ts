class DialogStore {
    dialogs: JSX.Element[]

    constructor() {
        this.dialogs = [];
    }

    public popDialog(): void {
        this.dialogs.pop();
    }

    public get current(): JSX.Element | undefined {
        return this.dialogs[0];
    }

    public addDialog(dialog: JSX.Element): void {
        this.dialogs.push(dialog)
    }
}

export default new DialogStore();