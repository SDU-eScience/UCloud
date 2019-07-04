import * as React from "react";

const SvgLicense = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M21 24.001H3a3 3 0 0 1-3-3v-18a3 3 0 0 1 3-3h18a3 3 0 0 1 3 3v18a3.004 3.004 0 0 1-3 3zm-3-3l3-3v-12l-3-3H6l-3 3v12l3 3h12z"
      fill={undefined}
    />
    <path
      d="M16.91 17.894H7.09c-.602 0-1.09-.648-1.09-1.447S6.488 15 7.09 15h9.82c.602 0 1.09.648 1.09 1.447s-.488 1.447-1.09 1.447z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
    <path
      d="M11.952 5.728l1.902 1.382-.951 1.31 1.538.5-.726 2.235-1.54-.5v1.618h-2.35v-1.618l-1.54.5L7.56 8.92l1.538-.5-.951-1.309 1.902-1.382.95 1.31.953-1.31z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgLicense;
