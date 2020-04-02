import {Client, WSFactory} from "Authentication/HttpClientInstance";
import {formatDistance} from "date-fns/esm";
import {NotificationsReduxObject, ReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import {Redirect, useHistory} from "react-router";
import {Dispatch} from "redux";
import {Snack} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled, {ThemeProvider} from "styled-components";
import {Absolute, Badge, Box, Button, Divider, Flex, Icon, Relative} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {IconName} from "ui-components/Icon";
import {TextSpan} from "ui-components/Text";
import theme, {Theme, ThemeColor} from "ui-components/theme";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {replaceHomeOrProjectFolder} from "Utilities/FileUtilities";
import {
    fetchNotifications,
    notificationRead,
    readAllNotifications,
    receiveSingleNotification
} from "./Redux/NotificationsActions";

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

    React.useEffect(() => {
        reload();
        const conn = WSFactory.open("/notifications", {
            init: c => {
                c.subscribe({
                    call: "notifications.subscription",
                    payload: {},
                    disallowProjects: true,
                    handler: message => {
                        if (message.type === "message") {
                            props.receiveNotification(message.payload);
                        }
                    }
                });
            }
        });
        const subscriber = (snack?: Snack): void => {
            if (snack)
                props.receiveNotification({
                    id: -new Date().getTime(),
                    message: snack.message,
                    read: false,
                    type: "info",
                    ts: new Date().getTime(),
                    meta: ""
                });
        };
        snackbarStore.subscribe(subscriber);

        return () => conn.close();
    }, []);

    function reload(): void {
        props.fetchNotifications();
    }

    function onNotificationAction(notification: Notification): void {
        switch (notification.type) {
            case "APP_COMPLETE":
                // TODO This is buggy! Doesn't update if already present on analyses page
                // TODO Should refactor these URLs somewhere else
                history.push(`/applications/results/${notification.meta.jobId}`);
                break;
            case "REVIEW_PROJECT":
                reload();
                history.push("/projects/view/" + encodeURIComponent(notification.meta["project"]));
                break;

            case "SHARE_REQUEST":
                reload();
                history.push("/shares");
                break;
        }
    }

    const entries: JSX.Element[] = props.items.map((notification, index) => (
        <NotificationEntry
            key={index}
            notification={notification}
            onMarkAsRead={it => props.notificationRead(it.id)}
            onAction={onNotificationAction}
        />
    ));

    if (props.redirectTo) {
        return <Redirect to={props.redirectTo} />;
    }

    const unreadLength = props.items.filter(e => !e.read).length;
    const readAllButton = unreadLength ? (
        <>
            <Button onClick={props.readAll} fullWidth>Mark all as read</Button>
            <Divider />
        </>
    ) : null;
    return (
        <ClickableDropdown
            colorOnHover={false}
            top="37px"
            width="380px"
            left="-270px"
            trigger={(
                <Flex>
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
                                    <Badge bg="red">{unreadLength}</Badge>
                                </Absolute>
                            </ThemeProvider>
                        ) : null}
                    </Relative>
                </Flex>
            )}
        >
            <ContentWrapper>
                {entries.length ? <>{readAllButton}{entries}</> : <NoNotifications />}
            </ContentWrapper>
        </ClickableDropdown>
    );

}

const ContentWrapper = styled(Box)`
    height: 600px;
    overflow-y: auto;
    padding: 5px;
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

interface NotificationEntryProps {
    notification: Notification;
    onMarkAsRead?: (notification: Notification) => void;
    onAction?: (notification: Notification) => void;
}

export function NotificationEntry(props: NotificationEntryProps): JSX.Element {
    const {notification} = props;
    return (
        <NotificationWrapper
            alignItems="center"
            read={notification.read}
            flexDirection="row"
            onClick={handleAction}
        >
            <Box mr="0.4em" width="10%">
                <Icon {...resolveEventType(notification.type)} />
            </Box>
            <Flex width="90%" flexDirection="column">
                <TextSpan color="grey" fontSize={1}>
                    {formatDistance(notification.ts, new Date(), {addSuffix: true})}
                </TextSpan>
                <TextSpan fontSize={1}>{replaceHomeOrProjectFolder(notification.message, Client)}</TextSpan>
            </Flex>
        </NotificationWrapper>
    );

    function handleRead(): void {
        if (props.onMarkAsRead) props.onMarkAsRead(props.notification);
    }

    function handleAction(): void {
        handleRead();
        if (props.onAction) props.onAction(props.notification);
    }

    function resolveEventType(eventType: string): {name: IconName; color: ThemeColor; color2: ThemeColor} {
        switch (eventType) {
            case "REVIEW_PROJECT":
                return {name: "projects", color: "black", color2: "black"};
            case "SHARE_REQUEST":
                return {name: "share", color: "black", color2: "black"};
            case "APP_COMPLETE":
            default:
                return {name: "info", color: "white", color2: "black"};
        }
    }
}

const read = (p: {read: boolean; theme: Theme}): {backgroundColor: ThemeColor} => p.read ?
    {backgroundColor: p.theme.colors.white as ThemeColor} : {backgroundColor: p.theme.colors.lightGray as ThemeColor};

const NotificationWrapper = styled(Flex) <{read: boolean}>`
    ${read};
    margin: 0.1em 0.1em 0.1em 0.1em;
    padding: 0.3em 0.3em 0.3em 0.3em;
    border-radius: 3px;
    cursor: pointer;
    width: 100%;
    &:hover {
        background-color: ${p => p.theme.colors.lightGray};
    }
`;

interface NotificationsOperations {
    receiveNotification: (notification: Notification) => void;
    fetchNotifications: () => void;
    notificationRead: (id: number) => void;
    showUploader: () => void;
    readAll: () => void;
}

const mapDispatchToProps = (dispatch: Dispatch): NotificationsOperations => ({
    receiveNotification: notification => dispatch(receiveSingleNotification(notification)),
    fetchNotifications: async () => dispatch(await fetchNotifications()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    showUploader: () => dispatch(setUploaderVisible(true, Client.homeFolder)),
    readAll: async () => dispatch(await readAllNotifications())
});
const mapStateToProps = (state: ReduxObject): NotificationsReduxObject => state.notifications;

export default connect(mapStateToProps, mapDispatchToProps)(Notifications);
