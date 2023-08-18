import {BottomProps, BoxShadowProps, LeftProps, RightProps, TopProps, HeightProps} from "styled-system";
import {CSSVarThemeColor} from "./theme";
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
}> = props => {
    return <div
        className={DropdownClass}
        data-hover={props.hover === true}
        data-full-width={props.fullWidth === true}
        ref={props.divRef}
        children={props.children}
    />;
};

Dropdown.defaultProps = {
    hover: true
};

interface DropdownProps {
    hover?: boolean;
    fullWidth?: boolean;
}

export const DropdownContentClass = injectStyle("dropdown-content", k => `
    ${k} {
        border-radius: 5px;
        box-shadow: 0px 3px 6px rgba(0, 0, 0, 16%);
        position: absolute;
        background: var(--white);
        color: var(--black);
        min-width: 138px;
        z-index: 1000;
        text-align: left;
        visibility: visible;
        pointer-events: auto;
        border: 1px solid var(--borderGray);
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

    ${k} > div {
        padding-top: 5px;
        padding-bottom: 5px;
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
        background: var(--lightBlue);
    }
`);

export const DropdownContent: React.FunctionComponent<DropdownContentProps & {
    children?: React.ReactNode;
}> = props => {
    const style: CSSProperties = {};
    if (props.width) style.width = extractSize(props.width);
    if (props.minWidth) style.minWidth = extractSize(props.minWidth);
    if (props.maxHeight) style.maxHeight = extractSize(props.maxHeight);
    if (props.cursor) style.cursor = props.cursor;
    if (props.color) style.color = `var(--${props.color})`;
    if (props.top !== undefined) style.top = extractSize(props.top);
    if (props.left !== undefined) style.left = extractSize(props.left);
    if (props.bottom !== undefined) style.bottom = extractSize(props.bottom);
    if (props.right !== undefined) style.right = extractSize(props.right);

    return <div
        className={DropdownContentClass}
        data-hover={props.hover === true}
        data-square={props.squareTop === true}
        data-fixed={props.fixed === true}
        data-hover-color={props.colorOnHover === true}
        data-padding-controlled={props.paddingControlledByContent === true}
        data-visible={props.visible === true}
        style={style}
        children={props.children}
    />;
};

DropdownContent.defaultProps = {
    squareTop: false,
    hover: true,
    width: "auto",
    backgroundColor: "--white",
    color: "black",
    colorOnHover: true,
    disabled: false,
    cursor: "pointer",
    minWidth: "138px",
    boxShadow: "md",
    visible: false
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
    backgroundColor?: CSSVarThemeColor;
    colorOnHover?: boolean;
    squareTop?: boolean;
    visible?: boolean;
    fixed?: boolean;
    paddingControlledByContent?: boolean;
    color?: string;
}
