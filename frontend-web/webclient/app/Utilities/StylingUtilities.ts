import {ThemeColor} from "@/ui-components/theme";
import {useEffect, useState} from "react";
import {useGlobal} from "@/Utilities/ReduxHooks";

export function getCssPropertyValue(name: ThemeColor | string): string {
    return getComputedStyle(document.body).getPropertyValue(`--${name.replace("--", "")}`);
}

export function useWindowDimensions(): [number, number] {
    const [dims, setDims] = useState<[number, number]>(() => [window.innerWidth, window.innerHeight]);
    useEffect(() => {
        const listener = () => {
            setDims([window.innerWidth, window.innerHeight]);
        };

        window.addEventListener("resize", listener);

        return () => {
            window.removeEventListener("resize", listener);
        };
    }, []);

    return dims;
}

// Returns the maximum width that the content on UCloud can have. This is useful for elements which need dynamic
// sizing that cannot use CSS. In other words, the primary use-case for this is dynamically generated SVGs (e.g. charts)
// that require their sizing up-front and not through CSS.
export function useMaxContentWidth(): number {
    const [windowWidth] = useWindowDimensions();
    const [sidebarStickyWidth] = useGlobal("sidebarStickyWidth", 64);
    const globalPadding = 32;
    return Math.min(1400 - globalPadding, windowWidth - globalPadding - sidebarStickyWidth);
}
