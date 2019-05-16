import * as React from "react";

const SvgCalendar = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M17 0v2H7V0H4v2H2.5A2.507 2.507 0 0 0 0 4.5v17C0 22.873 1.124 24 2.5 24h19c1.375 0 2.5-1.126 2.5-2.5v-17C24 3.125 22.875 2 21.5 2H20V0h-3zm4.5 21.5h-19V8.25h19V21.5z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
      d="M13 13h6v6h-6z"
    />
  </svg>
);

export default SvgCalendar;
