import * as React from 'react'
import styled from 'styled-components'
import { space, color, propTypes, cleanElement } from 'styled-system'
//import icons from '../icons.json' // TODO We are missing this guy!!!
import theme from './theme'

const icons = {}; // TODO FIXME

const getPath = ({ name }) => {
  return icons[name]
}

// Remove `space` props from the `svg` element prevents react warnings
const CleanSvg = cleanElement('svg')
CleanSvg.propTypes = {
  ...propTypes.space
}

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
`

Icon.displayName = 'Icon'

Icon.defaultProps = {
  name: 'checkLight',
  size: 24,
  legacy: true,
  theme: theme
}

export default Icon;
