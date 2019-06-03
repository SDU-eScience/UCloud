import * as React from "react";

const SvgRename = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M6.002 19v2.743c0 1.225.955 2.207 2.244 2.257h13.512a2.271 2.271 0 0 0 2.244-2.257V2.258A2.245 2.245 0 0 0 21.758 0H8.246a2.261 2.261 0 0 0-2.244 2.258V5h3V3h12v18h-12v-2h-3z"
      fill={undefined}
    />
    <path
      d="M.236 7.15a.752.752 0 0 1 0-1.088l1.828-1.828a.752.752 0 0 1 1.089 0l1.439 1.439-2.917 2.916L.236 7.15z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
    <path
      d="M14.002 15.083V18h-2.916L2.492 9.406 5.408 6.49l8.594 8.593z"
      fill={undefined}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgRename;
