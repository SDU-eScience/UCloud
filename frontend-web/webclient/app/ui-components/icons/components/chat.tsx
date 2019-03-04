import * as React from "react";

const SvgChat = props => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M21.6 0H2.4A2.397 2.397 0 0 0 .012 2.4L0 24l4.8-4.8h16.8c1.32 0 2.4-1.08 2.4-2.4V2.4C24 1.08 22.92 0 21.6 0z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M4.8 8.4h14.4v2.4H4.8V8.4zm9.6 6H4.8V12h9.6v2.4zm4.8-7.2H4.8V4.8h14.4v2.4z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgChat;
