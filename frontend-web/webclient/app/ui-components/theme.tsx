import {isLightThemeStored, setSiteTheme, toggleCssColors} from "@/UtilityFunctions";
import {useEffect, useState} from "react";

export const appColors: HexColor[][] = [
    // ["#0096ff", "#043eff"], // blue
    ["#F7D06A", "#E98C33", "#C46927"], // gold
    // ["#EC6F8E", "#C75480", "#AA2457"], // salmon
    // ["#B8D1E3", "#7C8DB3", "#5B698C"], // silver
    ["#83D8F9", "#3F80F6", "#2951BE"], // blue
    ["#AE83CF", "#9065D1", "#68449E"], // violet
    // ["#E392CC", "#E2689D", "#B33B6D"], // pink
    // ["#ECB08C", "#EA7B4B", "#BC4F33"], // bronze
    ["#90DCA1", "#59b365", "#4D9161"], // green
    // ["#F3B576", "#B77D50", "#7C4C3C"], // brown
    // ["#D57AC5", "#E439C9", "#A1328F"], // purple
    ["#98E0F9", "#53A5F5", "#3E79C0"], // lightblue
    ["#DC6AA6", "#C62A5A", "#AA2457"], // red
    // ["#C9D3DF", "#8393A7", "#53657D"], // gray colors from the theme
];

export type HexColor = `#${string}`;
export type ThemeColor =
    | "primaryMain"
    | "primaryLight"
    | "primaryDark"
    | "primaryContrast"
    | "primaryContrastAlt"

    | "secondaryMain"
    | "secondaryLight"
    | "secondaryDark"
    | "secondaryContrast"
    | "secondaryContrastAlt"

    | "errorMain"
    | "errorLight"
    | "errorDark"
    | "errorContrast"
    | "errorContrastAlt"

    | "warningMain"
    | "warningLight"
    | "warningDark"
    | "warningContrast"
    | "warningContrastAlt"

    | "infoMain"
    | "infoLight"
    | "infoDark"
    | "infoContrast"
    | "infoContrastAlt"

    | "successMain"
    | "successLight"
    | "successDark"
    | "successContrast"
    | "successContrastAlt"

    | "backgroundDefault"
    | "backgroundCard"

    | "textPrimary"
    | "textSecondary"
    | "textDisabled"

    | "iconColor"
    | "iconColor2"

    | "fixedWhite"
    | "fixedBlack"

    | "favoriteColor"

    | "wayfGreen"

    | "borderColor"
    | "borderColorHover"

    | "rowHover"
    | "rowActive"

    | "FtFolderColor"
    | "FtFolderColor2"

    | "sidebarColor"
    ;

export function selectContrastColor(inputColor: ThemeColor): ThemeColor {
    if (inputColor.endsWith("Main")) return inputColor.replace("Main", "Contrast") as ThemeColor;
    if (inputColor.endsWith("Dark")) return inputColor.replace("Dark", "Contrast") as ThemeColor;
    if (inputColor.endsWith("Light")) return inputColor.replace("Light", "Contrast") as ThemeColor;
    if (inputColor === "textPrimary" || inputColor === "textSecondary" || inputColor === "textDisabled") {
        return "backgroundDefault";
    }
    return "textPrimary";
}

export function selectHoverColor(inputColor: string | ThemeColor): string | ThemeColor {
    if (inputColor.endsWith("Main")) {
        return inputColor.replace("Main", "Dark");
    } else if (inputColor.endsWith("Dark")) {
        return inputColor.replace("Dark", "Main");
    } else if (inputColor.endsWith("Light")) {
        return inputColor.replace("Light", "Main");
    }
    return inputColor;
}

const isLight = isLightThemeStored();
toggleCssColors(isLight);

export function toggleTheme(): void {
    const isLight = isLightThemeStored();
    toggleCssColors(!isLight);
    setSiteTheme(!isLight);
    for (const listener of Object.values(themeListeners)) {
        listener();
    }
}

const themeListeners: Record<string, () => void> = {};
export function addThemeListener(key: string, op: () => void) {
    themeListeners[key] = op;
}

export function removeThemeListener(key: string) {
    delete themeListeners[key];
}

export function useIsLightThemeStored(): boolean {
    const [isLight, setIsLight] = useState(isLightThemeStored());

    useEffect(() => {
        const random = Math.random().toString();
        addThemeListener(random, () => {
            setIsLight(isLightThemeStored());
        });
        return () => {
            removeThemeListener(random);
        }
    }, []);

    return isLight;
}