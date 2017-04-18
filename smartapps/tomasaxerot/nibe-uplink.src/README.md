## Installation


 1. Register at Nibeuplink.com and connect your heating pump with an ethernet cable. 
 Then when you are at the overview page for your pump, look in the adressbar and note down your System ID, it's right between "system" and "status".
 Example: ```https://www.nibeuplink.com/System/<SystemId>/Status/Overview```

 2. Login to https://api.nibeuplink.com, go to "my applications" and create new. 
 Call it what you want and set the callback url to "https://graph.api.smartthings.com/oauth/callback". 
 Then note down your "identifier" and "secret". (or just keep the page open to copy these values in the following steps)

 3. Go to the SmartThings IDE and create SmartApp from the code nibe-uplink.groovy found [here](https://github.com/tomasaxerot/SmartThings/tree/master/smartapps/tomasaxerot/nibe-uplink.src).

 4. Go to that apps "app settings" and then under "settings" set clientId to your noted Identifier and clientSecret to your noted Secret from the API. Then enable Oauth, update and publish.

 5. Go create a new Device handler from the code nibe-f750.groovy found [here](https://github.com/tomasaxerot/SmartThings/tree/master/devicetypes/tomasaxerot/nibe-f750.src). Publish.

 6. Install the SmartApp and you will be asked to login to your Nibe UpLink account. Do that and when you see a white page, 
 click done and enter your System ID.
 
