import {KeyCode} from "@/DefaultObjects";
import * as React from "react";
import * as ReactDOM from "react-dom";
import {Icon} from "@/ui-components";
import * as Text from "@/ui-components/Text";
import Box from "./Box";
import {Dropdown, DropdownContent} from "./Dropdown";
import {PropsWithChildren, useCallback, useEffect, useMemo, useRef, useState} from "react";

export interface ClickableDropdownProps<T> {
    trigger: React.ReactNode;
    children?: any;
    options?: {text: string; value: T}[];

    keepOpenOnClick?: boolean;
    keepOpenOnOutsideClick?: boolean;
    onChange?: (value: T) => void;
    onTriggerClick?: () => void;
    onClose?: () => void;
    open?: boolean;

    fullWidth?: boolean;
    height?: string | number;
    width?: string | number;
    minWidth?: string;
    left?: string | number;
    top?: string | number;
    bottom?: string | number;
    right?: string | number;

    useMousePositioning?: boolean;
    paddingControlledByContent?: boolean;

    chevron?: boolean;
    overflow?: string;
    colorOnHover?: boolean;
    squareTop?: boolean;

    // NOTE(Dan): I am sorry for having to go imperative but this is needed to force close a mouse positioned
    // dropdown. Otherwise, this will cause issues for confirmation buttons (which require a hold action versus a
    // click).
    closeFnRef?: React.MutableRefObject<() => void>;
    openFnRef?: React.MutableRefObject<(left: number, top: number) => void>;
    onKeyDown?: (ev: KeyboardEvent) => void;
}

type ClickableDropdownType = <T>(props: PropsWithChildren<ClickableDropdownProps<T>>, context?: any) =>
    JSX.Element | null;

const dropdownPortal = "dropdown-portal";

const ClickableDropdown: ClickableDropdownType =
    ({keepOpenOnClick, onChange, onTriggerClick, ...props}) => {
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
            onTriggerClick?.();
            if (!isControlled) setOpen(true);
        }, [onTriggerClick]);

        const forceOpen = useCallback((left: number, top: number) => {
            setLocation([left, top]);
            doOpen()
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

        const handleEscPress: (ev: KeyboardEvent) => void = useCallback((event): void => {
            if (event.key === "Escape" && open) {
                close();
            } else {
                props.onKeyDown?.(event);
            }
        }, [open]);


        useEffect(() => {
            if (open) {
                document.addEventListener("mousedown", handleClickOutside);
                document.addEventListener("keydown", handleEscPress);
            } else {
                document.removeEventListener("mousedown", handleClickOutside);
                document.removeEventListener("keydown", handleEscPress);
            }
            return () => {
                document.removeEventListener("mousedown", handleClickOutside);
                document.removeEventListener("keydown", handleEscPress);
            };
        }, [handleClickOutside, handleEscPress, open]);

        let children: React.ReactNode[] = [];
        if (props.options !== undefined && onChange) {
            children = props.options.map((opt, i) => (
                <Box
                    cursor="pointer"
                    width="auto"
                    key={i}
                    onClick={() => onChange!(opt.value)}
                >
                    {opt.text}
                </Box>
            ));
        } else if (props.children) {
            children = props.children;
        }
        const emptyChildren = (React.Children.map(children, it => it) ?? []).length === 0;
        let width = props.fullWidth && !props.useMousePositioning ? "100%" : props.width;
        let top = !props.useMousePositioning ? props.top : location[1];

        let left = !props.useMousePositioning ? props.left : location[0];
        if (props.useMousePositioning) {
            if (width === undefined) width = 300;
            const widthAsNumber = parseInt(width.toString().replace("px", ""));
            const leftAsNumber = parseInt((left ?? 0).toString().replace("px", ""));
            left = leftAsNumber - widthAsNumber;
            if (left < 0) left = leftAsNumber;

            const topAsNumber = parseInt((top ?? 0).toString().replace("px", ""));
            const estimatedHeight = 38 * children.length;
            if (window.innerHeight - (topAsNumber + estimatedHeight) < 50) {
                top = topAsNumber - estimatedHeight;
            }
        }

        const dropdownContent = <DropdownContent
            overflow={"visible"}
            squareTop={props.squareTop}
            cursor="pointer"
            {...(props as any)}
            top={top}
            left={left}
            fixed={props.useMousePositioning}
            width={width}
            hover={false}
            visible={open}
            onClick={e => {
                e.stopPropagation();
                !keepOpenOnClick ? close() : null;
            }}
        >
            {children}
        </DropdownContent>;
        return (
            <Dropdown data-tag="dropdown" ref={dropdownRef} fullWidth={props.fullWidth}>
                <Text.TextSpan
                    cursor="pointer"
                    onClick={e => {
                        e.preventDefault();
                        e.stopPropagation();
                        toggle(e);
                    }}
                >
                    {props.trigger}{props.chevron ? <Icon name="chevronDownLight" size="1em" ml=".7em" color={"darkGray"} /> : null}
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

export default ClickableDropdown;
