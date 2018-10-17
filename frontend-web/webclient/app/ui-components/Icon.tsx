import * as React from 'react'
import styled from 'styled-components'
import { space, color } from 'styled-system'
import { icons } from './icons.json'
import theme from './theme'

const getPath = ({ name }) => icons[name]

const Svg = styled.svg`
  flex: none;
  ${space} ${color};
`

const IconBase = ({ name, size, ...props }): JSX.Element  => {
  const icon = getPath({ name })
  if (!icon) return (<></>);

  return (
    <Svg
      {...props}
      viewBox={icon.viewBox}
      width={size}
      height={size}
      fill="currentcolor"
    >
      <path d={icon.path} />
    </Svg>
  )
}

export interface IconProps {
  name: string
  size: string | number
  color: string
}

const Icon = styled(IconBase)`
  flex: none;
  ${space} ${color};
`;

Icon.displayName = 'Icon'

Icon.defaultProps = {
  theme,
  name: 'checkLight',
  size: 24
}


export default Icon
