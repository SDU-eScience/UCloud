import {
    BackgroundColorProps,
    BorderProps,
    BorderRadiusProps,
    FontSizeProps,
    MarginProps,
    SpaceProps, TextAlignProps,
    WidthProps
} from "styled-system";
import {ThemeColor} from "./theme";
import {classConcat, extractEventHandlers, injectStyle, unbox, unboxDataTags} from "@/Unstyled";
import * as React from "react";
import {Cursor} from "./Types";

export interface InputProps extends BorderProps, BackgroundColorProps, SpaceProps, FontSizeProps, BorderRadiusProps, React.InputHTMLAttributes<HTMLInputElement> {
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
        color: var(--textPrimary);
        background-color: var(--backgroundDefault);
        margin: 0;
        border-width: 0px;
        
        width: 100%;
        border-radius: 5px;
        padding: 7px 12px;
        height: 35px;
        border: 1px solid var(--borderColor);
    }
    
    ${k}:disabled:hover {
        border: 1px solid var(--borderColor);
    }
    
    ${k}:hover {
        border: 1px solid var(--borderColorHover);
    }

    ${k}:focus {
        outline: 0;
        border-color: var(--primaryMain);
    }

    ${k}::placeholder {
        color: var(--textSecondary);
    }
    
    ${k}[data-error="true"], ${k}:invalid:not(:placeholder-shown) {
        border-color: var(--errorMain, #f00);
    }
    
    ${k}::placeholder {
        color: var(--textPrimary, #f00);
    }
    
    ${k}:disabled {
        background: var(--backgroundDisabled);
        color: var(--textDisabled);
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
    let style = unbox(props);
    if (props.style) style = {...style, ...props.style};
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
    inputProps["value"] = props.value;
    inputProps["step"] = props.step;
    inputProps["min"] = props.min;
    inputProps["max"] = props.max;

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
        return <input {...inputProps} {...unboxDataTags(props as Record<string, string>)} />;
    } else {
        style.height = "unset";
        style.resize = props.resize;
        return <textarea {...inputProps} {...unboxDataTags(props as Record<string, string>)} />;
    }
}

Input.displayName = "Input";

export const HiddenInputField: React.FunctionComponent<InputProps> = props => {
    return <Input {...props} hidden={true} />;
};

export default Input;

export interface InputLabelProps extends WidthProps, TextAlignProps, MarginProps {
    leftLabel?: boolean;
    rightLabel?: boolean;
    independent?: boolean;
}

const InputLabelClass = injectStyle("input-label", k => `
    ${k} {
        height: 35px;
        border-radius: 5px;
        padding: 7px 12px;
        font-size: 14px;
        background-color: var(--backgroundDefault);
        border: 1px solid var(--borderColor);
    }
    
    ${k}[data-left-label="true"] {
        border-top-left-radius: 5px;
        border-bottom-left-radius: 5px;
        border-top-right-radius: 0;
        border-bottom-right-radius: 0;
        border-right: 0;
        margin-right: 0;
    }
    
    ${k}[data-right-label="true"] {
        border-top-right-radius: 5px;
        border-bottom-right-radius: 5px;
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
        border-left: 0;
        margin-left: 0;
    }
`);

export const InputLabel: React.FunctionComponent<InputLabelProps & {children?: React.ReactNode}> = props => {
    return <div style={unbox(props)} data-right-label={props.rightLabel} data-left-label={props.leftLabel} className={InputLabelClass}>{props.children}</div>;
}
