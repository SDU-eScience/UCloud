import {style} from "styled-system";
import {ThemeColor} from "ui-components/theme";

export const cursor = style({
    prop: "cursor",
    cssProperty: "cursor",
    key: "cursor"
});


export function getCssVar(name: ThemeColor): string {
    return getComputedStyle(document.documentElement).getPropertyValue(`--${name}`);
}
