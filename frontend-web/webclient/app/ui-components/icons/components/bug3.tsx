import * as React from "react";

const SvgBug3 = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    strokeLinecap="round"
    fill="currentcolor"
    {...props}
  >
    <g fill="none" stroke="#3e332e" strokeWidth={1.5}>
      <path d="M1.75 5.75l3 3h14l2.999-3M.75 15.75h2l1-3h16l1 3h2" />
      <path d="M3.75 22.75v-4l8-5 8 5v4" />
    </g>
    <path
      d="M9.75.75l-2 1 1 2 3 2 3-2 1-2-2-1"
      fill="none"
      stroke="#54433f"
      strokeWidth={1.5}
    />
    <circle cx={11.75} cy={6.25} r={3.5} fill="#3e332e" />
    <g>
      <path
        d="M17.659 9.75c.059.316.091.65.091 1v5.5c0 3.036-2.689 5.5-6 5.5s-6-2.464-6-5.5v-5.5c0-.358.022-.692.063-1h11.846z"
        fill="#ff9102"
      />
      <path
        d="M17.659 9.75c.059.316.091.65.091 1v5.5c0 3.036-2.689 5.5-6 5.5v-12h5.909z"
        fill="#ff641a"
      />
      <path
        d="M14.75 6.75c1.541 0 2.989 1.626 3 3.972v.028c-3 0-5.985-.028-6 2 0-1.752-3-2-6-1.983v-.017c0-2.736 1.266-4 3-4h6z"
        fill="#54433f"
      />
    </g>
  </svg>
);

export default SvgBug3;
