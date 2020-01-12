/**
 *  Copyright 2017 Mike Nestor & Anthony Pastor
 *  Hubitat conversion by cometfish.
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
 */
 
/**
 *
 * Updates:
 *
 * 20170327.2 - Added options to receive start and end event notifications via SMS or Push
 * 20170327.1 - Changed screen format; made search string & calendar name the default Trigger name
 * 20170322.1 - added checkMsgWanted(); made tips on screen hideable & hidden 
 * 20170321.1 - Fixed OAuth issues; added notification times offset option 
 * 20170306.1 - Bug fixes; No search string now working; schedules fixed
 * 20170303.1 - Re-release version.  Added choice to make child device either contact or presence; conformed methods with updated DTH
 *
 * 20160411.1 - Change schedule to happen in the child app instead of the device
 * 20150304.1 - Revert back hub ID to previous method
 * 20160303.1 - Ensure switch is added to the currently used hub
 * 20160302.1 - Added device versioning
 * 20160223.4 - Fix for duplicating sensors, not having a clostTime at time of open when event is in progress
 * 20160223.2 - Don't make a quick change and forget to test
 * 20160223.1 - Error checking - Force check for Device Handler so we can let the user have a more informative error
 *
 */

definition(
    name: "GCal Search Trigger",
    namespace: "mnestor",
    author: "Mike Nestor and Anthony Pastor. Hubitat conversion by cometfish.",
    description: "Creates & Controls virtual contact (event) or presence sensors.",
    category: "My Apps",
    parent: "mnestor:GCal Search",
    iconUrl: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal.png",
    iconX2Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    iconX3Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
) {}

preferences {
	page(name: "selectCalendars")
   	page(name: "offset")
	page(name: "nameTrigger")    
}

private version() {
	def text = "20170422.1"
}

def selectCalendars() {
	if (parent.logEnable) log.trace "selectCalendars()"
    
    def calendars = parent.getCalendarList()
    if (parent.logEnable) log.debug "Calendar list = ${calendars}"
    
    //force a check to make sure the device handler is available for use
    try {
    	def device = getDevice()
    } catch (e) {
    	return dynamicPage(name: "selectCalendars", title: "Missing Device", install: false, uninstall: false) {
        	section ("Error") {
            	paragraph "We can't seem to create a child device, did you install both associated drivers?"
            }
        }
    }
    
    return dynamicPage(name: "selectCalendars", title: "Create new calendar search", install: false, uninstall: state.installed, nextPage: "offset" ) {
    	section("Required Info") {                               
               //we can't do multiple calendars because the api doesn't support it and it could potentially cause a lot of traffic to happen
            input name: "watchCalendars", title:"", type: "enum", required:true, multiple:false, description: "Which calendar do you want to search?", options:calendars, submitOnChange: true                
            input name: "eventOrPresence", title:"Type of Virtual Device to create?  Contact (for events) or Presence?", type: "enum", required:true, multiple:false, 
            		description: "Do you want this gCal Search Trigger to control a virtual Contact Sensor (for Events) or a virtual presence sensor?", options:["Contact", "Presence"]	//, defaultValue: "Contact"
               
        }
        
        section("Event Filter Tips", hideable:true, hidden:true) {
        	paragraph "Leave search blank to match every event on the selected calendar(s)"
            paragraph "Searches for entries that have all terms\n\nTo search for an exact phrase, " +
            		  "enclose the phrase in quotation marks: \"exact phrase\"\n\nTo exclude entries that " +
                	  "match a given term, use the form -term\n\nExamples:\nHoliday (anything with Holiday)\n" +
                   	  "\"#Holiday\" (anything with #Holiday)\n#Holiday (anything with Holiday, ignores the #)"
		}
        
        section("Optional - Event Filter") {
            input name: "search", type: "text", title: "Search String", required: false, submitOnChange: true                                
        }               
            
        if ( state.installed ) {
	    	section ("Remove Trigger and Corresponding Device") {
            	paragraph "ATTENTION: The only way to uninstall this trigger and the corresponding device is by clicking the button below.\n" +                		
                		  "Trying to uninstall the corresponding device from within that device's preferences will NOT work."
            }
    	}   
	}       
}

def offset(params) {
	if (parent.logEnable) log.trace "offset()"

	def isSMS = false
	if (sendSmsMsg == "Yes") { isSMS = true }
	return dynamicPage(name: "offset", title: "Offset Options", install: false, nextPage: "nameTrigger" ) {

		section("Optional - Set offset times?") {
           	input name:"startOffset", type:"number", title:"Number of Minutes to Offset From Start of Calendar Event", required: false , range:"*..*"
			
	        input name:"endOffset", type:"number", title:"Number of Minutes to Offset From End of Calendar Event", required: false , range:"*..*"
         }
         
         section("Offset Time Tips", hideable:true, hidden:true) {            
          	paragraph "If you want the offset to occur BEFORE the start/end of the event, " + 
              		  "then use a negative number for offset time.  For example, to set it " +
                      "to 5 minutes beforehand, use an offset of -5. \n\n" +
                      "If you want it to occur AFTER the start/end of the event, " +
                      "then use positive number for offset time.  For example, to set it " +
                      "to 9 hours after event start, use an an offset of 540 (can be " +
                      "helpful for all-day events, which start at midnight)." 
             	      "of the calendar event, enter number of minutes to offset here."
         }
	}
}


def nameTrigger(params) {
	if (parent.logEnable) log.trace "nameTrigger()"
	if (parent.logEnable) log.debug "eventOrPresence = ${eventOrPresence}"
    
	//Populate default trigger name & corresponding device name
    def defName = ""
    if (search && eventOrPresence == "Contact") {
    	defName = search - "\"" - "\"" //.replaceAll(" \" [^a-zA-Z0-9]+","")        
    }
    if (parent.logEnable) log.debug "defName = ${defName}"
    
	def dName = defName
	if ( name ) { 
    	dName = name 
    } else {
    	dName = "[Name of Trigger] +"
    }
    
	if (eventOrPresence == "Contact") {
		dName = dName + " Events"
    } else {
		dName = dName + " Presence"
	}

    
	return dynamicPage(name: "nameTrigger", title: "Name of Trigger and Device", install: true, uninstall: false, nextPage: "" ) {
        section("Required - Trigger Name") {
            input name: "name", type: "text", title: "Trigger Name", required: true, multiple: false, defaultValue: "${defName}", submitOnChange: true   
        }
        section("Name of the Corresponding Device will be") {
	        paragraph "${dName}"
    	}
    }
}

def installed() {
	if (parent.logEnable) log.trace "Installed with settings: ${settings}"
    
	initialize()    
}

def updated() {
	if (parent.logEnable) log.trace "Updated with settings: ${settings}"

	//we have nothing to subscribe to yet
    //leave this just in case something crazy happens though
	unsubscribe()
    
	initialize()
}

def initialize() {
   if (parent.logEnable)  log.trace "initialize()"
    state.installed = true
   	
    // Sets Label of Trigger
    app.updateLabel(settings.name)

	// Sets Label of Corresponding Device
    def device = getDevice()    
    if (eventOrPresence == "Contact") {
	    device.label = "${settings.name} Events"
	} else if (eventOrPresence == "Presence") {        
	    device.label = "${settings.name} Presence"    
    }
}

def getDevice() {
	if (parent.logEnable) log.trace "GCalSearchTrigger: getDevice()"
	def device
    if (!childCreated()) {
	    def calName = state.calName
    	if (eventOrPresence == "Contact") {        	
	        device = addChildDevice(getNamespace(), getEventDeviceHandler(), getDeviceID(), null, [label: "${settings.name}", calendar: watchCalendars, hub:hub, completedSetup: true])
    	} else if (eventOrPresence == "Presence") {
			device = addChildDevice(getNamespace(), getPresenceDeviceHandler(), getDeviceID(), null, [label: "${settings.name}", calendar: watchCalendars, hub:hub, completedSetup: true])
		}
	} else {
        device = getChildDevice(getDeviceID())
		     
    }
    return device
}

def getNextEvents() {
   if (parent.logEnable)  log.trace "GCalSearchTrigger: getNextEvents() child"
    def search = (!settings.search) ? "" : settings.search
    return parent.getNextEvents(settings.watchCalendars, search)
}

def getStartOffset() {
   return (!settings.startOffset) ?"" : settings.startOffset
}

def getEndOffset() {
   return (!settings.endOffset) ?"" : settings.endOffset
}

private getPresenceDeviceHandler() { return "GCal Presence Sensor" }
private getEventDeviceHandler() { return "GCal Event Sensor" }


def refresh() {
	if (parent.logEnable) log.trace "GCalSearchTrigger::refresh()"
	try { unschedule(poll) } catch (e) {  }
    
    runEvery15Minutes(poll)
}

def poll() {
	getDevice().poll()
}

def scheduleEvent(method, time, args) {
    def device = getDevice()
   	if (parent.logEnable) log.trace "scheduleEvent( ${method}, ${time}, ${args} ) from ${device}." 
	runOnce( time, method, args)	
}

def unscheduleEvent(method) {
	if (parent.logEnable) log.trace "unscheduleEvent( ${method} )" 
    try { 
    	unschedule( "${method}" ) 
    } catch (e) {}       
}

def open() {
	if (parent.logEnable) log.trace "${settings.name}.open():"
	getDevice().open()
}

def close() {
	if (parent.logEnable) log.trace "${settings.name}.close():"
	getDevice().close()

}

def arrive() {
	if (parent.logEnable) log.trace "${settings.name}.arrive():"
	getDevice().arrived()

}

def depart() {
	if (parent.logEnable) log.trace "${settings.name}.depart():"
	getDevice().departed()
}


private uninstalled() {
    log.trace "uninstalled():"
    
    log.info "Delete all child devices."    
	deleteAllChildren()
}

private deleteAllChildren() {
    if (parent.logEnable) log.trace "deleteAllChildren():"
    
    getChildDevices().each {
    	if (parent.logEnable) log.debug "Delete $it.deviceNetworkId"
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (Exception e) {
            log.error "Fatal exception? $e"
        }
    }
}


private childCreated() {
    def isChild = getChildDevice(getDeviceID()) != null
    if (parent.logEnable) log.debug "childCreated? ${isChild}"
    return isChild
}

private getDeviceID() {
    return "GCal_${app.id}"
}

private getNamespace() { return "info_fiend" }

private textVersion() {
    def text = "Trigger Version: ${ version() }"
}
private dVersion(){
	def text = "Device Version: ${getChildDevices()[0].version()}"
}


