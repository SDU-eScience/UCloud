import * as React from "react";

const SvgTags = props => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M21.964 3.404v8.135l-11.4 11.752c.429.461 1.114.71 1.543.71.429 0 1.221-.214 1.682-.71l10.21-10.368V5.54l-2.035-2.135z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
    <path
      d="M18.857 0h-8.571L.659 10.823A2.452 2.452 0 0 0 0 12.462c-.016.652.198 1.344.66 1.84l6.631 7.13c.429.462 1.114.722 1.543.722.428 0 1.216-.225 1.677-.721l10.06-10.356v-9.23L18.857 0zm-1.645 5.925C16.05 6.277 15 5.348 15 4.155c0-1.022.766-1.847 1.714-1.847 1.109 0 1.971 1.13 1.645 2.383-.156.594-.595 1.067-1.147 1.234z"
      fill={undefined}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgTags;
