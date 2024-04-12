import * as React from "react";
import {useDispatch} from "react-redux";
import {toggleThemeRedux} from "@/Applications/Redux/Actions";
import {isLightThemeStored} from "@/UtilityFunctions";
import {toggleTheme} from "./theme";
import {Toggle} from "./Toggle";
import Icon from "./Icon";
import Box from "./Box";
import Flex from "./Flex";
import {injectStyle} from "@/Unstyled";

export function ThemeToggler(): JSX.Element {
    const isLightTheme = isLightThemeStored();
    const dispatch = useDispatch();

    function toggleActive(): void {
        setActive(!active);
        toggleTheme();
        dispatch(toggleThemeRedux(active ? "dark" : "light"));
    }

    const [active, setActive] = React.useState<boolean>(isLightTheme);

    return (
        <button className={ThemeToggle} aria-checked={active ? "true" : "false"} onClick={toggleActive}>
            <Icon size="24px" cursor="pointer" color="fixedWhite" name="heroSun" className={ToggleIcon} opacity={!active?0:1}/>
            <Icon size="24px" cursor="pointer" color="fixedWhite" name="heroMoon" className={ToggleIcon} opacity={active?0:1}/>
        </button>
    );
}

const ToggleIcon = injectStyle("toggleicon", k => `
    ${k} {
        position: absolute;
        top: 0;
        left: 0;
        transition: opacity .25s ease;
    }
`);

const ThemeToggle = injectStyle("themetoggle", k => `
    ${k} {
        position: relative;
        display: block;
        width: 24px;
        height: 24px;
        flex-shrink: 0;
        border: unset;
        background: unset;
        margin: 0;
        padding: 0;
    }
`);
