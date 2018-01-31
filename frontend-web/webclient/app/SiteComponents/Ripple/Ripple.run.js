import './Ripple.scss'
import Ripple from './Ripple';

function initRipple() {
    $('.ripple').each(function(){
        new Ripple($(this));
    });
}

export default initRipple;
