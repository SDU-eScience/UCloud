import * as React from "react";

const SvgSun = (props: any) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 10 10"
    fill="currentcolor"
    {...props}
  >
    <circle cx={5} cy={5} r={3} />
  </svg>
);

export default SvgSun;
