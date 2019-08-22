import styled from "styled-components";
import { color, ColorProps, space, SpaceProps, style } from "styled-system";

export interface BaseLinkProps extends SpaceProps, ColorProps {
  hoverColor?: string;
}

const hoverColor = style({
  prop: "hoverColor",
  cssProperty: "color",
  key: "colors"
});

const BaseLink = styled.a<BaseLinkProps>`
  cursor: pointer;
  text-decoration: none;
  ${space};
  ${color};

  &:hover {
    ${hoverColor};
  }
`;

BaseLink.defaultProps = {
  color: "text",
  hoverColor: "textHighlight"
};

BaseLink.displayName = "BaseLink";

export default BaseLink;
