import styled from "styled-components";
import Box from "./Box"
import Flex from "./Flex";


export const Step = styled(Box) <{ active?: boolean }>`
    text-align: center;
    position: relative;
    height: 5em;
    line-height: 5em;
    width: 100%;
    background-color: ${({ active, theme }) => active ? theme.colors.lightGray : theme.colors.white};
    /* Semantic UI css */
    border-right: 1px solid rgba(34,36,38,.15);
    border-radius: 2px;
    &::after {
        display: block;
        content: "";
        position: absolute;
        top: 50%;
        right: 0;
        z-index: 2;
        border: medium none;
        background-color: ${({ active, theme }) => active ? theme.colors.lightGray : theme.colors.white};
        width: 1.14285714em;
        height: 1.14285714em;
        border-style: solid;
        border-color: rgba(34,36,38,.15);
        border-width: 0 1px 1px 0;
        -webkit-transform: translateY(-50%) translateX(50%) rotate(-45deg);
        transform: translateY(-50%) translateX(50%) rotate(-45deg);
    }
`;

export const StepGroup = styled(Flex)`
    border:1px solid rgba(34,36,38,.15);
    border-radius: 2px;
    & > ${Step}:last-child::after {
        content: none;   
    }
`