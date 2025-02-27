
def fapiInteractionId = request.getHeaders().getFirst("x-fapi-interaction-id");
if(fapiInteractionId == null) fapiInteractionId = "No x-fapi-interaction-id";
SCRIPT_NAME = "[TranslatePaymentFundsConfirmationResource] (" + fapiInteractionId + ") - ";
logger.debug(SCRIPT_NAME + "Running...")

request.uri.path = request.uri.path.replaceFirst("/open-banking/.*","/backoffice/payment-funds-confirmation")

// Add query parameters
request.uri.rawPath = request.uri.rawPath +
        "/" + attributes.get("accountId") + "?" +
        routeArgAmountQueryParameter + "=" + attributes.get("amount") + "&" +
        routeArgVersionQueryParameter + "=" + attributes.get("version")

logger.debug(SCRIPT_NAME + " The updated raw request uri: " + request.uri.rawPath)
next.handle(context, request)
