/*!
 *
 * Centric - Bootstrap Admin App + ReactJS
 *
 * Version: 1.9.5
 * Author: @themicon_co
 * Website: http://themicon.co
 * License: https://wrapbootstrap.com/help/licenses
 *
 */

import React from 'react';
import ReactDOM from 'react-dom';
import { Router, Route, Link, IndexRoute, useRouterHistory } from 'react-router';
import { createHistory } from 'history'

import Core from './components/Core/Core';
import Bootstrap from './components/Bootstrap/Bootstrap';
import Common from './components/Common/Common';
import Colors from './components/Colors/Colors';
import FloatButton from './components/FloatButton/FloatButton';
import Translate from './components/Translate/Translate';

import Dashboard from './components/Dashboard/Dashboard';
import Cards from './components/Cards/Cards';
import Charts from './components/Charts/Charts';
import Forms from './components/Forms/Forms';
import Elements from './components/Elements/Elements';
import Tables from './components/Tables/Tables';
import Layouts from './components/Layouts/Layouts';
import User from './components/User/User';
import Maps from './components/Maps/Maps';
import Pages from './components/Pages/Pages';

import Utils from './components/Utils/Utils';

Translate();

$(() => {
    // prevent page reload when using dummy anchors
    $(document).on('click', '[href=""],[href="#"]', () => {
        return false;
    })

    // Support for float labels on inputs
    $(document).on('change', '.mda-form-control > input', function() {
        $(this)[this.value.length ? 'addClass' : 'removeClass']('has-value');
    });

})

const browserHistory = useRouterHistory(createHistory)({ basename: REACT_BASE_HREF })

// Declare routes
ReactDOM.render(
    <Router history={browserHistory}>
        <Route path="/" component={Core}>

            {/* Default route*/}
            <IndexRoute component={Dashboard} />

            <Route path="dashboard" component={Dashboard} />
            <Route path="cards" component={Cards} />

            <Route path="charts">
                <Route path="radial" component={Charts.Radial} />
                <Route path="flot" component={Charts.Flot} />
                <Route path="rickshaw" component={Charts.Rickshaw} />
            </Route>

            <Route path="elements">
                <Route path="bootstrap" component={Elements.Bootstrapui} />
                <Route path="buttons" component={Elements.Buttons} />
                <Route path="colors" component={Elements.Colors} />
                <Route path="elements" component={Elements.Elements} />
                <Route path="grid" component={Elements.Grid} />
                <Route path="gridmasonry" component={Elements.GridMasonry} />
                <Route path="icons" component={Elements.Icons} />
                <Route path="lists" component={Elements.Lists} />
                <Route path="nestable" component={Elements.Nestable} />
                <Route path="spinners" component={Elements.Spinners} />
                <Route path="sweetalert" component={Elements.Sweetalert} />
                <Route path="typography" component={Elements.Typography} />
                <Route path="utilities" component={Elements.Utilities} />
                <Route path="whiteframes" component={Elements.Whiteframes} />
            </Route>

            <Route path="tables">
                <Route path="tableclassic" component={Tables.TablesClassic} />
                <Route path="datatable" component={Tables.Datatable} />
                <Route path="bootgrid" component={Tables.Bootgrid} />
            </Route>

            <Route path="forms">
                <Route path="dropzone" component={Forms.Dropzone} />
                <Route path="editor" component={Forms.Editor} />
                <Route path="advanced" component={Forms.FormsAdvanced} />
                <Route path="classic" component={Forms.FormsClassic} />
                <Route path="material" component={Forms.Material} />
                <Route path="validation" component={Forms.Validation} />
                <Route path="wizard" component={Forms.Wizard} />
                <Route path="xeditable" component={Forms.XEditable} />
            </Route>

            <Route path="layouts">
                <Route path="boxed" component={Layouts.LayoutsBoxed} />
                <Route path="columns" component={Layouts.LayoutsColumns} />
                <Route path="containers" component={Layouts.LayoutsContainers} />
                <Route path="overlap" component={Layouts.LayoutsOverlap} />
                <Route path="tabs" component={Layouts.LayoutsTabs} />
            </Route>

            <Route path="maps">
                <Route path="google" component={Maps.GoogleMap} />
                <Route path="googlefull" component={Maps.GoogleMapFull} />
                <Route path="vector" component={Maps.VectorMap} />
                <Route path="datamaps" component={Maps.Datamaps} />
            </Route>

            <Route path="pages">
                <Route path="blog" component={Pages.Blog} />
                <Route path="blogarticle" component={Pages.BlogArticle} />
                <Route path="contacts" component={Pages.Contacts} />
                <Route path="faq" component={Pages.Faq} />
                <Route path="gallery" component={Pages.Gallery} />
                <Route path="invoice" component={Pages.Invoice} />
                <Route path="messages" component={Pages.Messages} />
                <Route path="pricing" component={Pages.Pricing} />
                <Route path="profile" component={Pages.Profile} />
                <Route path="projects" component={Pages.Projects} />
                <Route path="search" component={Pages.Search} />
                <Route path="timeline" component={Pages.Timeline} />
                <Route path="wall" component={Pages.Wall} />
            </Route>

            {/* Not found handler */}
            {/*<Route path="*" component={NotFound}/>*/}

        </Route>

        {/* User Pages */}
        <Route path="/" component={User.Layout}>
            <Route path="login" component={User.Login}/>
            <Route path="signup" component={User.Signup}/>
            <Route path="recover" component={User.Recover}/>
            <Route path="lock" component={User.Lock}/>
        </Route>

    </Router>,
    document.getElementById('app')
);

// Auto close sidebar on route changes
browserHistory.listen(function(ev) {
  $('.sidebar-visible').removeClass('sidebar-visible');
});
