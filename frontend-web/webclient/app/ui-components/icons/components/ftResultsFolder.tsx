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
      d="M9.47 15.7l-2.355-2.6-2.1 2.136 4.454 4.454 9.546-9.545L16.915 8l-7.446 7.7z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgFtResultsFolder;
