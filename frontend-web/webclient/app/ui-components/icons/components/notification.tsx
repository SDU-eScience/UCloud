import * as React from "react";

const SvgNotification = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M20.47 15.333V8c0-4.4-3.811-8-8.47-8C7.34 0 3.53 3.6 3.53 8v7.333L0 20h24l-3.53-4.667z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M12 24c1.56 0 2.823-1.194 2.823-2.667H9.176C9.176 22.806 10.44 24 12 24"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgNotification;
