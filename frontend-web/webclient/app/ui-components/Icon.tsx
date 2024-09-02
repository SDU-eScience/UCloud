import * as React from "react";
import {SpaceProps} from "styled-system";
import * as icons from "./icons";
import {HexColor, ThemeColor} from "./theme";
import {Cursor} from "./Types";
import {classConcat, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";

const IconBase = ({name, size, squared, color2, spin, hoverColor, ...props}: IconBaseProps): React.ReactNode => {
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
            height={squared ? size : undefined}
            color2={color2 ? getCssPropertyValue(color2) : undefined}
            {...props}
        />
    );
};

export interface IconBaseProps extends SpaceProps, React.SVGAttributes<HTMLDivElement> {
    name: IconName | "bug";
    color?: ThemeColor | HexColor;
    color2?: ThemeColor | HexColor;
    rotation?: number;
    cursor?: Cursor;
    size?: string | number;
    squared?: boolean;
    spin?: boolean;
    hoverColor?: ThemeColor | HexColor;
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
    
    ${k}.with-hover:hover {
        color: var(--hoverColor, var(--color)) !important;
    }
    
    ${k}[data-spin="true"] {
        animation: icon-spin 1s linear infinite;
    }
    
    @keyframes icon-spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
    }
`);

const Icon: React.FunctionComponent<IconBaseProps> = ({size = 18, squared = true, ...props}) => {
    const style: CSSProperties = unbox(props);
    if (props.color) style["--color"] = `var(--${props.color})`;
    if (props.hoverColor) style["--hoverColor"] = `var(--${props.hoverColor})`;
    if (props.rotation) style.transform = `rotate(${props.rotation}deg)`;
    style.cursor = props.cursor ?? "inherit";
    if (props.name === "fork") {
        style.fill = "none";
    }

    return <IconBase {...props} size={size} squared={squared} className={classConcat(IconClass, props.className, props.hoverColor ? "with-hover" : undefined)} data-spin={props.spin === true} style={style} />
};

Icon.displayName = "Icon";

// Use to see every available icon in debugging.
export const EveryIcon = (): React.ReactNode => (
    <>
        {Object.keys(icons).map((it: IconName, i: number) =>
            (<span title={it} key={i}><Icon name={it} key={i} color="iconColor" color2="iconColor2" /></span>)
        )}
    </>
);


// bug icon
const randomInt = (min: number, max: number): number => {
    return Math.floor(Math.random() * (max - min + 1)) + min;
};

function Bug({size, color2, spin, ...props}: Omit<IconBaseProps, "name">): React.ReactNode {
    const bugs: string[] = ["bug1", "bug2", "bug3", "bug4", "bug5", "bug6"];
    const [idx] = React.useState(randomInt(0, bugs.length - 1));

    const Component = icons[bugs[idx]];

    return (
        <Component width={size} height={size} color2={color2 ? `var(--${color2})` : undefined} {...props} />
    );
}

export type IconName = Exclude<keyof typeof icons, "bug1" | "bug2" | "bug3" | "bug4" | "bug5" | "bug6"> | "bug";

export default Icon;
