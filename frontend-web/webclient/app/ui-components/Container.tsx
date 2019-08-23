import styled from "styled-components";
import theme, {Theme} from "./theme";

const maxWidth = (props: {maxWidth?: number | string, theme: Theme}) =>
  props.maxWidth
    ? {maxWidth: `${props.maxWidth}px`}
    : {maxWidth: props.theme.maxContainerWidth};

const Container = styled("div") <{maxWidth?: number}> `
  margin-left: auto;
  margin-right: auto;

  ${maxWidth};
`;

Container.defaultProps = {
  theme
};

Container.displayName = "Container";

export default Container;
