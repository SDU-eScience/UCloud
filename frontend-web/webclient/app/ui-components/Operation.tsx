import {IconName} from "@/ui-components/Icon";
import {Box, Button, Flex, Icon, Tooltip} from "@/ui-components/index";
import {EventHandler, MouseEvent, PropsWithChildren, useCallback, useRef, useState} from "react";
import * as React from "react";
import {TextSpan} from "@/ui-components/Text";
import ClickableDropdown, {ClickableDropdownProps} from "@/ui-components/ClickableDropdown";
import {doNothing, preventDefault, stopPropagation} from "@/UtilityFunctions";
import Grid from "@/ui-components/Grid";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import theme, {ThemeColor} from "@/ui-components/theme";
import * as Heading from "@/ui-components/Heading";
import {injectStyle} from "@/Unstyled";

type OperationComponentType = typeof Box | typeof Button | typeof Flex |
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
    text: string | ((selected: T[], extra: R) => string);
    onClick: (selected: T[], extra: R, all?: T[]) => void;
    enabled: (selected: T[], extra: R, all?: T[]) => OperationEnabled;
    icon?: IconName;
    iconRotation?: number;
    color?: ThemeColor;
    hoverColor?: ThemeColor;
    outline?: boolean;
    operationType?: (location: OperationLocation, allOperations: Operation<T, R>[]) => OperationComponentType;
    primary?: boolean;
    canAppearInLocation?: (location: OperationLocation) => boolean;
    confirm?: boolean;
    tag?: string;
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
        return Button;
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
    all?: unknown[];
    reasonDisabled?: string;
    location: OperationLocation;
    onAction: () => void;
    text: string;
}> = ({As, op, selected, all, extra, reasonDisabled, location, onAction, text}) => {
    const onClick = useCallback((e?: React.SyntheticEvent) => {
        if (op.primary === true) e?.stopPropagation();
        if (reasonDisabled !== undefined) return;
        op.onClick(selected, extra, all);
        onAction();
    }, [op, selected, extra, reasonDisabled, onAction]);

    // eslint-disable-next-line
    const extraProps: Record<string, any> = {};

    if (As === ConfirmationButton) {
        extraProps["onAction"] = onClick;
        extraProps["asSquare"] = !op.primary || location === "SIDEBAR";
        extraProps["actionText"] = text;
        extraProps["hoverColor"] = op.hoverColor;
        if (op.primary && location === "IN_ROW") {
            extraProps["align"] = "center";
            extraProps["fontSize"] = "14px";
            extraProps["mx"] = "12px";
        } else {
            extraProps["align"] = "left";
            extraProps["fontSize"] = "large";
            extraProps["ml"] = "-16px";
            extraProps["width"] = "calc(100% + 32px)";
        }
    }

    const component = <As
        cursor="pointer"
        color={reasonDisabled === undefined ? op.color : "gray"}
        alignItems="center"
        onClick={onClick}
        data-tag={`${text.replace(/\./g, "").replace(/ /g, "_")}-action`}
        disabled={reasonDisabled !== undefined}
        fullWidth={!op.primary || location !== "TOPBAR"}
        height={"38px"}
        icon={op.icon}
        {...extraProps}
    >
        {As === ConfirmationButton ? null : <>
            {op.icon ? <Icon size={20} mr="1em" name={op.icon} rotation={op.iconRotation} /> : null}
            <span>{text}</span>
        </>}
    </As>;

    if (reasonDisabled === undefined) {
        return component;
    }

    return <Tooltip trigger={component}>{reasonDisabled}</Tooltip>;
};

interface OperationProps<EntityType, Extras = undefined> {
    topbarIcon?: IconName;
    location: OperationLocation;
    operations: Operation<EntityType, Extras>[];
    selected: EntityType[];
    extra: Extras;
    entityNameSingular: string;
    entityNamePlural?: string;
    dropdownTag?: string;
    row?: EntityType;
    showSelectedCount?: boolean;
    displayTitle?: boolean;
    all?: EntityType[];
    openFnRef?: React.MutableRefObject<(left: number, top: number) => void>;
    hidden?: boolean;
    forceEvaluationOnOpen?: boolean;
}

type OperationsType = <EntityType, Extras = undefined>(props: PropsWithChildren<OperationProps<EntityType, Extras>>, context?: any) =>
    JSX.Element | null;

export const Operations: OperationsType = props => {
    const closeDropdownRef = useRef<() => void>(doNothing);
    const closeDropdown = () => closeDropdownRef.current();

    const [, forceRender] = useState(0);
    const dropdownOpenFn = useRef<(left: number, top: number) => void>(doNothing);
    const open = useCallback((left: number, top: number) => {
        if (props.forceEvaluationOnOpen) {
            forceRender(p => p + 1);
        }

        dropdownOpenFn.current(left, top);
    }, []);
    if (props.openFnRef) props.openFnRef.current = open;

    if (props.location === "IN_ROW") {
        // Don't render anything if we are in row and we have selected something
        const WIDTH = "51px";
        if (!props.row) return <Box width={WIDTH} />;
        if (props.selected.length > 0 && !props.selected.includes(props.row)) return <Box width={WIDTH} />;
    }

    const selected = props.location === "IN_ROW" && props.selected.length === 0 ? [props.row!] : props.selected;

    const entityNamePlural = props.entityNamePlural ?? props.entityNameSingular + "s";

    const operations: {elem: JSX.Element, priority: number, primary: boolean}[] = props.operations
        .filter(op => op.enabled(selected, props.extra, props.all) !== false && op.canAppearInLocation?.(props.location) !== false)
        .map(op => {
            const enabled = op.enabled(selected, props.extra, props.all);
            let reasonDisabled: string | undefined = undefined;
            if (typeof enabled === "string") {
                reasonDisabled = enabled;
            }
            if (enabled === false) return null;

            const text = typeof op.text === "string" ? op.text : op.text(selected, props.extra);
            const opTypeFn = op.operationType ?? ((a, b) => defaultOperationType(a, b, op));
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const As = opTypeFn(props.location, props.operations) as React.ComponentType<any>;
            const elem = <OperationComponent key={text} As={As} op={op} extra={props.extra} selected={selected}
                reasonDisabled={reasonDisabled} location={props.location} all={props.all}
                onAction={closeDropdown} text={text} />;
            const priority = As === Button ? 0 : As === Box ? 2 : 2;
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
        keepOpenOnClick: true,
        useMousePositioning: true,
        closeFnRef: closeDropdownRef,
        openFnRef: dropdownOpenFn,
        trigger: (
            props.hidden ? null :
                (props.location === "IN_ROW" && [0, 1].includes(props.selected.length))
                    || props.location === "TOPBAR" ?
                    <Icon
                        onClick={preventDefault}
                        ml={"5px"}
                        mr={"10px"}
                        name={"ellipsis"}
                        size={"1em"}
                        rotation={90}
                        data-tag={props.dropdownTag}
                    /> : <Box ml={"33px"} />
        )
    };

    if (props.hidden === true) {
        return <ClickableDropdown {...dropdownProps}>
            {content}
        </ClickableDropdown>
    } else {
        switch (props.location) {
            case "IN_ROW":
                return <>
                    <div onClick={stopPropagation} className={InRowPrimaryButtonsClass}>{primaryContent}</div>
                    <Box mr={"10px"} />
                    {content.length === 0 ? <Box ml={"33px"} /> :
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
                        {props.displayTitle === false ? null :
                            <Heading.h3 flexGrow={1}>
                                {props.topbarIcon ?
                                    <Icon
                                        name={props.topbarIcon}
                                        m={8}
                                        ml={0}
                                        size="20"
                                        color={theme.colors.darkGray}
                                    /> :
                                    null
                                }
                                {entityNamePlural}
                                {" "}
                                {props.selected.length === 0 ? null :
                                    <TextSpan color={"gray"}
                                        fontSize={"80%"}>{props.selected.length} selected</TextSpan>
                                }
                            </Heading.h3>
                        }
                        {primaryContent}
                        <Box mr={"10px"} />
                        {content.length === 0 ? <Box ml={"30px"} /> :
                            <Flex alignItems={"center"} justifyContent={"center"}>
                                <ClickableDropdown {...dropdownProps}>
                                    {content}
                                </ClickableDropdown>
                            </Flex>
                        }
                        <Box mr={"8px"} />
                    </Flex>
                </>;
        }
    }
};

const InRowPrimaryButtonsClass = injectStyle("in-row-primary-buttons", k => `
    ${k} {
        margin-top: 4px;
        margin-left: 8px;
    }
    
    ${k} > button {
        max-width: 150px;
    }
`);

export function useOperationOpener(): [React.MutableRefObject<(left: number, top: number) => void>, EventHandler<MouseEvent<never>>] {
    const openOperationsRef = useRef<(left: number, top: number) => void>(doNothing);
    const onContextMenu = useCallback<EventHandler<MouseEvent<never>>>((e) => {
        e.stopPropagation();
        e.preventDefault();
        openOperationsRef.current(e.clientX, e.clientY);
    }, []);
    return [openOperationsRef, onContextMenu];
}
