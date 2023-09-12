import * as React from "react";
import Icon, {IconName} from "./Icon";
import {Box} from "@/ui-components";
import {FontSizeProps} from "styled-system";
import {extractSize, injectStyle} from "@/Unstyled";
import {BoxProps} from "@/ui-components/Box";
import {LabelClass} from "./Label";

export const RadioTilesContainerClass = injectStyle("radio-tiles-container", k => `
    ${k} {
        align-items: center;
        display: inline-grid;
        grid-auto-flow: column;
        grid-template-columns: repeat(auto);
        column-gap: 5px;
    }
`);

const RadioTilesContainer: React.FunctionComponent<BoxProps & {children?: React.ReactNode}> = props => {
    return <Box {...props} className={RadioTilesContainerClass} />;
}

const RadioTile = (props: RadioTileProps): JSX.Element => {
    const {height, label, icon, checked, disabled, fontSize, onChange, name} = props;

    return (
        <div
            className={RadioTileClass}
            style={{height: extractSize(height), width: extractSize(height), fontSize: extractSize(fontSize)}}
            data-checked={checked}
            data-disabled={disabled}
        >
            <input
                type="radio"
                name={name}
                id={label}
                value={label}
                checked={checked}
                disabled={disabled}
                onChange={onChange}
            />

            <div className={RadioTileIconClass}>
                <Icon name={icon} size={props.labeled ? "65%" : "85%"} />
                <label htmlFor={label}>
                    {props.labeled ? label : undefined}
                </label>
            </div>
        </div>
    );
};

export function tileAsHTML(props: Exclude<RadioTileProps, "onChange" | "icon"> & {onChange: (e: Event) => void; icon: HTMLDivElement}) {
    const wrapper = document.createElement("div");
    wrapper.className = RadioTileClass;
    wrapper.style.height = extractSize(props.height);
    wrapper.style.width = extractSize(props.height);
    wrapper.style.fontSize = extractSize(props.fontSize);
    wrapper.setAttribute("data-checked", props.checked.toString());
    wrapper.setAttribute("data-disabled", props.disabled?.toString() ?? "false");

    const input = document.createElement("input");
    input.type = "radio";
    input.name = props.name;
    input.id = props.label;
    input.value = props.label;
    input.checked = props.checked;
    if (props.disabled != null) input.disabled = props.disabled;
    input.onchange = props.onChange;
    
    const innerWrapper = document.createElement("div");
    innerWrapper.className = RadioTileIconClass;
    
    wrapper.append(input, innerWrapper);

    const label = document.createElement("label");
    label.className = LabelClass;
    if (props.labeled) label.innerText = props.label;
    
    innerWrapper.append(props.icon, label);

    return wrapper;
}

interface RadioTileProps extends RadioTileWrapProps, FontSizeProps {
    label: string;
    icon: IconName;
    name: string;
    labeled?: boolean;
    onChange: (value: React.ChangeEvent<HTMLInputElement>) => void;
}

interface RadioTileWrapProps {
    height: number;
    checked: boolean;
    disabled?: boolean;
}


RadioTile.defaultProps = {
    labeled: true
};

const RadioTileIconClass = injectStyle("radio-tile-icon", () => ``);

const RadioTileClass = injectStyle("radio-tile", k => `
    ${k} {
        position: relative;
    }
    
    ${k}:hover {
        transform: translateY(-2px);
    }
    
    ${k}[data-checked="true"]:hover, ${k}[data-disabled="true"] {
        transform: unset;
    }
    
    ${k}[data-checked="true"]:hover > .${RadioTileIconClass}, ${k}[data-disabled="true"] > .${RadioTileIconClass} {
        color: white;
        border: 0;
    }
    
    ${k}:hover > .${RadioTileIconClass} {
        color: var(--blue, #f00); 
        border: 1px solid var(--blue, #f00);
    }
    
    ${k} input {
        opacity: 0;
        position: absolute;
        top: 0;
        left: 0;
        height: 100%;
        width: 100%;
        margin: 0;
        cursor: pointer;
    }
    
    ${k} input:checked + .${RadioTileIconClass} {
        background-color: var(--blue, #f00);
        border: 0px solid white;
        color: white;
    }   
    
    ${k} label {
        text-align: center;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 1px;
        line-height: 1;
        padding-top: 0.1rem;   
    }
    
    ${k} .${RadioTileIconClass} {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        border-radius: 5px;
        border: 1px solid var(--invertedThemeColor);
        color: var(--invertedThemeColor, #f00);
        transition: all 300ms ease;
    }
`);

export {RadioTilesContainer, RadioTile};
