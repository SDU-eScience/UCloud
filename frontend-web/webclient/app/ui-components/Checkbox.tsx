import * as React from "react";
import {Icon} from ".";
import {injectStyle} from "@/Unstyled";

interface CheckboxProps extends React.InputHTMLAttributes<HTMLInputElement> {
    disabled?: boolean;
    handleWrapperClick?: () => void;
}

function Checkbox(props: CheckboxProps): JSX.Element {
    const {disabled, size} = props;
    const checkboxProps = {...props};
    delete checkboxProps["handleWrapperClick"];
    return (
        <div className={CheckboxClass} data-disabled={!!disabled} onClick={props.handleWrapperClick}>
            <input type="checkbox" {...checkboxProps} />
            <Icon name="boxChecked" size={size} data-name="checked" />
            <Icon name="boxEmpty" size={size} data-name="empty" />
        </div>
    );
}

const CheckboxClass = injectStyle("checkbox", k => `
    ${k} {
        display: inline-block;
        position: relative;
        vertical-align: middle;
        cursor: pointer;
        color: var(--gray);
        margin-right: .5em;
    }
    
    ${k}[data-disabled="true"], ${k}[data-disabled="true"] > input:checked ~ svg[data-name="checked"] {
        color: var(--borderGray);
    }
    
    ${k} svg[data-name="checked"] {
        display: none;
    }
    
    ${k} > input:checked ~ svg[data-name="checked"] {
        display: inline-block;
        color: var(--blue);
    }
    
    ${k} > input:checked ~ svg[data-name="empty"] {
        display: none;
    }
    
    ${k} > input {
        appearance: none;
        opacity: 0;
        position: absolute;
    }
`);

Checkbox.displayName = "Checkbox";

Checkbox.defaultProps = {
    size: 20,
    disabled: false
};

export default Checkbox;
