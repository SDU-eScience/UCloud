import styled from 'styled-components'
import { width } from 'styled-system'
import theme from './theme'
import { NumberOrStringOrArray } from "./Types";

const image = (props: { image?: string }) => props.image ? { backgroundImage: `url(${props.image})` } : null

const height = (props: { height?: number | string }) => (props.height ? { height: props.height } : null)

interface BackgroundImageProps {
  image?: string
  width?: NumberOrStringOrArray
  height?: NumberOrStringOrArray
}

const BackgroundImage = styled<BackgroundImageProps, "div">("div")`
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  background-color: ${props => props.theme.colors.gray};
  ${image} ${height} ${width};
`
BackgroundImage.defaultProps = {
  theme: theme
}

BackgroundImage.displayName = "BackgroundImage";

export default BackgroundImage;
