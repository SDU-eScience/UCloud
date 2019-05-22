import * as React from "react";

const SvgCheckDouble = (props: any) => (
  <svg
    viewBox="0 0 25 19"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M7.999 15l-6-6.019-2 2.02 8 8 16-17-2.001-2-14 15z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M13 16.962L14.999 19 24 9.96l-2-1.999-9 9z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgCheckDouble;
