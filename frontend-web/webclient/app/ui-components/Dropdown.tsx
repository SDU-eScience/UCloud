import * as React from "react";
import styled from "styled-components";
import { left, width, minWidth } from "styled-system";
import { CursorProperty } from "csstype";


export const Dropdown = styled.div<{ hover?: boolean }>`
    position: relative;
    display: inline-block;

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
    background-color: ${props => props.theme.colors.white}
    color: black;
    width: ${props => props.width};
    min-width: ${props => props.minWidth ? props.minWidth : "138" }px;
    box-shadow: 0px 0px 3px 1px rgba(0, 0, 0, 0.2);
    padding: 12px 16px;
    z-index: 1;
    text-align: left;
    & > *:hover {
        background-color: rgba(0, 0, 0, 0.05);
        cursor: ${props => props.cursor}
    }
    
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
    disabled: false,
    cursor: "auto",
    minWidth: "138px"
}

interface DropdownContentProps {
    left?: number | string
    hover?: boolean
    width?: string
    disabled?: boolean
    minWidth?: string
    cursor?: string // FIXME There must be a type
}