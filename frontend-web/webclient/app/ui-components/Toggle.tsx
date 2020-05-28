import * as React from "react";
import styled from "styled-components";
import {HiddenInputField} from "./Input";
import Label from "./Label";
import {ThemeColor} from "./theme";

// https://www.w3schools.com/howto/howto_css_switch.asp
const ToggleLabel = styled(Label) <{scale: number}>`
    position: relative;
    display: inline-block;
    width: ${props => 30 * props.scale}px;
    height: ${props => 17 * props.scale}px;
`;

ToggleLabel.displayName = "ToggleLabel";

const RoundSlider = styled.span<{scale: number; disabledColor: ThemeColor}>`
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: var(--${props => props.disabledColor ?? "gray"});
    -webkit-transition: .4s;
    transition: .4s;
    border-radius: 34px;

    &:before {
        position: absolute;
        content: "";
        height: ${props => props.scale * 13}px;
        width: ${props => props.scale * 13}px;
        left: ${props => 2 * props.scale}px;
        bottom: ${props => 2 * props.scale}px;
        background-color: white;
        -webkit-transition: .4s;
        transition: .4s;
        border-radius: 50%;
    }
`;

RoundSlider.displayName = "RoundSlider";

const ToggleInput = styled(HiddenInputField) <{scale: number; activeColor: ThemeColor}>`
    &:checked + ${RoundSlider} {
        background-color: var(--${props => props.activeColor});
    }

    &:focus + ${RoundSlider} {
        box-shadow: 0 0 1px var(--${props => props.activeColor});
    }

    &:checked + ${RoundSlider}:before {
        -webkit-transform: translateX(${props => props.scale * 13}px);
        -ms-transform: translateX(${props => props.scale * 13}px);
        transform: translateX(${props => props.scale * 13}px);
    }
`;

ToggleInput.displayName = "ToggleInput";

interface ToggleProps {
    checked?: boolean;
    onChange: () => void;
    scale?: number;
    activeColor?: ThemeColor;
    disabledColor?: ThemeColor;
}
export const Toggle: React.FC<ToggleProps> = ({
    checked,
    onChange,
    scale = 1,
    activeColor = "blue",
    disabledColor = "gray"
}) => (
    <ToggleLabel scale={scale}>
        <ToggleInput scale={scale} type="checkbox" checked={checked} onChange={onChange} activeColor={activeColor} />
        <RoundSlider disabledColor={disabledColor} scale={scale} />
    </ToggleLabel>
);
