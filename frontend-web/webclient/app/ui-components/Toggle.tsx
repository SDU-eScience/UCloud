import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {useCallback, useEffect, useRef} from "react";
import {ThemeColor} from "./theme";

interface ToggleProps {
    checked: boolean;
    onChange: (prevValue: boolean) => void;
    activeColor?: ThemeColor;
    inactiveColor?: ThemeColor;
}

export const Toggle: React.FC<ToggleProps> = ({
    checked,
    onChange,
    activeColor = "successMain",
    inactiveColor = "textSecondary",
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

    return <div onClick={handler} style={style} data-active={checked} className={ToggleWrapperClass}>
        <div />
    </div>
}

const ToggleWrapperClass = injectStyle("toggle-wrapper", k => `
    ${k} {
        --inactiveColor: #ff0;
        --activeColor: #f0f;
    }

    ${k} {
        border-radius: 12px;
        height: 26px;
        width: 45px;
        background-color: var(--inactiveColor);
        transition: 0.2s;
        padding-top: 2px;
        padding-left: 2px;
        cursor: pointer;
    }

    ${k}[data-active="true"] {
        background-color: var(--activeColor);
        padding-left: 21px;
    }

    ${k} > div {
        border-radius: 50%;
        width: 22px;
        background-color: white;
        height: 22px;
    }
`);
