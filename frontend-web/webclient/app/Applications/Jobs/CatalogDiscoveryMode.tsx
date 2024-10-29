import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {injectStyle} from "@/Unstyled";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Flex, Input, Relative} from "@/ui-components";
import {FilterInputClass} from "@/Project/ContextSwitcher";
import {joinToString, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import Icon from "@/ui-components/Icon";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {
    CatalogDiscovery,
    CatalogDiscoveryMode,
    defaultCatalogDiscovery,
    emptyLandingPage
} from "@/Applications/AppStoreApi";
import {getProviderTitle, ProviderTitle} from "@/Providers/ProviderTitle";
import {findDomAttributeFromAncestors} from "@/Utilities/HTMLUtilities";
import {fuzzyMatch} from "@/Utilities/CollectionUtilities";
import {useDiscovery} from "@/Applications/Hooks";

const triggerClass = injectStyle("discovery-mode-trigger", k => `
    ${k} {
        background: var(--primaryMain);
        color: var(--primaryContrast);
        border-radius: 6px;
        padding: 6px 12px;
        display: flex;
        user-select: none;
    }
`);

const catalogDiscoverRow = injectStyle("catalog-discovery-row", k => `
    ${k} {
        display: flex;
        padding: 4px 8px;
    }
    
    ${k}:hover {
        background: var(--rowHover);
    }
`);

export const CatalogDiscoveryModeSwitcher: React.FunctionComponent = () => {
    const [mode, setMode] = useDiscovery();
    const [filter, setFilter] = useState<string>("");
    const [landingPage] = useGlobal("catalogLandingPage", emptyLandingPage);
    const closeFn = React.useRef(() => void 0);

    const onModeChange = useCallback((mode: string) => {
        switch (mode) {
            case CatalogDiscoveryMode.ALL.toString():
                setMode({discovery: CatalogDiscoveryMode.ALL});
                break;

            case CatalogDiscoveryMode.AVAILABLE.toString():
                setMode({discovery: CatalogDiscoveryMode.AVAILABLE});
                break;

            default:
                const selected = mode.replace(CatalogDiscoveryMode.SELECTED + "/", "");
                setMode({discovery: CatalogDiscoveryMode.SELECTED, selected});
                break;
        }

        closeFn.current();
    }, []);

    const onKeyboardSelect = useCallback((el: HTMLElement) => {
        const mode = el?.getAttribute("data-mode");
        if (mode) onModeChange(mode);
    }, [onModeChange]);

    const onMouseSelect = useCallback((ev: React.SyntheticEvent) => {
        const mode = findDomAttributeFromAncestors(ev.target, "data-mode");
        if (mode) onModeChange(mode);
    }, [onModeChange]);

    const resetFilter = useCallback(() => {
        setFilter("");
    }, []);

    return <ClickableDropdown
        colorOnHover={false}
        paddingControlledByContent
        arrowkeyNavigationKey={"data-mode"}
        hoverColor={"rowHover"}
        onSelect={onKeyboardSelect}
        closeFnRef={closeFn}
        onOpeningTriggerClick={resetFilter}
        onClose={resetFilter}

        trigger={
            <div className={triggerClass}>
                <Icon name={"heroFunnel"} mr={"8px"}/>
                {mode.discovery !== CatalogDiscoveryMode.ALL ? null : <>
                    All applications
                </>}

                {mode.discovery !== CatalogDiscoveryMode.AVAILABLE ? null : <>
                    Applications available to me
                </>}

                {mode.discovery !== CatalogDiscoveryMode.SELECTED ? null : <>
                    From {joinToString(mode.selected?.split(",").map(providerId => getProviderTitle(providerId)) ?? [], ", ")}
                </>}

                <Icon name="chevronDownLight" size="14px" ml="4px" mt="4px"/>
            </div>
        }
    >
        <Flex width={"600px"}>
            <Input
                autoFocus
                className={FilterInputClass}
                placeholder={"Search for a service provider..." + filter}
                onClick={stopPropagationAndPreventDefault}
                enterKeyHint="enter"
                onKeyDown={e => {
                    if (["Escape"].includes(e.code) && e.target["value"]) {
                        setFilter("");
                        e.target["value"] = "";
                    }
                    e.stopPropagation();
                }}
                onKeyUp={e => {
                    e.stopPropagation();
                    setFilter("value" in e.target ? e.target.value as string : "");
                }}
                type="text"
            />

            <Relative right="24px" top="5px" width="0px" height="0px">
                <Icon name="search"/>
            </Relative>
        </Flex>

        <Flex maxHeight={"450px"} overflowY="auto" flexDirection={"column"}>
            {filter === "" ? <>
                <div className={catalogDiscoverRow} data-mode={CatalogDiscoveryMode.ALL} onClick={onMouseSelect}>
                    All applications
                </div>

                <div className={catalogDiscoverRow} data-mode={CatalogDiscoveryMode.AVAILABLE} onClick={onMouseSelect}>
                    Applications available to me
                </div>
            </> : null}

            {landingPage.availableProviders.map(providerId => {
                    const title = getProviderTitle(providerId);
                    if (filter !== "" && !fuzzyMatch({title}, ["title"], filter)) return null;
                    return <div
                        className={catalogDiscoverRow}
                        data-mode={CatalogDiscoveryMode.SELECTED + "/" + providerId}
                        key={providerId}
                        onClick={onMouseSelect}
                    >
                        From {title}
                    </div>;
                }
            )}
        </Flex>
    </ClickableDropdown>;
};