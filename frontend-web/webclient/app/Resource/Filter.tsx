import * as React from "react";
import {useCallback, useState} from "react";
import {IconName} from "@/ui-components/Icon";
import {Box, Flex, Icon, Stamp} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Cursor} from "@/ui-components/Types";
import {ListRow, ListRowStat} from "@/ui-components/List";
import {SlimDatePickerClass} from "@/ui-components/DatePicker";
import {enGB} from "date-fns/locale";
import ReactDatePicker from "react-datepicker";
import {Toggle} from "@/ui-components/Toggle";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {getStartOfDay} from "@/Utilities/DateUtilities";
import {dateToStringNoTime} from "@/Utilities/DateUtilities";
import {BrowseType} from "./BrowseType";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

export interface SortFlags {
    sortBy?: string;
    sortDirection?: "ascending" | "descending";
}

export interface SortEntry {
    icon: IconName;
    title: string;
    column: string;
    helpText?: string;
}

export interface FilterWidgetProps {
    properties: Record<string, string>;
    onPropertiesUpdated: (updatedProperties: Record<string, string | undefined>) => void;
    expanded: boolean;
    browseType: BrowseType;
    id: number;
    onExpand: (id: number) => void;
}

export interface PillProps {
    canRemove?: boolean;
    properties: Record<string, string>;
    onDelete: (keys: string[]) => void;
}

const FilterPill: React.FunctionComponent<{
    icon: IconName;
    onRemove: () => void;
    canRemove?: boolean;
    children: React.ReactNode;
}> = ({icon, onRemove, canRemove, children}) => {
    return <Stamp onClick={canRemove ? onRemove : undefined} icon={icon}>
        {children}
    </Stamp>;
};

interface BaseFilterWidgetProps {
    icon: IconName;
    title: string;
}

const FilterWidgetWrapper = injectStyleSimple("filter-widget-wrapper", `
    display: flex;
    align-items: center;
    user-select: none;
    -webkit-user-select: none;
`);

const FilterWidget: React.FunctionComponent<{
    cursor?: Cursor;
    onClick?: () => void;
    browseType?: BrowseType;
    children?: React.ReactNode;
} & BaseFilterWidgetProps> = props => {
    return <Box className={FilterWidgetWrapper} mr={props.browseType === BrowseType.Embedded ? "16px" : undefined} cursor={props.cursor} onClick={props.onClick}>
        <Icon name={props.icon} size={"16px"} color={"iconColor"} color2={"iconColor2"} mr={"8px"} />
        <b>{props.title}</b>
        {props.children}
    </Box>
};

const ExpandableDropdownFilterWidget: React.FunctionComponent<{
    expanded: boolean;
    dropdownContent: React.ReactElement;
    onExpand: () => void;
    contentWidth?: string;
    facedownChevron?: boolean;
    browseType?: BrowseType;
    children?: React.ReactNode;
} & BaseFilterWidgetProps> = props => {
    const [open, setOpen] = useState(false);

    const trigger = (
        <FilterWidget browseType={props.browseType} icon={props.icon} title={props.title} cursor={"pointer"}
            onClick={props.expanded ? props.onExpand : undefined}>
            <Box flexGrow={1} />
            <Icon ml="6px" name={"heroChevronDown"} rotation={props.expanded || props.facedownChevron || open ? 0 : -90} size={"16px"}
                color={"iconColor"} />
        </FilterWidget>
    );

    if (props.browseType === BrowseType.MainContent || props.browseType === BrowseType.Embedded) {
        return <>
            <div onClick={() => setOpen(o => !o)}>{trigger}</div>
            {open ? props.dropdownContent : null}
            {!props.expanded ? null : props.children}
        </>;
    }

    return <div>
        {!props.expanded ?
            <ClickableDropdown
                fullWidth
                trigger={trigger}
                width={props.contentWidth}
                useMousePositioning
                colorOnHover={false}
                paddingControlledByContent
            >
                {props.dropdownContent}
            </ClickableDropdown> :
            trigger
        }

        {!props.expanded ? null : props.children}
    </div>;
};

export const ValuePill: React.FunctionComponent<{
    propertyName: string;
    showValue: boolean;
    secondaryProperties?: string[];
    valueToString?: (value: string) => string;
    children?: React.ReactNode;
} & PillProps & BaseFilterWidgetProps> = (props) => {
    const onRemove = useCallback(() => {
        const allProperties = [...(props.secondaryProperties ?? [])];
        allProperties.push(props.propertyName);
        props.onDelete(allProperties);
    }, [props.secondaryProperties, props.onDelete, props.propertyName]);

    const value = props.properties[props.propertyName];
    if (!value) return null;

    return <FilterPill icon={props.icon} onRemove={onRemove} canRemove={props.canRemove}>
        {props.title}
        {props.title.length > 0 && (props.showValue || props.children) ? ": " : null}
        {!props.showValue ? null : props.valueToString ? props.valueToString(value) : value}
        {props.children}
    </FilterPill>;
};

export interface EnumOption {
    icon?: IconName;
    value: string;
    title: string;
    helpText?: string;
}

interface EnumOptions {
    options: EnumOption[];
}

export const EnumPill: React.FunctionComponent<{
    propertyName: string;
} & PillProps & BaseFilterWidgetProps & EnumOptions> = props => {
    const onRemove = useCallback(() => {
        props.onDelete([props.propertyName]);
    }, [props.onDelete, props.propertyName]);

    const value = props.properties[props.propertyName];
    if (!value) return null;

    return <FilterPill icon={props.icon} onRemove={onRemove} canRemove={props.canRemove}>
        {props.title}: {props.options.find(it => it.value === value)?.title ?? value}
    </FilterPill>;
};

const EmbeddedOffset = injectStyle("embedded-offset", k => `
    ${k}[data-embedded="true"] {
        margin-left: -15px;
    }
`);

export const EnumFilterWidget: React.FunctionComponent<{
    propertyName: string;
    facedownChevron?: boolean;
    browseType?: BrowseType;
} & BaseFilterWidgetProps & FilterWidgetProps & EnumOptions> = props => {
    const onChange = useCallback((newValue: string) => {
        const properties: Record<string, string | undefined> = {};
        properties[props.propertyName] = newValue === "" ? undefined : newValue;
        props.onPropertiesUpdated(properties);
    }, [props.onPropertiesUpdated, props.propertyName]);

    return <ExpandableDropdownFilterWidget
        expanded={props.expanded}
        icon={props.icon}
        title={props.title}
        onExpand={doNothing}
        browseType={props.browseType}
        facedownChevron={props.facedownChevron}
        contentWidth={"300px"}
        dropdownContent={
            <div className={EmbeddedOffset} data-embedded={props.browseType === BrowseType.Embedded}>
                {props.options.map(opt =>
                    <ListRow
                        key={opt.value}
                        icon={!opt.icon ? null :
                            <Icon name={opt.icon} color={"iconColor"} color2={"iconColor2"} size={"16px"} />
                        }
                        left={opt.title}
                        leftSub={opt.helpText ? <ListRowStat>{opt.helpText}</ListRowStat> : null}
                        right={null}
                        fontSize={"16px"}
                        select={() => onChange(opt.value)}
                        stopPropagation={false}
                    />
                )}
            </div>
        }
    />
};

export function EnumFilter(
    icon: IconName,
    propertyName: string,
    title: string,
    options: EnumOption[]
): [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>] {
    return [
        (props) => <EnumFilterWidget options={options} propertyName={propertyName} icon={icon}
            title={title} {...props} />,
        (props) => <EnumPill options={options} propertyName={propertyName} icon={icon} title={title} {...props} />
    ];
}

export const CheckboxPill: React.FunctionComponent<{
    propertyName: string;
    invert?: boolean;
} & PillProps & BaseFilterWidgetProps> = props => {
    const onRemove = useCallback(() => {
        props.onDelete([props.propertyName]);
    }, [props.onDelete, props.propertyName]);

    const value = props.properties[props.propertyName];
    if (!value) return null;
    let isChecked = value === "true";
    if (props.invert === true) {
        isChecked = !isChecked;
    }

    return <FilterPill icon={props.icon} onRemove={onRemove} canRemove={props.canRemove}>
        {props.title}: {isChecked ? "Yes" : "No"}
    </FilterPill>;
};

export const CheckboxFilterWidget: React.FunctionComponent<{
    propertyName: string;
    invert?: boolean;
    browseType?: BrowseType
} & BaseFilterWidgetProps & FilterWidgetProps> = props => {
    const isTrue = props.properties[props.propertyName] === "true";
    const isChecked = props.invert === true ? !isTrue : isTrue;

    const onChange = useCallback(() => {
        const properties: Record<string, string | undefined> = {};
        properties[props.propertyName] = (!isTrue).toString();
        props.onPropertiesUpdated(properties);
    }, [isTrue, props.onPropertiesUpdated, props.propertyName]);

    return (
        <Flex>
            <Box mt="-3px">
                <FilterWidget
                    icon={props.icon}
                    title={props.title}
                    cursor="pointer"
                    browseType={props.browseType}
                    onClick={onChange}
                />
            </Box>
            <Box flexGrow={1} />
            <span style={{marginRight: props.browseType === BrowseType.Embedded ? "16px" : undefined}}>
                <Toggle onChange={onChange} checked={isChecked} />
            </span>
        </Flex>
    );
}

export function CheckboxFilter(
    icon: IconName,
    propertyName: string,
    title: string,
    invert?: boolean
): [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>] {
    return [
        (props) => <CheckboxFilterWidget propertyName={propertyName} icon={icon} title={title}
            invert={invert} {...props} />,
        (props) => <CheckboxPill propertyName={propertyName} icon={icon} title={title} invert={invert} {...props} />
    ];
}

export function ConditionalFilter(
    condition: () => boolean,
    baseFilter: [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>]
): [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>] {
    return [
        (props) => {
            if (condition()) {
                return baseFilter[0](props);
            } else {
                return null;
            }
        },
        (props) => {
            if (condition()) {
                return baseFilter[1](props);
            } else {
                return null;
            }
        }
    ];
}
