import * as React from "react";
import "react-bootstrap";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { Button, Popup, Feed, Icon, Divider, SemanticICONS, Label } from 'semantic-ui-react';
import { Redirect } from "react-router";
import * as moment from "moment";
import "./index.scss";
import { withRouter } from "react-router";
import { Page } from "../../types/types";

export interface NotificationState {
    currentPage: Number
    items: Notification[]
    redirectTo?: string
}

class Notifications extends React.Component<any, NotificationState> {
    constructor(props: any) {
        super(props);
        this.state = {
            currentPage: 0,
            items: [],
            redirectTo: null
        };
    }

    public componentDidMount() {
        this.reload();
        setInterval(() => this.reload(), 30_000);
    }

    // TODO We don't cancel any promises. This is technically okay since this component is never unmounted
    private reload() {
        this.retrieveNotifications().then(e => {
            this.setState({
                currentPage: e.pageNumber,
                items: e.items
            })
        });
    }

    private retrieveNotifications(): Promise<Page<Notification>> {
        return Cloud.get("notifications").then(f => f.response);
    }

    private onNotificationRead(notification: Notification) {
        // TODO This is not the "react" way
        this.setState({
            items: this.state.items.map(e => {
                if (e.id == notification.id) e.read = true;
                return e;
            })
        });

        Cloud.post(`notifications/read/${notification.id}`)
    }

    private onNotificationAction(notification: Notification) {
        switch (notification.type) {
            case "APP_COMPLETE":
                // TODO This is buggy! Does't update if already present on analyses page
                // TODO Should refactor these URLs somewhere else
                this.props.history.push(`/analyses/${notification.meta.jobId}`);
                break;
        }
    }

    public render() {
        let entries: JSX.Element[] = this.state.items.map((notification, index) => {
            return <NotificationEntry
                key={index}
                notification={notification}
                onMarkAsRead={(it) => this.onNotificationRead(it)}
                onAction={(it) => this.onNotificationAction(it)}
            />
        });

        if (this.state.redirectTo) {
            let theRedirect = this.state.redirectTo;
            return <Redirect to={theRedirect} />
        }

        let unreadLength = this.state.items.filter((e) => !e.read).length;

        return (
            <div>
                <Popup
                    trigger={
                        <Label color='blue' circular size='large' className='notification-trigger'>
                            <Icon name='bell' />{unreadLength}
                        </Label>
                    }
                    content={<Feed>{entries}</Feed>}

                    on='click'
                    position='top right'
                />
            </div>
        );
    }
}

interface Notification {
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
            default: return "question";
        }
    }
}

export default withRouter(Notifications);