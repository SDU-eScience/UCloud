import React from 'react';
import pubsub from 'pubsub-js';

class Utilities extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <h5>Utility classes</h5>
                    <p>Helpers classes to help build components without adding extra css. Default values can be changed via CSS or LESS @variables.</p>
                    <div className="row">
                        <div className="col-lg-6">
                            <div className="card">
                                <div className="card-heading">Margin</div>
                                <table className="table">
                                    <tbody>
                                        <tr>
                                            <td><code>.m0</code></td>
                                            <td>Clear all margins</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ml0</code></td>
                                            <td>Clear left margin</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mr0</code></td>
                                            <td>Clear right margin</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mt0</code></td>
                                            <td>Clear top margin</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mb0</code></td>
                                            <td>Clear bottom margin</td>
                                        </tr>
                                        <tr>
                                            <td><code>.m</code></td>
                                            <td>All margins to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ml</code></td>
                                            <td>Margin left to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mr</code></td>
                                            <td>Margin right to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mt</code></td>
                                            <td>Margin top to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mb</code></td>
                                            <td>Margin bottom to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.m-sm</code></td>
                                            <td>Small margins of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ml-sm</code></td>
                                            <td>Small margin left of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mr-sm</code></td>
                                            <td>Small margin right of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mt-sm</code></td>
                                            <td>Small margin top of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mb-sm</code></td>
                                            <td>Small margin bottom of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.m-lg</code></td>
                                            <td>Large margins of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ml-lg</code></td>
                                            <td>Large margin left of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mr-lg</code></td>
                                            <td>Large margin right of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mt-lg</code></td>
                                            <td>Large margin top of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mb-lg</code></td>
                                            <td>Large margin bottom of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.m-xl</code></td>
                                            <td>Extra large margins of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ml-xl</code></td>
                                            <td>Extra large margin left of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mr-xl</code></td>
                                            <td>Extra large margin right of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mt-xl</code></td>
                                            <td>Extra large margin top of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mb-xl</code></td>
                                            <td>Extra large margin bottom of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mv</code></td>
                                            <td>Vertical margins of 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mh</code></td>
                                            <td>Horizontal margins of 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mv-lg</code></td>
                                            <td>Large vertical margins of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mh-lg</code></td>
                                            <td>Large horizontal margins of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mv-sm</code></td>
                                            <td>Small vertical margins of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.mh-sm</code></td>
                                            <td>Small horizontal margins of 8px</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div className="col-lg-6">
                            <div className="card">
                                <div className="card-heading">Padding</div>
                                <table className="table">
                                    <tbody>
                                        <tr>
                                            <td><code>.p0</code></td>
                                            <td>Clear all paddings</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pl0</code></td>
                                            <td>Clear left padding</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pr0</code></td>
                                            <td>Clear right padding</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pt0</code></td>
                                            <td>Clear top padding</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pb0</code></td>
                                            <td>Clear bottom padding</td>
                                        </tr>
                                        <tr>
                                            <td><code>.p</code></td>
                                            <td>All paddings to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pl</code></td>
                                            <td>padding left to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pr</code></td>
                                            <td>padding right to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pt</code></td>
                                            <td>padding top to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pb</code></td>
                                            <td>padding bottom to 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.p-sm</code></td>
                                            <td>Small paddings of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pl-sm</code></td>
                                            <td>Small padding left of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pr-sm</code></td>
                                            <td>Small padding right of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pt-sm</code></td>
                                            <td>Small padding top of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pb-sm</code></td>
                                            <td>Small padding bottom of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.p-lg</code></td>
                                            <td>Large paddings of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pl-lg</code></td>
                                            <td>Large padding left of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pr-lg</code></td>
                                            <td>Large padding right of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pt-lg</code></td>
                                            <td>Large padding top of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pb-lg</code></td>
                                            <td>Large padding bottom of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.p-xl</code></td>
                                            <td>Extra large paddings of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pl-xl</code></td>
                                            <td>Extra large padding left of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pr-xl</code></td>
                                            <td>Extra large padding right of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pt-xl</code></td>
                                            <td>Extra large padding top of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pb-xl</code></td>
                                            <td>Extra large padding bottom of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pv</code></td>
                                            <td>Vertical paddings of 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ph</code></td>
                                            <td>Horizontal paddings of 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pv-lg</code></td>
                                            <td>Large vertical paddings of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ph-lg</code></td>
                                            <td>Large horizontal paddings of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.pv-sm</code></td>
                                            <td>Small vertical paddings of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.ph-sm</code></td>
                                            <td>Small horizontal paddings of 8px</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                    <div className="row">
                        <div className="col-lg-6">
                            <div className="card">
                                <div className="card-heading">Sizes</div>
                                <table className="table">
                                    <tbody>
                                        <tr>
                                            <td><code>.fh / .fw</code></td>
                                            <td>Height or Width at 100%</td>
                                        </tr>
                                        <tr>
                                            <td><code>.fh-sm / .fw-sm</code></td>
                                            <td>Height or Width at 100% above 768px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.fh-md / .fw-md</code></td>
                                            <td>Height or Width at 100% above 992px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.fh-lg / .fw-lg</code></td>
                                            <td>Height or Width at 100% above 1200px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb8</code></td>
                                            <td>Set width and height of 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb16</code></td>
                                            <td>Set width and height of 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb24</code></td>
                                            <td>Set width and height of 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb32</code></td>
                                            <td>Set width and height of 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb40</code></td>
                                            <td>Set width and height of 40px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb48</code></td>
                                            <td>Set width and height of 48px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb64</code></td>
                                            <td>Set width and height of 64px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb80</code></td>
                                            <td>Set width and height of 80px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb96</code></td>
                                            <td>Set width and height of 96px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.thumb128</code></td>
                                            <td>Circle with radius 128px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial8</code></td>
                                            <td>Circle with radius 8px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial16</code></td>
                                            <td>Circle with radius 16px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial24</code></td>
                                            <td>Circle with radius 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial32</code></td>
                                            <td>Circle with radius 32px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial40</code></td>
                                            <td>Circle with radius 40px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial48</code></td>
                                            <td>Circle with radius 48px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial64</code></td>
                                            <td>Circle with radius 64px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial80</code></td>
                                            <td>Circle with radius 80px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial96</code></td>
                                            <td>Circle with radius 96px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.initial128</code></td>
                                            <td>Circle with radius 128px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-xxs</code></td>
                                            <td>Set width of 60px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-xs</code></td>
                                            <td>Set width of 90px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-sm</code></td>
                                            <td>Set width of 150px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-sd</code></td>
                                            <td>Set width of 200px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-md</code></td>
                                            <td>Set width of 240px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-lg</code></td>
                                            <td>Set width of 280px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-xl</code></td>
                                            <td>Set width of 320px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-xxl</code></td>
                                            <td>Set width of 360px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-wide</code></td>
                                            <td>Set width of 100%</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-auto</code></td>
                                            <td>Set width auto</td>
                                        </tr>
                                        <tr>
                                            <td><code>.wd-zero</code></td>
                                            <td>Set width of 0px</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right"><a href="whiteframes.html">Material Shadows</a></div>Miscellaneous
                                </div>
                                <table className="table">
                                    <tbody>
                                        <tr>
                                            <td><code>.table-fixed</code></td>
                                            <td>Make table layout fixed</td>
                                        </tr>
                                        <tr>
                                            <td><code>.oh</code></td>
                                            <td>Overflow hidden (clear floats)</td>
                                        </tr>
                                        <tr>
                                            <td><code>.oa</code></td>
                                            <td>Overflow auto (adds scrollbars)</td>
                                        </tr>
                                        <tr>
                                            <td><code>.shadow-clear</code></td>
                                            <td>Remove box shadow</td>
                                        </tr>
                                        <tr>
                                            <td><code>.radius-clear</code></td>
                                            <td>Remove border radius</td>
                                        </tr>
                                        <tr>
                                            <td><code>.link-unstyled</code></td>
                                            <td>Remove underline and outline styles</td>
                                        </tr>
                                        <tr>
                                            <td><code>.reader-block</code></td>
                                            <td>Makes paragraphs more readable</td>
                                        </tr>
                                        <tr>
                                            <td><code>.image-list</code></td>
                                            <td>A list of images links overlap to the previous</td>
                                        </tr>
                                        <tr>
                                            <td><code>textarea.no-resize</code></td>
                                            <td>Remove resize option from Texareas</td>
                                        </tr>
                                        <tr>
                                            <td><code>.inline</code></td>
                                            <td>Forces display: inline</td>
                                        </tr>
                                        <tr>
                                            <td><code>.block</code></td>
                                            <td>Forces display: block</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div className="col-lg-6">
                            <div className="card">
                                <div className="card-heading">Text &amp; Font icons</div>
                                <table className="table">
                                    <tbody>
                                        <tr>
                                            <td><code>.text-white</code></td>
                                            <td>Make text color white</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-alpha</code></td>
                                            <td>Make text color white 50% opacity</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-alpha-inverse</code></td>
                                            <td>Make text color dark 50% opacity</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-gray-darker</code></td>
                                            <td>Make text color @gray-darker</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-gray-dark</code></td>
                                            <td>Make text color @gray-dark</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-gray</code></td>
                                            <td>Make text color @gray</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-gray-light</code></td>
                                            <td>Make text color @gray-light</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-gray-lighter</code></td>
                                            <td>Make text color @gray-lighter</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-inherit</code></td>
                                            <td>Make text color inherit</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-soft</code></td>
                                            <td>Make text color black 26% opacity</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-light</code></td>
                                            <td>Make text color white 74% opacity</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-sm</code></td>
                                            <td>Make text 85% smaller</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-md</code></td>
                                            <td>Make text 90% bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-lg</code></td>
                                            <td>Make text 3x bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-nowrap</code></td>
                                            <td>Make text no wrap</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-thin</code></td>
                                            <td>Make text font weight 100</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-bold</code></td>
                                            <td>Make text font weight bold</td>
                                        </tr>
                                        <tr>
                                            <td><code>.text-normal</code></td>
                                            <td>Make text font weight normal</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-18</code></td>
                                            <td>Make icon at font size 18px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-24</code></td>
                                            <td>Make icon at font size 24px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-36</code></td>
                                            <td>Make icon at font size 36px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-48</code></td>
                                            <td>Make icon at font size 48px</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-2x</code></td>
                                            <td>Make icon 2x bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-3x</code></td>
                                            <td>Make icon 3x bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-4x</code></td>
                                            <td>Make icon 4x bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-5x</code></td>
                                            <td>Make icon 5x bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-lg</code></td>
                                            <td>Make icon 33% bigger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-fw</code></td>
                                            <td>Make icon fixed width</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-dark</code></td>
                                            <td>Make icon dark for light backgrounds</td>
                                        </tr>
                                        <tr>
                                            <td><code>.icon-light</code></td>
                                            <td>Make icon light for dark backgrounds</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right"><a href="colors.html">Material Colors</a></div>Backgrounds
                                </div>
                                <table className="table">
                                    <tbody>
                                        <tr>
                                            <td><code>.bg-white</code></td>
                                            <td>Make background color white</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-primary</code></td>
                                            <td>Make background color @primary</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-success</code></td>
                                            <td>Make background color @success</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-info</code></td>
                                            <td>Make background color @info</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-warning</code></td>
                                            <td>Make background color @warning</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-danger</code></td>
                                            <td>Make background color @danger</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-gray-darker</code></td>
                                            <td>Make background color @gray-darker</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-gray-dark</code></td>
                                            <td>Make background color @gray-dark</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-gray</code></td>
                                            <td>Make background color @gray</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-gray-light</code></td>
                                            <td>Make background color @gray-light</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-gray-lighter</code></td>
                                            <td>Make background color @gray-lighter</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-transparent</code></td>
                                            <td>Make background color transparent</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-fade</code></td>
                                            <td>Make background dark faded from bottom</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-cover</code></td>
                                            <td>Make background image cover</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-full</code></td>
                                            <td>Make background image cover and no-repeat</td>
                                        </tr>
                                        <tr>
                                            <td><code>.bg-center</code></td>
                                            <td>Make background image cover and centered</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Utilities;
