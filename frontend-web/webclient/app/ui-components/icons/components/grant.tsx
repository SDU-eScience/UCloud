import * as React from "react";

const SvgGrant = (props: any) => (
  <svg
    viewBox="0 0 24 18"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 0v13.714h24V0H0zm8.598 12H4.291a2.574 2.574 0 0 0-2.576-2.577v-4.28a3.432 3.432 0 0 0 3.428-3.429h3.455c-1.066 1.26-1.74 3.097-1.74 5.143s.674 3.884 1.74 5.143zm13.688-2.577A2.59 2.59 0 0 0 19.715 12h-4.313c1.066-1.26 1.741-3.097 1.741-5.143s-.675-3.884-1.74-5.143h3.454a3.432 3.432 0 0 0 3.429 3.429v4.28z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
      d="M0 15.428h24v1.715H0z"
    />
  </svg>
);

export default SvgGrant;
