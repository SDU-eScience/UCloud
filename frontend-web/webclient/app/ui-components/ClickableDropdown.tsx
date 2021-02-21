import {KeyCode} from "DefaultObjects";
import * as React from "react";
import {Icon} from "ui-components";
import * as Text from "ui-components/Text";
import Box from "./Box";
import {Dropdown, DropdownContent} from "./Dropdown";
import {PropsWithChildren, useCallback, useEffect, useMemo, useRef, useState} from "react";

export interface ClickableDropdownProps<T> {
    trigger: React.ReactNode;
    children?: any;
    options?: { text: string; value: T }[];

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

    chevron?: boolean;
    overflow?: string;
    colorOnHover?: boolean;
    squareTop?: boolean;
}

type ClickableDropdownType = <T>(props: PropsWithChildren<ClickableDropdownProps<T>>, context?: any) =>
    JSX.Element | null;

const ClickableDropdown: ClickableDropdownType =
    ({keepOpenOnClick, onChange, onTriggerClick, ...props}) => {
        const dropdownRef = useRef<HTMLDivElement>(null);
        const [open, setOpen] = useState(props.open ?? false);
        const isControlled = useMemo(() => props.open !== undefined, []);

        useEffect(() => {
            if (isControlled && props.open !== undefined) {
                setOpen(props.open);
            }
        }, [props.open]);


        const close = useCallback(() => {
            if (isControlled && props.onClose) props.onClose();
            else if (!isControlled) setOpen(false);
        }, [props.onClose]);

        const doOpen = useCallback(() => {
            onTriggerClick?.();
            if (!isControlled) setOpen(true);
        }, [onTriggerClick]);

        const toggle = useCallback(() => {
            if (open) close();
            else doOpen();
        }, [open]);

        let neither = true;
        if (props.children) neither = false;
        if (!!onChange && !!props.options) neither = false;
        if (neither) throw Error("Clickable dropdown must have either children prop or options and onChange");

        // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
        const handleClickOutside = useCallback(event => {
            if (props.keepOpenOnOutsideClick) return;
            if (dropdownRef.current && !dropdownRef.current.contains(event.target) && open) {
                close();
            }
        }, [props.keepOpenOnOutsideClick, open]);

        const handleEscPress = useCallback((event: { keyCode: KeyCode }): void => {
            if (event.keyCode === KeyCode.ESC && open) {
                close();
            }
        }, [open]);


        useEffect(() => {
            document.addEventListener("mousedown", handleClickOutside);
            document.addEventListener("keydown", handleEscPress);
            return () => {
                document.removeEventListener("mousedown", handleClickOutside);
                document.removeEventListener("keydown", handleEscPress);
            };
        }, [handleClickOutside, handleEscPress]);

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
        const width = props.fullWidth ? "100%" : props.width;
        return (
            <Dropdown data-tag="dropdown" ref={dropdownRef} fullWidth={props.fullWidth}>
                <Text.TextSpan
                    cursor="pointer"
                    onClick={e => {
                        e.preventDefault();
                        e.stopPropagation();
                        toggle();
                    }}
                >
                    {props.trigger}{props.chevron ? <Icon name="chevronDown" size=".7em" ml=".7em"/> : null}
                </Text.TextSpan>
                {emptyChildren || !open ? null : (
                    <DropdownContent
                        overflow={"visible"}
                        squareTop={props.squareTop}
                        cursor="pointer"
                        {...props}
                        width={width}
                        hover={false}
                        visible={open}
                        onClick={e => {
                            e.stopPropagation();
                            !keepOpenOnClick ? close() : null;
                        }}
                    >
                        {children}
                    </DropdownContent>
                )}
            </Dropdown>
        );

    }

export default ClickableDropdown;
