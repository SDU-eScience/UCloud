import styled, {css} from "styled-components";
import Flex from "./Flex";

export interface StepProps {
    numbered?: boolean;
    numberSize?: number;
    active?: boolean;
    size?: number;
    pad?: number;
}

const numbered = (props: {
    numbered?: boolean;
    numberSize?: number;
    size?: number;
    pad?: number;
}) => props.numbered ? css`
    &::before {
        content: counter(stepcounter);
        counter-increment: stepcounter;
        border-radius: 100%;
        width: ${props.numberSize}px;
        height: ${props.numberSize}px;
        line-height: ${props.numberSize}px;
        margin: ${(props.size! - props.numberSize!) / 2}px 0;
        position: absolute;
        top: 0;
        left: ${(props.size! / 2) + props.pad!}px;
        font-weight: bold;
        color: var(--blue, #f00);
        background: var(--white, #f00);
        box-shadow: 0 0 0 2px var(--blue, #f00);
    }

    &:first-child::before {
        left: ${props.pad}px;
    }
` : null;

export const Step = styled(Flex) <StepProps>`
    text-decoration: none;
    outline: none;
    // display: inline-block;
    align-items: center;
    float: left;
    height: ${({size}) => size}px;
    padding: 0 ${({pad}) => pad}px 0 ${({numbered, numberSize, size, pad}) => numbered ? size! / 2 + pad! * 2 + numberSize! : size! / 2 + pad!}px;
    position: relative;
    background: ${({active, theme}) => active ? theme.colors.blue : theme.colors.white};
    color: ${({active, theme}) => active ? theme.colors.white : theme.colors.blue};
    transition: background 0.5s;

    &:first-child {
        padding-left: ${({numbered, numberSize, pad}) => numbered ? numberSize! + pad! * 2 : pad!}px;
        border-radius: 5px 0 0 5px;
    }
    &:last-child {
        border-radius: 0 5px 5px 0;
        padding-right: ${({pad}) => pad};
    }

    &:last-child::after {
        content: none;
    }

    &::after {
        content: '';
        position: absolute;
        top: 0;
        right: -${({size}) => size! / 2}px;
        width: ${({size}) => size}px;
        height: ${({size}) => size}px;
        transform: scale(0.707) rotate(45deg);
        z-index: 1;
        border-radius: 0 5px 0 ${({size}) => size}px;
        background: var(--${p => p.active ? "blue" : "white"}, #f00);
        transition: background 0.5s;
        box-shadow: 2px -2px 0 2px var(--lightBlue2, #f00);
    }

    ${numbered}
`;

Step.displayName = "Step";

Step.defaultProps = {
    numbered: false,
    numberSize: 22,
    size: 48,
    pad: 14,
};

export const StepGroup = styled.div`
    text-align: center;
    display: inline-block;
    box-shadow: ${p => p.theme.shadows.sm};
    overflow: hidden;
    border-radius: 5px;
    counter-reset: stepcounter;
`;

StepGroup.displayName = "StepGroup";
