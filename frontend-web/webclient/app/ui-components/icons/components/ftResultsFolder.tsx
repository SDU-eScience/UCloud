import * as React from "react";

const SvgFtResultsFolder = (props: any) => (
  <svg
    viewBox="0 0 25 23"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 1.5A1.503 1.503 0 0 1 1.5 0H9l3 4h10.5A1.5 1.5 0 0 1 24 5.5V8H0V1.5z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M0 7.5A1.5 1.5 0 0 1 1.5 6h21A1.5 1.5 0 0 1 24 7.5l.001 13.5a1.002 1.002 0 0 1-1 1H1a1 1 0 0 1-1-1V7.5z"
      fill={undefined}
    />
    <path
      d="M12 8l5.196 3v6L12 20l-5.196-3v-6L12 8z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgFtResultsFolder;
