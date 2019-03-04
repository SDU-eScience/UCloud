import * as React from "react";

const SvgVerified = props => (
  <svg
    viewBox="0 0 20 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M10 0L0 4.04v6.546C0 16.641 4.37 22.626 10 24c5.629-1.375 10-7.36 10-13.414V4.041L10 0z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M7.955 13.896l-2.355-2.6-2.1 2.136 4.455 4.454L17.5 8.341l-2.1-2.145-7.445 7.7z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgVerified;
