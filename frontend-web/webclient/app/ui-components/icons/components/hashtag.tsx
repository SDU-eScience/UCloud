import * as React from "react";

const SvgHashtag = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path d="M4 24h3l4-24H8L4 24zM13 24h3l4-24h-3l-4 24z" fill={undefined} />
    <path
      d="M21.993 18H0l.5-3h21.993l-.5 3zM23.493 9H1.5L2 6h21.993l-.5 3z"
      fill={undefined}
    />
  </svg>
);

export default SvgHashtag;
