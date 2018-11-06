import * as React from 'react'
import styled from 'styled-components'
import * as StyledSystem from 'styled-system'
import theme from './theme'

const ToggleBadge = styled<any, any>("button")`
  border-radius: ${props => props.theme.radius};
  border: 0;
  display: inline-block;
  font-weight: ${props => props.theme.bold};
  font-family: inherit;
  cursor: pointer;
  background-color: ${(props: any) =>
    props.selected ? props.theme.colors[props.bg] : props.unSelectedBg};
  color: ${(props: any) => props.theme.colors[props.color]};
  ${StyledSystem.space} ${StyledSystem.fontSize};
  &:hover {
    background-color: ${(props: any) => props.theme.colors[props.bg]};
  }
`

ToggleBadge.displayName = "ToggleBadge";

interface ToggleBadge extends StyledSystem.SpaceProps, StyledSystem.FontSizeProps, StyledSystem.ColorProps {
  selected?: boolean
}

ToggleBadge.defaultProps = {
  selected: false,
  px: 2,
  py: 1,
  mx: 1,
  my: 1,
  fontSize: 0,
  theme: theme,
  color: 'blue',
  bg: 'lightBlue',
  unSelectedBg: 'transparent'
}

export default ToggleBadge
