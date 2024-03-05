import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import Flex from "@/ui-components/Flex";
import Icon from "@/ui-components/Icon";
import * as React from "react";
import {useSelector} from "react-redux";
import {KeyboardEventHandler, useCallback, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import {Input} from "@/ui-components";

export function UtilityBar(props: {
    onSearch?: (query: string) => void;
    zIndex?: number;
    initialSearchQuery?: string;
}): React.ReactNode {
    return (<Flex zIndex={props.zIndex ?? 1} alignItems={"center"} gap={"16px"}>
        {props.onSearch && <SearchIcon initialQuery={props.initialSearchQuery} onSearch={props.onSearch}/>}
        <RefreshIcon/>
        <ContextSwitcher/>
    </Flex>);
}

const SearchClass = injectStyle("search", k => `
    ${k} {
        display: flex;
        align-items: center;
    }
    
    ${k} input {
        --width: 200px;
        left: 32px;
        position: relative;
        width: var(--width);
        transition: transform 0.2s;
    }
    
    ${k}[data-active=false] input {
        transform: translate(calc(var(--width) / 2), 0) scale(0, 1)
    }
    
    ${k}[data-active=true] input {
        transform: translate(0, 0) scale(1);
    }
    
    ${k} svg {
        z-index: 1;
    }
`);

function SearchIcon(props: {
    onSearch: (query: string) => void;
    initialQuery?: string;
}): React.ReactNode {
    const [visible, setVisible] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);
    const ignoreNextToggle = useRef(false);

    const close = useCallback(() => {
        ignoreNextToggle.current = true;
        setVisible(false);
    }, []);

    const toggleVisible = useCallback(() => {
        if (ignoreNextToggle.current) {
            ignoreNextToggle.current = false;
            return;
        }
        setVisible(prev => !prev);
        inputRef.current?.focus();
    }, []);

    const doSearch = useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        const query = inputRef.current?.value ?? "";
        props.onSearch(query);
        close();
    }, [props.onSearch]);

    const handleEscape = useCallback<KeyboardEventHandler>(e => {
        if (e.code === "Escape") {
            close();
            inputRef.current?.blur();
        }
    }, []);

    return <form className={SearchClass} data-active={visible} onSubmit={doSearch}>
        <Input inputRef={inputRef} onBlur={close} placeholder={"Search..."} onKeyDown={handleEscape}
               defaultValue={props.initialQuery}/>
        <Icon id={"search-icon"} size={20} color="primaryMain" name="heroMagnifyingGlass"
              onClick={toggleVisible} cursor={"pointer"}/>
    </form>;
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

function RefreshIcon(): React.ReactNode {
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
