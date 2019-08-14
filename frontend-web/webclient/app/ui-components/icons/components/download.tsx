import * as React from "react";

const SvgDownload = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M9 21h12V3H11V0h10.757A2.261 2.261 0 0124 2.26v19.485A2.245 2.245 0 0121.757 24H8.245A2.271 2.271 0 016 21.744V18h3v3z"
      fill={props.color2 ? props.color2 : null}
    />
    <path d="M6 16l6-7H7.998V0H4v9H0l6 7z" fill={undefined} />
  </svg>
);

export default SvgDownload;
