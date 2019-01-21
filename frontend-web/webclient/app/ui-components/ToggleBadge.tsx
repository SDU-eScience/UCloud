import styled from 'styled-components'
import theme from './theme'
import { SpaceProps, FontSizeProps, ColorProps, space, fontSize } from 'styled-system';

const ToggleBadge = styled.button<ToggleBadge>`
  border-radius: ${props => props.theme.radius};
  border: 0;
  display: inline-block;
  font-weight: ${props => props.theme.bold};
  font-family: inherit;
  cursor: pointer;
  background-color: ${(props: any) =>
    props.selected ? props.theme.colors[props.bg] : props.unSelectedBg};
  color: ${(props: any) => props.theme.colors[props.color]};
  ${space};
  ${fontSize};
  &:hover {
    background-color: ${(props: any) => props.theme.colors[props.bg]};
  }
`

ToggleBadge.displayName = "ToggleBadge";

interface ToggleBadge extends SpaceProps, FontSizeProps, ColorProps {
  selected?: boolean
  unSelectedBg?: string
}

ToggleBadge.defaultProps = {
  selected: false,
  fontSize: 0,
  theme: theme,
  color: 'blue',
  bg: 'lightBlue',
  unSelectedBg: 'transparent'
}

export default ToggleBadge
