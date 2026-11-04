## Styling

Styling for the frontend is primarily done with the function `injectStyle` found in the [/Unstyled/index.ts](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Unstyled/index.ts)-file. This function dynamically injects CSS rules to a stylesheet at run-time.

### Motivation

The motivation for this approach, was to make a largely drop-in replacement for `styled-components`, a popular JavaScript library for CSS styling.
Investigating websites with larger userbases, that use the library, revealed that the performance issues we were facing on UCloud using the library were not a result of us not using it correctly. The UCloud solution `Unstyled`, gave performance improvements, and provides what is needed for the styling, without dependening on an external package. As a `devDependency`, `Unstyled` does depend on `styled-systems` which provides TypeScript definitions for styling.

### Using `injectStyle`

Creating a named CSS-class with rules is done as follows:

```typescript
const NameOfClass = injectStyle("class-name", cl => `
    ${cl} {
        background-color: var(--errorMain);
    }

    ${cl}:hover {
        background-color: var(--primaryMain);
    }
`);
```

Using the result of the function call is done as such:

```typescript
function Component(): React.ReactNode {
    return <div className={NameOfClass} />
}
```

The function `injectStyleSimple` can also be used if the code doesn't require pseudo-classes, for instance:

```typescript
const NameOfClass = injectStyleSimple("class-name", `
    background-color: red;
`);
```

Using the class is the same as previously described above.

To ensure uniqueness, the function appends a number. The above could for instance return the string `class-name90`.

If just a classname is needed without any rules, see `makeClassName` function.

### Drawbacks

One of the drawbacks of `Unstyled` is the same as you'd experience writing CSS in general: whether or not a child-selector is matched somewhere. 
While separate tools exist to help with finding these cases, the aforementionened `makeClassName` can be used to leverage the TypeScript compiler to warn when it's the case.
If the result of the `makeClassName` is no longer applied to any component, the rule can safely be deleted. This requires selectors to be written where references to classes in selectors has the classname interpolated. 

`styled-components` will also add browser specific rules that will have to be added manually. While this is more rare,
some rules with the `-webkit`-prefix (similar to the legacy `moz-`) could require manual addition.
