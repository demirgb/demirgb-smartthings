/**
 *  Copyright 2018 Velociraptor Aerospace Dynamics
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

import groovy.json.JsonOutput

metadata {
	definition (name: "DemiRGB LED Strip Controller", namespace: "vad", author: "Velociraptor Aerospace Dynamics") {
		capability "Switch Level"
		capability "Actuator"
		capability "Color Control"
		capability "Switch"
		capability "Refresh"

		command "demo"
		command "refresh"
		command "resetcolor"
		command "resetdevice"
	}

	preferences {
		section() {
			input name: "pwmFrequency", type: "number", title: "PWM frequency [Hz]", defaultValue: 240, range: "1..1000"
			input name: "fadeTime", type: "number", title: "Transition fade time [ms]", defaultValue: 1000, range: "0..10000"
			input name: "brNorm", type: "bool", title: "Normalize brightness", defaultValue: true
			input name: "deviceAuth", type: "password", title: "Device authentication (optional)"
		}
	}

tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.Health & Wellness.health4", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.Health & Wellness.health4", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.Health & Wellness.health4", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.Health & Wellness.health4", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
			tileAttribute ("device.color", key: "COLOR_CONTROL") {
				attributeState "color", action:"setColor"
			}
		}

		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "refresh", label:"", action:"refresh.refresh", icon:"st.secondary.refresh", defaultState: true
		}
		standardTile("demo", "device.demo", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "demo", label:"Demo", action:"demo", icon:"st.Health & Wellness.health4", defaultState: true
		}

		main(["switch"])
		details(["switch", "refresh", "demo"])
	}
}

def parse(description) {
	log.debug "parse: $description"
	def msg = parseLanMessage(description)
	def status = msg.status
	def json = msg.json
	log.debug msg.json

	if (json.state) {
		// Yes, for some reason the color picker sends "hex", but the event is "color"
		if (json.state.hex) { sendEvent(name: "color", value: json.state.hex)}
		if (json.state.hue) { sendEvent(name: "hue", value: json.state.hue)}
		if (json.state.saturation) { sendEvent(name: "saturation", value: json.state.saturation)}
		if (json.state.level) { sendEvent(name: "level", value: json.state.level)}
		if (json.state.switch) { sendEvent(name: "switch", value: json.state.switch)}
	}
	if (json.sysinfo) {
		if (json.sysinfo.freq) { updateDataValue("freq", json.sysinfo.freq)}
		if (json.sysinfo.id) { updateDataValue("id", json.sysinfo.id)}
		if (json.sysinfo.machine) { updateDataValue("machine", json.sysinfo.machine)}
		if (json.sysinfo.release) { updateDataValue("release", json.sysinfo.release)}
		if (json.sysinfo.version) { updateDataValue("version", json.sysinfo.version)}
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	//log.debug("Convert hex to ip: $hex") 
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect { String.format( '%02x', it.toInteger() ) }.join()
	//log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
	return hex

}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
	//log.debug hexport
	return hexport
}

private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}

private sendHTTPRequest(data) {
	if (!data.state) { data.state = [:] }
	data.state.brnorm = settings.brNorm
	data.state.frequency = settings.pwmFrequency
	data.state.fadetime = settings.fadeTime
	def headers = [
		HOST: getHostAddress(),
		"Content-Type": "application/json"
	]
	if (settings.deviceAuth) {
		def encoded = ("admin:" + settings.deviceAuth).bytes.encodeBase64()
		headers["Authorization"] = "Basic ${encoded}"
	}
	def result = new physicalgraph.device.HubAction(
		method: "POST",
		path: "/command",
		body: JsonOutput.toJson(data),
		headers: headers
	)
	result
}

def on() {
	log.debug "Turning light on"
	sendHTTPRequest([state: [switch: "on"]])
}

def off() {
	log.debug "Turning light off"
	sendHTTPRequest([state: [switch: "off"]])
}

def setLevel(percent) {
	log.debug "setLevel: ${percent}"
	sendEvent(name: "level", value: percent)
	sendHTTPRequest([state: [level: percent]])
}

def setColor(value) {
	log.debug "setColor: ${value}"
	sendHTTPRequest([
		state: [
			level: value.level,
			switch: value.switch,
			hue: value.hue,
			saturation: value.saturation
		]
	])
}

def demo() {
	log.debug "Demo"
	sendHTTPRequest([cmd: "demo"])
}

def refresh() {
	log.debug "Refresh"
	sendHTTPRequest([])
}
