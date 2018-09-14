export default class PromiseKeeper {
    promises: Array<CancelablePromise<any>>
    constructor() {
        this.promises = [];
    }

    _cleanup(): void {
        this.promises = this.promises.filter(it => !it.isComplete);
    }

    makeCancelable<T>(promise: Promise<T>): CancelablePromise<T> {
        let hasCanceled_ = false;
        let cancelablePromise = new CancelablePromise<T>();
        cancelablePromise.promise = new Promise((resolve: any, reject: any) => {
            promise.then(
                val => {
                    cancelablePromise.isComplete = true;
                    this._cleanup();
                    hasCanceled_ ? reject({ isCanceled: true }) : resolve(val);
                },
                error => {
                    cancelablePromise.isComplete = true;
                    this._cleanup();
                    hasCanceled_ ? reject({ isCanceled: true }) : reject(error)
                }
            );
        });
        cancelablePromise.cancel = () => cancelablePromise.hasCanceled_ = true;
        this.promises.push(cancelablePromise);
        return cancelablePromise;
    };

    /**
     *  Cancels all promises stored in the promise keeper.
     *  The held promises are cleared from the keeper as they are cancelled and no longer have any function
     */
    cancelPromises(): void {
        this.promises.forEach((it) => it.cancel());
        this.promises = [];
    }
}

class CancelablePromise<T> {
    isComplete: boolean
    promise: Promise<T>
    cancel: VoidFunction
    hasCanceled_: boolean
    constructor() { }
}