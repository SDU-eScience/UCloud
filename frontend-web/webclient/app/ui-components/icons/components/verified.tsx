import * as React from "react";

const SvgVerified = (props: any) => (
  <svg
    viewBox="0 0 20 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M10 0L0 4.041v6.545C0 16.641 4.371 22.625 10 24c5.629-1.375 10-7.359 10-13.414V4.041L10 0zm0 3l7.5 3.031v4.909c0 4.541-3.278 9.029-7.5 10.06-4.222-1.031-7.5-5.519-7.5-10.06V6.031L10 3z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M8.5 13.498l-1.85-2.042L5 13.134l3.5 3.5 7.5-7.5-1.65-1.686-5.85 6.05z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgVerified;
