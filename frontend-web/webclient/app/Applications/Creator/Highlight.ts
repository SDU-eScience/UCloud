// Feature toggle highlight
// =====================================================================================================================
// When the user clicks a feature card or a sub-section in the content area, the editor
// scrolls to the corresponding toggle in the metadata panel and plays a short pulse-glow
// animation to draw attention to it.
//
// Each highlightable target has a stable DOM id. The MetadataPanel tags its toggle rows with
// these ids. The FeatureCards call creatorHighlightTarget to trigger the scroll and animation.
//
// The mapping from feature to target id is centralized here so the cards and the panel agree.

// Stable DOM ids for highlightable metadata controls. The MetadataPanel applies these as the
// `id` attribute on the relevant toggle row or section wrapper.
export type CreatorHighlightTarget =
    | "feature-folders"
    | "feature-links"
    | "feature-ipAddresses"
    | "feature-jobLinking"
    | "feature-ssh"
    // SSH lives in the Connectivity section, not in the features section.

// Trigger the highlight for a metadata target. Scrolls the element into view and applies the
// `creator-highlight-active` class to play the pulse-glow animation defined in the CSS.
//
// The function is safe to call before the metadata panel is visible: it does nothing if the
// element is not in the DOM. The metadata panel is shown when no parameter is selected, so the
// caller should deselect the parameter first when needed.
export function creatorHighlightTarget(target: CreatorHighlightTarget): void {
    const el = document.getElementById(target);
    if (!el) return;

    // Scroll the nearest scroll container so the element is visible with some padding.
    const scrollContainer = el.closest<HTMLElement>(".creator-panel-scroll");
    if (scrollContainer) {
        const containerRect = scrollContainer.getBoundingClientRect();
        const elRect = el.getBoundingClientRect();
        const margin = 80;
        if (elRect.top < containerRect.top + margin || elRect.bottom > containerRect.bottom - margin) {
            el.scrollIntoView({block: "center", behavior: "smooth"});
        }
    } else {
        el.scrollIntoView({block: "center", behavior: "smooth"});
    }

    // Restart the animation by removing and re-adding the class.
    el.classList.remove("creator-highlight-active");
    // Force a reflow so the animation replays even if the class was just removed.
    void el.offsetWidth;
    el.classList.add("creator-highlight-active");

    // Remove the class after the animation completes so it can be re-triggered.
    window.setTimeout(() => {
        el.classList.remove("creator-highlight-active");
    }, 2200);
}
