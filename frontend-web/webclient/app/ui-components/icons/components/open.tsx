import * as React from "react";

const SvgOpen = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M17.283 3.569L14.935 1.22C14.26.546 14.485 0 15.41 0H24v8.365-.223 13.29A2.572 2.572 0 0 1 21.432 24H2.568A2.572 2.572 0 0 1 0 21.432V2.568A2.572 2.572 0 0 1 2.568 0h13.29-5.573v3.428H3.428v17.143h17.143v-6.857H24V8.365v.223c0 .936-.554 1.144-1.22.477l-2.73-2.73-8.277 8.279-2.768-2.769 8.278-8.276z"
      fill={undefined}
    />
  </svg>
);

export default SvgOpen;
