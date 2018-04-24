import * as React from "react";
import "react-bootstrap";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { Button, Popup, Feed, Icon, Divider } from 'semantic-ui-react';
import * as moment from "moment";

export interface NotificationState {
    currentPage: Number
    items: Notification[]
}

export class Notifications extends React.Component<any, NotificationState> {
    constructor(props: any) {
        super(props);
        this.state = {
            currentPage: 0,
            items: []
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

    public render() {
        let entries: JSX.Element[] = this.state.items.map((notification, index) => {
            return <NotificationEntry notification={notification} key={index} />
        });

        return (
            <div>
                <Popup
                    trigger={
                        <Button inverted circular icon='bell' />
                    }

                    content={<Feed>{entries}</Feed>}

                    on='click'
                    position='top right'
                />
            </div>
        );
    }
}


interface Page<T> {
    itemsInTotal: Number,
    itemsPerPage: Number,

    pageNumber: Number,
    items: T[]
}

interface Notification {
    type: String
    id: any
    message: String
    ts: Number
    read: boolean
    meta: any
}

interface NotificationEntryProps {
    notification: Notification
    onMarkAsRead?: (id: any) => void
}

class NotificationEntry extends React.Component<NotificationEntryProps, any> {
    constructor(props: NotificationEntryProps) {
        super(props);
    }

    public render() {
        return (
            <Feed.Event>
                <Feed.Label><Icon name="signal" /></Feed.Label>
                <Feed.Content>
                    <Feed.Date content={moment(this.props.notification.ts.toString(), "x").fromNow()} />
                    <Feed.Summary>{this.props.notification.message}</Feed.Summary>
                </Feed.Content>
            </Feed.Event>
        );
    }
}