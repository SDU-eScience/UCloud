import * as React from "react";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {CSSProperties, useCallback, useEffect, useMemo, useRef, useState} from "react";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {doNothing, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {Flex, Icon, Input, Relative} from "@/ui-components/index";
import {FilterInputClass} from "@/Project/ProjectSwitcher";
import Box from "@/ui-components/Box";

export type RichSelectChildComponent<T> = React.FunctionComponent<RichSelectProps<T>>;

export interface RichSelectProps<T> {
    element?: T;
    dataProps?: Record<string, string>;
    onSelect: () => void;
}

export interface SimpleRichItem {
    key: string;
    value: string;
}

const SIMPLE_RICH_SELECT_OPENED_EVENT = "ucloud:simple-rich-select-opened";
interface SimpleRichSelectOpenedDetail {
    sourceId: string;
}

export const SimpleRichSelect: React.FunctionComponent<{
    items: SimpleRichItem[];
    selected?: SimpleRichItem;
    onSelect: (item: SimpleRichItem) => void;

    fullWidth?: boolean;
    dropdownWidth?: string;
    placeholder?: string;
    noResultsItem?: SimpleRichItem;
    searchable?: boolean;
}> = props => {
    const instanceIdRef = useRef(`simple-rich-select-${Math.random().toString(36).slice(2)}`);
    const [instanceVersion, setInstanceVersion] = useState(0);
    const closeFn = useRef<() => void>(doNothing);

    useEffect(() => {
        if (typeof window === "undefined") return;

        const onAnySimpleRichSelectOpened = (event: Event) => {
            const customEvent = event as CustomEvent<SimpleRichSelectOpenedDetail>;
            if (customEvent.detail?.sourceId === instanceIdRef.current) return;
            setInstanceVersion(current => current + 1);
        };

        window.addEventListener(SIMPLE_RICH_SELECT_OPENED_EVENT, onAnySimpleRichSelectOpened as EventListener);
        return () => {
            window.removeEventListener(SIMPLE_RICH_SELECT_OPENED_EVENT, onAnySimpleRichSelectOpened as EventListener);
        };
    }, []);

    const announceOpen = useCallback(() => {
        if (typeof window === "undefined") return;
        window.dispatchEvent(new CustomEvent<SimpleRichSelectOpenedDetail>(SIMPLE_RICH_SELECT_OPENED_EVENT, {
            detail: {sourceId: instanceIdRef.current},
        }));
    }, []);

    if (props.searchable === false) {
        const triggerText = props.selected?.value ?? props.placeholder ?? "Select...";
        const dropdownWidth = props.dropdownWidth ?? "300px";
        const itemHeight = 33;
        const visibleItemCount = Math.max(1, Math.min(10, props.items.length));
        const dropdownHeight = visibleItemCount * itemHeight;
        const optionsRef = React.useRef<HTMLDivElement>(null);

        const onTriggerClick = () => {
            requestAnimationFrame(() => optionsRef.current?.focus());
        };

        return <div onMouseDownCapture={announceOpen}>
            <ClickableDropdown
                key={`${instanceIdRef.current}:${instanceVersion}`}
                trigger={
                    <div className={TriggerClass} style={{minWidth: props.fullWidth ? "500px" : dropdownWidth}}>
                        <Box p={"4px"} textAlign={"left"} minHeight={25}>
                            {triggerText}
                        </Box>
                        <Icon name="heroChevronDown" style={{position: "absolute", bottom: "5px", right: "5px"}} />
                    </div>
                }
                onOpeningTriggerClick={onTriggerClick}
                rightAligned
                paddingControlledByContent
                fullWidth={props.fullWidth ?? false}
                width={props.fullWidth ? undefined : dropdownWidth}
                height={dropdownHeight}
                closeFnRef={closeFn}
                arrowkeyNavigationKey={"data-active"}
                hoverColor={"rowHover"}
                colorOnHover={false}
                onSelect={el => {
                    const idxS = el?.getAttribute("data-idx") ?? "";
                    const idx = parseInt(idxS);
                    const item = props.items[idx];
                    if (!item) return;
                    props.onSelect(item);
                    closeFn.current();
                }}
            >
                <div
                    ref={optionsRef}
                    tabIndex={0}
                    style={{maxHeight: `${dropdownHeight}px`, overflowY: "auto", outline: "none"}}
                >
                    {props.items.map((item, idx) => (
                        <Box
                            key={item.key}
                            className={SimpleRichSelectOptionClass}
                            p={"4px"}
                            textAlign={"left"}
                            minHeight={25}
                            data-idx={idx.toString()}
                            data-active={(props.selected?.key === item.key).toString()}
                            style={props.selected?.key === item.key ? {backgroundColor: "var(--rowHover)"} : undefined}
                            onClick={() => props.onSelect(item)}
                        >
                            {item.value}
                        </Box>
                    ))}
                </div>
            </ClickableDropdown>
        </div>
    }

    return <div onMouseDownCapture={announceOpen}>
        <RichSelect
            key={`${instanceIdRef.current}:${instanceVersion}`}
            items={props.items}
            keys={["key"]}
            RenderRow={p =>
                <Box p={"4px"} textAlign={"left"} minHeight={25} onClick={p.onSelect} {...p.dataProps}>
                    {p?.element?.value}
                </Box>
            }
            RenderSelected={p =>
                <Box p={"4px"} textAlign={"left"} minHeight={25} onClick={p.onSelect} {...p.dataProps}>
                    {p?.element?.value}
                </Box>
            }
            onSelect={props.onSelect}
            placeholder={props.placeholder}
            dropdownWidth={props.dropdownWidth}
            elementHeight={25}
            selected={props.selected}
            noResultsItem={props.noResultsItem}
            chevronPlacement={{position: "absolute", bottom: "5px", right: "5px"}}
        />
    </div>
}

const INPUT_FIELD_HEIGHT = 35;
export function RichSelect<T, K extends keyof T>(props: {
    items: T[];
    keys: K[];

    RenderRow: RichSelectChildComponent<T>;
    RenderSelected?: RichSelectChildComponent<T>;
    FullRenderSelected?: RichSelectChildComponent<T>;
    fullWidth?: boolean;
    dropdownWidth?: string;
    elementHeight?: number;

    selected?: T;
    onSelect: (element: T) => void;

    chevronPlacement?: CSSProperties; // hack

    placeholder?: string;
    noResultsItem?: T;
}): React.ReactNode {
    const [query, setQuery] = useState("");
    const closeFn = useRef<() => void>(doNothing);

    const filteredElements = useMemo(() => {
        const withKeys = props.items.map((it, itIdx) => ({idx: itIdx, ...it}));
        if (query === "") return withKeys;
        return fuzzySearch(withKeys, props.keys, query);
    }, [query, props.items, props.keys]);

    const limitedElements = useMemo(() => {
        if (filteredElements.length > 500) {
            return filteredElements.slice(0, 500);
        } else {
            return filteredElements;
        }
    }, [filteredElements]);

    const triggerRef = useRef<HTMLDivElement>(null);

    const [dropdownSize, setDropdownSize] = useState(props.dropdownWidth ?? "300px");

    const onTriggerClick = useCallback(() => {
        const trigger = triggerRef.current;
        if (!trigger) return;
        const width = trigger.getBoundingClientRect().width;
        setQuery("");
        setDropdownSize(width + "px");
    }, []);

    const height = Math.min(370, (props.elementHeight ?? 40) * limitedElements.length + INPUT_FIELD_HEIGHT);

    return <ClickableDropdown
        trigger={props.FullRenderSelected ?
            <props.FullRenderSelected element={props.selected} onSelect={doNothing} />
            :
            props.RenderSelected ?
                <div className={TriggerClass} style={{minWidth: props.fullWidth ? "500px" : props.dropdownWidth ?? "500px"}} ref={triggerRef}>
                    <props.RenderSelected element={props.selected} onSelect={doNothing} />
                    <Icon name="heroChevronDown" style={props.chevronPlacement} />
                </div>
                : <></>
        }
        onOpeningTriggerClick={onTriggerClick}
        rightAligned
        height={height}
        closeFnRef={closeFn}
        paddingControlledByContent
        arrowkeyNavigationKey={"data-active"}
        hoverColor={"rowHover"}
        colorOnHover={false}
        fullWidth={props.fullWidth ?? false}
        width={props.fullWidth ? undefined : dropdownSize}
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
        <div style={{height: height + "px", width: dropdownSize}}>
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
                    <Icon name="search" />
                </Relative>
            </Flex>

            <div className={ResultWrapperClass} style={{maxHeight: (height - INPUT_FIELD_HEIGHT) + "px", height: height + "px"}}>
                {limitedElements.map(it => <props.RenderRow
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

                {limitedElements.length !== 0 ? null : <>
                    {props.noResultsItem ?
                        <props.RenderRow
                            element={props.noResultsItem}
                            onSelect={() => {
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
    }
    
    ${k} > *:hover {
        background-color: var(--rowHover);
        cursor: pointer;
        width: 100%;
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

const SimpleRichSelectOptionClass = injectStyle("simple-rich-select-option", k => `
    ${k}:hover {
        background-color: var(--rowHover);
        cursor: pointer;
    }
`);
