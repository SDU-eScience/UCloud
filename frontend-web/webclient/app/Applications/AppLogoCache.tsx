import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {defaultApplicationGroupLogo, integratedTerminalLogo, ProceduralLogo} from "@/Applications/ProceduralLogo";
import {ApplicationGroupLogo} from "@/Applications/AppStoreApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {SvgCache} from "@/Utilities/SvgCache";
import {isLightThemeStored} from "@/UtilityFunctions";
import * as React from "react";

const logoCache = new AsyncCache<string>();
const svgCache = new SvgCache();

export function appendAppIcon(
    el: HTMLElement,
    name: string,
    groupId?: number | null,
    size: number = 30,
    jobName?: string | null,
): void {
    const isCustom = name.startsWith("custom-") || (groupId ?? 0) < 0;
    const isIntegratedTerminal = name === "unknown" && jobName === "Integrated terminal";
    const themeKey = isLightThemeStored() ? "light" : "dark";
    const cacheKey = `${name}-${groupId ?? 0}-${isIntegratedTerminal ? "terminal" : ""}-${themeKey}-${size}`;

    el.style.width = `${size}px`;
    el.style.height = `${size}px`;
    el.style.minWidth = `${size}px`;
    el.style.minHeight = `${size}px`;
    el.style.display = "inline-block";

    void logoCache.retrieve(cacheKey, async () => {
        if (isCustom) {
            const logo = isIntegratedTerminal ? integratedTerminalLogo : await fetchCustomLogo(name, groupId);
            return await svgCache.renderSvg(
                cacheKey,
                () => <ProceduralLogo
                    logo={logo}
                    size={`${size}px`}
                    isLightOverride={themeKey === "light"}
                    title={name}
                />,
                size,
                size,
            );
        } else {
            return AppStore.retrieveAppLogo({
                name,
                darkMode: themeKey === "dark",
                includeText: false,
                placeTextUnderLogo: false,
            });
        }
    }).then(url => {
        el.style.backgroundImage = `url(${url})`;
        el.style.backgroundSize = "contain";
        el.style.backgroundPosition = "center";
        el.style.backgroundRepeat = "no-repeat";
    });
}

async function fetchCustomLogo(name: string, groupId?: number | null): Promise<ApplicationGroupLogo> {
    try {
        const hasGroup = groupId != null && groupId < 0;
        return await callAPI(AppStore.retrieveCustomLogo(
            hasGroup ? {groupId: groupId!} : {applicationName: name},
        ));
    } catch {
        return defaultApplicationGroupLogo(name);
    }
}
