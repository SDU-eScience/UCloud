import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject"
import { Popup, Feed, Icon, SemanticICONS, Label, Button, Divider } from 'semantic-ui-react';
import { Redirect } from "react-router";
import * as moment from "moment";
import { connect } from "react-redux";
import { withRouter } from "react-router";
import { Page } from "Types";
import { fetchNotifications, notificationRead } from "./Redux/NotificationsActions";
import { History } from "history";
import Status from "Navigation/Status";

interface NotificationProps {
    page: Page<Notification>
    redirectTo: string
    fetchNotifications: Function,
    notificationRead: Function,
    history: History
    activeUploads: number
}

class Notifications extends React.Component<NotificationProps> {
    constructor(props) {
        super(props);
    }

    public componentDidMount() {
        this.reload();
        setInterval(() => this.reload(), 30_000);
    }

    private reload() {
        this.props.fetchNotifications();
    }

    private onNotificationRead(notification: Notification) {
        this.props.notificationRead(notification.id);
        Cloud.post(`notifications/read/${notification.id}`);
    }

    private onNotificationAction(notification: Notification) {
        switch (notification.type) {
            case "APP_COMPLETE":
                // TODO This is buggy! Does't update if already present on analyses page
                // TODO Should refactor these URLs somewhere else
                this.props.history.push(`/analyses/${notification.meta.jobId}`);
                break;
            case "SHARE_REQUEST":
                // TODO This is a bit lazy
                this.props.history.push("/shares");
                break;
        }
    }

    public render() {
        const { activeUploads, page } = this.props;
        const entries: JSX.Element[] = page.items.map((notification, index) =>
            <NotificationEntry
                key={index}
                notification={notification}
                onMarkAsRead={(it) => this.onNotificationRead(it)}
                onAction={(it) => this.onNotificationAction(it)}
            />
        );

        if (this.props.redirectTo) {
            let theRedirect = this.props.redirectTo;
            return <Redirect to={theRedirect} />
        }

        const unreadLength = page.items.filter((e) => !e.read).length;
        const label = unreadLength + activeUploads > 0 ? (
            <Label color="red" floating circular>
                {unreadLength + activeUploads}
            </Label>
        ) : null;
        const uploads = activeUploads > 0 ? (
            <>
                {`${activeUploads} active upload${activeUploads > 1 ? "s" : ""} in progress.`}
                <Divider />
            </>
        ) : null;
        return (
            <Popup
                trigger={
                    <Button color="blue" className="notification-button-padding" circular>
                        <Icon className="notification-button-padding" name="bell" />
                        {label}
                    </Button>
                }
                content={
                    <Feed>
                        {entries.length ? entries : <NoNotifications />}
                        <Divider />
                        {uploads}
                        <Status />
                    </Feed>}
                on="click"
                position="bottom right"
            />
        );
    }
}

const NoNotifications = () =>
    <Feed.Event className="notification">
        <Feed.Content>
            <Feed.Label>No notifications</Feed.Label>
        </Feed.Content>
    </Feed.Event>

export interface Notification {
    type: string
    id: any
    message: string
    ts: Number
    read: boolean
    meta: any
}

interface NotificationEntryProps {
    notification: Notification
    onMarkAsRead?: (notification: Notification) => void
    onAction?: (notification: Notification) => void
}

class NotificationEntry extends React.Component<NotificationEntryProps, any> {
    constructor(props: NotificationEntryProps) {
        super(props);
    }

    public render() {
        return (
            <Feed.Event className={"notification " + (this.props.notification.read ? "read " : "unread ")} onClick={() => this.handleAction()}>
                <Feed.Label><Icon name={this.resolveEventIcon(this.props.notification.type)} /></Feed.Label>
                <Feed.Content>
                    <Feed.Date content={moment(this.props.notification.ts.toString(), "x").fromNow()} />
                    <Feed.Summary>{this.props.notification.message}</Feed.Summary>
                </Feed.Content>
            </Feed.Event>
        );
    }

    private handleRead() {
        if (this.props.onMarkAsRead) this.props.onMarkAsRead(this.props.notification);
    }

    private handleAction() {
        this.handleRead();
        if (this.props.onAction) this.props.onAction(this.props.notification);
    }

    private resolveEventIcon(eventType: string): SemanticICONS {
        switch (eventType) {
            case "APP_COMPLETE": return "tasks";
            case "SHARE_REQUEST": return "share";
            default: return "question";
        }
    }
}

const mapDispatchToProps = (dispatch) => ({
    fetchNotifications: () => dispatch(fetchNotifications()),
    notificationRead: (id) => dispatch(notificationRead(id))
});
const mapStateToProps = (state) => ({
    ...state.notifications,
    activeUploads: state.uploader.uploads.filter(it => it.uploadXHR &&
        it.uploadXHR.readyState > XMLHttpRequest.UNSENT && it.uploadXHR.readyState < XMLHttpRequest.DONE).length
});

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(Notifications));