import {ThemeColor} from "@/ui-components/theme";

export function getCssPropertyValue(name: ThemeColor | string): string {
    return getComputedStyle(document.body).getPropertyValue(`--${name.replace("--", "")}`);
}
