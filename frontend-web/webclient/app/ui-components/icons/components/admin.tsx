import * as React from "react";

const SvgAdmin = props => (
  <svg
    viewBox="0 0 16 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M5.332 13.492l4.808-3.006h5.52L3.006 23.992l3.615-10.5h-1.29z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M9.03 10.497h1.103l-4.8 3.001H0L12.644 0 9.03 10.497z"
      fill={undefined}
    />
  </svg>
);

export default SvgAdmin;
