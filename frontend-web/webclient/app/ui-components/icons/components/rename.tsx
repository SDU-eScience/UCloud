import * as React from "react";

const SvgRename = props => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M4.5 24H2.244A2.27 2.27 0 0 1 0 21.744V2.258A2.244 2.244 0 0 1 2.244.001H4.5v3H3v18h1.5v3z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M21.755 24H8.244A2.272 2.272 0 0 1 6 21.744V2.258A2.244 2.244 0 0 1 8.244.001h13.511A2.26 2.26 0 0 1 24 2.258v19.485c0 1.223-.951 2.202-2.245 2.258zM21 21V3H9v18h12z"
      fill={undefined}
    />
    <path
      d="M19.846 17.594h-2.502l-.725-2.33h-2.995l-.718 2.33h-2.372l3.575-11.61h2.139l3.598 11.61zm-3.166-3.89L15.1 8.61l-1.517 5.093h3.097z"
      fill={undefined}
    />
  </svg>
);

export default SvgRename;
