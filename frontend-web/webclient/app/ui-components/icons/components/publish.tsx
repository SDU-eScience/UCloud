import * as React from "react";

const SvgPublish = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M3 20.25c0-.554-.243-1.032-.6-1.292V9.947l10.2 8.052L24 9 12.6 0 1.2 9v9.959c-.358.26-.6.738-.6 1.291 0 .524.215.984.541 1.253L0 23.999h3.6l-1.142-2.497c.326-.269.542-.73.542-1.253"
      fill={undefined}
    />
    <path
      d="M12.6 19.774l-.641-.506-7.16-5.652v4.226L12.6 24l7.8-6.158v-4.226l-7.159 5.652-.64.506z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgPublish;
