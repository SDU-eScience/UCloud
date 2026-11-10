### ui-components

The base components of the UCloud platform are found in [ui-components](https://github.com/SDU-eScience/UCloud/tree/master/frontend-web/webclient/app/ui-components).

The ranges from components that are used in place of `div` that need styling, like `Box` or `Flex` to fully featured component with logic, like `Tree` and `VirtualizedTree`.

The components should have as few dependencies as possible and only depend on `npm`-packages and other `ui-components`. If they depend on outside components or functions, they may be a better fit outside the `ui-components` folder.

Icons are also found in the `ui-components` file. They are originally SVGs transformed into React-components, which can still be viewed and modified. To add a new icon, or modify an existing, see [here](./frontend-development.md#color-references-and-icons).
)
