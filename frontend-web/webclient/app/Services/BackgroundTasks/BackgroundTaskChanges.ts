import * as React from "react";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";

export interface BackgroundTaskChange {
    sequence: number;
    message: string;
}

class BackgroundTaskChangeStore extends ExternalStoreBase {
    private sequence = 0;
    private latestChange: BackgroundTaskChange | null = null;

    public report(message: string): void {
        this.latestChange = {sequence: ++this.sequence, message};
        this.emitChange();
    }

    public getSnapshot(): BackgroundTaskChange | null {
        return this.latestChange;
    }
}

export const backgroundTaskChangeStore = new BackgroundTaskChangeStore();

export function reportBackgroundTaskChange(message: string): void {
    backgroundTaskChangeStore.report(message);
}

export function useBackgroundTaskChanges(): BackgroundTaskChange | null {
    return React.useSyncExternalStore(
        listener => backgroundTaskChangeStore.subscribe(listener),
        () => backgroundTaskChangeStore.getSnapshot()
    );
}
