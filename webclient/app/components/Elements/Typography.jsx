import React from 'react';
import pubsub from 'pubsub-js';

class Typography extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    {/* START row */}
                    <div className="row">
                        <div className="col-lg-6">
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Headings</h5>
                                    <h1>Heading 1<small>Sub-heading</small></h1>
                                    <h2>Heading 2<small>Sub-heading</small></h2>
                                    <h3>Heading 3<small>Sub-heading</small></h3>
                                    <h4>Heading 4<small>Sub-heading</small></h4>
                                    <h5>Heading 5<small>Sub-heading</small></h5>
                                    <h6>Heading 6<small>Sub-heading</small></h6>
                                </div>
                            </div>
                            {/* END card */}
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Emphasis Classes</h5>
                                    <p className="text-muted">This is an example of muted text.</p>
                                    <p className="text-primary">This is an example of primary text.</p>
                                    <p className="text-success">This is an example of success text.</p>
                                    <p className="text-info">This is an example of info text.</p>
                                    <p className="text-warning">This is an example of warning text.</p>
                                    <p className="text-danger">This is an example of danger text.</p>
                                </div>
                            </div>
                            {/* END card */}
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Abbreviations</h5>
                                    <p>The abbreviation of the word attribute is<abbr title="attribute">attr</abbr>.</p>
                                    <p><abbr title="HyperText Markup Language" className="initialism">HTML</abbr>is an abbreviation for a programming language.</p><br/>
                                    <h4>Addresses</h4>
                                    <address><strong>Twitter, Inc.</strong><br/>795 Folsom Ave, Suite 600<br/>San Francisco, CA 94107<br/><abbr title="Phone">P:</abbr>(123) 456-7890</address>
                                    <address><strong>Full Name</strong><br/><a href="mailto:#">first.last@example.com</a></address>
                                </div>
                            </div>
                            {/* END card */}
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Description Lists</h5>
                                    <dl>
                                        <dt>Standard Description List</dt>
                                        <dd>Description Text</dd>
                                        <dt>Description List Title</dt>
                                        <dd>Description List Text</dd>
                                    </dl>
                                    <dl className="dl-horizontal">
                                        <dt>Horizontal Description List</dt>
                                        <dd>Description Text</dd>
                                        <dt>Description List Title</dt>
                                        <dd>Description List Text</dd>
                                    </dl>
                                </div>
                            </div>
                            {/* END card */}
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Code</h5>
                                    <p>This is an example of an inline code element within body copy. Wrap inline code within a<code><code>...</code></code>tag.</p>
                                    <pre>\nThis is an example of preformatted text.</pre>
                                </div>
                            </div>
                            {/* END card */}
                        </div>
                        <div className="col-lg-6">
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Paragraphs</h5>
                                    <p className="lead">This is an example of lead body copy.</p>
                                    <p>This is an example of standard paragraph text. This is an example of<a href="#">link anchor text</a>within body copy.</p>
                                    <p><small>This is an example of small, fine print text.</small></p>
                                    <p><strong>This is an example of strong, bold text.</strong></p>
                                    <p><em>This is an example of emphasized, italic text.</em></p><br/>
                                    <h4>Alignment Helpers</h4>
                                    <p className="text-left">Left aligned text.</p>
                                    <p className="text-center">Center aligned text.</p>
                                    <p className="text-right">Right aligned text.</p>
                                </div>
                            </div>
                            {/* END card */}
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Blockquotes</h5>
                                    <h4>Default Blockquote</h4>
                                    <blockquote>
                                        <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer posuere erat a ante.</p>
                                    </blockquote>
                                    <h4>Blockquote with Citation</h4>
                                    <blockquote>
                                        <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer posuere erat a ante.</p><small>Someone famous in
                                            <cite title="Source Title">Source Title</cite></small>
                                    </blockquote>
                                    <h4>Right Aligned Blockquote</h4>
                                    <div className="clearfix">
                                        <blockquote className="pull-right">
                                            <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer posuere erat a ante.</p>
                                        </blockquote>
                                    </div>
                                </div>
                            </div>
                            {/* END card */}
                            {/* START card */}
                            <div className="card">
                                <div className="card-body">
                                    <h5 className="mt0">Lists</h5>
                                    <h4>Unordered List</h4>
                                    <ul>
                                        <li>List Item</li>
                                        <li>List Item</li>
                                        <li>
                                            <ul>
                                                <li>List Item</li>
                                                <li>List Item</li>
                                                <li>List Item</li>
                                            </ul>
                                        </li>
                                        <li>List Item</li>
                                    </ul>
                                    <h4>Ordered List</h4>
                                    <ol>
                                        <li>List Item</li>
                                        <li>List Item</li>
                                        <li>List Item</li>
                                    </ol>
                                    <h4>Unstyled List</h4>
                                    <ul className="list-unstyled">
                                        <li>List Item</li>
                                        <li>List Item</li>
                                        <li>List Item</li>
                                    </ul>
                                    <h4>Inline List</h4>
                                    <ul className="list-inline">
                                        <li>List Item</li>
                                        <li>List Item</li>
                                        <li>List Item</li>
                                    </ul>
                                </div>
                            </div>
                            {/* END card */}
                        </div>
                    </div>
                    {/* END row */}
                </div>
            </section>
        );
    }
}

export default Typography;
