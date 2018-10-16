import styled from 'styled-components'
import * as StyledSystem from "styled-system";
import theme from "./theme"

const Flex = styled<FlexProps, "div">("div")`
  display: flex;
  ${StyledSystem.space} ${StyledSystem.width} ${StyledSystem.color} ${StyledSystem.alignItems} ${StyledSystem.justifyContent} ${StyledSystem.flexWrap} ${StyledSystem.flexDirection};
`

Flex.defaultProps = {
  theme
}

interface FlexProps extends StyledSystem.SpaceProps, StyledSystem.WidthProps, StyledSystem.ColorProps,
  StyledSystem.AlignItemsProps, StyledSystem.JustifyContentProps, StyledSystem.FlexWrapProps, StyledSystem.FlexDirectionProps {
    align?: any
    width?: any
    mt?: any
    mr?: any
    ml?: any
    children?: any
    justify?: any
}

Flex.displayName = 'Flex'

export default Flex
