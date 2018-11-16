import styled from 'styled-components';
import theme from './theme';
import { Link as ReactRouterLink } from "react-router-dom";
import { space, SpaceProps } from 'styled-system';


const Link = styled(ReactRouterLink) <{ color?: string } & SpaceProps>`
  cursor: pointer;
  text-decoration: none;
  ${space};
  color: ${props => props.color ? props.theme.colors[props.color] : "red"};

  &:hover {
    color: ${props => props.theme.colors.blue};
  }
`

Link.defaultProps = {
  theme,
  color: "black"
};

Link.displayName = "Link";


export default Link;
