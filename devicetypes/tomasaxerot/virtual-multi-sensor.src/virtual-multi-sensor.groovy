/**
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
 */
metadata {
	definition (name: "Virtual Multi Sensor", namespace: "tomasaxerot", author: "Tomas Axerot") {
		capability "Motion Sensor"
        capability "Contact Sensor"        

		command "active"
		command "inactive"
        command "open"
		command "close"
        
        command "localOk"
        command "localWarn"
	}

	simulator {
		status "active": "motion:active"
		status "inactive": "motion:inactive"
        status "open": "contact:open"
		status "closed": "contact:closed"
	}
    
    tiles(scale: 2) {
        multiAttributeTile(name:"mainTile", type: "generic", width: 6, height: 4, canChangeIcon: false) {
            tileAttribute ("device.virtualMultiSensor", key: "PRIMARY_CONTROL") {
                attributeState "ok", label: 'ok', icon: "st.Home.home3", backgroundColor: "#79b821"
                attributeState "warn", label: 'warn', icon: "st.alarm.alarm.alarm", backgroundColor: "#8a0707"
            }
        }

        standardTile("motionTile", "device.motion", width:3, height:2) {
        	state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
            state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")                        
        }
        standardTile("contactTile", "device.contact", width:3, height:2) {
        	state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e")
            state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821")                                 
        }        

        main(["mainTile"])        
        details(["mainTile", "motionTile", "contactTile"])
    }
}

def parse(String description) {
	log.trace "parse: $description"
	def pair = description.split(":")
	createEvent(name: pair[0].trim(), value: pair[1].trim())
}

def active() {
	log.trace "active"
	sendEvent(name: "motion", value: "active")
    updateMainState()    
}

def inactive() {
	log.trace "inactive"
    sendEvent(name: "motion", value: "inactive")
    updateMainState()
}

def open() {
	log.trace "open"
	sendEvent(name: "contact", value: "open")
    updateMainState()
}

def close() {
	log.trace "close"
    sendEvent(name: "contact", value: "closed")
    updateMainState()
}

def updated() {
	log.debug "updated"
    updateMainState()    
}

def updateMainState() {
	if(device.currentValue("motion") == "active" || device.currentValue("contact") == "open")
    	localWarn()
    else
    	localOk()
}

def localOk() {
	if(device.currentValue("virtualMultiSensor") != "ok") {
    	log.trace "localOk"
		sendEvent(name: "virtualMultiSensor", value: "ok", displayed: false)
    }
}

def localWarn() {
	if(device.currentValue("virtualMultiSensor") != "warn") {
		log.trace "localWarn"
		sendEvent(name: "virtualMultiSensor", value: "warn", displayed: false)
    }
}