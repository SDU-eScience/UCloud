import * as React from "react";
import styled from "styled-components";


export const Dropdown = styled.div`
    position: relative;
    display: inline-block;

    &:hover > div {
        display: block;
    }
`

export const DropdownContent = styled.div`
    display: none;
    position: absolute;
    background-color: rgba(235, 239, 243, 1);
    min-width: 138;
    box-shadow: 0px 8px 16px 0px rgba(0, 0, 0, 0.2);
    padding: 12px 16px;
`

export const DropdownExample = () => (
    <Dropdown>
        <span>Some title</span>
        <DropdownContent>
            <p>Oy</p>
        </DropdownContent>
    </Dropdown>
);