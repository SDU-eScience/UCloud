import styled from "styled-components";
import {color, ColorProps, space, SpaceProps, width, WidthProps} from "styled-system";

export interface BaseLinkProps extends SpaceProps, ColorProps, WidthProps {
    hoverColor?: string;
}

const BaseLink = styled.a<BaseLinkProps>`
  cursor: pointer;
  text-decoration: none;
  ${space};
  ${color};
  ${width};

  &:hover {
    color: var(--${props => props.hoverColor ?? "textHighlight"});
  }
`;

BaseLink.defaultProps = {
    color: "text"
};

BaseLink.displayName = "BaseLink";

export default BaseLink;
