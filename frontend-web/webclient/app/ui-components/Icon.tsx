import * as React from "react";
import {SpaceProps} from "styled-system";
import Bug from "./Bug";
import * as icons from "./icons";
import theme, {ThemeColor} from "./theme";
import {Cursor} from "./Types";
import {injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";

const IconBase = ({name, size, squared, color2, spin, hoverColor, ...props}: IconBaseProps): JSX.Element => {
    let Component = icons[name];
    if (!Component) {
        if (name === "bug") {
            Component = Bug;
        } else {
            return (<></>);
        }
    }

    return (
        <Component
            data-component={`icon-${name}`}
            width={size}
            height={squared ? size : undefined }
            color2={color2 ? theme.colors[color2] : undefined}
            {...props}
        />
    );
};

export interface IconBaseProps extends SpaceProps, React.SVGAttributes<HTMLDivElement> {
    name: IconName | "bug";
    color?: string;
    color2?: string;
    rotation?: number;
    cursor?: Cursor;
    size?: string | number;
    squared?: boolean;
    spin?: boolean;
    hoverColor?: ThemeColor;
    title?: string;
    className?: string;
    style?: CSSProperties;
}

export const IconClass = injectStyle("icon", k => `
    ${k} {
        flex: none;
        vertical-align: middle;
        transition: transform .2s ease-in-out; 
    }
    
    ${k}:hover {
        color: var(--hoverColor);
    }
    
    ${k}[data-spin="true"] {
        animation: icon-spin 1s linear infinite;
    }
    
    @keyframes icon-spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
    }
`);

const Icon: React.FunctionComponent<IconBaseProps> = props => {
    const style: CSSProperties = unbox(props);
    if (props.hoverColor) style["--hoverColor"] = `var(--${props.hoverColor})`;
    else style["--hoverColor"] = "inherit";
    if (props.rotation) style.transform = `rotate(${props.rotation}deg)`;
    style.cursor = props.cursor ?? "inherit";

    return <IconBase {...props} className={IconClass} data-spin={props.spin === true} style={style} />
};

Icon.displayName = "Icon";

Icon.defaultProps = {
    size: 24,
    squared: true
};

// Use to see every available icon in debugging.
export const EveryIcon = (): JSX.Element => (
    <>
        {Object.keys(icons).map((it: IconName, i: number) =>
            (<span key={i}><span>{it}</span>: <Icon name={it} key={i} color="iconColor" color2="iconColor2" /></span>)
        )}
    </>
);

export type IconName = Exclude<keyof typeof icons, "bug1" | "bug2" | "bug3" | "bug4" | "bug5" | "bug6"> | "bug";

export default Icon;
