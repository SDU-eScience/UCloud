import * as React from "react";

const SvgProperties = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M1.5 18.5c-.397 0-.78.159-1.06.44A1.498 1.498 0 001.5 21.5H8v-3H1.5zm0-16a1.5 1.5 0 000 3h11.833v-3H1.5zm11.5 16v3h9.5c.397 0 .78-.159 1.06-.44a1.498 1.498 0 00-1.06-2.56H13zm-7-5v-3H1.499A1.499 1.499 0 000 12V12a1.499 1.499 0 001.499 1.5H6zm16.501 0A1.499 1.499 0 0024 12.001V12A1.499 1.499 0 0022.5 10.5H10.667v3H22.5zM18 2.5v3h4.5a1.5 1.5 0 000-3H18z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      fill={props.color2 ? props.color2 : null}
      d="M16 0h3v8h-3zM5 8h3v8H5zM10.667 16h3v8h-3z"
    />
  </svg>
);

export default SvgProperties;
