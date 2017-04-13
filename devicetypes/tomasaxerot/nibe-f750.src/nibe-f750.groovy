/**
 *  Nibe F750
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
 
metadata {
	definition (name: "Nibe F750", namespace: "tomasaxerot", author: "Tomas Axerot") {
		capability "Polling"
		capability "Refresh"
        capability "Temperature Measurement"
        capability "Power Meter"
        attribute "fan_speed", "number"
       	attribute "water_temp", "number"
        attribute "outdoor_temp", "number"
	}

tiles(scale: 2) {
		multiAttributeTile(name:"temperature", type:"thermostat", width:6, height:4, canChangeIcon: true) {
  			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
    			attributeState "temperature", label:'${currentValue}°', backgroundColors:[
                	[value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
  			}
  			tileAttribute("device.water_temp", key: "SECONDARY_CONTROL") {
    			attributeState "temperature", icon:"http://cdn.device-icons.smartthings.com/Bath/bath6-icn@2x.png", label:'${currentValue}°', unit:"C"
  			}
        }        
        
        valueTile("outdoor_temp", "device.outdoor_temp", width: 2, height: 2) {
            state "temperature", label:'Outdoor ${currentValue}°', unit:"F", defaultState: true, backgroundColors: [
                [value: 31, color: "#153591"],
                [value: 44, color: "#1e9cbb"],
                [value: 59, color: "#90d2a7"],
                [value: 74, color: "#44b621"],
                [value: 84, color: "#f1d801"],
                [value: 95, color: "#d04e00"],
                [value: 96, color: "#bc2323"]
            ]
        }         
        
        valueTile("fan_speed", "device.fan_speed", width: 2, height: 2) {
            state "default", label:'Fan ${currentValue}%', unit:"%", defaultState: true, backgroundColors: [
                [value: 0, color: "#cccccc"],
                [value: 1, color: "#00a0dc"]
            ]
        }
        
        valueTile("power", "device.power", width: 2, height: 2) {
            state "default", label:'Addition ${currentValue}kW', unit:"kW", defaultState: true, backgroundColors: [
                [value: 0, color: "#cccccc"],
                [value: 1, color: "#00a0dc"]
            ]
        }

		standardTile("refresh", "device.weather", inactiveLabel: false, width: 2, height: 2, decoration: "flat", wordWrap: true) {
			state "default", label: "", action: "refresh", icon:"st.secondary.refresh"
		}

		main(["temperature"])
		details(["temperature", "water_temp", "outdoor_temp", "fan_speed", "power", "refresh"])}
}
def parse(String description) {}

def poll() {
	getIndoorTemp()
    getOutdoorTemp()
    getWaterTemp()
	getFanSpeed()
	getAddition()	
}

def getIndoorTemp() {
	def rawValue = parent.getParameter('indoor_temperature')
    if(rawValue != null) {
    	def indoor_temp = rawValue.toInteger() / 10
        log.debug "indoor_temperature: ${indoor_temp}"
        sendEvent("name":"temperature", "value":indoor_temp)
    } else {
        log.debug "No data available"
    }    
}


def getOutdoorTemp() {
	def rawValue = parent.getParameter('outdoor_temperature')    
    if(rawValue != null) {
    	def outdoor_temp = rawValue.toInteger() / 10
        log.debug "outdoor_temperature: ${outdoor_temp}"
        sendEvent("name":"outdoor_temp", "value":outdoor_temp)
    } else {
        log.debug "No data available"
    }    
}

def getWaterTemp() {
	def rawValue = parent.getParameter('hot_water_temperature')    
    if(rawValue != null) {
    	def water_temp = rawValue.toInteger() / 10
        log.debug "hot_water_temperature: ${water_temp}"
        sendEvent("name":"water_temp", "value":water_temp)
    } else {
        log.debug "No data available"
    }    
}

def getFanSpeed() {
    def rawValue = parent.getParameter('fan_speed')
    if(rawValue != null) {
    	def fan_speed = rawValue.toInteger()
        log.debug "fan_speed: ${fan_speed}"
        sendEvent("name":"fan_speed", "value":fan_speed)
    } else {
        log.debug "No data available"
    }
}

def getAddition() {
	def rawValue = parent.getParameter('43084')    
    if(rawValue != null) {
    	def addition = rawValue.toInteger() / 100
        log.debug "Addition: ${addition}"
        sendEvent("name":"power", "value":addition)
    } else {
        log.debug "No data available"
    }
}

def refresh() {
    poll()
}