import * as React from "react";

const SvgMail = props => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M.004 12.86l6.86 2.573L7.718 24l4.286-6 6 6 6-24-24 12.86z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M17.018 20.129l-4.783-4.817L18.864 6l-10.46 7.95-3.963-1.43 16.788-9.012-4.211 16.62z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgMail;
