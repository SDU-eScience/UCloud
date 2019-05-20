import * as React from "react";

const SvgFtResultsFolder = (props: any) => (
  <svg
    viewBox="0 0 24 22"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 21.312c0 .379.27.688.6.688h22.8c.33 0 .6-.31.6-.688v-16.5c0-.378-.27-.687-.6-.687H10.8L7.2 0H.6C.27 0 0 .31 0 .687v20.625z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M9.455 15.01l-2.354-2.6L5 14.546l4.454 4.455 9.546-9.546-2.1-2.145-7.446 7.7z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgFtResultsFolder;
