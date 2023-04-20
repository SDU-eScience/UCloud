import {
    BorderRadiusProps,
    FontSizeProps,
    SpaceProps, TextAlignProps,
    WidthProps
} from "styled-system";
import {ThemeColor} from "./theme";
import {classConcat, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import * as React from "react";
import {Cursor} from "./Types";

export interface InputProps extends SpaceProps, FontSizeProps, BorderRadiusProps, React.InputHTMLAttributes<HTMLInputElement> {
    leftLabel?: boolean;
    rightLabel?: boolean;
    id?: string;
    noBorder?: boolean;
    error?: boolean;
    overrideDisabledColor?: ThemeColor;
    rows?: number;
    inputRef?: React.RefObject<HTMLInputElement>;
    resize?: "vertical" | "horizontal";
}

export const InputClass = injectStyle("input", k => `
    ${k} {
        display: block;
        font-family: inherit;
        color: var(--black);
        /* TODO(Jonas): use var */
        background: #E9E9E9;
        margin: 0;
        border-width: 0px;
        
        width: 100%;
        border-radius: 5px;
        padding: 7px 12px;
        height: 42px;
        
        --inputBorder: var(--blue);
        --inputDisabledBorder: var(--lightGray);
    }
    
    ${k}:focus {
        outline: 0;
        border-color: var(--inputBorder);
    }
    
    ${k}[data-error="true"], ${k}:invalid:not(:placeholder-shown) {
        border-color: var(--red, #f00);
    }
    
    ${k}::placeholder {
        color: var(--black, #f00);
    }
    
    ${k}:disabled {
        background: var(--inputDisabledColor);
    }
    
    ${k}[data-hidden="true"] {
        display: none;
    }
    
    ${k}[data-right-label="true"] {
        border-top-right-radius: 0;
        border-bottom-right-radius: 0;
    }
    
    ${k}[data-left-label="true"] {
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
    }
    
    ${k}[data-no-border="true"] {
        border-width: 0;
    }
`);

const Input: React.FunctionComponent<InputProps & {as?: "input" | "textarea"; cursor?: Cursor}> = props => {
    const style = unbox(props);
    if (props.overrideDisabledColor) style["--inputDisabledColor"] = `var(--${props.overrideDisabledColor})`;
    const evHandlers = extractEventHandlers(props);

    const inputProps = {...evHandlers};
    inputProps["id"] = props.id;
    inputProps["type"] = props.type;
    inputProps["accept"] = props.accept;
    inputProps["disabled"] = props.disabled;
    inputProps["autoComplete"] = props.autoComplete;
    inputProps["autoFocus"] = props.autoFocus;
    inputProps["required"] = props.required;
    inputProps["readOnly"] = props.readOnly;
    inputProps["pattern"] = props.pattern;
    inputProps["name"] = props.name;
    inputProps["placeholder"] = props.placeholder;
    inputProps["defaultValue"] = props.defaultValue;

    inputProps["data-error"] = props.error === true;
    inputProps["data-left-label"] = props.leftLabel === true;
    inputProps["data-right-label"] = props.rightLabel === true;
    inputProps["data-no-border"] = props.noBorder === true;
    inputProps["data-hidden"] = props.hidden === true;

    inputProps["ref"] = props.inputRef;

    inputProps["className"] = classConcat(InputClass, props.className);
    inputProps["style"] = style;

    inputProps["rows"] = props.rows;

    if (props.as !== "textarea") {
        return <input {...inputProps} />;
    } else {
        style.height = "unset";
        style.resize = props.resize;
        return <textarea {...inputProps} />;
    }
}

Input.displayName = "Input";

export const HiddenInputField: React.FunctionComponent<InputProps> = props => {
    return <Input {...props} hidden={true} />;
};

export default Input;

export interface InputLabelProps extends WidthProps, TextAlignProps {
    leftLabel?: boolean;
    rightLabel?: boolean;
    independent?: boolean;
}

const InputLabelClass = injectStyle("input-label", k => `
    ${k} {
        padding-left: 1em;
        padding-right: 1em;
        padding-top: 6px;
        height: 42px;
        border-radius: 5px;
    }

    
    ${k}[data-left-label="true"] {
        border-top-left-radius: 5px;
        border-bottom-left-radius: 5px;
        border-right: 0;
        margin-right: 0;
    }
    
    ${k}[data-right-label="true"] {
        border-top-right-radius: 5px;
        border-bottom-right-radius: 5px;
        border-left: 0;
        margin-left: 0;
    }
`);

export const InputLabel: React.FunctionComponent<InputLabelProps & {children?: React.ReactNode}> = props => {
    return <div style={unbox(props)} className={InputLabelClass}>{props.children}</div>;
}
