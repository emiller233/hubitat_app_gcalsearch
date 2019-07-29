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

definition (
    name: "GCal Search",
    namespace: "mnestor",
    author: "Mike Nestor & Anthony Pastor. Hubitat conversion by cometfish.",
    description: "Integrates Hubitat with Google Calendar events to trigger virtual event using contact sensor (or a virtual presence sensor).",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal.png",
    iconX2Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    iconX3Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    singleInstance: false,
)

preferences {
	page(name: "authentication", title: "Google Calendar Triggers", content: "mainPage", submitOnChange: true, uninstall: false, install: true)
    
	page name: "pageAbout"
    page name: "pagePoll"
    page name: "pageRestart"
}

private version() {
	def text = "20170326.1"
}

def mainPage() {
	log.info "atomicState.refreshToken = ${atomicState.refreshToken}"

    return dynamicPage(name: "authentication", uninstall: false) {
        if (!atomicState.authToken) {
	        log.debug "No authToken found."
            log.debug gaClientID
            if (!gaClientID || !gaClientSecret) {
                section("Google Authentication"){
                    paragraph "Enter your Google API credentials below"
                    input "gaClientID", "text", title:"Google API Client ID", required: true
                    input "gaClientSecret", "text", title:"Google API Client Secret", required: true
                }
            } else if (!atomicState.authToken) {
                if (!atomicState.verificationUrl)
                    oauthInitUrl()
                else
                    oauthPoll()
                
                if (!atomicState.authToken) {
                  section("Google Authentication"){
                    paragraph "Log in to Google and allow access for GCal Search, with code: "+ atomicState.userCode
                    href url:atomicState.verificationUrl, style:"external", required:true, title:"Authenticate GCal Search", description:"Click to enter credentials"
                      
            href "pagePoll", title: "Check Authentication", description: "Click to check authentication once completed"
        
                  }
                }
            }
            
            if (atomicState.authToken) {
	          log.debug "authToken ${atomicState.authToken} found."
             state.myCals = getCalendarList()
                
              section(){
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "mnestor", title: "New Trigger...", multiple: true)
              }
            } else {
            section("Restart Auth"){
            	href "pageRestart", title: "Restart Authentication", description: "Tap to restart the authentication process, if you entered the wrong credentials."
              }
            }
        } else {
            section(){
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "mnestor", title: "New Trigger...", multiple: true)
            }
        }
        section("Options"){
            	href "pageAbout", title: "About ${textAppName()}", description: "Tap to get application version, license, instructions or remove the application"
              }
        
    }
} 

def pageAbout() {
    dynamicPage(name: "pageAbout", title: "About ${textAppName()}", uninstall: true) {
        section {
            paragraph "${textVersion()}\n${textCopyright()}\n\n${textContributors()}\n\n${textLicense()}\n"
        }
        section("Instructions") {
            paragraph textHelp()
        }
        section("Tap button below to remove all GCal Searches, triggers and switches"){
        }
	}
}

def pageRestart() {
    atomicState.authToken = null
    atomicState.refreshToken = null
    atomicState.verificationUrl = null
    atomicState.userCode = null
    atomicState.deviceCode = null
    app.removeSetting("gaClientID")
    app.removeSetting("gaClientSecret")
    
    dynamicPage(name: "pageRestart", title: "Restart Authentication") {
        section {
            paragraph "Authentication process reset"
            href "authentication", title: "Back", description: "Tap to go back to the start"
        }
	}
}

def pagePoll() {
    oauthPoll()
    if (atomicState.authToken) {
        
      dynamicPage(name: "pagePoll", title: "Waiting for Authentication") {
        section {
            paragraph "Authentication process complete!"
            href "mainPage", title: "Home", description: "Tap to start"
        }
	  }
    } else {
        section {
            paragraph "Still waiting for Authentication..."
            href "pagePoll", title: "Check Authentication", description: "Tap to check again"
        }
        section("Restart Auth"){
            	href "pageRestart", title: "Restart Authentication", description: "Tap to restart the authentication process, if you entered the wrong credentials."
        }
    }
}

def installed() {
   log.trace "Installed with settings: ${settings}"
   initialize()
}

def updated() {
   log.trace "Updated with settings: ${settings}"
   unsubscribe()
   initialize()
}

def initialize() {
    log.trace "GCalSearch: initialize()"

    log.debug "There are ${childApps.size()} GCal Search Triggers"
    childApps.each {child ->
        log.debug "child app: ${child.label}"
    }

	log.info "initialize: state.refreshToken = ${state.refreshToken}"
    
    state.setup = true
}



def getCalendarList() {
    log.trace "getCalendarList()"
    isTokenExpired("getCalendarList")    
    
    def path = "/calendar/v3/users/me/calendarList"
    def calendarListParams = [
        uri: "https://www.googleapis.com",
        path: path,
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
        query: [format: 'json', body: requestBody]
    ]

    log.debug "calendar params: $calendarListParams"
	
    def stats = [:]

    try {
        httpGet(calendarListParams) { resp ->
            resp.data.items.each { stat ->
                stats[stat.id] = stat.summary
            }
            
        }
    } catch (e) {
        log.debug "error: ${path}"
        log.debug e
        if (refreshAuthToken()) {
            return getCalendarList()
        } else {
            log.debug "fatality"
            log.error e.getResponse().getData()
        }
    }
    
    def i=1
    def calList = ""
    def calCount = stats.size()
    calList = calList + "\nYou have ${calCount} available Gcal calendars (Calendar Name - calendarId): \n\n"
    stats.each {
     	calList = calList + "(${i})  ${it.value} - ${it.key} \n"
        i = i+1
	}
           
    log.info calList
    
    state.calendars = stats
    return stats
}

def getNextEvents(watchCalendars, search) {
    log.trace "getNextEvents()"
    isTokenExpired("getNextEvents")    
    
    def pathParams = [
        maxResults: 1,
        orderBy: "startTime",
        singleEvents: true,
        timeMin: getCurrentTime()
    ]
    if (search != "") {
        pathParams['q'] = "${search}"
    }
    log.debug "pathParams: ${pathParams}"
   
    def path = "/calendar/v3/calendars/${watchCalendars}/events"
    def eventListParams = [
        uri: "https://www.googleapis.com",
        path: path,
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
        query: pathParams
    ]

    log.debug "event params: $eventListParams"

    def evs = []
    try {
        httpGet(eventListParams) { resp ->
            evs = resp.data
        }
    } catch (e) {
        log.debug "error: ${path}"
        log.debug e
        log.error e.getResponse().getData()
        if (refreshAuthToken()) {
            return getNextEvents(watchCalendars, search)
        } else {
            log.debug "fatality"
            log.error e.getResponse().getData()
        }
    }
    
   log.debug evs
   return evs
}

def oauthInitUrl() {
	def postParams = [
		uri: "https://accounts.google.com",  
        
		path: "/o/oauth2/device/code",		
		requestContentType: "application/x-www-form-urlencoded; charset=utf-8",
		body: [
			
			client_id: getAppClientId(),
			scope: "https://www.googleapis.com/auth/calendar.readonly"
		]
	]

	log.debug "postParams: ${postParams}"

	
	try {
        
		httpPost(postParams) { resp ->
			log.debug "resp callback"
			log.debug resp.data
            atomicState.deviceCode = resp.data.device_code
            atomicState.verificationUrl = resp.data.verification_url
            atomicState.userCode = resp.data.user_code
		}
        
	} catch (e) {
		log.error "something went wrong: $e"
		log.error e.getResponse().getData()
		return
	}
}

def oauthPoll() {
	def postParams = [
		uri: "https://www.googleapis.com",  
        
		path: "/oauth2/v4/token",		
		requestContentType: "application/x-www-form-urlencoded; charset=utf-8",
		body: [
			
			client_id: getAppClientId(),
            client_secret: getAppClientSecret(),
			code: state.deviceCode,
            grant_type: "http://oauth.net/grant_type/device/1.0"
		]
	]

	log.debug "postParams: ${postParams}"

	
	try {
		httpPost(postParams) { resp ->
			log.debug "resp callback"
			log.debug resp.data
            if (resp.data.error) {
            log.error resp.data.error_description
			displayMessageAsHtml(resp.data.error_description)
            } else {
                state.authToken = resp.data.access_token
                state.refreshToken = resp.data.refresh_token
            }
		}
        
	} catch (e) {
		log.error "something went wrong: $e"
		log.error e.getResponse().getData()
		return
	}
}

def isTokenExpired(whatcalled) {
    log.trace "isTokenExpired() called by ${whatcalled}"
    
    if (atomicState.last_use == null || now() - atomicState.last_use > 3000) {
    	log.debug "authToken null or old (>3000) - calling refreshAuthToken()"
        return refreshAuthToken()
    } else {
	    log.debug "authToken good"
	    return false
    }    
}

def displayMessageAsHtml(message) {
    def html = """
        <!DOCTYPE html>
        <html>
            <head>
            </head>
            <body>
                <div>
                    ${message}
                </div>
            </body>
        </html>
    """
    render contentType: 'text/html', data: html
}

private refreshAuthToken() {
    log.trace "GCalSearch: refreshAuthToken()"
    if(!atomicState.refreshToken && !state.refreshToken) {    
        log.warn "Can not refresh OAuth token since there is no refreshToken stored"
        log.debug state
    } else {
    	def refTok 
   	    if (state.refreshToken) {
        	refTok = state.refreshToken
    		log.debug "Existing state.refreshToken = ${refTok}"
        } else if ( atomicState.refreshToken ) {        
        	refTok = atomicState.refreshToken
    		log.debug "Existing atomicState.refreshToken = ${refTok}"
        }    
        def stcid = getAppClientId()		
        log.debug "ClientId = ${stcid}"
        def stcs = getAppClientSecret()		
        log.debug "ClientSecret = ${stcs}"
        		
        def refreshParams = [
            
            uri   : "https://www.googleapis.com",
            path  : "/oauth2/v4/token",
            body : [
                refresh_token: "${refTok}", 
                client_secret: stcs,
                grant_type: 'refresh_token', 
                client_id: stcid
            ],
        ]

        log.debug refreshParams

        //changed to httpPost
        try {
            httpPost(refreshParams) { resp ->
                log.debug "Token refreshed...calling saved RestAction now!"

                if(resp.data) {
                    log.debug resp.data
                    atomicState.authToken = resp?.data?.access_token
					atomicState.last_use = now()
                    
                    return true
                }
            }
        }
        catch(Exception e) {
            log.debug "caught exception refreshing auth token: " + e
            log.error e.getResponse().getData()
        }
    }
    return false
}

def toQueryString(Map m) {
   return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def getCurrentTime() {
   //RFC 3339 format
   //2015-06-20T11:39:45.0Z
   def d = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
   return d
}

def getAppClientId() { return gaClientID }
def getAppClientSecret() { return gaClientSecret }

def uninstalled() {
    revokeAccess()
}

def childUninstalled() { 

}

def revokeAccess() {

    log.trace "GCalSearch: revokeAccess()"

	refreshAuthToken()
	
	if (!atomicState.authToken) {
    	return
    }
    
	try {
    	def uri = "https://accounts.google.com/o/oauth2/revoke?token=${atomicState.authToken}"
        log.debug "Revoke: ${uri}"
		httpGet(uri) { resp ->
			log.debug "resp"
			log.debug resp.data
    		revokeAccessToken()
            atomicState.accessToken = atomicState.refreshToken = atomicState.authToken = state.refreshToken = null
		}
	} catch (e) {
		log.debug "something went wrong: $e"
		log.debug e.getResponse().getData()
	}
}

//Version/Copyright/Information/Help
private def textAppName() {
	def text = "GCal Search"
}	
private def textVersion() {
    def version = "Main App Version: ${version()}"
    def childCount = childApps.size()
    def childVersion = childCount ? childApps[0].textVersion() : "No GCal Triggers installed"
    def deviceVersion = childCount ? "\n${childApps[0].dVersion()}" : ""
    return "${version}\n${childVersion}${deviceVersion}"
}
private def textCopyright() {
    def text = "Copyright © 2017 Mike Nestor & Anthony Pastor. Hubitat conversion by cometfish."
}
private def textLicense() {
	def text =
		"Licensed under the Apache License, Version 2.0 (the 'License'); "+
		"you may not use this file except in compliance with the License. "+
		"You may obtain a copy of the License at"+
		"\n\n"+
		"    http://www.apache.org/licenses/LICENSE-2.0"+
		"\n\n"+
		"Unless required by applicable law or agreed to in writing, software "+
		"distributed under the License is distributed on an 'AS IS' BASIS, "+
		"WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. "+
		"See the License for the specific language governing permissions and "+
		"limitations under the License."
}

private def textHelp() {
	def text =
        "Once you associate your Google Calendar with this application, you can set up  "+
        "different seaches for different events that will trigger the corresponding GCal "+
        "switch to go on or off.\n\nWhen searching for events, if you leave the search "+
        "string blank it will trigger for each event in your calendar.\n\nTo search an exact phrase, "+
        "enclose the phrase in quotation marks: \"exact phrase\"\n\nTo exclude entries "+
        "that match a given term, use the form -term\n\nExamples:\nHoliday (anything with Holiday)\n" +
        "\"#Holiday\" (anything with #Holiday)\n#Holiday (anything with Holiday, ignores the #)"
}

private def textContributors() {
	def text = "Contributors:\nUI/UX: Michael Struck \nOAuth: Gary Spender"
}