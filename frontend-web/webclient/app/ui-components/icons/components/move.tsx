import * as React from "react";

const SvgMove = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M15 0h6.758a2.261 2.261 0 012.244 2.259v19.485c0 1.225-.955 2.207-2.244 2.257H8.246a2.27 2.27 0 01-2.244-2.257V14h3v7h12V3H15V0z"
      fill={props.color2 ? props.color2 : null}
    />
    <path d="M16 6.001l-7.001-6v4h-9v4h9v4l7-6z" fill={undefined} />
  </svg>
);

export default SvgMove;
