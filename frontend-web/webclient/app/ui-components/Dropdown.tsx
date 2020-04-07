import styled from "styled-components";
import {
    bottom,
    BottomProps,
    boxShadow,
    BoxShadowProps,
    height,
    left,
    LeftProps,
    right,
    RightProps,
    top,
    TopProps,
    HeightProps,
} from "styled-system";
import {Button} from "ui-components";
import {Cursor} from "./Types";

interface FullWidthProps {fullWidth?: boolean}
const useFullWidth = ({fullWidth}: FullWidthProps) => fullWidth ? {width: "100%"} : null;

export const Dropdown = styled.div<DropdownProps>`
    position: relative;
    display: inline-block;
    ${useFullWidth};
    ${props => props.hover ?
        `&:hover > div {
            display: block;
        }` : ""
    }
`;

Dropdown.defaultProps = {
    hover: true
};

interface DropdownProps {
    hover?: boolean;
    fullWidth?: boolean;
}

export const DropdownContent = styled.div<DropdownContentProps>`
    ${props => props.overflow ?
        `overflow: ${props.overflow};` :
        `overflow-y: auto;
        overflow-x: hidden;`
    }
    border-bottom-left-radius: 5px;
    border-bottom-right-radius: 5px;
    border-top-left-radius: ${props => props.squareTop ? "0" : "5px"};
    border-top-right-radius: ${props => props.squareTop ? "0" : "5px"};
    ${boxShadow}
    ${props => props.hover ? "display: none;" : ""}
    position: absolute;
    background-color: var(--${p => p.backgroundColor}, #f00);
    color: var(--${p => p.color}, #f00);
    width: ${props => props.width};
    ${props => props.minWidth ? `min-width: ${props.minWidth};` : "min-width: 138px;"}
    max-height: ${props => props.maxHeight ? props.maxHeight : ""};
    padding: 12px 16px;
    z-index: 47;
    text-align: left;
    cursor: ${props => props.cursor};
    visibility: ${props => props.visible ? "visible" : "hidden"};
    opacity: ${props => props.visible ? 1 : 0};
    pointer-events: ${props => props.visible ? "auto" : "none"};

    ${props => props.colorOnHover ? `
        & > *:hover:not(${Button}) {
            background-color: rgba(0, 0, 0, 0.05);
        }` : null};


    ${top} ${left} ${right} ${bottom} ${height};
`;

DropdownContent.defaultProps = {
    squareTop: false,
    hover: true,
    width: "138px",
    backgroundColor: "white",
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
    backgroundColor?: string;
    colorOnHover?: boolean;
    squareTop?: boolean;
    visible?: boolean;
}
