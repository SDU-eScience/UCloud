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
        font-family: inherit;
        color: var(--black);
        background-color: var(--inputColor);
        margin: 0;
        border-width: 0px;
        width: 100%;
        border-radius: 5px;
        height: 42px;
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);         
        border: 1px solid var(--midGray);
        padding-left: 12px;
        padding-right: 32px;
        padding-top: 7px;
        padding-bottom: 7px;
    }
    
    ${k}:disabled:hover {
        border: 1px solid var(--midGray);
    }
    
    ${k}:hover {
        border-color: var(--gray);
    }
    
    ${k}::placeholder {
        color: var(--gray);
    }
    
    ${k} > option {
        color: black;
    }

    ${k}[data-error="true"], ${k}:invalid:not(:placeholder-shown) {
        border-color: var(--red, #f00);
    }

    ${k}:focus {
        outline: 0;
        border-color: var(--blue);
        box-shadow: 0 0 3px -1px var(--blue);
    }

    ${k}:disabled {
        background: var(--lightGray);
        opacity: 1;
        color: var(--black);
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
