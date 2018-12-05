import styled from "styled-components";
import { left } from "styled-system";
import { min } from "moment";

const fullWidth = (props) => props.fullWidth ? { width: "100%" } : null; 

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
    ${left}
    border-radius: 5px;
    ${props => props.hover ? "display: none;" : ""}
    position: absolute;
    background-color: ${props => props.theme.colors[props.backgroundColor!]}
    color: ${props => props.theme.colors[props.color!]};
    width: ${props => props.width};
    min-width: ${props => props.minWidth ? props.minWidth : "138" }px;
    max-height: ${props => props.maxHeight ? props.maxHeight : ""};
    box-shadow: 0px 0px 3px 1px rgba(0, 0, 0, 0.2);
    padding: 12px 16px;
    z-index: 1;
    overflow-y: auto;
    overflow-x: hidden;
    text-align: left;

    ${props => props.colorOnHover ? `
        & > *:hover {
            background-color: rgba(0, 0, 0, 0.05);
            cursor: ${props => props.cursor}
        }` : null};
    
    & svg {
        margin-right: 1em;
    }

    & > svg ~ span {
        margin-right: 1em;
    }
`;

DropdownContent.defaultProps = {
    hover: true,
    width: "138px",
    backgroundColor: "white",
    color: "black",
    colorOnHover: true,
    disabled: false,
    cursor: "auto",
    minWidth: "138px",
    maxHeight: "300px"
}

interface DropdownContentProps {
    left?: number | string
    hover?: boolean
    width?: string | number
    disabled?: boolean
    minWidth?: string
    maxHeight?: number | string
    cursor?: string // FIXME There must be a type
    backgroundColor?: string
    colorOnHover?: boolean
}