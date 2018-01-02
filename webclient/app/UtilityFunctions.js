import React from 'react'

function NotificationIcon(props) {
    if (props.type === "Complete") {
        return (<div className="initial32 bg-green-500">✓</div>)
    } else if (props.type === "In Progress") {
        return (<div className="initial32 bg-blue-500">...</div>)
    } else if (props.type === "Pending") {
        return (<div className="initial32 bg-blue-500"/>)
    } else if (props.type === "Failed") {
        return (<div className="initial32 bg-red-500">&times;</div>)
    } else {
        return (<div>Unknown type</div>)
    }
}

function WebSocketSupport() {
    let hasWebSocketSupport = "WebSocket" in window;
    if (!hasWebSocketSupport)
        return (
            <h3>
                <small>WebSockets are not supported in this browser. Notifications won't be updated automatically.
                </small>
            </h3>);
    else {
        return (<div></div>)
    }
}

export {NotificationIcon, WebSocketSupport}
