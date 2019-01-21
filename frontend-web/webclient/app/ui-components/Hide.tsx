import styled from 'styled-components'
import Box, { BoxProps } from './Box'
import theme, { Theme } from './theme'

//const getMaxWidth = (em: number) => em - 0.01

const getMaxWidth = (px: string) => (parseInt(px) - 1) + 'px' 


//TODO: cleanup the media selectors below (maybe put in theme.tsx ?)
const breakpoints = (props: { theme: Theme }) => ({
  xs: `@media screen and (max-width: ${getMaxWidth(props.theme.breakpoints[0])})`,
  sm: `@media screen and (min-width: ${props.theme.breakpoints[0]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[1])})`,
  md: `@media screen and (min-width: ${props.theme.breakpoints[1]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[2])})`,
  lg: `@media screen and (min-width: ${props.theme.breakpoints[2]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[3])})`,
  xl: `@media screen and (min-width: ${props.theme.breakpoints[3]}) and (max-width: ${getMaxWidth(props.theme.breakpoints[4])})`,
  xxl:`@media screen and (min-width: ${props.theme.breakpoints[4]})`
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
  xxl?: boolean
}

const Hide = styled(Box) <HideProps>`
  ${hidden("xs")} 
  ${hidden("sm")} 
  ${hidden("md")} 
  ${hidden("lg")} 
  ${hidden("xl")} 
  ${hidden("xxl")}
`;

Hide.defaultProps = {
  theme: theme
};

Hide.displayName = "Hide";

export default Hide;
