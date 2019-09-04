import * as React from "react";

const SvgCopy = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M21.757 0A2.26 2.26 0 0124 2.257v19.485c0 1.226-.955 2.208-2.244 2.258H8.245A2.271 2.271 0 016 21.742V17h3v4h12V3H11V0h10.756z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M4.875 5.25H0v3.5h4.875V14h3.25V8.75H13v-3.5H8.125V0h-3.25v5.25z"
      fill={undefined}
    />
  </svg>
);

export default SvgCopy;
