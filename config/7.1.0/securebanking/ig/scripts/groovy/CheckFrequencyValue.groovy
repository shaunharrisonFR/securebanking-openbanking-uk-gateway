/**
 * The script validates if the current request entity contains a valid value for the Frequency field.
 * The frequency field should be found under this hierarchy in the request JSON payload: Data.Initiation.Frequency
 */
def fapiInteractionId = request.getHeaders().getFirst("x-fapi-interaction-id");
if(fapiInteractionId == null) fapiInteractionId = "No x-fapi-interaction-id";
SCRIPT_NAME = "[CheckFrequencyValue] (" + fapiInteractionId + ") - ";

FREQUENCY_REGEX = "^(EvryDay)\$|^(EvryWorkgDay)\$|^(IntrvlWkDay:0[1-9]:0[1-7])\$|^(WkInMnthDay:0[1-5]:0[1-7])\$|^(IntrvlMnthDay:(0[1-6]|12|24):(-0[1-5]|0[1-9]|[12][0-9]|3[01]))\$|^(QtrDay:(ENGLISH|SCOTTISH|RECEIVED))\$"
logger.debug(SCRIPT_NAME + "Running...")

def method = request.method
if (method != "POST") {
    //This script should be executed only if it is a POST request
    logger.debug(SCRIPT_NAME + "Skipping the filter because the method is not POST, the method is " + method)
    return next.handle(context, request)
}

def requestObj = request.entity.getJson()

def frequency = ""
try {
    frequency = requestObj.Data.Initiation.Frequency
} catch (java.lang.Exception e) {
    logger.error(SCRIPT_NAME + "Could not obtain frequency value from request: " + e)
}

logger.debug(SCRIPT_NAME + "Requested frequency: " + frequency)
if (frequency) {
    def match = (frequency =~ FREQUENCY_REGEX)
    if (!match.find()) {
        message = "Invalid frequency value in the request payload."
        logger.error(SCRIPT_NAME + message)
        response = new Response(Status.BAD_REQUEST)
        response.headers['Content-Type'] = "application/json"
        response.status = Status.BAD_REQUEST
        response.entity = "{ \"error\":\"" + message + "\"}"
        return response
    }
    logger.debug(SCRIPT_NAME + "Frequency value is valid.")
}

return next.handle(context, request)