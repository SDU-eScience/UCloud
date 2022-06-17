import {NotificationProps} from "./NotificationCard";
import {doNothing} from "@/UtilityFunctions";

export const SendNotificationCb: { callback: (notification: NotificationProps) => void } = { callback: doNothing };

export function sendNotification(notification: NotificationProps) {
    SendNotificationCb.callback(notification);
}

