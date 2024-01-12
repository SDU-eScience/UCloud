import * as React from "react";
import Icon, {IconName} from "./Icon";
import {Box} from "@/ui-components";
import {FontSizeProps} from "styled-system";
import {extractSize, injectStyle} from "@/Unstyled";
import {BoxProps} from "@/ui-components/Box";

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
                <Icon name={icon} color2={checked ? "textSecondary" : "primaryContrastAlt"} size={props.labeled ? "65%" : "85%"} />
                <label htmlFor={label}>
                    {props.labeled ? label : undefined}
                </label>
            </div>
        </div>
    );
};

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
    
    ${k}[data-disabled="false"]:hover > .${RadioTileIconClass} {
        color: var(--primaryMain, #f00); 
        border: 1px solid var(--primaryMain, #f00);
    }

    ${k}[data-checked="true"]:hover > .${RadioTileIconClass}, ${k}[data-disabled="true"] > .${RadioTileIconClass} {
        color: var(--textPrimary);
        border: 0;
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

    ${k}[data-disabled="true"] input {
        cursor: default;
    }
    
    ${k} input:checked + .${RadioTileIconClass} {
        background-color: var(--primaryMain, #f00);
        border: 0px solid var(--borderColor);
        color: var(--primaryContrast);
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
        border: 1px solid var(--borderColor);
        color: var(--textPrimary, #f00);
        transition: all 300ms ease;
    }
`);

export {RadioTilesContainer, RadioTile};
