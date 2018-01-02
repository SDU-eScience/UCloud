function formValidation() {

    $('#form-register').validate({
        errorPlacement: errorPlacementInput,
        // Form rules
        rules: {
            email: {
                required: true,
                email: true
            },
            password1: {
                required: true
            },
            confirm_match: {
                required: true,
                equalTo: '#id-password'
            }
        }
    });

    $('#form-login').validate({
        errorPlacement: errorPlacementInput,
        // Form rules
        rules: {
            loginemail: {
                required: true,
                email: true
            },
            loginpassword: {
                required: true
            }
        }
    });

    $('#form-subscribe').validate({
        errorPlacement: errorPlacementInput,
        // Form rules
        rules: {
            feedemail: {
                required: true,
                email: true
            }
        }
    });

    $('#form-example').validate({
        errorPlacement: errorPlacementInput,
        // Form rules
        rules: {
            sometext: {
                required: true
            },
            email: {
                required: true,
                email: true
            },
            digits: {
                required: true,
                digits: true
            },
            url: {
                required: true,
                url: true
            },
            min: {
                required: true,
                min: 6
            },
            max: {
                required: true,
                max: 6
            },
            minlength: {
                required: true,
                minlength: 6
            },
            maxlength: {
                required: true,
                maxlength: 10
            },
            length: {
                required: true,
                range: [6,10]
            },
            match1: {
                required: true
            },
            confirm_match: {
                required: true,
                equalTo: '#id-source'
            }
        }
    });

}

// Necessary to place dyncamic error messages
// without breaking the expected markup for custom input
function errorPlacementInput(error, element) {
    if( element.parent().parent().is('.mda-input-group') ) {
        error.insertAfter(element.parent().parent()); // insert at the end of group
    }
    else if( element.parent().is('.mda-form-control') ) {
        error.insertAfter(element.parent()); // insert after .mda-form-control
    }
    else if ( element.is(':radio') || element.is(':checkbox')) {
        error.insertAfter(element.parent());
    }
    else {
        error.insertAfter(element);
    }
}

export default formValidation;
