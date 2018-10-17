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

const IconBase = ({ name, size, ...props }): JSX.Element => {
  const icon = getPath({ name })
  if (!icon) return (<></>);

  const listPath = icon.path.map((path: [string, string?], i: number) =>
    //fill can be null, in which case it will not render 
    <path key={i} d={path[0]} fill={path[1]} />
  )

  return (
    <Svg
      {...props}
      viewBox={icon.viewBox}
      width={size}
      height={size}
      fill="currentcolor"
    >
      {listPath}
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
