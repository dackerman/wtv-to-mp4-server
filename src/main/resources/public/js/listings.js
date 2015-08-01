$(document).ready(function(){
  var inputs = [];

  function reDrawInputs() {
    $('#inputs').empty();
    inputs.forEach(function(i){
      $('#inputs').append(makeInput(i));
    });
    $('.listing-button').each(function() {
      var path = $(this).data('value');
      if (inputs.indexOf(path) > -1) {
        $(this).prop('disabled', true);
      } else {
        $(this).prop('disabled', false);
      }
    })
  }

  function makeInput(path) {
    var input = $('<div class="form-group"><input class="selected-input" type="checkbox" checked name="inputFiles" value="'+path+'">'+path+'</div>');
    input.find(".selected-input").click(clickHandlerFor(path));
    return input;
  }

  $('.listing-button').click(function(){
    var path = $(this).data('value');
    inputs.push(path);
    reDrawInputs();
  });

  function clickHandlerFor(path){
    return function() {
      var idx = inputs.indexOf(path);
      inputs.splice(idx,1);
      reDrawInputs();
    };
  };
});