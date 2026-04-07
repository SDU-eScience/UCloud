import * as React from "react";
import {Box, Button, Flex, Icon} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import {classConcat, injectStyle} from "@/Unstyled";

export type VmPowerTone = "success" | "warning" | "neutral";

export interface VmActionItem {
    key: string;
    value: string;
    icon: IconName;
    color: ThemeColor;
}

export const VmActionRow: RichSelectChildComponent<VmActionItem> = ({element, onSelect, dataProps}) => {
    if (!element) return null;
    return <Box p="8px" onClick={onSelect} {...dataProps}>
        <Flex gap="8px" alignItems="center">
            <Icon name={element.icon} color={element.color} />
            <span>{element.value}</span>
        </Flex>
    </Box>;
};

export const SplitDropdownTrigger = injectStyle("split-dropdown-trigger", k => `
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

export const VmActionSplitButton: React.FunctionComponent<{
    tone: VmPowerTone;
    disabled: boolean;
    buttonColor: ThemeColor;
    buttonText: string;
    buttonIcon: IconName;
    onButtonClick: () => void;
    menuItems: VmActionItem[];
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
    onSelectMenuItem,
    dropdownWidth = "260px",
}) => {
    const powerDropdownClass =
        tone === "success"
            ? classConcat(SplitDropdownTrigger, SuccessSplitDropdownTrigger)
            : tone === "warning"
                ? classConcat(SplitDropdownTrigger, DangerSplitDropdownTrigger)
                : SplitDropdownTrigger;

    return <Flex>
        <Button color={buttonColor} onClick={onButtonClick} disabled={disabled} attachedLeft>
            <Icon name={buttonIcon} mr="8px" />
            {buttonText}
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
