import React from 'react';
import $ from 'jquery';
import LoadingIcon from '../LoadingIcon'
import { NotificationIcon, WebSocketSupport } from '../../UtilityFunctions'
import pubsub from "pubsub-js";

class Notifications extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            recent: [],
            remaining: [],
            loading: true,
            hasWebSocketSupport: "WebSocket" in window,
            currentNotification: {
                body: "",
                timestamp: 0,
                message: "",
            },
            // FIXME: Should be WSS
            ws: new WebSocket("ws://localhost:8080/ws/notifications"),
            recentShown: 10,
            remainingShown: 10,
        }
    }

    componentDidMount() {
        this.getNotifications();
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    getNotifications() {
        //this.setState({loading: true});
        /*$.getJSON("http://localhost:8080/api/getNotifications").then((notifications) => {
            let yesterday = new Date().getTime() - 24 * 60 * 60 * 1000;
            let recentNotifications = this.state.recent.slice();
            let remainingNotifications = this.state.remaining.slice();
            notifications.forEach((it) => {
                if (it.timestamp > yesterday) {
                    recentNotifications.push(it);
                } else {
                    remainingNotifications.push(it);
                }
            });
            recentNotifications.sort();
            remainingNotifications.sort();
            this.setState({
                loading: false,
                recent: recentNotifications,
                remaining: remainingNotifications,
            });
            //if (this.state.hasWebSocketSupport) this.initWS();
        });*/
    }

    initWS() {
        this.state.ws.onerror = () => {
            console.log("Socket error.")
        };

        this.state.ws.onopen = () => {
            console.log("Connected");
        };

        this.state.ws.onmessage = response => {
            let recentList = this.state.recent.slice();
            recentList.push(JSON.parse(response.data));
            this.setState({
                recent: recentList,
            });
        };
    }

    updateCurrentNotification(notification) {
        this.setState({
            currentNotification: notification
        })
    }

    showMore(name) {
        if (name === "recent") {
            this.setState({
                recentShown: this.state.recentShown + 10
            });
        } else {
            this.setState({
                remainingShown: this.state.remainingShown + 10
            });
        }
    }

    render() {
        return (
            <section>
                <div className="container container-md">
                    <LoadingIcon loading={this.state.loading}/>
                    <WebSocketSupport/>
                    <p className="ph">Last 24 hours</p>
                    <div className="card">
                        <table className="table table-hover table-fixed va-middle">
                            <NotificationList onClick={(notification) => this.updateCurrentNotification(notification)} notifications={this.state.recent} showCount={this.state.recentShown}/>
                        </table>
                        <ShowButton onClick={() => this.showMore("recent")} hasMoreNotifications={this.state.recent.length > this.state.recentShown}/>
                    </div>
                    <p className="ph">Older</p>
                    <div className="card">
                        <table className="table table-hover table-fixed va-middle">
                            <NotificationList onClick={(notification) => this.updateCurrentNotification(notification)} notifications={this.state.remaining} showCount={this.state.remainingShown}/>
                        </table>
                        <ShowButton onClick={() => this.showMore("remaining")} hasMoreNotifications={this.state.remaining.length > this.state.remainingShown}/>
                    </div>
                </div>
                <MessageModal notification={this.state.currentNotification}/>
            </section>)
    }
}

function NotificationList(props) {
    if (!props.notifications) return (null);
    const notifications = props.notifications.slice(0, Math.min(props.showCount, props.notifications.length));
    let i = 0;
    const notificationsList = notifications.map((notification) =>
        <tr key={i++} className="msg-display clickable" onClick={() => props.onClick(notification)} data-toggle="modal"
            data-target="#notificationModal">
            <td className="wd-xxs">
                <NotificationIcon type={notification.type}/>
            </td>
            <th className="mda-list-item-text mda-2-line">
                <small>{notification.message}</small>
                <br/>
                <small className="text-muted">{new Date(notification.timestamp).toLocaleString()}</small>
            </th>
            <td className="text">{notification.body}</td>
        </tr>
    );

    return (
        <tbody>
        {notificationsList}
        </tbody>)
}

function ShowButton(props) {
    if (props.hasMoreNotifications) {
        return (
            <button onClick={() => props.onClick()} className="btn btn-info ion-ios-arrow-down"/>)
    } else {
        return (<div/>)
    }
}

function MessageModal(props) {
    return (
        <div id="notificationModal" className="modal fade" role="dialog">
            <div className="modal-dialog">
                <div className="modal-content">
                    <div className="modal-header">
                        <button type="button" className="close" data-dismiss="modal">&times;</button>
                        <h4 className="modal-title"> {props.notification.message}<br/>
                            <small>{ new Date(props.notification.timestamp).toLocaleString() }</small>
                        </h4>
                    </div>
                    <div className="modal-body">
                        <p>{props.notification.body}</p>
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-default" data-dismiss="modal">Close</button>
                    </div>
                </div>
            </div>
        </div>)
}

export default Notifications
