import {Box, Flex, Heading} from "@/ui-components";
import * as React from "react";

// TODO(Jonas): {
//      - add gradient colors;    
// }

export const CUSTOM_THEME_COLOR_KEY = "custom-theme-color-key";
export function findCustomThemeColorOnLaunch() {
    const root = document.querySelector(":root")!;
    const color = localStorage.getItem(CUSTOM_THEME_COLOR_KEY);
    if (!color) return;
    root["style"].setProperty("--blue", color);
}

type HexColor = `#${string}`;
const COLORS: HexColor[] = ["#006aff", "#bed730", "#80007d", "#eaa621"];
export function CustomTheming(): React.ReactNode {

    const root = React.useMemo(() => document.querySelector(':root')!, []);

    const setColor = React.useCallback((color: HexColor) => {
        localStorage.setItem(CUSTOM_THEME_COLOR_KEY, color);
        root["style"].setProperty('--blue', color);
    }, []);

    const activeColor = localStorage.getItem(CUSTOM_THEME_COLOR_KEY) ?? COLORS[0];

    return <div>
        <Heading>Theme color options (beta)</Heading>
        <Flex mt="12px">
            {COLORS.map(color =>
                <ColorOption key={color} isActive={color === activeColor} color={color} setColor={setColor} />
            )}
        </Flex>
    </div>
}

function ColorOption({color, setColor, isActive}: {isActive: boolean, color: HexColor, setColor(color: HexColor): void}) {
    return <Box
        onClick={() => setColor(color)}
        style={{border: "2px solid " + isActive ? "var(--black)" : "var(--white)"}}
        borderRadius="12px"
        backgroundColor={color}
        cursor="pointer"
        width="24px"
        height="24px"
        mr="12px"
    />
}
