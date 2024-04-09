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

    return (<Flex mx="auto" width="45px">
        <Box width="45px"><Toggle checked={!active} inactiveColor="primaryMain" activeColor="primaryLight" circleColor={!active ? "fixedBlack" : "fixedWhite"} onChange={toggleActive} /></Box>
        <Icon data-active={active} cursor="pointer" color="fixedBlack" name="heroSun" className={Sun} onClick={toggleActive} />
        <Icon data-active={active} cursor="pointer" color="fixedWhite" name="heroMoon" className={Moon} onClick={toggleActive} />
    </Flex>);
}

const Sun = injectStyle("sun", k => `
    ${k} {
        position: relative;
        left: -41.5px;
        top: 4px;
        animation: opacity 0.4 ease-in;
        opacity: 1;
    }
    
    ${k}[data-active="false"] {
        opacity: 0;
    }
`);

const Moon = injectStyle("moon", k => `
    ${k} {
        position: relative;
        left: -40px;
        top: 4px;
        animation: opacity 0.4 ease-in;
        opacity: 1;
    }
    
    ${k}[data-active="true"] {
        opacity: 0;
    }
`);