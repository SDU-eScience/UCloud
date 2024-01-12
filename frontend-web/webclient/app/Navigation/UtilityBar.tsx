import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import Icon from "@/ui-components/Icon";
import * as React from "react";
import {useSelector} from "react-redux";
import {useCallback, useState} from "react";
import {injectStyle} from "@/Unstyled";

export function UtilityBar(props: {searchEnabled: boolean;}): JSX.Element {
    return (<Flex zIndex={"1"} alignItems={"center"}>
        {props.searchEnabled && <Box width="32px"><SearchIcon enabled={props.searchEnabled} /></Box>}
        <Box width="32px" mr={10}><RefreshIcon /></Box>
        <ContextSwitcher />
    </Flex>);
}

function SearchIcon({enabled}): JSX.Element | null {
    if (!enabled) return null;
    return <Icon id={"search-icon"} size={20} color="primaryMain" name="heroMagnifyingGlass" />
}

const refreshIconClass = injectStyle("refresh-icon", k => `
    ${k}:hover {
        transform: rotate(45deg);
        transition: transform 0.5s;
    }
    
    ${k}.did-click {
        transform: rotate(405deg);
    }
`);

function RefreshIcon(): JSX.Element | null {
    const refresh = useRefresh();
    const spin = useSelector((it: ReduxObject) => it.loading);
    const loading = useSelector((it: ReduxObject) => it.status.loading);
    const delayedRefresh = useCallback(() => {
        if (!refresh) return;
        const icon = document.querySelector<HTMLElement>("#refresh-icon");
        if (icon) icon.style.transform = "rotate(405deg)";

        setTimeout(() => {
            refresh();
            if (icon) {
                icon.style.transition = "transform 0s";
                icon.style.transform = "rotate(45deg)";
                window.requestAnimationFrame(() => {
                    icon.style.removeProperty("transition");
                    icon.style.removeProperty("transform");
                });
            }
        }, 500);
    }, [refresh]);
    if (!refresh) return null;
    return <Icon cursor="pointer" size={24} onClick={delayedRefresh} spin={spin || loading} hoverColor="primaryMain"
                 id={"refresh-icon"} className={refreshIconClass} color="primaryMain" name="heroArrowPath"/>
}
