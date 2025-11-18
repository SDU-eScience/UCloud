import * as React from "react";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";
import {CSSProperties, useCallback, useMemo, useRef, useState} from "react";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {doNothing, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {Flex, Icon, Input, Relative} from "@/ui-components/index";
import {FilterInputClass} from "@/Project/ProjectSwitcher";
import Box from "@/ui-components/Box";
import Error from "@/ui-components/Error";

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

export const SimpleRichSelect: React.FunctionComponent<{
    items: SimpleRichItem[];
    selected?: SimpleRichItem;
    onSelect: (item: SimpleRichItem) => void;

    fullWidth?: boolean;
    dropdownWidth?: string;
    placeholder?: string;
    noResultsItem?: SimpleRichItem;
}> = props => {
    return <RichSelect
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
}

const INPUT_FIELD_HEIGHT = 35;
export function RichSelect<T, K extends keyof T>(props: {
    items: T[];
    keys: K[];

    RenderRow: RichSelectChildComponent<T>;
    RenderSelected?: RichSelectChildComponent<T>;
    InfoOnSearch?: React.ReactNode;
    FullRenderSelected?: RichSelectChildComponent<T>;
    fullWidth?: boolean;
    dropdownWidth?: string;
    elementHeight?: number;

    selected?: T;
    onSelect: (element: T) => void;

    chevronPlacement?: CSSProperties; // hack

    placeholder?: string;
    error?: string;
    noResultsItem?: T;
}): React.ReactNode {
    const [query, setQuery] = useState("");
    const closeFn = useRef<() => void>(doNothing);

    const filteredElements = useMemo(() => {
        const withKeys = props.items.map((it, itIdx) => ({idx: itIdx, ...it}));
        if (query === "") return withKeys;
        return fuzzySearch(withKeys, props.keys, query, {sort: true});
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
            : props.RenderSelected ?
                <div className={TriggerClass} style={{minWidth: props.fullWidth ? "500px" : props.dropdownWidth ?? "500px"}} ref={triggerRef}>
                    <props.RenderSelected element={props.selected} onSelect={doNothing} />
                    <Icon name="heroChevronDown" style={props.chevronPlacement} />
                </div> : null
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
        <Error error={props.error} />
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
                {query ? props.InfoOnSearch : null}
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