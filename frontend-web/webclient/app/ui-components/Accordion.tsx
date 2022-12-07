import * as React from "react";
import styled from "styled-components";
import {MarginProps, PaddingProps} from "styled-system";
import Box from "./Box";
import Icon, {IconName} from "./Icon";
import {ListRow} from "./List";
import {ThemeColor} from "./theme";

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
    return (
        <>
            <AccordionStyle active={isOpen} borderColor={props.borderColor} noBorder={props.noBorder ?? false} onClick={() => setOpen(!open)}>
                <ListRow
                    stopPropagation={false}
                    left={props.title}
                    leftSub={props.titleSub}
                    icon={
                        props.icon ? <Icon color2={props.iconColor2} color={color} name={props.icon} /> :
                           props.omitChevron ? null : <RotatingIcon color="text" size={15} mt="6px" name="chevronDown" rotation={open || props.forceOpen ? 0 : -90} />
                    }

                    right={<>{props.titleContent}{isOpen ? props.titleContentOnOpened : null}</>}
                />
            </AccordionStyle>
            <Panel active={isOpen} noBorder={props.noBorder ?? false}>
                <Box {...props.panelProps}>
                    {props.children}
                </Box>
            </Panel>
        </>
    );
}

/* FIXME(Jonas): `noBorder` is a workaround that should be handled purely by CSS. :last-child, for example. */
const AccordionStyle = styled.div <{active: boolean; noBorder: boolean; borderColor?: string;}>`
    background-color: var(--white);
    width: 100%;
    border: none;
    text-align: left;
    outline: none;
    font-size: 15px;
    cursor: pointer;
    border-bottom: solid ${p => p.borderColor ?? "lightGray"} 1px;
    ${p => p.noBorder && !p.active ? "border-bottom: solid lightGray 0px;" : null}
`;

/* FIXME(Jonas): `noBorder` is a workaround that should be handled purely by CSS. :last-child, for example. */
const Panel = styled.div<{active: boolean; noBorder: boolean;}>`
    display: ${props => props.active ? "block" : "hidden"};
    ${props => props.active && !props.noBorder ? "border-bottom: 1px solid lightGray;" : null}
    max-height: ${props => props.active ? "auto" : 0};
    overflow: hidden;
    transition: all 0.2s ease-out;
`;

const RotatingIcon = styled(Icon)`
    size: 14px;
    margin-right: 12px;
    cursor: pointer;
    transition: transform 0.2s;
`;
