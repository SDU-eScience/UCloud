import * as React from "react";

const SvgMail = (props: any) => (
  <svg
    viewBox="0 0 24 21"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path d="M8.978 19.12l5.017-5.335-4.493-.035-.524 5.37z" fill="#798aa0" />
    <path
      d="M.585 8.69a.916.916 0 0 0 .042 1.727l7.966 2.654 9.692 7.238a1 1 0 0 0 1.576-.587l4.111-18.697a.844.844 0 0 0-1.129-.968L.585 8.69z"
      fill={undefined}
    />
    <path
      d="M6.391 12.337l12.78-8.124a.237.237 0 0 1 .288.375c-3.382 3.11-9.957 9.162-9.957 9.162l-.437 5.17a.34.34 0 0 1-.663.07l-2.01-6.653z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgMail;
