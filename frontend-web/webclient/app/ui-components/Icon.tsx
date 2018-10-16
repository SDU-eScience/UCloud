import * as React from 'react'
import styled from 'styled-components'
import { space, color, cleanElement } from 'styled-system'
import * as icons from './icons.json';
import theme from './theme'

const getPath = ({ name }) => {
  return icons[name]
}

// Remove `space` props from the `svg` element prevents react warnings
const CleanSvg = cleanElement('svg')

const Base = ({ name, size, legacy, ...props }): JSX.Element => {
  const icon = getPath({ name })
  if (!icon) <></>

  return (
    <CleanSvg
      {...props}
      viewBox={icon.viewBox}
      width={size}
      height={size}
      fill="currentcolor"
    >
      <path d={icon.path} />
    </CleanSvg>
  )
}

export interface IconProps {
  name: string
  size: string | number
  color: string
}

const Icon = styled(Base)`
  flex: none;
  ${space} ${color};
`;

Icon.displayName = 'Icon';

Icon.defaultProps = {
  name: 'checkLight',
  size: 24,
  legacy: true,
  theme: theme
};

export default Icon;
