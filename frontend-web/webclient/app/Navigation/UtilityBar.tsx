import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import Icon from "@/ui-components/Icon";
import * as React from "react";
import {useSelector} from "react-redux";

export function UtilityBar(props: {searchEnabled: boolean;}): JSX.Element {
    return (<Flex zIndex={"1"} alignItems={"center"}>
        {props.searchEnabled && <Box width="32px"><SearchIcon enabled={props.searchEnabled} /></Box>}
        <Box width="32px" mr={10}><RefreshIcon /></Box>
        <ContextSwitcher />
    </Flex>);
}

function SearchIcon({enabled}): JSX.Element | null {
    if (!enabled) return null;
    return <Icon size={20} color="var(--primary)" name="heroMagnifyingGlass" />
}

function RefreshIcon(): JSX.Element | null {
    const refresh = useRefresh();
    const spin = useSelector((it: ReduxObject) => it.loading);
    const loading = useSelector((it: ReduxObject) => it.status.loading);
    if (!refresh) return null;
    return <Icon cursor="pointer" size={24} onClick={refresh} spin={spin || loading} hoverColor="blue"
        color="var(--primary)" name="heroArrowPath" />
}
