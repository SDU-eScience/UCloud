import * as React from "react";

const SvgPlay = (props: any) => (
  <svg
    viewBox="0 0 24 26"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M13.206 6.203l10.023 5.534c.492.27.77.725.77 1.267 0 .53-.278.996-.77 1.267L5.228 24.202l7.978-18z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M5.229 24.202l-2.884 1.592a1.602 1.602 0 0 1-1.562.006C.284 25.531 0 25.069 0 24.527V1.474C0 .932.284.47.783.201a1.597 1.597 0 0 1 1.562.007l10.861 5.994-7.977 18z"
      fill={undefined}
    />
  </svg>
);

export default SvgPlay;
