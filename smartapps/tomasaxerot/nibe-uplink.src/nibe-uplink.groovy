/**
 *  NIBE Uplink
 *
 *  Copyright 2017 Tomas Axerot
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
 *  Based on work by Petter Arnqvist Eriksson
 */
 
 /*
 1. Register at Nibeuplink.com and connect your pump with an ethernet cable, if your pump is fitted with one. 
 Mine is, but its relative new and i dont know when they started mounting it. Then when you are at the overview page for your pump. 
 Look in the adressbar and note down your System ID, it's right between "system" and "status".

 2. Login to https://api.nibeuplink.com, go to "my applications" and create new. 
 Call it what you want and set the callback url to "https://graph.api.smartthings.com/oauth/callback". 
 Then note down your "identifier" and "secret". And keep them secret.

 3. Go to the SmartThings IDE and create SmartApp from the code nice-uplink-ST.groovy found at "https://github.com/tomasaxerot/nibe-uplink-ST/".

 4. Go to that apps "app settings" and then under "settings" set clientId to your noted Identifier and clientSecret to your noted Secret from the API. Then enable Oauth, update and publish.

 5. Go create a new Device handler from the code nibe-f750-tile.groovy found at the same repository as before. 
 Since I'm new to this i don't know if you have to enable Oauth in the device handler or not. Publish.

 6. Install the SmartApp and you will be asked to login to your Nibe UpLink account. Do that and when you see a white page (lol), 
 click done and enter your noted System ID.
 */
 
definition(
    name: "Nibe Uplink",
    namespace: "tomasaxerot",
    author: "Tomas Axerot",
    description: "Nibe connection",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Home/home1-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn@2x.png",
    oauth: true) {
    appSetting "clientId"
    appSetting "clientSecret"
}

mappings {
	path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
	path("/oauth/callback") {action: [GET: "callback"]}
}

preferences {
	page(name: "authentication", title: "Nibe Uplink", content: "mainPage", submitOnChange: true, install: true)
}

def mainPage() {
	if(!atomicState.accessToken) {
        atomicState.authToken = null
        atomicState.accessToken = createAccessToken()
    }

    return dynamicPage(name: "authentication", uninstall: true) {
        if (!atomicState.authToken) {
            def redirectUrl = "https://graph.api.smartthings.com/oauth/initialize?appId=${app.id}&access_token=${atomicState.accessToken}&apiServerUrl=${getApiServerUrl()}"

            section("Nibe authentication") {
                paragraph "Tap below to log in to Nibe Uplink and authorize SmartThings access."
                href url:redirectUrl, style:"embedded", required:true, title:"", description:"Click to enter credentials"
            }
        } else {
             section("Options") {
            	input "systemId", "number", title:"System ID:", required: true
            }
        }
    }
}

def oauthInitUrl() {
   atomicState.oauthInitState = UUID.randomUUID().toString()

   def oauthParams = [
      response_type: "code",
      scope: "READSYSTEM",
      client_id: getAppClientId(),
      state: atomicState.oauthInitState,
      access_type: "offline",
      redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
   ]

   redirect(location: "https://api.nibeuplink.com/oauth/authorize?" + toQueryString(oauthParams))
}

def callback() {
 	//log.debug "callback()>> params: $params, params.code ${params.code}"

	def postParams = [
		uri: "https://api.nibeuplink.com",
		path: "/oauth/token",
		requestContentType: "application/x-www-form-urlencoded;charset=UTF-8",
		body: [
			code: params.code,
			client_secret: getAppClientSecret(),
			client_id: getAppClientId(),
			grant_type: "authorization_code",
			redirect_uri: "https://graph.api.smartthings.com/oauth/callback",
            scope: "READSYSTEM"
		]
	]

	def jsonMap
	try {
		httpPost(postParams) { resp ->
			log.debug "resp callback"
			log.debug resp.data
			atomicState.refreshToken = resp.data.refresh_token
            atomicState.authToken = resp.data.access_token
            atomicState.last_use = now()
			jsonMap = resp.data
		}
	} catch (e) {
		log.error "something went wrong: $e"
		return
	}

	if (atomicState.authToken) {
        def message = """
                <p>Your account is now connected to SmartThings!</p>
                <p>Click 'Done' to finish setup.</p>
        """
        displayMessageAsHtml(message)
        getChildDevice(atomicState.childDeviceID)?.poll()
	} else {
        def message = """
            <p>There was an error connecting your account with SmartThings</p>
            <p>Please try again.</p>
        """
        displayMessageAsHtml(message)
	}
}

def isTokenExpired() {
    return (atomicState.last_use == null || now() - atomicState.last_use > 1800)    	
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

    if(!atomicState.refreshToken) {
        log.warn "Can not refresh OAuth token since there is no refreshToken stored"
    } else {
        def refreshParams = [
            method: 'POST',
            uri   : "https://api.nibeuplink.com",
            path  : "/oauth/token",
            body : [
                refresh_token: "${atomicState.refreshToken}",
                client_secret: getAppClientSecret(),
                grant_type: 'refresh_token',
                client_id: getAppClientId()
            ],
        ]

        try {
            httpPost(refreshParams) { resp ->
                if(resp.data) {
                    //log.debug resp.data
                    atomicState.authToken = resp?.data?.access_token
					atomicState.last_use = now()

                    return true
                }
            }
        }
        catch(Exception e) {
            log.debug "caught exception refreshing auth token: " + e
        }
    }
    return false
}

def toQueryString(Map m) {
   return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def getAppClientId() { appSettings.clientId }
def getAppClientSecret() { appSettings.clientSecret }

def setupChildDevice() {
	if(!atomicState.childDeviceID) {
    	atomicState.childDeviceID = UUID.randomUUID().toString()
    }

    if(!getChildDevice(atomicState.childDeviceID)) {
    	if(!addChildDevice("tomasaxerot", "Nibe F750", atomicState.childDeviceID, null, [name: "Nibe F750 ${atomicState.childDeviceID}", label:"Nibe F750", completedSetup: true])) {
        	log.error "Failed to add child device"
        }
    }
}

def getParameter(String parameterId) {    
    if(isTokenExpired()) {
    	refreshAuthToken()
    }
    
    def params = [
        uri:  'https://api.nibeuplink.com',
        path: "/api/v1/systems/" + systemId + "/parameters",
        query: [parameterIds: parameterId],
        contentType: 'application/json',
        headers: ["Authorization": "Bearer ${atomicState.authToken}"]
    ]
    
    try {
        httpGet(params) {resp ->
            log.debug "resp data: ${resp.data}"
            return resp.data.rawValue[0]
        }
    } catch (e) {
        log.error "error: $e"
    }
}

def installed() {
	setupChildDevice()
}

def updated() {
	setupChildDevice()
}

/**
def isSystemIdSet() {
    if (systemId == null) {
    	return getSystemId()
    }
    return false
}
*/

/**
def getSystemId() {
	//refreshAuthToken()

    def params = [
        uri:  'https://api.nibeuplink.com',
        path: '/api/v1/systems',
        //query: [parameterIds: 'systemid'],
        contentType: 'application/json',
        headers: ["Authorization": "Bearer ${atomicState.authToken}"]
    ]
    //log.debug " Chilla: ${params}"
    try {
        httpGet(params) {resp ->
            //log.debug "resp data: ${resp.data}"
            //log.debug "SystemID: ${resp.data.objects.systemId}"

            def systemId = resp.data.objects.systemId
            log.debug "SystemID: ${systemId}"
        }
    } catch (e) {
        log.error "error: $e"
    }
}
*/