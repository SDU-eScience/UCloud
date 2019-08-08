import styled from "styled-components";
import theme from "./theme";
import {space, width, borderColor, SpaceProps, WidthProps, BorderColorProps} from "styled-system";

export type DividerProps = SpaceProps & WidthProps & BorderColorProps;

const Divider = styled("hr")<DividerProps>`
  border: 0;
  border-bottom-style: solid;
  border-bottom-width: 1px;
  ${space} ${width} ${borderColor};
`;

Divider.displayName = "Divider";

Divider.defaultProps = {
    borderColor: "borderGray",
    theme: theme,
    ml: 0,
    mr: 0
};

export default Divider;