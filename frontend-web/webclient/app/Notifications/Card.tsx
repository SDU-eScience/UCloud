import * as React from "react";
import {useCallback} from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import {Absolute, Box, Flex} from "@/ui-components";
import {classConcat, injectStyle, injectStyleSimple, makeKeyframe} from "@/Unstyled";
import Card, {CardClass} from "@/ui-components/Card";
import {ThemeColor} from "@/ui-components/theme";
import {AvatarForUser} from "@/AvataaarLib/UserAvatar";
import {copyToClipboard} from "@/UtilityFunctions";
import {TooltipV2} from "@/ui-components/Tooltip";
import {MultiLineTruncateClass} from "@/Applications/Card";

export interface NotificationProps {
    icon: IconName;
    title: string;
    body: React.ReactNode;
    isPinned: boolean;
    uniqueId: string;
    read?: boolean;
    ts?: number;
    iconColor?: ThemeColor;
    iconColor2?: ThemeColor;
    onAction?: () => void;
    avatar?: string;
}

export const NotificationCard: React.FunctionComponent<NotificationProps & {
    bottom: string;
    exit?: boolean;
    callbackItem?: any;
    onMouseEnter?: (callbackItem?: any) => void;
    onMouseLeave?: (callbackItem?: any) => void;
    onSnooze?: (callbackItem?: any) => void;
    onDismiss?: (callbackItem?: any) => void;
}> = (props) => {
    const [copied, setCopied] = React.useState(false);
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
        style={{position: "fixed", bottom: props.bottom, right: "16px"}}
        onMouseEnter={onMouseEnterMemo}
        onMouseLeave={onMouseLeaveMemo}
        onClick={props.onAction}
    >
        <div className={DefaultHidden} data-tag="operations">
            {props.onDismiss ?
                <Absolute className={NotificationOperation} top="8px" left="-15px" onClick={e => {
                    e.stopPropagation();
                    props.onDismiss?.(props.callbackItem)
                }}>
                    <TooltipV2 tooltip="Dismiss" contentWidth={80}>
                        <Icon name="close" marginLeft={"5px"} marginBottom="1px" size={12} />
                    </TooltipV2>
                </Absolute> : null}
            <Absolute className={NotificationOperation} top="44px" left="-15px" onClick={e => {
                e.stopPropagation();
                let content = props.title;
                if (typeof props.body === "string") {
                    content += `\n${props.body}`;
                }
                copyToClipboard(content);
                setCopied(true);
            }}>
                <TooltipV2 tooltip="Copy to clipboard" contentWidth={150}>
                    <Icon name={copied ? "check" : "heroDocumentDuplicate"} color={copied ? "successMain" : undefined} marginLeft={"5px"} marginBottom="1px" size={12} />
                </TooltipV2>
            </Absolute>
        </div>
        <Card backgroundColor={`${props.isPinned ? "var(--warningMain)" : "var(--backgroundDefault)"}`}>
            <div className="notification-inner">
                {props.avatar === undefined ?
                    <Icon name={props.icon} size="32px" color={props.iconColor ?? "iconColor"}
                        color2={props.iconColor2 ?? "iconColor2"} /> :
                    <Box flexShrink={0}>
                        <AvatarForUser username={props.avatar} height="32px" width="32px" mx="0px"></AvatarForUser>
                    </Box>
                }

                <div className="notification-content">
                    <Flex pr="12px">
                        <h3>{props.title}</h3>
                        <div className={props.isPinned ? "snooze" : "time"} onClick={onSnooze}>
                            {props.isPinned ? "Snooze" : "Now"}
                        </div>
                    </Flex>

                    <div className={classConcat("notification-body", MultiLineTruncateClass)}>{props.body}</div>
                </div>
            </div>
        </Card>
    </div>;
};

const fadeInAnimation = makeKeyframe("fade-in-animation", `
  from {
    opacity: 0%;
  }
  to {
    opacity: 100%;
  }
`);

const DefaultHidden = injectStyleSimple("default-hidden", `
    display: none;    
`);

const NotificationOperation = injectStyleSimple("notification-operation", `
    background-color: var(--backgroundDefault);
    border: var(--defaultCardBorder);
    border-radius: 25px;
    margin-top: -1px;
    margin-left: 4px;
    cursor: pointer;
    width: 24px;
    height: 24px;
`);

const Style = injectStyle("notification", k => `
    ${k}:hover > div[data-tag=operations] {
        display: block;
        animation: 0.3s ${fadeInAnimation};
    }

    ${k} {
        cursor: pointer;
        animation: 0.5s ease-in notification-enter;
        width: 450px;
        z-index: 1000;
        color: var(--textPrimary);
    }

    ${k}.exit {
        animation: 0.5s ease-in notification-exit;
    }

    ${k} .notification-inner {
        height: 56px;
        display: flex;
        gap: 10px;
        align-items: center;
    }

    ${k} .notification-inner h3 {
        margin: 0;
        font-size: 16px;
        flex-grow: 1;

        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        width: 330px;
    }
    
    ${k} > .${CardClass} {
        padding: 8px;
        border-radius: 8px;
    }

    ${k} .notification-inner .notification-body {
        font-size: 12px;
        margin-bottom: 5px;
        width: calc(450px - 30px - 32px);
        margin-top: -3px;
    }

    ${k} .notification-inner .snooze, ${k} .notification-inner .time {
        font-size: 12px;
    }

    ${k} .notification-inner a, ${k} .notification-inner .snooze {
        color: var(--primary);
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

