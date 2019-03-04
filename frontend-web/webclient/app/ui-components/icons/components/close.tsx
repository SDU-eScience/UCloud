import * as React from "react";

const SvgClose = props => (
  <svg
    viewBox="0 0 25 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M24.002 2.001L22 .001 12 9.6l-10-9.6-2 2 9.6 10L.001 22l2 2 10-9.6 10 9.6 2-2-9.6-10 9.6-9.999z"
      fill={undefined}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgClose;
