import {LocalStorageCache} from "@/Utilities/LocalStorageCache";
import {timestampUnixMs} from "@/UtilityFunctions";

const SNOOZE_TIMES_MS: number[] = [
    1000 * 60 * 60 * 4,
    1000 * 60 * 60 * 15,
    1000 * 60 * 60 * 60,
];

interface SnoozeData {
    lastSnooze: number;
    snoozeCounter: number;
}

const sessionAppearance: Record<string, number> = {};
const globalSnoozeData = new LocalStorageCache<Record<string, SnoozeData>>("snooze-data");

export function isDismissed(notificationId: string): boolean {
    const globalData = globalSnoozeData.retrieve() ?? {};
    const mySnooze = globalData[notificationId]?.snoozeCounter ?? 0;

    return mySnooze <= SNOOZE_TIMES_MS.length;
}

export function snooze(notificationId: string) {
    const globalData = globalSnoozeData.retrieve() ?? {};
    const mySnooze = globalData[notificationId];
    const now = timestampUnixMs();
    if (!mySnooze) {
        globalData[notificationId] = { lastSnooze: now, snoozeCounter: 1 };
    } else {
        globalData[notificationId] = {
            lastSnooze: now, 
            snoozeCounter: mySnooze.snoozeCounter + 1,
        };
    }

    globalSnoozeData.update(globalData);
}

export function trackAppearance(notificationId: string) {
    const now = timestampUnixMs();
    sessionAppearance[notificationId] = now;
}

export function shouldAppear(notificationId: string): boolean {
    const globalData = globalSnoozeData.retrieve() ?? {};
    const mySnooze = globalData[notificationId];
    const lastApperance = sessionAppearance[notificationId];
    if (!mySnooze && !lastApperance) return true;
    if (!mySnooze) return false;

    if (mySnooze.snoozeCounter === 0 && !lastApperance) return true;
    const snoozeIdx = mySnooze.snoozeCounter - 1;
    if (snoozeIdx < SNOOZE_TIMES_MS.length) {
        const nextAppearance = (mySnooze.lastSnooze + SNOOZE_TIMES_MS[snoozeIdx]);
        return timestampUnixMs() >= nextAppearance && (!lastApperance || lastApperance < nextAppearance);
    }

    return false;
}

