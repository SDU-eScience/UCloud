import * as React from "react";
import {classConcat, injectStyle} from "@/Unstyled";
import {useCallback, useEffect, useRef} from "react";
import {ThemeColor} from "./theme";

interface ToggleProps {
    checked: boolean;
    onChange: (prevValue: boolean) => void;
    activeColor?: ThemeColor;
    inactiveColor?: ThemeColor;
    circleColor?: ThemeColor;
    height?: number;
    colorAnimationDisabled?: boolean;
}

const DEFAULT_TOGGLE_HEIGHT = 26;
export const Toggle: React.FC<ToggleProps> = ({
    checked,
    onChange,
    height = 26,
    activeColor = "successMain",
    inactiveColor = "textSecondary",
    circleColor = "fixedWhite",
    colorAnimationDisabled = false
}) => {
    const checkedRef = useRef(checked);
    useEffect(() => {
        checkedRef.current = checked;
    }, [checked]);

    const handler = useCallback((e: React.SyntheticEvent) => {
        e.stopPropagation();
        e.preventDefault();
        onChange(checkedRef.current);
    }, [onChange]);

    const style: React.CSSProperties = {};
    style["--inactiveColor"] = `var(--${inactiveColor})`;
    style["--activeColor"] = `var(--${activeColor})`;
    style["--circleColor"] = `var(--${circleColor})`;
    style["--scale"] = height / DEFAULT_TOGGLE_HEIGHT;


    return <div
        onClick={handler}
        style={style}
        data-is-active={checked}
        className={classConcat(ToggleWrapperClass, colorAnimationDisabled ? "color-anim-disabled" : undefined)}
    >
        <div />
    </div>
}

const ToggleWrapperClass = injectStyle("toggle-wrapper", k => `
    ${k} {
        --inactiveColor: #ff0;
        --activeColor: #f0f;
        --circleColor: #0ff;
    }

    ${k} {
        border-radius: 12px;
        height: calc(26px * var(--scale));
        width: calc(45px * var(--scale));
        background-color: var(--inactiveColor);
        transition: 0.2s all;
        padding-top:  calc(2px * var(--scale));
        padding-left: calc(2px * var(--scale));
        cursor: pointer;
    }

    ${k}[data-is-active="true"] {
        background-color: var(--activeColor);
        padding-left: calc(21px * var(--scale));
    }

    ${k} > div {
        border-radius: 50%;
        width: calc(22px * var(--scale));
        background-color: var(--circleColor);
        animation: background-color 0.2;
        height: calc(22px * var(--scale));
    }
    
    ${k}.color-anim-disabled {
        transition: 0.2s padding;
    }
    
    ${k}.color-anim-disabled > div {
        animation: unset;
    }
`);
