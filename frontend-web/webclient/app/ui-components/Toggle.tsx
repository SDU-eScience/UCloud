import * as React from "react";
import styled from "styled-components";
import { HiddenInputField } from "./Input";
import Label from "./Label";

// https://www.w3schools.com/howto/howto_css_switch.asp
const ToggleLabel = styled(Label) <{ scale: number }>`
    position: relative;
    display: inline-block;
    width: ${props => 30 * props.scale}px;
    height: ${props => 17 * props.scale}px;
`

const RoundSlider = styled.span<{ scale: number }>`
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #ccc;
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
`

const ToggleInput = styled(HiddenInputField) <{ scale: number }>`
    &:checked + ${RoundSlider} {
        background-color: #2196F3;
    }

    &:focus + ${RoundSlider} {
        box-shadow: 0 0 1px #2196F3;
    }

    &:checked + ${RoundSlider}:before {
        -webkit-transform: translateX(${props => props.scale * 13}px);
        -ms-transform: translateX(${props => props.scale * 13}px);
        transform: translateX(${props => props.scale * 13}px);
    }
`;

interface ToggleProps { checked?: boolean, onChange: () => void, scale?: number }
export const Toggle = ({ checked, onChange, scale = 1 }: ToggleProps) => (
    <ToggleLabel scale={scale}>
        <ToggleInput scale={scale} type="checkbox" checked={checked} onChange={onChange} />
        <RoundSlider scale={scale} />
    </ToggleLabel>
);