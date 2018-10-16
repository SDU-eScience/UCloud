import styled from "styled-components"
import * as StyledSystem from "styled-system"
import theme from "./theme"
import { NumberOrStringOrArray } from "./Types";

const Stamp = styled<StampProps, "div">("div")`
  display: inline-flex;
  align-items: center;
  vertical-align: top;
  min-height: 24px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: ${StyledSystem.theme('letterSpacings.caps')};
  border-radius: 2px;
  border-width: 1px;
  border-style: solid;
  border-color: ${() => theme.colors.borderGray};
  ${StyledSystem.space} ${StyledSystem.fontSize} ${StyledSystem.color};
`

Stamp.displayName = "Stamp";

interface StampProps {
  bg: string
  px: NumberOrStringOrArray
  py: NumberOrStringOrArray
  theme: any
  fontSize: number | string
}

Stamp.defaultProps = {
  px: 1,
  py: 0,
  theme: theme,
  color: 'gray',
  bg: 'lightGray',
  fontSize: 0
}

export default Stamp;
