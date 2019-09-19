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
      d="M0 1.5A1.5 1.5 0 011.5 0h15A1.5 1.5 0 0118 1.5V23a.997.997 0 01-1 1H1a.997.997 0 01-1-1V1.5zM14.251 24v-2.049a1.248 1.248 0 00-1.144-1.2 164.95 164.95 0 00-8.203 0 1.258 1.258 0 00-1.153 1.209V24h.68v-1.799c.009-.374.127-.688.498-.715.24-.006.481-.012.722-.016V24h.7v-2.543c.433-.007.867-.013 1.3-.016V24h.7v-2.563c.433-.002.867-.002 1.3-.001V24h.7v-2.559c.433.003.867.008 1.3.015V24h.7v-2.531l.731.017c.312.004.505.316.505.742 0 .5-.021 1.09-.021 1.772h.685zm1.751-2.75a.75.75 0 110 1.5.75.75 0 010-1.5zm-14 0a.75.75 0 110 1.5.75.75 0 010-1.5zm0-9.25a.75.75 0 110 1.5.75.75 0 010-1.5zm14 0a.75.75 0 110 1.5.75.75 0 010-1.5zm0-10.75a.75.75 0 110 1.5.75.75 0 010-1.5zm-14 0a.75.75 0 110 1.5.75.75 0 010-1.5z"
      fill="url(#FtFileSystem_svg___Linear1)"
    />
    <text
      x={3.442}
      y={10.892}
      fontFamily="'IBMPlexMono-SemiBold','IBM Plex Mono SemiBold',monospace"
      fontWeight={600}
      fontSize={9}
      fill={props.color2 ? props.color2 : null}
    >
      {"FS"}
    </text>
    <defs>
      <linearGradient
        id="FtFileSystem_svg___Linear1"
        x1={0}
        y1={0}
        x2={1}
        y2={0}
        gradientUnits="userSpaceOnUse"
        gradientTransform="matrix(14 -18 18 14 2.001 21)"
      >
        <stop offset={0} stopColor="#e10000" />
        <stop offset={0.49} stopColor="#f81140" />
        <stop offset={1} stopColor="#ff1654" />
      </linearGradient>
    </defs>
  </svg>
);

export default SvgFtFileSystem;
