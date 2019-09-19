import * as React from "react";

const SvgExtract = (props: any) => (
  <svg
    viewBox="-2 0 20 25"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M9 0v3H3v18h12V11h2.999v-1H18v11.743A2.244 2.244 0 0115.756 24H2.244A2.26 2.26 0 010 21.743V2.258A2.245 2.245 0 012.244 0H9zm5.999 10H15v1h-.001v-1z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M19.999 0l-9.193.707 2.829 2.829-6.364 6.363 2.828 2.829 6.364-6.364 2.829 2.828L19.999 0z"
      fill={undefined}
    />
  </svg>
);

export default SvgExtract;
