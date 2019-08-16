import * as React from "react";

const SvgLogout = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M19.712 2.808A12.023 12.023 0 0011.99 0C5.37 0-.005 5.378-.005 12c0 6.624 5.375 12 11.996 12 2.823 0 5.556-.993 7.72-2.807l-1.93-2.299A9.013 9.013 0 0111.991 21c-4.961 0-8.993-4.033-8.993-9s4.032-9 8.993-9c2.12 0 4.167.746 5.79 2.106l1.93-2.298z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M23.997 12l-6.25-5.625v3.75h-8.75v3.749h8.75v3.75L23.998 12z"
      fill={undefined}
    />
  </svg>
);

export default SvgLogout;
