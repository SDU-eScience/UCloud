import * as React from "react";

const SvgFtFileSystem = (props: any) => (
  <svg
    viewBox="0 0 18 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 1.5A1.5 1.5 0 011.5 0h15A1.5 1.5 0 0118 1.5V23a.997.997 0 01-1 1H1a.997.997 0 01-1-1V1.5zM16.5 22a.5.5 0 110 1 .5.5 0 010-1zm-15 0a.5.5 0 110 1 .5.5 0 010-1zM9.001 1.5c3.587 0 6.5 2.913 6.5 6.5s-2.913 6.5-6.5 6.5a6.503 6.503 0 01-6.5-6.5c0-3.587 2.912-6.5 6.5-6.5zM16.5 11a.5.5 0 110 1 .5.5 0 010-1zm-15 0a.5.5 0 110 1 .5.5 0 010-1zm7.501-4.5A1.5 1.5 0 119 9.501 1.5 1.5 0 019 6.5zM16.5 1a.5.5 0 110 1 .5.5 0 010-1zm-15 0a.5.5 0 110 1 .5.5 0 010-1z"
      fill={undefined}
    />
    <text
      x={3.443}
      y={22.141}
      fontFamily="'IBMPlexMono-SemiBold','IBM Plex Mono SemiBold',monospace"
      fontWeight={600}
      fontSize={9}
      fill={props.color2 ? props.color2 : null}
    >
      {"FS"}
    </text>
  </svg>
);

export default SvgFtFileSystem;
