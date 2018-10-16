import styled from 'styled-components';
import { color, ColorProps } from 'styled-system';
import theme from './theme';

export interface LinkProps extends ColorProps {

}

const Link = styled<LinkProps, "a">("a")`
  cursor: pointer;
  text-decoration: none;
  ${color} &:hover {
    text-decoration: underline;
  }
`

Link.displayName = 'Link';

/*
Link.propTypes = {
  color: PropTypes.string
};
*/

Link.defaultProps = {
  color: 'blue',
  theme: theme
};

export default Link;
