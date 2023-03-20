import * as CSS from "csstype";
import * as React from "react";
import styled from "styled-components";
import {color, ResponsiveValue, space, SpaceProps, style} from "styled-system";
import Bug from "./Bug";
import * as icons from "./icons";
import theme, {Theme} from "./theme";
import {Cursor} from "./Types";

const IconBase = ({name, size, squared, theme, color2, spin, hoverColor, ...props}: IconBaseProps): JSX.Element => {
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

const hoverColor = style({
    prop: "hoverColor",
    cssProperty: "color",
    key: "colors",
});

export interface IconBaseProps extends SpaceProps, React.SVGAttributes<HTMLDivElement> {
    name: IconName | "bug";
    color?: string;
    color2?: string;
    rotation?: number;
    theme: Theme;
    cursor?: Cursor;
    size?: string | number;
    squared?: boolean;
    spin?: boolean;
    hoverColor?: ResponsiveValue<CSS.Property.Color>;
    title?: string;
}

const spin = (props: {spin?: boolean}): string | null => props.spin ? `
  animation: spin 1s linear infinite;
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
` : null;

const Icon = styled(IconBase) <IconBaseProps>`
    flex: none;
    vertical-align: middle;
    cursor: ${props => props.cursor};
    ${props => props.rotation ? `transform: rotate(${props.rotation}deg);` : ""}
    ${space} ${color};
    ${spin};

    transition: transform .2s ease-in-out; 

    &:hover {
        ${hoverColor};
    }
`;

Icon.displayName = "Icon";

Icon.defaultProps = {
    theme,
    cursor: "inherit",
    name: "notification",
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
