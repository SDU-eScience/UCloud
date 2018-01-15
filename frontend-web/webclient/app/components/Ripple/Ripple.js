/**
 * Ripple effect for common components
 * @param [element] jQuery or jqLite element
 */

const TRANSITION_END = 'transitionend webkitTransitionEnd';
const jq = jQuery;

class RippleEffect {

    constructor(element) {

        this.element = element;
        this.rippleElement = this.getElement();
        this.$rippleElement = jq(this.rippleElement);

        var clickEv = this.detectClickEvent();

        var self = this;
        element.on(clickEv, function() {
            // remove animation on click
            self.$rippleElement.removeClass('md-ripple-animate');
            // Set ripple size and position
            self.calcSizeAndPos();
            // start to animate
            self.$rippleElement
                .addClass('md-ripple-animate');
        });

        this.$rippleElement.on(TRANSITION_END, function() {
            self.$rippleElement
                .removeClass('md-ripple-animate');
            // avoid weird affect when ripple is not active
            self.rippleElement.style.width = 0;
            self.rippleElement.style.height = 0;
        });
    }


    /**
     * Returns the elements used to generate the effect
     * If not exists, it is created by appending a new
     * dom element
     */
    getElement() {
        var dom = this.element[0];
        var rippleElement = dom.querySelector('.md-ripple');

        if (rippleElement === null) {
            // Create ripple
            rippleElement = document.createElement('span');
            rippleElement.className = 'md-ripple';
            // Add ripple to element
            this.element.append(rippleElement);
        }
        return rippleElement;
    }

    /**
     * Determines the better size for the ripple element
     * based on the element attached and calculates the
     * position be fully centered
     */
    calcSizeAndPos() {
        var size = Math.max(this.element.width(), this.element.height());
        this.rippleElement.style.width = size + 'px';
        this.rippleElement.style.height = size + 'px';
        // autocenter (requires css)
        this.rippleElement.style.marginTop = -(size / 2) + 'px';
        this.rippleElement.style.marginLeft = -(size / 2) + 'px';
    }

    detectClickEvent() {
        var isIOS = ((/iphone|ipad/gi).test(navigator.appVersion));
        return isIOS ? 'touchstart' : 'click';
    }
}


export default RippleEffect;