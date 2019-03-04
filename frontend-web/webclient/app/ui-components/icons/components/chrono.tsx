import * as React from "react";

const SvgChrono = props => (
  <svg
    viewBox="0 0 21 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
      d="M6.044 12.577l1.484-2.1 4.083 2.885-1.484 2.1z"
    />
    <path
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
      d="M15.587 10.385l.993 2.372-6.456 2.705-.993-2.372z"
    />
    <path
      d="M18.411 7.408l1.65-1.65-1.819-1.818-1.65 1.65a10.24 10.24 0 0 0-6.306-2.161C4.606 3.429 0 8.034 0 13.715 0 19.395 4.605 24 10.286 24c5.68 0 10.286-4.605 10.286-10.285a10.24 10.24 0 0 0-2.16-6.307zm-2.443 11.989a7.983 7.983 0 0 1-5.682 2.353 7.983 7.983 0 0 1-5.682-2.353 7.983 7.983 0 0 1-2.354-5.682c0-2.147.836-4.165 2.354-5.683a7.983 7.983 0 0 1 5.682-2.353c2.146 0 4.164.836 5.682 2.353a7.983 7.983 0 0 1 2.354 5.683 7.983 7.983 0 0 1-2.354 5.682z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
      d="M6.857 0h6.858v2.572H6.857z"
    />
  </svg>
);

export default SvgChrono;
