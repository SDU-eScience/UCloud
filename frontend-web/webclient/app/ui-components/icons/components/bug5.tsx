import * as React from "react";

const SvgBug5 = (props: any) => (
  <svg
    viewBox="0 0 18 24"
    fillRule="evenodd"
    clipRule="evenodd"
    strokeLinecap="round"
    fill="currentcolor"
    {...props}
  >
    <g fill="none" stroke="#463f3f" strokeWidth={1.25}>
      <path d="M2.749 6.832l2-1.99 4 5 4-5 2.043 1.99M2.749 17.832l2-2 .001-3h8l-.001 3 2 2" />
      <path d="M2.749 10.832l2-2 4.022 3 3.978-3 2 2M8.749 16.832v6M15.749 21.332s-5-1-7-6c-2 5-7 6-7 6" />
    </g>
    <path
      d="M8.75 5.832c2.999 0 2.5 1.912 2.5 5.5 0 3.587-1.12 6.5-2.5 6.5s-2.5-2.913-2.5-6.5c0-3.588-.501-5.5 2.5-5.5z"
      fill="#766f6e"
    />
    <path
      d="M8.75 5.832c2.999 0 2.5 1.912 2.5 5.5 0 3.587-1.12 6.5-2.5 6.5v-12z"
      fill="#5b5555"
    />
    <g>
      <path
        d="M.75.832s6-1 8 4c2-5 8-4 8-4"
        fill="none"
        stroke="#766f6e"
        strokeWidth={1.5}
      />
      <circle cx={8.75} cy={4.832} r={2} fill="#5b5555" />
      <path d="M8.75 2.832a2 2 0 010 4v-4z" fill="#463f3f" />
    </g>
  </svg>
);

export default SvgBug5;
