import * as React from "react";

const SvgWarning = (props: any) => (
  <svg viewBox="0 0 24 24" fill="currentcolor" {...props}>
    <path
      d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2zm1 11h-2V7h2v6zm0 4h-2v-2h2v2z"
      fill={undefined}
    />
  </svg>
);

export default SvgWarning;
