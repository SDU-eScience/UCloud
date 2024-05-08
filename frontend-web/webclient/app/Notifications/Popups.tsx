import * as React from "react";
import {useEffect, useCallback} from "react";
import {doNothing} from "@/UtilityFunctions";
import {NotificationProps, NotificationCard} from "./Card";
import {timestampUnixMs} from "@/UtilityFunctions";
import {useForcedRender} from "@/Utilities/ReactUtilities";

// NOTE(Dan): The <NotificationPopups> component is responsible for displaying incoming <NotificationCard>s in an
// orderly manner. A new notification is triggered with `triggerNotificationPopup` which will display the notification
// if possible. 
//
// The container allows for up to 6 notifications shown at once, two of which are reserved for pinned
// notifications. No more than two pinned notification can be displayed at any point in time. Pinned notifications
// will be placed in a queue if there are already more than two pinned notifications shown. Up to 6 normal
// notifications can be shown at a time. Normal notifications never enter a queue, to avoid the problem where
// notifications enter the screen for a very long time. Thus if 50 notifications are fired, then up to 6 are shown and
// the rest are discarded (moved to the tray).
//
// This component does not use a React way of programming, it also doesn't use Redux for state management. This is a 
// very intentional choice, since I believe the logic is easier to manage this way. For the most part we simply manage
// the state globally (the component is assumed to be mounted at all relevant times) and force a rerender of the
// active component via the `callback` variable.
//
const NORMAL_DURATION = 3000;
const EXIT_ANIMATION = 500;
const CARD_SIZE = 61;
const CARD_GAP = 16;
const DEMO = false; // Set DEMO to true to see a bunch of notifications rolling in.

type NotificationWithSnooze = NotificationProps & { onSnooze?: (props: NotificationProps) => void };

// NOTE(Dan): Callback to force a rerender. This is set by <NotificationPopups> when mounting. This should only be
// invoked through `triggerCallback()`.
let callback: () => void = doNothing;

// NOTE(Dan): Basic ever increasing number to use as a React key for the <NotificationCard>
let idAllocator = 0;

interface PinnedNotification {
    notification: NotificationWithSnooze;
    needsExit: boolean;
    slotIdx: number;
}

// NOTE(Dan): Contains state about an active (normal) notification.
interface ActiveNotification {
    notification?: NotificationWithSnooze;
    createdAt: number;
    needsExit: boolean;
    isPaused: boolean;
    uniqueId: number;
}

const emptyNotification: ActiveNotification = {createdAt: 0, needsExit: false, isPaused: false, uniqueId: 0};

const pinnedQueue: NotificationWithSnooze[] = [];

// NOTE(Dan): The size of pinnedSlots is manually loop unrolled below. This should be changed before naively changing
// the size of pinnedSlots.
const pinnedSlots: (PinnedNotification | null)[] = Array(2).fill(null);

// NOTE(Dan): The size of normal slots is not loop unrolled below and can be changed freely. Do note that normalSlots
// shares its 'slots' with pinnedSlots. That means if pinnedSlots has to active notifications then the first two slots
// of normalSlots will always be empty.
const normalSlots: ActiveNotification[] = Array(6).fill(emptyNotification).map(it => ({...emptyNotification}));

// NOTE(Dan): Adds a new notification to the container. This should not be invoked directly by most code, as this code
// does not add it to the notification tray.
export function triggerNotificationPopup(notification: NotificationWithSnooze) {
    if (notification.isPinned) {
        const slotIdx = pinnedQueue.length > 0 ? -1 : pinnedSlots[0] === null ? 0 : pinnedSlots[1] === null ? 1 : -1;
        if (slotIdx === -1) {
            pinnedQueue.push(notification);
        } else {
            if (normalSlots[slotIdx].notification !== undefined) {
                pinnedQueue.push(notification);
            } else {
                pinnedSlots[slotIdx] = {notification, slotIdx, needsExit: false};
            }
        }
    } else {
        const earliestLegalSlot = 
            pinnedQueue.length > 0 ? Math.min(pinnedQueue.length, 2) : 
            pinnedSlots[1] !== null ? 2 : 
            pinnedSlots[0] !== null ? 1 : 
            0;

        let foundSlot: ActiveNotification | null = null;
        for (let i = 0; i < normalSlots.length; i++) {
            if (i < earliestLegalSlot) continue;
            const slot = normalSlots[i];
            if (slot.notification !== undefined) continue;
            foundSlot = slot;
            slot.needsExit = false;
            slot.notification = notification;
            slot.createdAt = timestampUnixMs();
            slot.uniqueId = idAllocator++;
            slot.isPaused = false;
            break;
        }
        if (foundSlot) {
            startExitTimer(foundSlot);
        }
    }

    triggerCallback();
}

function triggerCallback() {
    // NOTE(Dan): Start out by trying to move items from the pinned queue into the pinned slots. We can do this if we
    // have available pinned slots and the normal slots aren't blocking.
    if (pinnedQueue.length > 0) {
        const nextSlotIdx = pinnedSlots[0] === null ? 0 : pinnedSlots[1] === null ? 1 : -1;
        if (nextSlotIdx !== -1) {
            if (normalSlots[nextSlotIdx].notification === undefined) {
                const notification = pinnedQueue.shift();
                if (notification) {
                    pinnedSlots[nextSlotIdx] = {notification, slotIdx: nextSlotIdx, needsExit: false};
                    triggerCallback(); // Try to do it again.
                }
                return;
            }
        }
    }

    callback();
}

function startExitTimer(slot: ActiveNotification, duration: number = NORMAL_DURATION) {
    setTimeout(() => {
        if (slot.isPaused) {
            startExitTimer(slot, 500);
        } else {
            slot.needsExit = true;
            triggerCallback();
            startDeletionTimer(slot);
        }
    }, duration)
}

function startDeletionTimer(slot: ActiveNotification) {
    setTimeout(() => {
        if (slot.isPaused) {
            startDeletionTimer(slot);
        } else {
            slot.notification = undefined;
            triggerCallback();
        }
        // NOTE(Dan): We often get flashes if we wait until the end. This is probably because setTimeout is not 
        // super precise and React isn't always super fast.
    }, EXIT_ANIMATION - 30); 
}

export const NotificationPopups: React.FunctionComponent = () => {
    const rerender = useForcedRender();

    useEffect(() => {
        callback = rerender;
        return () => { callback = doNothing; };
    }, []);

    const onMouseEnter = useCallback((userData?: any) => {
        (userData as ActiveNotification).isPaused = true;
    }, []);

    const onMouseLeave = useCallback((userData?: any) => {
        (userData as ActiveNotification).isPaused = false;
    }, []);

    const onSnooze = useCallback((userData?: any) => {
        const pin = (userData as PinnedNotification);
        pin.needsExit = true;
        rerender();
        setTimeout(() => {
            pinnedSlots[pin.slotIdx] = null;
            rerender();
        }, 500);

        pin.notification.onSnooze?.(pin.notification);
    }, []);

    useEffect(() => {
        if (DEMO) {
            let counter = 0;
            setInterval(() => {
                triggerNotificationPopup({
                    icon: "mail",
                    title: `Notification ${counter}`,
                    body: "This is a test notification!",
                    isPinned: false,
                    uniqueId: `${counter}`,
                });
                counter++;
            }, 600);
        }
    }, []);

    const elems: React.ReactNode[] = [];

    const baseOffset = 12;

    for (let i = 0; i < pinnedSlots.length; i++) {
        const slot = pinnedSlots[i];
        if (slot === null) continue;

        elems.push(
            <NotificationCard 
                key={i} 
                top={`${baseOffset + (CARD_SIZE + CARD_GAP) * i}px`} 
                exit={slot.needsExit} 
                callbackItem={slot}
                {...slot.notification} 
                onSnooze={onSnooze}
            />
        );
    }

    for (let i = 0; i < normalSlots.length; i++) {
        const slot = normalSlots[i];
        if (slot.notification !== undefined) {
            elems.push(
                <NotificationCard 
                    key={slot.uniqueId} 
                    top={`${baseOffset + (CARD_SIZE + CARD_GAP) * i}px`} 
                    exit={slot.needsExit} 
                    {...slot.notification} 
                    callbackItem={slot}
                    onMouseEnter={onMouseEnter}
                    onMouseLeave={onMouseLeave}
                />
            );
        }
    }

    return <>{elems}</>;
};

