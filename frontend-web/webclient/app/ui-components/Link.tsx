import styled from 'styled-components';
import { color, ColorProps } from 'styled-system';
import theme from './theme';
import { Link as ReactRouterLink, Router } from "react-router-dom";

export interface LinkProps extends ColorProps {

}



const Link = styled<LinkProps, "a">("a")`
  cursor: pointer;
  text-decoration: none;
  ${color} &:hover {
    color: ${props => props.theme.colors["red"]}
  }
`

Link.displayName = 'Link';

Link.defaultProps = {
  theme: theme,
  color: "inherit"
};


const RouterLink = styled(ReactRouterLink)`
  cursor: pointer;
  text-decoration: none;
  ${color} &:hover {
    color: ${props => props.theme.colors["red"]}
  }
`

RouterLink.defaultProps = {
  theme: theme,
  color: "inherit"
};

RouterLink.displayName = "Link";


export default RouterLink;
