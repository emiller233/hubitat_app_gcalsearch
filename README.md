*GCal-Search*

*Now with choice of virtual contact or virtual presence devices*

Steps to set this up...

1) Create a Google Project - https://console.developers.google.com and enable OAuth2 - see https://support.google.com/googleapi/answer/6158849

        a) Give your project any name (perhaps "HE-GCal")
        b) Enable the Calendar API - https://console.developers.google.com/apis/library
        c) Set up the OAuth Consent screen - https://console.developers.google.com/apis/credentials/consent 
              i) Application Type: Internal
              ii) Give it any name (perhaps "HE-GCal")
              iii) Add scope `../auth/calendar.events.readonly` (if you don't see it in the list, go back to step 1b)
        d) Create credentials - https://console.developers.google.com/apis/credentials
              i) Type: OAuth Client ID
              ii) Application Type: Other
              iii) Give it any name (perhaps "HE-GCal")
                               
        e) Copy the Client ID and Client Secret from the Google credentials you just made.  Paste them in a text editor, as you will need these later
        
2) Copy the code for the 2 Apps "GCal Search" and "GCal Search Trigger" into Hubitat, under Apps Code
        
3) Copy the code for the 2 Devices "GCal Event Sensor" and "GCal Presence Sensor" into Hubitat, under Drivers Code

4) In Hubitat, go to the Apps page and install the "GCal Search" app. 
        -This will walk you through connecting to Google and selecting a calendar and search terms.
        - You can create multiple connections, and based on your selection of virtual device, the app will create a virtual Contact Sensor or a virtual Presence Sensor that are Open/Present when the event starts and Close/Not Present when the event ends.


Donations always welcome... 
https://www.paypal.me/infofiend
