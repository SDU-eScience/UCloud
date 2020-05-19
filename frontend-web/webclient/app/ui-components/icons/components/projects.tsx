import * as React from "react";

const SvgProjects = (props: any) => (
  <svg
    viewBox="0 0 21 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M10.394-.002l10.392 6v12l-10.392 6-10.392-6v-12l10.392-6z"
      fill={undefined}
    />
    <path
      d="M10.393 6l5.196 3v6l-5.196 3-5.196-3V9l5.196-3z"
      fill={props.color2 ? props.color2 : "currentcolor"}
    />
  </svg>
);

export default SvgProjects;
