import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import Icon from "@/ui-components/Icon";
import * as React from "react";
import {useSelector} from "react-redux";
import {useCallback} from "react";
import {injectStyle} from "@/Unstyled";

export function UtilityBar(props: {searchEnabled: boolean; zIndex?: number}): JSX.Element {
    return (<Flex zIndex={props.zIndex ?? 1} alignItems={"center"}>
        {props.searchEnabled && <Box width="32px"><SearchIcon enabled={props.searchEnabled} /></Box>}
        <Box width="32px" mr={10}><RefreshIcon /></Box>
        <ContextSwitcher />
    </Flex>);
}

function SearchIcon({enabled}): JSX.Element | null {
    if (!enabled) return null;
    return <Icon id={"search-icon"} size={20} color="primaryMain" name="heroMagnifyingGlass" />
}

// NOTE(Dan): This should be kept up with the similar implementation which exists in ResourceBrowser
const refreshIconClass = injectStyle("refresh-icon", k => `
    ${k} {
        transition: transform 0.5s;
    }
    
    ${k}:hover {
        transform: rotate(45deg);
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
        refresh();

        if (icon) {
            const evListener = () => {
                icon.style.transition = "transform 0s";
                icon.style.transform = "rotate(45deg)";
                icon.removeEventListener("transitionend", evListener);
                setTimeout(() => {
                    icon.style.removeProperty("transition");
                    icon.style.removeProperty("transform");
                }, 30);
            };
            icon.addEventListener("transitionend", evListener);
            icon.style.transform = "rotate(405deg)";
        }
    }, [refresh]);
    if (!refresh) return null;
    return <Icon cursor="pointer" size={24} onClick={delayedRefresh} spin={spin || loading} hoverColor="primaryMain"
                 id={"refresh-icon"} className={refreshIconClass} color="primaryMain" name="heroArrowPath"/>
}
