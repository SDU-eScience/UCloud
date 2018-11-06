import styled from "styled-components"
import theme from "./theme"

const maxWidth = props =>
  props.maxWidth
    ? { maxWidth: `${props.maxWidth}px` }
    : { maxWidth: props.theme.maxContainerWidth }

const Container = styled("div") <{ maxWidth?: number }> `
  margin-left: auto;
  margin-right: auto;

  ${maxWidth};
`;

Container.defaultProps = {
  theme: theme
};

Container.displayName = "Container";

export default Container;
