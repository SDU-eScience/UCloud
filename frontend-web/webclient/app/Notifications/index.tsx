import {WSFactory} from "@/Authentication/HttpClientInstance";
import {formatDistance} from "date-fns/esm";
import {NotificationsReduxObject} from "@/DefaultObjects";
import * as React from "react";
import {connect, useSelector} from "react-redux";
import {Redirect, useHistory} from "react-router";
import {Dispatch} from "redux";
import {Snack} from "@/Snackbar/Snackbars";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import styled, {ThemeProvider} from "styled-components";
import {Absolute, Badge, Flex, Icon, Relative} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {TextSpan} from "@/ui-components/Text";
import theme, {ThemeColor} from "@/ui-components/theme";
import * as UF from "@/UtilityFunctions";
import {
    fetchNotifications,
    notificationRead,
    readAllNotifications,
    receiveSingleNotification
} from "./Redux/NotificationsActions";
import {dispatchSetProjectAction} from "@/Project/Redux";
import {useProjectStatus} from "@/Project/cache";
import {getProjectNames} from "@/Utilities/ProjectUtilities";
import {NotificationProps as NotificationCardProps} from "./NotificationCard";
import {timestampUnixMs} from "@/UtilityFunctions";
import {SendNotificationCb} from "./SendNotification";
import {triggerNotification} from "./NotificationContainer";
import * as Snooze from "./NotificationSnooze";

interface NotificationProps {
    items: Notification[];
    redirectTo: string;
    fetchNotifications: () => void;
    notificationRead: (id: number | string) => void;
    error?: string;
}

type Notifications = NotificationProps & NotificationsOperations;

function Notifications(props: Notifications): JSX.Element {
    const history = useHistory();
    const projectNames = getProjectNames(useProjectStatus());
    const globalRefresh = useSelector<ReduxObject, (() => void) | undefined>(it => it.header.refresh);
    const localSnoozeQueue = React.useRef<NotificationCardProps[]>([]);

    const [showError, setShowError] = React.useState(false);
    const [notificationsVisible, setNotificationsVisible] = React.useState(true);

    React.useEffect(() => {
        setShowError(!!props.error);
    }, [props.error]);

    const toggleNotifications = React.useCallback((ev?: React.SyntheticEvent) => {
        ev?.stopPropagation();
        setNotificationsVisible(prev => !prev);
        setShowError(false);
    }, []);

    React.useEffect(() => {
        const evHandler = () => { setNotificationsVisible(false) };
        document.addEventListener("click", evHandler);
        return () => {
            document.removeEventListener("click", evHandler);
        };
    }, []);

    React.useEffect(() => {
        reload();
        WSFactory.open("/notifications", {
            init: c => {
                c.subscribe({
                    call: "notifications.subscription",
                    payload: {},
                    handler: message => {
                        if (message.type === "message") {
                            props.receiveNotification(message.payload);
                        }
                    }
                });
            }
        });

        const subscriber = (snack?: Snack): void => {
            if (snack && snack.addAsNotification) {
                props.receiveNotification({
                    id: -new Date().getTime(),
                    message: snack.message,
                    read: false,
                    type: "info",
                    ts: new Date().getTime(),
                    meta: ""
                });
            }
        };
        snackbarStore.subscribe(subscriber);

        SendNotificationCb.callback = (notification) => {
            if (Snooze.shouldAppear(notification.uniqueId)) {
                props.receiveNotification(notification);
            } else {
                localSnoozeQueue.current.push(notification);
            }
        };

        setInterval(() => {
            const queue = localSnoozeQueue.current;
            const newQueue: NotificationCardProps[] = [];
            for (const item of queue) {
                if (Snooze.shouldAppear(item.uniqueId)) {
                    props.receiveNotification(item);
                } else {
                    newQueue.push(item);
                }
            }
            localSnoozeQueue.current = newQueue;
        }, 1000);
    }, []);

    function reload(): void {
        props.fetchNotifications();
    }

    function onNotificationAction(notification: Notification): void {
        reload();
        const before = history.location.pathname;
        UF.onNotificationAction(history, props.setActiveProject, notification, projectNames, props.notificationRead);
        const after = history.location.pathname;
        if (before === after) {
            if (globalRefresh) globalRefresh();
        }
    }

    const pinnedEntries: JSX.Element | null = React.useMemo(() => {
        const pinnedItems = props.items.filter(it => normalizeNotification(it).isPinned);
        if (pinnedItems.length === 0) return null;
        return <div className="container">
            {pinnedItems.map((notification, index) => {
                const normalized = normalizeNotification(notification);

                return <NotificationEntry
                    key={index}
                    notification={normalized}
                    onMarkAsRead={() => props.notificationRead(notification.id)}
                    onAction={() => onNotificationAction(notification)}
                />
            })}
        </div>;
    }, [props.items]);

    const entries: JSX.Element = React.useMemo(() => {
        if (props.items.length === 0) return <NoNotifications />;
        return <>
            {props.items.map((notification, index) => {
                const normalized = normalizeNotification(notification);
                if (normalized.isPinned) return null;

                return <NotificationEntry
                    key={index}
                    notification={normalized}
                    onMarkAsRead={() => props.notificationRead(notification.id)}
                    onAction={() => onNotificationAction(notification)}
                />
            })}
        </>;
    }, [props.items]);

    if (props.redirectTo) {
        return <Redirect to={props.redirectTo} />;
    }

    const unreadLength = props.items.filter(e => !e.read).length;

    return <>
        <Flex onClick={toggleNotifications} data-component="notifications" cursor="pointer">
            <Relative top="0" left="0">
                <Flex justifyContent="center" width="48px">
                    <Icon
                        cursor="pointer"
                        name="notification"
                        color="headerIconColor"
                        color2="headerIconColor2"
                    />
                </Flex>
                {unreadLength > 0 ? (
                    <ThemeProvider theme={theme}>
                        <Absolute top="-12px" left="28px">
                            <Badge bg="red" data-component={"notifications-unread"}>{unreadLength}</Badge>
                        </Absolute>
                    </ThemeProvider>
                ) : null}
                {showError ? (
                    <ThemeProvider theme={theme}>
                        <Absolute top="-12px" left="28px">
                            <Badge bg="red">!</Badge>
                        </Absolute>
                    </ThemeProvider>
                ) : null}
            </Relative>
        </Flex>

        {!notificationsVisible ? null :
            <ContentWrapper onClick={UF.stopPropagation}>
                <div className="header">
                    <h3>Notifications</h3>
                    <Icon name="checkDouble" className="read-all" color="iconColor" color2="iconColor2"
                          onClick={props.readAll} />
                </div>

                {pinnedEntries}

                <div className="container-wrapper">
                    <div className="container">{entries}</div>
                </div>
            </ContentWrapper>
        }
    </>;
}

const ContentWrapper = styled.div`
    position: fixed;
    top: 60px;
    right: 16px;
    width: 450px;
    height: 600px;
    z-index: 10000;
    background: var(--white);
    color: var(--black);
    padding: 16px;
    border-radius: 6px;
    border: 1px solid rgba(0, 0, 0, 20%);

    display: flex;
    flex-direction: column;

    box-shadow: ${theme.shadows.sm};

    .container-wrapper {
        flex-grow: 1;
        overflow-y: auto;
    }

    .container {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
        flex-direction: column;
    }

    .header {
        display: flex;
        align-items: center;
        margin-bottom: 20px;

        h3 {
            flex-grow: 1;
            margin: 0;
            font-size: 22px;
            font-weight: normal;
        }

        .read-all {
            cursor: pointer;
        }
    }
`;

const NoNotifications = (): JSX.Element => <TextSpan>No notifications</TextSpan>;

export interface Notification {
    type: string;
    id: number;
    message: string;
    ts: number;
    read: boolean;
    meta: any;
}

export function normalizeNotification(notification: Notification | NotificationCardProps): NotificationCardProps & { onSnooze?: () => void } {
    if ("isPinned" in notification) {
        return {...notification, onSnooze: () => Snooze.snooze(notification.uniqueId) };
    }

    function resolveEventType(eventType: string): {name: IconName; color: ThemeColor; color2: ThemeColor} {
        switch (eventType) {
            case "REVIEW_PROJECT":
                return {name: "projects", color: "black", color2: "midGray"};
            case "SHARE_REQUEST":
                return {name: "share", color: "black", color2: "black"};
            case "PROJECT_INVITE":
                return {name: "projects", color: "black", color2: "midGray"};
            case "PROJECT_ROLE_CHANGE":
                return {name: "projects", color: "black", color2: "midGray"};
            case "PROJECT_USER_LEFT":
                return {name: "projects", color: "black", color2: "midGray"};
            case "PROJECT_USER_REMOVED":
                return {name: "projects", color: "black", color2: "midGray"};
            case "APP_COMPLETE":
            default:
                return {name: "info", color: "white", color2: "black"};
        }
    }

    const iconInfo = resolveEventType(notification.type)
    const result: NotificationCardProps & { onSnooze?: () => void } = {
        icon: iconInfo.name,
        iconColor: iconInfo.color,
        iconColor2: iconInfo.color2,
        title: "Information",
        body: notification.message,
        isPinned: false,
        ts: notification.ts,
        read: notification.read,
        uniqueId: `${notification.id}`,
        onSnooze: () => Snooze.snooze(`${notification.id}`),
    };
    console.log("Result is ", result);
    return result;
}

interface NotificationEntryProps {
    notification: NotificationCardProps;
    onMarkAsRead?: () => void;
    onAction?: () => void;
}

export function NotificationEntry(props: NotificationEntryProps): JSX.Element {
    console.log(props.notification);
    const {notification} = props;
    const classes: string[] = [];
    if (notification.read !== true) classes.push("unread");

    if (notification.isPinned) classes.push("pinned");
    else classes.push("unpinned");

    return (
        <NotificationWrapper onClick={handleAction} className={classes.join(" ")}>
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
        </NotificationWrapper>
    );

    function handleRead(): void {
        if (props.onMarkAsRead) props.onMarkAsRead();
    }

    function handleAction(): void {
        handleRead();
        if (props.onAction) props.onAction();
    }
}

const NotificationWrapper = styled.div`
    display: flex;
    gap: 10px;
    align-items: center;
    background: var(--white);
    border-radius: 6px;
    padding: 10px;
    width: 100%;

    &.unread.pinned {
        background: rgba(255, 100, 0, 20%);
    }

    &.unread.unpinned {
        background: rgba(204, 221, 255, 20%);
    }

    b {
        margin: 0;
        font-size: 14px;
        flex-grow: 1;

        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        max-width: 330px;
    }

    .notification-content {
        width: calc(100% - 34px);
    }

    .notification-body {
        font-size: 12px;
        margin-bottom: 5px;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        margin-top: -3px;
    }

    .time {
        font-size: 12px;
        flex-shrink: 0;
    }

    a {
        color: var(--blue);
        cursor: pointer;
    }

    .time {
        color: var(--midGray);
    }
`;

interface NotificationsOperations {
    receiveNotification: (notification: Notification | NotificationCardProps) => void;
    fetchNotifications: () => void;
    notificationRead: (id: number) => void;
    readAll: () => void;
    setActiveProject: (projectId?: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): NotificationsOperations => ({
    receiveNotification: notification => {
        triggerNotification(normalizeNotification(notification));
        dispatch(receiveSingleNotification(notification));
    },
    fetchNotifications: async () => dispatch(await fetchNotifications()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    readAll: async () => dispatch(await readAllNotifications()),
    setActiveProject: projectId => dispatchSetProjectAction(dispatch, projectId)
});
const mapStateToProps = (state: ReduxObject): NotificationsReduxObject => state.notifications;

export default connect(mapStateToProps, mapDispatchToProps)(Notifications);
