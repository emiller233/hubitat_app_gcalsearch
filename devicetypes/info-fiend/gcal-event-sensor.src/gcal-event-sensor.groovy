/**
 *  Copyright 2017 Mike Nestor & Anthony Pastor
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Updates:
 *
 * 20170422.1 - added device Health capability
 * 20170419.1 - cleaned up tiles; added offsetNotify attribute - for additional CoRE flexibility - which turns on if startMsg / turns off at either endMsg event time or endTime (whichever occurs 1st).
 * 20170327.1 - restructured scheduling; bug fixes
 * 20170326.1 - bug fixes
 * 20170322.1 - startMsgTime, startMsg, endMsgTime, and endMsg are now attributes that CoRE should be able to use
 *			  - cleaned up variables and code.
 * 20170321.1 - Added notification offset times.
 * 20170306.1 - Scheduling updated. 
 * 				Fixed Event Trigger with no search string.
 *				Added AskAlexa Message Queue compatibility
 * 20170302.1 - Re-release version 
 * 20170411.1 - Change schedule to happen in the child app instead of the device
 * 20170332.2 - Updated date parsing for non-fullday events
 * 20170331.1 - Fix for all day event attempt #2
 * 20170319.1 - Fix for all day events
 * 20170302.1 - Allow for polling of device version number
 * 20170301.1 - GUI fix for white space
 * 20170223.4 - Fix for Dates in UK
 * 20170223.3 - Fix for DateFormat, set the closeTime before we call open() on in progress event to avoid exception
 * 20170223.1 - Error checking - Force check for Device Handler so we can let the user have a more informative error
 * 20180312.1 - added location and locationForURL attributes; added location to event summary
 * 20180325.1 - added eventTime
 * 20180327.1 - eventTime now works for all-day events; added eventTitle; added Power capability (toggles between 0 and 1) - for use with webCoRE bc defined virtual device subscriptions can't use custom attributes 
 * 20180327.2 - added startOffset and endOffset attributes; - these can be set by new commands below (and will then override the offSets from parent Trigger app)
 *			  - added setStartoffset() and setEndoffset() 
 */

preferences {
    input("primaryName", "test", title: "Name of your GCal primary calendar", description: "Google doesn't provide this info, so if you don't add it here, the device will list it as \"Primary Google Calendar\".")
} 

metadata {
	// Automatically generated. Make future change here.
	definition (name: "GCal Event Sensor", namespace: "info_fiend", author: "anthony pastor") {
		capability "Contact Sensor"
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        capability "Actuator"
		capability "Health Check"
        capability "Power Meter"

		command "open"
		command "close"
        command "childSummary"
        command "childLocation"
        
        
        attribute "calendar", "json_object"
        attribute "calName", "string"
        attribute "name", "string"
        attribute "eventSummary", "string"
        attribute "openTime", "number"
        attribute "closeTime", "number"
        attribute "deleteInfo", "string"
        attribute "location", "string"
        attribute "locationForURL", "string"
        attribute "eventTime", "string"
        attribute "eventTitle", "string"
	}
}

def installed() {
    log.trace "GCalEventSensor: installed()"
    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed", isStateChange: true)
    
    initialize()
}

def updated() {
    log.trace "GCalEventSensor: updated()"
	initialize()
}

def initialize() {
	sendEvent(name: "power", value: "0", isStateChange: true)
    refresh()
    

}

def parse(String description) {

}

// refresh status
def refresh() {
	if (parent.logEnable) log.trace "GCalEventSensor: refresh()"
    
    parent.refresh() // reschedule poll
    poll() // and do one now
    
}

def open() {
	if (parent.logEnable) log.trace "GCalEventSensor: open()"
        
    sendEvent(name: "switch", value: "on")
	sendEvent(name: "contact", value: "open", isStateChange: true)
}

def close() {
	if (parent.logEnable) log.trace "GCalEventSensor: close()"
    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed", isStateChange: true)
}

void poll() {
    if (parent.logEnable) log.trace "poll()"
    def items = parent.getNextEvents()
    try {	                 
            
	    def currentState = device.currentValue("contact") ?: "closed"
    	def isOpen = currentState == "open"
//	    if (parent.logEnable) log.debug "isOpen is currently: ${isOpen}"
    
        // EVENT FOUND **********
    	if (items && items.items && items.items.size() > 0) {        

        	if (parent.logEnable) log.debug "GCalEventSensor: We Haz Eventz!"
            
			//	Only process the next scheduled event             
            def event = items.items[0]
        	def title = event.summary                       
           	sendEvent(name: "eventTitle", value: title)
           	

			// Get Calendar Name 
			def calName = "Primary Google Calendar"
            if (primaryName) { calName = primaryName }
	        if ( event?.organizer?.displayName ) {
    	       	calName = event.organizer.displayName
        	} 
	        sendEvent("name":"calName", "value":calName, displayed: false)             	           
			
            // Get Location, if available
            def evtLocation
            if ( event?.location ) {
            	evtLocation = event.location
                sendEvent("name":"location", "value":evtLocation, displayed: false)
            }    else {
                sendEvent("name":"location", "value":" ", displayed: false)
            }
            
            
			// Get event start and end times
	        def startTime
    	    def endTime
            def type = "E"
        	
        	if (event.start.containsKey('date')) {
	        	//	this is for all-day events            	
				type = "All-day e"   				             
    	        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
        	    sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))            	
                startTime = sdf.parse(event.start.date)
                endTime = new Date(sdf.parse(event.end.date).time - 60)   
	        } else {            	
				//	this is for timed events            	            
        	    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            	sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))	            
                startTime = sdf.parse(event.start.dateTime)
        	    endTime = sdf.parse(event.end.dateTime)
            }   						
            if (parent.logEnable) log.debug "From GCal: startTime = ${startTime} & endTime = ${endTime}"
//            if (parent.logEnable) log.debug "old power = ${device.currentValue("power")}"
            // Toggles power attribute when new event is detected
            if (startTime != device.currentValue("eventTime") ) {
	            if (device.currentValue("power") == 0 ) {
    	        	sendEvent(name: "power", value: "1", isStateChange: true)
//		            if (parent.logEnable) log.debug "new power = ${device.currentValue("power")}"			        	    
                } else {
            		sendEvent(name: "power", value: "0", displayed: true, isStateChange: true)
//		            if (parent.logEnable) log.debug "new power = ${device.currentValue("power")}"			
                }
            }            

            sendEvent("name":"eventTime", "value":startTime, displayed: false, isStateChange: true)
                
			// Build Event Summary
	        def eventSummary = "Next GCal Event: ${title}\n\n"
			eventSummary += "Calendar: ${calName}\n\n" 
            if ( evtLocation ) { eventSummary += "Location: ${evtLocation}\n\n" }
            
    	    def startTimeHuman = startTime.format("EEE, MMM dd hh:mm a", location.timeZone)
        	eventSummary += "Event Start: ${startTimeHuman}\n"
	        
	        def endTimeHuman = endTime.format("EEE, MMM dd hh:mm a", location.timeZone)
    	    eventSummary += "Event End: ${endTimeHuman}\n"

       	    sendEvent("name":"eventSummary", "value":eventSummary)
			
            // Then set the closeTime before opening an event in progress
            sendEvent("name":"closeTime", "value":endTime, displayed: false)

			// Then set the openTime
			sendEvent("name":"openTime", "value":startTime, displayed: false)
            
       //     def eventTest = new Date()
            if (parent.logEnable) log.debug "eventTest = ${eventTest}"
      		// ALREADY IN EVENT?	        	                   
	           // YES
        	if ( startTime <= new Date() ) {
//				if (parent.logEnable) log.debug "startTime ${startTime} should be before eventTest = ${eventTest}"
            	if ( new Date() < endTime ) {
	        		if (parent.logEnable) log.debug "Currently within ${type}vent ${title}."
		        	if (!isOpen) {                     
        	    		if (parent.logEnable) log.debug "Contact currently closed, so opening."                    
            	        open()
                        
                        //Schedule Close & end event messaging
						if (parent.logEnable) log.debug "SCHEDULING CLOSE: parent.scheduleEvent: (close, ${endTime}, '[overwrite: true]' )."
					    parent.scheduleEvent("close", endTime, [overwrite: true])
					    if (parent.logEnable) log.debug "SCHEDULING ENDMSG: parent.scheduleMsg(endMsg, ${endMsgTime}, ${endMsg}, '[overwrite: true]' )."
					    parent.scheduleMsg("endMsg", endMsgTime, endMsg, [overwrite: true]) 
                	}
				} else {
	                if (parent.logEnable) log.debug "Already past start of ${type}vent ${title}."
                    
		        	if (isOpen) {                     
        	    		if (parent.logEnable) log.debug "Contact incorrectly open, so close."                    
            	        close()
                        offsetOff()
						
                        // Unschedule All
						parent.unscheduleEvent("open")
		                parent.unscheduleEvent("close")

                	}
                }    
                // NO                        
	        } else {
            	if (parent.logEnable) log.debug "${type}vent ${title} still in future."
	        	if (isOpen) { 				
                    if (parent.logEnable) log.debug "Contact incorrectly open, so close."
                    close()
                    offsetOff()
				}                 
	            
                // Schedule Open & start event messaging
                if (parent.logEnable) log.debug "SCHEDULING OPEN: parent.scheduleEvent(open, ${startTime}, '[overwrite: true]' )."
        		parent.scheduleEvent("open", startTime, [overwrite: true])

				//Schedule Close & end event messaging
				if (parent.logEnable) log.debug "SCHEDULING CLOSE: parent.scheduleEvent: (close, ${endTime}, '[overwrite: true]' )."
			    parent.scheduleEvent("close", endTime, [overwrite: true])            
        	
            }
            
        // END EVENT FOUND *******


        // START NO EVENT FOUND ******
    	} else {
        	if (parent.logEnable) log.trace "No events - set all attributes to null."

	    	sendEvent("name":"eventSummary", "value":"No events found", isStateChange: true)
            
	    	if (isOpen) {             	
                if (parent.logEnable) log.debug "Contact incorrectly open, so close."
                close()
    	    } else {
            	// Unschedule All
				parent.unscheduleEvent("open")
                parent.unscheduleEvent("close")
    		}            
        }      
        // END NO EVENT FOUND
            
    } catch (e) {
    	log.error "Failed to do poll: ${e}"
    }
}

def getMapHTML() {
	def html = """
		<!DOCTYPE html>
<html>
  <head>
    <meta name="viewport" content="initial-scale=1.0, user-scalable=no">
    <meta charset="utf-8">
    <title>Directions service</title>
    <style>
      /* Always set the map height explicitly to define the size of the div
       * element that contains the map. */
      #map {
        height: 100%;
      }
      /* Optional: Makes the sample page fill the window. */
      html, body {
        height: 100%; 
        margin: 0;
        padding: 0;
      }
      #right-panel {
        font-family: 'Roboto','sans-serif';
        line-height: 20px;
        padding-left: 5px;
        font-size: 10px;
      }

      #right-panel select, #right-panel input {
        font-size: 12px;
      }

      #right-panel select {
        width: 100%;
      }

      #right-panel i {
        font-size: 12px;
      }
      #right-panel {
        height: 95%;
        float: right;
        width: 30%;
        overflow: auto;
      }
      #map {
        margin-right: 30%;
      }
      @media print {
        #map {
          height: 100%;
          margin: 0;
        }
        #right-panel {
          float: none;
          width: auto;
        }
      }
    </style>
  </head>
  <body>
    <div id="right-panel"></div>
	<div id="map"></div>
    <script>
      function initMap() {
        var directionsService = new google.maps.DirectionsService;
        var directionsDisplay = new google.maps.DirectionsRenderer;
        var map = new google.maps.Map(document.getElementById('map'), {
          zoom: 12,
          center: {lat: 40.7114747, lng: -73.9844757}
        });
        
        directionsDisplay.setMap(map);
        directionsDisplay.setPanel(document.getElementById('right-panel'));

        calculateAndDisplayRoute(directionsService, directionsDisplay);
                
      }

      function calculateAndDisplayRoute(directionsService, directionsDisplay) {
        directionsService.route({
          origin: '115 Lenox Rd., Brooklyn, NY 11226',
          destination: '5 W. 91st St., New York, NY 10024',
          travelMode: 'TRANSIT',
          transitOptions: {
    		arrivalTime: new Date(1522209600000 +(4*60*60*1000))				   
  		  }          
        }, function(response, status) {
          if (status === 'OK') {
            directionsDisplay.setDirections(response);
          } else {
            window.alert('Directions request failed due to ' + status);
          }
        });
      }
    </script>
    <script async defer
    src="https://maps.googleapis.com/maps/api/js?key=AIzaSyDL95CqZ5wav1hrEhWXn2zfTCBwKgC4K8o&callback=initMap">
    </script>
  </body>
</html>
		"""
	render contentType: "text/html", data: html, status: 200
}

def childSummary() {
	def theSum 
    theSum = device.currentValue("eventSummary") ?: "There is no event."
	if (parent.logEnable) log.debug "sending summary of ${theSum}"
	return "${theSum}"
}    

def childLocation() {
	def theLoc
    theLoc = device.currentValue("location") 
//    replace (theLoc, " ", "+")
	if (parent.logEnable) log.debug "sending location of ${theLoc}"
	return "${theLoc}"
}    

    
def version() {
	def text = "20180327.2"
}