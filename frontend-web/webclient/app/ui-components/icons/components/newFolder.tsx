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
      d="M.005 23.07A.82.82 0 01.002 23V1.5A1.504 1.504 0 011.503 0h10.499l3 4h7.501a1.503 1.503 0 011.5 1.5V23a1 1 0 01-1 1H.997h10.005v-3h10V7h-8l-3-4h-7l.001 10H.005v10.07z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M4.875 15.25H0v3.5h4.875V24h3.25v-5.25H13v-3.5H8.125V10h-3.25v5.25z"
      fill={undefined}
    />
  </svg>
);

export default SvgNewFolder;
