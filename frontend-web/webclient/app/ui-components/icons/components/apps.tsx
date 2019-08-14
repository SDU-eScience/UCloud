import * as React from "react";

const SvgApps = (props: any) => (
  <svg
    viewBox="0 0 24 25"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M13.206 5.723l10.023 5.108c.492.25.77.67.77 1.17 0 .49-.278.92-.77 1.17L5.228 22.337l7.978-16.615z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M5.229 22.338l-2.884 1.469a1.72 1.72 0 01-1.562.006C.284 23.564 0 23.138 0 22.638V1.358C0 .858.284.43.783.183a1.714 1.714 0 011.562.006l10.861 5.533-7.977 16.616z"
      fill={undefined}
    />
  </svg>
);

export default SvgApps;
