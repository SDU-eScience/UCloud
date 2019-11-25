import * as React from "react";

const SvgFullscreen = (props: any) => (
  <svg viewBox="0 0 500 500" fill="currentcolor" {...props}>
    <path
      fill="currentcolor"
      stroke="currentcolor"
      strokeWidth={20}
      d="M10 10h190L10 200zM300 10l190 190V10zM200 490H10V300zM300 490l190-190v190z"
    />
    <path
      d="M200 160l-40 40-80-80 40-40M300 160l40 40 80-80-40-40M160 300l40 40-80 80-40-40M340 300l-40 40 80 80 40-40"
      fill="currentcolor"
      stroke="currentcolor"
    />
  </svg>
);

export default SvgFullscreen;
