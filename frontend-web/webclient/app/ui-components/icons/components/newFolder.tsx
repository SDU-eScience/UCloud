import * as React from "react";

const SvgNewFolder = (props: any) => (
  <svg
    viewBox="0 0 25 25"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M.005 23.025L.002 23V1.5a1.504 1.504 0 011.5-1.5h7.5l3 4h10.5a1.503 1.503 0 011.5 1.5V23a1 1 0 01-1 1h-22 10v-3h10V7h-11l-3-4h-4v10H.006v10.025z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M4.875 15.25H0v3.5h4.875V24h3.25v-5.25H13v-3.5H8.125V10h-3.25v5.25z"
      fill={undefined}
    />
  </svg>
);

export default SvgNewFolder;
