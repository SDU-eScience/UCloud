import * as React from "react";

const SvgPreview = (props: any) => (
  <svg
    viewBox="0 0 24 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <g fill={undefined}>
      <path d="M7.842 7.854h-3v2h3v3h2v-3h3v-2h-3v-3h-2v3z" />
      <path
        d="M17.143 15.086h-1.097l-.412-.412c1.372-1.508 2.195-3.565 2.195-5.76A8.897 8.897 0 008.914 0 8.896 8.896 0 000 8.914a8.897 8.897 0 008.914 8.915c2.195 0 4.252-.823 5.76-2.195l.412.412v1.097L21.943 24 24 21.943l-6.857-6.857zm-8.229 0a6.146 6.146 0 01-6.171-6.172 6.146 6.146 0 016.171-6.171 6.146 6.146 0 016.172 6.171 6.146 6.146 0 01-6.172 6.172z"
        fillRule="nonzero"
      />
    </g>
  </svg>
);

export default SvgPreview;
