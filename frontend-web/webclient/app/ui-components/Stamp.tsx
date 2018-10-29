import styled from "styled-components"
import { themeGet, space, fontSize, color, SpaceProps } from 'styled-system'
import theme from "./theme"
import { NumberOrStringOrArray } from "./Types";

const Stamp = styled("div") <StampProps>`
  display: inline-flex;
  align-items: center;
  vertical-align: top;
  min-height: 24px;
  font-weight: 600;
  letter-spacing: ${themeGet('letterSpacings.caps')};
  border-radius: 2px;
  border-width: 1px;
  border-style: solid;
  border-color: ${() => theme.colors.borderGray};
  ${space} ${fontSize} ${color};
`

Stamp.displayName = "Stamp";

interface StampProps extends SpaceProps {
  bg?: string
  theme?: any
  fontSize?: number | string
}

Stamp.defaultProps = {
  px: 1,
  py: 0,
  theme: theme,
  color: "black",
  bg: "lightGray",
  fontSize: 0
}

export default Stamp;
