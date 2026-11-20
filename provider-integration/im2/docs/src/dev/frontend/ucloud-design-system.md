# UCloud design system

## ui-components

The building block components of the UCloud platform are found in [ui-components](https://github.com/SDU-eScience/UCloud/tree/master/frontend-web/webclient/app/ui-components).

This ranges from components that are used in place of `div` that need styling, like `Box` or `Flex` to fully featured components with logic, like `Tree` and `VirtualizedTree`.

The components should have as few dependencies as possible and only depend on `npm`-packages and other `ui-components`. If they depend on outside components or functions, they may be a better fit outside the `ui-components` folder.

## Layout

The UCloud page layout always consists of three parts and an additional optional one:

- Sidebar
- Main container
- Secondary sidebar
- Modal (Optional)

The sidebar, positioned on the left side of the screen. This is used for navigation and is always shown.

<figure>
<img class="dark" src="./assets/sidebar-main-dark.png">
<img class="light" src="./assets/sidebar-main-light.png">
<figcaption>
    The view of the UCloud frontend.
</figcaption>
</figure>
The main container is a component that centers the content, and restricts to a max width of 1400px and resizes for smaller widths. The main container shows the content navigated to by either the sidebar or secondary sidebar. Navigation can happen from the main container, but it shouldn't change the resource type being viewed unless is has a relation to the one it is being changed to.

The secondary sidebar can be in one of two states: either hovering over the main container, or pushing the main container, hover being the default. This is used for further navigation and quick access.

<figure>
<img class="dark" src="./assets/sidebar-secondaryhover-main-dark.png" />
<img class="light" src="./assets/sidebar-secondaryhover-main-light.png" />
<figcaption>
    A view of the UCloud platform with the secondary sidebar hovering over the main container.
</figcaption>
</figure>
<figure>
<img class="dark" src="./assets/sidebar-secondaryfixed-main-dark.png" />
<img class="light" src="./assets/sidebar-secondaryfixed-main-light.png" />
<figcaption>
    A view of the UCloud platform with the secondary sidebar pushing the main container.
</figcaption>
</figure>

Modals are placed on top of the other parts, which are not interactive as long as a modal is present.
Modals do not use the main container, and are styled as relevant for what they contain. They cannot be stacked when presented, as only one will be visible at most. Modals are used for confirmation, resource selection, resource creation, etc.

<figure>
<img class="dark" src="./assets/modal-dark.png" />
<img class="light" src="./assets/modal-light.png" />
<figcaption>
    The UCloud platform with an active modal showing.
</figcaption>
</figure>

## Font

The UCloud platform uses two different fonts, each for a different purpose. Below is the name of the font and the context in which it should be used.

| Font                                                                | Uses                                                                      |
|---------------------------------------------------------------------|---------------------------------------------------------------------------|
| [Inter](https://fonts.google.com/specimen/Inter)                    | The default font for UCloud, used for non-monospaced text.                |
| [JetBrains Mono](https://fonts.google.com/specimen/JetBrains+Mono)  | The font used for monospaced text, e.g. code samples, editor, etc.        |


## Default CSS property values

For consistency, components should by default use specific property values for `font-size`, `padding`, `margin` and `border-radius`. These values won't always be correct ones to use, but components should start out with these values before experimenting with others, if the original ones aren't applicable.

|Property     |Value |
|-------------|------|
|font-size    | 14px |
|border-radius| 8px  |
|margin       | 8px  |
|padding      | 8px  |
 
## Colors

The color palette for the UCloud frontend is chosen from a list of colors of colors, available in [Colors.css](https://github.com/SDU-eScience/UCloud/blob/master/frontend-web/webclient/app/Assets/Colors.css#L184). The colors are then given a name to signify in which context they should be used, e.g. the `success`-prefix for approvals, and `error`-prefix for rejections.

The main accent color for the UCloud platform is a blue color, with a different level of darkness for light and dark themes.

The color variable names and their associated hex-values can be seen in [Colors.css](https://github.com/SDU-eScience/UCloud/tree/master/frontend-web/webclient/app/Assets/Colors.css).

| Notable color variable name (without dashes prefix) | Uses                                      |
|-----------------------------------------------------|-------------------------------------------|
| primaryMain                                         | The accent color used for default buttons, checkboxes  |
| successMain                                         | Used for creation, approving, accepting, etc.          |
| errorMain                                           | Used for cancelling, dismissing, deleting, removing, etc. |

The `Main`-suffix 

Two themes, `light` and `dark` are available on the UCloud frontend, the default presented to the user being based on their system theme. The presented theme can be changed at any time in the sidebar, unless a modal is being shown. When toggled, the CSS color variables will be set according the active theme. Some colors remain the same between themes, but it is still necessary to ensure visual clarity when selecting colors, for both light and dark settings. 

## Icons

The UCloud frontend features icons from the [`HeroIcons`](https://heroicons.com/) set. These were chosen due to their simple monochrome design and licensing. New icons are added as needed, and removed when no longer used anywhere. For a full list of available icons on the UCloud frontend, see [Playground-component](./frontend-development.md#the-playground-component)

Icons are located in the `ui-components` folder. They are originally SVGs transformed into React-components, which can still be viewed and modified. To add a new icon, or modify an existing one, see [here](./frontend-development.md#color-references-and-icons).
