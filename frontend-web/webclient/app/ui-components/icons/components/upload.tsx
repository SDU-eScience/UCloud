import * as React from "react";

const SvgUpload = (props: any) => (
  <svg
    viewBox="0 0 25 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M9.001 21h12V3h-10V0h10.756a2.261 2.261 0 0 1 2.244 2.259v19.485A2.245 2.245 0 0 1 21.757 24H8.245a2.271 2.271 0 0 1-2.244-2.257V18h3v3z"
      fill={props.color2 ? props.color2 : null}
    />
    <path d="M6 .001l-6 7h4v9h4v-9h4l-6-7z" fill={undefined} />
  </svg>
);

export default SvgUpload;
