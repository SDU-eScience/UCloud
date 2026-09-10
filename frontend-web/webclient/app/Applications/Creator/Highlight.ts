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

export type CreatorHighlightTarget =
    | "feature-folders"
    | "feature-links"
    | "feature-ipAddresses"
    | "feature-jobLinking"
    | "feature-ssh"

export function creatorHighlightTarget(target: CreatorHighlightTarget): void {
    const el = document.getElementById(target);
    if (!el) return;

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

    el.classList.remove("creator-highlight-active");
    void el.offsetWidth;
    el.classList.add("creator-highlight-active");

    window.setTimeout(() => {
        el.classList.remove("creator-highlight-active");
    }, 2200);
}
