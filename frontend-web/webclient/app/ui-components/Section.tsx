import * as React from "react";
import styled from "styled-components";

export const Section = styled.section<{ highlight?: boolean, gap?: string }>`
  padding: 16px;
  border-top-left-radius: 10px;
  border-top-right-radius: 10px;
  background-color: var(${props => props.highlight ? "--appStoreFavBg" : "--lightGray"}, #f00);
  ${props => props.gap === undefined ? null : ({
    display: "grid",
    gridGap: props.gap
  })}
`;

Section.defaultProps = {
  gap: "16px"
};