import * as React from 'react'
import styled from 'styled-components'
import { space, color, SpaceProps, ColorProps } from "styled-system"
import * as icons from './icons/index'
import theme from './theme'


const IconBase = ({ name, size, theme, color, color2, ...props }): JSX.Element => {
  const Component = icons[name]
  if (!Component) return (<></>);
  return <Component width={size} height={size} color2={theme.colors[color2]} {...props} />
}

export interface IconProps extends SpaceProps, ColorProps
{
  name: IconName
  color2?: string
  rotation?: number
  cursor?: string
}

const Icon = styled(IconBase) <IconProps>`
  flex: none;
  vertical-align: middle;
  cursor: ${props => props.cursor};
  ${props => props.rotation ? `transform: rotate(${props.rotation}deg);` : ""}
  ${space} ${color};
`;

Icon.displayName = "Icon"

Icon.defaultProps = {
  theme,
  cursor: "auto",
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
