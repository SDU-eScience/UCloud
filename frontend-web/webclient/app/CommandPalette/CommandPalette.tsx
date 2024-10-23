import * as React from "react";
import {useCommandProviderList} from "@/CommandPalette/index";
import {useCallback, useEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import {Feature, hasFeature} from "@/Features";

const wrapper = injectStyle("command-palette", k => `
    ${k} {
        --own-width: 600px;
        --own-base-height: 48px;
        
        width: var(--own-width);
        min-height: var(--own-base-height);
        
        position: fixed;
        top: calc(50vh - var(--own-base-height));
        left: calc(50vw - (var(--own-width) / 2));
        
        border-radius: 16px;
        color: var(--textPrimary);
        z-index: 99999999;
        
        box-shadow: var(--defaultShadow);
        background: var(--backgroundCardHover);
    }
    
    ${k} input {
        width: 100%;
        height: var(--own-base-height);
        outline: none;
        border: 0;
        background: transparent;
        font-size: calc(0.4 * var(--own-base-height));
        margin: 0 16px;
    }
`);

export function isCommandPaletteTriggerEvent(ev: KeyboardEvent): boolean {
    return ((ev.metaKey || ev.ctrlKey) && ev.code === "KeyP");
}

export const CommandPalette: React.FunctionComponent = () => {
    if (!hasFeature(Feature.COMMAND_PALETTE)) return false;

    const commandProviders = useCommandProviderList();
    const [visible, setVisible] = useState(false);
    const queryRef = useRef("");
    const [query, setQuery] = useState("");

    useEffect(() => {
        queryRef.current = query;
    }, [query]);

    useEffect(() => {
        const listener = (ev: WindowEventMap["keydown"]) => {
            if (isCommandPaletteTriggerEvent(ev)) {
                ev.preventDefault();
                ev.stopPropagation();
                setVisible(prev => !prev);
            }
        };

        window.addEventListener("keydown", listener)

        return () => {
            window.removeEventListener("keydown", listener);
        };
    }, []);

    const open = useCallback(() => {
        setVisible(true);
    }, []);

    const close = useCallback(() => {
        setVisible(false);
    }, []);

    const onInput = useCallback((ev: React.KeyboardEvent) => {
        ev.stopPropagation();
        if (ev.code === "Escape") {
            if (queryRef.current != "") {
                setQuery("");
            } else {
                close();
            }
        }
    }, [setQuery]);

    const onChange = useCallback((ev: React.SyntheticEvent) => {
        setQuery((ev.target as HTMLInputElement).value);
    }, [setQuery]);

    if (!visible) return null;

    return <div className={wrapper}>
        <input
            autoFocus
            placeholder={"Search for anything on UCloud..."}
            onKeyDown={onInput}
            onChange={onChange}
            value={query}
        />
    </div>;
};