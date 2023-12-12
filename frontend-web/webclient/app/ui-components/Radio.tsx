import * as React from "react";
import Icon from "./Icon";
import {injectStyle} from "@/Unstyled";

const Radio = (props: RadioWrapProps & {onChange: (e: React.ChangeEvent<HTMLInputElement>) => void}): JSX.Element => {
    const {checked} = props;

    const radioIconName = checked ? "radioChecked" : "radioEmpty";

    return (
        <div className={RadioClass} data-checked={checked}>
            <input type="radio" {...props} />
            <Icon name={radioIconName} size={24} mr={".5em"} />
        </div>
    );
};

interface RadioWrapProps {
    checked: boolean;
    disabled?: boolean;
}

const RadioClass = injectStyle("radio", k => `
    ${k} {
        display: inline-block;
        color: var(--borderGray);
    }
    
    ${k}[data-checked="true"]:hover {
        color: var(--primary);
    }
    
    ${k} input {
        appearance: none;
        opacity: 0;
        position: absolute;
        z-index: 0;
    }
    
    ${k} input:focus {
        box-shadow: none;
    }
    
    ${k} input:checked ~ svg {
        color: var(--primary);
    }
    
    ${k} input:disabled ~ svg {
        color: var(--borderGray);
    }
    
    ${k} svg {
        vertical-align: middle;
    }
`);

export default Radio;
