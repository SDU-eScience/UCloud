import styled from 'styled-components';
import { Link as ReactRouterLink } from "react-router-dom";
import { style, space, color, SpaceProps, ColorProps } from 'styled-system';

interface LinkProps extends SpaceProps, ColorProps 
{
  hoverColor?: string
}

const hoverColor = style({
  prop: 'hoverColor',
  cssProperty: 'color',
  key: 'colors'
})

const Link = styled(ReactRouterLink) <LinkProps>`
  cursor: pointer;
  text-decoration: none;
  ${space}
  ${color}

  &:hover {
    ${hoverColor}
  }
`

Link.defaultProps = {
  color: "text",
  hoverColor: "textHighlight"
};

Link.displayName = "Link";


export default Link;
