import * as React from "react";

const SvgDocs = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M5 1h17v19.5m.5 2.5H4m18-3H4a1 1 10 000 3m-1.5-1.5V3S2.4 1 5 1"
      fill="none"
      stroke="currentColor"
    />
    <path
      fill="none"
      stroke="currentColor"
      strokeWidth={0.2}
      d="M4.5 1v19m18 0a1 1 10 00-.5 3"
    />
    <svg x={6.4} y={4} viewBox="0 0 22 24" fill="currentcolor" {...props}>
      <use xlinkHref="#docs_svg__ucloud" />
    </svg>
  </svg>
);

export default SvgDocs;
