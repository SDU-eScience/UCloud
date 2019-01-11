import * as React from 'react'
import styled from 'styled-components'
import { style, space, color, SpaceProps, ColorProps, ResponsiveValue } from "styled-system"
import * as icons from './icons/index';
import theme from './theme'
import * as CSS from "csstype";


const IconBase = ({ name, size, theme, color, color2, spin, ...props }): JSX.Element => {
  const Component = icons[name];
  if (!Component) return (<></>);
  return <Component width={size} height={size} color2={theme.colors[color2]} {...props} />
}

const hoverColor = style({
  prop: 'hoverColor',
  cssProperty: 'color',
  key: 'colors'
})
export interface IconProps extends SpaceProps, ColorProps {
  name: IconName
  color2?: CSS.ColorProperty
  rotation?: number
  cursor?: string
  spin?: boolean
  hoverColor?: ResponsiveValue<CSS.ColorProperty>
}

const spin = (props: { spin?: boolean }) => props.spin ? `
  -webkit-animation: spin 1s linear infinite; /* Safari */
  animation: spin 1s linear infinite;
  @-webkit-keyframes spin {
    0% { -webkit-transform: rotate(0deg); }
    100% { -webkit-transform: rotate(360deg); }
  }
  
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
` : null;

const Icon = styled(IconBase) <IconProps>`
  flex: none;
  vertical-align: middle;
  cursor: ${props => props.cursor};
  ${props => props.rotation ? `transform: rotate(${props.rotation}deg);` : ""}
  ${space} ${color};
  ${spin};
  
  &:hover {
    ${hoverColor};
  }

`;

Icon.displayName = "Icon"

// FIXME: Workaround, not a fix.
// @ts-ignore
Icon.defaultProps = {
  theme,
  cursor: "inherit",
  name: "notification",
  size: 24
}

// Use to see every available icon.
export const EveryIcon = () => (
  <>
    {Object.keys(icons).map((it: IconName, i: number) =>
      (<span key={i}><span>{it}</span>: <Icon name={it} key={i} />, </span>)
    )}
  </>
);

export type IconName = keyof typeof icons;

export default Icon
