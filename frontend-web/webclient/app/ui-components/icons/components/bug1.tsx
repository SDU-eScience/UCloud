import * as React from "react";

const SvgBug1 = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    strokeLinecap="round"
    fill="currentcolor"
    {...props}
  >
    <g fill="none" stroke="#3e332e" strokeWidth={1.5}>
      <path d="M3.679 6.348l2.602 2.494h11.002l2.509-2.494M4.75 22.832v-4.507l6.986-3.493 7.014 3.493v4.507" />
      <path d="M.75 15.208h3l1-2.753h14l1.042 2.753h2.958" />
    </g>
    <path
      d="M.75.832s9-1 11 4c2-5 11-4 11-4"
      fill="none"
      stroke="#3e332e"
      strokeWidth={1.5}
    />
    <circle cx={11.75} cy={4.832} r={2} fill="#725d57" />
    <path d="M11.75 2.832a2 2 0 010 4v-4z" fill="#54433f" />
    <g>
      <ellipse cx={11.75} cy={13.832} rx={5} ry={8} fill="#725d57" />
      <path
        d="M11.75 5.832c2.76 0 5 3.584 5 8 0 4.415-2.24 8-5 8v-16z"
        fill="#54433f"
      />
      <path
        d="M7.479 9.673c.878-2.303 2.463-3.841 4.271-3.841 1.808 0 3.393 1.538 4.271 3.841a8.348 8.348 0 01-4.271 1.159 8.348 8.348 0 01-4.271-1.159z"
        fill="#3e332e"
      />
    </g>
  </svg>
);

export default SvgBug1;
