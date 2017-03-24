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
 
 /**
  * Warning
  * I would consider this DTH in alpha state atm
  * 
  * Version: 
  * 0.1 (2017-03-24): Initial version of DTH for Ubisys Universal Dimmer D1 (Rev. 1), Application: 1.12, Stack: 1.88, Version: 0x010C0158
  * 
  */

metadata {
    definition (name: "Ubisys Universal Dimmer D1", namespace: "tomasaxerot", author: "Tomas Axerot") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Health Check"
        capability "Light"

		//0000 = Basic
        //0003 = Identify
        //0004 = Groups
        //0005 = Scenes
        //0006 = On/off        
        //0008 = Level control
        //0301 = ballast
        //0702 = Metering
		//0B04 = Electrical Measurement
        //FC01 = Manufacturer configuration

		//Raw
		//01 0104 0101 00 08 0000 0003 0004 0005 0006 0008 0301 FC01 00
        //model: D1 (5503), manufacturer: ubisys
        //Endpoint 1: in: 0000, 0003, 0004, 0005, 0006, 0008, 0301 FC01        
        //Endpoint 2: in: 0000, 0003, out: 0005, 0006, 0008 (input 1)
        //Endpoint 3: in: 0000, 0003, out: 0005, 0006, 0008 (input 2)
        //Endpoint 4: in: 0702, 0B04        
        
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008, 0702", manufacturer: "ubisys", model: "D1 (5503)", deviceJoinName: "Ubisys Universal Dimmer D1"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
            tileAttribute ("power", key: "SECONDARY_CONTROL") {
                attributeState "power", label:'${currentValue} W'
            }
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "switch"
        details(["switch", "refresh"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.trace "parse: description is $description"	

	def event = zigbee.getEvent(description)
	if (event) {
    	log.trace "parse: event is $event.name"
		if (event.name == "power") {			
            def descriptionText = '{{ device.displayName }} power is {{ value }} Watts'
			event = createEvent(name: event.name, value: event.value, descriptionText: descriptionText, translatable: true)
		} 
	} else {
		def cluster = zigbee.parse(description)

		if (cluster && cluster.clusterId == 0x0006 && cluster.command == 0x07) {
			if (cluster.data[0] == 0x00) {
				log.debug "ON/OFF REPORTING CONFIG RESPONSE: " + cluster
				event = createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}"
				event = null
			}
		} else if(!shouldProcessMessage(cluster)) {
        	log.trace "Message ignored"
            event = null        	
        } else {
			log.warn "Failed to process message: $description"
			log.debug "${cluster}"
		}        
	}
	return event ? createEvent(event) : event   
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    // 0x07 is bind message
    boolean ignoredMessage = cluster.profileId != 0x0104 ||
        cluster.command == 0x0B ||
        cluster.command == 0x07 ||
        (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}

def off() {
	log.trace "off"
    zigbee.off()
}

def on() {
	log.trace "on"
    zigbee.on()
}

def setLevel(value) {
	log.trace "setLevel: $value"
    zigbee.setLevel(value) + (value?.toInteger() > 0 ? zigbee.on() : [])
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	log.trace "ping"
    return zigbee.onOffRefresh()
}

def refresh() {
	log.trace "refresh"
    //zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.simpleMeteringPowerRefresh() + zigbee.electricMeasurementPowerRefresh() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + zigbee.simpleMeteringPowerConfig() + zigbee.electricMeasurementPowerConfig()
    //Removed: zigbee.electricMeasurementPowerRefresh(), zigbee.electricMeasurementPowerConfig()
    //zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x04]) /*+ zigbee.readAttribute(0x0B04, 0x050B, [destEndpoint: 0x04])*/
    
    zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x04])
}

def configure() {
    //log.debug "Configuring Reporting and Bindings."
    log.trace "configure"

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    //zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x04]) /*+ zigbee.configureReporting(0x0B04, 0x050B, 0x29, 1, 600, 0x05, [destEndpoint: 0x04])*/
    
    refresh() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + zigbee.configureReporting(0x0702, 0x0400, 0x2A, 1, 600, 0x05, [destEndpoint: 0x04])
}