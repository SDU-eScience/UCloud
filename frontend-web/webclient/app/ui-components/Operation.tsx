import {IconName} from "ui-components/Icon";
import {Box, Button, Flex, Icon, OutlineButton, Tooltip} from "ui-components/index";
import {PropsWithChildren, useCallback, useState} from "react";
import * as React from "react";
import {StyledComponent} from "styled-components";
import {TextSpan} from "ui-components/Text";
import ClickableDropdown, {ClickableDropdownProps} from "ui-components/ClickableDropdown";
import {preventDefault} from "UtilityFunctions";
import Grid from "ui-components/Grid";
import {ConfirmationButton} from "ui-components/ConfirmationAction";
import {ThemeColor} from "ui-components/theme";
import * as Heading from "ui-components/Heading";

type OperationComponentType = typeof OutlineButton | typeof Box | typeof Button | typeof Flex |
    typeof ConfirmationButton;

export type OperationLocation = "SIDEBAR" | "IN_ROW" | "TOPBAR";

/**
 * The enabled function can either return a boolean or a string.
 *
 * - A boolean value of true indicates that the operation is enabled and ready for use.
 * - A boolean value of false indicates that the operation is not enabled and the operation should be hidden.
 * - Any string value indicates that the operation is not enabled and should be disabled with a tooltip displaying
 *   the text as an explanation.
 */
export type OperationEnabled = boolean | string;

export interface Operation<T, R = undefined> {
    text: string;
    onClick: (selected: T[], extra: R) => void;
    enabled: (selected: T[], extra: R) => OperationEnabled;
    icon?: IconName;
    color?: ThemeColor;
    hoverColor?: ThemeColor;
    outline?: boolean;
    operationType?: (location: OperationLocation, allOperations: Operation<T, R>[]) => OperationComponentType;
    primary?: boolean;
    canAppearInLocation?: (location: OperationLocation) => boolean;
    confirm?: boolean;
}

export function defaultOperationType(
    location: OperationLocation,
    allOperations: Operation<unknown, unknown>[],
    op: Operation<unknown, unknown>,
): OperationComponentType {
    if (op.confirm === true) {
        return ConfirmationButton;
    } else if (op.primary) {
        return Button;
    } else if (allOperations.length === 1) {
        return OutlineButton;
    } else if (location === "IN_ROW" || location === "TOPBAR") {
        return Flex;
    } else {
        return Flex;
    }
}

const OperationComponent: React.FunctionComponent<{
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    As: React.ComponentType<any>;
    op: Operation<unknown, unknown>;
    extra: unknown;
    selected: unknown[];
    reasonDisabled?: string;
    location: OperationLocation;
    onAction: () => void;
}> = ({As, op, selected, extra, reasonDisabled, location, onAction}) => {
    const onClick = useCallback((e?: React.SyntheticEvent) => {
        if (op.primary === true) e?.stopPropagation();
        if (reasonDisabled !== undefined) return;
        op.onClick(selected, extra);
        onAction();
    }, [op, selected, extra, reasonDisabled, onAction]);

    // eslint-disable-next-line
    const extraProps: Record<string, any> = {};

    if (As === ConfirmationButton) {
        extraProps["onAction"] = onClick;
        extraProps["asSquare"] = (location === "IN_ROW" || location === "TOPBAR") && !op.primary;
        extraProps["actionText"] = op.text;
        extraProps["hoverColor"] = op.hoverColor;
        if (location === "SIDEBAR" || op.primary) {
            extraProps["align"] = "center"
            extraProps["fontSize"] = "14px"
        } else {
            extraProps["align"] = "left"
            extraProps["fontSize"] = "large"
            extraProps["ml"] = "-16px";
            extraProps["width"] = "calc(100% + 32px)"
        }
    }
    const component = <As
        cursor="pointer"
        color={reasonDisabled === undefined ? op.color : "gray"}
        alignItems="center"
        onClick={onClick}
        data-tag={`${op.text}-action`}
        disabled={reasonDisabled !== undefined}
        fullWidth={!op.primary || location !== "TOPBAR"}
        height={"38px"}
        icon={op.icon}
        {...extraProps}
    >
        {As === ConfirmationButton ? null : <>
            {op.icon ? <Icon size={20} mr="1em" name={op.icon}/> : null}
            <span>{op.text}</span>
        </>}
    </As>;

    if (reasonDisabled === undefined) return component;
    return <Tooltip trigger={component}>{reasonDisabled}</Tooltip>;
};

interface OperationProps<T, R = undefined> {
    location: OperationLocation;
    operations: Operation<T, R>[];
    selected: T[];
    extra: R;
    entityNameSingular: string;
    entityNamePlural?: string;
    dropdownTag?: string;
    row?: T;
    showSelectedCount?: boolean;
}

type OperationsType = <T, R = undefined>(props: PropsWithChildren<OperationProps<T, R>>, context?: any) =>
    JSX.Element | null;

export const Operations: OperationsType = props => {
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const closeDropdown = useCallback(() => {
        setDropdownOpen(false);
    }, [setDropdownOpen]);
    const openDropdown = useCallback(() => {
        setDropdownOpen(true);
    }, [setDropdownOpen]);
    if (props.operations.length === 0) return null;

    // Don't render anything if we are in row and we have selected something
    if (props.selected.length > 0 && props.location === "IN_ROW") return null;
    if (props.location === "IN_ROW" && !props.row) return null;

    const selected = props.location === "IN_ROW" ? [props.row!] : props.selected;

    const entityNamePlural = props.entityNamePlural ?? props.entityNameSingular + "s";

    const operations: { elem: JSX.Element, priority: number, primary: boolean }[] = props.operations
        .filter(op => op.enabled(selected, props.extra) !== false && op.canAppearInLocation?.(props.location) !== false)
        .map(op => {
            const enabled = op.enabled(selected, props.extra);
            let reasonDisabled: string | undefined = undefined;
            if (typeof enabled === "string") {
                reasonDisabled = enabled;
            }
            if (enabled === false) return null;

            const opTypeFn = op.operationType ?? ((a, b) => defaultOperationType(a, b, op));
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const As = opTypeFn(props.location, props.operations) as StyledComponent<any, any>;
            const elem = <OperationComponent key={op.text} As={As} op={op} extra={props.extra} selected={selected}
                                             reasonDisabled={reasonDisabled} location={props.location}
                                             onAction={closeDropdown}/>;
            const priority = As === OutlineButton ? 0 : As === Button ? 0 : As === Box ? 2 : 2;
            return {elem, priority, primary: op.primary === true};
        })
        .filter(op => op !== null)
        .map(op => op!);

    if (!(selected.length === 0 || props.operations.length === 1) && props.location === "SIDEBAR") {
        if (props.showSelectedCount ?? true) {
            operations.push({
                elem: <div key={"selected"}>
                    <TextSpan bold>
                        {selected.length}
                        {" "}
                        {selected.length === 1 ? props.entityNameSingular : entityNamePlural}
                        {" "}
                        selected
                    </TextSpan>
                </div>,
                priority: 1,
                primary: false
            });
        }
    }

    const content = operations
        .filter(it => !it.primary)
        .sort((a, b) => a.priority - b.priority)
        .map(it => it.elem);

    const primaryContent = operations.filter(it => it.primary).map(it => it.elem);

    const dropdownProps: ClickableDropdownProps<unknown> = {
        width: "220px",
        left: "-200px",
        open: dropdownOpen,
        onTriggerClick: openDropdown,
        keepOpenOnClick: true,
        onClose: closeDropdown,
        trigger: (
            <Icon
                onClick={preventDefault}
                ml={"5px"}
                mr={"10px"}
                name={"ellipsis"}
                size={"1em"}
                rotation={90}
                data-tag={props.dropdownTag}
            />
        )
    };

    switch (props.location) {
        case "IN_ROW":
            return <>
                {primaryContent}
                <Box mr={"10px"}/>
                {content.length === 0 ? <Box ml={"30px"}/> :
                    <Flex alignItems={"center"} justifyContent={"center"}>
                        <ClickableDropdown {...dropdownProps}>
                            {content}
                        </ClickableDropdown>
                    </Flex>
                }
            </>;

        case "SIDEBAR":
            if (content.length === 0 && primaryContent.length === 0) return null;
            return (
                <Grid gridTemplateColumns={"1 fr"} gridGap={"8px"} my={"8px"}>
                    {primaryContent}
                    {content}
                </Grid>
            );

        case "TOPBAR":
            return <>
                <Flex alignItems={"center"}>
                    <Heading.h3 flexGrow={1}>
                        {entityNamePlural}
                        {" "}
                        {props.selected.length === 0 ? null :
                            <TextSpan color={"gray"} fontSize={"80%"}>{props.selected.length} selected</TextSpan>
                        }
                    </Heading.h3>
                    {primaryContent}
                    <Box mr={"10px"}/>
                    {content.length === 0 ? <Box ml={"30px"}/> :
                        <Flex alignItems={"center"} justifyContent={"center"}>
                            <ClickableDropdown {...dropdownProps}>
                                {content}
                            </ClickableDropdown>
                        </Flex>
                    }
                    <Box mr={"8px"}/>
                </Flex>
            </>;
    }
};
