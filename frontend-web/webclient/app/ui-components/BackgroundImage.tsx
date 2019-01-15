import styled from 'styled-components'
import { width } from 'styled-system'
import theme from './theme'
import { NumberOrStringOrArray } from "./Types";

const image = (props: { image?: string }) => props.image ? { backgroundImage: `url(${props.image})` } : null

const height = (props) => (props.height ? { height: props.height } : null)

interface BackgroundImageProps {
  image?: string
  width?: NumberOrStringOrArray
  height?: NumberOrStringOrArray
}

const BackgroundImage = styled("div")<BackgroundImageProps>`
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  background-color: ${props => props.theme.colors.gray};
  ${image} ${height} ${width};
`

// FIXME A workaround not fix
// @ts-ignore
BackgroundImage.defaultProps = {
  theme: theme
}

BackgroundImage.displayName = "BackgroundImage";

export default BackgroundImage;
