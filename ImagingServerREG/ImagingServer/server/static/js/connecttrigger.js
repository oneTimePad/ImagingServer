
function set_smart_trigger(){
	var smart_trigger = document.getElementById("smart_trigger")
	smart_trigger.value=!(smart_trigger.value)
	if(smart_trigger.value){
		smart_trigger.innerHtml="Smart Triggering On"
	}
	else {
		smart_trigger.innterHtml = "Smart Triggering Off"
	}

}


function trigger(url){

	$.ajax({

		url:url,
		type:'POST',
		dataType:'JSON',
		data:{"time":$("#time_interval").val(),"trigger":$("#trigger_on").val(),"smart_trigger":$("#smart_trigger").val()},
		success: function(json){
			var time_int = document.getElementById("time_inteval")
			time_int.value=0
			var trigger_on = document.getElementById("trigger_on")

			trigger_on.value=!(trigger_on.value)
			if(trigger_on.value){
				triger_on.innterHtml="Stop Triggering"
			}
			else{
				trigger_on.innterHtml="Start Triggering"
			}

		}

	})




}