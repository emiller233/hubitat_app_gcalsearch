/**
 *  Copyright 2017 Anthony Pastor
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
 * 20170422.1 - added Health Check 
 * 20170306.1 - Scheduling updated
 * 				Fixed Event Trigger with no search string.
 *				Added AskAlexa Message Queue compatibility
 *                
 * 20170302.1 - Initial release 
 *
 */
 
metadata {
	// Automatically generated. Make future change here.
	definition (name: "GCal Presence Sensor", namespace: "info_fiend", author: "anthony pastor") {

        capability "Presence Sensor"
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        capability "Actuator"
        capability "Health Check"

		command "arrived"
		command "departed"
        command "present"
		command "away"       
        
        attribute "calendar", "json_object"
        attribute "calName", "string"
        attribute "eventSummary", "string"
        attribute "arriveTime", "number"
        attribute "departTime", "number"
        attribute "deleteInfo", "string"  
	}  
}

def installed() {
    log.trace "GCalPresenceSensor: installed()"
    sendEvent(name: "DeviceWatch-Enroll", value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}")
    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present", isStateChange: true)

    initialize()
}

def updated() {
    log.trace "GCalPresenceSensor: updated()"
	initialize()
}

def initialize() {
	if (parent.logEnable) log.trace "GCalPresenceSensor: initialize()"
    refresh()
}

def parse(String description) {

}

def arrived() {
	if (parent.logEnable) log.trace "arrived():"
	present()
}

def present() {
	if (parent.logEnable) log.trace "present()"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: 'presence', value: 'present', isStateChange: true)
    
    def departTime = new Date( device.currentState("departTime").value )	
    if (parent.logEnable) log.debug "Scheduling Close for: ${departTime}"
    sendEvent("name":"departTime", "value":departTime)
    parent.scheduleEvent("depart", departTime, [overwrite: true])
}


// refresh status
def refresh() {
	if (parent.logEnable) log.trace "refresh()"
    
    parent.refresh() // reschedule poll
    poll() // and do one now
    
}

def departed() {
	if (parent.logEnable) log.trace "departed():"
	away()
}

def away() {
	if (parent.logEnable) log.trace "away():"
    
	sendEvent(name: "switch", value: "off")
    sendEvent(name: 'presence', value: 'not present', isStateChange: true)
   	
}

def poll() {
    if (parent.logEnable) log.trace "poll()"
    def items = parent.getNextEvents()
    try {
    
	    def currentState = device.currentValue("presence") ?: "not present"
    	def isPresent = currentState == "present"
	    if (parent.logEnable) log.debug "isPresent is currently: ${isPresent}"
    
        // START EVENT FOUND **********
    	if (items && items.items && items.items.size() > 0) {        
	        //	Only process the next scheduled event 
    	    def event = items.items[0]
        	def title = event.summary
            
            def calName = "GCal Primary"
            if ( event.organizer.displayName ) {
            	calName = event.organizer.displayName
           	}
            
        	if (parent.logEnable) log.debug "We Haz Eventz! ${event}"

	        def start
    	    def end
            def type = "E"
            
        	if (event.start.containsKey('date')) {
        	//	this is for all-day events            	
				type = "All-day e"   				             
    	        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
        	    sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))
            	start = sdf.parse(event.start.date)
    	        end = new Date(sdf.parse(event.end.date).time - 60)
	        } else {
			//	this is for timed events            	            
        	    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
            	sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))
	            start = sdf.parse(event.start.dateTime)
        	    end = sdf.parse(event.end.dateTime)
	        }            		            
        
	        def eventSummary = "Event: ${title}\n\n"
			eventSummary += "Calendar: ${state.calName}\n\n"            
    	    def startHuman = start.format("EEE, hh:mm a", location.timeZone)
        	eventSummary += "Arrives: ${startHuman}\n"
	        def endHuman = end.format("EEE, hh:mm a", location.timeZone)
    	    eventSummary += "Departs: ${endHuman}\n\n"
        	
            def startMsg = "${title} arrived at: " + startHuman
            def endMsg = "${title} departed at: " + endHuman
        	    	    

            if (event.description) {
	            eventSummary += event.description ? event.description : ""
    		}    	
            	       
			sendEvent("name":"eventSummary", "value":eventSummary, isStateChange: true)   
            
			//Set the closeTime and endMeg before opening an event in progress
	        //Then use in the open() call for scheduling close

            sendEvent("name":"departTime", "value":end)
                    
			sendEvent("name":"arriveTime", "value":start)

      		// ALREADY IN EVENT?	        	                   
	           // YES
        	if ( start <= new Date() ) {
        		if (parent.logEnable) log.debug "Already in event ${title}."
	        	if (!isPresent) {                     
            		if (parent.logEnable) log.debug "Not Present, so arriving."                    
                    open()                     
                }   
            	
				// NO                
	        } else {
            	if (parent.logEnable) log.debug "Event ${title} still in future."
                
                if (isPresent) { 
	                if (parent.logEnable) og.debug "Presence incorrect, so departing."	                
    	            departed()                                         
              	}
                    
                if (parent.logEnable) log.debug "SCHEDULING ARRIVAL: parent.scheduleEvent(arrive, ${start}, '[overwrite: true]' )."
        		parent.scheduleEvent("arrive", start, [overwrite: true])
                
			}           
        // END EVENT FOUND *******


        // START NO EVENT FOUND ******
    	} else {
        	if (parent.logEnable) log.trace "No events - set all atributes to null."
	    	
            sendEvent("name":"eventSummary", "value":"No events found", isStateChange: true)
            
	    	if (isPresent) { 
            	   
                if (parent.logEnable) log.debug "Presence incorrect, so departing."
                departed()                                          
            } else { 
				parent.unscheduleEvent("open")   
    	    } 
    	}
        // END NO EVENT FOUND
        
    } catch (e) {
    	log.error "Failed to do poll: ${e}"
    }
}

def version() {
	def text = "20170422.1"
}