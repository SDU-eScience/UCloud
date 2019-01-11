import styled from 'styled-components'
import theme from './theme'

const Image = styled.img`
  display: block;
  max-width: 100%;
  height: auto;
`;

Image.displayName = 'Image';

// FIXME: Workaround, not a fix.
// @ts-ignore
Image.defaultProps = {
  theme: theme
};

export default Image;
