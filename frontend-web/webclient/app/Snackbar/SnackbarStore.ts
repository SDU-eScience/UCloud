import {Snack, SnackType} from "@/Snackbar/Snackbars";
import {hackySetFailureCallback, hackySetSuccessCallback, timestampUnixMs} from "@/UtilityFunctions";

type SnackbarSubscriber = (activeSnack?: Snack) => void;

class SnackbarStore {
    private subscribers: SnackbarSubscriber[] = [];
    private snackQueue: Snack[] = [];
    private activeExpiresAt = -1;

    public subscribe(subscriber: SnackbarSubscriber): void {
        this.subscribers.push(subscriber);
    }

    public unsubscribe(subscriber: SnackbarSubscriber): void {
        this.subscribers = this.subscribers.filter(it => it !== subscriber);
    }

    public addSnack(snack: Snack): void {
        this.snackQueue.push(snack);

        if (this.activeExpiresAt === -1) {
            this.process();
        }
    }

    public addFailure(message: string, addAsNotification: boolean, lifetime?: number): void {
        this.addSnack({
            message,
            type: SnackType.Failure,
            addAsNotification,
            lifetime,
        });
    }

    public addSuccess(message: string, addAsNotification: boolean, lifetime?: number): void {
        this.addSnack({
            message,
            type: SnackType.Success,
            addAsNotification,
            lifetime
        });
    }

    public addInformation(message: string, addAsNotification: boolean, lifetime?: number): void {
        this.addSnack({
            message,
            type: SnackType.Information,
            addAsNotification,
            lifetime
        });
    }

    public requestCancellation(): void {
        if (this.activeExpiresAt !== -1) {
            // Setting this to 0 will cause the invariant (-1 means no active) to still be true while still cancelling
            // in next loop.
            this.activeExpiresAt = 0;
        }
    }

    private process(): void {
        setTimeout(() => {
            const now = timestampUnixMs();
            const deadline = this.activeExpiresAt;

            if (deadline === -1 || now >= deadline) {
                const next = this.snackQueue.pop();

                // If queue is empty return and don't renew loop.
                if (next === undefined) {
                    this.subscribers.forEach(it => it(undefined));
                    this.activeExpiresAt = -1;
                    return;
                }

                const lifetime = next.lifetime ?? (next.message.length > 80 ? 10_000 : 5000);
                this.activeExpiresAt = now + lifetime;
                this.subscribers.forEach(it => it(next));
            }

            this.process();
        }, 50);
    }
}


export const snackbarStore = new SnackbarStore();
hackySetSuccessCallback(msg => {
    snackbarStore.addSuccess(msg, false);
});
hackySetFailureCallback(msg => {
    snackbarStore.addFailure(msg, false);
});
