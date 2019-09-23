import * as React from "react";

const SvgBug6 = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    strokeLinecap="round"
    fill="currentcolor"
    {...props}
  >
    <g fill="none" stroke="#3e332e" strokeWidth={1.5}>
      <path d="M.75 7.75h3l2.532 1.011h11.002L19.75 7.75h3" />
      <path d="M1.75.75l3 1 6.989 8 7.011-8 3-1M.75 16.75l3.001-2v-3H19.75v3l3 2" />
    </g>
    <path
      d="M11.717 9.25c-9.967.5-9.967 19.5 0 12.5 10.033 7 10.033-12 0-12.5z"
      fill="#fff7cd"
    />
    <path
      d="M11.75 9.252c9.989.54 9.989 19.448 0 12.521V9.252z"
      fill="#ffe6b3"
    />
    <path
      d="M11.717 4.5c-4.169 3.34-3.795 7.043 0 11 3.765-3.71 4.314-7.382 0-11z"
      fill="#725d57"
    />
    <path
      d="M8.746 10.05a7.051 7.051 0 012.971-.8 7.15 7.15 0 013.025.818c-.105 1.8-1.178 3.612-3.025 5.432-1.801-1.878-2.831-3.698-2.971-5.45z"
      fill="#54433f"
    />
    <g>
      <path
        d="M11.751 4.75l-.001-3"
        fill="none"
        stroke="#725d57"
        strokeWidth={1.5}
      />
      <circle cx={10.5} cy={5.75} r={1.25} fill="#ff641a" />
      <circle cx={13.001} cy={5.75} r={1.25} fill="#ff641a" />
    </g>
  </svg>
);

export default SvgBug6;
