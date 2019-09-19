export default class PromiseKeeper {
    public canceledKeeper: boolean = false;
    private promises: Array<CancelablePromise<any>>;

    constructor() {
        this.promises = [];
    }

    public makeCancelable<T>(promise: Promise<T>): CancelablePromise<T> {
        const cancelablePromise: CancelablePromise<T> = {
            hasCanceled_: false,
            isComplete: false,
            promise: new Promise((resolve: any, reject: any) => {
                promise.then(
                    val => {
                        cancelablePromise.isComplete = true;
                        this.cleanup();
                        cancelablePromise.hasCanceled_ ? reject({isCanceled: true}) : resolve(val);
                    },
                    error => {
                        cancelablePromise.isComplete = true;
                        this.cleanup();
                        cancelablePromise.hasCanceled_ ? reject({isCanceled: true}) : reject(error);
                    }
                );
            }),
            cancel: () => cancelablePromise.hasCanceled_ = true,
        };
        this.promises.push(cancelablePromise);
        return cancelablePromise;
    }
    /**
     *  Cancels all promises stored in the promise keeper.
     *  The held promises are cleared from the keeper as they are cancelled and no longer have any function
     */
    public cancelPromises(): void {
        this.canceledKeeper = true;
        this.promises.forEach((it) => it.cancel());
        this.promises = [];
    }

    private cleanup(): void {
        this.promises = this.promises.filter(it => !it.isComplete);
    }
}

interface CancelablePromise<T> {
    isComplete: boolean;
    promise: Promise<T>;
    cancel: VoidFunction;
    hasCanceled_: boolean;
}
