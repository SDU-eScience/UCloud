import Box from "ui-components/Box";
import styled from "styled-components";

export const StickyBox = styled(Box)<{ shadow?: boolean, normalMarginX?: string }>`
  position: sticky;
  background: var(--white, #f00);

  margin-left: -${p => p.normalMarginX};
  padding-left: ${p => p.normalMarginX};
  padding-right: ${p => p.normalMarginX};
  width: calc(100% + ${p => p.normalMarginX} * 2);

  ${p => p.shadow === true ? ({
    boxShadow: "0 1px 5px 0 rgba(0, 0, 0, 0.2)"
  }) : null}
`;

StickyBox.defaultProps = {
    paddingY: "20px",
    top: "-20px",
    zIndex: 1000,
    normalMarginX: "0px"
};