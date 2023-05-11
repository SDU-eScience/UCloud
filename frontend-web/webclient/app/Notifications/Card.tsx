import * as React from "react";
import {useCallback} from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import {Flex} from "@/ui-components";
import {classConcat, injectStyle} from "@/Unstyled";
import Card, {CardClass} from "@/ui-components/Card";

export interface NotificationProps {
    icon: IconName;
    title: string;
    body: JSX.Element | string;
    isPinned: boolean;
    uniqueId: string;
    read?: boolean;
    ts?: number;
    iconColor?: string;
    iconColor2?: string;
    onAction?: () => void;
}

export const NotificationCard: React.FunctionComponent<NotificationProps & {
    top: string;
    exit?: boolean;
    callbackItem?: any;
    onMouseEnter?: (callbackItem?: any) => void;
    onMouseLeave?: (callbackItem?: any) => void;
    onSnooze?: (callbackItem?: any) => void;
}> = (props) => {
    const onMouseEnterMemo = useCallback(() => {
        props.onMouseEnter?.(props.callbackItem);
    }, [props.callbackItem, props.onMouseEnter]);
    const onMouseLeaveMemo = useCallback(() => {
        props.onMouseLeave?.(props.callbackItem);
    }, [props.callbackItem, props.onMouseLeave]);
    const onSnooze = useCallback((e: React.SyntheticEvent) => {
        e.stopPropagation();
        e.preventDefault();
        if (props.isPinned) {
            props.onSnooze?.(props.callbackItem);
        }
    }, [props.callbackItem, props.onSnooze, props.isPinned]);

    return <div
        className={classConcat(Style, props.exit ? "exit" : undefined)}
        style={{position: "fixed", top: props.top, right: "16px"}}
        onMouseEnter={onMouseEnterMemo}
        onMouseLeave={onMouseLeaveMemo}
        onClick={props.onAction}
    >
        <Card backgroundColor="white" border={`solid 2px var(--${props.isPinned ? "orange" : "blue"})`}>
            <div className="notification-inner">
                <Icon name={props.icon} size="32px" color={props.iconColor ?? "iconColor"}
                    color2={props.iconColor2 ?? "iconColor2"} />
                <div className="notification-content">
                    <Flex pr="20px">
                        <h3>{props.title}</h3>
                        <div className={props.isPinned ? "snooze" : "time"} onClick={onSnooze}>
                            {props.isPinned ? "Snooze" : "Now"}
                        </div>
                    </Flex>

                    <div className="notification-body">{props.body}</div>
                </div>
            </div>
        </Card>
    </div>;
};

const Style = injectStyle("notification", k => `
    ${k} {
        cursor: pointer;
        animation: 0.5s ease-in notification-enter;
        width: 450px;
        z-index: 10;
        color: var(--black);
    }

    ${k}.exit {
        animation: 0.5s ease-in notification-exit;
    }

    ${k} .notification-inner {
        display: flex;
        gap: 10px;
        align-items: center;
        background: var(--white);
    }

    ${k} .notification-inner h3 {
        margin: 0;
        font-size: 18px;
        flex-grow: 1;

        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        width: 330px;
    }
    
    ${k} > .${CardClass} {
        padding: 12px;
        border-radius: 8px;
    }

    ${k} .notification-inner .notification-body {
        font-size: 12px;
        margin-bottom: 5px;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        width: calc(450px - 30px - 32px);
        margin-top: -3px;
    }

    ${k} .notification-inner .snooze, ${k} .notification-inner .time {
            font-size: 12px;
        }

    ${k} .notification-inner a, ${k} .notification-inner .snooze {
        color: var(--blue);
        cursor: pointer;
    }

    ${k} .notification-inner .time {
        color: var(--midGray);
    }

    @keyframes notification-enter {
        from {
            transform: translate(500px, 0);
        }

        to {
            transform: translate(0);
        }
    }

    @keyframes notification-exit {
        from {
            transform: translate(0);
        }

        to {
            transform: translate(500px, 0);
        }
    }
`);

