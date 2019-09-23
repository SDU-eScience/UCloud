import * as React from "react";

const SvgMail = (props: any) => (
  <svg
    viewBox="0 0 24 21"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path d="M9.898 19.684l5.017-5.335-4.598-.044-.419 5.379z" fill="#798aa0" />
    <path
      d="M.586 8.691a.918.918 0 00.041 1.726l7.966 2.654 9.693 7.238a.999.999 0 001.575-.587l4.111-18.697a.845.845 0 00-1.129-.968L.586 8.691z"
      fill={undefined}
    />
    <path
      d="M7.542 12.72s7.743-5.83 11.934-8.494a.236.236 0 01.315.056.239.239 0 01-.028.319c-3.382 3.112-9.357 9.823-9.357 9.823s-.237 2.997-.41 5.052a.34.34 0 01-.663.07c-.738-2.441-1.791-6.826-1.791-6.826z"
      fill="url(#mail_svg___Linear1)"
    />
    <defs>
      <linearGradient
        id="mail_svg___Linear1"
        x1={0}
        y1={0}
        x2={1}
        y2={0}
        gradientUnits="userSpaceOnUse"
        gradientTransform="matrix(7 -6 6 7 7.996 13.785)"
      >
        <stop offset={0} stopColor={props.color2 ? props.color2 : null} />
        <stop offset={1} stopColor="#3d4b5c" />
      </linearGradient>
    </defs>
  </svg>
);

export default SvgMail;
