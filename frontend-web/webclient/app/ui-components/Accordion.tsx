import * as React from "react";
import {MarginProps, PaddingProps} from "styled-system";
import Box from "./Box";
import Icon, {IconName} from "./Icon";
import {ListRow} from "./List";
import {ThemeColor} from "./theme";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";

/* https://www.w3schools.com/howto/tryit.asp?filename=tryhow_js_accordion_symbol */
export function Accordion(props: React.PropsWithChildren<{
    icon?: IconName;
    iconColor?: ThemeColor;
    iconColor2?: ThemeColor;
    title: React.ReactNode;
    titleSub?: React.ReactNode;
    titleContent?: React.ReactNode;
    titleContentOnOpened?: React.ReactNode;
    forceOpen?: boolean;
    noBorder?: boolean;
    omitChevron?: boolean;
    borderColor?: string;
    panelProps?: MarginProps & PaddingProps;
}>): JSX.Element {
    const color = props.iconColor ?? "text";
    const [open, setOpen] = React.useState(false);
    const isOpen = props.forceOpen || open;
    const style: CSSProperties = {};
    if (props.borderColor) style["--separatorColor"] = `var(--${props.borderColor})`;
    return (
        <>
            <div className={AccordionStyleClass} style={style} data-active={isOpen} data-no-order={props.noBorder ?? false} onClick={() => setOpen(!open)}>
                <ListRow
                    stopPropagation={false}
                    left={props.title}
                    leftSub={props.titleSub}
                    icon={
                        props.icon ? <Icon color2={props.iconColor2} color={color} name={props.icon} /> :
                           props.omitChevron ? null : <Icon data-chevron={"true"} color="text" size={15} mt="6px" name="chevronDown" rotation={open || props.forceOpen ? 0 : -90} />
                    }

                    right={<>{props.titleContent}{isOpen ? props.titleContentOnOpened : null}</>}
                />
            </div>
            <div className={PanelClass} data-active={isOpen} data-no-border={props.noBorder ?? false}>
                <Box {...props.panelProps}>
                    {props.children}
                </Box>
            </div>
        </>
    );
}

const AccordionStyleClass = injectStyle("accordion", k => `
    ${k} {
        --separatorColor: var(--lightGray);
        background-color: var(--white);
        width: 100%;
        border: none;
        text-align: left;
        outline: none;
        font-size: 15px;
        cursor: pointer;
        border-bottom: 1px solid var(--separatorColor);
    }
    
    ${k}[data-no-border="true"] {
        border-bottom: 0;
    }
    
    ${k} [data-chevron="true"] {
        margin-right: 12px;
        cursor: pointer;
        transition: transform 0.2s;
    }
`);

const PanelClass = injectStyle("accordion-panel", k => `
    ${k} {
        display: block;
        border-bottom: 1px solid var(--lightGray);
        overflow: hidden;
        transition: all 0.2s ease-out;
    }
    
    ${k}[data-active="false"] {
        display: none;
        max-height: 0;
    }
    
    ${k}[data-no-border="true"] {
        border: 0;
    }
`);


/* FIXME(Jonas): `noBorder` is a workaround that should be handled purely by CSS. :last-child, for example. */
