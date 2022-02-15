import * as React from "react";
import styled from "styled-components";
import Box from "./Box";
import Icon, {IconName} from "./Icon";
import {Spacer} from "./Spacer";

/* https://www.w3schools.com/howto/tryit.asp?filename=tryhow_js_accordion_symbol */
export function Accordion(props: React.PropsWithChildren<{icon?: IconName, title: string, titleContent?: React.ReactNode}>): JSX.Element {
    const [open, setOpen] = React.useState(false);
    return (
        <div>
            <AccordionStyle active={open} onClick={() => setOpen(!open)}>
                <Spacer
                    left={<>
                        {props.icon ? <Icon mr="12px" name={props.icon} /> :
                            <RotatingIcon size={15} mt="6px" name="chevronDown" rotation={open ? 0 : -90} />
                        }
                        {props.title}
                    </>}
                    right={<Box width="150px">{props.titleContent}</Box>}
                />
            </AccordionStyle>
            <Panel active={open}>
                {props.children}
            </Panel>
        </div>
    );
}

const AccordionStyle = styled.button<{active: boolean}>`
    background-color: var(--white);
    padding: 18px;
    width: 100%;
    border: none;
    text-align: left;
    outline: none;
    font-size: 15px;
    transition: 0.4s;
    cursor: pointer;
`;

const Panel = styled.div<{active: boolean}>`
    display: ${props => props.active ? "block" : "none"};
    max-height: ${props => props.active ? "auto" : 0};
    overflow: hidden;
    transition: max-height 0.2s ease-out;  
    transition: padding 0.2s ease-out;  
    padding: ${props => props.active ? "15px" : 0} 18px;
    ${p => p.active ? "border-bottom: 1px solid #ddd;" : null}
`;

export const AccordionWrapper = styled.div`
    box-shadow: ${p => p.theme.shadows.md};

    & > div > ${AccordionStyle} {
        border-bottom: 1px solid #ddd;
    }
    & > div > ${AccordionStyle}:last-child {
        border-bottom: 0px solid #ddd;
    }
`;

const RotatingIcon = styled(Icon)`
    size: 14px;
    margin-right: 12px;
    cursor: pointer;
    color: var(--black, #f00);
    transition: transform 0.2s;
`;
