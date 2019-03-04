import * as React from "react";

const SvgArrowDown = props => (
  <svg
    viewBox="0 0 18 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path d="M9 24l9-10h-6V0H6v14h-6l9 10z" fill={undefined} />
  </svg>
);

export default SvgArrowDown;
