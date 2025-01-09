import * as React from "react";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {useCallback, useLayoutEffect, useMemo, useRef, useState} from "react";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {doNothing, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {Flex, Icon, Input, Relative, Text} from "@/ui-components/index";
import {FilterInputClass} from "@/Project/ContextSwitcher";
import Box from "@/ui-components/Box";

export type RichSelectChildComponent<T> = React.FunctionComponent<{
    element?: T;
    dataProps?: Record<string, string>;
    onSelect: () => void;
}>;

export function RichSelect<T, K extends keyof T>(props: {
    items: T[];
    keys: K[];

    RenderRow: RichSelectChildComponent<T>;
    RenderSelected?: RichSelectChildComponent<T>;
    FullRenderSelected?: RichSelectChildComponent<T>;
    
    selected?: T;
    onSelect: (element: T) => void;

    placeholder?: string;
    noResultsItem?: T;
}): React.ReactNode {
    const [query, setQuery] = useState("");
    const closeFn = useRef<() => void>(doNothing);

    const filteredElements = useMemo(() => {
        const withKeys = props.items.map((it, itIdx) => ({ idx: itIdx, ...it }));
        if (query === "") return withKeys;
        return fuzzySearch(withKeys, props.keys, query, { sort: true });
    }, [query, props.items, props.keys]);

    const triggerRef = useRef<HTMLDivElement>(null);

    const [dropdownSize, setDropdownSize] = useState("300px");

    const onTriggerClick = useCallback(() => {
        const trigger = triggerRef.current;
        if (!trigger) return;
        const width = trigger.getBoundingClientRect().width;
        setDropdownSize(width + "px");
    }, []);

    return <ClickableDropdown
        trigger={props.FullRenderSelected ? 
                <props.FullRenderSelected element={props.selected} onSelect={doNothing} />
            :
                props.RenderSelected ? 
                    <div className={TriggerClass} ref={triggerRef}>
                        <props.RenderSelected element={props.selected} onSelect={doNothing}/>
                        <Icon name="chevronDownLight"/>
                    </div>
                : <></>
        }
        onOpeningTriggerClick={onTriggerClick}
        rightAligned
        closeFnRef={closeFn}
        paddingControlledByContent
        arrowkeyNavigationKey={"data-active"}
        hoverColor={"rowHover"}
        colorOnHover={false}
        fullWidth
        onSelect={el => {
            const idxS = el?.getAttribute("data-idx") ?? "";
            const idx = parseInt(idxS);
            let item: T | undefined = props.items[idx];
            if (!item) {
                item = props.noResultsItem;
            }

            if (item) props.onSelect(item);
            closeFn.current();
        }}
    >
        <div style={{maxHeight: "385px", width: dropdownSize}}>
            <Flex>
                <Input
                    autoFocus
                    className={FilterInputClass}
                    placeholder={props.placeholder ?? "Search..."}
                    defaultValue={query}
                    onClick={stopPropagationAndPreventDefault}
                    enterKeyHint="enter"
                    onKeyDownCapture={e => {
                        if (["Escape"].includes(e.code) && e.target["value"]) {
                            setQuery("");
                            e.target["value"] = "";
                            e.stopPropagation();
                        }
                    }}
                    onKeyUp={e => {
                        e.stopPropagation();
                        setQuery("value" in e.target ? e.target.value as string : "");
                    }}
                    type="text"
                />

                <Relative right="24px" top="5px" width="0px" height="0px">
                    <Icon name="search"/>
                </Relative>
            </Flex>

            <div className={ResultWrapperClass}>
                {filteredElements.map(it => <props.RenderRow
                    element={it}
                    key={it.idx}
                    onSelect={() => {
                        props.onSelect(it);
                    }}
                    dataProps={{
                        "data-idx": it.idx.toString(),
                        "data-active": (props.selected === it).toString()
                    }}
                />)}

                {filteredElements.length !== 0 ? null : <>
                    {props.noResultsItem ?
                        <props.RenderRow
                            element={props.noResultsItem}
                            onSelect={() => {
                                console.log(props.noResultsItem);
                                props.onSelect(props.noResultsItem as T)
                            }}
                            dataProps={{
                                "data-active": (props.selected === props.noResultsItem).toString(),
                            }}
                        /> :
                        <Box my="32px" textAlign="center" className={"no-hover-effect"}>No results found</Box>
                    }
                </>}
            </div>
        </div>
    </ClickableDropdown>;
}

const ResultWrapperClass = injectStyle("rich-select-result-wrapper", k => `
    ${k} {
        cursor: default;
        overflow-y: auto;
        max-height: 285px;
    }
    
    ${k} > *:hover {
        background-color: var(--rowHover);
        cursor: pointer;
    }
    
    ${k} .no-hover-effect:hover {
        background: unset;
        cursor: unset;
    }
`);

const TriggerClass = injectStyle("rich-select-trigger", k => `
    ${k} {
        position: relative;
        cursor: pointer;
        border-radius: 5px;
        border: 1px solid var(--borderColor, #f00);
        width: 100%;
        user-select: none;
        -webkit-user-select: none;
        min-width: 500px;
        background: var(--backgroundDefault);
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);
    }

    ${k}:hover {
        border-color: var(--borderColorHover);
    }

    ${k}[data-omit-border="true"] {
        border: unset;
    }

    ${k} > svg {
        position: absolute;
        bottom: 13px;
        right: 15px;
        height: 16px;
    }
`);