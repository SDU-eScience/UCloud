import * as React from "react";
import styled from "styled-components";
import { left } from "styled-system";


export const Dropdown = styled.div`
    position: relative;
    display: inline-block;

    &:hover > div {
        display: block;
    }
`;

export const DropdownContent = styled.div<DropdownContentProps>`
    ${left}
    border-radius: 5px;
    display: none;
    position: absolute;
    background-color: rgba(235, 239, 243, 1);
    color: black;
    min-width: 138px;
    box-shadow: 0px 8px 16px 0px rgba(0, 0, 0, 0.2);
    padding: 12px 16px;
    z-index: 1;
`;

interface DropdownContentProps {
    left?: number | string
}