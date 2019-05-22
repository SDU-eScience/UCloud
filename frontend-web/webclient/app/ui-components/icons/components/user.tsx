import * as React from "react";

const SvgUser = (props: any) => (
  <svg
    viewBox="0 0 25 25"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <circle
      cx={424.005}
      cy={664.005}
      r={12.005}
      fill={props.color2 ? props.color2 : null}
      transform="matrix(1 0 0 1 -412 -652)"
    />
    <path
      d="M5.131 17.813c.697-1.688 1.976-3.32 3.84-3.887a5.942 5.942 0 0 0 2.942.775c1.07 0 2.075-.282 2.943-.775 1.915.584 3.214 2.292 3.896 4.03a8.98 8.98 0 0 1-6.748 3.048 8.985 8.985 0 0 1-6.873-3.191z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M11.914 3.01a5.151 5.151 0 1 0 5.151 5.15 5.151 5.151 0 0 0-5.151-5.15z"
      fill={undefined}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgUser;
