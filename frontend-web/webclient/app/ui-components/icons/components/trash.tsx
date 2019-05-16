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
      d="M3.997 8h-3l1.001 13.998c.036.778 1.219 2 2 2h13.644c.78-.008 2.308-1.22 2.356-2l1-13.999h-3l-1 12c0 .777-.221 1-1 1h-10c-.772 0-1-.23-1-1l-1-12zm-4-5.001h22v2c0 .387-.611.998-1 1h-20c-.382 0-1-.617-1-1V3zm7.5-1.412c0-.78.216-1.588 1.001-1.588h5c.778 0 1 .803 1 1.588v1.412h-7V1.587z"
      fill={undefined}
    />
    <path
      d="M6.498 8h2v10.998h-2V8zm3.5 0h2v10.998h-2V8zm3.5 0h2v10.998h-2V8z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgTrash;
