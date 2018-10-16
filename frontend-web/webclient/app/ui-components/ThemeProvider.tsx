import * as React from 'react'
import * as PropTypes from 'prop-types'
import styled, {
  ThemeProvider as StyledThemeProvider,
  injectGlobal
} from 'styled-components'
import nextTheme from './theme'

injectGlobal`body {
  margin: 0;
}`

export const Base = styled<any, any>("div")`
  font-family: ${props => props.theme.font};
  line-height: 1.4;

  * {
    box-sizing: border-box;
  }
`

const ThemeProvider = ({ customBreakpoints, ...props }) => {
  const baseTheme = nextTheme
  const breakpoints = customBreakpoints || baseTheme.breakpoints
  const theme = {
    ...baseTheme,
    breakpoints
  }

  return (
    <StyledThemeProvider theme={theme}>
      <Base {...props} />
    </StyledThemeProvider>
  )
}

ThemeProvider.propTypes = {
  /** Enable legacy color palette */
  legacy: PropTypes.bool,
  /** Array of pixel values for custom breakpoint overrides */
  customBreakpoints: PropTypes.arrayOf(PropTypes.number)
}

export default ThemeProvider
