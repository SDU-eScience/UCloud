import * as React from "react";

const SvgCopy = props => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 16h3v5h1.5v3H2.244A2.271 2.271 0 0 1 0 21.743V16zM4.5 3H3v5H0V2.258A2.245 2.245 0 0 1 2.244 0H4.5v3z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M24 2.258v19.485a2.244 2.244 0 0 1-2.244 2.258H8.244A2.27 2.27 0 0 1 6 21.743V2.258A2.244 2.244 0 0 1 8.244.001h13.512A2.26 2.26 0 0 1 24 2.258zM9 3.001v18h12V3H9zM6 4.5v15h3v-15H6z"
      fill={undefined}
    />
    <path d="M6 10.5H1.5v3H6V18h3v-4.5h4.5v-3H9V6H6v4.5z" fill={undefined} />
  </svg>
);

export default SvgCopy;
