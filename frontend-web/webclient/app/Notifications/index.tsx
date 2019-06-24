import * as React from "react";
import {Redirect, withRouter, RouteComponentProps} from "react-router";
import * as moment from "moment";
import {connect} from "react-redux";
import {
    fetchNotifications,
    notificationRead,
    readAllNotifications,
    receiveSingleNotification
} from "./Redux/NotificationsActions";
import {History} from "history";
import {setUploaderVisible} from "Uploader/Redux/UploaderActions";
import {Dispatch} from "redux";
import {Absolute, Badge, Box, Button, Divider, Flex, Icon, Relative} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {TextSpan} from "ui-components/Text";
import styled from "styled-components";
import {IconName} from "ui-components/Icon";
import {ReduxObject, NotificationsReduxObject} from "DefaultObjects";
import {Cloud, WSFactory} from "Authentication/SDUCloudObject";
import {replaceHomeFolder} from "Utilities/FileUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Snack} from "Snackbar/Snackbars";
import {Theme} from "ui-components/theme";

interface NotificationProps {
    items: Notification[]
    redirectTo: string
    fetchNotifications: Function,
    notificationRead: (id: number | string) => void,
    history: History
    error?: string
}

type Notifications = NotificationProps & NotificationsOperations & RouteComponentProps;

function Notifications(props: Notifications) {
    React.useEffect(() => {
        reload();
        const conn = WSFactory.open("/notifications", {
            init: conn => {
                conn.subscribe({
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
        const subscriber = (snack?: Snack) => {
            if (!!snack)
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

    function reload() {
        props.fetchNotifications();
    }

    function onNotificationAction(notification: Notification) {
        switch (notification.type) {
            case "APP_COMPLETE":
                // TODO This is buggy! Doesn't update if already present on analyses page
                // TODO Should refactor these URLs somewhere else
                props.history.push(`/applications/results/${notification.meta.jobId}`);
                break;
            case "SHARE_REQUEST":
                reload();
                props.history.push("/shares");
                break;
        }
    }

    const entries: JSX.Element[] = props.items.map((notification, index) =>
        <NotificationEntry
            key={index}
            notification={notification}
            onMarkAsRead={it => props.notificationRead(it.id)}
            onAction={it => onNotificationAction(it)}
        />
    );

    if (props.redirectTo) {
        return <Redirect to={props.redirectTo} />
    }

    const unreadLength = props.items.filter(e => !e.read).length;
    const readAllButton = unreadLength ? (
        <>
            <Button onClick={() => props.readAll()} fullWidth>Mark all as read</Button>
            <Divider />
        </>) : null;
    return (
        <ClickableDropdown colorOnHover={false} top="37px" width={"380px"} left={"-270px"} trigger={
            <Flex>
                <Relative top="0" left="0">
                    <Flex justifyContent="center" width="48px">
                        <Icon cursor="pointer" name="notification" color="headerIconColor"
                            color2="headerIconColor2" />
                    </Flex>
                    {unreadLength > 0 ? <Absolute top="-12px" left="28px">
                        <Badge bg="red">{unreadLength}</Badge>
                    </Absolute> : null}
                </Relative>
            </Flex>
        }>
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

const NoNotifications = () => <TextSpan>No notifications</TextSpan>

export interface Notification {
    type: string
    id: number
    message: string
    ts: number
    read: boolean
    meta: any
}

interface NotificationEntryProps {
    notification: Notification
    onMarkAsRead?: (notification: Notification) => void
    onAction?: (notification: Notification) => void
}

export class NotificationEntry extends React.Component<NotificationEntryProps> {

    public render() {
        const {notification} = this.props;
        return (
            <NotificationWrapper alignItems="center" read={notification.read} flexDirection="row"
                onClick={() => this.handleAction()}>
                <Box mr="0.4em" width="10%"><Icon name={NotificationEntry.resolveEventIcon(notification.type)} /></Box>
                <Flex width="90%" flexDirection="column">
                    <TextSpan color="grey" fontSize={1}>{moment(notification.ts.toString(), "x").fromNow()}</TextSpan>
                    <TextSpan fontSize={1}>{replaceHomeFolder(notification.message, Cloud.homeFolder)}</TextSpan>
                </Flex>
            </NotificationWrapper>
        );
    }

    private handleRead() {
        if (this.props.onMarkAsRead) this.props.onMarkAsRead(this.props.notification);
    }

    private handleAction() {
        this.handleRead();
        if (this.props.onAction) this.props.onAction(this.props.notification);
    }

    private static resolveEventIcon(eventType: string): IconName {
        switch (eventType) {
            case "APP_COMPLETE":
                return "info";
            case "SHARE_REQUEST":
                return "share";
            default:
                return "warning";
        }
    }
}

const read = (p: {read: boolean, theme: Theme}) => p.read ? {
    backgroundColor: p.theme.colors.white
} : {
        backgroundColor: p.theme.colors.lightGray
    };

const NotificationWrapper = styled(Flex) <{read: boolean}>`
    ${read};
    margin: 0.1em 0.1em 0.1em 0.1em;
    padding: 0.3em 0.3em 0.3em 0.3em;
    border-radius: 3px;
    cursor: pointer;
    width: 100%;
    background-color: ${({theme}) => theme.colors.white}
    &:hover {
        background-color: ${({theme}) => theme.colors.lightGray};
    }
`;

interface NotificationsOperations {
    receiveNotification: (notification: Notification) => void
    fetchNotifications: () => void
    notificationRead: (id: number) => void
    showUploader: () => void
    readAll: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): NotificationsOperations => ({
    receiveNotification: notification => dispatch(receiveSingleNotification(notification)),
    fetchNotifications: async () => dispatch(await fetchNotifications()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    showUploader: () => dispatch(setUploaderVisible(true)),
    readAll: async () => dispatch(await readAllNotifications())    
});
const mapStateToProps = (state: ReduxObject): NotificationsReduxObject => state.notifications;

export default connect<NotificationsReduxObject, NotificationsOperations>(mapStateToProps, mapDispatchToProps)(withRouter(Notifications));
