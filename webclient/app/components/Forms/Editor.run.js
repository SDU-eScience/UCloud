function formEditor() {

    // Summernote HTML editor
    $('.summernote').each(function(){
        $(this).summernote({
            height: 380
        });
    });

    $('.summernote-air').each(function(){
        $(this).summernote({
            airMode: true
        });
    });

}

export default formEditor;
