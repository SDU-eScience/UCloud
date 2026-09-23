import {IconName} from "@/ui-components/Icon";
import {Box, Button, Flex, Icon} from "@/ui-components/index";
import {PropsWithChildren} from "react";
import * as React from "react";
import {TextSpan} from "@/ui-components/Text";
import {doNothing, stopPropagation} from "@/UtilityFunctions";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {ThemeColor} from "@/ui-components/theme";
import * as Heading from "@/ui-components/Heading";
import {injectStyle} from "@/Unstyled";
import {ActionAppearance, ActionBar, ActionEntry, ActionItem, ActionMenu, ResourceBrowserActions} from "@/ui-components/Actions";

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

// Note(Jonas): The closes I could get to typesafe keys, as `KeyboardEvent["code"]` is just a string
export enum ShortcutKey {
    A = "KeyA",
    B = "KeyB",
    C = "KeyC",
    D = "KeyD",
    E = "KeyE",
    F = "KeyF",
    G = "KeyG",
    H = "KeyH",
    I = "KeyI",
    J = "KeyJ",
    K = "KeyK",
    L = "KeyL",
    M = "KeyM",
    N = "KeyN",
    O = "KeyO",
    // Reserved for Command Palette shortcut
    // P = "KeyP",
    Q = "KeyQ",
    R = "KeyR",
    S = "KeyS",
    T = "KeyT",
    U = "KeyU",
    V = "KeyV",
    W = "KeyW",
    X = "KeyX",
    Y = "KeyY",
    Z = "KeyZ",
    Backspace = "Backspace",
    Enter = "Enter"
}

export interface Operation<T, R = undefined> {
    text: string | ((selected: T[], extra: R) => string);
    onClick: (selected: T[], extra: R, all?: T[]) => void;
    enabled: (selected: T[], extra: R, all?: T[]) => OperationEnabled;
    shortcut?: ShortcutKey;
    icon?: IconName;
    iconRotation?: number;
    color?: ThemeColor;
    color2?: ThemeColor
    hoverColor?: ThemeColor;
    outline?: boolean;
    operationType?: (location: OperationLocation, allOperations: Operation<T, R>[]) => OperationComponentType;
    primary?: boolean;
    confirm?: boolean;
    confirmationText?: string | ((selected: T[], callbacks: R) => string);
    confirmationButtonText?: string | ((selected: T[], callbacks: R) => string);
    tag?: string;
    splitButtonGroupId?: string
}

export function defaultOperationType<T, Extra>(
    location: OperationLocation,
    allOperations: Operation<T, Extra>[],
    op: Operation<T, Extra>,
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

export interface OperationProps<EntityType, Extras = undefined> {
    topbarIcon?: IconName;
    location: OperationLocation;
    operations: Operation<EntityType, Extras>[];
    selected: EntityType[];
    extra: Extras;
    entityNameSingular: string;
    entityNamePlural?: string;
    row?: EntityType;
    showSelectedCount?: boolean;
    displayTitle?: boolean;
    all?: EntityType[];
    openFnRef?: React.RefObject<(left: number, top: number) => void>;
    hidden?: boolean;
    forceEvaluationOnOpen?: boolean;
}

type OperationsType = <EntityType, Extras = undefined>(props: PropsWithChildren<OperationProps<EntityType, Extras>>, context?: any) => React.ReactNode;

export function operationsToActions<T, C>(operations: Operation<T, C>[], all?: T[]): {
    actions: ActionEntry<T, C>[];
    appearance: (action: ActionItem<T, C>) => ActionAppearance | undefined;
} {
    const appearance = new Map<ActionItem<T, C>, ActionAppearance>();
    const convert = (operation: Operation<T, C>): ActionItem<T, C> => {
        const action: ActionItem<T, C> = {
            text: operation.text,
            enabled: (selected, callbacks) => operation.enabled(selected, callbacks, all),
            onClick: (selected, callbacks) => operation.onClick(selected, callbacks, all),
            icon: operation.icon,
            destructive: operation.confirm,
            confirmationText: operation.confirmationText,
            confirmationButtonText: operation.confirmationButtonText,
            tag: operation.tag,
            shortcut: operation.shortcut,
        };
        appearance.set(action, {
            color: operation.color,
            iconRotation: operation.iconRotation,
            iconSize: 20,
            iconSpacing: "1em",
            primary: operation.primary,
        });
        return action;
    };

    const actions: ActionEntry<T, C>[] = [];
    const handledGroups = new Set<string>();
    for (const operation of operations) {
        if (!operation.splitButtonGroupId) {
            actions.push(convert(operation));
            continue;
        }
        if (handledGroups.has(operation.splitButtonGroupId)) continue;
        handledGroups.add(operation.splitButtonGroupId);
        const group = operations.filter(candidate => candidate.splitButtonGroupId === operation.splitButtonGroupId);
        const [first, ...rest] = group;
        const parent = convert(first);
        parent.children = rest.map(convert);
        actions.push(parent);
    }

    return {actions, appearance: action => appearance.get(action)};
}

export function appendOperationsToActions<T, LegacyCallbacks, C extends LegacyCallbacks>(
    source: ResourceBrowserActions<T, C>,
    operations: Operation<T, LegacyCallbacks>[],
    all?: T[],
): ResourceBrowserActions<T, C> {
    if (!operations.length) return source;
    const additions = operationsToActions(operations, all).actions as ActionEntry<T, C>[];
    const topbar = source.topbar ?? source.contextMenu ?? [];
    const contextMenu = source.contextMenu ?? source.topbar ?? [];
    return {
        topbar: [...topbar, ...additions],
        contextMenu: [...contextMenu, ...additions],
        appearance: source.appearance,
        topbarMaxVisible: source.topbarMaxVisible,
    };
}

function NewOperations<EntityType, Extras>(props: PropsWithChildren<OperationProps<EntityType, Extras>>): React.ReactNode {
    const adapted = operationsToActions(props.operations, props.all);
    if (props.location === "IN_ROW") {
        const width = "47px";
        if (!props.hidden && !props.row) return <Box width={width} />;
        if (!props.hidden && props.selected.length > 0 && !props.selected.includes(props.row!)) return <Box width={width} />;
        if (!props.hidden && props.selected.length > 1) return <Box width={width} />;
        const selected = props.selected.length === 0 && props.row ? [props.row] : props.selected;
        if (props.hidden) return <ActionMenu
                actions={adapted.actions}
                selected={selected}
                callbacks={props.extra}
                appearance={adapted.appearance}
                openFnRef={props.openFnRef}
                trigger={null}
            />;

        const primary = adapted.actions.filter(entry => entry !== "divider" && adapted.appearance(entry)?.primary);
        const overflow = adapted.actions.filter(entry => entry === "divider" || !adapted.appearance(entry)?.primary);
        return <>
            <div onClick={stopPropagation} className={InRowPrimaryButtonsClass}>
                <ActionBar
                    actions={primary}
                    selected={selected}
                    callbacks={props.extra}
                    appearance={adapted.appearance}
                    compact
                    enableShortcuts={false}
                />
            </div>
            <Box mr="10px" />
            {overflow.length ? <ActionMenu
                actions={overflow}
                selected={selected}
                callbacks={props.extra}
                appearance={adapted.appearance}
                openFnRef={props.openFnRef}
            /> : <Box ml="29px" />}
        </>;
    }

    const entityNamePlural = props.entityNamePlural ?? props.entityNameSingular + "s";
    const primary: ActionEntry<EntityType, Extras>[] = [];
    const overflow: ActionEntry<EntityType, Extras>[] = [];
    for (const entry of adapted.actions) {
        if (entry !== "divider" && adapted.appearance(entry)?.primary) primary.push(entry);
        else overflow.push(entry);
    }
    if (overflow.length) {
        const overflowAction: ActionItem<EntityType, Extras> = {
            text: "",
            icon: "ellipsis",
            enabled: () => true,
            onClick: doNothing,
            children: overflow,
        };
        const originalAppearance = adapted.appearance;
        adapted.appearance = action => action === overflowAction ? {groupOnly: true, iconRotation: 90} : originalAppearance(action);
        primary.push(overflowAction);
    }

    return <Flex alignItems="center">
        {props.displayTitle === false ? null : <Heading.h3 flexGrow={1}>
            {props.topbarIcon ? <Icon name={props.topbarIcon} m={8} ml={0} size="20" color="iconColor2" /> : null}
            {entityNamePlural}{" "}
            {props.selected.length === 0 ? null : <TextSpan color="textSecondary" fontSize="80%">
                {props.selected.length} selected
            </TextSpan>}
        </Heading.h3>}
        <ActionBar
            actions={primary}
            selected={props.selected}
            callbacks={props.extra}
            appearance={adapted.appearance}
        />
        <Box mr="8px" />
    </Flex>;
}

export const Operations: OperationsType = props => {
    return <NewOperations {...props} />;
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
