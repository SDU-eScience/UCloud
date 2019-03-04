import * as React from "react";

const SvgFtFavFolder = props => (
  <svg
    viewBox="0 0 24 22"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 21.313c0 .378.27.687.6.687h22.8c.33 0 .6-.31.6-.687V4.812c0-.378-.27-.687-.6-.687H10.8L7.2 0H.6C.27 0 0 .309 0 .687v20.626z"
      fill={undefined}
    />
    <path
      d="M11.993 6.322l2.163 4.383 4.837.703-3.5 3.412.826 4.817-4.326-2.274-4.326 2.274.826-4.817-3.5-3.412 4.837-.703 2.163-4.383z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgFtFavFolder;
