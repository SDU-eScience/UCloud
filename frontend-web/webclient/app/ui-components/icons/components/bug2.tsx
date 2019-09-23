import * as React from "react";

const SvgBug2 = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    strokeLinecap="round"
    fill="currentcolor"
    {...props}
  >
    <g fill="none" stroke="#3e332e" strokeWidth={1.5}>
      <path d="M5.75.75l-2 4 8.001 6 7.999-6-2-4M.751 22.75l1.999-2v-4l9-3 9 3v4l2.001 2" />
      <path d="M.75 7.75l1 4 10.001 2 10.042-2 .957-4" />
    </g>
    <circle cx={11.75} cy={4.25} r={3.5} fill="#54433f" />
    <path
      d="M11.749.75h.001c1.932 0 3.5 1.568 3.5 3.5s-1.568 3.5-3.5 3.5h-.001v-7z"
      fill="#3e332e"
    />
    <g>
      <path
        d="M11.75 4.25c3.863 0 7.5 4.809 7.5 9.5s-2.5 7-7.5 7-7.5-2.309-7.5-7 3.636-9.5 7.5-9.5z"
        fill="#725d57"
      />
      <path
        d="M11.75 4.25c3.863 0 7.5 4.809 7.5 9.5s-2.5 7-7.5 7V4.25z"
        fill="#54433f"
      />
      <path
        d="M11.75 9.75c2.575 0 5 3.206 5 6.333 0 3.128-1.667 4.667-5 4.667s-5-1.539-5-4.667c0-3.127 2.424-6.333 5-6.333z"
        fill="#3e332e"
      />
    </g>
  </svg>
);

export default SvgBug2;
