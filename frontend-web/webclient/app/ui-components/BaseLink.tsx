import styled from "styled-components";
import {color, ColorProps, space, SpaceProps, style, width, WidthProps} from "styled-system";

export interface BaseLinkProps extends SpaceProps, ColorProps, WidthProps {
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
  ${width};

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
