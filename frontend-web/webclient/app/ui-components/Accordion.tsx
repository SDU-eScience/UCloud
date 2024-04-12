import * as React from "react";
import {MarginProps, PaddingProps} from "styled-system";
import Box from "./Box";
import Icon, {IconName} from "./Icon";
import {ListRow} from "./List";
import {ThemeColor} from "./theme";
import {classConcat, injectStyle} from "@/Unstyled";
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
    className?: string;
    style?: Partial<CSSProperties>;
}>): JSX.Element {
    const color = props.iconColor ?? "textPrimary";
    const [open, setOpen] = React.useState(false);
    const isOpen = props.forceOpen || open;
    const style: CSSProperties = {...(props.style ?? {})};
    if (props.borderColor) style["--separatorColor"] = `var(--${props.borderColor})`;
    return (
        <>
            <div className={classConcat(AccordionStyleClass, props.className)} style={style} data-active={isOpen} data-no-order={props.noBorder ?? false} onClick={() => setOpen(!open)}>
                <ListRow
                    stopPropagation={false}
                    left={props.title}
                    leftSub={props.titleSub}
                    icon={
                        props.icon ? <Icon color2={props.iconColor2} color={color} name={props.icon} /> :
                           props.omitChevron ? null : <Icon data-chevron={"true"} color="textPrimary" size={15} name="chevronDownLight" rotation={open || props.forceOpen ? 0 : -90} />
                    }

                    right={<>{props.titleContent}{isOpen ? props.titleContentOnOpened : null}</>}
                />
            </div>
            {props.children !== undefined &&
                <div className={PanelClass} data-active={isOpen} data-no-border={props.noBorder ?? false}>
                    <Box {...props.panelProps}>
                        {props.children}
                    </Box>
                </div>
            }
        </>
    );
}

const AccordionStyleClass = injectStyle("accordion", k => `
    ${k} {
        --separatorColor: var(--borderColor);
        background-color: var(--backgroundDefault);
        width: 100%;
        border: none;
        text-align: left;
        outline: none;
        font-size: 15px;
        cursor: pointer;
        border-bottom: 1px solid var(--borderColor);
        user-select: none;
        -webkit-user-select: none;
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
        border-bottom: 1px solid var(--borderColor);
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

export default Accordion;
