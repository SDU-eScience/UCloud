import * as React from "react";

const SvgEdit = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M23.6 5.4a1.289 1.289 0 000-1.867L20.467.4A1.289 1.289 0 0018.6.4l-2.467 2.467 5 5L23.6 5.4z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
    <path
      d="M0 19v5h5L19.733 9.267l-5-5L0 19z"
      fill={undefined}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgEdit;
