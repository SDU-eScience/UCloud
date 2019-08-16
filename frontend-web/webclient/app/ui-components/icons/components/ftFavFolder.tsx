import * as React from "react";

const SvgFtFavFolder = (props: any) => (
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
      d="M0 7.5A1.5 1.5 0 011.5 6h21A1.5 1.5 0 0124 7.5l.001 13.5a1.002 1.002 0 01-1 1H1a.997.997 0 01-1-1V7.5z"
      fill={undefined}
    />
    <path
      d="M12 8l1.95 3.95 4.359.634-3.154 3.074.744 4.342L12 17.95 8.101 20l.745-4.342-3.154-3.074 4.359-.634L12 8z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgFtFavFolder;
