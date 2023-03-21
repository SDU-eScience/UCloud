import * as React from "react";
import {SpaceProps, WidthProps} from "styled-system";
import Flex from "./Flex";
import Icon, {IconClass} from "./Icon";
import {injectStyle, unbox} from "@/Unstyled";

interface SelectProps extends SpaceProps, WidthProps, React.SelectHTMLAttributes<HTMLSelectElement> {
    error?: boolean;
    selectRef?: React.RefObject<HTMLSelectElement>;
}

const SelectClass = injectStyle("select", k => `
    ${k} {
        appearance: none;
        display: block;
        width: 100%;
        font-family: inherit;
        color: inherit;
        background-color: transparent;
        border-radius: 5px;
        border-width: 2px;
        border-style: solid;
        border-color: var(--borderGray, #f00);
        
        margin: 0;
        padding-left: 12px;
        padding-right: 32px;
        padding-top: 7px;
        padding-bottom: 7px;
    }
    
    ${k} > option {
        color: black;
    }

    ${k}:invalid, ${k}[data-error="true"] {
        border-color: var(--red, #f00);
    }

    ${k}:focus {
        outline: none;
        border-color: var(--blue, #f00);
    }
    
    ${k} + .${IconClass} {
        pointer-events: none;
        margin-left: -32px;
    }
`);

const Select: React.FunctionComponent<SelectProps> = props => {
    return <Flex width={1} alignItems="center" style={unbox(props)}>
        <select className={SelectClass} {...props} ref={props.selectRef}/>
        <Icon name="chevronDown" color="gray" size="0.7em"/>
    </Flex>;
};

export default Select;
