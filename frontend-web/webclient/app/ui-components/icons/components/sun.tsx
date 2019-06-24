import * as React from "react";

const SvgSun = (props: any) => (
  <svg viewBox="0 0 10 10" fill="currentcolor" {...props}>
    <circle cx={5} cy={5} r={3} stroke="#000" strokeWidth={0} />
  </svg>
);

export default SvgSun;
