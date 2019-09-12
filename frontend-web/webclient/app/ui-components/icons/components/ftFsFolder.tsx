import * as React from "react";

const SvgFtFsFolder = (props: any) => (
  <svg
    viewBox="0 0 25 23"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 1.5A1.503 1.503 0 011.5 0H9l3 4h10.5A1.5 1.5 0 0124 5.5V8H0V1.5z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M0 7.5A1.5 1.5 0 011.5 6h21A1.5 1.5 0 0124 7.5l.001 13.5a1.002 1.002 0 01-1 1H1a1 1 0 01-1-1V7.5z"
      fill={undefined}
    />
    <ellipse
      cx={12}
      cy={9.5}
      rx={5}
      ry={1.5}
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M6.999 12.5c0 .828 2.242 1.5 5.001 1.5 2.754 0 4.991-.674 4.999-1.5v-2c0 .826-2.245 1.5-4.999 1.5-2.759 0-5.001-.672-5-1.5l-.001 2z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M6.999 15.5c0 .828 2.242 1.5 5.001 1.5 2.754 0 4.991-.674 4.999-1.5v-2c0 .826-2.245 1.5-4.999 1.5-2.759 0-5.001-.672-5-1.5l-.001 2z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M6.999 18.5c0 .828 2.242 1.5 5.001 1.5 2.754 0 4.991-.674 4.999-1.5v-2c0 .826-2.245 1.5-4.999 1.5-2.759 0-5.001-.672-5-1.5l-.001 2z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgFtFsFolder;
