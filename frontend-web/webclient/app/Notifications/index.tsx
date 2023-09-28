import {WSFactory} from "@/Authentication/HttpClientInstance";
import {formatDistance} from "date-fns/esm";
import * as React from "react";
import {Snack} from "@/Snackbar/Snackbars";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ThemeProvider} from "styled-components";
import {Link, Button, Absolute, Flex, Icon, Relative} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {TextSpan} from "@/ui-components/Text";
import theme, {ThemeColor} from "@/ui-components/theme";
import * as UF from "@/UtilityFunctions";
import {NotificationProps as NormalizedNotification} from "./Card";
import * as Snooze from "./Snooze";
import {callAPI} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {triggerNotificationPopup, NotificationPopups} from "./Popups";
import {useForcedRender} from "@/Utilities/ReactUtilities";
import {timestampUnixMs} from "@/UtilityFunctions";
import {Dispatch} from "redux";
import {Location, NavigateFunction, useLocation, useNavigate} from "react-router";
import {useDispatch, useSelector} from "react-redux";
import HighlightedCard from "@/ui-components/HighlightedCard";
import * as Heading from "@/ui-components/Heading";
import {WebSocketConnection} from "@/Authentication/ws";
import AppRoutes from "@/Routes";
import {classConcatArray, injectStyle} from "@/Unstyled";

// NOTE(Dan): If you are in here, then chances are you want to attach logic to one of the notifications coming from
// the backend. You can do this by editing the following two functions: `resolveNotification()` and
// `onNotificationAction()`.
//
// If you are here to learn about sending a notification, then you can jump down to `sendNotification()`.
//
// Otherwise, look further down where the general concepts are explained.
function resolveNotification(event: Notification): {
    icon: IconName;
    color?: ThemeColor;
    color2?: ThemeColor;
    modifiedTitle?: string;
    modifiedMessage?: JSX.Element | string;
} {
    switch (event.type) {
        case "REVIEW_PROJECT":
            return {icon: "projects", color: "black", color2: "midGray"};
        case "SHARE_REQUEST":
            return {icon: "share", color: "black", color2: "black"};
        case "PROJECT_INVITE":
            return {icon: "projects", color: "black", color2: "midGray"};
        case "PROJECT_ROLE_CHANGE":
            return {icon: "projects", color: "black", color2: "midGray"};
        case "PROJECT_USER_LEFT":
            return {icon: "projects", color: "black", color2: "midGray"};
        case "PROJECT_USER_REMOVED":
            return {icon: "projects", color: "black", color2: "midGray"};
        case "NEW_GRANT_APPLICATION":
            const icon = "mail";
            const oldPrefix = "New grant application to ";
            if (event.message.indexOf(oldPrefix) === 0) {
                const modifiedTitle = event.message.substring(oldPrefix.length);
                const modifiedMessage = "You have received a new grant application to review.";
                return {icon, modifiedMessage, modifiedTitle};
            }
            return {icon};
        case "APP_COMPLETE":
        default:
            return {icon: "info", color: "white", color2: "black"};
    }
}

function onNotificationAction(notification: Notification, navigate: NavigateFunction, dispatch: Dispatch) {
    switch (notification.type) {
        case "APP_COMPLETE":
            navigate(`/applications/results/${notification.meta.jobId}`);
            break;
        case "SHARE_REQUEST":
            navigate("/shares");
            break;
        case "REVIEW_PROJECT":
        case "PROJECT_INVITE":
            navigate("/projects/");
            break;
        case "NEW_GRANT_APPLICATION":
        case "COMMENT_GRANT_APPLICATION":
        case "GRANT_APPLICATION_RESPONSE":
        case "GRANT_APPLICATION_UPDATED": {
            const {meta} = notification;
            navigate(`/project/grants/view/${meta.appId}`);
            break;
        }
        case "PROJECT_ROLE_CHANGE": {
            navigate(AppRoutes.project.members());
            break;
        }
        default:
            console.warn("unhandled");
            console.warn(notification);
            break;
    }
}

// NOTE(Dan): The code of this module contain all the relevant logic and components to control the notification system
// of UCloud. A notification is UCloud acts as a hint to the user that an interesting event has occured, in doing so
// it invites the user to take action in response to said event. This is the main difference between the "Snackbar"
// functionality of UCloud which is only meant to notify the user of some event which does not require any user
// action.
//
// The anatomy of an notification is roughly as follows:
//
//
//  /-----------------------------------------\
// X +--+ --Title--                      Date  X
// X |  | --Message----------------            X
// X +--+                                      X
//  \-----------------------------------------/
//
// Each notification has:
//
// - An icon, which indicates roughly the type of event which has occured.
// - A title and a message, which provides a more detailed description of the event.
// - A timestamp, telling the user when this occured. Old notifications are rarely useful.
// - An action, which allows the user to act on the notification.
// - A flag which indicates if the user has read the message or not.
//
// Two types of notifications exist in UCloud. Normal notifications and pinned notifications. A normal notification
// will popup on the user's screen for a few seconds after which it disappears and can be found in the notification
// tray. The notification tray is placed in the header, and can be opened to view the most recent notifications (see
// `<Notifications>`). A pinned notification, however, stays on the user's screen until they are either dealt with or
// snoozed. A snoozed notification will eventually return to the user. After the user has snoozed a notification a few
// times, the snooze option is replaced with a "dismiss" option. After which the notification will not re-appear.
// Pinned notifications are used to notify the end-user of critical events which are important that they act on.
//
// Notifications, in the frontend, come from a few different sources:
//
// 1. Backend notification system. Old ones are fetched through a normal RPC call while new ones are fetched using a
//    WebSocket subscription.
// 2. From the `sendNotification` call. This allows the frontend to generate its own notifications. This is currently
//    the only way of generating a pinned notification.
// 3. From the `snackbarStore`. This is done mostly for legacy reasons with the old snackbars, which could optionally
//    be added to the notification tray. Using the snackbars this way will no longer generate a snackbar, since this
//    would have caused two distinct popups instead of one.
//
// All notifications, regardless of source, are normalized using the `normalizeNotification()` function. The output of
// this function is used throughout all the other components. These notifications are stored in the
// `notificationStore` and UI components can subscribe to changes through the `notificationCallbacks`:
const notificationStore: NormalizedNotification[] = [];

const notificationCallbacks = new Set<() => void>();
function renderNotifications() {
    for (const callback of notificationCallbacks) {
        callback();
    }
}

// NOTE(Dan): The frontend can generate its own notification through `sendNotification()`. This is generally preferred
// over using the `snackbar` functions, as these allow for greater flexibility.
export function sendNotification(notification: NormalizedNotification) {
    const normalized = normalizeNotification(notification);
    for (const item of notificationStore) {
        if (item.uniqueId === normalized.uniqueId) return;
    }

    notificationStore.unshift(normalized);
    renderNotifications();

    if (!notification.isPinned || Snooze.shouldAppear(normalized.uniqueId)) {
        if (notification.isPinned) Snooze.trackAppearance(normalized.uniqueId);
        triggerNotificationPopup(normalized);
    }
}

// NOTE(Dan): The `notificationStore` is filled from various sources (as described above). The `initializeStore()`
// function is responsible for pulling information from these sources and pushing it into the `notificationStore`.
// When UI updates are required, then this function will invoke `renderNotifications()` to trigger a UI update in all
// relevant components.
let wsConnection: WebSocketConnection | undefined = undefined;
let snackbarSubscription: (snack?: Snack) => void = () => { };
let snoozeLoop: any;
function initializeStore() {
    // NOTE(Dan): We first fetch a history of old events. These are only added to the tray and do not trigger a popup.
    callAPI<Page<Notification>>({
        context: "",
        path: buildQueryString("/api/notifications", {itemsPerPage: 250}),
    }).then(resp => {
        for (const item of resp.items) {
            notificationStore.push(normalizeNotification(item));
        }
        renderNotifications();
    });

    // NOTE(Dan): New messages from the WebSocket subscription do trigger a notification, since we will only receive
    // notifications which are created after the subscription.
    wsConnection = WSFactory.open("/notifications", {
        init: c => {
            c.subscribe({
                call: "notifications.subscription",
                payload: {},
                handler: message => {
                    if (message.type === "message") {
                        sendNotification(normalizeNotification(message.payload));
                    }
                }
            });
        }
    });

    // NOTE(Dan): Sets up the subscriber to the snackbarStore. This is here mostly for legacy reasons. Generally you
    // should prefer generating frontend notifications through `sendNotification()`.
    snackbarSubscription = (snack?: Snack): void => {
        if (snack && snack.addAsNotification) {
            sendNotification(normalizeNotification({
                id: -new Date().getTime(),
                message: snack.message,
                read: false,
                type: "info",
                ts: new Date().getTime(),
                meta: ""
            }));
        }
    };
    snackbarStore.subscribe(snackbarSubscription);

    // NOTE(Dan): A pinned notification which has been snoozed should automatically re-appear as a popup after some
    // time. This small `setInterval()` handler is responsible for doing this.
    snoozeLoop = setInterval(() => {
        for (const notification of notificationStore) {
            if (notification.isPinned && Snooze.shouldAppear(notification.uniqueId)) {
                triggerNotificationPopup(notification);
                Snooze.trackAppearance(notification.uniqueId);
            }
        }
    }, 500);
}

function deinitStore() {
    notificationStore.length = 0;
    wsConnection?.close();
    wsConnection = undefined;

    if (snoozeLoop) clearInterval(snoozeLoop);
    snackbarStore.unsubscribe(snackbarSubscription);
}

// NOTE(Dan): Whenever a user has read a message, we mark it as read and notify the backend (if relevant). This is
// done through the `markAllAsRead()` and `markAsRead()` functions.
export function markAllAsRead() {
    markAsRead(notificationStore);
}

export function markAsRead(notifications: NormalizedNotification[]) {
    for (const notification of notifications) {
        notification.read = true;
    }
    renderNotifications();

    const idsToUpdate: string[] = [];
    for (const notification of notifications) {
        if (!notification.isPinned && notification.uniqueId.indexOf("-") !== 0) {
            idsToUpdate.push(notification.uniqueId);
        }
    }

    if (idsToUpdate.length > 0) {
        callAPI({
            context: "",
            method: "POST",
            path: "/api/notifications/read",
            payload: {ids: idsToUpdate.join(",")},
        });
    }
}

// HACK(Dan): I would agree this isn't great.
let normalizationDependencies: {
    navigate: NavigateFunction;
    location: Location;
    dispatch: Dispatch;
    refresh: {current?: () => void}
} | null = null;

// The <Notifications> component is the main component for notifications. It is almost always mounted and visible in
// the navigation header. Here it leaves a bell icon, which can be clicked to open the notification tray. This
// function is responsible for initializing the notification store. It is also responsible for mounting the popup
// component.
export const Notifications: React.FunctionComponent = () => {
    const dispatch = useDispatch();
    const location = useLocation();
    const navigate = useNavigate();
    const rerender = useForcedRender();
    const [notificationsVisible, setNotificationsVisible] = React.useState(false);

    const globalRefresh = useSelector<ReduxObject, (() => void) | undefined>(it => it.header.refresh);
    const globalRefreshRef = React.useRef<(() => void) | undefined>(undefined);
    React.useEffect(() => {
        globalRefreshRef.current = globalRefresh;
    }, [globalRefresh]);

    const toggleNotifications = React.useCallback((ev?: React.SyntheticEvent) => {
        ev?.stopPropagation();
        setNotificationsVisible(prev => !prev);
    }, []);

    React.useEffect(() => {
        const evHandler = () => {setNotificationsVisible(false)};
        document.addEventListener("click", evHandler);
        return () => {
            document.removeEventListener("click", evHandler);
        };
    }, []);

    React.useEffect(() => {
        notificationCallbacks.add(rerender);
        normalizationDependencies = {navigate, location, dispatch, refresh: globalRefreshRef};
        initializeStore();

        return () => {
            notificationCallbacks.delete(rerender);
            normalizationDependencies = null;
            deinitStore();
        };
    }, []);

    const pinnedEntries: JSX.Element | null = (() => {
        const pinnedItems = notificationStore.filter(it => normalizeNotification(it).isPinned);
        if (pinnedItems.length === 0) return null;
        return <div className="container">
            {pinnedItems.map((notification, index) => {
                const normalized = normalizeNotification(notification);
                return <NotificationEntry key={index} notification={normalized} />
            })}
        </div>;
    })();

    const entries: JSX.Element = (() => {
        if (notificationStore.length === 0) return <NoNotifications />;
        return <>
            {notificationStore.map((notification, index) => {
                if (notification.isPinned) return null;

                return <NotificationEntry key={index} notification={notification} />
            })}
        </>;
    })();

    const unreadLength = notificationStore.filter(e => !e.read).length;

    const divRef = React.useRef<HTMLDivElement>(null);
    const closeOnOutsideClick = React.useCallback(e => {
        if (divRef.current && !divRef.current.contains(e.target)) {
            setNotificationsVisible(false);
        }
    }, []);

    React.useEffect(() => {
        document.addEventListener("mousedown", closeOnOutsideClick);
        return () => document.removeEventListener("mousedown", closeOnOutsideClick);
    }, []);


    return <>
        <NotificationPopups />
        <Flex onClick={toggleNotifications} data-component="notifications" cursor="pointer">
            <Relative top={0} left={0}>
                <Flex justifyContent="center" width="48px">
                    <Icon
                        cursor="pointer"
                        size={"24px"}
                        name="heroBell"
                        color="fixedWhite"
                    />
                </Flex>
                {unreadLength > 0 ? (
                    <ThemeProvider theme={theme}>
                        <Absolute top={-12} left={28}>
                            <div
                                style={{
                                    borderRadius: "999999px",
                                    background: "var(--red)",
                                    color: "white",
                                    letterSpacing: "0.025em",
                                    fontSize: "10pt",
                                    padding: "1px 5px"
                                }}
                                data-component={"notifications-unread"}
                            >
                                {unreadLength}
                            </div>
                        </Absolute>
                    </ThemeProvider>
                ) : null}
            </Relative>
        </Flex>

        {!notificationsVisible ? null :
            <div ref={divRef} className={ContentWrapper} onClick={UF.stopPropagation}>
                <div className="header">
                    <h3>Notifications</h3>
                    <Icon name="checkDouble" className="read-all" cursor="pointer" color="iconColor" color2="iconColor2"
                        onClick={markAllAsRead} />
                </div>

                {pinnedEntries}

                <div className="container-wrapper">
                    <div className="container">{entries}</div>
                </div>
            </div>
        }
    </>;
}

const ContentWrapper = injectStyle("content-wrapper", k => `
    ${k} {
        position: fixed;
        bottom: 8px;
        left: calc(8px + var(--sidebarWidth));
        width: 450px;
        height: 600px;
        max-height: 100vh;
        z-index: 10000;
        background: var(--white);
        color: var(--black);
        padding: 16px;
        border-radius: 6px;
        border: 1px solid rgba(0, 0, 0, 20%);

        display: flex;
        flex-direction: column;

        box-shadow: ${theme.shadows.sm};
    }

    ${k} > .container-wrapper {
        flex-grow: 1;
        overflow-y: auto;
    }

    ${k} > .container {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
        flex-direction: column;
    }

    ${k} > .container-wrapper > .container > div {
        margin-bottom: 8px;
    }

    ${k} > .header {
        display: flex;
        align-items: center;
        margin-bottom: 20px;
    }

    ${k} > .header > h3 {
        flex-grow: 1;
        margin: 0;
        font-size: 22px;
        font-weight: normal;
    }

    ${k} > .header > .read-all {
        cursor: pointer;
    }
`);

const NoNotifications = (): JSX.Element => <TextSpan>No notifications</TextSpan>;

export interface Notification {
    type: string;
    id: number;
    message: string;
    ts: number;
    read: boolean;
    meta: any;
}

function normalizeNotification(
    notification: Notification | NormalizedNotification,
): NormalizedNotification & {onSnooze?: () => void} {
    const {location, dispatch, refresh, navigate} = normalizationDependencies!;

    if ("isPinned" in notification) {
        const result = {
            ...notification,
            onSnooze: () => Snooze.snooze(notification.uniqueId),
        };
        result.onAction = () => {
            const before = location.pathname;

            markAsRead([result]);
            notification.onAction?.();

            const after = location.pathname;
            if (before === after && refresh.current) refresh.current();
        };
        return result;
    }

    const resolved = resolveNotification(notification);
    const result: NormalizedNotification & {onSnooze?: () => void} = {
        icon: resolved.icon,
        iconColor: resolved.color ?? "iconColor",
        iconColor2: resolved.color2 ?? "iconColor2",
        title: resolved.modifiedTitle ?? "Information",
        body: resolved.modifiedMessage ?? notification.message,
        isPinned: false,
        ts: notification.ts,
        read: notification.read,
        uniqueId: `${notification.id}`,
        onSnooze: () => Snooze.snooze(`${notification.id}`),
    };

    result.onAction = () => {
        const before = location.pathname;

        markAsRead([result]);
        onNotificationAction(notification, navigate, dispatch);

        const after = location.pathname;
        if (before === after && refresh.current) refresh.current();
    };

    return result;
}

interface NotificationEntryProps {
    notification: NormalizedNotification;
}

function NotificationEntry(props: NotificationEntryProps): JSX.Element {
    const {notification} = props;

    const classes: string[] = [];
    {
        if (notification.isPinned) classes.push("pinned");
        else classes.push("unpinned");

        if (notification.read !== true) classes.push("unread");
    }

    const onAction = React.useCallback(() => {
        markAsRead([props.notification]);
        props.notification.onAction?.();
    }, [props.notification]);

    return (
        <div className={classConcatArray(NotificationWrapper, classes)} onClick={onAction}>
            <Icon name={notification.icon} size="24px" color={notification.iconColor ?? "iconColor"}
                color2={notification.iconColor2 ?? "iconColor2"} />
            <div className="notification-content">
                <Flex>
                    <b>{notification.title}</b>
                    <div className="time">
                        {formatDistance(notification.ts ?? timestampUnixMs(), new Date(), {addSuffix: true})}
                    </div>
                </Flex>

                <div className="notification-body">{notification.body}</div>
            </div>
        </div>
    );
}

const NotificationWrapper = injectStyle("notification-wrapper", k => `
    ${k} {
        display: flex;
        gap: 10px;
        align-items: center;
        background: var(--white);
        border-radius: 6px;
        padding: 10px;
        width: 100%;
        user-select: none;
        cursor: pointer;
    }

    ${k}.pinned {
        background: rgba(255, 100, 0, 20%);
    }

    ${k}.unread.unpinned {
        background: rgba(204, 221, 255, 20%);
    }

    ${k}:hover {
        background: rgba(240, 246, 255, 50%);
    }

    ${k}.unread.unpinned:hover {
        background: rgba(204, 221, 255, 50%);
    }

    ${k}.pinned:hover {
        background: rgba(255, 100, 0, 30%);
    }

    ${k} > b {
        margin: 0;
        font-size: 14px;
        flex-grow: 1;

        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
    }

    ${k} > .notification-content {
        width: calc(100% - 34px);
    }

    ${k} > .notification-body {
        font-size: 12px;
        margin-bottom: 5px;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        margin-top: -3px;
    }

    ${k} > a {
        color: var(--blue);
        cursor: pointer;
    }

    ${k} > .time {
        font-size: 12px;
        flex-shrink: 0;
        color: var(--midGray);
    }
`);

export const NotificationDashboardCard: React.FunctionComponent = () => {
    const rerender = useForcedRender();

    React.useEffect(() => {
        notificationCallbacks.add(rerender);
        return () => {
            notificationCallbacks.delete(rerender);
        }
    }, []);

    return <HighlightedCard
        color="darkGreen"
        icon="heroBell"
        title="Recent notifications"
        subtitle={
            <Icon name="checkDouble" color="iconColor" color2="iconColor2" title="Mark all as read" cursor="pointer"
                onClick={markAllAsRead} />
        }
    >
        {notificationStore.length !== 0 ? null :
            <Flex
                alignItems="center"
                justifyContent="center"
                height="calc(100% - 60px)"
                minHeight="250px"
                mt="-30px"
                width="100%"
                flexDirection="column"
            >
                <Heading.h4>No notifications</Heading.h4>
                As you use UCloud notifications will appear here.

                <Link to="/applications/overview" mt={8}>
                    <Button fullWidth mt={8}>Explore UCloud</Button>
                </Link>
            </Flex>
        }

        <div style={{display: "flex", gap: "10px", flexDirection: "column", margin: "10px 0"}}>
            {notificationStore.slice(0, 7).map(it => <NotificationEntry key={it.uniqueId} notification={it} />)}
        </div>
    </HighlightedCard>;
};

export default Notifications;

