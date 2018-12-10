import styled from "styled-components"
import { themeGet, space, fontSize, color, SpaceProps } from 'styled-system'
import theme, { ThemeColor } from "./theme"

const fullWidth = ({ fullWidth }: { fullWidth?: boolean }) => fullWidth ? { width: "100%" } : null;

const Stamp = styled.div<StampProps>`
  display: inline-flex;
  align-items: center;
  vertical-align: top;
  min-height: 24px;
  ${fullWidth}
  font-weight: 600;
  letter-spacing: ${themeGet('letterSpacings.caps')};
  border-radius: 4px;
  border-width: 1px;
  border-style: solid;
  border-color: ${props => props.borderColor ? theme.colors[props.borderColor] : theme.colors.black};
  ${space} ${fontSize} ${color};
`

Stamp.displayName = "Stamp";

interface StampProps extends SpaceProps {
  bg?: string
  theme?: any
  fontSize?: number | string
  borderColor?: ThemeColor,
  fullWidth?: boolean
}

Stamp.defaultProps = {
  px: 1,
  py: 0,
  mr: "4px",
  theme: theme,
  color: "black",
  bg: "lightGray",
  borderColor: "black",
  fontSize: 0,
  fullWidth: false
}

export default Stamp;
