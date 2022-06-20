import {LocalStorageCache} from "@/Utilities/LocalStorageCache";
import {timestampUnixMs} from "@/UtilityFunctions";

const SNOOZE_TIMES_MS: number[] = [1000, 5000, 10_000];

interface SnoozeData {
    lastSnooze: number;
    snoozeCounter: number;
}

const globalSnoozeData = new LocalStorageCache<Record<string, SnoozeData>>("snooze-data");

export function isDismissed(notificationId: string): boolean {
    const globalData = globalSnoozeData.retrieve() ?? {};
    const mySnooze = globalData[notificationId]?.snoozeCounter ?? 0;

    return mySnooze <= SNOOZE_TIMES_MS.length;
}

export function snooze(notificationId: string) {
    const globalData = globalSnoozeData.retrieve() ?? {};
    const mySnooze = globalData[notificationId];
    if (!mySnooze) {
        globalData[notificationId] = { lastSnooze: timestampUnixMs(), snoozeCounter: 1 };
    } else {
        globalData[notificationId] = { lastSnooze: timestampUnixMs(), snoozeCounter: mySnooze.snoozeCounter + 1 };
    }

    globalSnoozeData.update(globalData);
}

export function shouldAppear(notificationId: string): boolean {
    const globalData = globalSnoozeData.retrieve() ?? {};
    const mySnooze = globalData[notificationId];
    if (!mySnooze) return true;

    const snoozeIdx = mySnooze.lastSnooze - 1;
    if (snoozeIdx >= 0 && snoozeIdx < SNOOZE_TIMES_MS.length) {
        return timestampUnixMs() >= (mySnooze.lastSnooze + SNOOZE_TIMES_MS[snoozeIdx]);
    }

    return false;
}


