import styled from 'styled-components';
import { style, space, color, SpaceProps, ColorProps } from 'styled-system';

export interface BaseLinkProps extends SpaceProps, ColorProps {
  hoverColor?: string
}

const hoverColor = style({
  prop: 'hoverColor',
  cssProperty: 'color',
  key: 'colors'
})

const BaseLink = styled.a<BaseLinkProps>`
  cursor: pointer;
  text-decoration: none;
  ${space};
  ${color};

  &:hover {
    ${hoverColor};
  }
`;

// FIXME
// @ts-ignore
BaseLink.defaultProps = {
  color: "text",
  hoverColor: "textHighlight"
};

BaseLink.displayName = "BaseLink";

export default BaseLink;
