import * as React from "react";

const SvgEllipsis = (props: any) => (
  <svg
    viewBox="0 0 24 6"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M3 0C1.35 0 0 1.35 0 3s1.35 3 3 3 3-1.35 3-3-1.35-3-3-3zm18 0c-1.65 0-3 1.35-3 3s1.35 3 3 3 3-1.35 3-3-1.35-3-3-3zm-9 0c-1.65 0-3 1.35-3 3s1.35 3 3 3 3-1.35 3-3-1.35-3-3-3z"
      fill={undefined}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgEllipsis;
