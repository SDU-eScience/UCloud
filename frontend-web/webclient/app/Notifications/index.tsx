import {Client, WSFactory} from "Authentication/HttpClientInstance";
import {format, formatDistance} from "date-fns/esm";
import {NotificationsReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect, useSelector} from "react-redux";
import {Redirect, useHistory} from "react-router";
import {Dispatch} from "redux";
import {Snack} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled, {ThemeProvider} from "styled-components";
import {Absolute, Badge, Box, Button, Divider, Flex, Icon, Text, Relative} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {IconName} from "ui-components/Icon";
import {TextSpan} from "ui-components/Text";
import theme, {Theme, ThemeColor} from "ui-components/theme";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {replaceHomeOrProjectFolder} from "Utilities/FileUtilities";
import * as UF from "UtilityFunctions";
import {
    fetchNotifications,
    notificationRead,
    readAllNotifications,
    receiveSingleNotification
} from "./Redux/NotificationsActions";
import {dispatchSetProjectAction} from "Project/Redux";
import {useProjectStatus} from "Project/cache";
import {getProjectNames} from "Utilities/ProjectUtilities";

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

    React.useEffect(() => {
        reload();
        const conn = WSFactory.open("/notifications", {
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

        return () => conn.close();
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
                <TextSpan fontSize={1}>{replaceHomeOrProjectFolder(notification.message, Client, [])}</TextSpan>
                <Text title="From" fontSize={0} mr="12px" color="gray">
                    <Icon size="10" mr="3px" name="edit" />
                    {formatDistance(notification.ts, new Date(), {addSuffix: true})}
                </Text>
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
}

const read = (p: {read: boolean; theme: Theme}): {backgroundColor: string} => p.read ?
    {backgroundColor: "var(--white, #f00)"} : {backgroundColor: "var(--lightGray, #f00)"};

const NotificationWrapper = styled(Flex) <{read: boolean}>`
    ${read};
    margin: 0.1em 0.1em 0.1em 0.1em;
    padding: 0.3em 0.3em 0.3em 0.3em;
    border-radius: 3px;
    cursor: pointer;
    width: 100%;
    &:hover {
        background-color: var(--lightGray, #f00);
    }
`;

interface NotificationsOperations {
    receiveNotification: (notification: Notification) => void;
    fetchNotifications: () => void;
    notificationRead: (id: number) => void;
    showUploader: () => void;
    readAll: () => void;
    setActiveProject: (projectId?: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): NotificationsOperations => ({
    receiveNotification: notification => dispatch(receiveSingleNotification(notification)),
    fetchNotifications: async () => dispatch(await fetchNotifications()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    showUploader: () => dispatch(setUploaderVisible(true, Client.homeFolder)),
    readAll: async () => dispatch(await readAllNotifications()),
    setActiveProject: projectId => dispatchSetProjectAction(dispatch, projectId)
});
const mapStateToProps = (state: ReduxObject): NotificationsReduxObject => state.notifications;

export default connect(mapStateToProps, mapDispatchToProps)(Notifications);
