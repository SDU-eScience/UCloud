import {BottomProps, BoxShadowProps, LeftProps, RightProps, TopProps, HeightProps} from "styled-system";
import {Cursor} from "./Types";
import {extractSize, injectStyle} from "@/Unstyled";
import {ButtonClass} from "@/ui-components/Button";
import * as React from "react";
import {CSSProperties} from "react";

export const DropdownClass = injectStyle("dropdown", k => `
    ${k} {
        position: relative;
        display: inline-block;
    }
    
    ${k}[data-full-width="true"] {
        width: 100%;
    }
    
    ${k}[data-hover="true"]:hover > div {
        display: block;
    }
`);

export const Dropdown: React.FunctionComponent<DropdownProps & {
    children?: React.ReactNode;
    divRef?: React.RefObject<HTMLDivElement>;
}> = ({hover = true, ...props}) => {
    return <div
        className={DropdownClass}
        data-hover={hover === true}
        data-full-width={props.fullWidth === true}
        ref={props.divRef}
        children={props.children}
    />;
};

interface DropdownProps {
    hover?: boolean;
    fullWidth?: boolean;
}

export const DropdownContentClass = injectStyle("dropdown-content", k => `
    ${k} {
        border-radius: 6px;
        box-shadow: var(--defaultShadow);
        position: absolute;
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        min-width: 138px;
        z-index: 1000;
        text-align: left;
        visibility: visible;
        pointer-events: auto;
        border: 1px solid var(--borderColor);
        overflow: hidden;
        user-select: none;
        -webkit-user-select: none;
    }
    
    ${k}[data-padding-controlled="false"] {
        padding: 0px 17px;
    }
    
    ${k}[data-padding-controlled="false"] > div {
        margin-left: -17px;
        margin-right: -17px;
        padding-left: 17px;
        padding-right: 17px;
        white-space: nowrap;
    }

    ${k}[data-no-y-padding="false"] > div {
        padding-top: 0px;
        padding-bottom: 0px;
    }

    ${k} > div:first-child {
        border-top-left-radius: 4px;
        border-top-right-radius: 4px;
    }

    ${k} > div:last-child {
        border-bottom-left-radius: 4px;
        border-bottom-right-radius: 4px;
    }
    
    ${k}[data-square="true"] {
        border-top-left-radius: 0;
        border-top-right-radius: 0;
    }
    
    ${k}[data-fixed="true"] {
        position: fixed;
    }
    
    ${k}[data-visible="false"] {
        visibility: hidden;
        opacity: 0;
        pointer-events: none;
    }
    
    ${k}[data-hover-color="true"] > *:hover:not(.${ButtonClass}) {
        background: var(--rowHover);
    }
`);

export function DropdownContent({
    squareTop = false,
    hover = true,
    width = "auto",
    color = "textPrimary",
    colorOnHover = true,
    disabled = false,
    cursor = "pointer",
    minWidth = "138px",
    boxShadow = "md",
    visible = false,
    ...props
}: React.PropsWithChildren<DropdownContentProps>): React.ReactNode {
    const style: CSSProperties = {};
    if (width) style.width = extractSize(width);
    if (minWidth) style.minWidth = extractSize(minWidth);
    if (props.maxHeight) style.maxHeight = extractSize(props.maxHeight);
    if (cursor) style.cursor = cursor;
    if (color) style.color = `var(--${color})`;
    if (props.top !== undefined) style.top = extractSize(props.top);
    if (props.left !== undefined) style.left = extractSize(props.left);
    if (props.bottom !== undefined) style.bottom = extractSize(props.bottom);
    if (props.right !== undefined) style.right = extractSize(props.right);

    return <div
        className={DropdownContentClass}
        data-hover={hover === true}
        ref={props.dropdownRef}
        data-square={squareTop === true}
        data-fixed={props.fixed === true}
        data-hover-color={colorOnHover === true}
        data-padding-controlled={props.paddingControlledByContent === true}
        data-no-y-padding={props.noYPadding === true}
        data-visible={visible === true}
        style={style}
        onClick={props.onClick}
        children={props.children}
    />;
};

Dropdown.displayName = "Dropdown";

interface DropdownContentProps extends RightProps, LeftProps, TopProps, BottomProps, BoxShadowProps, HeightProps {
    hover?: boolean;
    width?: string | number;
    disabled?: boolean;
    overflow?: string;
    minWidth?: string;
    maxHeight?: number | string;
    cursor?: Cursor;
    colorOnHover?: boolean;
    squareTop?: boolean;
    visible?: boolean;
    fixed?: boolean;
    paddingControlledByContent?: boolean;
    noYPadding?: boolean;
    color?: string;
    onKeyDown?: React.KeyboardEventHandler;
    dropdownRef?: React.Ref<HTMLDivElement>
    onClick?: React.MouseEventHandler<HTMLDivElement>;
}
