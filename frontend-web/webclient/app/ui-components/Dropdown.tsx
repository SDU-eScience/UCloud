import styled from "styled-components";
import { right, left, top, RightProps, LeftProps, TopProps, boxShadow, BoxShadowProps, bottom, BottomProps, height } from "styled-system";
import { Button } from "ui-components";

interface FullWidthProps { fullWidth?: boolean }
const fullWidth = ({ fullWidth }: FullWidthProps) => fullWidth ? { width: "100%" } : null;

export const Dropdown = styled.div<{ hover?: boolean, fullWidth?: boolean }>`
    position: relative;
    display: inline-block;
    ${fullWidth};
    ${props => props.hover ?
        `&:hover > div {
            display: block;
        }` : ""
    }
`;

Dropdown.defaultProps = {
    hover: true
}

export const DropdownContent = styled.div<DropdownContentProps>`
    border-bottom-left-radius: 5px;
    border-bottom-right-radius: 5px;
    border-top-left-radius: ${props => props.squareTop ? "0" : "5px"};
    border-top-right-radius: ${props => props.squareTop ? "0" : "5px"};
    ${boxShadow}
    ${props => props.hover ? "display: none;" : ""}
    position: absolute;
    background-color: ${props => props.theme.colors[props.backgroundColor!]};
    color: ${props => props.theme.colors[props.color!]};
    width: ${props => props.width};
    min-width: ${props => props.minWidth ? props.minWidth : "138"}px;
    max-height: ${props => props.maxHeight ? props.maxHeight : ""};
    padding: 12px 16px;
    z-index: 47;
    overflow-y: auto;
    overflow-x: hidden;
    text-align: left;
    cursor: ${props => props.cursor};

    ${props => props.colorOnHover ? `
        & > *:hover:not(${Button}) {
            background-color: rgba(0, 0, 0, 0.05);
        }` : null};

    & svg {
        margin-right: 1em;
    }

    & > svg ~ span {
        margin-right: 1em;
    }

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
    boxShadow: "sm",
}

interface DropdownContentProps extends RightProps, LeftProps, TopProps, BottomProps, BoxShadowProps {
    hover?: boolean
    width?: string | number
    disabled?: boolean
    height?: string | number
    minWidth?: string
    maxHeight?: number | string
    cursor?: string // FIXME There must be a type
    backgroundColor?: string
    colorOnHover?: boolean
    squareTop?: boolean
}