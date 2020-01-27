import * as React from "react";

const SvgTrash = (props: any) => (
  <svg
    viewBox="0 0 22 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M4 8H1l1 14c.035.778 1.219 2 2 2h13.644c.781-.008 2.309-1.22 2.356-2l1-14h-3l-1 12c0 .776-.221 1-1 1H6c-.771 0-1-.229-1-1L4 8zM0 3h22v2c0 .388-.612.998-1 1H1c-.383 0-1-.617-1-1V3zm7.5-1.412C7.5.809 7.715 0 8.5 0h5c.779 0 1 .803 1 1.588V3h-7V1.588z"
      fill={undefined}
    />
    <path
      d="M6.5 8h2v11H7L6.5 8zM10 8h2v11h-2V8zm3.5 0h2L15 19h-1.5V8z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgTrash;
