import * as React from "react";

const SvgRefresh = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M.168 10C1.127 4.343 6.081 0 12 0c3.3 0 6.3 1.35 8.475 3.525L23 .98l1 8.488-8.377-.916L18.3 5.7A8.717 8.717 0 0 0 12 3c-4.263 0-7.859 3.004-8.775 7H.168z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M23.832 14C22.873 19.657 17.919 24 12 24c-3.3 0-6.3-1.35-8.475-3.525L1 23.02l-1-8.488 8.377.916L5.7 18.3A8.717 8.717 0 0 0 12 21c4.263 0 7.859-3.004 8.775-7h3.057z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgRefresh;
