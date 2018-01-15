import $ from 'jquery';
import jQBrowser from 'jquery.browser';
import screenfull from 'screenfull';

function initScreenfull() {

    // Not supported under IE (requires jQuery Browser)
    if (jQBrowser.msie)
        return;

    $(document).on('click', '[data-toggle-fullscreen]', (e) => {
        e.preventDefault();

        if (screenfull.enabled) {

            screenfull.toggle();

        } else {
            // Fullscreen not enabled ;
        }

    });
}

export default initScreenfull;