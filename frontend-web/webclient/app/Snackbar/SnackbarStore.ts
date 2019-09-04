import {Snack, SnackType} from "Snackbar/Snackbars";
import {timestampUnixMs} from "UtilityFunctions";

type SnackbarSubscriber = (activeSnack?: Snack) => void;

class SnackbarStore {
    private subscribers: SnackbarSubscriber[] = [];
    private snackQueue: Snack[] = [];
    private activeExpiresAt: number = -1;

    public subscribe(subscriber: SnackbarSubscriber) {
        this.subscribers.push(subscriber);
    }

    public unsubscribe(subscriber: SnackbarSubscriber) {
        this.subscribers = this.subscribers.filter(it => it !== subscriber);
    }

    public addSnack(snack: Snack) {
        this.snackQueue.push(snack);

        if (this.activeExpiresAt == -1) {
            this.process();
        }
    }

    public addFailure(message: string, lifetime?: number) {
        this.addSnack({
            message,
            type: SnackType.Failure,
            lifetime
        })
    }

    requestCancellation() {
        if (this.activeExpiresAt !== -1) {
            // Setting this to 0 will cause the invariant (-1 means no active) to still be true while still cancelling
            // in next loop.
            this.activeExpiresAt = 0;
        }
    }

    process() {
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

                const lifetime = next.lifetime || 3000;
                this.activeExpiresAt = now + lifetime;
                this.subscribers.forEach(it => it(next));
            }

            this.process();
        }, 50);
    }
}

export const snackbarStore = new SnackbarStore();
