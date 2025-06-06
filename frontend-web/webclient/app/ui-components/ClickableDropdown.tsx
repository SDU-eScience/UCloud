import * as React from "react";
import * as ReactDOM from "react-dom";
import {Icon} from "@/ui-components";
import * as Text from "@/ui-components/Text";
import Box from "./Box";
import {Dropdown, DropdownContent} from "./Dropdown";
import {PropsWithChildren, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {FlexClass} from "./Flex";
import {clamp} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";

export interface ClickableDropdownProps<T> {
    trigger: React.ReactNode;
    options?: {text: string; value: T}[];

    keepOpenOnClick?: boolean;
    keepOpenOnOutsideClick?: boolean;
    onChange?: (value: T) => void;
    onOpeningTriggerClick?: () => void;
    onClose?: () => void;
    open?: boolean;

    fullWidth?: boolean;
    height?: number;
    rightAligned?: boolean;
    width?: string | number;
    minWidth?: string;
    left?: string | number;
    top?: string | number;
    bottom?: string | number;
    right?: string | number;

    useMousePositioning?: boolean;
    paddingControlledByContent?: boolean;
    noYPadding?: boolean;

    /**
     *   Intended usage is that either:
     *
     *       - User (programmer) adds the `arrowkeyNavigationKey` to the passed `children`, that the dropdown renders
     *
     *   With the above in mind, the `onSelect` function will be called on pressing "Enter", but unless the key is provided,
     *   nothing will happen, so adding `onSelect` without `arrowkeyNavigationKey` has no effect.
     **/
    arrowkeyNavigationKey?: string; // e.g. "data-entry";
    hoverColor?: ThemeColor;
    /**
     * Requires `arrowkeyNavigationKey` to be set or that `props.children` are provided.
     **/
    onSelect?: (el: HTMLElement | undefined) => void;

    chevron?: boolean;
    overflow?: string;
    colorOnHover?: boolean;
    squareTop?: boolean;

    // NOTE(Dan): I am sorry for having to go imperative but this is needed to force close a mouse positioned
    // dropdown. Otherwise, this will cause issues for confirmation buttons (which require a hold action versus a
    // click).
    closeFnRef?: React.RefObject<() => void>;
    openFnRef?: React.RefObject<(left: number, top: number) => void>;
    onKeyDown?: (ev: KeyboardEvent) => boolean | void;
}

const dropdownPortal = "dropdown-portal";

function ClickableDropdown<T>({
    keepOpenOnClick, onChange, onOpeningTriggerClick, ...props
}: PropsWithChildren<ClickableDropdownProps<T>>): React.ReactNode {
    const dropdownRef = useRef<HTMLDivElement>(null);
    const [open, setOpen] = useState(props.open ?? false);
    const [location, setLocation] = useState<[number, number]>([0, 0]);
    const isControlled = useMemo(() => props.open !== undefined, []);
    let portal = document.getElementById(dropdownPortal);
    if (!portal) {
        const elem = document.createElement("div");
        elem.id = dropdownPortal;
        document.body.appendChild(elem);
        portal = elem;
    }

    if (isControlled && props.useMousePositioning) {
        throw "Cannot use a controlled dropdown with useMousePositioning";
    }

    useEffect(() => {
        if (isControlled && props.open !== undefined) {
            setOpen(props.open);
        }
    }, [props.open]);

    const close = useCallback(() => {
        if (isControlled && props.onClose) props.onClose();
        else if (!isControlled) setOpen(false);
    }, [props.onClose]);
    if (props.closeFnRef) props.closeFnRef.current = close;

    const doOpen = useCallback(() => {
        onOpeningTriggerClick?.();
        if (!isControlled) setOpen(true);
    }, [onOpeningTriggerClick]);

    const forceOpen = useCallback((left: number, top: number) => {
        setLocation([left, top]);
        doOpen();
    }, [doOpen, setLocation]);
    if (props.openFnRef) props.openFnRef.current = forceOpen;

    const toggle = useCallback((e: React.MouseEvent) => {
        if (open) close();
        else doOpen();

        if (props.useMousePositioning) {
            setLocation([e.clientX, e.clientY]);
        }
    }, [open, props.useMousePositioning]);

    let neither = true;
    if (props.children) neither = false;
    if (!!onChange && !!props.options) neither = false;
    if (neither) throw Error("Clickable dropdown must have either children prop or options and onChange");

    // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
    const handleClickOutside = useCallback(event => {
        if (props.keepOpenOnOutsideClick) return;
        if (dropdownRef.current && !dropdownRef.current.contains(event.target) && open &&
            !portal?.contains(event.target)) {
            close();
        }
    }, [props.keepOpenOnOutsideClick, open]);

    const counter = useRef(-1);
    const divRef = useRef<HTMLDivElement>(null);

    const handleKeyPress: (ev: KeyboardEvent) => void = useCallback((event): void => {
        if (props.arrowkeyNavigationKey) {
            const navigationKey = props.arrowkeyNavigationKey ?? "data-active";
            _onKeyDown(event, divRef, counter, navigationKey, props.onSelect, props.hoverColor ?? "primaryLight")
        }

        if (event.key === "Escape" && open) {
            close();
        } else {
            props.onKeyDown?.(event)
        }
    }, [open]);

    useEffect(() => {
        counter.current = -1;
    }, [open]);

    useEffect(() => {
        if (open) {
            document.addEventListener("click", handleClickOutside);
            divRef.current?.addEventListener("keydown", handleKeyPress);
        } else {
            document.removeEventListener("click", handleClickOutside);
            divRef.current?.removeEventListener("keydown", handleKeyPress);
        }
        return () => {
            document.removeEventListener("click", handleClickOutside);
            divRef.current?.removeEventListener("keydown", handleKeyPress);
        };
    }, [handleClickOutside, handleKeyPress, open]);

    let children: React.ReactNode[] = [];
    if (props.options !== undefined && onChange) {
        const onChangeFunc = onChange;
        children = props.options.map((opt, i) => (
            <Box
                cursor="pointer"
                width="auto"
                key={i}
                data-active={""}
                onClick={() => onChangeFunc(opt.value)}
            >
                {opt.text}
            </Box>
        ));
    } else if (props.children) {
        if (Array.isArray(props.children)) children = props.children;
        else children = [props.children];
    }
    const emptyChildren = (React.Children.map(children, it => it) ?? []).length === 0;
    let width = props.fullWidth && !props.useMousePositioning ? "100%" : props.width;
    let top = !props.useMousePositioning ? props.top : location[1];

    if (props.rightAligned && !props.width) {
        width = undefined;
        const f = dropdownRef.current;
        let boundingClientRect = f?.getBoundingClientRect();
        if (boundingClientRect) {
            top = boundingClientRect.top + boundingClientRect.height;
        }
    }

    let left = !props.useMousePositioning ? props.left : location[0];
    if (props.rightAligned) {
        left = extractLeftAlignedPosition(dropdownRef.current, width) ?? left;
    }

    if (props.useMousePositioning) {
        if (width === undefined) width = 300;
        const widthAsNumber = parseInt(width.toString().replace("px", ""));
        const leftAsNumber = parseInt((left ?? 0).toString().replace("px", ""));
        left = leftAsNumber - widthAsNumber;
        if (left < 0) left = leftAsNumber;

        const topAsNumber = parseInt((top ?? 0).toString().replace("px", ""));
        let estimatedHeight = 38 * children.length;
        if (props.height) {
            estimatedHeight = Math.max(props.height, estimatedHeight);
        }

        if (window.innerHeight - (topAsNumber + estimatedHeight) < 50) {
            top = topAsNumber - estimatedHeight;
        }
    } else if (props.rightAligned) {
        // Fixed positioning, but not based on the mouse. We need to push the content around to make sure that we have
        // space for it.

        const screenHeight = window.innerHeight;
        const screenWidth = window.innerWidth;

        let x = parseInt((left ?? "0")?.toString().replace("px", ""));
        if (isNaN(x)) x = 0;

        let y = parseInt((top ?? dropdownRef.current?.getBoundingClientRect().y ?? "0")?.toString().replace("px", ""));
        if (isNaN(y)) y = 0;

        const widthAsNumber = parseInt((width ?? 300).toString().replace("px", ""));
        let heightAsNumber = 38 * children.length;
        if (props.height) {
            heightAsNumber = Math.max(props.height, heightAsNumber);
        }

        if (x + widthAsNumber >= screenWidth) {
            left = x - widthAsNumber;
        }

        if (props.height) {
            if (y + props.height! >= screenHeight) {
                top = y - props.height!;
            }
        } else if (y + heightAsNumber >= screenHeight) {
            top = y - heightAsNumber;
        }
    }

    const dropdownContent = <DropdownContent
        dropdownRef={divRef}
        overflow={props.height ? "auto" : "visible"}
        squareTop={props.squareTop}
        cursor="pointer"
        {...(props as any)}
        top={top}
        left={left}
        fixed={props.rightAligned || props.useMousePositioning}
        maxHeight={`${props.height}px`}
        width={width}
        hover={false}
        visible={open}
        onClick={e => {
            e.stopPropagation();
            if (!keepOpenOnClick) close();
        }}
    >
        {children}
    </DropdownContent>;

    return (
        <Dropdown data-tag="dropdown" divRef={dropdownRef} fullWidth={props.fullWidth}>
            <Text.TextSpan
                cursor="pointer"
                className={FlexClass}
                onClick={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    toggle(e);
                }}
            >
                {props.trigger}{props.chevron ? <Icon name="chevronDownLight" my="auto" size="1em" ml=".7em" color="textPrimary" /> : null}
            </Text.TextSpan>
            {emptyChildren || !open ? null : (
                props.useMousePositioning ?
                    ReactDOM.createPortal(
                        dropdownContent,
                        portal
                    ) : dropdownContent
            )}
        </Dropdown>
    );

}

function extractLeftAlignedPosition(el: HTMLDivElement | null, width: string | number | undefined): string | null {
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    if (width === undefined || width === "100%") return rect.x + "px";
    return `calc(${rect.x + rect.width}px - ${width})`;
}

export default ClickableDropdown;


function _onKeyDown(
    e: KeyboardEvent,
    wrapper: React.RefObject<HTMLDivElement | null>,
    index: React.RefObject<number>,
    entryKey: string,
    onSelect: ((el: Element | undefined) => void) | undefined,
    hoverColor: ThemeColor,
) {
    if (!wrapper.current) return;
    const isUp = e.key === "ArrowUp";
    const isDown = e.key === "ArrowDown";
    const isEnter = e.key === "Enter";

    if (isUp || isDown || isEnter) {
        e.preventDefault();
        e.stopPropagation();
    }

    const listEntries = wrapper.current?.querySelectorAll(`[${entryKey}]`);
    // If listEntries.length has changed, the active index may no longer be valid, but we may also not
    // have used the keys yet, so -1 can be the active index.
    index.current = clamp(index.current, -1, listEntries.length - 1);
    if (listEntries.length === 0) return;

    const oldIndex = index.current;
    if (isDown) {
        index.current += 1;
        if (index.current >= listEntries.length) {
            index.current = 0;
        }
    } else if (isUp) {
        index.current -= 1;
        if (index.current < 0) {
            index.current = listEntries.length - 1;
        }
    }

    if (isUp || isDown) {
        if (oldIndex !== -1) listEntries.item(oldIndex)["style"].backgroundColor = "";
        listEntries.item(index.current)["style"].backgroundColor = `var(--${hoverColor})`;
        listEntries.item(index.current).scrollIntoView({behavior: "instant", block: "nearest"});
    } else if (isEnter && index.current !== -1) {
        onSelect?.(listEntries.item(index.current));
    }
}
