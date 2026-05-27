import * as React from "react";
import {Box, Button, Flex, Icon} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import {classConcat, injectStyle} from "@/Unstyled";
import {ShortcutKey} from "@/ui-components/Operation";
import {Shortcut} from "@/ui-components/ResourceBrowser";

export type VmPowerTone = "success" | "warning" | "neutral" | "none";

export interface VmActionItem {
    key: string;
    value: string;
    icon: IconName;
    color: ThemeColor;
    shortcut?: ShortcutKey;
}

function extractShortcutKey(shortcut: ShortcutKey): string {
    const splitted = shortcut?.split("Key");
    if (splitted.length === 1) return shortcut; // Backspace or Enter
    if (splitted.length === 2) return splitted[1];
    return "";
}

export const VmActionRow: RichSelectChildComponent<VmActionItem> = ({element, onSelect, dataProps}) => {
        if (!element) return null;
        return <Flex justifyContent={"space-between"} onClick={onSelect} {...dataProps}>
                <Box padding="8px">
                    <Icon name={element.icon} color={element.color} />
                    <span style={{padding: "4px"}}>{element.value}</span>
                </Box>
                <Box padding={"8px"}>
                    {element.shortcut ? <Shortcut alt name={""} keys={[extractShortcutKey(element.shortcut)]}></Shortcut> : <></>}
                </Box>
            </Flex>
    };

function getDefaultToneLook(tone : VmPowerTone) : string {
    if (tone === "none") {
        return SecondarySplitDropdownTrigger;
    }
    return PrimarySplitDropdownTrigger;

}

export const SecondarySplitDropdownTrigger = injectStyle("secondary-split-dropdown-trigger", k => `
    ${k} {
        position: relative;
        width: 35px;
        height: 35px;
        border-radius: 8px;
        user-select: none;
        -webkit-user-select: none;
        background: var(--secondaryMain);
        padding: 6px;
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
        border-left 1px;
        cursor: pointer;
    }

    ${k}:hover {
        background: var(--secondaryDark);
    }

    ${k}[data-disabled="true"] {
        opacity: 0.25;
        cursor: not-allowed;
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--secondaryMain);
    }

    ${k} > svg {
        color: var(--secondaryContrast);
        position: absolute;
        bottom: 9px;
        right: 10px;
        height: 16px;
    }
`);


export const PrimarySplitDropdownTrigger = injectStyle("primary-split-dropdown-trigger", k => `
    ${k} {
        position: relative;
        width: 35px;
        height: 35px;
        border-radius: 8px;
        user-select: none;
        -webkit-user-select: none;
        background: var(--primaryMain);
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);
        padding: 6px;
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
        cursor: pointer;
    }

    ${k}:hover {
        background: var(--primaryDark);
    }

    ${k}[data-disabled="true"] {
        opacity: 0.25;
        cursor: not-allowed;
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--primaryMain);
    }

    ${k} > svg {
        color: var(--primaryContrast);
        position: absolute;
        bottom: 9px;
        right: 10px;
        height: 16px;
    }
`);

const DangerSplitDropdownTrigger = injectStyle("danger-split-dropdown-trigger", k => `
    ${k} {
        background: var(--warningMain);
    }

    ${k}:hover {
        background: var(--warningDark);
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--warningMain);
    }

    ${k} > svg {
        color: var(--warningContrast);
    }
`);

const SuccessSplitDropdownTrigger = injectStyle("success-split-dropdown-trigger", k => `
    ${k} {
        background: var(--successMain);
    }

    ${k}:hover {
        background: var(--successDark);
    }

    ${k}[data-disabled="true"]:hover {
        background: var(--successMain);
    }

    ${k} > svg {
        color: var(--successContrast);
    }
`);

function getToneLook(tone: VmPowerTone): string {
    switch (tone) {
        case "success":
            return SuccessSplitDropdownTrigger;
        case "warning":
            return DangerSplitDropdownTrigger;
        default:
            return "";
    }
}

export const VmActionSplitButton: React.FunctionComponent<{
    tone: VmPowerTone;
    disabled: boolean;
    buttonColor: ThemeColor;
    buttonText: string;
    buttonIcon: IconName;
    onButtonClick: () => void;
    menuItems: VmActionItem[];
    shortcut?: ShortcutKey;
    onSelectMenuItem: (item: VmActionItem) => void;
    dropdownWidth?: string;
}> = ({
    tone,
    disabled,
    buttonColor,
    buttonText,
    buttonIcon,
    onButtonClick,
    menuItems,
    shortcut,
    onSelectMenuItem,
    dropdownWidth = "260px",
}) => {
    const powerDropdownClass = classConcat(
        getDefaultToneLook(tone),
        getToneLook(tone)
    );

    // Adding shortcut keys if any
    React.useEffect(() => {
        const handleKeyDown = (ev: KeyboardEvent) => {
            if (!ev.altKey) return;
            
            if (shortcut && ev.code === shortcut && !disabled) {
                ev.preventDefault();
                onButtonClick();
            }
            
            const matchingItem = menuItems.find(item => item.shortcut === ev.code);
            if (matchingItem && !disabled) {
                ev.preventDefault();
                onSelectMenuItem(matchingItem);
            }
        };
        
        document.addEventListener("keydown", handleKeyDown);
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [shortcut, onButtonClick, menuItems, onSelectMenuItem, disabled]);

    return <Flex>
        <Button color={buttonColor} onClick={onButtonClick} disabled={disabled} attachedLeft>
            <Flex justifyContent={"space-between"}>
                <span style={{marginRight: "8px"}}>
                    <Icon name={buttonIcon} mr="8px" />
                    {buttonText}
                </span>
                {shortcut ? <Shortcut name="" alt keys={[extractShortcutKey(shortcut)]}></Shortcut> : <></>}
            </Flex>
        </Button>
        <RichSelect
            items={menuItems}
            keys={["value"]}
            RenderRow={VmActionRow}
            onSelect={onSelectMenuItem}
            showSearchField={false}
            dropdownWidth={dropdownWidth}
            matchTriggerWidth={false}
            disabled={disabled}
            trigger={
                <div className={powerDropdownClass} data-disabled={disabled}>
                    <Icon name="heroChevronDown" />
                </div>
            }
        />
    </Flex>;
};
