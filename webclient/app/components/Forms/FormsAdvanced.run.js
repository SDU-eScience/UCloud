function formAdvanced() {

    if ( !$.fn.select2 ||
         !$.fn.datepicker ||
         !$.fn.clockpicker ||
         !$.fn.colorpicker ) return;

    // Select 2

    $('#select2-1').select2();
    $('#select2-2').select2();
    $('#select2-3').select2();
    $('#select2-4').select2({
        placeholder: 'Select a state',
        allowClear: true
    });

    // Datepicker

    $('#example-datepicker-1').datepicker();
    $('#example-datepicker-2').datepicker();
    $('#example-datepicker-3').datepicker();
    $('#example-datepicker-4')
        .datepicker({
            container:'#example-datepicker-container-4'
        });
    $('#example-datepicker-5')
        .datepicker({
            container:'#example-datepicker-container-5'
        });

    // Clockpicker
    var cpInput = $('.clockpicker').clockpicker();
    // auto close picker on scroll
    $('main').scroll(function() {
        cpInput.clockpicker('hide');
    });

    // Colorpicker

    $('#cp-demo-basic').colorpicker({
        customClass: 'colorpicker-2x',
        sliders: {
            saturation: {
                maxLeft: 200,
                maxTop: 200
            },
            hue: {
                maxTop: 200
            },
            alpha: {
                maxTop: 200
            }
        }
    });
    $('#cp-demo-component').colorpicker();
    $('#cp-demo-hex').colorpicker();

    $('#cp-demo-bootstrap').colorpicker({
        colorSelectors: {
            'default': '#777777',
            'primary': '#337ab7',
            'success': '#5cb85c',
            'info': '#5bc0de',
            'warning': '#f0ad4e',
            'danger': '#d9534f'
        }
    });

}

export default formAdvanced;
