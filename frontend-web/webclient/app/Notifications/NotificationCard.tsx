import * as React from "react";
import {useCallback} from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import {Flex, Card} from "@/ui-components";
import HighlightedCard from "@/ui-components/HighlightedCard";
import styled from "styled-components";

export interface NotificationProps {
    icon: IconName;
    title: string;
    body: JSX.Element | string;
    isPinned: boolean;
    read?: boolean;
    ts?: number;
    iconColor?: string;
    iconColor2?: string;
}

export const NotificationCard: React.FunctionComponent<NotificationProps & { 
    top: string;
    exit?: boolean;
    callbackItem?: any;
    onMouseEnter?: (callbackItem?: any) => void;
    onMouseLeave?: (callbackItem?: any) => void;
}> = (props) => {
    const onMouseEnterMemo = useCallback(() => {
        props.onMouseEnter?.(props.callbackItem);
    }, [props.callbackItem, props.onMouseEnter]);
    const onMouseLeaveMemo = useCallback(() => {
        props.onMouseLeave?.(props.callbackItem);
    }, [props.callbackItem, props.onMouseLeave]);

    return <Style 
        style={{position: "fixed", top: props.top, right: "16px"}} 
        className={props.exit ? "exit" : undefined}
        onMouseEnter={onMouseEnterMemo}
        onMouseLeave={onMouseLeaveMemo}
    >
        <HighlightedCard 
            color={props.isPinned ? "orange" : "blue"} 
            highlightSize="2px" 
            innerPaddingX="10px"
            innerPaddingY="6px"
        >
            <div className="notification-inner">
                <Icon name={props.icon} size="32px" color={props.iconColor ?? "iconColor"} 
                      color2={props.iconColor2 ?? "iconColor2"} />
                <div className="notification-content">
                    <Flex>
                        <h3>{props.title}</h3>
                        <div className={props.isPinned ? "snooze" : "time"}>
                            {props.isPinned ? "Snooze" : "Now"}
                        </div>
                    </Flex>

                    <div className="notification-body">{props.body}</div>
                </div>
            </div>
        </HighlightedCard>
    </Style>;
};

const Style = styled.div`
    animation: 0.5s ease-in notification-enter;
    width: 450px;
    z-index: 10;

    &.exit {
        animation: 0.5s ease-in notification-exit;
    }

    ${Card} {
        background: var(--white);
    }

    .notification-inner {
        display: flex;
        gap: 10px;
        align-items: center;
        background: var(--white);

        h3 {
            margin: 0;
            font-size: 18px;
            flex-grow: 1;

            text-overflow: ellipsis;
            white-space: nowrap;
            overflow: hidden;
            width: 330px;
        }

        .notification-body {
            font-size: 12px;
            margin-bottom: 5px;
            text-overflow: ellipsis;
            white-space: nowrap;
            overflow: hidden;
            width: calc(450px - 30px - 32px);
            margin-top: -3px;
        }

        .snooze, .time {
            font-size: 12px;
        }

        a, .snooze {
            color: var(--blue);
            cursor: pointer;
        }

        .time {
            color: var(--midGray);
        }
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
`;

