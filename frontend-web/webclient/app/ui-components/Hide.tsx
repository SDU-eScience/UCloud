import styled from 'styled-components'
import Box, { BoxProps } from './Box'
import theme from './theme'

const getMaxWidth = (em: number) => em - 0.01

const breakpoints = (props: { theme: any }) => ({
  xs: `@media screen and (max-width: ${getMaxWidth(
    props.theme.breakpoints[0]
  )}em)`,
  sm: `@media screen and (min-width: ${
    props.theme.breakpoints[0]
    }em) and (max-width: ${getMaxWidth(props.theme.breakpoints[1])}em)`,
  md: `@media screen and (min-width: ${
    props.theme.breakpoints[1]
    }em) and (max-width: ${getMaxWidth(props.theme.breakpoints[2])}em)`,
  lg: `@media screen and (min-width: ${
    props.theme.breakpoints[2]
    }em) and (max-width: ${getMaxWidth(props.theme.breakpoints[3])}em)`,
  xl: `@media screen and (min-width: ${props.theme.breakpoints[3]}em)`
})

export const hidden = (key: any) => (props: any) =>
  props[key]
    ? {
      [breakpoints(props)[key]]: {
        display: "none"
      }
    }
    : null

export interface HideProps extends BoxProps {
  xs?: boolean
  sm?: boolean
  md?: boolean
  lg?: boolean
  xl?: boolean
}

const Hide = styled(Box) <HideProps>`
  ${hidden("xs")} ${hidden("sm")} ${hidden("md")} ${hidden("lg")} ${hidden("xl")};
`;

Hide.defaultProps = {
  theme: theme
};

Hide.displayName = "Hide";

export default Hide;
