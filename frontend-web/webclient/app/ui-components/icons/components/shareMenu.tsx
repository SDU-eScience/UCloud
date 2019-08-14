import * as React from "react";

const SvgShareMenu = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 19h3v2h1.5v3H2.244A2.271 2.271 0 010 21.743V19zM4.5 3H3v8H0V2.258A2.245 2.245 0 012.244 0H4.5v3z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M6 7V2.257A2.245 2.245 0 018.242 0h13.512A2.26 2.26 0 0124 2.257v19.485c0 1.223-.95 2.203-2.244 2.258H8.243A2.271 2.271 0 016 21.742V16h3v5h12V3H9v4H6z"
      fill={undefined}
    />
    <path
      d="M19 10.996l-7 6v-4h-1c-3.18 0-7.046 1.93-9 4 .207-3.97 4.715-8 9-8h1v-4l7 6z"
      fill={undefined}
    />
  </svg>
);

export default SvgShareMenu;
