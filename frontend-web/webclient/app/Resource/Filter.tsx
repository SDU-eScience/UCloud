import * as React from "react";
import {useCallback, useMemo, useState} from "react";
import {IconName} from "@/ui-components/Icon";
import {Box, Flex, Grid, Icon, Input, Stamp} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
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

function mergeProperties(
    properties: Record<string, string>,
    newProperties: Record<string, string | undefined>,
    setProperties: (p: Record<string, string>) => void
): Record<string, string> {
    const result: Record<string, string> = {...properties};
    for (const [key, value] of Object.entries(newProperties)) {
        if (value === undefined) {
            delete result[key];
        } else {
            result[key] = value;
        }
    }
    setProperties(result);
    return result;
}

export const ResourceFilter: React.FunctionComponent<{
    pills: React.FunctionComponent<PillProps>[];
    browseType: BrowseType;
    filterWidgets: React.FunctionComponent<FilterWidgetProps>[];
    sortEntries: SortEntry[];
    properties: Record<string, string>;
    readOnlyProperties?: Record<string, string>;
    setProperties: (props: Record<string, string>) => void;
    sortDirection: "ascending" | "descending";
    sortColumn?: string;
    onSortUpdated: (direction: "ascending" | "descending", column: string) => void;
}> = props => {
    const {properties, setProperties} = props;
    const [expanded, setExpanded] = useState<number | null>(null);
    const [sortProperties, setSortProperties] = useState<Record<string, string>>({});
    const combinedProperties = useMemo(
        () => ({...(props.readOnlyProperties ?? {}), ...properties}),
        [props.readOnlyProperties, properties]
    );

    const onSortDeleted = useCallback((keys: string[]) => {
        const result: Record<string, string> = {...(sortProperties)};
        for (const key of keys) {
            delete result[key];
        }

        setSortProperties(result);
    }, [setSortProperties, sortProperties]);

    const onPillDeleted = useCallback((keys: string[]) => {
        const result: Record<string, string> = {...(properties)};
        for (const key of keys) {
            delete result[key];
        }

        setProperties(result);
        setExpanded(null);
    }, [setProperties, setExpanded, properties]);

    const onPropertiesUpdated = useCallback((updatedProperties: Record<string, string | undefined>) => {
        mergeProperties(properties, updatedProperties, setProperties);
    }, [setProperties, properties]);

    const sortOptions = useMemo(() =>
        props.sortEntries.map(it => ({
            icon: it.icon,
            title: it.title,
            value: it.column,
            helpText: it.helpText
        })),
        [props.sortEntries]
    );

    const expand = useCallback((id: number) => {
        if (expanded === id) {
            setExpanded(null);
        } else {
            setExpanded(id);
        }
    }, [expanded, setExpanded]);

    const isEmbedded = props.browseType === BrowseType.Embedded;

    return <>
        {isEmbedded ? null :
            <Heading.h4 mt={"32px"} mb={"16px"}>
                <Icon name={"filterSolid"} size={"16px"} mr={"8px"} />
                Filter
            </Heading.h4>
        }
        <MainContentGrid browseType={BrowseType.Embedded}>
            <EnumPill propertyName={"column"} properties={sortProperties} onDelete={onSortDeleted}
                icon={"heroAdjustmentsHorizontal"} title={"Sort by"} options={sortOptions} canRemove={onSortDeleted != null} />
            {props.pills.map((Pill, idx) =>
                <Pill key={Pill.displayName + "_" + idx} properties={combinedProperties} onDelete={onPillDeleted} canRemove={onPillDeleted != null} />
            )}
        </MainContentGrid>
        <Grid gridGap={"20px"}
            mt={Object.keys(props.filterWidgets).length === 0 && Object.keys(sortProperties).length === 0 && Object.keys(properties).length === 0 ? null : "10px"}>
            <EmbeddedFilterDropdown embedded={isEmbedded}>
                {props.filterWidgets.map((Widget, idx) =>
                    <Widget id={idx} browseType={props.browseType} key={Widget.displayName + "_" + idx} properties={properties}
                        onPropertiesUpdated={onPropertiesUpdated} onExpand={expand} expanded={expanded == idx} />
                )}
            </EmbeddedFilterDropdown>
        </Grid>
    </>;
};

function MainContentGrid(props: React.PropsWithChildren<{browseType: BrowseType}>): JSX.Element {
    return props.browseType !== BrowseType.MainContent ? (
        <>{props.children}</>
    ) : (
        <Grid gridGap={"8px"}>
            {props.children}
        </Grid>
    );
}

function EmbeddedFilterDropdown(props: React.PropsWithChildren<{embedded: boolean}>): JSX.Element {
    return props.embedded ? (
        <ClickableDropdown chevron width="250px" trigger="Filters" keepOpenOnClick colorOnHover={false}>
            <Grid mr="5px" gridAutoFlow="row" gridGap={"12px"}>
                {props.children}
            </Grid>
        </ClickableDropdown>
    ) : (<>
        {props.children}
    </>);
}

export const FilterPill: React.FunctionComponent<{
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
`);

export const FilterWidget: React.FunctionComponent<{
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

export const ExpandableFilterWidget: React.FunctionComponent<{
    expanded: boolean;
    onExpand: () => void;
    browseType?: BrowseType;
    children: React.ReactNode;
} & BaseFilterWidgetProps> = props => {
    return <div>
        <FilterWidget icon={props.icon} browseType={props.browseType} title={props.title} onClick={props.onExpand} cursor={"pointer"}>
            <Box flexGrow={1} />
            <Icon name={"chevronDownLight"} rotation={props.expanded ? 0 : -90} size={"16px"} color={"iconColor"} />
        </FilterWidget>
        {!props.expanded ? null : props.children}
    </div>;
};

export const ExpandableDropdownFilterWidget: React.FunctionComponent<{
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
            <Icon ml="6px" name={"chevronDownLight"} rotation={props.expanded || props.facedownChevron || open ? 0 : -90} size={"16px"}
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

export const TextPill: React.FunctionComponent<{
    propertyName: string;
} & PillProps & BaseFilterWidgetProps> = props => {
    const onRemove = useCallback(() => {
        props.onDelete([props.propertyName]);
    }, [props.onDelete, props.propertyName]);

    const value = props.properties[props.propertyName];
    if (!value) return null;

    return <FilterPill icon={props.icon} onRemove={onRemove} canRemove={props.canRemove}>
        {props.title}: {value}
    </FilterPill>;
};

export const TextFilterWidget: React.FunctionComponent<{
    propertyName: string
} & BaseFilterWidgetProps & FilterWidgetProps> = props => {
    const onExpand = useCallback(() => props.onExpand(props.id), [props.onExpand, props.id]);
    const currentValue = props.properties[props.propertyName] ?? "";
    const onChange = useCallback((e: React.SyntheticEvent) => {
        const newValue = (e.target as HTMLInputElement).value;
        const properties: Record<string, string | undefined> = {};
        properties[props.propertyName] = newValue === "" ? undefined : newValue;
        props.onPropertiesUpdated(properties);
    }, [props.onPropertiesUpdated, props.propertyName]);
    return <ExpandableFilterWidget browseType={props.browseType} expanded={props.expanded} icon={props.icon} title={props.title} onExpand={onExpand}>
        <Input autoFocus value={currentValue} onChange={onChange} width={props.browseType === BrowseType.Embedded ? "calc(100% - 16px)" : undefined} />
    </ExpandableFilterWidget>;
};

export function TextFilter(
    icon: IconName,
    propertyName: string,
    title: string
): [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>] {
    return [
        (props) => <TextFilterWidget propertyName={propertyName} icon={icon} title={title} {...props} />,
        (props) => <TextPill propertyName={propertyName} icon={icon} title={title} {...props} />
    ];
}

export const DateRangePill: React.FunctionComponent<{
    beforeProperty: string;
    afterProperty: string
} & PillProps & BaseFilterWidgetProps> = props => {
    const onRemove = useCallback(() => {
        props.onDelete([props.beforeProperty, props.afterProperty]);
    }, [props.onDelete, props.beforeProperty, props.afterProperty]);

    const after = props.properties[props.afterProperty];
    if (!after) return null;

    const before = props.properties[props.beforeProperty];

    return <>
        <FilterPill icon={props.icon} onRemove={onRemove} canRemove={props.canRemove}>
            {before ?
                <>
                    {props.title} between: {dateToStringNoTime(parseInt(after))} - {dateToStringNoTime(parseInt(before))}
                </> :
                <>
                    {props.title} after: {dateToStringNoTime(parseInt(after))}
                </>
            }
        </FilterPill>
    </>;
};

const DateRangeEntry: React.FunctionComponent<{title: string; range: string; onClick?: () => void;}> = props => {
    return <ListRow
        select={props.onClick}
        fontSize={"16px"}
        icon={<Icon name={"calendar"} size={"20px"} ml={"16px"} />}
        left={props.title}
        leftSub={<ListRowStat>{props.range}</ListRowStat>}
        right={null}
        stopPropagation={false}
    />;
};

export const DateRangeFilterWidget: React.FunctionComponent<{
    beforeProperty: string;
    afterProperty: string
} & BaseFilterWidgetProps & FilterWidgetProps> = props => {
    const onExpand = useCallback(() => props.onExpand(props.id), [props.onExpand, props.id]);
    const [isSelectingRange, setIsSelectingRange] = useState(false);
    const toggleIsSelectingRange = useCallback(() => setIsSelectingRange(prev => !prev), [setIsSelectingRange]);
    const createdAfter = props.properties[props.afterProperty] ?? getStartOfDay(new Date()).getTime();
    const createdBefore = props.properties[props.beforeProperty];

    const updateDates = useCallback((dates: [Date, Date] | Date) => {
        if (Array.isArray(dates)) {
            const [start, end] = dates;
            const newCreatedAfter = start.getTime();
            const newCreatedBefore = end?.getTime();
            const newProps: Record<string, string | undefined> = {};
            newProps[props.afterProperty] = newCreatedAfter.toString();
            newProps[props.beforeProperty] = newCreatedBefore?.toString() ?? undefined;
            props.onPropertiesUpdated(newProps);
        } else {
            const newCreatedAfter = dates.getTime();
            const newProps: Record<string, string | undefined> = {};
            newProps[props.afterProperty] = newCreatedAfter.toString();
            newProps[props.beforeProperty] = undefined;
            props.onPropertiesUpdated(newProps);
        }
    }, [props.beforeProperty, props.afterProperty, props.onPropertiesUpdated]);

    const todayMs = getStartOfDay(new Date(timestampUnixMs())).getTime();
    const yesterdayEnd = todayMs - 1;
    const yesterdayStart = getStartOfDay(new Date(todayMs - 1)).getTime();
    const pastWeekEnd = new Date(timestampUnixMs()).getTime();
    const pastWeekStart = getStartOfDay(new Date(pastWeekEnd - (7 * 1000 * 60 * 60 * 24))).getTime();
    const pastMonthEnd = new Date(timestampUnixMs()).getTime();
    const pastMonthStart = getStartOfDay(new Date(pastMonthEnd - (30 * 1000 * 60 * 60 * 24))).getTime();

    return <ExpandableDropdownFilterWidget
        expanded={props.expanded}
        contentWidth={"300px"}
        browseType={BrowseType.Embedded}
        dropdownContent={
            <>
                <DateRangeEntry
                    title={"Today"}
                    range={dateToStringNoTime(todayMs)}
                    onClick={() => {
                        updateDates(new Date(todayMs));
                    }}
                />
                <DateRangeEntry
                    title={"Yesterday"}
                    range={`${dateToStringNoTime(yesterdayStart)}`}
                    onClick={() => {
                        updateDates([new Date(yesterdayStart), new Date(yesterdayEnd)]);
                    }}
                />
                <DateRangeEntry
                    title={"Past week"}
                    range={`${dateToStringNoTime(pastWeekStart)} - ${dateToStringNoTime(pastWeekEnd)}`}
                    onClick={() => {
                        updateDates([new Date(pastWeekStart), new Date(pastWeekEnd)]);
                    }}
                />
                <DateRangeEntry
                    title={"Past month"}
                    range={`${dateToStringNoTime(pastMonthStart)} - ${dateToStringNoTime(pastMonthEnd)}`}
                    onClick={() => {
                        updateDates([new Date(pastMonthStart), new Date(pastMonthEnd)]);
                    }}
                />
                <DateRangeEntry
                    title={"Custom"}
                    range={"Enter your own period"}
                    onClick={onExpand}
                />
            </>
        }
        onExpand={onExpand}
        icon={props.icon}
        title={props.title}>
        <Flex mt={"8px"} mb={"16px"}>
            <Box flexGrow={1} cursor={"pointer"} onClick={toggleIsSelectingRange}>Filter by period</Box>
            <Toggle onChange={toggleIsSelectingRange} checked={isSelectingRange} />
        </Flex>

        {isSelectingRange ? "Created between:" : "Created after:"}
        <div className={SlimDatePickerClass}>
            <ReactDatePicker
                locale={enGB}
                startDate={new Date(parseInt(createdAfter))}
                endDate={createdBefore ? new Date(parseInt(createdBefore)) : undefined}
                onChange={updateDates}
                selectsRange={isSelectingRange}
                inline
                dateFormat="dd/MM/yy HH:mm"
            />
        </div>
    </ExpandableDropdownFilterWidget>
}

export function DateRangeFilter(
    icon: IconName,
    title: string,
    beforeProperty: string,
    afterProperty: string
): [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>] {
    return [
        (props) => <DateRangeFilterWidget beforeProperty={beforeProperty} afterProperty={afterProperty}
            icon={icon} title={title} {...props} />,
        (props) => <DateRangePill beforeProperty={beforeProperty} afterProperty={afterProperty}
            icon={icon} title={title} {...props} />,
    ];
}

export const StaticPill: React.FunctionComponent<{
    value: string
} & PillProps & BaseFilterWidgetProps> = (props) => {
    return <FilterPill icon={props.icon} onRemove={doNothing} canRemove={false}>
        {props.value}
    </FilterPill>
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
                return <>{baseFilter[0](props)}</>;
            } else {
                return null;
            }
        },
        (props) => {
            if (condition()) {
                return <>{baseFilter[1](props)}</>;
            } else {
                return null;
            }
        }
    ];
}
