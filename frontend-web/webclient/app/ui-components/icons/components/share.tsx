import * as React from "react";

const SvgShare = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M6 5V2.258A2.245 2.245 0 0 1 8.246 0h13.512A2.261 2.261 0 0 1 24 2.258v19.485c0 1.225-.956 2.207-2.244 2.257H8.245A2.27 2.27 0 0 1 6 21.743V19h3v2h12V3H9v2H6z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M16 12.001l-7 6v-4H8c-3.18 0-6.046 1.93-8 4 .207-3.97 3.715-8 8-8h1v-4l7 6z"
      fill={undefined}
    />
  </svg>
);

export default SvgShare;
