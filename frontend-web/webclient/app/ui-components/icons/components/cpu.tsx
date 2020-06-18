import * as React from "react";

const SvgCpu = (props: any) => (
  <svg viewBox="0 0 79 79" fill="currentcolor" {...props}>
    <rect
      width={47.49}
      height={47.49}
      x={16.255}
      y={16.48}
      rx={4.523}
      ry={7.894}
      fill="none"
      stroke="currentcolor"
      strokeWidth={2.51}
    />
    <rect rx={1.009} y={25.225} height={2.5} width={16} />
    <rect width={16} height={2.5} y={35.225} rx={1.009} />
    <rect rx={1.009} y={45.225} height={2.5} width={16} />
    <rect width={16} height={2.5} y={55.225} rx={1.009} />
    <rect width={16} height={2.5} x={63} y={25.225} rx={1.009} />
    <rect rx={1.009} y={35.225} x={63} height={2.5} width={16} />
    <rect width={16} height={2.5} x={63} y={45.225} rx={1.009} />
    <rect rx={1.009} y={55.225} x={63} height={2.5} width={16} />
    <rect ry={1.009} width={2.5} height={16} x={29.646} />
    <rect x={39.646} height={16} width={2.5} ry={1.009} />
    <rect ry={1.009} width={2.5} height={16} x={49.646} />
    <rect y={63.285} x={29.646} height={16} width={2.5} ry={1.009} />
    <rect ry={1.009} width={2.5} height={16} x={39.646} y={63.285} />
    <rect y={63.285} x={49.646} height={16} width={2.5} ry={1.009} />
    <rect
      ry={6.315}
      rx={3.619}
      y={21.246}
      x={21.004}
      height={37.992}
      width={37.992}
      fill="none"
      stroke="currentcolor"
      strokeWidth={2.008}
    />
    <text
      y={45.724}
      x={23.855}
      style={{
        lineHeight: 1.25,
      }}
      fontWeight={400}
      fontSize={16}
      fontFamily="sans-serif"
      strokeWidth={0.265}
    >
      <tspan y={45.724} x={23.855}>
        {"CPU"}
      </tspan>
    </text>
  </svg>
);

export default SvgCpu;
