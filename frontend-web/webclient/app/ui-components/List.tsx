import {BoxProps} from "./Box";
import * as React from "react";
import {IconName} from "@/ui-components/Icon";
import {Icon} from "@/ui-components/index";
import {ThemeColor} from "./theme";
import {Cursor} from "@/ui-components/Types";
import {EventHandler, MouseEvent, useCallback} from "react";
import {deviceBreakpoint} from "@/ui-components/Hide";
import {classConcat, extractSize, injectStyle, unbox} from "@/Unstyled";
import {CSSVarCurrentSidebarWidth} from "./Sidebar";

export const ListClass = injectStyle("list", k => `
    ${k} {
        --listChildPadding: 0;
    }
    
    ${k} > *, .list-item {
        margin-top: var(--listChildPadding);
        margin-bottom: var(--listChildPadding);
    }
    
    ${k}[data-bordered="true"] > *, .list-item {
        border-bottom: 1px solid #96B3FF;
    }
`);

const List: React.FunctionComponent<BoxProps & {
    childPadding?: number | string;
    bordered?: boolean;
    children?: React.ReactNode;
}> = props => {
    const style = unbox(props);
    if (props.childPadding) style["--listChildPadding"] = extractSize(props.childPadding);
    return <div
        className={ListClass}
        data-bordered={props.bordered !== false}
        children={props.children}
        style={style}
    />;
};

List.defaultProps = {
    bordered: true
};

List.displayName = "List";

interface ListRowProps {
    isSelected?: boolean;
    select?: () => void;
    navigate?: () => void;
    truncateWidth?: string;
    left: React.ReactNode;
    leftSub?: React.ReactNode;
    icon?: React.ReactNode;
    right: React.ReactNode;
    fontSize?: string;
    highlight?: boolean;
    stopPropagation?: boolean;
    onContextMenu?: EventHandler<MouseEvent<never>>;
    disableSelection?: boolean;
    className?: string;
}

export const ListRow: React.FunctionComponent<ListRowProps> = (props) => {
    const stopPropagation = props.stopPropagation ?? true;
    const doNavigate = useCallback((e: React.SyntheticEvent) => {
        (props.navigate ?? props.select)?.();
        if (stopPropagation) e.stopPropagation();
    }, [props.navigate, props.select, stopPropagation]);

    const doSelect = useCallback((e: React.SyntheticEvent) => {
        if (!props.disableSelection) props.select?.();
        if (stopPropagation) e.stopPropagation();
    }, [props.select, stopPropagation]);

    return <div
        className={classConcat(ListRowClass, props.className)}
        data-component={"list-row"}
        data-highlighted={props.highlight === true}
        data-selected={props.isSelected === true}
        data-navigate={props.navigate !== undefined}
        onClick={doSelect}
        style={{fontSize: props.fontSize ?? "14px"}}
        onContextMenu={props.onContextMenu}
    >
        {props.icon ? <div className="row-icon">{props.icon}</div> : null}
        <div className="row-left">
            <div className="row-left-wrapper">
                <div className="row-left-content" onClick={doNavigate}>{props.left}</div>
                <div className="row-left-padding"/>
            </div>
            <div className="row-left-sub">{props.leftSub}</div>
        </div>
        <div className="row-right">{props.right}</div>
    </div>;
}

export const ListStatContainer: React.FunctionComponent<{ children: React.ReactNode }> = props => <>{props.children}</>;

export const ListRowStat: React.FunctionComponent<{
    icon?: IconName;
    color?: ThemeColor;
    color2?: ThemeColor;
    textColor?: ThemeColor;
    onClick?: () => void;
    cursor?: Cursor;
    children: React.ReactNode;
}> = props => {
    const color: ThemeColor = props.color ?? "gray";
    const color2: ThemeColor = props.color2 ?? "white";
    const body = <>
        {!props.icon ? null : <Icon size={"10"} color={color} color2={color2} name={props.icon}/>}
        {props.children}
    </>;

    if (props.onClick) {
        return <a href={"javascript:void(0)"} onClick={props.onClick}>{body}</a>;
    } else {
        return <div>{body}</div>;
    }
};

const ListRowClass = injectStyle("list-item", k => `
    ${k} {
        padding: 5px 0;
        width: 100%;
        align-items: center;
        display: flex;
    }

    ${k}[data-highlighted="true"] {
        background-color: var(--projectHighlight);
    }

    ${k}[data-selected="true"] {
        background-color: var(--lightBlue);
    }

    ${k}:hover {
        background-color: var(--lightBlue);
    }

    ${k} .row-icon {
        margin-right: 5px;
        margin-left: 5px;
        flex-shrink: 0;
    }

    ${k} .row-left {
        flex-grow: 1;
        overflow: hidden;
        max-width: calc(100vw - var(--sidebarWidth));
    }

    ${k}[data-navigate="true"] .row-left-content {
        cursor: pointer;
    }

    ${k} .row-left-content {
        margin-bottom: -4px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }

    ${k} .row-left-content:has(> form) {
        width: 100%;
    }
  
    ${deviceBreakpoint({minWidth: "767px", maxWidth: "1279px"})} {
      ${k} .row-left{
        max-width: calc(100vw - var(${CSSVarCurrentSidebarWidth}));
      }
    }

    ${deviceBreakpoint({maxWidth: "767px"})} {
        ${k} .row-left {
            max-width: calc(100vw - var(${CSSVarCurrentSidebarWidth}));
        }
    }

    ${k} .row-left-wrapper {
        display: flex;
    }

    ${k} .row-left-padding {
        width: auto;
        cursor: auto;
    }

    ${k} .row-left-sub {
        display: flex;
        margin-top: 4px;
    }
    
    ${k} .row-left-sub > * {
        margin-right: 16px;
        color: var(--gray);
        text-decoration: none;
        font-size: 10px;
    }
    
    ${k} .row-left-sub > * > svg {
        margin-top: -2px;
        margin-right: 4px;
    }

    ${k} .row-right {
        text-align: right;
        display: flex;
        margin-right: 8px;
        flex-shrink: 0;
    }
`);

export default List;
