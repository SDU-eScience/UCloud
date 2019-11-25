import * as React from "react";

const SvgEye = (props: any) => (
  <svg viewBox="0 0 60 20" fill="currentcolor" {...props}>
    <path
      d="M2 10c8 10 38 10 48 0M2 10c8-10 38-10 48 0"
      stroke="currentcolor"
      fill="transparent"
      strokeWidth={0.5}
    />
    <circle cx={26} cy={10} r={4} stroke="currentcolor" fill="currentcolor" />
  </svg>
);

export default SvgEye;
